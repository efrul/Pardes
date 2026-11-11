package io.github.kdroidfilter.seforimapp.core.settings

import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The daf's own settings: its zoom (the zoom keys, buttons and menu zoom the text, and a tab's daf with it while it
 * shows one; a pinch over the daf zooms both by its ratio), and whether it marks the lines its alignment is not sure of.
 */
class DafZoomTest {
    private val settings = testAppSettings()
    private val tab = "tab"

    @Test
    fun `a tab showing no daf zooms the text`() {
        settings.zoomIn(tab)

        assertEquals(AppSettings.DEFAULT_TEXT_SIZE + AppSettings.TEXT_SIZE_INCREMENT, settings.getTextSize())
        assertEquals(AppSettings.DEFAULT_DAF_ZOOM, settings.getDafZoom())
    }

    @Test
    fun `a tab showing its daf zooms the daf, level by level, and the text beside it`() {
        settings.dafShown(tab)
        settings.zoomIn(tab)
        settings.zoomIn(tab)

        assertEquals(1.25f, settings.getDafZoom())
        // The commentaries beside the daf are drawn in the text's size
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE + 2 * AppSettings.TEXT_SIZE_INCREMENT, settings.getTextSize())
        settings.zoomOut(tab)
        assertEquals(1.1f, settings.dafZoomFlow.value)
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE + AppSettings.TEXT_SIZE_INCREMENT, settings.textSizeFlow.value)
    }

    @Test
    fun `a pinch over the daf zooms the text by the same ratio, and back`() {
        settings.dafShown(tab)
        settings.zoomDaf(1.5f)

        assertEquals(1.5f, settings.getDafZoom())
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE * 1.5f, settings.getTextSize())
        // Out past the text's smallest size and back: the text is the size it was, not the smallest size zoomed in
        settings.zoomDaf(0.5f)
        assertEquals(AppSettings.MIN_TEXT_SIZE, settings.getTextSize())
        settings.zoomDaf(1f)
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE, settings.getTextSize())
        // The text's size changed some other way meanwhile: the next pinch goes on from it
        settings.setTextSize(20f)
        settings.zoomDaf(2f)
        assertEquals(40f, settings.getTextSize())
    }

    @Test
    fun `a pinch's zoom goes on from the next level, and the zoom stops at its ends`() {
        settings.dafShown(tab)
        settings.setDafZoom(1.18f)
        settings.zoomIn(tab)
        assertEquals(1.25f, settings.getDafZoom())

        repeat(AppSettings.DAF_ZOOM_LEVELS.size) { settings.zoomIn(tab) }
        assertEquals(AppSettings.MAX_DAF_ZOOM, settings.getDafZoom())
        repeat(AppSettings.DAF_ZOOM_LEVELS.size * 2) { settings.zoomOut(tab) }
        assertEquals(AppSettings.MIN_DAF_ZOOM, settings.getDafZoom())
        settings.setDafZoom(0.1f)
        assertEquals(AppSettings.MIN_DAF_ZOOM, settings.getDafZoom())
    }

    @Test
    fun `a tab shows its daf until its last view of it is gone`() {
        // A tab dragged to another window is shown there before it leaves this one
        settings.dafShown(tab)
        settings.dafShown(tab)
        settings.dafHidden(tab)
        assertTrue(settings.showsDaf(tab))

        settings.dafHidden(tab)
        assertFalse(settings.showsDaf(tab))
        assertFalse(tab in settings.dafViewsFlow.value)
    }

    @Test
    fun `the daf marks its unsure lines only when asked, and the reset turns it off`() {
        assertFalse(settings.isDafMarkUnsureEnabled())
        settings.setDafMarkUnsureEnabled(true)
        assertTrue(settings.dafMarkUnsureFlow.value)

        settings.clearAll()
        assertFalse(settings.dafMarkUnsureFlow.value)
        assertFalse(settings.isDafMarkUnsureEnabled())
    }

    @Test
    fun `the daf's review is off until asked, and the reset turns it off`() {
        assertFalse(settings.isDafReviewEnabled())
        settings.setDafReviewEnabled(true)
        assertTrue(settings.dafReviewFlow.value)

        settings.clearAll()
        assertFalse(settings.dafReviewFlow.value)
        assertFalse(settings.isDafReviewEnabled())
    }

    @Test
    fun `the daf's zoom is kept, and reset with the other settings`() {
        settings.setDafZoom(2f)
        assertEquals(2f, settings.getDafZoom())

        settings.clearAll()
        assertEquals(AppSettings.DEFAULT_DAF_ZOOM, settings.dafZoomFlow.value)
        assertEquals(AppSettings.DEFAULT_DAF_ZOOM, settings.getDafZoom())
    }
}
