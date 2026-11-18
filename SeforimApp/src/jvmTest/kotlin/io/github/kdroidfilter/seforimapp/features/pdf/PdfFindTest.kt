package io.github.kdroidfilter.seforimapp.features.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A find in a PDF book's page (request 48): its text matched as a book's, and the boxes of each match. */
class PdfFindTest {
    // A page 100 by 200 points, its [lines] one under the other from its top, each letter 10 points wide; PDFium's line
    // break between them has no box
    private fun page(vararg lines: String): PageChars {
        val codepoints = ArrayList<Int>()
        val boxes = ArrayList<Float>()
        lines.forEachIndexed { row, line ->
            if (row > 0) {
                "\r\n".forEach {
                    codepoints += it.code
                    boxes += listOf(0f, 0f, 0f, 0f)
                }
            }
            val top = 190f - row * 20f
            line.forEachIndexed { i, letter ->
                codepoints += letter.code
                // Left, bottom, right, top, from the page's bottom left
                boxes += listOf(i * 10f, top - 10f, i * 10f + 10f, top)
            }
        }
        return letters(codepoints.toIntArray(), boxes.toFloatArray())
    }

    private fun letters(
        codepoints: IntArray,
        boxes: FloatArray,
    ) = object : PageChars {
        override val count = codepoints.size
        override val width = 100f
        override val height = 200f

        override fun codepoint(i: Int) = codepoints[i]

        override fun left(i: Int) = boxes[i * 4]

        override fun bottom(i: Int) = boxes[i * 4 + 1]

        override fun right(i: Int) = boxes[i * 4 + 2]

        override fun top(i: Int) = boxes[i * 4 + 3]
    }

    @Test
    fun `a word is found on its line, its letters' boxes joined as fractions of the page from its top left`() {
        val matches = pageMatches(page("שלום עולם", "ברוך"), 0, "עולם")
        assertEquals(1, matches.size)
        val box = matches.single().boxes.single()
        assertEquals(0.5f, box.left, 0.001f)
        assertEquals(0.9f, box.right, 0.001f)
        assertEquals(0.05f, box.top, 0.001f)
        assertEquals(0.1f, box.bottom, 0.001f)
    }

    @Test
    fun `a match over two lines has a box on each, the line break left aside`() {
        val boxes = boxesOf(page("אב", "גד"), (0..5).toList())
        assertEquals(2, boxes.size)
        assertEquals(0.15f, boxes[1].top, 0.001f)
    }

    @Test
    fun `nikud and final letters aside, and a PDF that keeps its Hebrew backwards found too`() {
        assertEquals(1, pageMatches(page("שָׁלוֹם לכם"), 0, "שלום").size)
        assertEquals(1, pageMatches(page("ךורב"), 0, "ברוך").size, "the page's letters backwards")
        assertTrue(pageMatches(page("שלום"), 0, "ש").isEmpty(), "a letter alone is no find")
        assertTrue(
            pageMatches(letters(IntArray(0), FloatArray(0)), 0, "שלום").isEmpty(),
        )
    }
}
