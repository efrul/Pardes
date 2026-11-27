package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import java.util.WeakHashMap
import kotlin.math.abs

// Pardes (request 100 of docs/DESKTOP_STAGE3_PLAN.md: "אני רוצה שמקש שיפט יהיה עם משמעות ויוכל להעביר פוקוס, תנסה
// לבדוק מה רלוונטי ואם מקובל וכדאי להשתמש גם בחיצי ימין ושמאל"): the keyboard's focus moved between the parts of a
// reader's window that take keys, the book tree, the contents and the text. Shift with the arrow left or right goes to
// the part on that side, as Opera's and Vivaldi's spatial navigation moves with Shift and the arrows, and Windows
// Terminal between its panes with Alt and them; F6 goes round them and Shift+F6 back, Windows' own key for the panes
// of a window. The arrows alone stay what they are in each part: a tree's folders, a field's caret.

/** A part of a window the focus keys stop at: where it is in the window, whether it has the focus, and how it takes it. */
internal class FocusStop(
    val focus: () -> Unit,
) {
    var bounds: Rect = Rect.Zero
    var focused: Boolean = false
    internal var stops: FocusStops? = null

    /** A key that came up to this part unused: Shift with the arrow left or right goes to the part on that side. */
    fun key(event: KeyEvent): Boolean {
        val toLeft = sideOf(event) ?: return false
        return stops?.move(this, toLeft) == true
    }
}

// Shift+Left (true) or Shift+Right (false) pressed, with nothing else held; null for any other key
private fun sideOf(event: KeyEvent): Boolean? {
    if (event.type != KeyEventType.KeyDown || !event.isShiftPressed) return null
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return null
    return when (event.key) {
        Key.DirectionLeft -> true
        Key.DirectionRight -> false
        else -> null
    }
}

/** The parts of one window the focus keys stop at, as they are shown now. */
internal class FocusStops {
    private val stops = ArrayList<FocusStop>()

    fun add(stop: FocusStop) {
        stop.stops = this
        stops += stop
    }

    fun remove(stop: FocusStop) {
        stops -= stop
    }

    /** The focus goes from [from] to the nearest part on its left ([toLeft]) or on its right; false with none there. */
    fun move(
        from: FocusStop,
        toLeft: Boolean,
    ): Boolean {
        val shown = stops.filter { !it.bounds.isEmpty }
        val to = stopBeside(shown.map { it.bounds }, shown.indexOf(from), toLeft) ?: return false
        shown[to].focus()
        return true
    }

    /**
     * F6: the focus goes to the part read after the one that has it, or before it with Shift, round them all; to the
     * first with none focused. False for another key, or with no part shown.
     */
    fun cycle(
        event: KeyEvent,
        rtl: Boolean,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown || event.key != Key.F6) return false
        if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
        val shown = stops.filter { !it.bounds.isEmpty }
        val to = stopAfter(shown.map { it.bounds }, shown.indexOfFirst { it.focused }, !event.isShiftPressed, rtl) ?: return false
        shown[to].focus()
        return true
    }
}

/**
 * The part beside the one at [from] among parts at [bounds], on its left ([toLeft]) or its right: of those whose middle
 * is on that side, the nearest, and of two as near the one more level with it. Null with none there.
 */
internal fun stopBeside(
    bounds: List<Rect>,
    from: Int,
    toLeft: Boolean,
): Int? {
    val here = bounds.getOrNull(from)?.center ?: return null
    return bounds.indices
        .filter { it != from && if (toLeft) bounds[it].center.x < here.x else bounds[it].center.x > here.x }
        .minWithOrNull(compareBy({ abs(bounds[it].center.x - here.x) }, { abs(bounds[it].center.y - here.y) }))
}

/**
 * The part read after the one at [from] among parts at [bounds] ([forward]), or before it, round them all: they are
 * read from the right in a right-to-left window ([rtl]), those one above the other from the top. From none (-1), the
 * first read. Null with no parts.
 */
internal fun stopAfter(
    bounds: List<Rect>,
    from: Int,
    forward: Boolean,
    rtl: Boolean,
): Int? {
    if (bounds.isEmpty()) return null
    val read = bounds.indices.sortedWith(compareBy({ if (rtl) -bounds[it].center.x else bounds[it].center.x }, { bounds[it].center.y }))
    val at = read.indexOf(from)
    if (at < 0) return read.first()
    return read[(at + (if (forward) 1 else -1) + read.size) % read.size]
}

private val stopsByWindow = WeakHashMap<OpenWindow, FocusStops>()

/** This window's parts the focus keys stop at: kept beside the window, as its tabs' list of Ctrl+Tab is. */
internal val OpenWindow.focusStops: FocusStops
    get() = synchronized(stopsByWindow) { stopsByWindow.getOrPut(this) { FocusStops() } }

/**
 * A part of the window the focus keys stop at while it is [shown] (every tab of a window stays composed: only the
 * selected one's text is a stop), taking the focus by [focus]: for the modifier of what takes its keys, before its
 * `focusable` (it reads the part's place and focus, and the keys that came up to it unused).
 */
@Composable
internal fun rememberFocusStop(
    shown: Boolean,
    focus: () -> Unit,
): Modifier {
    val stops = LocalOpenWindow.current.focusStops
    val current by rememberUpdatedState(focus)
    val stop = remember { FocusStop { current() } }
    DisposableEffect(stops, stop, shown) {
        if (shown) stops.add(stop)
        onDispose { stops.remove(stop) }
    }
    return remember(stop) {
        Modifier
            .onGloballyPositioned { stop.bounds = it.boundsInWindow() }
            .onFocusChanged { stop.focused = it.hasFocus }
            .onKeyEvent(stop::key)
    }
}
