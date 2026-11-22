package io.github.kdroidfilter.seforimapp.features.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The point under the pointer kept as pages zoom (request 44): across a row, and down a list of titled pages. */
class ZoomPivotTest {
    private val page = listOf(RowPart(400f, scales = true))
    private val spread = listOf(RowPart(400f, scales = true), RowPart(8f, scales = false), RowPart(400f, scales = true))

    @Test
    fun `along a row, a page's parts scale and a gap does not`() {
        assertEquals(200f, alongScaled(100f, page, 2f))
        assertEquals(804f, alongScaled(404f, spread, 2f), "in the gap: after the first page, doubled")
        assertEquals(1008f, alongScaled(508f, spread, 2f), "in the second page, after the gap as it is")
        assertEquals(-20f, alongScaled(-20f, page, 2f), "a margin before the row")
        assertEquals(830f, alongScaled(430f, page, 2f), "a margin after it")
    }

    @Test
    fun `sideways, the point under the pointer stays as a page grows past the view, from the left or the right`() {
        // A 400 px page centered in a 1000 px view (300 px each side), the pointer at 600: 300 px into the page, which
        // is 1500 px into it five times as wide, a list of 2000: scrolled by 900, they are under the pointer again
        val along = alongAt(scroll = 0, pointer = 600f, view = 1000f, parts = page)
        assertEquals(300f, along)
        assertEquals(900 to 1500f, sidewaysAnchored(along, pointer = 600f, view = 1000f, parts = page, scale = 5f))
        // And back: 1500 px into the wide page, at the pointer, is 300 px into the page centered again
        val wide = listOf(RowPart(2000f, scales = true))
        assertEquals(0 to 300f, sidewaysAnchored(alongAt(900, 600f, 1000f, wide), pointer = 600f, view = 1000f, parts = wide, scale = 0.2f))
        // A place the page's start would have to pass is as near as the list allows, and kept exact for the next frames
        assertEquals(0 to 300f, sidewaysAnchored(100f, pointer = 400f, view = 1000f, parts = page, scale = 3f))
        // The end of the list bounds it
        assertEquals(200, sidewaysAnchored(alongAt(0, 990f, 1000f, page), pointer = 990f, view = 1000f, parts = page, scale = 3f).first)
    }

    @Test
    fun `down, the point in a page stays, a title and the gap after do not grow`() {
        val items = listOf(ListItem(index = 3, offset = -100, size = 536), ListItem(index = 4, offset = 444, size = 536))

        // 300 px down: 400 px into item 3, past its 36 px title, so 36 + 364 × 2 = 764 into it at twice the size
        val (item, offset) = downAnchored(items, down = 300f, fixed = 36f, scale = 2f)!!
        assertEquals(3, item)
        assertEquals(764f - 300f, offset)
        // In item 4's title, which does not grow
        assertEquals(4 to (10f - 454f), downAnchored(items, down = 454f, fixed = 36f, scale = 2f))
        // In the gap after item 3 (436 to 444): after its page, grown, the gap as it is
        assertEquals(3 to (36f + 500f * 2f + 4f - 440f), downAnchored(items, down = 440f, fixed = 36f, scale = 2f))
        assertNull(downAnchored(items, down = -200f, fixed = 36f, scale = 2f), "above the items laid out")
    }

    @Test
    fun `a zoom in and out again comes back to the same place`() {
        val items = listOf(ListItem(index = 0, offset = -250, size = 1036))
        val (_, there) = downAnchored(items, down = 300f, fixed = 36f, scale = 2f)!!
        // Laid out at twice the size, item 0 begins at -there; back to its size
        val back = downAnchored(listOf(ListItem(0, -there.toInt(), 2036)), down = 300f, fixed = 36f, scale = 0.5f)!!
        assertEquals(250f, back.second, 1f)
    }
}
