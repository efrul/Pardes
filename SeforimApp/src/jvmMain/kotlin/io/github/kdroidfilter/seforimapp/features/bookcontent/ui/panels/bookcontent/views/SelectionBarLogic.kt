package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

/**
 * Whether the line should display the thick (primary) selection bar.
 * Thick bar is used for:
 * - The primary selected line (TOC heading or clicked line)
 * - All lines in Ctrl+click multi-selection mode (isTocEntrySelection = false)
 *
 * Thin bar is only used for secondary lines in a TOC entry selection.
 */
internal fun shouldUseThickBar(
    lineId: Long,
    primarySelectedLineId: Long?,
    isTocEntrySelection: Boolean,
): Boolean = lineId == primarySelectedLineId || !isTocEntrySelection

/**
 * Whether the selection bar should extend downward to bridge the gap to the next item.
 * Only extends when:
 * - The current line is selected
 * - The next line is also selected
 * - Both lines use the same bar style (both thick or both thin)
 */
internal fun shouldExtendToNext(
    isCurrentSelected: Boolean,
    nextLineId: Long?,
    selectedLineIds: Set<Long>,
    currentUseThickBar: Boolean,
    nextUseThickBar: Boolean,
): Boolean =
    isCurrentSelected &&
        nextLineId != null &&
        nextLineId in selectedLineIds &&
        nextUseThickBar == currentUseThickBar

/**
 * Whether alt TOC headings should be placed inside the selection bar.
 * This happens only when:
 * - The current line is selected
 * - The previous line is also selected (consecutive selection)
 * - There are alt headings for this line
 *
 * When a single line is selected (or the line is the first in a group),
 * alt headings appear outside the bar to avoid visual coverage.
 */
internal fun shouldPlaceAltHeadingsInsideBar(
    isCurrentSelected: Boolean,
    isPrevSelected: Boolean,
    hasAltHeadings: Boolean,
): Boolean = isCurrentSelected && isPrevSelected && hasAltHeadings

/**
 * Whether a line is fully visible within the viewport.
 * Used for keyboard navigation: only scroll to the newly selected line
 * if it is not already fully visible.
 */
internal fun isLineFullyVisible(
    itemOffset: Int?,
    itemSize: Int?,
    viewportEnd: Int,
): Boolean =
    itemOffset != null &&
        itemSize != null &&
        itemOffset >= 0 &&
        itemOffset + itemSize <= viewportEnd
