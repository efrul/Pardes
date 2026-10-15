package io.github.kdroidfilter.seforimapp.android.ui.shell

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import io.github.kdroidfilter.seforimapp.android.data.SearchScope
import io.github.kdroidfilter.seforimapp.android.ui.AppUiState
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.Tab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * What a screen inside a tab may ask of its tab (desktop: the tabId-keyed TabPersistedStateStore, AppSettings'
 * find-bar requests, BookContentViewModel's history rules). Read it with `LocalTabSession.current` (null outside a tab).
 */
@Stable
class TabSession internal constructor(
    val tabId: String,
    /** The tab is the one shown; hidden tabs stay composed (see [TabHost]) and must not record history. */
    val isFront: Boolean,
    /** The tab came back from the saved session or another workspace: it records no history visit (desktop drop(1)). */
    val restored: Boolean,
    private val appVm: AppViewModel,
    /** A Home kept composed while its tab shows what was opened in its place ([TabHost]). */
    val parked: Boolean = false,
) {
    /** Emits when the title bar's magnifier, Ctrl+F or the menu asks this tab for its find-in-page bar. */
    val findRequests: Flow<Unit> = appVm.findRequests.filter { it == tabId }.map { }

    /** The reader's first visible line and the chapter heading there: the session reopens the tab there. */
    fun reportReaderPosition(lineId: Long?, heading: String) = appVm.reportReaderPosition(tabId, lineId, heading)

    /** A query run in this search tab, in [scope] and [globalExtended]: the tab title follows it and it goes into the history. */
    fun reportSearchQuery(query: String, scope: SearchScope = SearchScope.Global, globalExtended: Boolean = false) =
        appVm.reportSearchQuery(tabId, query, scope, globalExtended)

    /** The search results' category tab and place in the list: the session brings the tab back there. */
    fun reportSearchView(categoryIds: Set<Long>, index: Int, offset: Int, anchorId: Long) =
        appVm.reportSearchView(tabId, categoryIds, index, offset, anchorId)
}

val LocalTabSession = compositionLocalOf<TabSession?> { null }

/**
 * False in a tab kept composed in the background (see [TabHost]). A Dialog, bottom sheet or popup is a window of its
 * own, drawn over whatever tab is shown, so one a tab opened is only shown while that tab is in front (true outside a tab).
 */
@Composable
fun isTabInFront(): Boolean = LocalTabSession.current?.isFront != false

/**
 * The shell's [AppViewModel], for screens inside a tab: there `viewModel<AppViewModel>()` resolves to the tab's own
 * ViewModelStore, whose factory cannot build an (Application, SavedStateHandle) ViewModel, and throws. Use
 * `LocalAppViewModel.current ?: viewModel()`.
 */
val LocalAppViewModel = staticCompositionLocalOf<AppViewModel?> { null }

/**
 * For the content of a Dialog or bottom sheet opened from inside a tab that has its own BackHandler: a dialog's
 * composition inherits the tab's LocalNavigationEventDispatcherOwner (see [TabHost]), whose handlers never see the
 * Back presses the dialog window receives. This gives the content back its own window's dispatcher.
 */
@Composable
fun WindowBackScope(content: @Composable () -> Unit) {
    val owner = LocalView.current.findViewTreeNavigationEventDispatcherOwner()
    if (owner == null) content() else CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, content = content)
}

/** Tabs kept composed besides the shown one (CLAUDE.md MAX_RETAINED_TAB_COMPOSITIONS); the others keep saved state. */
private const val MAX_RETAINED_TAB_COMPOSITIONS = 3

/** Back presses never reach a tab that is hidden or under an overlay. */
private class InertBackDispatcherOwner(override val lifecycle: Lifecycle) : OnBackPressedDispatcherOwner {
    override val onBackPressedDispatcher = OnBackPressedDispatcher()
}

/**
 * Identifies what a tab shows: a new destination in the same tab starts from a fresh composition and saved state. Its
 * Home is the exception ([homeKey]).
 */
private fun contentKey(tab: Tab): String = if (tab.destination == Destination.Home) homeKey(tab.id) else "${tab.id}:${tab.generation}:" + when (val d = tab.destination) {
    Destination.Home -> "home"
    is Destination.Book -> "book:${d.bookId}:${d.lineId}"
    is Destination.Search -> "search:${d.id}"
    Destination.History -> "history"
    Destination.Favorites -> "favorites"
    is Destination.Author -> "author:${d.authorId}"
}

/** A tab's Home: one composition and saved state for the tab's life, whatever the tab opened in its place since. */
private fun homeKey(tabId: String): String = "$tabId:home"

/** The layout id of a tab's Home, which stays composed beside the book a phone opened in its place ([TabHost]). */
private fun homeSlot(tabId: String): String = "$tabId#home"

/**
 * Hosts the tabs' screens like desktop core/presentation/tabs/TabsContent.kt:
 * - each tab gets its own ViewModelStoreOwner (desktop SimpleTabViewModelOwner) as LocalViewModelStoreOwner, so
 *   `viewModel()` in ReaderScreen or SearchScreen is per tab, and is cleared when the tab closes;
 * - the shown tab and the last [MAX_RETAINED_TAB_COMPOSITIONS] used stay composed (only the shown one is measured and
 *   placed), so switching back is instant and keeps scroll position, open panes and find bars; older tabs are
 *   disposed and come back from their saved state (rememberSaveable, through a SaveableStateHolder per tab);
 * - Back presses go to the shown tab only, and not while [backEnabled] is false (an overlay is shown): each tab's
 *   BackHandlers live in a child of the Activity's NavigationEventDispatcher that is disabled meanwhile (a Dialog
 *   opened from a tab with a BackHandler inside wraps its content in [WindowBackScope]);
 * - a tab's Home stays composed (parked: not placed, not in front) while the tab shows what was opened in its place
 *   (the catalog, the search bar and the library drawer open books in the Home's tab), as long as the tab stays among
 *   those kept composed, with ViewModels of its own ([AppViewModel.homeOwnerFor]). Back or "בית" then shows that same
 *   Home at once, scrolled where it was, instead of building a new one that fills in widget by widget. [content] is
 *   called for it with the tab's destination set to Home.
 */
@Composable
fun TabHost(
    state: AppUiState,
    appVm: AppViewModel,
    backEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (tab: Tab, front: Boolean) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val lru = remember { ArrayList<String>() }
    val selectedId = state.selectedTab.id
    lru.remove(selectedId)
    lru.add(0, selectedId)
    val liveIds = state.tabs.mapTo(HashSet()) { it.id }
    lru.retainAll(liveIds)
    while (lru.size > MAX_RETAINED_TAB_COMPOSITIONS + 1) lru.removeAt(lru.lastIndex)
    val shown = lru.toHashSet()

    // Saved states of contents that are gone: a tab navigated elsewhere, or a tab the app forgot entirely.
    val provided = remember { HashMap<String, String>() }
    SideEffect {
        val known = appVm.knownTabIds()
        state.tabs.forEach { tab ->
            val ck = contentKey(tab)
            // A Home's state is kept while the tab shows something else (the parked Home below)
            provided.put(tab.id, ck)?.takeIf { it != ck && it != homeKey(tab.id) }?.let(holder::removeState)
        }
        provided.entries.removeAll { (tabId, ck) ->
            (tabId !in known).also {
                if (it) {
                    holder.removeState(ck)
                    holder.removeState(homeKey(tabId))
                }
            }
        }
    }

    val realBack = LocalOnBackPressedDispatcherOwner.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val inert = remember(lifecycle) { InertBackDispatcherOwner(lifecycle) }
    // Tabs whose Home is composed: it stays so while the tab is kept composed, even once a book replaced it. A parked
    // Home the tab no longer keeps is let go whole, its saved state and ViewModels too: built again later, it starts
    // clean (the widgets' and catalog's values are kept app-wide), not half restored
    val parkedHomes = remember { HashSet<String>() }
    val letGo = parkedHomes.filter { it !in shown }
    parkedHomes.removeAll(letGo.toSet())
    if (letGo.isNotEmpty()) {
        SideEffect {
            letGo.forEach { id ->
                if (state.tabs.firstOrNull { it.id == id }?.destination != Destination.Home) {
                    holder.removeState(homeKey(id))
                    appVm.dropHome(id)
                }
            }
        }
    }

    Layout(
        modifier = modifier,
        content = {
            for (tab in state.tabs) {
                if (tab.id !in shown) continue
                key(tab.id) {
                    val front = tab.id == selectedId
                    val atHome = tab.destination == Destination.Home
                    if (atHome) parkedHomes.add(tab.id)
                    val slots = SlotScope(holder, appVm, backEnabled, realBack, inert)
                    if (tab.id in parkedHomes) {
                        // Generation 0 whatever the tab opened since: its navigations give the Home no new session
                        val home = tab.copy(destination = Destination.Home, generation = 0)
                        TabSlot(slots, home, homeSlot(tab.id), homeKey(tab.id), front && atHome, appVm.homeOwnerFor(tab.id), parked = !atHome) {
                            content(home, front && atHome)
                        }
                    }
                    if (!atHome) TabSlot(slots, tab, tab.id, contentKey(tab), front, appVm.ownerFor(tab.id)) { content(tab, front) }
                }
            }
        },
    ) { measurables, constraints ->
        val shownSlot = if (state.selectedTab.destination == Destination.Home) homeSlot(selectedId) else selectedId
        val placeable = measurables.firstOrNull { it.layoutId == shownSlot }?.measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable?.place(0, 0) }
    }
}

/** What every slot of a [TabHost] shares. */
private class SlotScope(
    val holder: SaveableStateHolder,
    val appVm: AppViewModel,
    val backEnabled: Boolean,
    val realBack: OnBackPressedDispatcherOwner?,
    val inert: OnBackPressedDispatcherOwner,
)

/**
 * One screen of [TabHost]: [content] laid out as [slotId], its saved state under [stateKey], [owner]'s ViewModels, and
 * Back, focus and the tab session ([active] when it is the screen shown).
 */
@Composable
private fun TabSlot(
    slots: SlotScope,
    tab: Tab,
    slotId: String,
    stateKey: String,
    active: Boolean,
    owner: ViewModelStoreOwner,
    parked: Boolean = false,
    content: @Composable () -> Unit,
) = key(slotId) {
    val appVm = slots.appVm
    val session = remember(tab.id, active, tab.destination, tab.generation, parked) { TabSession(tab.id, active, appVm.isRestored(tab.id), appVm, parked) }
    // activity-compose 1.13's BackHandler registers with LocalNavigationEventDispatcherOwner (the Activity's by default)
    // and only falls back to LocalOnBackPressedDispatcherOwner when there is none, so the inert owner below alone does not
    // keep a hidden tab, or the tab under an overlay, from taking Back. A child dispatcher of the Activity's, disabled
    // meanwhile, does.
    val navEvents = rememberNavigationEventDispatcherOwner(enabled = active && slots.backEnabled)
    // A field focused in a tab that goes to the background must not keep the keyboard.
    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(active) { if (!active && hasFocus) focusManager.clearFocus(force = true) }
    Box(Modifier.layoutId(slotId).fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }.focusGroup()) {
        key(stateKey) {
            slots.holder.SaveableStateProvider(stateKey) {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides owner,
                    LocalOnBackPressedDispatcherOwner provides (if (active && slots.backEnabled && slots.realBack != null) slots.realBack else slots.inert),
                    LocalNavigationEventDispatcherOwner provides navEvents,
                    LocalTabSession provides session,
                ) { content() }
            }
        }
    }
}
