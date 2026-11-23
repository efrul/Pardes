package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): which of a unit's rows of pages are composed, the ones in the view or
// near it (BookDafPlaces' rowsIn): a chapter's hundreds of pages composed together held the window.
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
}
