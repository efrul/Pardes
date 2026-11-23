package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pages' zoom a size, which no pane opened beside them changes, and the view's width, a whole page and two thirds of
 * it zooms it is set to, in turn (requests 63, 73 and 81 of stage 3).
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
    fun `two thirds of a page are the zoom that leaves them in the view's height, and no wider than the view`() {
        // The page 560 wide whole in the view, half again as large: 840 wide, two thirds of its 1260 in the view's 840
        assertEquals(0.84f, pageFit(2400f, 900f, aspect = 1.5f, columns = 1, around = 60f).twoThirds, 1e-6f)
        assertEquals(0.7f, pageFit(700f, 900f, aspect = 1.5f, columns = 1, around = 60f).twoThirds, "a narrow view: its width")
    }

    @Test
    fun `a page whose shape is not known yet fits as wide as the view`() {
        assertEquals(PageFit(0.8f, 0.8f, 0.8f), pageFit(800f, 600f, aspect = null, columns = 1))
        assertEquals(PageFit(0.8f, 0.8f, 0.8f), pageFit(800f, 600f, aspect = 0f, columns = 1))
    }

    @Test
    fun `the button goes from a whole page to two thirds of it, to the view's width, and back`() {
        val fit = PageFit(width = 2.4f, whole = 0.56f, twoThirds = 0.84f)
        assertEquals(PageFitMode.WHOLE, fit.nextMode(1.3f), "from another zoom, a whole page")
        assertEquals(PageFitMode.TWO_THIRDS, fit.nextMode(0.56f))
        assertEquals(PageFitMode.WIDTH, fit.nextMode(0.84f))
        assertEquals(PageFitMode.WHOLE, fit.nextMode(2.4f))
        assertEquals(PageFitMode.TWO_THIRDS, fit.modeAt(0.84f))
        assertNull(fit.modeAt(0.6f))
    }

    @Test
    fun `a fit no other zoom than another one's is passed over`() {
        // Two thirds no wider than the view: its width
        val narrow = PageFit(width = 0.7f, whole = 0.56f, twoThirds = 0.7f)
        assertEquals(listOf(PageFitMode.WHOLE, PageFitMode.WIDTH), narrow.modes())
        assertEquals(PageFitMode.WIDTH, narrow.modeAt(0.7f))
        assertEquals(PageFitMode.WIDTH, narrow.nextMode(0.56f))
        // A whole page as wide as the view: one fit
        assertEquals(listOf(PageFitMode.WHOLE), PageFit(0.4f, 0.4f, 0.4f).modes())
        assertEquals(PageFitMode.WHOLE, PageFit(0.4f, 0.4f, 0.4f).nextMode(0.4f))
    }

    @Test
    fun `a zoom is at a fit as near as the zoom's ends let it be`() {
        assertEquals(PageFitMode.WHOLE, PageFit(2.4f, 0.56f, 0.84f).modeAt(0.56f))
        // A view so short that a whole page is below the zoom's least, and two thirds of it too: those are one fit
        val short = PageFit(0.5f, 0.1f, 0.15f)
        assertEquals(PageFitMode.WHOLE, short.modeAt(AppSettings.MIN_DAF_ZOOM))
        assertEquals(PageFitMode.WIDTH, short.nextMode(AppSettings.MIN_DAF_ZOOM))
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
