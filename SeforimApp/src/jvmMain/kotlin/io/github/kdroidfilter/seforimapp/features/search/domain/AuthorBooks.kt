package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails

/**
 * The authors found by name, each with the book of [ofBooks] they wrote: their row then comes beside the book's and
 * says so (`בן איש חי`: הרב יוסף חיים, the book's author). One whose name is [query] itself is offered for the name
 * alone (`רמבם`).
 */
internal fun List<AuthorHit>.withTheirBooks(
    ofBooks: List<AuthorHit>,
    query: String,
): List<AuthorHit> {
    val key = query.searchKey()
    val written = ofBooks.groupBy({ it.id }, { it.ofBook })
    return map { hit ->
        val named = hit.name.searchKey() == key || AuthorNames.display(hit.name).searchKey() == key
        if (named) hit else hit.copy(ofBook = written[hit.id]?.firstOrNull())
    }
}

/**
 * The books of the author [query] names exactly (by their name, the name as shown or an alias: `רמבן`, `רשי`), all of
 * them, the base books first, the author found among [authors] (those the name may be) and read by [details]; none
 * when it names no author. Request 41 of docs/DESKTOP_STAGE3_PLAN.md: the suggestions list them after the folders of
 * the author's name, not only those folders.
 */
internal suspend fun booksOfAuthorNamed(
    query: String,
    authors: suspend (String) -> List<AuthorHit>,
    details: suspend (Long) -> AuthorDetails?,
): List<Book> {
    val key = query.searchKey()
    if (key.isEmpty()) return emptyList()
    for (hit in authors(query)) {
        val author = details(hit.id) ?: continue
        val names = listOf(author.name, AuthorNames.display(author.name)) + author.aliases
        if (names.any { it.searchKey() == key }) return author.books.sortedByDescending { it.isBaseBook }
    }
    return emptyList()
}
