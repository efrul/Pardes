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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
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
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.TabContentScope
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.milliseconds

// Pardes (request 95 of docs/DESKTOP_STAGE3_PLAN.md: "בקונטרול טאב כאשר הקונטרול נשאר לחוץ יוצגו הלשוניות כמו בלחיצה
// על ה"עוד..."): Ctrl+Tab goes to the next tab as it did, and while Ctrl stays down the window's tabs are listed under
// the strip as the hidden tabs' menu lists its own, the selected one marked: each Tab goes on to the next, and the
// list goes when Ctrl is let go. The list takes no focus, so the window's own keys go on stepping through the tabs.

private val shownByWindow = WeakHashMap<OpenWindow, MutableStateFlow<Boolean>>()

/** Whether this window lists its tabs, Ctrl held down after Ctrl+Tab: kept beside the window, as the search from anywhere's. */
internal val OpenWindow.tabSwitcherShown: MutableStateFlow<Boolean>
    get() = synchronized(shownByWindow) { shownByWindow.getOrPut(this) { MutableStateFlow(false) } }

/**
 * Whether the tabs are listed after a key, [shown] before it: while Ctrl or Cmd is [held], from a press of Tab on
 * ([tabPressed]). A key with neither held hides them: the Ctrl let go, or whatever comes after one that was missed.
 */
internal fun tabSwitcherShown(
    shown: Boolean,
    held: Boolean,
    tabPressed: Boolean,
): Boolean = held && (tabPressed || shown)

/** A key [event] of this window, for its tabs' list; it is not taken. */
internal fun OpenWindow.tabSwitcherKey(event: KeyEvent) {
    val held = event.isCtrlPressed || event.isMetaPressed
    val tabPressed = event.type == KeyEventType.KeyDown && event.key == Key.Tab
    tabSwitcherShown.update { tabSwitcherShown(it, held, tabPressed) }
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
 * the selected one marked and kept in view; a click on a row selects its tab.
 */
@Composable
internal fun TabSwitcher(rows: List<TabSwitcherRow>) {
    val openWindow = LocalOpenWindow.current
    val held by openWindow.tabSwitcherShown.collectAsState()
    // A window that lost the keys never hears the Ctrl let go
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (!focused) openWindow.tabSwitcherShown.value = false }
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
            rows.forEach { row -> TabSwitcherItem(row) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabSwitcherItem(row: TabSwitcherRow) {
    val selected = row.data.selected
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) inView.bringIntoView() }
    val accent = JewelTheme.globalColors.outlines.focused
    Box(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(inView)
            .background(if (selected) accent.copy(alpha = 0.20f) else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(onClick = row.data.onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
