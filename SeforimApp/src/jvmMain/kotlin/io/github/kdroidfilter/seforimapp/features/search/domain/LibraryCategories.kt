package io.github.kdroidfilter.seforimapp.features.search.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The library's categories, for the search: each with its path (what the bar suggests) and the books it searches, all
 * those under it or its base books alone (the texts without their commentaries: תלמוד's tractates, the מהר״ל's books
 * without the notes on them; [BaseBooks] tells them apart).
 *
 * Read once for the app, every window and tab, from the catalog the library comes with (catalog.pb, in memory) and the
 * library's commentary links (one query, about a second): a category's books cost no query. Without a catalog, from the
 * database: the categories once, a category's books once each, and no category is offered in its base books alone.
 *
 * The commentaries on one book of one time (ראשונים על בבא בתרא, [CommentaryGroups]) are searched as categories are,
 * under ids of their own: [named] offers them, [hit] and [bookIds] know them.
 */
@Inject
@SingleIn(AppScope::class)
class LibraryCategories(
    private val catalogCache: CatalogCache,
    private val repository: SeforimRepository,
    private val commentaryLinks: CommentaryLinks,
) {
    // One category: as the bar shows it, and the books under it (its subcategories' too), null without a catalog
    private class Entry(
        val hit: CategoryHit,
        val bookIds: LongArray?,
        val baseBookIds: LongArray?,
    )

    // The categories in the library's order, a parent before its subcategories
    @Volatile
    private var entries: Map<Long, Entry>? = null
    private val mutex = Mutex()

    // The commentaries on a book by their time (CommentaryGroups), read with the categories; none without a catalog
    @Volatile
    private var groups: Map<Long, Entry> = emptyMap()

    // Without a catalog, the books of the categories searched so far: all of them, and the base ones
    private val booksFromDatabase = ConcurrentHashMap<Long, Pair<List<Long>, List<Long>>>()

    /** Every category, in the library's order, with its path. */
    suspend fun categories(): List<CategoryHit> = entries().values.map { it.hit }

    /**
     * What [query] names to search in, as typed in the bar: the commentaries on a book by their time
     * ([CommentaryGroups.match]: `ראשונים על בבא בתרא`), then the categories ([CategorySuggestions.match]), [limit] of
     * these at most.
     */
    suspend fun named(
        query: String,
        limit: Int,
    ): List<CategoryHit> {
        val categories = categories()
        return CommentaryGroups.match(query, groups.values.map { it.hit }, MAX_GROUPS) + CategorySuggestions.match(query, categories, limit)
    }

    /** [categoryId] as the bar shows it, whole or [baseBooksOnly]; null for a category the library doesn't have. */
    suspend fun hit(
        categoryId: Long,
        baseBooksOnly: Boolean = false,
    ): CategoryHit? = entry(categoryId)?.hit?.copy(baseBooksOnly = baseBooksOnly)

    /** Whether [categoryId] holds base books and others: in its base books alone it is another search. */
    suspend fun hasBaseBooksAndOthers(categoryId: Long): Boolean = entry(categoryId)?.hit?.hasBaseBooksAndOthers == true

    /**
     * The books a search in [categoryId] runs in: all those under it (its subcategories' too), or [baseBooksOnly] its
     * base books alone. Empty for a category the library doesn't have.
     */
    suspend fun bookIds(
        categoryId: Long,
        baseBooksOnly: Boolean = false,
    ): List<Long> {
        val entry = entry(categoryId) ?: return emptyList()
        if (entry.bookIds != null && entry.baseBookIds != null) {
            return (if (baseBooksOnly) entry.baseBookIds else entry.bookIds).asList()
        }
        val (all, base) =
            booksFromDatabase[categoryId] ?: run {
                val books = repository.getBooksUnderCategoryTree(categoryId)
                (books.map { it.id } to books.filter { it.isBaseBook }.map { it.id }).also { booksFromDatabase[categoryId] = it }
            }
        return if (baseBooksOnly) base else all
    }

    // A category of the library, or a group of commentaries searched as one
    private suspend fun entry(categoryId: Long): Entry? = entries()[categoryId] ?: groups[categoryId]

    private suspend fun entries(): Map<Long, Entry> =
        entries ?: mutex.withLock {
            entries ?: withContext(Dispatchers.IO) {
                val roots = catalogCache.getCatalog()?.rootCategories
                if (roots.isNullOrEmpty()) fromDatabase() else fromCatalog(roots)
            }.also { built -> if (built.isNotEmpty()) entries = built }
        }

    private fun fromCatalog(roots: List<CatalogCategory>): Map<Long, Entry> {
        val links = commentaryLinks.counted()
        val books = BaseBooks.of(roots, links.mapValues { (_, commented) -> commented.keys.toLongArray() })
        groups = CommentaryGroups.of(roots, links).associate { it.hit.id to Entry(it.hit, it.bookIds, it.bookIds) }
        val result = LinkedHashMap<Long, Entry>()

        // Lists [category] before its subcategories
        fun walk(
            category: CatalogCategory,
            parentPath: List<String>,
        ) {
            val path = parentPath + category.title
            val its = books[category.id]
            result[category.id] =
                Entry(CategoryHit(category.id, path, hasBaseBooksAndOthers = its?.hasBaseBooksAndOthers == true), its?.all, its?.base)
            category.subcategories.forEach { walk(it, path) }
        }
        roots.forEach { walk(it, emptyList()) }
        return result
    }

    private suspend fun fromDatabase(): Map<Long, Entry> {
        val result = LinkedHashMap<Long, Entry>()

        suspend fun walk(
            category: Category,
            parentPath: List<String>,
        ) {
            currentCoroutineContext().ensureActive()
            val path = parentPath + category.title
            result[category.id] = Entry(CategoryHit(category.id, path), null, null)
            repository.getCategoryChildren(category.id).forEach { walk(it, path) }
        }
        repository.getRootCategories().forEach { walk(it, emptyList()) }
        return result
    }

    private companion object {
        // The groups a text names are few: the book's commentaries of each time it names
        const val MAX_GROUPS = 3
    }
}
