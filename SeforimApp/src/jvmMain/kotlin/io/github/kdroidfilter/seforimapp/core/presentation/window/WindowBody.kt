package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.nucleusframework.window.tao.DockLayout
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.SatellitePlacement
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsContent
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.shownBeside
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabBookViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.handleBookShortcut
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookBreadcrumb
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.LocalBookTextReady
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.isBookTextShown
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.DockedPaneCard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.LineLayeredSides
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.LineOuterBandSides
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.LineSideOrder
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.NavigationLayeredSides
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.PaneCard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.ReaderSplitter
import io.github.kdroidfilter.seforimapp.features.search.rememberSearchTreeToggle
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopSession
import io.github.kdroidfilter.seforimapp.framework.desktop.DockSizes
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Under the tab strip, laid out as the reader's split panes were: the activity bars around the
 * outer dock (book tree, contents, notes — full height on the start side), whose centre is the
 * inner dock (links, commentaries, sources) around the selected tab's text, with the book's
 * breadcrumb under it. Every pane is a satellite drawing the selected tab.
 */
@Composable
fun WindowBody(openWindow: OpenWindow) {
    val session = openWindow.session
    val selected = openWindow.group()?.selectedId?.let(session::item)
    val destination = selected?.destination
    // Home, a book or a search: the tabs with the reader's bars and panes (not History / Favorites).
    val readerDestination =
        destination?.takeIf { it is TabsDestination.Home || it is TabsDestination.BookContent || it is TabsDestination.Search }
    val lineWorkspace = session.panesOf(openWindow.groupId, navigation = false)
    // The book view's shortcuts (Ctrl/Cmd+B, K, J) act on the selected reader tab, as they did
    // over the whole book screen.
    val readerEvents =
        if (readerDestination != null) {
            key(readerDestination.tabId) { tabBookViewModel(session.ownerOf(readerDestination.tabId), readerDestination)::onEvent }
        } else {
            null
        }
    // Pardes: with the daf's key, a search's tree of books and the focus keys (requests 94, 97 and 100 of stage 3)
    val readerKeys = rememberReaderKeys(openWindow, readerDestination, readerEvents)
    // Also from the window itself, which gets the keys when nothing has focus (no pane, no text).
    openWindow.readerShortcuts = readerKeys
    val bottomOpen =
        lineWorkspace.satellites.any { entry ->
            entry.isOpen && (entry.placement as? SatellitePlacement.Docked)?.side == DockSide.Bottom
        }

    val density = LocalDensity.current
    var outerWidth by remember { mutableIntStateOf(0) }
    var innerSize by remember { mutableStateOf(IntSize.Zero) }
    // The panes are sized as fractions of these, as the split panes were (see PaneSizeSync).
    LaunchedEffect(outerWidth, innerSize, density) {
        if (outerWidth > 0 && innerSize.width > 0) {
            openWindow.dockSizes = DockSizes(outerWidth, innerSize.width, innerSize.height, density.density)
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .then(rememberTabThumbnails(openWindow))
            .background(canvasBackground())
            .onPreviewKeyEvent { event -> readerKeys?.invoke(event) ?: false },
    ) {
        if (readerDestination != null) {
            key(readerDestination.tabId) { ReaderBar(session, readerDestination, start = true) }
        }
        DockLayout(
            workspace = session.panesOf(openWindow.groupId, navigation = true),
            modifier = Modifier.weight(1f).fillMaxHeight().onSizeChanged { outerWidth = it.width },
            layeredSides = NavigationLayeredSides,
            splitter = { ReaderSplitter() },
            panel = { panel -> PaneCard { panel() } },
        ) {
            Column(Modifier.fillMaxSize()) {
                DockLayout(
                    workspace = lineWorkspace,
                    modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { innerSize = it },
                    sideOrder = LineSideOrder,
                    layeredSides = LineLayeredSides,
                    // Pardes: a pane dropped at the window's edge runs its full length
                    outerBandSides = LineOuterBandSides,
                    splitter = { ReaderSplitter() },
                    panel = { panel -> DockedPaneCard(bottomOpen) { panel() } },
                ) {
                    // A book's text first lays out once its panes are in place, as with the split panes.
                    // Pardes: the tab beside the one selected as well, which has no panes of its own (request 59 of stage 3)
                    CompositionLocalProvider(
                        LocalBookTextReady provides { tabId -> openWindow.panesReadyFor == tabId || openWindow.shownBeside(tabId) },
                    ) {
                        TabsContent()
                    }
                }
                if (readerDestination != null) {
                    key(readerDestination.tabId) { ReaderBreadcrumb(session, readerDestination) }
                }
            }
        }
        if (readerDestination != null) {
            key(readerDestination.tabId) { ReaderBar(session, readerDestination, start = false) }
        }
    }
}

/**
 * Pardes: the keys of the window's selected reader tab [destination], whose events are [events]; none with no reader
 * tab. The book view's shortcuts, of which the book tree's (Ctrl/Cmd+B) is in a search tab its tree of the books found
 * (request 97 of stage 3); the daf's key (Ctrl/Cmd+D), the daf button's click of the tab (request 94); and F6, which
 * goes round the parts of the window that take keys (request 100).
 */
@Composable
private fun rememberReaderKeys(
    openWindow: OpenWindow,
    destination: TabsDestination?,
    events: ((BookContentEvent) -> Unit)?,
): ((KeyEvent) -> Boolean)? {
    val dafKeys = LocalAppGraph.current.dafKeys
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    if (events == null) return null
    val searchTree = if (destination is TabsDestination.Search) rememberSearchTreeToggle(events) else null
    val onEvent = searchTree?.onEvent ?: events
    return { event ->
        handleBookShortcut(event, onEvent) || dafKeys.press(event, destination?.tabId) || openWindow.focusStops.cycle(event, rtl)
    }
}

@Composable
private fun ReaderBreadcrumb(
    session: DesktopSession,
    destination: TabsDestination,
) {
    val viewModel = tabBookViewModel(session.ownerOf(destination.tabId), destination)
    val uiState by viewModel.uiState.collectAsState()
    if (isBookTextShown(uiState)) BookBreadcrumb(uiState = uiState, onEvent = viewModel::onEvent)
}

/** One of the two activity bars of a reader tab (start: navigation panes; end: text and line panes). */
@Composable
private fun ReaderBar(
    session: DesktopSession,
    destination: TabsDestination,
    start: Boolean,
) {
    val viewModel = tabBookViewModel(session.ownerOf(destination.tabId), destination)
    val uiState by viewModel.uiState.collectAsState()
    val diacritics by viewModel.diacritics.collectAsState()
    // Pardes: a search's results have the navigation bar too (Zayit's have none, their tabs filter), with the book
    // tree's button in its own place for the tree of the books found (request 97 of stage 3)
    val searchTree = (destination as? TabsDestination.Search)?.let { rememberSearchTreeToggle(viewModel::onEvent) }
    Box(Modifier.fillMaxHeight()) {
        if (start) {
            StartVerticalBar(
                uiState = uiState,
                onEvent = searchTree?.onEvent ?: viewModel::onEvent,
                bookTreeShown = searchTree?.shown ?: uiState.navigation.isVisible,
            )
        } else if (uiState.navigation.selectedBook != null || destination is TabsDestination.Search) {
            EndVerticalBar(uiState = uiState, onEvent = viewModel::onEvent, diacritics = diacritics)
        }
    }
}

@Composable
internal fun canvasBackground() = JewelTheme.globalColors.toolwindowBackground
