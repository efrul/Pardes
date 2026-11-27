package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.presentation.window.TabKeyAction
import io.github.kdroidfilter.seforimapp.core.presentation.window.tabKeyAction
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.TabContentScope
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.milliseconds

// Pardes (request 95 of docs/DESKTOP_STAGE3_PLAN.md: "בקונטרול טאב כאשר הקונטרול נשאר לחוץ יוצגו הלשוניות כמו בלחיצה
// על ה"עוד..."): while Ctrl stays down after Ctrl+Tab, the window's tabs are listed under the strip
// as the hidden tabs' menu lists its own. The list takes no focus. Since the reader's question on build 75 ("בקונטרול
// טאב - זה מחליף מיד עוד לפני עזית בקונטרול. מה מקובל?"), as a list shown while a key is held is read elsewhere
// (IntelliJ's Switcher, VS Code's, Windows' Alt+Tab): each Tab marks the next tab in it, the tab marked is selected
// when Ctrl is let go, and Escape lets the list go with no tab changed. Ctrl+Tab went to the next tab at once, as a
// browser's does.

private val markByWindow = WeakHashMap<OpenWindow, MutableStateFlow<Int?>>()

/** The tab this window's list marks, Ctrl held down after Ctrl+Tab; null with no list. Kept beside the window. */
internal val OpenWindow.tabSwitcherMark: MutableStateFlow<Int?>
    get() = synchronized(markByWindow) { markByWindow.getOrPut(this) { MutableStateFlow(null) } }

/**
 * The tab marked after a key, [mark] before it (null: no list): while Ctrl or Cmd is [held], the one a press of Tab
 * marks ([next]), else the one marked still; with neither held, none: the Ctrl let go, or whatever comes after one that
 * was missed.
 */
internal fun tabSwitcherMark(
    mark: Int?,
    held: Boolean,
    next: Int?,
): Int? = if (held) next ?: mark else null

/**
 * A key [event] of this window, for its tabs' list, the strip read right to left when [rtl]: Ctrl+Tab marks the next
 * tab (Shift: the one before), from the one marked or the selected one, and is taken; the Ctrl let go selects the tab
 * marked; Escape lets the list go, taken too.
 */
internal fun OpenWindow.tabSwitcherKey(
    event: KeyEvent,
    rtl: Boolean,
): Boolean {
    val held = event.isCtrlPressed || event.isMetaPressed
    val down = event.type == KeyEventType.KeyDown
    val before = tabSwitcherMark.value
    if (before != null && down && event.key == Key.Escape) {
        tabSwitcherMark.value = null
        return true
    }
    val state = tabsViewModel.state.value
    val tab = down && held && !event.isAltPressed && event.key == Key.Tab
    val from = before ?: state.selectedTabIndex
    val next = if (tab) (tabKeyAction(Key.Tab, event.isShiftPressed, from, state.tabs.size, rtl) as? TabKeyAction.Select)?.index else null
    val after = tabSwitcherMark(before, held, next)
    tabSwitcherMark.value = after
    if (before != null && after == null && before in state.tabs.indices) tabsViewModel.onEvent(TabsEvents.OnSelect(before))
    return tab
}

/** A row of the list: a tab's key, and the tab as the strip draws it. */
internal class TabSwitcherRow(
    val key: String,
    val data: TabData,
)

// Ctrl+Tab let go at once shows no list: it flashes otherwise at every step through the tabs
private val SHOW_AFTER = 180.milliseconds
private val ListMaxHeight = 480.dp
private val ListShape = RoundedCornerShape(6.dp)

/**
 * This window's tabs ([rows], in the order they are read) listed under the strip while Ctrl stays down after Ctrl+Tab,
 * the one marked kept in view; a click on a row selects its tab.
 */
@Composable
internal fun TabSwitcher(rows: List<TabSwitcherRow>) {
    val openWindow = LocalOpenWindow.current
    val mark by openWindow.tabSwitcherMark.collectAsState()
    val held = mark != null
    // The tab marked, by its key: the rows are read in the strip's direction, the tabs' places are the window's own
    val tabs by openWindow.tabsViewModel.state.collectAsState()
    val markedKey =
        mark?.let {
            tabs.tabs
                .getOrNull(it)
                ?.destination
                ?.tabId
        }
    // A window that lost the keys never hears the Ctrl let go: no tab is selected then
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (!focused) openWindow.tabSwitcherMark.value = null }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(held) {
        if (held) delay(SHOW_AFTER)
        shown = held
    }
    if (!shown || rows.size < 2) return
    Popup(popupPositionProvider = UnderStripMiddle, properties = PopupProperties(focusable = false)) {
        val scroll = rememberScrollState()
        Column(
            Modifier
                .width(HiddenTabsListWidth)
                .background(JewelTheme.globalColors.panelBackground, ListShape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, ListShape)
                .padding(4.dp)
                .heightIn(max = ListMaxHeight)
                .verticalScroll(scroll),
        ) {
            rows.forEach { row -> TabSwitcherItem(row, row.key == markedKey) { openWindow.tabSwitcherMark.value = null } }
        }
    }
}

// A row, [selected] when marked; a click selects its tab, the list let go first ([onPick]), Ctrl let go after it
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabSwitcherItem(
    row: TabSwitcherRow,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) inView.bringIntoView() }
    val accent = JewelTheme.globalColors.outlines.focused
    Box(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(inView)
            .background(if (selected) accent.copy(alpha = 0.20f) else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable {
                onPick()
                row.data.onClick()
            }.padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Drawn as the current tab is, at full strength, as the hidden tabs' menu draws its rows
        row.data.content(SwitcherTabScope, TabState.of(selected = true))
    }
}

// As TabsView's own (Jewel's is internal)
private object SwitcherTabScope : TabContentScope {
    @Composable
    override fun Modifier.tabContentAlpha(state: TabState): Modifier =
        alpha(
            JewelTheme.defaultTabStyle.contentAlpha
                .contentFor(state)
                .value,
        )
}

// Under the strip (the list's anchor), in the middle of the window
private object UnderStripMiddle : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset((windowSize.width - popupContentSize.width) / 2, anchorBounds.bottom)
}
