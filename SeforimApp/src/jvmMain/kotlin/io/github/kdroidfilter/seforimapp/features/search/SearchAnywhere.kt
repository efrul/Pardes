package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.components.rememberDesktopOpener
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.rememberHomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.UnifiedSearchBar
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.TabSplit
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_anywhere_keys
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Pardes (request 85 of docs/DESKTOP_STAGE3_PLAN.md, a search from anywhere): the home's search bar opened over any
// screen of the window by Ctrl/Cmd+L, as a browser's address bar is, with no button of its own in the title bar (the
// user's word, 2026-11-24: "בלי אייקון חדש לחיפוש המלא"). What it finds opens in the tab it was opened over, as the
// address bar's does ("פתיחת תוצאה מהחיפוש החדש תהיה באותה כרטיסיה").
//
// Since request 96 ("כיום השורה נפתחת ורק שניה אחרי זה מודגשת ברקע המוזהב, שיהיה מיד. שיוכלו לבחור האם יפתח באותו חלון
// או חדש, בדומה ללחיצה עם קונטרול וכדומה") the bar has the keys as it opens, and the keys held as a row is chosen say
// where it opens, as a browser's address bar reads them ([opensIn]).

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

/** Where what the bar found opens: the tab it was opened over, a new tab, or a new window. */
internal enum class OpensIn { Place, NewTab, NewWindow }

/**
 * Where what the bar found opens, by the keys held as it was chosen, as a browser's address bar reads them: [shift], a
 * new window; [alt], a new tab, and so Ctrl or Cmd ([ctrl]) with a click (with Enter they are the bar's own key, its
 * search in every book: [byEnter]); else the tab the bar was opened over.
 */
internal fun opensIn(
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean,
    byEnter: Boolean,
): OpensIn =
    when {
        shift -> OpensIn.NewWindow
        alt || (ctrl && !byEnter) -> OpensIn.NewTab
        else -> OpensIn.Place
    }

/**
 * The keys held as a row of the bar is chosen. What the row opens comes a moment after (a book is read, a reference
 * resolved), so a key let go since still counts: each is remembered [HELD] long, and so is Enter, by which a row was
 * chosen rather than by a click.
 */
internal class HeldKeys(
    private val now: () -> TimeMark = TimeSource.Monotonic::markNow,
) {
    private var ctrl: TimeMark? = null
    private var alt: TimeMark? = null
    private var shift: TimeMark? = null
    private var enter: TimeMark? = null
    private var last = Keys()

    /** The modifier keys as they are now ([keys]): those held, or just let go, are noted. */
    fun note(keys: Keys) {
        val at = now()
        if (keys.ctrl || last.ctrl) ctrl = at
        if (keys.alt || last.alt) alt = at
        if (keys.shift || last.shift) shift = at
        last = keys
    }

    /** Enter was pressed in the bar. */
    fun noteEnter() {
        enter = now()
    }

    /** Where what was just chosen opens. */
    fun opensIn(): OpensIn =
        opensIn(ctrl.isRecent() || last.ctrl, alt.isRecent() || last.alt, shift.isRecent() || last.shift, enter.isRecent())

    private fun TimeMark?.isRecent() = this != null && elapsedNow() < HELD

    /** The modifier keys held: Ctrl or Cmd, Alt, Shift. */
    data class Keys(
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val shift: Boolean = false,
    )

    private companion object {
        val HELD = 700.milliseconds
    }
}

/** What a search bar's [event] opens, in the tab the bar acts in; null for a desktop's link, which is no tab. */
internal fun tabDestination(event: SearchHomeNavigationEvent): TabsDestination? =
    when (event) {
        is SearchHomeNavigationEvent.NavigateToSearch -> TabsDestination.Search(searchQuery = event.query, tabId = event.tabId)
        is SearchHomeNavigationEvent.NavigateToBookContent ->
            TabsDestination.BookContent(bookId = event.bookId, tabId = event.tabId, lineId = event.lineId)
        is SearchHomeNavigationEvent.NavigateToDeepLink -> event.destination
        is SearchHomeNavigationEvent.OpenDesktop -> null
    }

/**
 * This window's search from anywhere, over the window while it is open. In the title bar, which it takes no room in:
 * the bar opens right under it.
 */
@Composable
fun SearchAnywhere() {
    val openWindow = LocalOpenWindow.current
    val visible by openWindow.searchAnywhereVisible.collectAsState()
    // The title bar's bottom, in the window: from the box's place, its bounds being those of no room at all
    var titleBarBottom by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxHeight().onGloballyPositioned { titleBarBottom = it.positionInWindow().y.roundToInt() + it.size.height }) {
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
    val openWindow = LocalOpenWindow.current
    // The bar's own state, as a results page's has its own; gone with the bar.
    val bar = remember { graph.desktopManager.newSearchBarViewModel() }
    DisposableEffect(bar) { onDispose { bar.viewModelScope.cancel() } }
    // The tab what the bar finds opens as, in the selected tab's place: the bar keeps its search's state under this id.
    val tabId = remember { UUID.randomUUID().toString() }
    val searchUi by bar.uiState.collectAsState()
    val callbacks = rememberHomeSearchCallbacks(bar) { tabId }
    val openDesktop = rememberDesktopOpener()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // The keys held as a row is chosen: where it opens (request 96)
    val held = rememberHeldKeys()
    LaunchedEffect(bar, openWindow) {
        bar.navigationEvents.collect { event ->
            if (event is SearchHomeNavigationEvent.OpenDesktop) {
                openDesktop(event)
            } else {
                tabDestination(event)?.let { openWindow.open(it, held.opensIn(), graph.desktopManager) }
            }
            currentOnDismiss()
        }
    }
    // The field has the keys as the bar opens, with no wait for the home's own bar's (request 96)
    val barFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { barFocus.requestFocus() }
    }
    Column(
        modifier
            .shadow(16.dp, BarShape)
            .background(JewelTheme.globalColors.panelBackground, BarShape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, BarShape)
            // A press on the bar's own frame is not one beside it.
            .pointerInput(Unit) { detectTapGestures { } }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Enter || event.key == Key.NumPadEnter) held.noteEnter()
                closesSearchAnywhere(event).also { if (it) onDismiss() }
            }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UnifiedSearchBar(
            searchUi = searchUi,
            searchCallbacks = callbacks,
            autoFocus = false,
            focusRequester = barFocus,
            trailing = { NextSearchModeSwitch() },
        )
        Text(
            stringResource(
                Res.string.search_anywhere_keys,
                if (PlatformInfo.isMacOS) "⌥" else "Alt",
                if (PlatformInfo.isMacOS) "⌘" else "Ctrl",
            ),
            color = JewelTheme.globalColors.text.disabled,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** The keys held in the window while the bar is open, for where a row chosen opens ([HeldKeys]). */
@Composable
private fun rememberHeldKeys(): HeldKeys {
    val held = remember { HeldKeys() }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(held, windowInfo) {
        snapshotFlow { windowInfo.keyboardModifiers }.collect { keys ->
            held.note(HeldKeys.Keys(keys.isCtrlPressed || keys.isMetaPressed, keys.isAltPressed, keys.isShiftPressed))
        }
    }
    return held
}

// Escape, or the key that opened the bar: the window's own handler does not get the keys while the bar has them.
private fun closesSearchAnywhere(event: KeyEvent): Boolean =
    isSearchAnywhereKey(event) || (event.type == KeyEventType.KeyDown && event.key == Key.Escape)

/** [destination] opened [where] the keys held say: in the selected tab's place, in a new tab, or in a new window. */
private fun OpenWindow.open(
    destination: TabsDestination,
    where: OpensIn,
    desktopManager: DesktopManager,
) {
    if (where == OpensIn.Place) return openInPlace(destination)
    tabsViewModel.openTab(destination)
    if (where == OpensIn.NewWindow) desktopManager.detachTabToNewWindow(destination.tabId, id)
}

/**
 * [destination] opened in the selected tab's place: a fresh tab there, as the Home button's is (the tab's ViewModels
 * are of what it showed), under the id the bar gave it. It keeps the pin of the tab it takes the place of, and its
 * half of a split.
 */
private fun OpenWindow.openInPlace(destination: TabsDestination) {
    val group = group()
    val current = group?.selectedId ?: return tabsViewModel.openTab(destination)
    val split = session.splits.saved(groupId)?.takeIf { current in it }
    session.addTab(
        destination,
        groupId,
        index = group.ids.indexOf(current).coerceAtLeast(0),
        replacing = current,
        pinned = session.isPinned(current),
    )
    split?.let { session.splits.restore(groupId, it.with(destination.tabId, inPlaceOf = current)) }
}

/** This pair with [tabId] where [inPlaceOf] was. */
internal fun TabSplit.with(
    tabId: String,
    inPlaceOf: String,
): TabSplit = if (first == inPlaceOf) copy(first = tabId) else copy(second = tabId)

private const val BAR_WIDTH_SHARE = 0.6f
