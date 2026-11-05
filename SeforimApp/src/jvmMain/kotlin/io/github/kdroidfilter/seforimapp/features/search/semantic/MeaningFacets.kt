package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets

/**
 * A search session whose list holds lines its engine's facets don't count: [HybridSearchEngine]'s, whose facets are
 * its words' hits (SearchEngine.computeFacets) while its list adds the lines found by meaning alone. Without them the
 * category tabs miss every category only the meaning search found lines in.
 */
interface MeaningFacets {
    /**
     * Book id to how many lines of it the list holds that the words don't find (the meaning search's own), once the
     * first page is read; empty before it, and without a meaning search.
     */
    suspend fun meaningBookCounts(): Map<Long, Int>
}

/**
 * [this] with [books] (book id to lines) counted too: each book's lines added to it, to its category and every category
 * above it ([categoryPath]: their ids, the root first; null for a book that can't be read, left out), and to the total.
 * The facets then count every line the list shows, for the category tabs and the "more" counts.
 */
suspend fun SearchFacets.plusBooks(
    books: Map<Long, Int>,
    categoryPath: suspend (bookId: Long) -> List<Long>?,
): SearchFacets {
    if (books.isEmpty()) return this
    val bookCounts = bookCounts.toMutableMap()
    val categoryCounts = categoryCounts.toMutableMap()
    var added = 0L
    for ((bookId, count) in books) {
        if (count <= 0) continue
        val path = categoryPath(bookId) ?: continue
        bookCounts.merge(bookId, count, Int::plus)
        path.distinct().forEach { categoryCounts.merge(it, count, Int::plus) }
        added += count
    }
    return SearchFacets(totalHits = totalHits + added, categoryCounts = categoryCounts, bookCounts = bookCounts)
}

/**
 * The library's top categories ([roots]) offered as tabs after those of the results, in the library's order, when a
 * search runs by meaning in the whole library: its list holds only the first lines found (the fused candidates), while a
 * category searched alone finds its own closest lines, among them or not. In the base books alone ([baseBooksOnly]),
 * only the categories that hold some ([hasBaseBooks]): in the others that search finds nothing.
 */
suspend fun otherTabCategories(
    roots: List<Category>,
    baseBooksOnly: Boolean,
    hasBaseBooks: suspend (categoryId: Long) -> Boolean,
): List<Category> = roots.sortedBy { it.order }.filter { !baseBooksOnly || hasBaseBooks(it.id) }
