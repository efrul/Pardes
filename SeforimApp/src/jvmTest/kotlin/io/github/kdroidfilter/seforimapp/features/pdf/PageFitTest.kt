package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pages' zoom a size, which no pane opened beside them changes, and the view's width and a whole page zooms it is set
 * to (requests 63 and 73 of stage 3).
 */
class PageFitTest {
    @Test
    fun `a page is as wide as the zoom says, whatever the view`() {
        assertEquals(1000f, pageWidthAt(1f, columns = 1))
        assertEquals(750f, pageWidthAt(1.5f, columns = 2), "two a row share the row")
        assertEquals(1500f, pageWidthAt(1f, columns = 1, one = 1500f), "in pixels")
    }

    @Test
    fun `the view's width is a zoom`() {
        // A view 2400 wide: a page as wide is a zoom of 2.4, and two of 1200 the same
        assertEquals(2.4f, pageFit(2400f, 900f, aspect = 1.5f, columns = 1).width)
        assertEquals(2.4f, pageFit(1200f, 900f, aspect = 1.5f, columns = 2).width)
    }

    @Test
    fun `a whole page is the zoom that leaves it whole in the view, and no wider than the view`() {
        // A page half again as tall as wide, in a view 900 tall less 60 around it: 560 wide, 840 tall
        assertEquals(0.56f, pageFit(2400f, 900f, aspect = 1.5f, columns = 1, around = 60f).whole, 1e-6f)
        // Two of them a row: a row 1120 wide
        assertEquals(1.12f, pageFit(1200f, 900f, aspect = 1.5f, columns = 2, around = 60f).whole, 1e-6f)
        assertEquals(0.4f, pageFit(400f, 900f, aspect = 1.5f, columns = 1).whole, "a narrow view: its width")
    }

    @Test
    fun `a page whose shape is not known yet fits as wide as the view`() {
        assertEquals(PageFit(0.8f, 0.8f), pageFit(800f, 600f, aspect = null, columns = 1))
        assertEquals(PageFit(0.8f, 0.8f), pageFit(800f, 600f, aspect = 0f, columns = 1))
    }

    @Test
    fun `a zoom is a whole page as near as the zoom's ends let it be`() {
        assertTrue(isWhole(0.56f, PageFit(2.4f, 0.56f)))
        assertFalse(isWhole(0.6f, PageFit(2.4f, 0.56f)))
        // A view so short that a whole page is below the zoom's least
        assertTrue(isWhole(AppSettings.MIN_DAF_ZOOM, PageFit(0.5f, 0.1f)))
    }

    @Test
    fun `a tab's fit is told while its pages are shown`() {
        val fits = PageFits()
        fits.show("a", PageFit(1f, 0.5f))
        fits.show("b", PageFit(2f, 1f))
        fits.show("a", null)

        assertEquals(mapOf("b" to PageFit(2f, 1f)), fits.fits.value)
    }
}
