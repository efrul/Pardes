package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

// Pardes (request 83 of docs/DESKTOP_STAGE3_PLAN.md, the strip scrolled as IntelliJ's is): two fingers along the strip,
// or the wheel over it, move the tabs on show while some are hidden, so that the hidden ones come into view from the
// strip's end (the user's word, 2026-11-24: "שעם שתי אצבעות לרוחב המסך יחשפו אותם - כמו גט בראין"). IntelliJ's strip
// goes by the pixel (JBTabsImpl hands the wheel to a scroll pane of its own), and so does this one: the run of tabs
// follows the fingers, a tab cut at each end of it on the way, and comes to rest on a whole tab, where the strip is
// Zayit's again. The selected tab stays the selected one.

/**
 * Zayit's [TabWindow], which a scroll moves too. The run of tabs on show keeps to the selected tab as his does; once a
 * scroll has moved it, it stays where the scroll left it, with the selected tab or without, until another tab is
 * selected.
 */
@Stable
internal class ScrolledTabWindow {
    // Where the run starts, in tabs from the first one: a whole number at rest, between two tabs on a scroll's way.
    private var position by mutableFloatStateOf(0f)

    // The last place the run may start at, as the strip last laid it out.
    private var lastStart = 0

    // The selected tab the run last went to, and whether a scroll moved the run since.
    private var followed: Any? = null
    private var scrolled = false

    // Where a slide under way is going: the wheel's next notch goes on from there.
    private var slidingTo: Float? = null

    // The tabs on show, as the strip last laid them out, and the one coming in after them on a scroll's way.
    private var run: List<Any?> = emptyList()
    private var coming: Any? = null

    /** The run's first tab, among the strip's unpinned ones: read where the strip picks its tabs. */
    val first: Int by derivedStateOf { floor(position).toInt() }

    /** Whether the strip scrolls at all, some of its tabs being hidden ([tabStripWheel]): it rests otherwise. */
    var scrolls by mutableStateOf(false)

    /** Whether the run stands between two tabs, on a scroll's way: the tab after it comes in, cut at the strip's end. */
    val between: Boolean by derivedStateOf { scrolls && position != floor(position) }

    /** How far past its first tab's edge the run stands, as a share of a tab's width: read where the tabs are drawn. */
    val fraction: Float get() = position - floor(position)

    /**
     * The [count] of [tabs] on show, around the one at [selected] (`-1`: none of them) unless a scroll moved them. The
     * tab after them, which comes in while the run stands [between] two ([arrives]), is not one of them: the strip
     * still counts it among its hidden tabs.
     */
    fun <T> follow(
        tabs: List<T>,
        count: Int,
        selected: Int,
    ): List<T> {
        val size = count.coerceIn(0, tabs.size)
        lastStart = tabs.size - size
        // Not observed: the strip picks its tabs again when [first] or [between] change, not at every pixel.
        val stood = Snapshot.withoutReadObservation { position }
        var start = stood.coerceIn(0f, lastStart.toFloat())
        val current = tabs.getOrNull(selected)
        if (current != followed) scrolled = false
        followed = current
        if (!scrolled) {
            if (selected in 0 until floor(start).toInt()) start = selected.toFloat()
            if (selected >= floor(start).toInt() + size) start = (selected - size + 1).toFloat()
        }
        if (start != stood) position = start
        val from = floor(start).toInt()
        val shown = tabs.subList(from, from + size)
        run = shown
        coming = if (start > from) tabs.getOrNull(from + size) else null
        return shown
    }

    /** Whether the tab [key] is the one coming in after the run, on a scroll's way: drawn too, cut at the strip's end. */
    fun arrives(key: Any?): Boolean = between && key != null && key == coming

    /** Whether a scroll moves the tab [key], one of the run's or the one coming in: a pinned tab is not. */
    fun moves(key: Any?): Boolean = key in run || key == coming

    /** Whether the tab [key] is the run's first, the one a scroll cuts at the run's start. */
    fun startsWith(key: Any?): Boolean = run.firstOrNull() == key

    /** The run moved [by] tabs along the strip at once, as far as its ends; whether it moved at all. */
    fun scroll(by: Float): Boolean {
        slidingTo = null
        val target = (position + by).coerceIn(0f, lastStart.toFloat())
        if (target == position) return false
        position = target
        scrolled = true
        return true
    }

    /** Where the run goes when it moves [by] tabs on from where it stands, or is sliding to; null at the strip's end. */
    fun aim(by: Float): Float? {
        val from = slidingTo ?: position
        val target = (from + by).coerceIn(0f, lastStart.toFloat())
        if (target == from) return null
        slidingTo = target
        scrolled = true
        return target
    }

    /** The run slid to [target] ([aim]). */
    suspend fun slideTo(target: Float) {
        slide(target)
        slidingTo = null
    }

    /** The run slid to the nearest whole tab, where the strip rests. */
    suspend fun settle() {
        slidingTo = null
        slide(round(position).coerceIn(0f, lastStart.toFloat()))
    }

    private suspend fun slide(to: Float) {
        if (to == position) return
        animate(position, to, animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing)) { value, _ -> position = value }
    }
}

// A notch of the wheel, and the run coming to rest, slide this long
private const val SLIDE_MS = 140

// A scroll with no end of its own (the wheel, a fine scroll) has ended once this long passes without another
private const val SCROLL_REST_MS = 250L

/**
 * A scroll over the strip moves [window]'s run of tabs, while some are hidden ([enabled]). Two fingers on a touchpad
 * move it as far as they travel, a pixel for a pixel ([tabWidth]: a tab's width, the run's unit): along the strip, or
 * up and down as the wheel turns. They come as a pan (Windows, macOS), in px the way a scroll goes, or as a fine
 * scroll. A notch of the wheel slides the run a tab on (down, or towards the strip's end) or back. Once the scroll
 * ends, the run slides to the nearest whole tab. [rtl]: the strip is read from the right, its later tabs to the left.
 */
internal fun Modifier.tabStripWheel(
    window: ScrolledTabWindow,
    enabled: Boolean,
    rtl: Boolean,
    tabWidth: Dp,
): Modifier {
    if (window.scrolls != enabled) window.scrolls = enabled
    if (!enabled) return this
    return pointerInput(window, rtl, tabWidth) {
        val tabPx = tabWidth.toPx().coerceAtLeast(1f)
        coroutineScope {
            // A scroll cut short (the strip laid out anew under it) left the run between two tabs
            var moving: Job? = launch { window.settle() }
            val pan = PanAxis()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val turn = event.stripTurn(rtl, tabPx, pan)
                    if (turn != null) {
                        moving?.cancel()
                        // A notch slides; the fingers' own move is the run's at once
                        val slideTo = if (turn.notch) window.aim(turn.tabs) else null
                        val moved = slideTo != null || (!turn.notch && window.scroll(turn.tabs))
                        if (moved) event.changes.forEach { it.consume() }
                        moving =
                            launch {
                                if (slideTo != null) window.slideTo(slideTo)
                                if (!turn.ends) delay(SCROLL_REST_MS)
                                window.settle()
                            }
                    }
                }
            }
        }
    }
}

// A scroll's step along the strip: [tabs] on (or back, negative), a wheel's [notch] or a finer move, and whether the
// scroll [ends] with it (the fingers lifted).
private class StripTurn(
    val tabs: Float,
    val notch: Boolean = false,
    val ends: Boolean = false,
)

// The way a touchpad's pan has gone so far, since its start: it moves the strip by its longer way.
private class PanAxis {
    var x = 0f
    var y = 0f

    fun start() {
        x = 0f
        y = 0f
    }
}

// What this event turns the strip by; null for an event that is no scroll.
@OptIn(ExperimentalComposeUiApi::class) // PointerInputChange.panOffset
private fun PointerEvent.stripTurn(
    rtl: Boolean,
    tabPx: Float,
    pan: PanAxis,
): StripTurn? {
    val change = changes.firstOrNull() ?: return null
    return when (type) {
        PointerEventType.Scroll -> {
            val delta = change.scrollDelta
            // The wheel's own turn, or a sideways one along the strip: to the right is on in a strip read from the left.
            val tabs = if (abs(delta.y) >= abs(delta.x)) delta.y else delta.x.alongStrip(rtl)
            StripTurn(tabs, notch = abs(tabs) >= 1f)
        }
        PointerEventType.PanStart -> {
            pan.start()
            null
        }
        PointerEventType.PanMove -> {
            pan.x += change.panOffset.x
            pan.y += change.panOffset.y
            val px = if (abs(pan.x) >= abs(pan.y)) change.panOffset.x.alongStrip(rtl) else change.panOffset.y
            StripTurn(px / tabPx)
        }
        PointerEventType.PanEnd -> StripTurn(0f, ends = true)
        else -> null
    }
}

private fun Float.alongStrip(rtl: Boolean): Float = if (rtl) -this else this

/**
 * The row of the strip's tabs while [window]'s run stands between two tabs: the tab more on show takes no room of the
 * row's, which stays as wide as at rest and cuts that tab at its end. At rest, the row as it is.
 */
internal fun Modifier.scrolledTabRun(
    window: ScrolledTabWindow,
    tabWidth: Dp,
): Modifier {
    if (!window.between) return this
    return clipToBounds().layout { measurable, constraints ->
        val row = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity))
        val width = (row.width - tabWidth.roundToPx()).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, row.height) { row.placeRelative(0, 0) }
    }
}

/**
 * The tab [key] drawn where [window]'s scroll has it, while the run stands between two tabs: moved towards the strip's
 * start by the run's [ScrolledTabWindow.fraction] of its width, the run's first tab cut there, where a pinned tab may
 * stand. A pinned tab, which no scroll moves, and every tab at rest: as it is. [rtl]: the strip's start is on the right.
 */
internal fun Modifier.scrolledTab(
    window: ScrolledTabWindow,
    key: Any?,
    rtl: Boolean,
): Modifier {
    if (!window.between || !window.moves(key)) return this
    val first = window.startsWith(key)
    return graphicsLayer {
        val shift = window.fraction * size.width
        translationX = if (rtl) shift else -shift
        if (first) {
            clip = true
            shape = StartCut(shift, rtl)
        }
    }
}

// A tab without its first [px], from the strip's start: the right in a strip read from the right ([rtl]).
private data class StartCut(
    val px: Float,
    val rtl: Boolean,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(if (rtl) Rect(0f, 0f, size.width - px, size.height) else Rect(px, 0f, size.width, size.height))
}
