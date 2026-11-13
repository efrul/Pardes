package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Chumash's pages of Mikraot Gedolot: verses, Onkelos and commentaries set as a printed edition does. */
class MikraotGedolotTest {
    // Every letter 4 points wide in square letters and 3 in Rashi script, a space 2
    private val widths =
        object : WordWidths {
            override fun of(
                word: String,
                square: Boolean,
            ) = word.length * if (square) 4f else 3f

            override fun space(square: Boolean) = 2f
        }

    private val rashi = MikraotCommentator(1, "רש\"י")
    private val ramban = MikraotCommentator(2, "רמב\"ן")
    private val half = MIKRAOT_PAGE_WIDTH / 2

    private fun verse(
        id: Long,
        comments: Map<Long, MikraotComment>,
    ) = MikraotVerse(id, "בראשית א, $id", List(12) { "בראשית" }, List(10) { "בקדמין" }, comments)

    private fun DafLine.fits(scale: Float): Boolean {
        val words = t.split(' ')
        val square = c == "G"
        val width = (words.sumOf { widths.of(it, square).toDouble() }.toFloat() + widths.space(square) * (words.size - 1)) * scale
        return width <= b[2] - b[0] + 0.01f
    }

    @Test
    fun `short comments share a page with their verses, each verse at the top of its part`() {
        val short = MikraotComment(List(40) { "מילה" }, bold = setOf(0))
        val verses = (1L..3L).map { verse(it, mapOf(1L to short, 2L to short)) }
        val pages = mikraotPages(verses, listOf(rashi, ramban), widths)
        assertEquals(1, pages.size)
        val page = pages.single()
        assertEquals(listOf(1L, 2L, 3L), page.verseIds)
        assertEquals("בראשית א, 1–3", page.head)
        // The verses on the right, in big letters; Onkelos on the left under its name; the commentaries in two columns
        assertTrue(page.verse.lines.all { it.c == "G" && it.b[0] >= MIKRAOT_PAGE_WIDTH * 0.38f - 0.01f && it.fits(VERSE_SCALE) })
        assertTrue(page.targum.lines.all { it.b[2] < MIKRAOT_PAGE_WIDTH * 0.38f && it.fits(1f) })
        assertEquals(listOf("אונקלוס", "רש\"י", "רמב\"ן"), page.headings.take(3).map { it.text })
        assertTrue(page.comments.lines.all { (it.b[0] > half || it.b[2] < half) && it.fits(1f) })
        assertEquals(
            listOf(0),
            page.comments.lines
                .first()
                .d,
        )
        // Read down the page: each verse below the comments of the one before it
        val secondVerse =
            page.verse.lines.first {
                it.b[1] > page.verse.lines
                    .first()
                    .b[1] + 30
            }
        assertTrue(page.comments.lines.any { it.b[1] < secondVerse.b[1] })
    }

    @Test
    fun `a long comment runs on to the next pages, in two columns, and the next verse follows it`() {
        val long = MikraotComment(List(3000) { "מילה" })
        val pages = mikraotPages(listOf(verse(1, mapOf(1L to long)), verse(2, emptyMap())), listOf(rashi), widths)
        assertTrue(pages.size > 2)
        assertEquals(listOf(1L), pages.first().verseIds)
        // A page that only goes on with the verse's comments is headed with it, as a book's: "א, א"
        assertEquals("בראשית א, 1", pages[1].head)
        assertEquals("בראשית א, 1–2", pages.last().head)
        // Its name once, on its first page; the pages between full in both columns, within the page
        assertEquals(1, pages.sumOf { p -> p.headings.count { it.text == rashi.title } })
        val middle = pages[1]
        assertTrue(middle.comments.lines.count { it.b[0] > half } == middle.comments.lines.count { it.b[2] < half })
        assertTrue(pages.all { p -> p.comments.lines.all { it.b[3] <= MIKRAOT_PAGE_HEIGHT && it.b[1] >= 0f } })
        // Balanced on its last page, the right column taking the odd line; then the next verse
        val last = pages.last()
        assertEquals(listOf(2L), last.verseIds)
        val right = last.comments.lines.count { it.b[0] > half }
        val left = last.comments.lines.count { it.b[2] < half }
        assertTrue(right - left in 0..1)
    }

    @Test
    fun `a comment's words keep its bold dibbur`() {
        assertEquals(
            listOf("קטורה.", "זו", "הגר,", "ונקראת") to setOf(0),
            htmlWords("<b>קטורה.</b> זו הגר, <span>ונקראת</span>"),
        )
    }
}
