package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorBooksTest {
    private val yosefChaim = AuthorHit(1L, "יוסף חיים", 55, matchedByName = true)
    private val vital = AuthorHit(2L, "חיים ויטאל", 14, matchedByName = true)

    @Test
    fun `an author found by name keeps the book typed that he wrote`() {
        val ofBooks = listOf(yosefChaim.copy(ofBook = "בן איש חי"), yosefChaim.copy(ofBook = "בן איש חי, הלכות"))
        assertEquals(
            listOf(yosefChaim.copy(ofBook = "בן איש חי"), vital),
            listOf(yosefChaim, vital).withTheirBooks(ofBooks, "בן איש חי"),
        )
    }

    @Test
    fun `an author named as typed is offered for his name alone`() {
        val rambam = AuthorHit(11L, "רמב\"ם", 164, matchedByName = true)
        val rashi = AuthorHit(12L, "רש״י", 84, matchedByName = true)
        assertEquals(listOf(rambam), listOf(rambam).withTheirBooks(listOf(rambam.copy(ofBook = "רמב\"ם על משנה ברכות")), "רמבם"))
        assertEquals(listOf(rashi), listOf(rashi).withTheirBooks(listOf(rashi.copy(ofBook = "רש\"י על בראשית")), "רש\"י"))
    }
}
