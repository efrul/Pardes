package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import java.util.concurrent.CompletableFuture

/** A search whose facets are counted: [SearchFacets]' search, as `SearchEngine.computeFacets` takes it. */
internal data class FacetsKey(
    val query: String,
    val near: Int,
    val bookFilter: Long? = null,
    val categoryFilter: Long? = null,
    val bookIds: Set<Long>? = null,
    val lineIds: Set<Long>? = null,
    val baseBookOnly: Boolean = false,
) {
    /**
     * Whether [other] searches some of this search's books and nothing else differs: its counts by book are then this
     * search's, of its books. Only of searches narrowed by books alone, or not at all.
     */
    fun holds(other: FacetsKey): Boolean {
        val same = query == other.query && near == other.near && baseBookOnly == other.baseBookOnly
        val wider = bookIds == null || (other.bookIds != null && bookIds.containsAll(other.bookIds))
        return same && byBooksAlone() && other.byBooksAlone() && wider
    }

    private fun byBooksAlone() = bookFilter == null && categoryFilter == null && lineIds == null
}

/**
 * The facets of a search tab's last few searches, each counted once: a search in the library's order opens its list as
 * the view model counts its facets, and the list asks for them too; a tab of it asks for those of some of the same
 * books, which the whole search's counts hold. They were two thirds of the list's opening (docs/scope-search-speed).
 */
internal class RecentFacets(
    private val size: Int = SIZE,
) {
    private class Counted(
        val key: FacetsKey,
        val facets: CompletableFuture<SearchFacets?>,
    )

    // The latest first
    private val recent = ArrayDeque<Counted>()

    /** [key]'s facets: counted by [count], or awaited while another counts them, or those counted lately. */
    fun get(
        key: FacetsKey,
        count: () -> SearchFacets?,
    ): SearchFacets? {
        var mine = false
        val counted =
            synchronized(recent) {
                recent.firstOrNull { it.key == key } ?: Counted(key, CompletableFuture()).also {
                    recent.addFirst(it)
                    if (recent.size > size) recent.removeLast()
                    mine = true
                }
            }
        if (!mine) return counted.facets.join()
        val result = runCatching(count)
        // A count that failed is not kept: the next one counts again
        if (result.isFailure) synchronized(recent) { recent.remove(counted) }
        counted.facets.complete(result.getOrNull())
        return result.getOrThrow()
    }

    /** The counts by book of [key]'s books, in the facets of a recent search that holds them; null when none does. */
    fun bookCounts(key: FacetsKey): Map<Long, Int>? {
        val counted = synchronized(recent) { recent.firstOrNull { it.key.holds(key) } } ?: return null
        val facets = counted.facets.join() ?: return null
        val books = key.bookIds ?: return facets.bookCounts
        return facets.bookCounts.filterKeys { it in books }
    }

    companion object {
        // A search's, its tabs' and its list's in the library's order
        const val SIZE = 4
    }
}
