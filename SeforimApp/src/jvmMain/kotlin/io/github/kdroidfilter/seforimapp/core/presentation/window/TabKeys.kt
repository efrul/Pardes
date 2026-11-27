package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabSwitcherKey
import io.github.kdroidfilter.seforimapp.features.search.searchAnywhereKey
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow

/**
 * Pardes (stage 3, the tab shortcuts of request 47): what a tab shortcut does in a strip of [count] tabs, [current]
 * selected, in the strip's order as it is read, from the right when [rtl] (the tabs' index 0 is the strip's left end).
 * Ctrl/Cmd with: 1 to 8 the tab at that place, 9 the last one; Tab or Page Down the next tab, Shift+Tab or Page Up the
 * one before, round the strip; Shift+Page Down or Up the selected tab moved one place on or back. Null for another key.
 */
internal fun tabKeyAction(
    key: Key,
    shift: Boolean,
    current: Int,
    count: Int,
    rtl: Boolean,
): TabKeyAction? {
    if (count <= 0) return null

    // A place as read, and back: the first tab read is the rightmost in a right-to-left strip
    fun indexOf(place: Int) = if (rtl) count - 1 - place else place

    val place = indexOf(current.coerceIn(0, count - 1))
    selectedPlace(key, shift, place, count)?.let { return TabKeyAction.Select(indexOf(it)) }
    return movedPlace(key, shift, place, count)?.let { TabKeyAction.Move(indexOf(it)) }
}

// The place, as read, of the tab a shortcut selects from the one at [place] in a strip of [count]: 1 to 8 the tab at
// that place (none past the strip's end), 9 the last one; Tab or Page Down the next tab, Shift+Tab or Page Up the one
// before, round the strip. Null for a key that selects none.
private fun selectedPlace(
    key: Key,
    shift: Boolean,
    place: Int,
    count: Int,
): Int? {
    val digit = DIGITS.indexOf(key)
    val step =
        when (key) {
            Key.Tab -> if (shift) -1 else 1
            Key.PageDown -> 1.takeIf { !shift }
            Key.PageUp -> (-1).takeIf { !shift }
            else -> null
        }
    return when {
        digit >= 0 -> (if (digit == DIGITS.lastIndex) count - 1 else digit).takeIf { !shift && it < count }
        step != null -> (place + step + count) % count
        else -> null
    }
}

// The place, as read, the selected tab at [place] moves to: Shift+Page Down one on, Shift+Page Up one back, none past
// the strip's ends. Null for another key.
private fun movedPlace(
    key: Key,
    shift: Boolean,
    place: Int,
    count: Int,
): Int? =
    when {
        !shift -> null
        key == Key.PageDown -> (place + 1).takeIf { it < count }
        key == Key.PageUp -> (place - 1).takeIf { it >= 0 }
        else -> null
    }

private val DIGITS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

/**
 * The window's tab shortcuts ([tabKeyAction]) for [tabsViewModel]'s strip, in the window's layout direction: a key
 * event taken, or not. Ctrl/Cmd+Tab is among them, from the right in a right-to-left strip as Chrome reads it in Hebrew
 * (Zayit's own went from the left).
 */
@Composable
internal fun rememberTabKeys(tabsViewModel: TabsViewModel): (KeyEvent) -> Boolean {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The search from anywhere's key is the window's too (request 85): it opens the bar, and closes it
    val openWindow = LocalOpenWindow.current
    return remember(tabsViewModel, rtl, openWindow) {
        keys@{ event ->
            // The tabs are listed while Ctrl stays down after Ctrl+Tab, the one marked selected when it is let go
            // (request 95)
            if (openWindow.tabSwitcherKey(event, rtl)) return@keys true
            if (openWindow.searchAnywhereKey(event)) return@keys true
            val ctrl = event.isCtrlPressed || event.isMetaPressed
            val state = tabsViewModel.state.value
            val action =
                if (event.type == KeyEventType.KeyDown && ctrl && !event.isAltPressed) {
                    tabKeyAction(event.key, event.isShiftPressed, state.selectedTabIndex, state.tabs.size, rtl)
                } else {
                    null
                }
            when (action) {
                is TabKeyAction.Select -> tabsViewModel.onEvent(TabsEvents.OnSelect(action.index))
                is TabKeyAction.Move -> tabsViewModel.onEvent(TabsEvents.OnReorder(state.selectedTabIndex, action.index))
                null -> Unit
            }
            action != null
        }
    }
}
