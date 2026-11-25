package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Pardes (request 102 of docs/DESKTOP_STAGE3_PLAN.md: "במקום הדגשה כבדה של הטקסט כמו היום יוכלו להגדיר בהגדרות שיהיה פס
// צידי כמו בתצוגת טקסט, והאם בחירה של הספר הראשי תדגיש את המפרשים המקושרים והאם בהדגשה כמו היום או גם בפס צידי"): what a
// page marks behind its words and what by a bar at its lines' side, as the reader set it, on DafLinksTest's small page.
class DafSelectionMarksTest {
    private val gemara = 100L
    private val first = 200L
    private val second = 201L
    private val color = Color.Blue

    // A Gemara line, and a line of Rashi with the end of one comment and the start of the next
    private val amud =
        DafAmud(
            amud = "2a",
            page = listOf(600f, 850f),
            block = listOf(0f, 0f, 100f, 100f),
            lines =
                listOf(
                    DafLine("G", listOf(0f, 0f, 100f, 10f), "הקטר חלבים ואברים מצותן", u = listOf(listOf(gemara, 0, 3))),
                    DafLine(
                        "R",
                        listOf(0f, 20f, 100f, 28f),
                        "הקטר חלבים ואברים. של קרבנות מצותן. להעלות",
                        d = listOf(0, 1, 2, 5),
                        u = listOf(listOf(first, 0, 4), listOf(second, 5, 6)),
                    ),
                ),
        )
    private val links = DafAmudLinks(amud, mapOf(first to setOf(gemara), second to setOf(gemara)), mapOf(first to 7L, second to 7L))

    @Test
    fun `as it always was, everything is highlighted and nothing has a bar`() {
        val how = DafSelectionMarks()
        assertSame(DafHighlight.NONE, dafStripe(links, setOf(gemara), emptySet(), color, how))
        val highlight = dafHighlight(links, setOf(gemara), emptySet(), color, how)
        assertNotNull(highlight.colorOf(DafWord(0, 0)), "the selected segment")
        assertNotNull(highlight.colorOf(DafWord(1, 0)), "a comment on it")
    }

    @Test
    fun `a bar beside the selection takes its highlight's place`() {
        val how = DafSelectionMarks(selection = SelectionMark.Stripe)
        val highlight = dafHighlight(links, setOf(gemara), emptySet(), color, how)
        val stripe = dafStripe(links, setOf(gemara), emptySet(), color, how)
        assertNull(highlight.colorOf(DafWord(0, 0)))
        assertEquals(color, stripe.colorOf(DafWord(0, 0)))
        assertNotNull(highlight.colorOf(DafWord(1, 0)), "the comments on it are highlighted still")
        assertNull(stripe.colorOf(DafWord(1, 0)))
    }

    @Test
    fun `a comment clicked itself is the selection, and the words it quotes are always highlighted`() {
        val how = DafSelectionMarks(selection = SelectionMark.Stripe)
        val highlight = dafHighlight(links, setOf(gemara), setOf(second), color, how)
        val stripe = dafStripe(links, setOf(gemara), setOf(second), color, how)
        assertEquals(color, stripe.colorOf(DafWord(1, 6)), "the comment has the selection's bar")
        assertNull(highlight.colorOf(DafWord(1, 6)))
        assertNotNull(highlight.colorOf(DafWord(0, 3)), "the word of the text it quotes: a bar is a whole line's")
    }

    @Test
    fun `the comments on the selection, by a lighter bar or not at all`() {
        val barred = DafSelectionMarks(comments = SelectionMark.Stripe)
        val stripe = dafStripe(links, setOf(gemara), emptySet(), color, barred)
        val related = assertNotNull(stripe.colorOf(DafWord(1, 0)))
        assertTrue(related.alpha < color.alpha)
        assertNull(stripe.colorOf(DafWord(0, 0)), "the selection itself is highlighted")
        assertNull(dafHighlight(links, setOf(gemara), emptySet(), color, barred).colorOf(DafWord(1, 0)))

        val unmarked = DafSelectionMarks(comments = null)
        assertSame(DafHighlight.NONE, dafStripe(links, setOf(gemara), emptySet(), color, unmarked))
        val highlight = dafHighlight(links, setOf(gemara), emptySet(), color, unmarked)
        assertNull(highlight.colorOf(DafWord(1, 0)))
        assertNotNull(highlight.colorOf(DafWord(0, 0)))
    }

    @Test
    fun `nothing selected, no bar`() {
        val how = DafSelectionMarks(SelectionMark.Stripe, SelectionMark.Stripe)
        assertSame(DafHighlight.NONE, dafStripe(links, emptySet(), emptySet(), color, how))
        assertSame(DafHighlight.NONE, dafStripe(null, setOf(gemara), emptySet(), color, how))
    }

    // A column of four lines of two words each, a place left empty before the last
    private fun run(): TextRun {
        fun line(
            index: Int,
            indent: Float = 0f,
        ) = RunLine(index, listOf("אבג", "דהו"), bold = emptySet(), size = 10f, indent = indent, extra = 0f, unsure = false)
        return TextRun(
            column = "R",
            left = 0f,
            top = 0f,
            width = 100f,
            pitch = 12f,
            size = 10f,
            isGemara = false,
            isBig = false,
            lines = listOf(line(0), line(1), line(2, indent = 8f), null, line(4)),
        )
    }

    @Test
    fun `lines one below the other share a bar`() {
        val run = run()
        val light = color.copy(alpha = 0.4f)
        // A bar on the second line's second word, the third line's first and the last line; a lighter one too on the third
        val stripes =
            stripesOf(run) { word ->
                when {
                    word == DafWord(1, 1) || word == DafWord(2, 0) || word.line == 4 -> color
                    word == DafWord(2, 1) -> light
                    else -> null
                }
            }
        assertEquals(2, stripes.size)
        val (shared, alone) = stripes
        assertEquals(run.rangeOf(1, 0).first, shared.first, "from the first line with a word of it, whole")
        assertEquals(run.rangeOf(2, 0).first, shared.last, "a line set in shares the bar: it is straight")
        assertEquals(color, shared.color, "a line's strongest bar")
        assertEquals(run.rangeOf(4, 0).first, alone.first, "an empty place between two lines parts their bars")
    }

    @Test
    fun `a lighter bar under a stronger one is another bar`() {
        val light = color.copy(alpha = 0.4f)
        val stripes = stripesOf(run()) { word -> if (word.line == 0) color else light.takeIf { word.line == 1 } }
        assertEquals(listOf(color, light), stripes.map { it.color })
    }

    @Test
    fun `a paragraph's short last line in the middle has its bar at the paragraph's edge`() {
        fun run(
            column: String,
            left: Float,
            top: Float,
            width: Float,
            lines: Int,
        ) = TextRun(column, left, top, width, pitch = 12f, size = 10f, isGemara = false, isBig = false, lines = List(lines) { null })
        val paragraph = run("A", left = 100f, top = 0f, width = 200f, lines = 3)
        val last = run("A", left = 160f, top = 36f, width = 80f, lines = 1)
        val narrower = run("A", left = 100f, top = 48f, width = 120f, lines = 4)
        val other = run("B", left = 100f, top = 36f, width = 80f, lines = 1)
        val edges = stripeEdges(listOf(paragraph, last, narrower, other))
        assertEquals(300f, edges[0])
        assertEquals(300f, edges[1], "the line in the middle, under its paragraph")
        assertEquals(220f, edges[2], "lines set to one side are a column's shape: their own edge")
        assertEquals(180f, edges[3], "another column's line")
    }

    @Test
    fun `no bar, no work`() {
        assertTrue(stripesOf(run(), DafHighlight.NONE).isEmpty())
        assertTrue(stripesOf(run()) { null }.isEmpty())
    }
}
