package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): which of a unit's rows of pages are composed, the ones in the view or
// near it (BookDafPlaces' rowsIn): a chapter's hundreds of pages composed together held the window. And where the view
// rests down a row of pages, which the keys move it between, a page at a time (request 101 of
// docs/DESKTOP_STAGE3_PLAN.md: "בתצוגת ספר המעבר על ידי המקשים יהיה עמוד - עמוד ולא יהיה מצב כזה").
class BookDafPlacesTest {
    // Four rows of a hundred pixels each
    private val tops = floatArrayOf(0f, 100f, 200f, 300f, 400f)

    @Test
    fun `the rows in view alone`() {
        assertEquals(1..2, rowsIn(tops, 150f, 250f))
        assertEquals(0..0, rowsIn(tops, 0f, 100f), "a row that begins where the view ends is not in it")
        assertEquals(0..3, rowsIn(tops, -50f, 1_000f))
    }

    @Test
    fun `the nearest row when the view is past them all`() {
        assertEquals(3..3, rowsIn(tops, 900f, 1_200f))
        assertEquals(0..0, rowsIn(tops, -500f, -100f))
    }

    @Test
    fun `no row of an item without any`() {
        assertEquals(IntRange.EMPTY, rowsIn(floatArrayOf(0f), 0f, 100f))
    }

    @Test
    fun `a row no taller than the view is read from its top alone`() {
        assertEquals(listOf(300f), restsOf(top = 300f, bottom = 1_100f, view = 800f))
        assertEquals(listOf(300f), restsOf(top = 300f, bottom = 700f, view = 800f), "a short page: the next one shows under it")
        assertEquals(listOf(300f), restsOf(top = 300f, bottom = 1_101f, view = 800f), "a pixel's rounding is no step")
    }

    @Test
    fun `down a row taller than the view, a view at a time to its bottom`() {
        // 2000 pixels of pages in a view of 800: most of a view at a time, the last with the row's bottom at the view's
        assertEquals(listOf(0f, 720f, 1_200f), restsOf(top = 0f, bottom = 2_000f, view = 800f))
        assertEquals(listOf(100f, 420f), restsOf(top = 100f, bottom = 1_220f, view = 800f))
    }

    @Test
    fun `a line is read where the view has it whole`() {
        val rests = listOf(0f, 720f, 1_200f)
        assertEquals(0f, restFor(rests, y = 10f, view = 800f, room = 40f), "a page's first lines: from its top, with its head")
        assertEquals(0f, restFor(rests, y = 760f, view = 800f, room = 40f))
        assertEquals(720f, restFor(rests, y = 780f, view = 800f, room = 40f), "cut by the view's bottom: a view further")
        assertEquals(1_200f, restFor(rests, y = 1_990f, view = 800f, room = 40f), "the row's last line: its bottom at the view's")
    }

    @Test
    fun `the row a place down an item is in`() {
        // Three rows, each its head, its pages and ten pixels of room below them
        val rows = floatArrayOf(0f, 750f, 1_500f, 3_450f)
        assertEquals(0, rowAt(rows, gap = 10f, offset = 0f))
        assertEquals(0, rowAt(rows, gap = 10f, offset = 739f))
        assertEquals(1, rowAt(rows, gap = 10f, offset = 740f), "the room below a row's pages is the next row's")
        assertEquals(2, rowAt(rows, gap = 10f, offset = 9_000f), "past them all, the last")
        assertEquals(0, rowAt(floatArrayOf(0f), gap = 10f, offset = 50f), "an item with no row")
    }
}
