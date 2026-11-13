package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a book's daf reads from the library: its units, and its lines' words as it prints them. */
class BookDafSourceTest {
    @Test
    fun `units run from each entry of the table of contents to the next`() {
        // The lines before the first entry (a book's title and author) are a unit of their own
        assertEquals(listOf(DafUnit(0, 2), DafUnit(2, 9), DafUnit(9, 11), DafUnit(11, 20)), dafUnits(listOf(2, 9, 11), 20))
    }

    @Test
    fun `a heading alone goes with the unit after it`() {
        // The book's title at 0, its first chapter at 1
        assertEquals(listOf(DafUnit(0, 33), DafUnit(33, 60)), dafUnits(listOf(0, 1, 33), 60))
    }

    @Test
    fun `a long part is laid out in pieces`() {
        val units = dafUnits(listOf(0), MOST_UNIT_LINES * 2 + 5)
        assertEquals(listOf(DafUnit(0, 40), DafUnit(40, 80), DafUnit(80, 85)), units)
    }

    @Test
    fun `a book without a table of contents is laid out in pieces from its start`() {
        assertEquals(listOf(DafUnit(0, 12)), dafUnits(emptyList(), 12))
    }

    @Test
    fun `words are the text's, joined where its tags part no word, bold where most of their letters are`() {
        val words = dafWords("(א) <big>בְּ</big>רֵאשִׁית <b>בָּרָא אֱלֹהִים</b> אֵת", DiacriticsMode.All)
        assertEquals(listOf("(א)", "בְּרֵאשִׁית", "בָּרָא", "אֱלֹהִים", "אֵת"), words.words)
        assertEquals(setOf(2, 3), words.bold)
    }

    @Test
    fun `the diacritics the text hides are hidden`() {
        val words = dafWords("בְּרֵאשִׁ֖ית בָּרָ֣א", DiacriticsMode.None)
        assertEquals(listOf("בראשית", "ברא"), words.words)
    }

    @Test
    fun `an anchor begins a passage that runs to the next one`() {
        val html =
            """(ד) <i data-commentator="Siftei Kohen" data-order="10"></i>אדם שחבל בחבירו """ +
                """<i data-commentator="Ketzot HaChoshen" data-order="4"></i>אין מגבין דיינים"""
        val words = dafWords(html, DiacriticsMode.All, setOf("Siftei Kohen", "Ketzot HaChoshen"))
        assertEquals(listOf("(ד)", "אדם", "שחבל", "בחבירו", "אין", "מגבין", "דיינים"), words.words)
        assertEquals(
            listOf(1..3 to setOf(CommentAnchor("Siftei Kohen", 10)), 4..6 to setOf(CommentAnchor("Ketzot HaChoshen", 4))),
            words.passages,
        )
    }

    @Test
    fun `a heading is told by its tag`() {
        assertTrue(isHeading("<h2>פרק א</h2>"))
        assertFalse(isHeading("(א) בראשית ברא"))
    }
}
