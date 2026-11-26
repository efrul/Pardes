package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals

// Pardes (request 89 of docs/DESKTOP_STAGE3_PLAN.md, pages that follow their view's width): how they share the view
// (BookDafSetup's wideFit). The user's word on the testers' build, of two pages a pane opened beside them had cut: "למה
// ככה הוא לא מתאים את עצמו?": each kept the least page's width, 240 points, where half the view was narrower.
class BookDafWideFitTest {
    @Test
    fun `two pages a row where each has the least page's room`() {
        // Half of 1764 pixels less its padding is 834: 240 points at 2.7 pixels a point are 648
        val fit = wideFit(width = 1_764f, perPoint = 2.7f, spreads = true, padding = 24f)
        assertEquals(2, fit.columns)
        assertEquals(2.7f, fit.perPoint)
    }

    @Test
    fun `one page a row where two would be cut by the view`() {
        // The view of the user's picture, a pane open beside the pages: half of 1282 less its padding is 593
        val fit = wideFit(width = 1_282f, perPoint = 2.7f, spreads = true, padding = 24f)
        assertEquals(1, fit.columns)
        assertEquals(2.7f, fit.perPoint, "the letters as the zoom has them: one page has the room")
    }

    @Test
    fun `one page a row without the spread, whatever the room`() {
        assertEquals(1, wideFit(width = 3_000f, perPoint = 1f, spreads = false, padding = 24f).columns)
    }

    @Test
    fun `smaller letters where the view is narrower than the least page, as little as it must`() {
        val fit = wideFit(width = 528f, perPoint = 2.7f, spreads = true, padding = 24f)
        assertEquals(1, fit.columns)
        assertEquals(2f, fit.perPoint, "480 pixels of room for 240 points")
    }
}
