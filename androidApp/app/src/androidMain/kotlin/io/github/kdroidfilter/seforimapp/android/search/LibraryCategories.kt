package io.github.kdroidfilter.seforimapp.android.search

import android.util.Log
import app.cash.sqldelight.db.QueryResult
import io.github.kdroidfilter.seforimapp.android.data.CatalogAccess
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.features.search.domain.BaseBooks
import io.github.kdroidfilter.seforimapp.features.search.domain.CategoryHit
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Port of Pardes desktop's features/search/domain/LibraryCategories.kt: the library's categories, for the search, each
 * with its path (what the bar suggests) and the books it searches, all those under it or its base books alone (the
 * texts without their commentaries: תלמוד's tractates, the מהר״ל's books without the notes on them; desktop's
 * BaseBooks, compiled unchanged, tells them apart).
 *
 * Read once for the app, every tab, from the library's catalog ([CatalogAccess.readCatalog]: catalog.pb, or the
 * database's tree without it) and its commentary links (one query), and again when the library changes: a category's
 * books cost no query.
 */
class LibraryCategories private constructor(private val entries: Map<Long, Entry>) {
    // One category: as the bar shows it, and the books under it (its subcategories' too)
    private class Entry(val hit: CategoryHit, val bookIds: LongArray, val baseBookIds: LongArray)

    /** Every category, in the library's order (a parent before its subcategories), with its path. */
    fun categories(): List<CategoryHit> = entries.values.map { it.hit }

    /** [categoryId] as the bar shows it, whole or [baseBooksOnly]; null for a category the library doesn't have. */
    fun hit(categoryId: Long, baseBooksOnly: Boolean = false): CategoryHit? = entries[categoryId]?.hit?.copy(baseBooksOnly = baseBooksOnly)

    /** Whether [categoryId] holds base books and others: in its base books alone it is another search. */
    fun hasBaseBooksAndOthers(categoryId: Long): Boolean = entries[categoryId]?.hit?.hasBaseBooksAndOthers == true

    /**
     * The books a search in [categoryId] runs in: all those under it (its subcategories' too), or [baseBooksOnly] its
     * base books alone. Empty for a category the library doesn't have.
     */
    fun bookIds(categoryId: Long, baseBooksOnly: Boolean = false): List<Long> =
        entries[categoryId]?.let { (if (baseBooksOnly) it.baseBookIds else it.bookIds).asList() }.orEmpty()

    companion object {
        private val mutex = Mutex()

        @Volatile
        private var cached: Pair<String, LibraryCategories>? = null

        /** The installed library's categories, read once: off the main thread. */
        suspend fun load(store: LibraryStore): LibraryCategories = mutex.withLock {
            val stamp = CatalogAccess.catalogStamp(store)
            cached?.takeIf { it.first == stamp }?.let { return@withLock it.second }
            val catalog = CatalogAccess.readCatalog(store)
            val commented = withContext(Dispatchers.IO) { commentaries(store) }
            val built = withContext(Dispatchers.Default) { of(catalog, commented) }
            if (built.entries.isNotEmpty()) cached = stamp to built
            built
        }

        /**
         * The categories of [catalog]; [commented] gives each commentary the books it comments on (desktop's
         * BaseBooks.COMMENTARY_PAIRS_SQL), which tells a category's base books apart where none is curated ([BaseBooks]).
         */
        fun of(catalog: PrecomputedCatalog, commented: Map<Long, LongArray> = emptyMap()): LibraryCategories {
            val books = BaseBooks.of(catalog.rootCategories, commented)
            val result = LinkedHashMap<Long, Entry>()

            // Lists [category] before its subcategories
            fun walk(category: CatalogCategory, parentPath: List<String>) {
                val path = parentPath + category.title
                val its = books[category.id]
                result[category.id] = Entry(
                    CategoryHit(category.id, path, hasBaseBooksAndOthers = its?.hasBaseBooksAndOthers == true),
                    its?.all ?: LongArray(0),
                    its?.base ?: LongArray(0),
                )
                category.subcategories.forEach { walk(it, path) }
            }
            catalog.rootCategories.forEach { walk(it, emptyList()) }
            return LibraryCategories(result)
        }

        /** The library's commentaries and the books they comment on, in one query; empty when it can't be read. */
        private fun commentaries(store: LibraryStore): Map<Long, LongArray> = runCatching {
            val pairs = HashMap<Long, MutableList<Long>>()
            store.driver().executeQuery(
                identifier = null,
                sql = BaseBooks.COMMENTARY_PAIRS_SQL,
                mapper = { cursor ->
                    while (cursor.next().value) pairs.getOrPut(cursor.getLong(0)!!) { ArrayList(2) } += cursor.getLong(1)!!
                    QueryResult.Unit
                },
                parameters = 0,
            )
            pairs.mapValues { it.value.toLongArray() }
        }.onFailure { Log.w("LibraryCategories", "commentaries", it) }.getOrDefault(emptyMap())
    }
}
