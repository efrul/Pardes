package io.github.kdroidfilter.seforimapp.android.ui.shell

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.SessionStore
import io.github.kdroidfilter.seforimapp.android.data.VisitKind
import io.github.kdroidfilter.seforimapp.android.ui.AppUiState
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.Overlay
import io.github.kdroidfilter.seforimapp.android.ui.ShellEvent
import io.github.kdroidfilter.seforimapp.android.ui.Tab
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.home.HomeScreen
import io.github.kdroidfilter.seforimapp.android.ui.home.WideHome
import io.github.kdroidfilter.seforimapp.android.ui.library.LibraryTree
import io.github.kdroidfilter.seforimapp.android.ui.library.LibraryViewModel
import io.github.kdroidfilter.seforimapp.android.ui.reader.ReaderNav
import io.github.kdroidfilter.seforimapp.android.ui.reader.ReaderScreen
import io.github.kdroidfilter.seforimapp.android.ui.reader.drawerWidthFor
import io.github.kdroidfilter.seforimapp.android.ui.author.AuthorScreen
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchScreen
import io.github.kdroidfilter.seforimapp.android.ui.settings.AppUpdateDialog
import io.github.kdroidfilter.seforimapp.android.ui.settings.SettingsScreen
import io.github.kdroidfilter.seforimapp.android.ui.theme.ThemeMode
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.tools.ToolScreen
import io.github.kdroidfilter.seforimapp.android.ui.tools.ToolsScreen
import io.github.kdroidfilter.seforimapp.android.update.AppUpdateStrings
import io.github.kdroidfilter.seforimapp.android.update.AppUpdater
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Width from which the desktop-like multi-pane layout is used (Material "expanded"). */
val WideBreakpoint = 840.dp

/** Something from outside the app to open: a zayit:// link (desktop ContentDeepLinkHandler), or a launcher shortcut. */
sealed interface ShellEntry {
    data class Link(val uri: String) : ShellEntry

    /** The launcher's "לשונית חדשה": a new Home tab. */
    data object NewTab : ShellEntry
}

/**
 * The tabbed browser: desktop 0.6.1 main.kt + core/presentation/window/MainAppWindow.kt (title bar, shortcuts, window
 * title) + core/presentation/tabs/TabsContent.kt (see [TabHost]) + core/deeplink/ContentDeepLinkHandler.kt.
 * A phone (< [WideBreakpoint]) gets a top bar, the current tab's screen, a library drawer and full-screen overlays;
 * a tablet gets desktop's title bar with the tab strip and side islands.
 */
@OptIn(FlowPreview::class)
@Composable
fun ZayitApp(
    entry: ShellEntry? = null,
    onEntryHandled: () -> Unit = {},
    shortcuts: KeyShortcuts? = null,
    appVm: AppViewModel = viewModel(),
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    val activity = context as? Activity
    val state by appVm.state.collectAsState()
    val themeMode by app.prefs.themeMode.collectAsState()
    val library: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app.libraryStore.repository()))
    val c = Zayit.colors
    val scope = rememberCoroutineScope()
    // Desktop OpenWindow.tabSearchVisible (Ctrl+Shift+A) and the favorites popup; on a phone, bottom sheets.
    var tabSearchVisible by remember { mutableStateOf(false) }
    var favoritesVisible by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Links and launcher shortcuts open next to the current tab. A link puts the pages shown aside, for Back from its
    // tab to bring them back; the launcher's new tab leaves them.
    LaunchedEffect(entry) {
        val e = entry ?: return@LaunchedEffect
        tabSearchVisible = false
        favoritesVisible = false
        when (e) {
            ShellEntry.NewTab -> {
                repeat(appVm.state.value.overlays.size) { appVm.pop() }
                appVm.newHomeTab()
            }
            is ShellEntry.Link -> when (val d = parseZayitDeepLink(e.uri)) {
                // Guard against dangling references (e.g. a link from a newer database).
                is Destination.Book -> runCatching { app.libraryStore.repository().getBookCore(d.bookId) }.getOrNull()?.let { book ->
                    appVm.openLink(d.copy(title = book.title), book.title)
                }
                is Destination.Search -> appVm.openLink(d, d.query)
                else -> Unit
            }
        }
        onEntryHandled()
    }

    LaunchedEffect(appVm) {
        appVm.events.collect { event ->
            when (event) {
                // The bar names the tab and stays 10 s: within Short's 4 s it was often gone before "בטל" was reached
                is ShellEvent.TabClosed -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val message = if (event.title.isBlank()) "הלשונית נסגרה" else "הלשונית נסגרה: ${event.title}"
                    val result = snackbar.showSnackbar(message, actionLabel = "בטל", duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) appVm.reopenClosedTab(event.tabId)
                }
                ShellEvent.ClosePinnedHint ->
                    Toast.makeText(context, "לחץ שוב על Ctrl+W כדי לסגור את הלשונית המוצמדת", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // The library is open by now: move the history and favorites the app kept before it used desktop's tables.
    LaunchedEffect(Unit) { app.history.start() }

    // Desktop main.kt's appUpdateService.checkOnStartup, after the session is restored: a newer Pardes is found quietly,
    // said once in a snackbar, and kept in the title bar's badge; the version this updater just installed says so once.
    val updater = remember(app) { AppUpdater.get(app) }
    // Only whether a version is known: the shell is not recomposed at each tick of a download
    val updateBadge by remember(updater) { updater.state.map { it.showBadge }.distinctUntilChanged() }.collectAsState(updater.state.value.showBadge)
    val updateDialog by updater.dialogVisible.collectAsState()
    val announcement by updater.announcement.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(announcement) {
        val version = announcement ?: return@LaunchedEffect
        // Said where it is seen: not while the app is in the background
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        val shownAt = System.currentTimeMillis()
        val result = snackbar.showSnackbar(AppUpdateStrings.found.format(version), actionLabel = AppUpdateStrings.details, duration = SnackbarDuration.Long)
        // Said once it was used or stayed its time; one pushed away at once (a closed tab's bar) comes again next start
        if (result == SnackbarResult.ActionPerformed || System.currentTimeMillis() - shownAt >= ANNOUNCEMENT_SEEN_MS) updater.markAnnounced(version)
        if (result == SnackbarResult.ActionPerformed) updater.openDialog()
    }
    LaunchedEffect(updater) {
        val updated = withContext(Dispatchers.IO) { runCatching { updater.onShellStart() }.getOrNull() }
        if (updated != null) scope.launch { snackbar.showSnackbar(AppUpdateStrings.updatedTo.format(updated), duration = SnackbarDuration.Long) }
        updater.checkOnStartup()
    }
    // An update that needs no confirmation ends the process with no onStop: the tabs are written before it.
    DisposableEffect(updater, appVm) {
        updater.sessionText = {
            if (appVm.sessionApplied && app.prefs.persistSession.value) runCatching { SessionStore.encode(appVm.buildSession()) }.getOrNull() else null
        }
        onDispose { updater.sessionText = null }
    }

    // Launcher shortcuts follow the books read last (desktop jump list / dock menu).
    LaunchedEffect(Unit) {
        app.userData.history.revision.debounce(3_000).collect {
            runCatching {
                val recent = app.userData.history.query("", 30).filter { it.kind == VisitKind.BOOK }
                withContext(Dispatchers.IO) { LauncherShortcuts.update(context.applicationContext, recent) }
            }
        }
    }

    // Recents shows desktop's window title, under this app's name: "פרדס - [מרחב ב׳] - <tab>" (MainAppWindow.kt 131-148).
    val windowTitle = buildString {
        append("פרדס")
        if (state.workspaces.size > 1) state.activeWorkspace?.let { append(" - [${it.name}]") }
        val tab = state.selectedTab
        append(" - ")
        append(if (tab.destination is Destination.Search) "תוצאות חיפוש : ${tab.title}" else tab.title.ifBlank { AppUiState.HOME_TITLE })
    }
    LaunchedEffect(windowTitle) {
        activity?.setTaskDescription(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityManager.TaskDescription.Builder().setLabel(windowTitle).build()
            } else {
                // TaskDescription.Builder is API 33: before it, the label-only constructor (deprecated since)
                @Suppress("DEPRECATION")
                ActivityManager.TaskDescription(windowTitle)
            },
        )
    }

    // Desktop: "keep the screen on while a book is open".
    val keepOn by app.prefs.keepScreenOn.collectAsState()
    val reading = state.selectedTab.destination is Destination.Book && state.overlays.isEmpty()
    val window = activity?.window
    DisposableEffect(window, keepOn, reading) {
        if (keepOn && reading) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // F11: full screen (desktop WindowPlacement.Fullscreen).
    LaunchedEffect(window, fullscreen) {
        val w = window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(w, w.decorView).apply {
            if (fullscreen) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Hardware keyboard (desktop MainAppWindow shortcuts).
    val keyActions = remember(appVm) {
        ShellKeyActions(
            toggleTabSearch = { tabSearchVisible = !tabSearchVisible },
            toggleFullscreen = { fullscreen = !fullscreen },
        )
    }
    DisposableEffect(shortcuts, appVm) {
        shortcuts?.handler = { e -> handleShellShortcut(e, appVm, app.prefs, keyActions) }
        onDispose { shortcuts?.handler = null }
    }

    // Back: an overlay first; then (after the screen's own drawers and panes) back to the tab this one was opened
    // from, as Chrome on Android; else a page that is not Home goes Home; on Home the app closes.
    val tab = state.selectedTab
    val openerOpen = tab.openerId != null && state.tabs.any { it.id == tab.openerId }
    BackHandler(enabled = state.overlays.isNotEmpty()) { appVm.pop() }
    BackHandler(enabled = state.overlays.isEmpty() && !tab.pinned && (openerOpen || tab.destination !is Destination.Home)) {
        if (openerOpen) appVm.closeTabBack(state.selectedIndex) else appVm.goHome()
    }

    val actions = remember(appVm, themeMode, updateBadge) {
        TopBarActions(
            onHome = appVm::goHome,
            onFind = { appVm.requestFind() },
            onTabs = { appVm.push(Overlay.Tabs) },
            onNewTab = appVm::newHomeTab,
            onSettings = { appVm.push(Overlay.Settings) },
            onTools = { appVm.push(Overlay.Tools) },
            onHistory = { tabSearchVisible = true },
            onFavorites = { favoritesVisible = true },
            onSwipeTab = appVm::selectRelative,
            onAppUpdate = if (updateBadge) updater::openDialog else null,
            onCycleTheme = {
                // Desktop cycle: Light → Dark → System → Light.
                app.prefs.setThemeMode(
                    when (themeMode) {
                        ThemeMode.Light -> ThemeMode.Dark
                        ThemeMode.Dark -> ThemeMode.System
                        ThemeMode.System -> ThemeMode.Light
                    },
                )
            },
        )
    }

    CompositionLocalProvider(LocalAppViewModel provides appVm) {
        BoxWithConstraints(Modifier.fillMaxSize().background(c.ground)) {
            val wide = maxWidth >= WideBreakpoint
            // The title bars (phone top bar, page headers, tablet tab strip) lie under the status bar
            SystemBarsOver(status = c.titleBarTone, navigation = c.ground)
            Column(Modifier.fillMaxSize()) {
                if (wide) {
                    TabletTitleBar(
                        state = state,
                        appVm = appVm,
                        themeMode = themeMode,
                        actions = actions,
                        tabSearchVisible = tabSearchVisible,
                        onTabSearchVisible = { tabSearchVisible = it },
                        favoritesVisible = favoritesVisible,
                        onFavoritesVisible = { favoritesVisible = it },
                    )
                }
                // Tablets: keep panels and rails above the taskbar / gesture area (phones pad their own bottom bars).
                TabHost(
                    state = state,
                    appVm = appVm,
                    backEnabled = state.overlays.isEmpty(),
                    modifier = Modifier.weight(1f).fillMaxWidth().then(if (wide) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier),
                ) { t, _ ->
                    TabContent(t, wide, state.tabs.size, themeMode, actions, appVm, library)
                }
            }

            // Overlays: full-screen routes above the tabs (desktop opens these as windows). Only the top one is
            // composed; the ones under it keep their saved state (the tools list's scroll), as desktop's windows stay
            // open behind a tool, and a popped one starts afresh next time. Pages a link put aside keep theirs too:
            // Back from the link's tab brings Settings back on the sub-page it showed (desktop leaves that window as it
            // was). Read here, after the composition: putting aside always comes with a state change that recomposes.
            // Keys hold the overlay epoch: a page opened anew on the link's tab drops the ones put aside, and its key
            // differs from theirs, or it would come back on their state (Settings on its last sub-page, the siddur on
            // an old day) before this effect could let theirs go.
            val overlayStates = rememberSaveableStateHolder()
            val overlayKeys = state.overlays.mapIndexed { i, overlay -> "${state.overlayEpoch}:$i:$overlay" }
            val keptOverlays = rememberSaveable { mutableSetOf<String>() }
            SideEffect {
                val (asideEpoch, aside) = appVm.overlaysPutAside()
                val keep = overlayKeys + aside.mapIndexed { i, overlay -> "$asideEpoch:$i:$overlay" }
                keptOverlays.filterNot { it in keep }.forEach(overlayStates::removeState)
                keptOverlays.retainAll(keep.toSet())
                keptOverlays.addAll(overlayKeys)
            }
            state.overlays.lastOrNull()?.let { overlay ->
                Box(Modifier.fillMaxSize().background(c.ground)) {
                    overlayStates.SaveableStateProvider(overlayKeys.last()) {
                        when (overlay) {
                            Overlay.Settings -> SettingsScreen(onBack = { appVm.pop() }, wide = wide)
                            Overlay.Tools -> ToolsScreen(onBack = { appVm.pop() }, onOpen = { appVm.push(Overlay.Tool(it)) })
                            is Overlay.Tool -> ToolScreen(overlay.tool, onBack = { appVm.pop() })
                            Overlay.Tabs -> TabsOverview(state, appVm)
                        }
                    }
                }
            }

            if (!wide && tabSearchVisible) {
                TabSearchSheet(state, appVm, onDismiss = { tabSearchVisible = false })
            }
            if (!wide && favoritesVisible) {
                ShellSheet(onDismiss = { favoritesVisible = false }) { FavoritesMenuContent(appVm, tablet = false, onDismiss = { favoritesVisible = false }) }
            }
            if (updateDialog) AppUpdateDialog()

            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 64.dp),
            ) { data -> Snackbar(data, containerColor = c.text, contentColor = c.panel, actionColor = c.accent) }
        }
    }
}

/** How long the "new version" bar must stay up to count as seen (SnackbarDuration.Long is 10 s). */
private const val ANNOUNCEMENT_SEEN_MS = 8_000L

/** One tab's screen. */
@Composable
private fun TabContent(
    tab: Tab,
    wide: Boolean,
    tabCount: Int,
    themeMode: ThemeMode,
    actions: TopBarActions,
    appVm: AppViewModel,
    library: LibraryViewModel,
) {
    val nav = remember(tab.id, appVm, library) {
        ReaderNav(
            openBook = { book, lineId ->
                library.reveal(book)
                appVm.openInCurrentTab(Destination.Book(book.id, lineId, book.title), book.title)
            },
            openBookInNewTab = { book, lineId -> appVm.openInNewTab(Destination.Book(book.id, lineId, book.title), book.title) },
            search = { q -> appVm.openInNewTab(Destination.Search(q), q) },
            setTitle = { title -> appVm.setBookTitle(tab.id, title) },
        )
    }
    val phoneTopBar: @Composable (String, String?, (@Composable () -> Unit)?, (@Composable () -> Unit)?) -> Unit = { title, subtitle, leading, extra ->
        PhoneTopBar(title, subtitle, tabCount, themeMode, actions, leading, extra)
    }
    // History and Favorites pages open their entries in this tab, as desktop.
    val openHere: (Destination, String) -> Unit = { d, title -> appVm.openInCurrentTab(d, title) }

    when (val d = tab.destination) {
        Destination.Home -> if (!wide) {
            // Desktop keeps "רשימת הספרים" beside the home page; on a phone it is a drawer.
            LibraryDrawer(library, onOpenBook = { nav.openBook(it, null) }, onOpenBookInNewTab = { nav.openBookInNewTab(it, null) }) { openDrawer ->
                Column(Modifier.fillMaxSize()) {
                    phoneTopBar("פרדס", null, { ZIconButton(Icons.AutoMirrored.Outlined.LibraryBooks, ShellTexts.BOOK_LIST, openDrawer) }, null)
                    HomeScreen(onOpenBook = { nav.openBook(it, null) }, onSearch = { q -> appVm.openInCurrentTab(Destination.Search(q), q) }, wide = false, onOpenBookAt = { b, l -> nav.openBook(b, l) })
                }
            }
        } else {
            // The start bar and the book tree (hideable, as on desktop) beside the home card
            WideHome(tab.id, appVm, library, onOpenBook = { nav.openBook(it, null) }, onOpenBookInNewTab = { nav.openBookInNewTab(it, null) }) {
                HomeScreen(onOpenBook = { nav.openBook(it, null) }, onSearch = { q -> appVm.openInCurrentTab(Destination.Search(q), q) }, wide = true, onOpenBookAt = { b, l -> nav.openBook(b, l) })
            }
        }
        is Destination.Book -> ReaderScreen(tab.id, d, wide, library, nav, phoneTopBar)
        is Destination.Search -> Column(Modifier.fillMaxSize()) {
            if (!wide) {
                // Find within the results: the magnifier, or the overflow on very narrow phones (Jelly Star), as the reader
                val narrowPhone = LocalConfiguration.current.screenWidthDp < 300
                PhoneTopBar(
                    if (tab.title.isBlank()) "חיפוש" else "תוצאות חיפוש : ${tab.title}", null, tabCount, themeMode, actions, null,
                    if (narrowPhone) null else ({ ZIconButton(Icons.Outlined.FindInPage, "חפש בתוך העמוד", { appVm.requestFind() }) }),
                    findInMenu = narrowPhone,
                )
            }
            // Results open in a new tab with the query in its find; the tablet preview is the search's own (no reader)
            SearchScreen(tabId = tab.id, destination = d, wide = wide, modifier = Modifier.weight(1f))
        }
        Destination.History -> HistoryPage(wide, openHere, if (wide) null else { tabActions -> phoneTopBar("היסטוריה", null, null, tabActions) })
        Destination.Favorites -> FavoritesPage(wide, openHere, if (wide) null else { tabActions -> phoneTopBar("מועדפים", null, null, tabActions) })
        is Destination.Author -> Column(Modifier.fillMaxSize()) {
            if (!wide) phoneTopBar(d.name.ifBlank { tab.title }, null, null, null)
            AuthorScreen(authorId = d.authorId, wide = wide, onOpenBook = { book -> nav.openBookInNewTab(book, null) })
        }
    }
}

/** Phone: the library tree in a modal drawer from the reading side (right in RTL). */
@Composable
private fun LibraryDrawer(
    library: LibraryViewModel,
    onOpenBook: (Book) -> Unit,
    onOpenBookInNewTab: (Book) -> Unit,
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // A closed drawer is still composed, measured and drawn (off-screen), so its tree (its search field, a row per
    // category) cost the Home's first frame at every cold start for a drawer most launches never open. Built the first
    // time it opens (only [content]'s button opens it: its swipe is off while closed), or found open (a restored
    // state), and kept from then on: its scroll, query and open categories stay as before (saved ones wait for it).
    var treeBuilt by remember { mutableStateOf(false) }
    LaunchedEffect(drawerState.isOpen) { if (drawerState.isOpen) treeBuilt = true }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Zayit.colors.panel, modifier = Modifier.width(drawerWidthFor(LocalConfiguration.current.screenWidthDp))) {
                Text(ShellTexts.BOOK_LIST, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Zayit.colors.text, modifier = Modifier.padding(16.dp))
                if (treeBuilt || drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) {
                    LibraryTree(
                        library,
                        null,
                        onOpenBook = { scope.launch { drawerState.close() }; onOpenBook(it) },
                        onOpenBookInNewTab = { scope.launch { drawerState.close() }; onOpenBookInNewTab(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
    ) {
        content {
            // Built in the frame the drawer starts sliding in, before it can be seen
            treeBuilt = true
            scope.launch { drawerState.open() }
        }
    }
}

/**
 * Phone tab switcher: the desktop tab strip as a list (pinned tabs first, with a pin and no ×), the workspace above it,
 * "+" for a new tab and a menu to close them all or reopen the last closed one. A long press on a tab opens its menu
 * (desktop's right click).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabsOverview(state: AppUiState, appVm: AppViewModel) {
    val c = Zayit.colors
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var overflow by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PhoneHeader("לשוניות פתוחות", onBack = { appVm.pop() }) {
            ZIconButton(Icons.Outlined.Add, "הוסף לשונית", appVm::newHomeTab)
            Box {
                ZIconButton(Icons.Outlined.MoreVert, "אפשרויות נוספות", { overflow = true })
                // Reopening a tab leaves this page: the menu goes at once rather than fade over the tab
                ShellMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(
                        text = { Text("פתח מחדש לשונית שנסגרה") },
                        leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                        enabled = state.canReopenClosedTab,
                        onClick = {
                            overflow = false
                            appVm.reopenClosedTab()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("סגור את כל הלשוניות") },
                        leadingIcon = { Icon(Icons.Outlined.Close, null) },
                        onClick = {
                            overflow = false
                            appVm.closeAll()
                        },
                    )
                }
            }
        }
        PhoneWorkspaceBar(state, appVm)
        LazyColumn(Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            itemsIndexed(state.tabs, key = { _, t -> t.id }) { i, t ->
                val sel = i == state.selectedIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) c.accentTint else c.panel)
                        .combinedClickable(role = Role.Tab, onLongClickLabel = "אפשרויות לשונית", onLongClick = { menuFor = i }, onClick = { appVm.selectTab(i) })
                        .padding(start = 14.dp, end = if (t.pinned) 14.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(tabIcon(t), null, tint = c.text)
                    Text(
                        t.title.ifBlank { AppUiState.HOME_TITLE },
                        color = c.text,
                        fontSize = 16.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (t.pinned) {
                        Icon(Icons.Outlined.PushPin, "לשונית מוצמדת", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    } else {
                        ZIconButton(Icons.Outlined.Close, "סגור לשונית", { appVm.closeTab(i) }, tint = c.textSecondary)
                    }
                }
            }
        }
    }
    menuFor?.let { i ->
        val t = state.tabs.getOrNull(i)
        if (t == null) {
            menuFor = null
        } else {
            TabMenuSheet(t.title.ifBlank { AppUiState.HOME_TITLE }, tabMenuItems(context, appVm, i, vertical = true), onDismiss = { menuFor = null })
        }
    }
}

/** Header for full-screen pages (settings, tools): title with a back arrow on the reading side. */
@Composable
fun PhoneHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val c = Zayit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.titleBar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "חזרה", onBack)
        // Smaller before it is cut: beside two actions a Jelly Star leaves "לשוניות פתוחות" about 100dp
        Text(
            title,
            autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 18.sp, stepSize = 1.sp),
            fontWeight = FontWeight.Bold,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
        Box(Modifier.size(4.dp).background(Color.Transparent))
    }
}
