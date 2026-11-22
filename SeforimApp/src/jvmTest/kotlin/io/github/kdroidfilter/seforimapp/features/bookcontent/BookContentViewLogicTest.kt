package io.github.kdroidfilter.seforimapp.features.bookcontent

import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.isLineFullyVisible
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.shouldPlaceAltHeadingsInsideBar
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for logic extracted from BookContentView:
 * - Alt heading placement (inside vs outside selection bar)
 * - Line visibility check (for keyboard navigation scroll decisions)
 */
class BookContentViewLogicTest {
    // ==================== shouldPlaceAltHeadingsInsideBar ====================

    @Test
    fun `alt headings inside bar when consecutive selection with headings`() {
        assertTrue(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = true,
                isPrevSelected = true,
                hasAltHeadings = true,
            ),
        )
    }

    @Test
    fun `alt headings outside bar when single line selected`() {
        assertFalse(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = true,
                isPrevSelected = false,
                hasAltHeadings = true,
            ),
        )
    }

    @Test
    fun `alt headings outside bar when line not selected`() {
        assertFalse(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = false,
                isPrevSelected = true,
                hasAltHeadings = true,
            ),
        )
    }

    @Test
    fun `no alt headings - never inside bar`() {
        assertFalse(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = true,
                isPrevSelected = true,
                hasAltHeadings = false,
            ),
        )
    }

    @Test
    fun `alt headings outside bar when neither selected`() {
        assertFalse(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = false,
                isPrevSelected = false,
                hasAltHeadings = true,
            ),
        )
    }

    // ==================== isLineFullyVisible ====================

    @Test
    fun `line fully visible within viewport`() {
        assertTrue(isLineFullyVisible(itemOffset = 0, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line fully visible at viewport edge`() {
        assertTrue(isLineFullyVisible(itemOffset = 400, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line partially visible - extends beyond viewport`() {
        assertFalse(isLineFullyVisible(itemOffset = 450, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line partially visible - starts before viewport`() {
        assertFalse(isLineFullyVisible(itemOffset = -10, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line not found - null offset`() {
        assertFalse(isLineFullyVisible(itemOffset = null, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line not found - null size`() {
        assertFalse(isLineFullyVisible(itemOffset = 0, itemSize = null, viewportEnd = 500))
    }

    @Test
    fun `line not found - both null`() {
        assertFalse(isLineFullyVisible(itemOffset = null, itemSize = null, viewportEnd = 500))
    }
}
