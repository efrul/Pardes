package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.max
import kotlin.math.roundToInt

// Pardes (request 44 of docs/DESKTOP_STAGE3_PLAN.md): "הגדלה באצבעות תהיה סביב המקום של הסמן": while the pointer zooms
// pages (a PDF book's, a tractate's scan, the daf), the point under it stays under it, rather than the view's top. The
// zoom itself is Zayit's (`rememberTextZoom`), which says no place: the views watch the pointer themselves.

/** Where the pointer zoomed last, in the pixels of the view it watches ([zoomPivot]), and when. */
@Stable
class ZoomPivot {
    private var position = Offset.Unspecified
    private var at = 0L

    /** The pointer's place while it zooms (one of its zoom's events in the last moments); null otherwise, as a key's. */
    fun now(): Offset? = position.takeIf { it.isSpecified && System.nanoTime() - at < RECENT_NANOS }

    internal fun record(place: Offset) {
        position = place
        at = System.nanoTime()
    }
}

/**
 * Watches where the pointer zooms over this ([pivot]): Ctrl/Cmd with the wheel or a trackpad's swipe, a trackpad's
 * pinch, the centre of two fingers. Nothing is consumed: the zoom is the gesture's own.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.zoomPivot(pivot: ZoomPivot): Modifier =
    pointerInput(pivot) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                val held = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                val wheel = event.type == PointerEventType.Scroll || event.type == PointerEventType.PanMove
                when {
                    pressed.size >= 2 ->
                        pivot.record(
                            pressed.fold(Offset.Zero) { sum, change -> sum + change.position } / pressed.size.toFloat(),
                        )
                    event.type == PointerEventType.ScaleChange || (held && wheel) ->
                        event.changes.firstOrNull()?.let {
                            pivot.record(
                                it.position,
                            )
                        }
                }
            }
        }
    }

/** A part of a row across the view: its length in pixels, and whether a zoom scales it (a page) or not (a gap). */
internal class RowPart(
    val length: Float,
    val scales: Boolean,
)

/** Where the point [along] pixels from the start of a row of [parts] is once the parts that scale are [scale] times as long. */
internal fun alongScaled(
    along: Float,
    parts: List<RowPart>,
    scale: Float,
): Float {
    if (along <= 0f) return along
    var from = 0f
    var to = 0f
    for (part in parts) {
        val factor = if (part.scales) scale else 1f
        if (along <= from + part.length) return to + (along - from) * factor
        from += part.length
        to += part.length * factor
    }
    return to + (along - from)
}

/**
 * The place along a row of [parts] (from its start) under the pointer, [pointer] pixels from the view's start edge, the
 * row centered in a list as wide as the view ([view]) or as the row and scrolled by [scroll].
 */
internal fun alongAt(
    scroll: Int,
    pointer: Float,
    view: Float,
    parts: List<RowPart>,
): Float {
    val row = parts.sumOf { it.length.toDouble() }.toFloat()
    return scroll + pointer - (max(view, row) - row) / 2
}

/**
 * The sideways scroll that brings the place [along] a row of [parts] (from its start) under the pointer, [pointer]
 * pixels from the view's start edge, once the row's parts that scale are [scale] times as long, centered in a list as
 * wide as the view ([view]) or as the row; and that place then, exact, which the list may not reach (a row narrower
 * than the view is centered, and a list's ends bound its scroll).
 */
internal fun sidewaysAnchored(
    along: Float,
    pointer: Float,
    view: Float,
    parts: List<RowPart>,
    scale: Float,
): Pair<Int, Float> {
    val scaledRow = parts.sumOf { (if (it.scales) it.length * scale else it.length).toDouble() }.toFloat()
    val scaled = alongScaled(along, parts, scale)
    val scrolled = (max(view, scaledRow) - scaledRow) / 2 + scaled - pointer
    return scrolled.roundToInt().coerceIn(0, (max(view, scaledRow) - view).roundToInt().coerceAtLeast(0)) to scaled
}

/** An item of a list as laid out: its index, its offset from the list's content start and its size, in pixels. */
internal class ListItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

/**
 * The item and scroll offset ([LazyListState.requestScrollToItem]) that keep the point [down] pixels below the list's
 * content start where it is when its items' heights past their first [fixed] pixels (a page's title) grow [scale]
 * times: the item at that point, the gap after it counted as its own; null when no item is there. The offset is exact,
 * for the next frame to go on from it rather than from its rounding.
 */
internal fun downAnchored(
    items: List<ListItem>,
    down: Float,
    fixed: Float,
    scale: Float,
): Pair<Int, Float>? {
    val item = items.lastOrNull { it.offset <= down } ?: return null
    return item.index to (scaledDown(down - item.offset, item.size, fixed, scale) - down)
}

// A point [local] pixels down an item of [size] once its height past [fixed] grows [scale] times; the gap after it as is
private fun scaledDown(
    local: Float,
    size: Int,
    fixed: Float,
    scale: Float,
): Float =
    when {
        local <= fixed -> local
        local <= size -> fixed + (local - fixed) * scale
        else -> fixed + (size - fixed) * scale + (local - size)
    }

/**
 * The place a list of zoomed rows keeps while the pointer zooms ([ZoomPivot]): its rows' width last laid out, and the
 * item and offset last asked of its list with their exact value, so that a zoom's frames do not add up their rounding,
 * as the daf's own scale does.
 */
internal class ZoomAnchor {
    var width = 0f
    var sideways: ScrollState? = null
    private var index = -1
    private var asked = Int.MIN_VALUE
    private var exact = 0f
    private var pointer = Offset.Unspecified
    private var along = 0f
    private var alongPointer = Float.NaN
    private var alongKept = -1

    /**
     * The sideways scroll for rows whose parts ([parts], of a width) grew from the last width to [width] in a view
     * [view] pixels wide, scrolled by [last] (the last scroll state, null at first), the pointer [pointer] pixels from
     * its start edge: the place under it stays, or comes back under it once the list can be scrolled there. The scroll
     * as it is when the pointer is not zooming.
     */
    fun keepSideways(
        last: ScrollState?,
        width: Float,
        view: Float,
        pointer: Float?,
        parts: (width: Float) -> List<RowPart>,
    ): Int {
        val scroll = last?.value ?: 0
        val from = this.width
        // Nothing to keep before a first width, or while the width stays
        val sameWidth = from <= 0f || from == width
        if (last == null || pointer == null || sameWidth) return scroll
        val before = parts(from)
        // Under the same pointer and not scrolled since, the place it zooms around, which a narrow row or the list's
        // end may have kept from it
        val same = pointer == alongPointer && scroll == alongKept.coerceAtMost(last.maxValue)
        val place = if (same) along else alongAt(scroll, pointer, view, before)
        val (kept, scaled) = sidewaysAnchored(place, pointer, view, before, width / from)
        along = scaled
        alongPointer = pointer
        alongKept = kept
        return kept
    }

    /**
     * The list [listState] moved for its rows' scaled parts grown from the last width to [width] (their titles [fixed]
     * pixels high), at the pointer's place [pivot] (in the list's container, whose content starts after its padding):
     * the point under it stays. Nothing when the width is the same or the pointer is not zooming.
     */
    fun keepDown(
        listState: LazyListState,
        width: Float,
        fixed: Float,
        pivot: Offset?,
    ) {
        val from = this.width
        this.width = width
        if (from <= 0f || from == width || pivot == null) return
        val info = listState.layoutInfo
        val down = pivot.y - info.beforeContentPadding
        val items = info.visibleItemsInfo.map { ListItem(it.index, it.offset, it.size) }
        // The same item where it was last asked, under the same pointer: from the exact place rather than its pixels
        val same = pivot == pointer && items.any { it.index == index && it.offset == -asked }
        val (item, offset) =
            if (same) {
                val size = items.first { it.index == index }.size
                index to (scaledDown(exact + down, size, fixed, width / from) - down)
            } else {
                downAnchored(items, down, fixed, width / from) ?: return
            }
        index = item
        exact = offset
        asked = offset.roundToInt()
        pointer = pivot
        listState.requestScrollToItem(item, asked)
    }
}

/** How long after the pointer's last zoom event its place still holds: a gesture's frames, not a later key's. */
private const val RECENT_NANOS = 400_000_000L
