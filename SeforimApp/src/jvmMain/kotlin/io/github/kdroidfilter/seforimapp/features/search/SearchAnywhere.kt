package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionButton
import io.github.kdroidfilter.seforimapp.core.presentation.components.rememberDesktopOpener
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.rememberHomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.UnifiedSearchBar
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_anywhere_tooltip
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.roundToInt

// Pardes (request 85 of docs/DESKTOP_STAGE3_PLAN.md, a search from anywhere): the home's search bar opened over any
// screen of the window, by Ctrl/Cmd+L or its button in the title bar, as a browser's address bar is. What it finds
// opens in a tab of its own, so the tab it was opened over stays as it was.

private val visibleByWindow = WeakHashMap<OpenWindow, MutableStateFlow<Boolean>>()

/** Whether this window's search from anywhere is open: kept beside the window, as `tabSearchVisible` is in it. */
internal val OpenWindow.searchAnywhereVisible: MutableStateFlow<Boolean>
    get() = synchronized(visibleByWindow) { visibleByWindow.getOrPut(this) { MutableStateFlow(false) } }

/** Ctrl/Cmd+L alone, a browser's key to its address bar: it opens the search from anywhere, and closes it. */
internal fun isSearchAnywhereKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        event.key == Key.L &&
        (event.isCtrlPressed || event.isMetaPressed) &&
        !event.isShiftPressed &&
        !event.isAltPressed

/** This window's search from anywhere opened, or closed, by its key; whether [event] was that key. */
internal fun OpenWindow.searchAnywhereKey(event: KeyEvent): Boolean {
    if (!isSearchAnywhereKey(event)) return false
    searchAnywhereVisible.value = !searchAnywhereVisible.value
    return true
}

/** The tab a search bar's [event] opens, when it is opened from over another tab; null for a desktop's link. */
internal fun newTabDestination(event: SearchHomeNavigationEvent): TabsDestination? =
    when (event) {
        is SearchHomeNavigationEvent.NavigateToSearch -> TabsDestination.Search(searchQuery = event.query, tabId = event.tabId)
        is SearchHomeNavigationEvent.NavigateToBookContent ->
            TabsDestination.BookContent(bookId = event.bookId, tabId = event.tabId, lineId = event.lineId)
        is SearchHomeNavigationEvent.NavigateToDeepLink -> event.destination
        is SearchHomeNavigationEvent.OpenDesktop -> null
    }

/**
 * The title bar's button of the search from anywhere, beside the find's, and the search bar it opens over the window.
 * macOS's title bar has no such buttons: there the key alone opens it.
 */
@Composable
fun SearchAnywhereButton() {
    val openWindow = LocalOpenWindow.current
    val visible by openWindow.searchAnywhereVisible.collectAsState()
    // The title bar's bottom, in the window: the bar opens right under it.
    var titleBarBottom by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier.fillMaxHeight().onGloballyPositioned { titleBarBottom = it.boundsInWindow().bottom.roundToInt() },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!PlatformInfo.isMacOS) {
            val label = stringResource(Res.string.search_anywhere_tooltip)
            TitleBarActionButton(
                // A magnifier over the whole width: the find's plain one stands beside it
                key = AllIconsKeys.Actions.FindEntireFile,
                contentDescription = label,
                onClick = { openWindow.searchAnywhereVisible.value = !visible },
                tooltipText = label,
                shortcutHint = "Ctrl+L",
            )
        }
        if (visible) {
            Popup(
                popupPositionProvider = WindowOrigin,
                onDismissRequest = { openWindow.searchAnywhereVisible.value = false },
                // A press beside the bar closes it here (SearchAnywhereLayer): the bar's suggestions are a popup of
                // their own, and a press on them is not one outside.
                properties = PopupProperties(focusable = true, dismissOnClickOutside = false),
            ) {
                SearchAnywhereLayer(top = titleBarBottom, onDismiss = { openWindow.searchAnywhereVisible.value = false })
            }
        }
    }
}

// The whole window, from its corner: the layer takes every press beside the bar.
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

private val BarShape = RoundedCornerShape(12.dp)
private val BarMaxWidth = 640.dp
private val BarGap = 6.dp

/** The search bar under the title bar ([top], in pixels), in the middle; a press anywhere beside it closes it. */
@Composable
private fun SearchAnywhereLayer(
    top: Int,
    onDismiss: () -> Unit,
) {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier =
            Modifier
                .size(with(density) { window.width.toDp() }, with(density) { window.height.toDp() })
                .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } },
        contentAlignment = Alignment.TopCenter,
    ) {
        val width = with(density) { window.width.toDp() } * BAR_WIDTH_SHARE
        SearchAnywhereBar(
            modifier = Modifier.padding(top = with(density) { top.toDp() } + BarGap).width(width.coerceAtMost(BarMaxWidth)),
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SearchAnywhereBar(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    // The bar's own state, as a results page's has its own; gone with the bar.
    val bar = remember { graph.desktopManager.newSearchBarViewModel() }
    DisposableEffect(bar) { onDispose { bar.viewModelScope.cancel() } }
    // What the bar opens goes to a tab of its own.
    val tabId = remember { UUID.randomUUID().toString() }
    val searchUi by bar.uiState.collectAsState()
    val callbacks = rememberHomeSearchCallbacks(bar) { tabId }
    val openDesktop = rememberDesktopOpener()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(bar, tabsViewModel) {
        bar.navigationEvents.collect { event ->
            openFromAnywhere(event, tabsViewModel, openDesktop)
            currentOnDismiss()
        }
    }
    Box(
        modifier
            .shadow(16.dp, BarShape)
            .background(JewelTheme.globalColors.panelBackground, BarShape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, BarShape)
            // A press on the bar's own frame is not one beside it.
            .pointerInput(Unit) { detectTapGestures { } }
            .onPreviewKeyEvent { event -> closesSearchAnywhere(event).also { if (it) onDismiss() } }
            .padding(12.dp),
    ) {
        UnifiedSearchBar(searchUi = searchUi, searchCallbacks = callbacks, trailing = { NextSearchModeSwitch() })
    }
}

// Escape, or the key that opened the bar: the window's own handler does not get the keys while the bar has them.
private fun closesSearchAnywhere(event: KeyEvent): Boolean =
    isSearchAnywhereKey(event) || (event.type == KeyEventType.KeyDown && event.key == Key.Escape)

private fun openFromAnywhere(
    event: SearchHomeNavigationEvent,
    tabsViewModel: TabsViewModel,
    openDesktop: (SearchHomeNavigationEvent.OpenDesktop) -> Unit,
) {
    if (event is SearchHomeNavigationEvent.OpenDesktop) openDesktop(event) else newTabDestination(event)?.let(tabsViewModel::openTab)
}

private const val BAR_WIDTH_SHARE = 0.6f
