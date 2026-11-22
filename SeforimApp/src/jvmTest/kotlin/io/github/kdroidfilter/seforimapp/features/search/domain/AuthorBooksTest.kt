package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `the author named as typed lists all his books, the base books first, and no other author does`() {
        val ramban = AuthorHit(21L, "רמב\"ן", 40, matchedByName = true)
        val commentary = Book(id = 1L, categoryId = 5L, sourceId = 1L, title = "רמב\"ן על בראשית")
        val base = Book(id = 2L, categoryId = 6L, sourceId = 1L, title = "תורת האדם", isBaseBook = true)
        val details =
            AuthorDetails(
                id = 21L,
                name = "רמב\"ן",
                birthYear = null,
                birthYearApprox = false,
                deathYear = null,
                deathYearApprox = false,
                era = null,
                aliases = listOf("נחמנידס"),
                bio = null,
                books = listOf(commentary, base),
            )
        val found = { _: String -> listOf(ramban) }

        assertEquals(listOf(base, commentary), runBlocking { booksOfAuthorNamed("רמבן", found) { details } })
        assertEquals(listOf(base, commentary), runBlocking { booksOfAuthorNamed("נחמנידס", found) { details } }, "by an alias")
        assertEquals(emptyList(), runBlocking { booksOfAuthorNamed("רמב", found) { details } }, "a name only begun")
        assertEquals(emptyList(), runBlocking { booksOfAuthorNamed(" ", found) { details } })
    }
}
