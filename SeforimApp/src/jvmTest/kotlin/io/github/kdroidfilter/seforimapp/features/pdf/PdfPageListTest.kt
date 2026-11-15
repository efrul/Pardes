package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.amudOfScanPage
import kotlin.test.Test
import kotlin.test.assertEquals

/** A PDF's pages in rows: one a row, or two as a book read right to left opens (a tractate's scan as printed). */
class PdfPageListTest {
    @Test
    fun `one page a row`() {
        assertEquals(5, rowCount(5, spreads = false))
        assertEquals(3, rowOf(3, spreads = false))
        assertEquals(3, firstPageOf(3, spreads = false))
    }

    @Test
    fun `two a row, the first page alone, then each odd page on the right of the next`() {
        // Pages 0 | 1 2 | 3 4 | 5
        assertEquals(4, rowCount(6, spreads = true))
        assertEquals(listOf(0, 1, 1, 2, 2, 3), (0 until 6).map { rowOf(it, spreads = true) })
        assertEquals(listOf(0, 1, 3, 5), (0 until 4).map { firstPageOf(it, spreads = true) })
        assertEquals(0, rowCount(0, spreads = true))
    }

    @Test
    fun `in a tractate's scan, amud b on the right and the next amud a on the left`() {
        // The row of page 1, 2b, has 3a on its left (page 2), as a Gemara opens
        val row = rowOf(1, spreads = true)
        val right = firstPageOf(row, spreads = true)

        assertEquals("2b", amudOfScanPage(right))
        assertEquals("3a", amudOfScanPage(right + 1))
        assertEquals("2a", amudOfScanPage(firstPageOf(0, spreads = true)))
    }
}
