package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.guardedWrite
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.components.Island
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.library.LibraryTree
import io.github.kdroidfilter.seforimapp.android.ui.library.LibraryViewModel
import io.github.kdroidfilter.seforimapp.android.ui.notes.NotesPage
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalKeyShortcuts
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalTabSession
import io.github.kdroidfilter.seforimapp.android.ui.shell.bookShareLink
import io.github.kdroidfilter.seforimapp.android.ui.shell.copyLink
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.shell.shareLink
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Callbacks the reader needs from the app shell. */
data class ReaderNav(
    val openBook: (Book, Long?) -> Unit,
    val openBookInNewTab: (Book, Long?) -> Unit,
    val search: (String) -> Unit,
    val setTitle: (String) -> Unit,
)

private const val TAG = "Zayit"

/**
 * The reader of a book tab: desktop 0.6.1 features/bookcontent (BookContentScreen, ReaderPanes, VerticalBars,
 * BookContentView) on touch. A tablet (≥ 840dp) lays out desktop's panes together around the text: book tree, TOC
 * and notes on the start side, targum over book details on the end side, commentaries beside sources under the text,
 * the breadcrumb at the bottom, the rails on both edges, every divider draggable. A phone shows one pane at a time
 * under the text with a drag handle, a drawer for the TOC and the book tree, and a bottom toolbar. Outside a tab (the
 * tablet search preview, [LocalTabSession] null) it records nothing and reports nothing.
 */
@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(
    tabId: String,
    destination: Destination.Book,
    wide: Boolean,
    library: LibraryViewModel,
    nav: ReaderNav,
    topBar: @Composable (title: String, subtitle: String?, leading: (@Composable () -> Unit)?, extra: (@Composable () -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    val session = LocalTabSession.current
    val appVm = LocalAppViewModel.current
    val vm: ReaderViewModel = viewModel(key = "reader-${destination.bookId}-${destination.lineId}", factory = ReaderViewModel.factory(app, destination))
    val state by vm.state.collectAsState()
    val textSize by app.prefs.textSize.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val readerPrefs = remember { ReaderPrefs.get(app) }
    val bookDetailsPref by readerPrefs.bookDetails.collectAsState()
    var notesPage by remember { mutableStateOf(false) }
    var breadcrumbSheet by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(state)

    LaunchedEffect(state.book?.title) { state.book?.title?.let { nav.setTitle(it) } }

    // The place on screen, for the breadcrumb, the subtitle and the tab title: after any move of the text (a scroll, a
    // fast-scroller drop, a find match, a jump), not only a new selection. The lines' map is read too, so a drop on lines
    // not loaded yet answers once they are. The primary line coming on screen (a tap) is followed at once.
    LaunchedEffect(vm, listState) {
        snapshotFlow { placeOnScreen(listState, vm, current) }
            .distinctUntilChanged()
            .debounce { (_, primary) -> if (primary) 0L else PLACE_SETTLE_MS }
            .collect { (lineId, _) -> if (lineId != null) vm.followPlace(lineId) }
    }

    // Where the tab stands (desktop's anchor and TOC title: the chapter on screen), and the history visit of a book
    // opened in this tab (the selected line and its chapter)
    if (session != null) {
        LaunchedEffect(vm, session) {
            snapshotFlow { ReaderPosition(listState.firstVisibleItemIndex, vm.lines.size, current.placeHeading, current.heading, current.primaryLineId) }
                .distinctUntilChanged()
                .debounce(300)
                .collect { p ->
                    val book = current.book ?: return@collect
                    val lineId = vm.lines[p.first]?.id
                    session.reportReaderPosition(lineId, p.placeHeading)
                    if (session.isFront && !session.restored) app.history.record(book.id, p.primaryLineId ?: lineId, book.title, p.heading)
                }
        }
        LaunchedEffect(session) { session.findRequests.collect { vm.openFind() } }
    }

    // Citation links (seforim://line/<id>) open their line in a new tab; any other link goes to the system
    val platformUri = LocalUriHandler.current
    val uriHandler = remember(platformUri) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val lineId = uri.takeIf { it.startsWith("seforim://line/") }?.removePrefix("seforim://line/")?.toLongOrNull()
                if (lineId == null) {
                    runCatching { platformUri.openUri(uri) }
                    return
                }
                scope.launch {
                    val repo = app.libraryStore.repository()
                    val line = runCatching { repo.getLine(lineId) }.getOrNull() ?: return@launch
                    runCatching { repo.getBookCore(line.bookId) }.getOrNull()?.let { nav.openBookInNewTab(it, line.id) }
                }
            }
        }
    }

    fun openBookAt(bookId: Long, lineId: Long?) {
        scope.launch { runCatching { app.libraryStore.repository().getBookCore(bookId) }.getOrNull()?.let { nav.openBookInNewTab(it, lineId) } }
    }

    fun openAuthor(id: Long, name: String) {
        val vmApp = appVm ?: return
        vmApp.openInNewTab(Destination.Author(id, name), name)
    }

    fun visibleLines(): List<Line> = listState.layoutInfo.visibleItemsInfo.mapNotNull { vm.lines[it.index] }.distinctBy { it.id }.sortedBy { it.lineIndex }

    // The text selected in the book; an action finds its text on the lines it was read from, which may reach past the
    // screen, and on those on screen when no selection was read
    val textSelection = remember(vm) { ListSelection() }
    val selectionFocus = remember(vm) { SelectionFocus() }
    val focus = remember { FocusRequester() }
    // A new selection takes the keys from a find or note field, so Ctrl+C copies it (its SelectionContainer did)
    SideEffect { selectionFocus.takeFocus = { runCatching { focus.requestFocus() } } }
    fun selectionLines(): List<Line> {
        val units = textSelection.lastUnits ?: return visibleLines()
        val lines = ((units.first / LINE_UNITS).toInt()..(units.last / LINE_UNITS).toInt()).mapNotNull { vm.lines[it] }
        return lines.ifEmpty { visibleLines() }
    }

    // Desktop BookTextMenus on the book's text
    val mainSpec = remember(vm) {
        SelectionMenuSpec(
            main = true,
            canCopyWithoutNikud = { text ->
                val b = current.book
                b != null && (b.hasNekudot || b.hasTeamim) && current.diacritics != DiacriticsMode.None && hasDiacritics(text)
            },
            onAction = { action, text -> mainAction(action, text, vm, current, ::selectionLines, nav, context, scope, app) },
            onHighlight = { color, text ->
                val book = current.book
                if (book != null) {
                    val lines = selectionLines()
                    val diacritics = current.diacritics
                    scope.launch {
                        // Off the main thread: a selection the edge scroll drew out may span thousands of lines
                        val ranges = withContext(Dispatchers.Default) { resolveHighlightRangesForSelection(lines.map { it.id to vm.plainText(it) }, text, diacritics) }
                        guardedWrite("Highlight") {
                            ranges.forEach { (lineId, r) ->
                                if (color == Color.Transparent) app.userData.removeOverlappingHighlights(book.id, lineId, r.first, r.last + 1)
                                else app.userData.addHighlight(book.id, lineId, r.first, r.last + 1, color.toArgbLong())
                            }
                        }
                    }
                }
            },
        )
    }
    val paneActions = remember(vm) {
        PaneActions(
            openBookAt = ::openBookAt,
            paneSelection = { items -> paneSpec(items, current, nav, context, scope, app, ::openBookAt, vm) },
        )
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var drawerTab by rememberSaveable { mutableStateOf(0) }
    val wideNow by rememberUpdatedState(wide)
    val crumbNav = remember(vm) {
        CrumbNavigation(
            revealCategory = { cat ->
                library.revealCategory(cat)
                // Tablet: the tree pane opens; phone: the drawer, on the books
                if (wideNow) vm.setTree(true) else scope.launch { drawerTab = 1; drawerState.open() }
            },
            openBook = { b -> if (b.id != current.book?.id) nav.openBook(b, null) },
            openLine = { vm.loadAndSelectLine(it) },
        )
    }

    // Back: an open drawer (its handler is composed when it opens, so it is the newest), a text selection
    // (SelectableArea's own handler, registered later than these), the find bar, the multi-selection, then the panes.
    // A search result's seeded find bar the user never asked for nor edited leaves Back to the shell, which returns to
    // the results.
    val findBack = state.find.open && (state.find.focusRequest > 0 || state.find.query != state.searchQuery)
    BackHandler(enabled = findBack) { vm.closeFind() }
    BackHandler(enabled = !findBack && state.multiSelect) { vm.setMultiSelect(false) }
    BackHandler(enabled = !wide && !findBack && !state.multiSelect && state.phonePane != null) { vm.closePhonePane() }

    // The reader takes the keys unless the find bar asked for the cursor (a search result's seeded bar did not)
    LaunchedEffect(session?.isFront, state.find.open) {
        if (session?.isFront != false && (!state.find.open || state.find.focusRequest == 0)) runCatching { focus.requestFocus() }
    }
    val onTree: () -> Unit = { if (wide) vm.toggleTree() else scope.launch { drawerTab = 1; drawerState.open() } }
    val onToc: () -> Unit = { if (wide) vm.toggleToc() else scope.launch { drawerTab = 0; drawerState.open() } }
    val onPane: (ReaderPane) -> Unit = { p -> if (wide) vm.togglePane(p) else vm.togglePhonePane(p) }
    val keyHandler: (KeyEvent) -> Boolean = { e ->
        // Ctrl+C copies the text selected last, in the book or a pane (desktop's SelectionContainer)
        val selected = selectionFocus.selection
        if (e.type == KeyEventType.KeyDown && (e.isCtrlPressed || e.isMetaPressed) && e.key == Key.C && selected != null) {
            scope.launch { selected.read()?.let { trimSelectionEdges(it.text) }?.takeIf { it.isNotBlank() }?.let { copyText(context, it) } }
            true
        } else {
            bookShortcut(e, vm, onTree, onToc, onPane) || readerKey(e, vm, current, listState, scope)
        }
    }
    // The book shortcuts work when nothing has the focus too, in the front tab: the keys no view used reach them
    // (desktop 0.6.5 f96e98f3, the window's readerShortcuts)
    val shortcuts = LocalKeyShortcuts.current
    val bookKeys by rememberUpdatedState<(KeyEvent) -> Boolean> { e -> bookShortcut(e, vm, onTree, onToc, onPane) }
    DisposableEffect(shortcuts, session?.isFront) {
        val fallback: (android.view.KeyEvent) -> Boolean = { native -> bookKeys(KeyEvent(native)) }
        if (shortcuts != null && session?.isFront != false) shortcuts.fallback = fallback
        onDispose { if (shortcuts?.fallback === fallback) shortcuts.fallback = null }
    }

    val text: @Composable (Modifier) -> Unit = { mod ->
        BookText(
            vm = vm,
            state = state,
            listState = listState,
            textSize = textSize,
            selection = mainSpec,
            textSelection = textSelection,
            findBarBand = !wide,
            onZoom = { factor -> app.prefs.setTextSize(textSize * factor) },
            modifier = mod,
        ) {
            if (state.multiSelect) MultiSelectChip(state.selectedLineIds.size, Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) { vm.setMultiSelect(false) }
            if (state.find.open) {
                FindBar(
                    find = state.find,
                    onQuery = vm::setFindQuery,
                    onPrevious = { vm.navigateFind(false, vm.lines[listState.firstVisibleItemIndex]?.lineIndex ?: listState.firstVisibleItemIndex) },
                    onNext = { vm.navigateFind(true, vm.lines[listState.firstVisibleItemIndex]?.lineIndex ?: listState.firstVisibleItemIndex) },
                    onClose = vm::closeFind,
                    compact = !wide && LocalConfiguration.current.screenWidthDp < 300,
                    // Phone: the bar spans the text, on a band of the card's colour, so the text scrolls under a clean
                    // edge rather than showing slivers of a line around the bar (the text makes room for the band)
                    modifier = if (wide) Modifier.align(Alignment.TopEnd).padding(10.dp).widthIn(max = 420.dp) else Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Zayit.colors.panel).padding(6.dp),
                )
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides uriHandler, LocalSelectionFocus provides selectionFocus) {
        // onKeyEvent, not onPreviewKeyEvent: a focused field (find, a note, the TOC or book search) gets the keys first,
        // so its arrows, Home/End and the book search's ↑/↓ work; what it leaves reaches the reader's shortcuts.
        Box(Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent(keyHandler)) {
            if (wide) {
                WideReader(vm, state, textSize, library, nav, paneActions, crumbNav, bookDetailsPref, readerPrefs, text, ::openAuthor, ::openBookAt, onNotesPage = { notesPage = true })
            } else {
                PhoneReader(
                    vm, state, textSize, library, nav, paneActions, topBar, drawerState, drawerTab, { drawerTab = it }, text,
                    onNotesPage = { notesPage = true },
                    onBreadcrumb = { breadcrumbSheet = true },
                    // The title bar's "דף הבית" (desktop replaceCurrentTabWithNewTabId): Home replaces the book in this tab
                    onHome = { appVm?.goHome() },
                    onOpenAuthor = ::openAuthor,
                    onOpenBookAt = ::openBookAt,
                )
            }
        }
    }
    // Windows of their own: only over this tab while it is the one shown
    val front = session?.isFront != false
    if (notesPage && front) {
        NotesPage(
            onOpen = { bookId, lineId, title -> appVm?.openInNewTab(Destination.Book(bookId, lineId, title, openNotes = true), title) },
            onDismiss = { notesPage = false },
        )
    }
    if (breadcrumbSheet && front) BreadcrumbSheet(vm, state, crumbNav, onDismiss = { breadcrumbSheet = false })
}

/** How long the text rests before the breadcrumb and the subtitle name where it stopped (a fling passes many lines). */
private const val PLACE_SETTLE_MS = 150L

/** What the tab reports and the history records, compared whole: a change of any part is reported. */
private data class ReaderPosition(val first: Int, val loaded: Int, val placeHeading: String, val heading: String, val primaryLineId: Long?)

/**
 * The line the reader stands at, and whether it is the primary line: the primary line while some of it shows; else the
 * current find match while it shows (a find step selects nothing); else the first line mostly below the text's top
 * edge (below the find bar's room while it is open). Null while that line is not loaded. Read inside a snapshotFlow:
 * the list's layout, the lines' map and the state are all observed.
 */
private fun placeOnScreen(list: LazyListState, vm: ReaderViewModel, state: ReaderUiState): Pair<Long?, Boolean> {
    val info = list.layoutInfo
    val visible = info.visibleItemsInfo
    // Offsets count from the content's start (under the top padding, or the find bar's room)
    fun shows(index: Int?) = index != null && visible.any { it.index == index && it.offset + it.size > 0 && it.offset < info.viewportEndOffset }
    if (state.primaryLineId != null && shows(state.primaryLineIndex)) return state.primaryLineId to true
    val match = state.find.current?.takeIf { state.find.open }
    if (match != null && shows(match.lineIndex)) return match.lineId to false
    val first = list.firstVisibleItemIndex
    val top = visible.firstOrNull { it.index == first }
    val index = if (top != null && list.firstVisibleItemScrollOffset * 2 > top.size && visible.any { it.index == first + 1 }) first + 1 else first
    return vm.lines[index]?.id to false
}

/**
 * A selection as a search or find query: one line, and at most [MAX_SELECTION_QUERY_CHARS] (cut at a word). A selection
 * the edge scroll drew out can run to thousands of characters; whole, it made the find field lay out all of it, which
 * froze the app, and no line's text can hold a query that spans lines anyway.
 */
private fun selectionQuery(text: String): String? {
    val line = text.replace(Regex("\\s+"), " ").trim()
    if (line.isEmpty()) return null
    if (line.length <= MAX_SELECTION_QUERY_CHARS) return line
    val cut = line.take(MAX_SELECTION_QUERY_CHARS)
    return cut.substringBeforeLast(' ', cut).trim()
}

private const val MAX_SELECTION_QUERY_CHARS = 300

/** As desktop HighlightStore stores it: `color.toArgb().toLong()` (rounded channels, sign-extended). */
private fun Color.toArgbLong(): Long = toArgb().toLong()

/** The main text's menu actions (desktop BookTextMenus items). */
private fun mainAction(
    action: SelectionAction,
    text: String,
    vm: ReaderViewModel,
    state: ReaderUiState,
    selectionLines: () -> List<Line>,
    nav: ReaderNav,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    app: ZayitApplication,
) {
    val book = state.book ?: return
    val lines = selectionLines()
    when (action) {
        SelectionAction.Copy -> copyText(context, text)
        SelectionAction.CopyWithoutNikud -> copyText(context, HebrewTextUtils.removeAllDiacritics(text))
        SelectionAction.CopyWithSource -> copyText(context, buildCopyWithSourcePayload(text, book, state.rootCategory?.title, lines))
        SelectionAction.CopyLink -> copyLink(context, bookShareLink(book.id, resolveLineRangeFromSelection(text, lines)?.first?.id ?: state.primaryLineId))
        SelectionAction.SearchDatabase -> selectionQuery(text)?.let(nav.search)
        SelectionAction.FindInPage -> selectionQuery(text)?.let(vm::openFind)
        SelectionAction.AddNote -> {
            // The note's passage starts on the selection's first line (or its second, when it starts at the first's end)
            val range = resolveHighlightRangesForSelection(lines.take(2).map { it.id to vm.plainText(it) }, text, state.diacritics).firstOrNull()
            if (range != null) vm.startNoteDraft(NoteDraftAnchor(range.lineId, range.range.first, range.range.last + 1, text.trim().substringBefore('\n').trim()))
        }
        SelectionAction.ReportError -> scope.launch {
            val range = resolveLineRangeFromSelection(text, lines) ?: state.primaryLineId?.let { id -> vm.lineById(id)?.let { it to it } }
            val mailto = runCatching { bookErrorReportMailto(app.libraryStore.repository(), book, state.rootCategory?.title, range, text) }.getOrNull() ?: return@launch
            try {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // No mail app: the address and message go to the clipboard
                // mailto: is an opaque URI (no getQueryParameter): its parts are split by hand
                val address = mailto.removePrefix("mailto:").substringBefore('?')
                val parts = mailto.substringAfter('?', "").split('&').mapNotNull { kv ->
                    kv.substringAfter('=', "").takeIf { it.isNotEmpty() }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                }
                copyLink(context, (listOf(address) + parts).joinToString("\n\n"))
                Toast.makeText(context, "לא נמצאה אפליקציית דואר: פרטי הדיווח הועתקו", Toast.LENGTH_LONG).show()
            }
        }
        SelectionAction.MultiSelect -> {
            vm.setMultiSelect(true)
            resolveLineRangeFromSelection(text, lines)?.first?.takeIf { it.id !in state.selectedLineIds }?.let { vm.toggleLine(it) }
        }
        SelectionAction.OpenInPlace -> Unit
    }
}

/** A pane column's menu: copies, searches, highlights stored on the commentary's own book, its place in a new tab. */
private fun paneSpec(
    items: () -> List<CommentaryWithText>,
    state: () -> ReaderUiState,
    nav: ReaderNav,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    app: ZayitApplication,
    openBookAt: (Long, Long?) -> Unit,
    vm: ReaderViewModel,
): SelectionMenuSpec {
    fun itemOf(text: String): CommentaryWithText? {
        val needle = HebrewTextUtils.removeAllDiacritics(text.substringBefore('\n')).trim()
        return items().firstOrNull { HebrewTextUtils.removeAllDiacritics(buildAnnotatedFromHtml(it.targetText, 16f).text).contains(needle) }
    }
    return SelectionMenuSpec(
        main = false,
        canCopyWithoutNikud = { state().diacritics != DiacriticsMode.None && hasDiacritics(it) },
        canOpenInPlace = true,
        onAction = { action, text ->
            when (action) {
                SelectionAction.Copy -> copyText(context, text)
                SelectionAction.CopyWithoutNikud -> copyText(context, HebrewTextUtils.removeAllDiacritics(text))
                SelectionAction.SearchDatabase -> selectionQuery(text)?.let(nav.search)
                SelectionAction.FindInPage -> selectionQuery(text)?.let(vm::openFind)
                SelectionAction.OpenInPlace -> itemOf(text)?.let { openBookAt(it.link.targetBookId, it.link.targetLineId) }
                else -> Unit
            }
        },
        onHighlight = { color, text ->
            val list = items()
            val bookOf = list.associate { it.link.targetLineId to it.link.targetBookId }
            val ranges = resolveHighlightRangesForSelection(list.map { it.link.targetLineId to buildAnnotatedFromHtml(it.targetText, 16f).text }, text, state().diacritics)
            scope.launch {
                guardedWrite("Highlight") {
                    ranges.forEach { (lineId, r) ->
                        val bookId = bookOf[lineId] ?: return@forEach
                        if (color == Color.Transparent) app.userData.removeOverlappingHighlights(bookId, lineId, r.first, r.last + 1)
                        else app.userData.addHighlight(bookId, lineId, r.first, r.last + 1, color.toArgbLong())
                    }
                }
            }
        },
    )
}

private fun paneSpec(
    items: () -> List<CommentaryWithText>,
    state: ReaderUiState,
    nav: ReaderNav,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    app: ZayitApplication,
    openBookAt: (Long, Long?) -> Unit,
    vm: ReaderViewModel,
): SelectionMenuSpec = paneSpec(items, { vm.state.value }, nav, context, scope, app, openBookAt, vm)

/** Desktop's book shortcuts (BookContentScreen handleBookShortcut): the book tree, the TOC, the panes, diacritics. */
private fun bookShortcut(
    e: KeyEvent,
    vm: ReaderViewModel,
    onTree: () -> Unit,
    onToc: () -> Unit,
    onPane: (ReaderPane) -> Unit,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ctrl = e.isCtrlPressed || e.isMetaPressed
    when {
        ctrl && e.key == Key.B -> if (e.isShiftPressed) onToc() else onTree()
        ctrl && e.key == Key.K -> onPane(
            when {
                e.isAltPressed -> ReaderPane.Sources
                e.isShiftPressed -> ReaderPane.Targum
                else -> ReaderPane.Commentaries
            },
        )
        ctrl && e.key == Key.J -> vm.cycleDiacritics()
        else -> return false
    }
    return true
}

/** Desktop's other reader keys (BookContentView keys, the find steps), which need the reader to have the focus. */
private fun readerKey(
    e: KeyEvent,
    vm: ReaderViewModel,
    state: ReaderUiState,
    list: LazyListState,
    scope: kotlinx.coroutines.CoroutineScope,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ctrl = e.isCtrlPressed || e.isMetaPressed
    val top = vm.lines[list.firstVisibleItemIndex]?.lineIndex ?: list.firstVisibleItemIndex
    when {
        (ctrl && e.key == Key.G) || e.key == Key.F3 -> {
            if (!state.find.open) vm.openFind()
            vm.navigateFind(!e.isShiftPressed, top)
        }
        e.key == Key.Escape && state.find.open -> vm.closeFind()
        ctrl && e.key == Key.MoveHome -> vm.scrollToIndex(0)
        ctrl && e.key == Key.MoveEnd -> vm.scrollToIndex(state.totalLines - 1)
        e.key == Key.DirectionUp && !ctrl -> vm.selectAdjacent(false)
        e.key == Key.DirectionDown && !ctrl -> vm.selectAdjacent(true)
        e.key == Key.PageDown -> scope.launch { list.animateScrollToItem((list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: list.firstVisibleItemIndex)) }
        e.key == Key.PageUp -> scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex - list.layoutInfo.visibleItemsInfo.size + 1).coerceAtLeast(0)) }
        else -> return false
    }
    return true
}

@Composable
private fun MultiSelectChip(count: Int, modifier: Modifier, onDone: () -> Unit) {
    val c = Zayit.colors
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.accent).clickable(role = Role.Button, onClick = onDone).heightIn(min = 40.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("$count שורות נבחרו", color = c.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("✓", color = c.onAccent, fontSize = 16.sp)
    }
}

/** What the selected line links to, for the rails and the toolbar (desktop LineResourceAvailability). */
private class Availability(val targum: Boolean, val commentaries: Boolean, val sources: Boolean)

@Composable
private fun availability(vm: ReaderViewModel, state: ReaderUiState): Availability {
    val snap = state.primaryLineId?.let { vm.connections[it] }
    // Unknown (not loaded yet) counts as available, as desktop's null
    return Availability(
        targum = snap == null || snap.targumSources.isNotEmpty() || ReaderPane.Targum in state.panes,
        commentaries = snap == null || snap.hasCommentaries || ReaderPane.Commentaries in state.panes,
        sources = snap == null || snap.sources.isNotEmpty() || ReaderPane.Sources in state.panes,
    )
}

// ---- Tablet ----

@Composable
private fun WideReader(
    vm: ReaderViewModel,
    state: ReaderUiState,
    textSize: Float,
    library: LibraryViewModel,
    nav: ReaderNav,
    actions: PaneActions,
    crumbNav: CrumbNavigation,
    bookDetails: Boolean,
    prefs: ReaderPrefs,
    text: @Composable (Modifier) -> Unit,
    onOpenAuthor: (Long, String) -> Unit,
    onOpenBookAt: (Long, Long?) -> Unit,
    onNotesPage: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val context = LocalContext.current
    val book = state.book
    val avail = availability(vm, state)
    // Pane sizes, per tab (desktop SplitDefaults and the splitters)
    var treeW by rememberSaveable { mutableFloatStateOf(250f) }
    var tocW by rememberSaveable { mutableFloatStateOf(220f) }
    var notesW by rememberSaveable { mutableFloatStateOf(300f) }
    var endW by rememberSaveable { mutableFloatStateOf(300f) }
    var bottomH by rememberSaveable { mutableFloatStateOf(0.42f) }
    var sourcesShare by rememberSaveable { mutableFloatStateOf(0.34f) }
    val showTargum = ReaderPane.Targum in state.panes
    val showComments = ReaderPane.Commentaries in state.panes
    val showSources = ReaderPane.Sources in state.panes
    val showNotes = ReaderPane.Notes in state.panes
    val showDetails = bookDetails && book != null
    // The side panes give way so the text keeps at least 42% of the room between the rails (an 840dp tablet with the
    // TOC and book details open left it ~150dp); the stored widths stay as dragged
    val compactRails by app.prefs.compactRails.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val room = maxWidth.value - 12f - 2 * ((if (compactRails) 48f else 64f) + 6f)
        val side = (if (state.showTree) treeW + 8f else 0f) + (if (state.showToc && book != null) tocW + 8f else 0f) +
            (if (showNotes && book != null) notesW + 8f else 0f) + (if (showTargum || showDetails) endW + 8f else 0f)
        val fit = if (side > 0f && room - side < room * 0.42f) ((room * 0.58f) / side).coerceIn(0.3f, 1f) else 1f
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            // Start rail (right): books, contents; notes at the bottom (desktop StartVerticalBar)
            Column(Modifier.fillMaxHeight().padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReaderRailButton(Icons.AutoMirrored.Outlined.LibraryBooks, "ספרים", selected = state.showTree) { vm.toggleTree() }
                if (book != null) ReaderRailButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "תוכן עניינים", selected = state.showToc) { vm.toggleToc() }
                Spacer(Modifier.weight(1f))
                if (book != null) ReaderRailButton(Icons.Outlined.EditNote, "הערות", selected = showNotes) { vm.togglePane(ReaderPane.Notes) }
            }
            if (state.showTree) {
                Island(Modifier.width((treeW * fit).dp).fillMaxHeight()) {
                    LibraryTree(
                        library,
                        book?.id,
                        onOpenBook = {
                            // Settings › "סגור את עץ הספרים בעת פתיחת ספר חדש"
                            if (app.prefs.closeTreeOnOpen.value) vm.setTree(false)
                            nav.openBook(it, null)
                        },
                        onOpenBookInNewTab = { nav.openBookInNewTab(it, null) },
                        modifier = Modifier.fillMaxSize(),
                        header = { search -> PaneHeader("רשימת הספרים", { vm.toggleTree() }) { search() } },
                    )
                }
                SplitHandle(vertical = false) { d -> treeW = (treeW - d / fit).coerceIn(160f, 420f) }
            }
            if (state.showToc && book != null) {
                Island(Modifier.width((tocW * fit).dp).fillMaxHeight()) {
                    PaneHeader("תוכן עניינים", { vm.toggleToc() }) { TocSearchButton(state.tocSearch != null) { vm.toggleTocSearch() } }
                    TocPane(vm, state, onPicked = {}, modifier = Modifier.fillMaxSize())
                }
                SplitHandle(vertical = false) { d -> tocW = (tocW - d / fit).coerceIn(140f, 400f) }
            }
            if (showNotes && book != null) {
                Island(Modifier.width((notesW * fit).dp).fillMaxHeight()) {
                    NotesPane(vm, state, Modifier.fillMaxSize()) {
                        PaneHeader("הערות", { vm.closePane(ReaderPane.Notes) }) {
                            ZIconButton(Icons.Outlined.EditNote, "הצג את כל ההערות", onNotesPage, tint = Zayit.colors.textSecondary)
                        }
                    }
                }
                SplitHandle(vertical = false) { d -> notesW = (notesW - d / fit).coerceIn(220f, 480f) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val total = maxHeight
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Island(Modifier.weight(1f).fillMaxHeight()) { text(Modifier.fillMaxSize()) }
                        if (showTargum || showDetails) {
                            SplitHandle(vertical = false) { d -> endW = (endW + d / fit).coerceIn(200f, 520f) }
                            Column(Modifier.width((endW * fit).dp).fillMaxHeight()) {
                                if (showTargum) {
                                    Island(Modifier.weight(1f).fillMaxWidth()) {
                                        LinksPane(vm, state, ConnectionType.TARGUM, textSize, actions, Modifier.fillMaxSize()) { PaneHeader("תרגומים", { vm.closePane(ReaderPane.Targum) }) }
                                    }
                                }
                                if (showTargum && showDetails) Spacer(Modifier.height(6.dp))
                                if (showDetails) {
                                    Island(Modifier.weight(1f).fillMaxWidth()) {
                                        PaneHeader("פרטי הספר", { prefs.setBookDetails(false) })
                                        BookDetailsPane(vm, onOpenPart = { onOpenBookAt(vm.bookId, it) }, onOpenAuthor = onOpenAuthor)
                                    }
                                }
                            }
                        }
                    }
                    if (showComments || showSources) {
                        SplitHandle(vertical = true) { d -> bottomH = (bottomH - d / total.value).coerceIn(0.18f, 0.8f) }
                        BoxWithConstraints(Modifier.fillMaxWidth().height(total * bottomH)) {
                            val width = maxWidth
                            Row(Modifier.fillMaxSize()) {
                                if (showComments) {
                                    Island(Modifier.weight(1f).fillMaxHeight()) {
                                        CommentariesPane(vm, state, textSize, wide = true, actions, Modifier.fillMaxSize()) { extra ->
                                            PaneHeader("מפרשים", { vm.closePane(ReaderPane.Commentaries) }) { extra() }
                                        }
                                    }
                                }
                                if (showComments && showSources) SplitHandle(vertical = false) { d -> sourcesShare = (sourcesShare + d / width.value).coerceIn(0.2f, 0.7f) }
                                if (showSources) {
                                    Island(if (showComments) Modifier.width(width * sourcesShare).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()) {
                                        LinksPane(vm, state, ConnectionType.SOURCE, textSize, actions, Modifier.fillMaxSize()) { PaneHeader("מקורות", { vm.closePane(ReaderPane.Sources) }) }
                                    }
                                }
                            }
                        }
                    }
                    if (book != null) {
                        Spacer(Modifier.height(6.dp))
                        Island(Modifier.fillMaxWidth().heightIn(min = 44.dp)) { BreadcrumbBar(vm, state, crumbNav) }
                    }
                }
            }
            // End rail (left): zoom, diacritics, shnayim mikra; book details and the line panes at the bottom (desktop EndVerticalBar)
            Column(Modifier.fillMaxHeight().padding(start = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val textSizeNow = app.prefs.textSize.collectAsState().value
                ReaderRailButton(Icons.Outlined.ZoomIn, "הגדל", enabled = textSizeNow < 50f) { app.prefs.setTextSize(textSizeNow + 2f) }
                ReaderRailButton(Icons.Outlined.ZoomOut, "הקטן", enabled = textSizeNow > 14f) { app.prefs.setTextSize(textSizeNow - 2f) }
                if (book != null && (book.hasNekudot || book.hasTeamim)) {
                    ReaderRailButton(
                        Icons.Outlined.TextFormat,
                        "ניקוד",
                        selected = state.diacritics != DiacriticsMode.None,
                        levels = if (book.hasTeamim) state.diacritics.shownLevel else null,
                        description = state.diacritics.nextActionLabel(book.hasTeamim),
                    ) { vm.cycleDiacritics() }
                }
                if (book?.isChumash == true) ReaderRailButton(Icons.Outlined.AutoStories, "שנים מקרא", selected = state.shnayimMikra, description = "שנים מקרא ואחד תרגום: כל פסוק פעמיים ואחריו התרגום") { vm.toggleShnayimMikra() }
                Spacer(Modifier.weight(1f))
                ReaderRailButton(Icons.Outlined.Info, "פרטי הספר", selected = bookDetails) { prefs.setBookDetails(!bookDetails) }
                if (book != null) {
                    if (book.hasTargumConnection) {
                        ReaderRailButton(Icons.Outlined.Translate, "תרגומים", selected = showTargum, dimmed = !avail.targum) {
                            if (avail.targum) vm.togglePane(ReaderPane.Targum) else Toast.makeText(context, "אין תרגומים זמינים לשורה זו", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (book.hasSourceConnection) {
                        ReaderRailButton(Icons.Outlined.Source, "מקור", selected = showSources, dimmed = !avail.sources) {
                            if (avail.sources) vm.togglePane(ReaderPane.Sources) else Toast.makeText(context, "אין מקורות זמינים לשורה זו", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (book.hasCommentaryConnection) {
                        ReaderRailButton(Icons.AutoMirrored.Outlined.Comment, "מפרשים", selected = showComments, dimmed = !avail.commentaries) {
                            if (avail.commentaries) vm.togglePane(ReaderPane.Commentaries) else Toast.makeText(context, "אין מפרשים זמינים לפסקה זו", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

/** A draggable divider between panes: a 6dp gap with a 24dp touch strip (desktop ReaderSplitter's grip). */
@Composable
internal fun SplitHandle(vertical: Boolean, onDrag: (Float) -> Unit) {
    val density = LocalDensity.current
    val drag by rememberUpdatedState(onDrag)
    val modifier = if (vertical) {
        Modifier.fillMaxWidth().height(10.dp).pointerInput(Unit) { detectVerticalDragGestures { change, dy -> change.consume(); drag(with(density) { dy.toDp().value }) } }
    } else {
        Modifier.fillMaxHeight().width(8.dp).pointerInput(Unit) { detectHorizontalDragGestures { change, dx -> change.consume(); drag(with(density) { dx.toDp().value }) } }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box((if (vertical) Modifier.width(36.dp).height(3.dp) else Modifier.width(3.dp).height(36.dp)).clip(CircleShape).background(Zayit.colors.textDisabled.copy(alpha = 0.5f)))
    }
}

/**
 * A rail button (desktop SelectableIconButtonWithToolip): [dimmed] when the selected line has none of it (still
 * pressable, to say so), [levels] the diacritics button's two dots (how many layers are shown).
 */
@Composable
internal fun ReaderRailButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    levels: Int? = null,
    description: String = label,
    width: Dp = 64.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Zayit.colors
    val app = LocalContext.current.applicationContext as ZayitApplication
    val compact = width != Dp.Unspecified && app.prefs.compactRails.collectAsState().value
    val faded = !enabled || dimmed
    Column(
        modifier
            .then(if (width != Dp.Unspecified) Modifier.width(if (compact) 48.dp else width) else Modifier)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.selectedRow else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        val iconTint = if (faded) c.textDisabled else c.text
        if (levels != null && width == Dp.Unspecified) {
            // The phone toolbar: the dots in the icon's empty band under its glyph, so this button keeps its neighbours'
            // height and their labels stay in line
            Box(Modifier.size(24.dp)) {
                Icon(icon, description, tint = iconTint, modifier = Modifier.size(24.dp))
                LevelDots(levels, 3.dp, Modifier.align(Alignment.BottomCenter))
            }
        } else {
            Icon(icon, description, tint = iconTint, modifier = Modifier.size(if (compact) 22.dp else 24.dp))
            if (levels != null) LevelDots(levels, 4.dp)
        }
        if (!compact) Text(label, fontSize = 11.sp, lineHeight = 12.sp, color = if (faded) c.textDisabled else c.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** The diacritics button's two dots (desktop LevelIndicator): lit for each layer shown, none when nothing is. */
@Composable
private fun LevelDots(levels: Int, dot: Dp, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) { i -> Box(Modifier.size(dot).clip(CircleShape).background(if (levels == 0) Color.Transparent else if (i < levels) c.text else c.textDisabled)) }
    }
}

// ---- Phone ----

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhoneReader(
    vm: ReaderViewModel,
    state: ReaderUiState,
    textSize: Float,
    library: LibraryViewModel,
    nav: ReaderNav,
    actions: PaneActions,
    topBar: @Composable (String, String?, (@Composable () -> Unit)?, (@Composable () -> Unit)?) -> Unit,
    drawerState: androidx.compose.material3.DrawerState,
    drawerTab: Int,
    setDrawerTab: (Int) -> Unit,
    text: @Composable (Modifier) -> Unit,
    onNotesPage: () -> Unit,
    onBreadcrumb: () -> Unit,
    onHome: () -> Unit,
    onOpenAuthor: (Long, String) -> Unit,
    onOpenBookAt: (Long, Long?) -> Unit,
) {
    val c = Zayit.colors
    val scope = rememberCoroutineScope()
    val narrowPhone = LocalConfiguration.current.screenWidthDp < 300
    // Composed each time the drawer opens, so it is the newest handler and wins: Back closes the drawer before anything
    // else, a text selection's handler (SelectableArea, registered when the book was shown) included
    if (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) BackHandler { scope.launch { drawerState.close() } }
    // The chapter on screen, not the selected line's: it follows scrolls and fast-scroller drops
    val subtitle = listOfNotNull(state.categories.lastOrNull()?.title, state.placeHeading.ifBlank { null }).joinToString(" › ")
    // Material's drawer leaves 56dp of scrim to tap; under 300dp (Jelly Star) the two tabs share the row, on two lines
    val drawerWidth = drawerWidthFor(LocalConfiguration.current.screenWidthDp)
    val compactDrawer = drawerWidth < 300.dp
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = c.panel, modifier = Modifier.width(drawerWidth)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val tabModifier = if (compactDrawer) Modifier.weight(1f) else Modifier
                    DrawerTab("תוכן עניינים", drawerTab == 0, compactDrawer, tabModifier) { setDrawerTab(0) }
                    DrawerTab("רשימת הספרים", drawerTab == 1, compactDrawer, tabModifier) { setDrawerTab(1) }
                    if (!compactDrawer) Spacer(Modifier.weight(1f))
                    if (drawerTab == 0) TocSearchButton(state.tocSearch != null) { vm.toggleTocSearch() }
                }
                if (drawerTab == 0) {
                    TocPane(vm, state, onPicked = { scope.launch { drawerState.close() } }, modifier = Modifier.fillMaxSize())
                } else {
                    LibraryTree(
                        library,
                        state.book?.id,
                        onOpenBook = { scope.launch { drawerState.close() }; nav.openBook(it, null) },
                        onOpenBookInNewTab = { scope.launch { drawerState.close() }; nav.openBookInNewTab(it, null) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
    ) {
        // The keyboard (a note, the find bar) pushes the reader up rather than covering the pane being typed in; the
        // toolbar steps aside meanwhile (edge-to-edge: nothing else makes room for it)
        val typing = WindowInsets.isImeVisible
        Column(Modifier.fillMaxSize().background(c.ground).imePadding()) {
            topBar(
                // The title the book was opened with, until the book is read
                state.book?.title ?: vm.openedTitle,
                subtitle.ifBlank { null },
                { ZIconButton(Icons.Outlined.Menu, "תוכן עניינים", { setDrawerTab(0); scope.launch { drawerState.open() } }) },
                // Very narrow phones (Jelly Star): find moves into the toolbar's "עוד" so the title stays readable
                if (narrowPhone) null else ({ ZIconButton(Icons.Outlined.FindInPage, "חפש בתוך העמוד", { vm.openFind() }) }),
            )
            PhoneBody(vm, state, textSize, actions, text, onOpenAuthor, onOpenBookAt, typing, Modifier.weight(1f))
            if (!typing) PhoneToolbar(
                vm, state, textSize,
                onHome = onHome,
                onBooks = { setDrawerTab(1); scope.launch { drawerState.open() } },
                onFind = if (narrowPhone) ({ vm.openFind() }) else null,
                onNotesPage = onNotesPage,
                onBreadcrumb = onBreadcrumb,
            )
        }
    }
}

@Composable
private fun DrawerTab(text: String, selected: Boolean, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Zayit.colors
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) c.selectedRow else Color.Transparent).clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = c.text,
            fontSize = if (compact) 13.sp else 15.sp,
            lineHeight = if (compact) 16.sp else TextUnit.Unspecified,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A phone drawer's width: 300dp, leaving at least Material's 56dp of scrim to tap on narrow phones (Jelly Star). */
internal fun drawerWidthFor(screenWidthDp: Int): Dp = minOf(300, screenWidthDp - 56).coerceAtLeast(160).dp

/**
 * Phone: the text, and under it the pane (the desktop's docks, one at a time), which moves as a bottom sheet: it slides
 * in and away, settles half open or almost to the top (a line of the text left, the one the pane is about), flung or
 * dragged by its handle or tabs, a drag down closes it, and its lists scroll it open before they scroll. It stays a
 * split, not a sheet over the text: the text above stays tappable, and the commentaries follow the line tapped.
 */
@Composable
private fun PhoneBody(
    vm: ReaderViewModel,
    state: ReaderUiState,
    textSize: Float,
    actions: PaneActions,
    text: @Composable (Modifier) -> Unit,
    onOpenAuthor: (Long, String) -> Unit,
    onOpenBookAt: (Long, Long?) -> Unit,
    typing: Boolean,
    modifier: Modifier,
) {
    val book = state.book
    val pane = state.phonePane
    // The pane shown: the one open, or the one sliding away
    var shownPane by remember { mutableStateOf(pane) }
    if (pane != null) shownPane = pane
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val total = maxHeight.coerceAtLeast(1.dp)
        val totalPx = constraints.maxHeight.coerceAtLeast(1).toFloat()
        // Its two places: almost to the top, the text keeping a line (below the find bar's room while it is open:
        // finding is about the text), and half the reader, at least its tabs, chips, title and two lines
        val textMin = if (state.find.open) PhoneTextMin + FindBarRoom else PhoneTextMin
        val full = ((total - PaneHandle - textMin) / total).coerceIn(0.3f, 0.92f)
        val half = maxOf(0.5f, PhonePaneMin / total).coerceAtMost(full)
        // Where it settled last, kept with the tab: it opens there again
        var expanded by rememberSaveable { mutableStateOf(false) }
        fun place() = if (expanded) full else half
        var share by remember { mutableFloatStateOf(if (pane != null) place() else 0f) }
        val scope = rememberCoroutineScope()
        var motion by remember { mutableStateOf<Job?>(null) }
        fun slideTo(
            to: Float,
            velocity: Float = 0f,
            then: () -> Unit = {},
        ) {
            motion?.cancel()
            motion = scope.launch {
                animate(share, to, velocity, PaneSpring) { value, _ -> share = value }
                then()
            }
        }
        // Opened or closed by the toolbar, ✕ or Back: it slides in, or away
        LaunchedEffect(pane != null) { slideTo(if (pane != null) place() else 0f) }
        // Its places moved (the find bar, the window): it moves with them
        LaunchedEffect(full, half) { if (pane != null && motion?.isActive != true) share = place() }
        val flingPx = with(LocalDensity.current) { PaneFlingVelocity.toPx() }

        // Where a drag or fling left it, [velocity] in px/s, positive downwards: the place it heads for, or closed
        fun settle(velocity: Float) {
            val to = when {
                velocity < -flingPx -> full
                velocity > flingPx -> if (share > half + 0.01f) half else 0f
                share < half * PANE_CLOSE_AT -> 0f
                share < (half + full) / 2 -> half
                else -> full
            }
            if (to == 0f) {
                slideTo(0f, -velocity / totalPx) { vm.closePhonePane() }
            } else {
                expanded = to == full && full > half
                slideTo(to, -velocity / totalPx)
            }
        }

        fun between() = share > 0f && abs(share - half) > 0.005f && abs(share - full) > 0.005f
        val drag = rememberDraggableState { delta ->
            motion?.cancel()
            share = (share - delta / totalPx).coerceIn(PANE_GESTURE_MIN, full)
        }
        val dragModifier = Modifier.draggable(drag, Orientation.Vertical, onDragStopped = { velocity -> settle(velocity) })
        // A list in the pane first opens the pane to the top, then scrolls; pulled down at its start, it brings it down
        // Keyed on what its settle() reads, so a moved place (the find bar, the window) is not settled to by an old one
        val nested = remember(full, half, totalPx, flingPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y >= 0f || source != NestedScrollSource.UserInput || share >= full) return Offset.Zero
                    motion?.cancel()
                    val before = share
                    share = (share - available.y / totalPx).coerceAtMost(full)
                    // Opened to the top by its list: remembered there, as a release there by the handle is
                    if (share >= full) expanded = full > half
                    return Offset(0f, -(share - before) * totalPx)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                    motion?.cancel()
                    val before = share
                    share = (share - available.y / totalPx).coerceAtLeast(PANE_GESTURE_MIN)
                    return Offset(0f, (before - share) * totalPx)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!between()) return Velocity.Zero
                    settle(available.y)
                    return available
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (between()) settle(available.y)
                    return Velocity.Zero
                }
            }
        }
        var noteFocused by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Island(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) { text(Modifier.fillMaxSize()) }
            val shown = shownPane
            if (shown != null && book != null && share > 0.001f) {
                Box(Modifier.fillMaxWidth().height(PaneHandle).then(dragModifier), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(Zayit.colors.textDisabled))
                }
                // Writing a note (its field focused, the keyboard up): the pane grows to hold the editor, the text keeping
                // a line for the passage noted. The find bar's keyboard leaves the split as it is: desktop never resizes
                // its panes for a find, and the matches are in the text.
                val height = if (typing && shown == ReaderPane.Notes && noteFocused) {
                    maxOf(share, NotePaneMin / total).coerceAtMost(maxOf(share, (total - PaneHandle - NoteTextMin) / total))
                } else {
                    share
                }
                Island(
                    Modifier.fillMaxWidth().height(total * height).padding(horizontal = 4.dp).padding(bottom = 4.dp)
                        .onFocusChanged { noteFocused = it.hasFocus }.nestedScroll(nested),
                ) {
                    val kinds = buildList {
                        if (book.hasCommentaryConnection) add(ReaderPane.Commentaries)
                        if (book.hasTargumConnection) add(ReaderPane.Targum)
                        if (book.hasSourceConnection) add(ReaderPane.Sources)
                        add(ReaderPane.Notes)
                        add(ReaderPane.BookInfo)
                    }
                    val tabs: @Composable () -> Unit = { PhonePaneTabs(shown, kinds, vm::showPane, vm::closePhonePane, dragModifier) }
                    when (shown) {
                        ReaderPane.Commentaries -> CommentariesPane(vm, state, textSize, wide = false, actions, Modifier.fillMaxSize()) { tabs() }
                        ReaderPane.Targum -> LinksPane(vm, state, ConnectionType.TARGUM, textSize, actions, Modifier.fillMaxSize(), tabs)
                        ReaderPane.Sources -> LinksPane(vm, state, ConnectionType.SOURCE, textSize, actions, Modifier.fillMaxSize(), tabs)
                        ReaderPane.Notes -> NotesPane(vm, state, Modifier.fillMaxSize(), tabs)
                        ReaderPane.BookInfo -> Column(Modifier.fillMaxSize()) {
                            tabs()
                            BookDetailsPane(vm, onOpenPart = { onOpenBookAt(vm.bookId, it) }, onOpenAuthor = onOpenAuthor)
                        }
                    }
                }
            }
        }
    }
}

/** The strip above the phone's pane that drags it (the tabs row drags it too). */
private val PaneHandle = 24.dp

/** Under the phone's pane opened to the top, the text keeps a line: the one the pane is about. */
private val PhoneTextMin = 44.dp

/** The phone's pane half open holds at least its tabs, the commentators' chips and title, and two lines of commentary. */
private val PhonePaneMin = 170.dp

/** A fling of the pane faster than this goes on to the next place (Material's bottom sheet: 125 dp/s). */
private val PaneFlingVelocity = 125.dp

/** Dragged and let go under this part of its half height, the pane closes. */
private const val PANE_CLOSE_AT = 0.6f

/**
 * A drag or a pull keeps the pane at least this open, above the 0.001 it is drawn from: at 0 its handle and lists left
 * the composition under the finger, the release that settles (and closes) it never came, and the pane stayed open unseen.
 */
private const val PANE_GESTURE_MIN = 0.002f

/** The pane's slide: Material's spatial motion, no bounce. */
private val PaneSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** A note being written: the notes pane holds its tabs and the note's card (passage, editor, date and delete). */
private val NotePaneMin = 210.dp

/** A note being written: the text keeps one line, the passage noted (kept in view by the text). */
private val NoteTextMin = 44.dp

/** Phone bottom toolbar: the desktop rails as one row (only what the book has), overflowing into "עוד". */
@Composable
private fun PhoneToolbar(
    vm: ReaderViewModel,
    state: ReaderUiState,
    textSize: Float,
    onHome: () -> Unit,
    onBooks: () -> Unit,
    onFind: (() -> Unit)?,
    onNotesPage: () -> Unit,
    onBreadcrumb: () -> Unit,
) {
    val c = Zayit.colors
    val app = LocalContext.current.applicationContext as ZayitApplication
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = state.book
    val avail = availability(vm, state)
    var more by remember { mutableStateOf(false) }
    var zoomMenu by remember { mutableStateOf(false) }
    val favorites = app.userData.favorites
    val favRevision by favorites.revision.collectAsState()
    val toc = state.placeBreadcrumb.lastOrNull()
    val favKey = book?.let { favorites.keyFor(it.id, toc?.id) }
    val isFavorite by produceState(false, favKey, favRevision) { value = favKey?.let { runCatching { favorites.isFavorite(it) }.getOrDefault(false) } == true }
    var favoriteSheet by remember { mutableStateOf(false) }
    // The place the star and the links name (desktop BreadcrumbActionsView): "<book> - <heading>", at the place on
    // screen, as the menu's breadcrumb row above them (the primary line while it shows)
    val placeLineId = state.placeLineId ?: state.primaryLineId ?: toc?.lineId
    val placeTitle = book?.let { b -> toc?.text?.takeIf { it.isNotBlank() && it != b.title }?.let { "${b.title} - $it" } ?: b.title }

    fun pane(p: ReaderPane, available: Boolean, none: String) {
        if (available || state.phonePane == p) vm.togglePhonePane(p) else Toast.makeText(context, none, Toast.LENGTH_SHORT).show()
    }
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.panel).windowInsetsPadding(WindowInsets.navigationBars)) {
        val actions = buildList {
            // The TOC is the title bar's ☰, so the first slot (on the right) goes Home rather than repeat it
            add(ToolbarAction(Icons.Outlined.Home, "בית", "דף הבית", onClick = onHome))
            if (book?.hasCommentaryConnection == true) add(ToolbarAction(Icons.AutoMirrored.Outlined.Comment, "מפרשים", "מפרשים", state.phonePane == ReaderPane.Commentaries, !avail.commentaries) { pane(ReaderPane.Commentaries, avail.commentaries, "אין מפרשים זמינים לפסקה זו") })
            if (book?.hasTargumConnection == true) add(ToolbarAction(Icons.Outlined.Translate, "תרגומים", "תרגומים", state.phonePane == ReaderPane.Targum, !avail.targum) { pane(ReaderPane.Targum, avail.targum, "אין תרגומים זמינים לשורה זו") })
            if (book?.hasSourceConnection == true) add(ToolbarAction(Icons.Outlined.Source, "מקור", "מקור", state.phonePane == ReaderPane.Sources, !avail.sources) { pane(ReaderPane.Sources, avail.sources, "אין מקורות זמינים לשורה זו") })
            if (book != null && (book.hasNekudot || book.hasTeamim)) {
                // With teamim there are three states: the dots tell how much is shown (desktop VerticalBars LevelIndicator)
                add(ToolbarAction(Icons.Outlined.TextFormat, "ניקוד", state.diacritics.nextActionLabel(book.hasTeamim), state.diacritics != DiacriticsMode.None, levels = if (book.hasTeamim) state.diacritics.shownLevel else null, onClick = vm::cycleDiacritics))
            }
            if (book?.isChumash == true) add(ToolbarAction(Icons.Outlined.AutoStories, "שנים מקרא", "שנים מקרא", state.shnayimMikra, onClick = vm::toggleShnayimMikra))
        }
        val slots = (maxWidth / 58.dp).toInt().coerceIn(4, 7)
        // One slot for "גודל" and one for "עוד"
        val shown = actions.take(slots - 2)
        val overflow = actions.drop(slots - 2)
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.panelBorder))
            // Shown once the book is read and its buttons known: before, the three it always has took the whole row
            // and moved aside a moment later. A book that failed to load shows them at once: בית is the way out.
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp).alpha(if (book == null && state.loading) 0f else 1f)) {
                val item = Modifier.weight(1f)
                shown.forEach { a -> ReaderRailButton(a.icon, a.label, selected = a.selected, dimmed = a.dimmed, levels = a.levels, description = a.menuLabel, width = Dp.Unspecified, modifier = item, onClick = a.onClick) }
                Box(item) {
                    ReaderRailButton(Icons.Outlined.ZoomIn, "גודל", width = Dp.Unspecified, modifier = Modifier.fillMaxWidth()) { zoomMenu = true }
                    DropdownMenu(zoomMenu, { zoomMenu = false }, containerColor = c.panel) {
                        DropdownMenuItem(text = { Text("הגדל") }, leadingIcon = { Icon(Icons.Outlined.ZoomIn, null) }, enabled = textSize < 50f, onClick = { app.prefs.setTextSize(textSize + 2f) })
                        DropdownMenuItem(text = { Text("הקטן") }, leadingIcon = { Icon(Icons.Outlined.ZoomOut, null) }, enabled = textSize > 14f, onClick = { app.prefs.setTextSize(textSize - 2f) })
                    }
                }
                Box(item) {
                    ReaderRailButton(Icons.Outlined.MoreHoriz, "עוד", width = Dp.Unspecified, modifier = Modifier.fillMaxWidth()) { more = true }
                    DropdownMenu(more, { more = false }, containerColor = c.panel, modifier = Modifier.heightIn(max = 480.dp)) {
                        fun item(text: String, icon: ImageVector, tint: Color = Color.Unspecified, onClick: () -> Unit): @Composable () -> Unit = {
                            DropdownMenuItem(
                                text = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { if (tint == Color.Unspecified) Icon(icon, null) else Icon(icon, null, tint = tint) },
                                onClick = { more = false; onClick() },
                            )
                        }
                        if (onFind != null) item("חפש בתוך העמוד", Icons.Outlined.FindInPage, onClick = onFind)()
                        overflow.forEach { a -> item(a.menuLabel, a.icon, onClick = a.onClick)() }
                        item("הערות", Icons.Outlined.EditNote) { vm.togglePhonePane(ReaderPane.Notes) }()
                        item("פרטי הספר", Icons.Outlined.Info) { vm.togglePhonePane(ReaderPane.BookInfo) }()
                        item("רשימת הספרים", Icons.AutoMirrored.Outlined.LibraryBooks, onClick = onBooks)()
                        item("בחירה מרובה", Icons.Outlined.SelectAll) { vm.setMultiSelect(!state.multiSelect) }()
                        if (book != null && placeTitle != null) {
                            val path = crumbPath(state).joinToString(" › ") { it.title }
                            item(path, Icons.AutoMirrored.Outlined.MenuBook, onClick = onBreadcrumb)()
                            item("העתק קישור", Icons.Outlined.Link) { copyLink(context, bookShareLink(book.id, placeLineId)) }()
                            item("שתף", Icons.Outlined.Share) { shareLink(context, placeTitle, bookShareLink(book.id, placeLineId)) }()
                            // Desktop's star: filled in the accent once starred. The phone has no star on screen, so
                            // adding says so, and "עריכת מועדף" opens the folders and the removal rather than removing.
                            if (isFavorite) {
                                item("עריכת מועדף", Icons.Outlined.Star, tint = c.accent) { favoriteSheet = true }()
                            } else {
                                item("הוסף למועדפים", Icons.Outlined.StarOutline) {
                                    scope.launch {
                                        if (guardedWrite("Favorite") { favorites.add(book.id, placeTitle, System.currentTimeMillis(), toc?.id, placeLineId) }) {
                                            Toast.makeText(context, "נוסף למועדפים", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }()
                            }
                        }
                        item("ההערות שלי", Icons.Outlined.EditNote, onClick = onNotesPage)()
                    }
                }
            }
        }
    }
    if (favoriteSheet && favKey != null && placeTitle != null && isTabInFront()) {
        FavoriteEditSheet(favKey, placeTitle, onDismiss = { favoriteSheet = false })
    }
}

private class ToolbarAction(
    val icon: ImageVector,
    val label: String,
    val menuLabel: String,
    val selected: Boolean = false,
    val dimmed: Boolean = false,
    val levels: Int? = null,
    val onClick: () -> Unit,
)
