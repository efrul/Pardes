package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.testAppSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The daf's own settings: its zoom (the zoom keys, buttons and menu zoom the text, or a tab's daf alone while it shows
 * one, the commentaries beside it in the text's size as it was: request 63 of the list after the third testers' build;
 * no wider than the view the pages are shown in unless it was set wider: request 79), and whether it shows its marks.
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
    fun `a tab showing its daf zooms the daf alone, level by level`() {
        settings.dafShown(tab)
        settings.zoomIn(tab)
        settings.zoomIn(tab)

        assertEquals(1.25f, settings.getDafZoom())
        // The commentaries beside the daf, drawn in the text's size, keep it
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE, settings.getTextSize())
        settings.zoomOut(tab)
        assertEquals(1.1f, settings.dafZoomFlow.value)
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE, settings.textSizeFlow.value)
    }

    @Test
    fun `a pinch over the daf zooms it alone`() {
        settings.dafShown(tab)
        settings.setDafZoom(1.5f)

        assertEquals(1.5f, settings.getDafZoom())
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE, settings.getTextSize())
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
    fun `the daf shows its marks only when asked, and the reset hides them`() {
        assertFalse(settings.isDafMarksShown())
        settings.setDafMarksShown(true)
        assertTrue(settings.dafMarksFlow.value)

        settings.clearAll()
        assertFalse(settings.dafMarksFlow.value)
        assertFalse(settings.isDafMarksShown())
    }

    @Test
    fun `the marks start shown for whoever had turned on one of the two settings before the button`() {
        for (former in listOf("daf_mark_unsure", "daf_review")) {
            val stored = PropertiesSettings(Properties()).apply { putBoolean(former, true) }
            assertTrue(AppSettings(stored).dafMarksFlow.value, former)

            // The button pressed once, the former setting says nothing more
            AppSettings(stored).setDafMarksShown(false)
            assertFalse(AppSettings(stored).isDafMarksShown(), former)
        }
    }

    @Test
    fun `the daf's zoom is kept, and reset with the other settings`() {
        settings.setDafZoom(2f)
        assertEquals(2f, settings.getDafZoom())

        settings.clearAll()
        assertEquals(AppSettings.DEFAULT_DAF_ZOOM, settings.dafZoomFlow.value)
        assertEquals(AppSettings.DEFAULT_DAF_ZOOM, settings.getDafZoom())
    }

    @Test
    fun `pages are shown no wider than their view, unless the zoom was set wider`() {
        assertEquals(0.8f, AppSettings.shownDafZoom(1.2f, wide = false, width = 0.8f))
        assertEquals(1.2f, AppSettings.shownDafZoom(1.2f, wide = true, width = 0.8f))
        assertEquals(0.6f, AppSettings.shownDafZoom(0.6f, wide = false, width = 0.8f))
        assertEquals(1.2f, AppSettings.shownDafZoom(1.2f, wide = false, width = null), "a view that told no width")
    }

    @Test
    fun `a pane opened beside the pages narrows them as much as it must, and closed gives their size back`() {
        settings.dafShown(tab)
        settings.showPageWidth(tab, 1.5f)
        settings.setDafZoomFrom(tab, 1.2f)
        assertFalse(settings.isDafZoomWide())

        // A pane opened: the view a zoom of 0.8 wide
        settings.showPageWidth(tab, 0.8f)
        assertEquals(0.8f, settings.shownDafZoom(tab))
        assertEquals(1.2f, settings.getDafZoom(), "the zoom kept")
        // Closed
        settings.showPageWidth(tab, 1.5f)
        assertEquals(1.2f, settings.shownDafZoom(tab))
    }

    @Test
    fun `the zoom keys go on from the zoom the pages are shown at, and wider than the view it stays so`() {
        settings.dafShown(tab)
        settings.showPageWidth(tab, 1.5f)
        settings.setDafZoomFrom(tab, 1.2f)
        settings.showPageWidth(tab, 0.8f)

        // Out from 0.8, not from 1.2
        settings.zoomOut(tab)
        assertEquals(0.75f, settings.shownDafZoom(tab))
        settings.showPageWidth(tab, 1.5f)
        assertEquals(0.75f, settings.shownDafZoom(tab))

        // In past the view's width: kept wider than it in a narrower view
        settings.showPageWidth(tab, 0.8f)
        settings.zoomIn(tab)
        settings.zoomIn(tab)
        assertEquals(0.9f, settings.shownDafZoom(tab))
        assertTrue(settings.isDafZoomWide())
        settings.showPageWidth(tab, 0.7f)
        assertEquals(0.9f, settings.shownDafZoom(tab))
        // A view gone tells no width
        settings.showPageWidth(tab, null)
        assertFalse(tab in settings.pageWidthsFlow.value)
    }

    @Test
    fun `the zoom set wider than its view stays so after a restart`() {
        val stored = PropertiesSettings(Properties())
        AppSettings(stored).apply {
            showPageWidth(tab, 1f)
            setDafZoomFrom(tab, 1.5f)
        }
        assertTrue(AppSettings(stored).isDafZoomWide())
        assertTrue(AppSettings(stored).dafZoomWideFlow.value)
        AppSettings(stored).setDafZoom(1.5f)
        assertFalse(AppSettings(stored).isDafZoomWide(), "set by a fit, as wide as its view")
    }

    @Test
    fun `the zoom is never set until the pages first fit their view, and the share of the view before is not read`() {
        // The zoom of before request 73 of stage 3, a share of the view's width
        val stored = PropertiesSettings(Properties()).apply { putFloat("daf_zoom", 1.5f) }
        val before = AppSettings(stored)
        assertFalse(before.hasDafZoom())
        assertEquals(AppSettings.DEFAULT_DAF_ZOOM, before.getDafZoom())

        before.setDafZoom(1.3f)
        assertTrue(AppSettings(stored).hasDafZoom())
        assertEquals(1.3f, AppSettings(stored).getDafZoom())
    }
}
