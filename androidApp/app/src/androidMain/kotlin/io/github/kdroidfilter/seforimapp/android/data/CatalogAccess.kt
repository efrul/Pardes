@file:OptIn(ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.android.data

import android.util.Log
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

/** A book of a catalog dropdown: its id and the title it shows (desktop core/catalog BookRef). */
data class BookRef(val id: Long, val title: String)

/** Desktop CatalogPresets.Ids.Categories used by the display rules. */
internal object CatalogIds {
    const val BAVLI = 13L
    const val YERUSHALMI = 20L
    const val MISHNE_TORAH = 45L
    const val SHULCHAN_ARUCH = 62L
}

/**
 * Port of desktop 0.6.7 core/catalog/CatalogAccess.kt: per-category titles and book lists of the home catalog
 * dropdowns, from catalog.pb (PrecomputedCatalog), with desktop's display rules:
 *  - "תלמוד" prefix on Bavli/Yerushalmi category titles;
 *  - "מפרשים" filter inside Mishneh Torah and its direct children;
 *  - "הקדמה" / "פרי מגדים" filter inside Shulchan Aruch and its direct children;
 *  - ancestor-label prefix stripping on book display titles ("שולחן ערוך, אורח חיים" → "אורח חיים").
 * Each category lists its own books only, not its subtree's. Without catalog.pb (an older library), the same tree is
 * built from the database ([load]).
 */
class CatalogAccess private constructor(catalog: PrecomputedCatalog) {
    private val categoryTitles: Map<Long, String>
    private val bookTitles: Map<Long, String>
    private val booksByCategory: Map<Long, List<BookRef>>

    fun categoryTitle(id: Long): String? = categoryTitles[id]

    fun bookTitle(id: Long): String? = bookTitles[id]

    fun booksFor(categoryId: Long): List<BookRef> = booksByCategory[categoryId].orEmpty()

    init {
        val rawCategoryTitles = LinkedHashMap<Long, String>()
        val parentIdByCategory = HashMap<Long, Long?>()
        val titles = HashMap<Long, String>()

        fun walk(cat: CatalogCategory) {
            rawCategoryTitles[cat.id] = cat.title
            parentIdByCategory[cat.id] = cat.parentId
            cat.books.forEach { titles[it.id] = it.title }
            cat.subcategories.forEach { walk(it) }
        }
        catalog.rootCategories.forEach { walk(it) }

        val displayTitles = LinkedHashMap<Long, String>(rawCategoryTitles.size)
        rawCategoryTitles.forEach { (id, title) ->
            displayTitles[id] = displayCategoryTitle(id, title, rawCategoryTitles, parentIdByCategory)
        }

        val byCategory = HashMap<Long, List<BookRef>>()
        val ancestorTitlesCache = HashMap<Long, List<String>>()

        fun walkBooks(cat: CatalogCategory) {
            val excludedPrefixes = excludedBookPrefixesFor(cat.id, parentIdByCategory)
            val ancestorLabels =
                ancestorTitlesCache.getOrPut(cat.id) {
                    collectAncestorTitles(cat.id, rawCategoryTitles, parentIdByCategory)
                }
            // Strip ancestor labels first, then exclude books whose display title starts with
            // a forbidden prefix (e.g. שולחן ערוך, הקדמה → הקדמה → filtered).
            val refs =
                cat.books.mapNotNull { book ->
                    val display = stripAnyLabelPrefix(ancestorLabels, book.title)
                    val trimmed = display.trimStart()
                    if (excludedPrefixes.any { trimmed.startsWith(it) }) null else BookRef(book.id, display)
                }
            byCategory[cat.id] = refs
            cat.subcategories.forEach { walkBooks(it) }
        }
        catalog.rootCategories.forEach { walkBooks(it) }

        categoryTitles = displayTitles
        bookTitles = titles
        booksByCategory = byCategory
    }

    private fun excludedBookPrefixesFor(
        categoryId: Long,
        parents: Map<Long, Long?>,
    ): List<String> {
        val parentId = parents[categoryId]
        return when {
            categoryId == CatalogIds.MISHNE_TORAH || parentId == CatalogIds.MISHNE_TORAH -> listOf(MEFARSHIM_PREFIX)
            categoryId == CatalogIds.SHULCHAN_ARUCH || parentId == CatalogIds.SHULCHAN_ARUCH -> SHULCHAN_ARUCH_EXCLUDED_PREFIXES
            else -> emptyList()
        }
    }

    private fun displayCategoryTitle(
        id: Long,
        rawTitle: String,
        rawTitles: Map<Long, String>,
        parents: Map<Long, Long?>,
    ): String {
        val needsTalmudPrefix = id == CatalogIds.BAVLI || id == CatalogIds.YERUSHALMI
        if (!needsTalmudPrefix) return rawTitle
        val parentTitle = parents[id]?.let { rawTitles[it] }?.takeIf { it.isNotBlank() } ?: TALMUD_FALLBACK
        return "$parentTitle $rawTitle"
    }

    private fun collectAncestorTitles(
        categoryId: Long,
        titles: Map<Long, String>,
        parents: Map<Long, Long?>,
    ): List<String> {
        val labels = mutableListOf<String>()
        var current: Long? = categoryId
        var guard = 0
        while (current != null && guard++ < MAX_ANCESTOR_WALK) {
            titles[current]?.takeIf { it.isNotBlank() }?.let { labels += it }
            current = parents[current]
        }
        return labels.distinct()
    }

    private fun stripAnyLabelPrefix(
        labels: List<String>,
        title: String,
    ): String {
        var result = title
        for (label in labels) result = stripLabelPrefix(label, result)
        return result
    }

    private fun stripLabelPrefix(
        label: String,
        title: String,
    ): String {
        if (label.isBlank()) return title
        val prefix = Regex.escape(label)
        val patterns =
            listOf(
                Regex("^$prefix\\s*,\\s*"),
                Regex("^$prefix,\\s*"),
                Regex("^$prefix\\s*[:–—-]\\s*"),
                Regex("^$prefix\\s*\\+\\s*"),
                Regex("^$prefix\\s+"),
            )
        // Desktop compares the replaced string by identity; a match is the same test, whatever the JDK returns
        for (p in patterns) {
            val match = p.find(title) ?: continue
            return title.substring(match.range.last + 1).trimStart()
        }
        return title
    }

    companion object {
        private const val MEFARSHIM_PREFIX = "מפרשים"
        private const val TALMUD_FALLBACK = "תלמוד"
        private const val MAX_ANCESTOR_WALK = 50
        private val SHULCHAN_ARUCH_EXCLUDED_PREFIXES = listOf("הקדמה", "פרי מגדים")

        private val mutex = Mutex()

        @Volatile
        private var cached: Pair<String, CatalogAccess>? = null

        /** The catalog of the installed library, read once (desktop CatalogCache): off the main thread. */
        suspend fun load(store: LibraryStore): CatalogAccess = mutex.withLock {
            val catalogFile = store.catalogFile
            val stamp = "${catalogFile.path}:${catalogFile.length()}:${catalogFile.lastModified()}"
            cached?.takeIf { it.first == stamp }?.let { return@withLock it.second }
            val access = withContext(Dispatchers.IO) {
                val catalog = runCatching {
                    if (catalogFile.isFile) ProtoBuf.decodeFromByteArray(PrecomputedCatalog.serializer(), catalogFile.readBytes()) else null
                }.onFailure { Log.w("CatalogAccess", "catalog.pb", it) }.getOrNull() ?: fromDatabase(store)
                CatalogAccess(catalog)
            }
            cached = stamp to access
            access
        }

        /** The catalog tree of the database, for a library without catalog.pb: categories, then each one's books in order. */
        private suspend fun fromDatabase(store: LibraryStore): PrecomputedCatalog {
            val repo = store.repository()
            val books = store.catalogBooks()
            suspend fun category(id: Long, title: String, level: Int, parentId: Long?): CatalogCategory {
                val children = repo.getCategoryChildren(id).map { category(it.id, it.title, it.level, it.parentId) }
                return CatalogCategory(id, title, level, parentId, books[id].orEmpty(), children)
            }
            return PrecomputedCatalog(repo.getRootCategories().map { category(it.id, it.title, it.level, it.parentId) })
        }
    }
}
