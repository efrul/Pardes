package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// Pardes (request 83 of docs/DESKTOP_STAGE3_PLAN.md, the strip scrolled by the wheel as IntelliJ's is): the wheel over
// the strip moves the tabs on show while some are hidden, and the selected tab stays the selected one.

/**
 * Zayit's [TabWindow], which the wheel moves too. The run of tabs on show keeps to the selected tab as his does; once
 * the wheel has moved it, it stays where the wheel left it, with the selected tab or without, until another tab is
 * selected.
 */
@Stable
internal class ScrolledTabWindow {
    private var start = 0

    // The last place the run may start at, as the strip last laid it out.
    private var lastStart = 0

    // The selected tab the run last went to, and whether the wheel moved the run since.
    private var followed: Any? = null
    private var wheeled = false

    /** The wheel's steps so far: read where the strip picks its tabs, so that a step lays it out again. */
    var steps by mutableIntStateOf(0)
        private set

    /** The [count] of [tabs] on show, around the one at [selected] (`-1`: none of them) unless the wheel moved them. */
    fun <T> follow(
        tabs: List<T>,
        count: Int,
        selected: Int,
    ): List<T> {
        val size = count.coerceIn(0, tabs.size)
        lastStart = tabs.size - size
        var first = start.coerceIn(0, lastStart)
        val current = tabs.getOrNull(selected)
        if (current != followed) wheeled = false
        followed = current
        if (!wheeled) {
            if (selected in 0 until first) first = selected
            if (selected >= first + size) first = selected - size + 1
        }
        start = first
        return tabs.subList(first, first + size)
    }

    /** The run moved [by] tabs along the strip, as far as its ends; whether it moved at all. */
    fun scroll(by: Int): Boolean {
        val target = (start + by).coerceIn(0, lastStart)
        if (target == start) return false
        start = target
        wheeled = true
        steps++
        return true
    }
}

// A touchpad's two fingers travel this far for one tab.
private val PanPerTab = 60.dp

/**
 * The wheel over the strip moves [window]'s run of tabs, while some are hidden ([enabled]): a notch down or towards
 * the strip's end a tab on, a notch up a tab back. A touchpad's two fingers, which come as a fine scroll or as a pan
 * (Windows), move a tab for each [PanPerTab]. [rtl]: the strip is read from the right, its later tabs to the left.
 */
internal fun Modifier.tabStripWheel(
    window: ScrolledTabWindow,
    enabled: Boolean,
    rtl: Boolean,
): Modifier {
    if (!enabled) return this
    return pointerInput(window, rtl) {
        val panPerTab = PanPerTab.toPx()
        var carried = 0f
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                // Whole tabs only; what is left of a fine scroll adds to the next event's.
                carried += event.tabsTurned(rtl, panPerTab) ?: 0f
                val tabs = carried.toInt()
                carried -= tabs
                if (tabs != 0 && window.scroll(tabs)) event.changes.forEach { it.consume() }
            }
        }
    }
}

// How many tabs on (or back, negative) this event turns the strip; null for an event that is not a turn of the wheel.
@OptIn(ExperimentalComposeUiApi::class) // PointerInputChange.panOffset
private fun PointerEvent.tabsTurned(
    rtl: Boolean,
    panPerTab: Float,
): Float? {
    val change = changes.firstOrNull() ?: return null
    val (x, y) =
        when (type) {
            PointerEventType.Scroll -> change.scrollDelta.x to change.scrollDelta.y
            PointerEventType.PanMove -> change.panOffset.x / panPerTab to change.panOffset.y / panPerTab
            else -> return null
        }
    // The wheel's own turn, or a sideways one along the strip: to the right is on in a strip read from the left.
    return when {
        abs(y) >= abs(x) -> y
        rtl -> -x
        else -> x
    }
}
