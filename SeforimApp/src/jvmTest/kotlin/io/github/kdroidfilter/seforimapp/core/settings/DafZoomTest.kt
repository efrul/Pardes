package io.github.kdroidfilter.seforimapp.core.settings

import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The daf's own settings: its zoom (the zoom keys, buttons and menu zoom a tab's daf while it shows one, else the
 * text), and whether it marks the lines its alignment is not sure of.
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
    fun `a tab showing its daf zooms the daf, level by level, the text left alone`() {
        settings.dafShown(tab)
        settings.zoomOut(tab)
        settings.zoomOut(tab)

        assertEquals(0.8f, settings.getDafZoom())
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE, settings.getTextSize())
        settings.zoomIn(tab)
        assertEquals(0.9f, settings.dafZoomFlow.value)
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
