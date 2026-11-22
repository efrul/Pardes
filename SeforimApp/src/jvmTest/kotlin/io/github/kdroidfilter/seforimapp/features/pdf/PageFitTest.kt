package io.github.kdroidfilter.seforimapp.features.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

/** A page as wide as the view at the zoom's 1, and a whole page in it when asked (request 63 of stage 3). */
class PageFitTest {
    @Test
    fun `a page is as wide as the view, with no widest`() {
        assertEquals(2400f, fittedPageWidth(2400f, 900f, aspect = 1.5f, whole = false))
    }

    @Test
    fun `a whole page is no wider than leaves it whole in the view`() {
        // A page half again as tall as wide, in a view 900 tall less 60 around it: 560 wide, 840 tall
        assertEquals(560f, fittedPageWidth(2400f, 900f, aspect = 1.5f, whole = true, around = 60f))
        assertEquals(400f, fittedPageWidth(400f, 900f, aspect = 1.5f, whole = true), "a narrow view: its width")
    }

    @Test
    fun `a page whose shape is not known yet is as wide as the view`() {
        assertEquals(800f, fittedPageWidth(800f, 600f, aspect = null, whole = true))
        assertEquals(800f, fittedPageWidth(800f, 600f, aspect = 0f, whole = true))
    }
}
