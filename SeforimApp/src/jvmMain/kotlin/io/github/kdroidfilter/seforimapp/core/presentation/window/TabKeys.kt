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

/** What a tab shortcut does: select the tab at [index], or move the selected tab there. */
internal sealed interface TabKeyAction {
    val index: Int

    data class Select(
        override val index: Int,
    ) : TabKeyAction

    data class Move(
        override val index: Int,
    ) : TabKeyAction
}

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
    val digit = DIGITS.indexOf(key)
    return when {
        digit >= 0 && !shift -> {
            val target = if (digit == DIGITS.lastIndex) count - 1 else digit
            if (target < count) TabKeyAction.Select(indexOf(target)) else null
        }
        (key == Key.Tab && !shift) || (key == Key.PageDown && !shift) -> TabKeyAction.Select(indexOf((place + 1) % count))
        (key == Key.Tab && shift) || (key == Key.PageUp && !shift) -> TabKeyAction.Select(indexOf((place - 1 + count) % count))
        key == Key.PageDown && shift -> (place + 1).takeIf { it < count }?.let { TabKeyAction.Move(indexOf(it)) }
        key == Key.PageUp && shift -> (place - 1).takeIf { it >= 0 }?.let { TabKeyAction.Move(indexOf(it)) }
        else -> null
    }
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
    return remember(tabsViewModel, rtl) {
        { event ->
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
