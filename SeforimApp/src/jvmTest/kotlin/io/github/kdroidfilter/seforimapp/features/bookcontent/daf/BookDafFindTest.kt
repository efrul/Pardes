package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.features.bookcontent.find.foundLines
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.BookFindMatches
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The find on a book's own pages with the commentators shown on them: its matches in the order the pages are read. */
class BookDafFindTest {
    // The text's lines 3 and 10 found, the second twice
    private val text = BookFindMatches(intArrayOf(3, 10), longArrayOf(103, 110), intArrayOf(0, 1, 3))

    // Each comment's line of the text, by its index; 1003 is a comment on another book
    private val bases = mapOf(1001L to 10, 1002L to 3, 2001L to 3, 2002L to 3)

    private suspend fun found(vararg books: Pair<Long, List<Pair<Long, Int>>>) =
        withBookComments(
            text,
            books.map {
                it.first
            },
            { ids -> bases.filterKeys { it in ids } },
        ) { book -> books.first { it.first == book }.second }

    @Test
    fun `a line of the text is followed by the comments on it, a commentator after another`() =
        runTest {
            val found = found(1L to listOf(1001L to 1, 1002L to 2, 1003L to 5), 2L to listOf(2001L to 1, 2002L to 1))
            assertEquals(listOf(103L, 1002L, 2001L, 2002L, 110L, 1001L), found.foundLines().map { it.lineId })
            assertEquals(listOf(1, 2, 1, 1, 2, 1), found.foundLines().map { it.count })
            assertEquals(8, found.occurrences, "a comment on another book is none of them")
            assertEquals(found.lineIds.indices.toList(), found.foundLines().map { it.lineIndex }, "their places in that order")
        }

    @Test
    fun `with nothing found in the commentators the text's matches are as they were`() =
        runTest {
            assertSame(text, found(1L to listOf(1003L to 5), 2L to emptyList()))
            assertSame(text, found())
        }
}
