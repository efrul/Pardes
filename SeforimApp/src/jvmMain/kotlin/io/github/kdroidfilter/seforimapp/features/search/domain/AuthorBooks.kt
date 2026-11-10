package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit

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
