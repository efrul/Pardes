package io.github.kdroidfilter.seforimapp.android.search

import io.github.kdroidfilter.seforimapp.android.data.CatalogAccess
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
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
 * texts without their commentaries: תלמוד's tractates, תורה's five books).
 *
 * Read once for the app, every tab, from the library's catalog ([CatalogAccess.readCatalog]: catalog.pb, or the
 * database's tree without it), and again when the library changes: a category's books cost no query.
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
            val built = withContext(Dispatchers.Default) { of(catalog) }
            if (built.entries.isNotEmpty()) cached = stamp to built
            built
        }

        /** The categories of [catalog]. */
        fun of(catalog: PrecomputedCatalog): LibraryCategories {
            val result = LinkedHashMap<Long, Entry>()

            // Lists [category] before its subcategories, and returns the books under it: all of them, and the base ones
            fun walk(category: CatalogCategory, parentPath: List<String>): Pair<LongArray, LongArray> {
                val path = parentPath + category.title
                // Its place, before its subcategories; filled once their books are known
                result[category.id] = Entry(CategoryHit(category.id, path), LongArray(0), LongArray(0))
                val all = ArrayList<Long>()
                val base = ArrayList<Long>()
                category.books.forEach { book ->
                    all += book.id
                    if (book.isBaseBook) base += book.id
                }
                category.subcategories.forEach { child ->
                    val (childAll, childBase) = walk(child, path)
                    childAll.forEach { all += it }
                    childBase.forEach { base += it }
                }
                val books = all.toLongArray() to base.toLongArray()
                val mixed = books.second.isNotEmpty() && books.second.size < books.first.size
                result[category.id] = Entry(CategoryHit(category.id, path, hasBaseBooksAndOthers = mixed), books.first, books.second)
                return books
            }
            catalog.rootCategories.forEach { walk(it, emptyList()) }
            return LibraryCategories(result)
        }
    }
}
