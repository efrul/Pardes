package io.github.kdroidfilter.seforimapp.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.kdroid.gematria.converter.toHebrewNumeral
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.SavedSession
import io.github.kdroidfilter.seforimapp.android.data.SearchScope
import io.github.kdroidfilter.seforimapp.android.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** What a tab shows: the desktop TabsDestination (Home, BookContent, Search, History, Favorites). */
@Serializable
sealed interface Destination {
    @Serializable
    @SerialName("home")
    data object Home : Destination

    /**
     * [findQuery]: set when a search result is opened, so the reader highlights the searched words (desktop seeds its find bar).
     * [endLineId]: the last line of a passage to mark (the day's limud, an aliya); [shnayimMikra]: open in that mode;
     * [openNotes]: open the notes pane on [lineId] (desktop TabsDestination.BookContent's endLineId, shnayimMikra, openNotes).
     */
    @Serializable
    @SerialName("book")
    data class Book(
        val bookId: Long,
        val lineId: Long? = null,
        val title: String = "",
        val findQuery: String? = null,
        val endLineId: Long? = null,
        val shnayimMikra: Boolean = false,
        val openNotes: Boolean = false,
    ) : Destination

    /**
     * [id] tells searches apart: a tab keeps its search view model, so a new search opened in it must re-run. [scope]
     * is where a fresh tab runs it (a search reopened from history runs where it first ran), [globalExtended] searches
     * all books rather than the base ones (desktop TabsDestination.Search).
     */
    @Serializable
    @SerialName("search")
    data class Search(
        val query: String,
        val id: String = UUID.randomUUID().toString(),
        val scope: SearchScope = SearchScope.Global,
        val globalExtended: Boolean = false,
    ) : Destination

    /** Full visit-history page (desktop TabsDestination.History, the chrome://history equivalent). */
    @Serializable
    @SerialName("history")
    data object History : Destination

    /** Favorites page (desktop TabsDestination.Favorites, the chrome://bookmarks equivalent). */
    @Serializable
    @SerialName("favorites")
    data object Favorites : Destination

    /** An author's page (desktop features/author), opened from search suggestions, the knowledge panel and book details. */
    @Serializable
    @SerialName("author")
    data class Author(val authorId: Long, val name: String = "") : Destination
}

/**
 * One tab (desktop TabItem). [pinned] tabs stay ahead of the others and survive the bulk closes; [shortTitle] is what a
 * pinned book tab shows (its acronym). [openerId] is the tab it was opened from: Back returns there (Chrome Android).
 * [generation] counts the navigations in this tab, so a destination opened again in it (the open book picked in the
 * tree) starts from fresh content like desktop's new tab id, not from a composition whose ViewModels were cleared.
 */
@Serializable
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val destination: Destination,
    val title: String,
    val pinned: Boolean = false,
    val shortTitle: String = "",
    val openerId: String? = null,
    val generation: Int = 0,
)

/** A workspace, "מרחב" (desktop VirtualDesktop): a named set of tabs, kept while another one is shown. */
@Serializable
data class Workspace(val id: String, val name: String)

/** The tabs of a workspace that is not shown (desktop DesktopTabsSnapshot, one window). */
@Serializable
data class WorkspaceSnapshot(val tabs: List<Tab> = emptyList(), val selectedIndex: Int = 0)

/**
 * What a tab remembers beyond its destination, restored with the session and by "reopen closed tab": the Android
 * subset of desktop BookContentPersistedState (contentAnchorLineId, the breadcrumb's TOC label, isBookTreeVisible) and
 * SearchPersistedState (query, selectedCategoryIds, scrollIndex, scrollOffset, anchorId).
 */
@Serializable
data class TabState(
    val anchorLineId: Long = -1L,
    val heading: String = "",
    val bookTitle: String = "",
    val searchQuery: String = "",
    /** Desktop isBookTreeVisible: the tablet Home shows its book tree. */
    val bookTreeVisible: Boolean = true,
    /** The search results' category tab (its category and sub-categories) and where the list stood. */
    val searchCategoryIds: Set<Long> = emptySet(),
    val searchScrollIndex: Int = 0,
    val searchScrollOffset: Int = 0,
    val searchAnchorId: Long = -1L,
)

/** Full-screen routes that sit above the tabbed browser (the desktop opens these as windows). */
sealed interface Overlay {
    data object Settings : Overlay

    data object Tools : Overlay

    data class Tool(val tool: ToolKind) : Overlay

    /** Phone: the open tabs and the workspaces. */
    data object Tabs : Overlay
}

enum class ToolKind(val title: String) {
    Siddur("סידור חכם"),
    HebrewCalendar("לוח עברי"),
    Dictionary("מילון"),
    DailyStudy("לימוד יומי"),
    Molad("מולד וקידוש לבנה"),
    ShnayimMikra("שנים מקרא ואחד תרגום"),
    DailyPrayer("תפילת היום"),
    Measures("ממיר מידות"),
    YearCycle("מעגל השנה"),
    Zmanim("זמני היום"),
}

data class AppUiState(
    val tabs: List<Tab> = listOf(homeTab()),
    val selectedIndex: Int = 0,
    val overlays: List<Overlay> = emptyList(),
    /**
     * Part of each overlay page's saved-state key: bumped when a page opened anew drops the ones a link put aside
     * ([AppViewModel.push]), which are still kept under the old one until the shell lets them go.
     */
    val overlayEpoch: Int = 0,
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspaceId: String = "",
    val canReopenClosedTab: Boolean = false,
) {
    val selectedTab: Tab get() = tabs[selectedIndex.coerceIn(0, tabs.lastIndex)]
    val activeWorkspace: Workspace? get() = workspaces.firstOrNull { it.id == activeWorkspaceId }

    companion object {
        const val HOME_TITLE = "דף הבית"
    }
}

/** One-off things the shell shows (desktop: the pinned-tab hint; Android: the undo bar after a close). */
sealed interface ShellEvent {
    data class TabClosed(val title: String, val tabId: String) : ShellEvent

    data object ClosePinnedHint : ShellEvent
}

fun homeTab(): Tab = Tab(destination = Destination.Home, title = AppUiState.HOME_TITLE)

/** Desktop desktop_default_name: "מרחב " + the Hebrew numeral and a geresh ("מרחב א׳", "מרחב ב׳"…). */
fun workspaceName(number: Int): String = "מרחב " + number.toHebrewNumeral(false) + "׳"

/** Desktop DesktopSwitcher MAX_DESKTOP_NAME_LENGTH. */
const val MAX_WORKSPACE_NAME_LENGTH = 8

/** Whether the tab has a find-in-page bar (desktop WindowUtils.hasFindInPage): search results, or a book. */
fun Destination.hasFindInPage(): Boolean = this is Destination.Book || this is Destination.Search

/**
 * What a pinned book tab shows instead of the book icon: the book's acronym, else its title. The acronym table also
 * holds search variants ("משנב", "רמבם הלכות שבת"), so a real acronym (with a gershayim) is preferred, single word
 * first, the shortest of them. Verbatim port of desktop core/presentation/tabs/PinnedTabLabel.kt.
 */
fun pinnedTabLabel(bookTitle: String, acronyms: List<String>): String {
    val marked = acronyms.map(String::trim).filter { '"' in it || '״' in it }
    return (marked.filter { ' ' !in it }.ifEmpty { marked }).minByOrNull(String::length) ?: bookTitle
}

/**
 * The ViewModels of one tab (desktop TabsContent.kt SimpleTabViewModelOwner): screens inside a tab get it as
 * LocalViewModelStoreOwner, so `viewModel()` there is per tab; the store is cleared when the tab closes or navigates.
 */
class TabViewModelOwner(private val application: Application) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply { set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application) }
}

private class ClosedTab(val tab: Tab, val state: TabState?)

/** A link's tab ([tabId]) and the pages it put aside, with the [AppUiState.overlayEpoch] their saved state is kept under. */
private class LinkReturn(val tabId: String, val overlays: List<Overlay>, val epoch: Int)

/**
 * The tabbed browser shared by every screen. Mirrors desktop 0.6.1:
 * - seforim/tabs/TabsViewModel.kt: open next to the opener, replace, close (a pinned tab on a second press), the bulk
 *   closes that skip pinned tabs, pin;
 * - framework/desktop/DesktopManager.kt and DesktopSession.kt: workspaces ("מרחבים", one window on Android), the
 *   stack of the last 25 closed tabs, moving a tab to another workspace;
 * - core/presentation/tabs/TabsContent.kt: one ViewModelStore per tab ([ownerFor]);
 * - framework/session/SessionManager.kt: the session, saved 2 s after any change and restored at start when
 *   "שמור מצב בין הפעלות" is on. Independently of that setting, the session also survives a process the system
 *   killed in the background (SavedStateHandle), as Android apps are expected to.
 *
 * Every call is meant for the main thread.
 */
@OptIn(FlowPreview::class)
class AppViewModel(application: Application, private val handle: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as ZayitApplication

    private val defaultWorkspace = Workspace(UUID.randomUUID().toString(), workspaceName(1))
    private val _state = MutableStateFlow(AppUiState(workspaces = listOf(defaultWorkspace), activeWorkspaceId = defaultWorkspace.id))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val owners = HashMap<String, TabViewModelOwner>()
    private val homeOwners = HashMap<String, TabViewModelOwner>()
    private val tabStates = HashMap<String, TabState>()
    private val dormant = HashMap<String, WorkspaceSnapshot>()
    private val closedTabs = HashMap<String, ArrayDeque<ClosedTab>>()
    private val restoredIds = HashSet<String>()

    private val _events = MutableSharedFlow<ShellEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ShellEvent> = _events.asSharedFlow()

    private val _findRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Tab ids whose find-in-page bar the title bar's magnifier or Ctrl+F asked for. */
    val findRequests: SharedFlow<String> = _findRequests.asSharedFlow()

    /** True once a session (saved state or file) was applied, or the start went without one. */
    @Volatile
    var sessionApplied = false
        private set

    private val saveTicks = MutableStateFlow(0L)

    /** The tab a link opened and the pages it put aside ([openLink]). */
    private var linkReturn: LinkReturn? = null
    private var hintedPinnedTabId: String? = null
    private var hintJob: Job? = null

    init {
        // A process the system killed in the background comes back with its tabs (Android convention).
        handle.get<String>(KEY_SESSION)?.let { saved ->
            runCatching { SessionStore.decode(saved) }.getOrNull()?.let {
                applySession(it)
                sessionApplied = true
            }
        }
        viewModelScope.launch { saveTicks.drop(1).debounce(SAVE_DEBOUNCE_MS).collect { saveNow() } }
        // Desktop setPersistSessionEnabled(false) drops the saved session at once (not only at the next save).
        viewModelScope.launch {
            app.prefs.persistSession.drop(1).collect { persist -> if (!persist) app.appScope.launch(Dispatchers.IO) { SessionStore.delete(app) } }
        }
    }

    // ---- Tabs ----

    /** Navigates the selected tab (desktop replaceCurrentTabWithNewTabId: fresh ViewModels, same place and pin). */
    fun openInCurrentTab(destination: Destination, title: String) {
        val current = state.value.selectedTab
        owners[current.id]?.viewModelStore?.clear()
        tabStates.remove(current.id)
        restoredIds.remove(current.id)
        mutate { s ->
            val tabs = s.tabs.toMutableList()
            tabs[s.selectedIndex.coerceIn(0, tabs.lastIndex)] =
                current.copy(destination = destination, title = title, shortTitle = "", generation = current.generation + 1)
            s.copy(tabs = tabs, overlays = s.overlays.filterNot { it == Overlay.Tabs })
        }
        onOpened(current.id, destination)
    }

    /** A tab opened from another one lands next to it, on the "+" side, and remembers its opener (desktop openTab). */
    fun openInNewTab(destination: Destination, title: String) {
        val opener = state.value.selectedTab
        val tab = Tab(destination = destination, title = title, openerId = opener.id)
        mutate { s ->
            val tabs = s.tabs.toMutableList()
            val index = constrained(tabs, pinned = false, index = s.selectedIndex + 1)
            tabs.add(index, tab)
            s.copy(tabs = tabs, selectedIndex = index, overlays = s.overlays.filterNot { it == Overlay.Tabs })
        }
        onOpened(tab.id, destination)
    }

    /**
     * What a zayit:// link points to, in a new tab above the pages shown (settings, tools…): they are put aside, and
     * Back from that tab ([closeTabBack]) brings them back. Desktop opens the tab in its main window and leaves its
     * settings or tool window as it was.
     */
    fun openLink(destination: Destination, title: String) {
        val shown = state.value.overlays
        _state.update { it.copy(overlays = emptyList()) }
        openInNewTab(destination, title)
        if (shown.isNotEmpty()) linkReturn = LinkReturn(state.value.selectedTab.id, shown, state.value.overlayEpoch)
    }

    /**
     * The pages a link put aside ([openLink]) while its tab is open: the shell keeps their saved state (the settings
     * sub-page shown, a list's scroll), for Back to bring them back as they were rather than at their start.
     */
    fun overlaysPutAside(): Pair<Int, List<Overlay>> = linkReturn?.let { it.epoch to it.overlays } ?: (0 to emptyList())

    /** Back on a tab opened from another: back to that one, with the pages a link put aside ([openLink]). */
    fun closeTabBack(index: Int) {
        val tab = state.value.tabs.getOrNull(index) ?: return
        val putAside = linkReturn?.takeIf { it.tabId == tab.id }
        close(listOf(tab.id), notify = false)
        if (putAside != null) _state.update { it.copy(overlays = putAside.overlays, overlayEpoch = putAside.epoch) }
    }

    /** The "+" button and Ctrl+T: a fresh Home tab at the "+" end of the strip (desktop OnAdd). */
    fun newHomeTab() {
        val tab = homeTab()
        mutate { s ->
            val tabs = s.tabs + tab
            s.copy(tabs = tabs, selectedIndex = tabs.lastIndex, overlays = s.overlays.filterNot { it == Overlay.Tabs })
        }
    }

    fun goHome() = openInCurrentTab(Destination.Home, AppUiState.HOME_TITLE)

    /** Selects the History (or Favorites) tab when one is open, else opens it next to the current one (Ctrl+H). */
    fun showPage(destination: Destination) {
        val index = state.value.tabs.indexOfFirst { it.destination == destination }
        if (index >= 0) {
            selectTab(index)
        } else {
            openInNewTab(destination, if (destination == Destination.Favorites) "מועדפים" else "היסטוריה")
        }
    }

    fun selectTab(index: Int) {
        mutate { s -> s.copy(selectedIndex = index.coerceIn(0, s.tabs.lastIndex), overlays = s.overlays.filterNot { it == Overlay.Tabs }) }
    }

    fun selectTabById(tabId: String) {
        state.value.tabs.indexOfFirst { it.id == tabId }.takeIf { it >= 0 }?.let(::selectTab)
    }

    /** Ctrl+Tab / Ctrl+Shift+Tab, and the swipe on the phone title bar. */
    fun selectRelative(delta: Int) {
        val s = state.value
        if (s.tabs.size > 1) selectTab(((s.selectedIndex + delta) % s.tabs.size + s.tabs.size) % s.tabs.size)
    }

    /** Closes one tab; it can be reopened (desktop onTabClosed keeps it with its reading state). */
    fun closeTab(index: Int, notify: Boolean = true) {
        val tab = state.value.tabs.getOrNull(index) ?: return
        close(listOf(tab.id), notify)
    }

    /**
     * Ctrl+W on the selected tab. Chromium-like, a pinned tab only closes on a second press while the hint the first
     * one showed is still up; a held key ([repeat]) never is that second press.
     */
    fun closeSelectedTab(repeat: Boolean = false) {
        val tab = state.value.selectedTab
        if (tab.pinned) {
            val hinted = hintJob?.isActive == true && hintedPinnedTabId == tab.id
            if (!hinted) {
                hintedPinnedTabId = tab.id
                hintJob?.cancel()
                hintJob = viewModelScope.launch { delay(CLOSE_PINNED_HINT_MS) }
                _events.tryEmit(ShellEvent.ClosePinnedHint)
            }
            if (!hinted || repeat) return
        }
        hintJob?.cancel()
        close(listOf(tab.id), notify = false)
    }

    /** Pinned tabs stay; with none, one fresh Home tab is left (desktop CloseAll). */
    fun closeAll() {
        val tabs = state.value.tabs
        close((if (tabs.any { it.pinned }) tabs.filterNot { it.pinned } else tabs).map { it.id }, notify = false)
    }

    fun closeOthers(index: Int) = closeUnpinned(state.value.tabs.filterIndexed { i, _ -> i != index })

    /** The tabs before [index]: on the right of it in the RTL strip ("סגור את הלשוניות מימין"). */
    fun closeBefore(index: Int) = closeUnpinned(state.value.tabs.take(index))

    /** The tabs after [index]: on the left of it in the RTL strip ("סגור את הלשוניות משמאל"). */
    fun closeAfter(index: Int) = closeUnpinned(state.value.tabs.drop(index + 1))

    // Chromium's GetTabsClosedByCommand: the bulk closes skip pinned tabs.
    private fun closeUnpinned(tabs: List<Tab>) = close(tabs.filterNot { it.pinned }.map { it.id }, notify = false)

    private fun close(ids: Collection<String>, notify: Boolean) {
        if (ids.isEmpty()) return
        val s = state.value
        val closing = s.tabs.filter { it.id in ids }
        val stack = closedTabs.getOrPut(s.activeWorkspaceId) { ArrayDeque() }
        closing.forEach { tab ->
            // A Home tab is not worth reopening (desktop DesktopSession.forget).
            if (tab.destination != Destination.Home) {
                stack.addLast(ClosedTab(tab, tabStates[tab.id]))
                if (stack.size > MAX_CLOSED_TABS) stack.removeFirst()
            }
            forget(tab.id)
        }
        val remaining = s.tabs.filterNot { it.id in ids }.ifEmpty { listOf(homeTab()) }
        val selected = s.selectedTab
        val selectedIndex = if (selected.id in ids) {
            // Chrome: back to the opener when it is still there, else the tab that takes the closed one's place.
            remaining.indexOfFirst { it.id == selected.openerId }.takeIf { it >= 0 }
                ?: s.tabs.drop(s.selectedIndex + 1).firstOrNull { it.id !in ids }?.let { next -> remaining.indexOfFirst { it.id == next.id } }
                ?: remaining.lastIndex
        } else {
            remaining.indexOfFirst { it.id == selected.id }
        }
        mutate { it.copy(tabs = remaining, selectedIndex = selectedIndex.coerceIn(0, remaining.lastIndex)) }
        val closedOne = closing.singleOrNull()
        if (notify && closedOne != null && closedOne.destination != Destination.Home) _events.tryEmit(ShellEvent.TabClosed(closedOne.title, closedOne.id))
    }

    /** Brings back the last closed tab of this workspace next to the current one, at the place it was read (Ctrl+Shift+T). */
    fun reopenClosedTab() {
        val closed = closedTabs[state.value.activeWorkspaceId]?.removeLastOrNull() ?: return
        reopen(closed)
    }

    /**
     * The undo bar's "בטל": the tab it names, while this workspace still has it to reopen. Tabs closed since without a
     * bar (Back, "סגור את כל הלשוניות") are on the same stack, and the last one came back instead.
     */
    fun reopenClosedTab(tabId: String) {
        val stack = closedTabs[state.value.activeWorkspaceId] ?: return
        val index = stack.indexOfLast { it.tab.id == tabId }.takeIf { it >= 0 } ?: return
        reopen(stack.removeAt(index))
    }

    private fun reopen(closed: ClosedTab) {
        closed.state?.let { tabStates[closed.tab.id] = it }
        val tab = closed.tab.copy(destination = withAnchor(closed.tab.destination, closed.state), openerId = null)
        mutate { st ->
            val tabs = st.tabs.toMutableList()
            val index = constrained(tabs, tab.pinned, st.selectedIndex + 1)
            tabs.add(index, tab)
            st.copy(tabs = tabs, selectedIndex = index, overlays = st.overlays.filterNot { it == Overlay.Tabs })
        }
        (tab.destination as? Destination.Book)?.let { app.history.armBookVisit(it.bookId) }
    }

    /** Pins or unpins: a pinned tab joins the end of the pinned run, an unpinned one the start of the others. */
    fun setPinned(index: Int, pinned: Boolean) {
        val s = state.value
        val tab = s.tabs.getOrNull(index)?.takeIf { it.pinned != pinned } ?: return
        mutate { st ->
            val tabs = st.tabs.toMutableList().apply { removeAt(index) }
            val at = tabs.count { it.pinned }
            tabs.add(at, tab.copy(pinned = pinned))
            st.copy(tabs = tabs, selectedIndex = tabs.indexOfFirst { it.id == st.selectedTab.id }.coerceAtLeast(0))
        }
        val book = tab.destination as? Destination.Book
        if (pinned && book != null && tab.shortTitle.isBlank()) {
            viewModelScope.launch {
                val bookTitle = tabStates[tab.id]?.bookTitle?.ifBlank { null } ?: book.title.ifBlank { tab.title }
                val label = withContext(Dispatchers.IO) {
                    runCatching { pinnedTabLabel(bookTitle, app.libraryStore.repository().getAcronymsForBook(book.bookId)) }.getOrDefault(bookTitle)
                }
                updateTab(tab.id) { it.copy(shortTitle = label) }
            }
        }
    }

    /** Reorders a tab, kept on its side of the pinned run (desktop OnReorder + pinConstrained). */
    fun moveTab(from: Int, to: Int) {
        mutate { s ->
            val tab = s.tabs.getOrNull(from) ?: return@mutate s
            val tabs = s.tabs.toMutableList().apply { removeAt(from) }
            tabs.add(constrained(tabs, tab.pinned, to.coerceIn(0, tabs.size)), tab)
            s.copy(tabs = tabs, selectedIndex = tabs.indexOfFirst { it.id == s.selectedTab.id })
        }
    }

    /** The plain title a screen gives its tab. */
    fun updateTabTitle(tabId: String, title: String) = updateTab(tabId) { it.copy(title = title) }

    /** The reader's book title: the tab reads "<book> - <chapter>" (desktop BookContentViewModel title). */
    fun setBookTitle(tabId: String, bookTitle: String) {
        val old = tabStates[tabId] ?: TabState()
        if (old.bookTitle != bookTitle) {
            tabStates[tabId] = old.copy(bookTitle = bookTitle)
            changed()
        }
        applyBookTitle(tabId)
    }

    /**
     * Where the reader of [tabId] stands: its first visible line and the TOC heading there. Kept for the session and
     * for reopening the tab, and shown in the tab title.
     */
    fun reportReaderPosition(tabId: String, lineId: Long?, heading: String) {
        val old = tabStates[tabId] ?: TabState()
        val updated = old.copy(anchorLineId = lineId ?: old.anchorLineId, heading = heading)
        if (updated != old) {
            tabStates[tabId] = updated
            changed()
        }
        applyBookTitle(tabId)
    }

    /**
     * A query run inside a search tab: the tab title follows it, and it goes into the history (desktop
     * recordSearchVisit) with the [scope] and [globalExtended] it ran with, which the tab also keeps for the session.
     */
    fun reportSearchQuery(tabId: String, query: String, scope: SearchScope = SearchScope.Global, globalExtended: Boolean = false) {
        val q = query.trim()
        if (q.isEmpty()) return
        val old = tabStates[tabId] ?: TabState()
        // Another query starts at the top, on all its results
        tabStates[tabId] = if (old.searchQuery == q) old else old.copy(searchQuery = q, searchCategoryIds = emptySet(), searchScrollIndex = 0, searchScrollOffset = 0, searchAnchorId = -1L)
        updateTab(tabId) { t ->
            val d = t.destination as? Destination.Search
            t.copy(title = q, destination = d?.copy(query = q, scope = scope, globalExtended = globalExtended) ?: t.destination)
        }
        recordSearch(q, scope, globalExtended)
    }

    /** Where the results of search tab [tabId] stand: its category tab and its place in the list (desktop onScroll). */
    fun reportSearchView(tabId: String, categoryIds: Set<Long>, index: Int, offset: Int, anchorId: Long) {
        val old = tabStates[tabId] ?: TabState()
        val updated = old.copy(searchCategoryIds = categoryIds, searchScrollIndex = index, searchScrollOffset = offset, searchAnchorId = anchorId)
        if (updated != old) {
            tabStates[tabId] = updated
            changed()
        }
    }

    /** What search tab [tabId] showed when it was saved (a category tab, a place in the list), else null. */
    fun savedSearchView(tabId: String): TabState? = tabStates[tabId]?.takeIf { it.searchCategoryIds.isNotEmpty() || it.searchScrollIndex > 0 || it.searchScrollOffset > 0 }

    /** The magnifier and Ctrl+F: asks the selected tab for its find-in-page bar (desktop showFindBar). */
    fun requestFind(): Boolean {
        val tab = state.value.selectedTab
        if (!tab.destination.hasFindInPage()) return false
        _findRequests.tryEmit(tab.id)
        return true
    }

    fun isRestored(tabId: String): Boolean = tabId in restoredIds

    /** Whether the tablet Home of [tabId] shows its book tree (desktop NavigationState.isVisible, kept with the tab). */
    fun isBookTreeVisible(tabId: String): Boolean = tabStates[tabId]?.bookTreeVisible ?: true

    /** Desktop ToggleBookTree on a Home tab: remembered for the session and for reopening the tab. */
    fun setBookTreeVisible(tabId: String, visible: Boolean) {
        val old = tabStates[tabId] ?: TabState()
        if (old.bookTreeVisible == visible) return
        tabStates[tabId] = old.copy(bookTreeVisible = visible)
        changed()
    }

    /** The line the reader of [tabId] stands at, when known. */
    fun anchorOf(tabId: String): Long? = tabStates[tabId]?.anchorLineId?.takeIf { it > 0 }

    fun ownerFor(tabId: String): ViewModelStoreOwner = owners.getOrPut(tabId) { TabViewModelOwner(app) }

    /**
     * The ViewModels of [tabId]'s Home (its search bar's): kept while the tab shows a book opened in its place, as the
     * Home itself is (TabHost), and cleared only with the tab, not by [openInCurrentTab] as [ownerFor]'s are.
     */
    fun homeOwnerFor(tabId: String): ViewModelStoreOwner = homeOwners.getOrPut(tabId) { TabViewModelOwner(app) }

    /** [tabId]'s Home, kept behind a book, is let go (TabHost keeps only a few tabs composed): its ViewModels go with it. */
    fun dropHome(tabId: String) {
        homeOwners.remove(tabId)?.viewModelStore?.clear()
    }

    /** Ids of every tab the app still knows: shown, in another workspace, or reopenable. */
    fun knownTabIds(): Set<String> = buildSet {
        state.value.tabs.forEach { add(it.id) }
        dormant.values.forEach { snap -> snap.tabs.forEach { add(it.id) } }
        closedTabs.values.forEach { stack -> stack.forEach { add(it.tab.id) } }
    }

    // ---- Overlays ----

    fun push(overlay: Overlay) {
        // A page opened anew replaces the ones a link put aside, and starts afresh: under a new epoch, as the shell
        // keeps their saved state under the keys this page would otherwise take ("0:Settings" for both)
        val dropped = linkReturn != null
        linkReturn = null
        _state.update { s -> s.copy(overlays = s.overlays + overlay, overlayEpoch = s.overlayEpoch + if (dropped) 1 else 0) }
    }

    fun pop(): Boolean {
        var popped = false
        _state.update { s ->
            if (s.overlays.isEmpty()) {
                s
            } else {
                popped = true
                s.copy(overlays = s.overlays.dropLast(1))
            }
        }
        return popped
    }

    // ---- Workspaces (desktop DesktopManager, one window) ----

    /** Creates a workspace named after the next Hebrew numeral and switches to it (desktop createDesktop). */
    fun createWorkspace() {
        val s = state.value
        val workspace = Workspace(UUID.randomUUID().toString(), workspaceName(s.workspaces.size + 1))
        putDormant()
        mutate { st ->
            st.copy(
                workspaces = st.workspaces + workspace,
                activeWorkspaceId = workspace.id,
                tabs = listOf(homeTab()),
                selectedIndex = 0,
                overlays = st.overlays.filterNot { it == Overlay.Tabs },
            )
        }
    }

    /** The whole tab set of the shown workspace goes dormant and [id]'s comes back (desktop switchTo). */
    fun switchWorkspace(id: String) {
        val s = state.value
        if (id == s.activeWorkspaceId || s.workspaces.none { it.id == id }) return
        putDormant()
        val snapshot = dormant.remove(id)
        val tabs = snapshot?.tabs?.map { it.copy(destination = withAnchor(it.destination, tabStates[it.id])) }?.ifEmpty { null } ?: listOf(homeTab())
        restoredIds += tabs.map { it.id }
        mutate { st ->
            st.copy(activeWorkspaceId = id, tabs = tabs, selectedIndex = (snapshot?.selectedIndex ?: 0).coerceIn(0, tabs.lastIndex))
        }
    }

    /** Ctrl+Alt+→/← (and the swipe on the phone tabs page). */
    fun switchWorkspaceRelative(direction: Int) {
        val s = state.value
        if (s.workspaces.size <= 1) return
        val index = s.workspaces.indexOfFirst { it.id == s.activeWorkspaceId }.coerceAtLeast(0)
        switchWorkspace(s.workspaces[((index + direction) % s.workspaces.size + s.workspaces.size) % s.workspaces.size].id)
    }

    fun renameWorkspace(id: String, name: String) {
        val trimmed = name.trim().take(MAX_WORKSPACE_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        mutate { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(name = trimmed) else it }) }
    }

    /** Deletes a workspace and its tabs; the shown one gives way to its neighbour (desktop deleteDesktop). */
    fun deleteWorkspace(id: String) {
        val s = state.value
        if (s.workspaces.size <= 1) return
        val index = s.workspaces.indexOfFirst { it.id == id }
        if (index < 0) return
        if (id == s.activeWorkspaceId) switchWorkspace(s.workspaces[if (index > 0) index - 1 else index + 1].id)
        dormant.remove(id)?.tabs?.forEach { forget(it.id) }
        closedTabs.remove(id)
        mutate { st -> st.copy(workspaces = st.workspaces.filterNot { it.id == id }) }
    }

    fun moveWorkspace(from: Int, to: Int) {
        mutate { s ->
            if (from !in s.workspaces.indices || to !in s.workspaces.indices || from == to) return@mutate s
            s.copy(workspaces = s.workspaces.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    /**
     * Moves a tab to the end of another workspace with its reading state, under a new id (desktop moveTabToDesktop).
     * The last tab stays.
     */
    fun moveTabToWorkspace(index: Int, workspaceId: String) {
        val s = state.value
        val tab = s.tabs.getOrNull(index) ?: return
        if (s.tabs.size <= 1 || workspaceId == s.activeWorkspaceId || s.workspaces.none { it.id == workspaceId }) return
        val newId = UUID.randomUUID().toString()
        tabStates[tab.id]?.let { tabStates[newId] = it }
        val target = dormant[workspaceId] ?: WorkspaceSnapshot()
        dormant[workspaceId] = target.copy(tabs = target.tabs + tab.copy(id = newId, openerId = null))
        forget(tab.id)
        val remaining = s.tabs.filterNot { it.id == tab.id }
        val selectedId = if (tab.id == s.selectedTab.id) remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id else s.selectedTab.id
        mutate { st -> st.copy(tabs = remaining, selectedIndex = remaining.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)) }
    }

    // ---- Session (desktop SessionManager) ----

    fun buildSession(): SavedSession {
        val s = state.value
        val snapshots = dormant + (s.activeWorkspaceId to WorkspaceSnapshot(s.tabs, s.selectedIndex))
        val ids = snapshots.values.flatMap { snap -> snap.tabs.map { it.id } }.toSet()
        return SavedSession(
            workspaces = s.workspaces,
            activeWorkspaceId = s.activeWorkspaceId,
            snapshots = snapshots,
            tabStates = tabStates.filterKeys { it in ids },
        )
    }

    /** Applies the session read from disk at start, once; null when there is none (or the setting is off). */
    fun restoreFromDisk(saved: SavedSession?) {
        if (sessionApplied) return
        sessionApplied = true
        if (saved != null) applySession(saved)
    }

    /**
     * Saves the session: into the saved state (process death) and, when "שמור מצב בין הפעלות" is on, to disk. Called 2 s
     * after a change and when the app goes to the background (desktop saves on quit, which Android never announces).
     */
    fun saveNow() {
        val text = runCatching { SessionStore.encode(buildSession()) }.getOrNull() ?: return
        handle[KEY_SESSION] = text
        val persist = app.prefs.persistSession.value
        app.appScope.launch(Dispatchers.IO) { if (persist) SessionStore.write(app, text) else SessionStore.delete(app) }
    }

    private fun applySession(saved: SavedSession) {
        val workspaces = saved.workspaces.ifEmpty { return }
        val activeId = saved.activeWorkspaceId.takeIf { id -> workspaces.any { it.id == id } } ?: workspaces.first().id
        tabStates.clear()
        tabStates.putAll(saved.tabStates)
        dormant.clear()
        saved.snapshots.filterKeys { it != activeId && workspaces.any { w -> w.id == it } }.forEach { (id, snap) -> dormant[id] = snap }
        val active = saved.snapshots[activeId]
        // Restored tabs open where they were read; they record no history visit (desktop drop(1) on restore).
        val tabs = active?.tabs?.map { it.copy(destination = withAnchor(it.destination, tabStates[it.id])) }?.ifEmpty { null } ?: listOf(homeTab())
        restoredIds += tabs.map { it.id }
        _state.update {
            AppUiState(
                tabs = tabs,
                selectedIndex = (active?.selectedIndex ?: 0).coerceIn(0, tabs.lastIndex),
                workspaces = workspaces,
                activeWorkspaceId = activeId,
                // Kept: a page key must not come back while the shell may still hold its state
                overlayEpoch = it.overlayEpoch,
            )
        }
    }

    // ---- Internals ----

    private fun applyBookTitle(tabId: String) {
        val tab = state.value.tabs.firstOrNull { it.id == tabId } ?: return
        val book = tab.destination as? Destination.Book ?: return
        val st = tabStates[tabId]
        val base = st?.bookTitle?.ifBlank { null } ?: book.title.ifBlank { return }
        val heading = st?.heading.orEmpty()
        // Some books' first heading repeats the book title: no "X - X".
        val title = if (heading.isNotBlank() && heading != base) "$base - $heading" else base
        if (tab.title != title) updateTab(tabId) { it.copy(title = title) }
    }

    private fun onOpened(tabId: String, destination: Destination) {
        when (destination) {
            is Destination.Book -> {
                if (destination.title.isNotBlank()) tabStates[tabId] = TabState(bookTitle = destination.title)
                app.history.armBookVisit(destination.bookId)
            }
            is Destination.Search -> if (destination.query.isNotBlank()) {
                tabStates[tabId] = TabState(searchQuery = destination.query)
                recordSearch(destination.query, destination.scope, destination.globalExtended)
            }
            else -> Unit
        }
    }

    private fun recordSearch(query: String, scope: SearchScope, globalExtended: Boolean) {
        app.appScope.launch {
            runCatching { app.userData.history.recordSearchVisit(query, scope, globalExtended, searchScopeLabel(scope), System.currentTimeMillis()) }
        }
    }

    /** The name of the TOC entry or book a search is scoped to, shown in its history entry (desktop scopeLabel). */
    private suspend fun searchScopeLabel(scope: SearchScope): String? = runCatching {
        val repo = app.libraryStore.repository()
        when (scope) {
            SearchScope.Global, is SearchScope.Category -> null
            is SearchScope.Book -> repo.getBookCore(scope.bookId)?.title
            is SearchScope.Toc -> listOfNotNull(repo.getBookCore(scope.bookId)?.title, repo.getTocEntry(scope.tocId)?.text).joinToString(", ").ifBlank { null }
        }
    }.getOrNull()

    private fun withAnchor(destination: Destination, state: TabState?): Destination = when {
        // A marked passage runs from lineId: once the reader moved past its start, the mark is dropped (desktop never
        // restores markedRange) rather than restarted at the anchor. The search result's find bar is not brought back
        // either (desktop TabsDestination.BookContent has no find query).
        destination is Destination.Book && state != null && state.anchorLineId > 0 ->
            destination.copy(lineId = state.anchorLineId, endLineId = destination.endLineId.takeIf { state.anchorLineId == destination.lineId }, findQuery = null)
        destination is Destination.Book -> destination.copy(findQuery = null)
        destination is Destination.Search && state != null && state.searchQuery.isNotBlank() && state.searchQuery != destination.query ->
            destination.copy(query = state.searchQuery)
        else -> destination
    }

    /** Snapshots the shown workspace and drops its ViewModels (desktop putDesktopDormant). */
    private fun putDormant() {
        val s = state.value
        dormant[s.activeWorkspaceId] = WorkspaceSnapshot(s.tabs, s.selectedIndex)
        s.tabs.forEach {
            owners.remove(it.id)?.viewModelStore?.clear()
            homeOwners.remove(it.id)?.viewModelStore?.clear()
        }
    }

    private fun forget(tabId: String) {
        owners.remove(tabId)?.viewModelStore?.clear()
        homeOwners.remove(tabId)?.viewModelStore?.clear()
        tabStates.remove(tabId)
        restoredIds.remove(tabId)
        if (linkReturn?.tabId == tabId) linkReturn = null
    }

    private fun updateTab(tabId: String, change: (Tab) -> Tab) {
        mutate { s ->
            val index = s.tabs.indexOfFirst { it.id == tabId }
            if (index < 0) return@mutate s
            val updated = change(s.tabs[index])
            if (updated == s.tabs[index]) s else s.copy(tabs = s.tabs.toMutableList().apply { set(index, updated) })
        }
    }

    private inline fun mutate(block: (AppUiState) -> AppUiState) {
        _state.update { s -> block(s).let { it.copy(canReopenClosedTab = closedTabs[it.activeWorkspaceId]?.isNotEmpty() == true) } }
        changed()
    }

    private fun changed() {
        saveTicks.value++
    }

    /** Where a tab may stand: pinned tabs ahead of the others (desktop pinConstrained). */
    private fun constrained(tabs: List<Tab>, pinned: Boolean, index: Int): Int {
        val pinnedCount = tabs.count { it.pinned }
        return (if (pinned) index.coerceAtMost(pinnedCount) else index.coerceAtLeast(pinnedCount)).coerceIn(0, tabs.size)
    }

    override fun onCleared() {
        (owners.values + homeOwners.values).forEach { it.viewModelStore.clear() }
        owners.clear()
        homeOwners.clear()
    }

    private companion object {
        const val KEY_SESSION = "zayit_session"
        const val SAVE_DEBOUNCE_MS = 2_000L
        const val MAX_CLOSED_TABS = 25
        const val CLOSE_PINNED_HINT_MS = 3_000L
    }
}
