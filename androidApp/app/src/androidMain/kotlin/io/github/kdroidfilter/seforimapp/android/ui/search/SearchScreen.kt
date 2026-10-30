package io.github.kdroidfilter.seforimapp.android.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.search.SearchEntity
import io.github.kdroidfilter.seforimapp.android.search.searchKey
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.components.Island
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.android.ui.components.pinchTextZoom
import io.github.kdroidfilter.seforimapp.android.ui.reader.PaneHeader
import io.github.kdroidfilter.seforimapp.android.ui.reader.ReaderPrefs
import io.github.kdroidfilter.seforimapp.android.ui.reader.ReaderRailButton
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalTabSession
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Below this the list takes the whole width and a tap opens a result (desktop TWO_PANES_MIN_WIDTH). */
private val TwoPanesMinWidth = 1000.dp
private val ListMinWidth = 380.dp
private val ListMaxWidth = 580.dp
private val TocPaneWidth = 260.dp

/** Desktop ReaderPane.BookDetails' docked extent. */
private val DetailsPaneWidth = 280.dp

/** In two panes the passage keeps at least this much beside the list: the side panes give way first. */
private val PreviewMinWidth = 400.dp
private val LogoHeight = 44.dp
private const val LOGO_RATIO = 1f
private const val TOP_TABS = 5

/** Below this (Jelly Star) the logo goes, the tabs scroll on one line, a snippet gets a third line. */
private val NarrowWidth = 360.dp

/** Below this height (Jelly Star, a phone held sideways) the header slides away as the results scroll. */
private val ShortHeight = 600.dp

/** Desktop AppSettings.increaseTextSize / decreaseTextSize step. */
private const val ZOOM_STEP = 2f

// Desktop 0.6.11 strings.
private const val SEARCH_SEARCHING = "מחפש..."
private const val SEARCH_NO_RESULTS = "לא נמצאו תוצאות"
private const val SEARCH_MORE_FROM_BOOK = "עוד %1\$d תוצאות מ%2\$s"
private const val SEARCH_STATUS_BASE = "כ־%1\$s תוצאות בספרי יסוד"
private const val SEARCH_STATUS_ALL = "כ־%1\$s תוצאות בכל הספרים"
private const val SEARCH_STATUS_IN = "כ־%1\$s תוצאות ב%2\$s"
private const val SEARCH_STATUS_FALLBACK = "לא נמצא בספרי יסוד · מוצגות %1\$s תוצאות מכל הספרים"
private const val SEARCH_SWITCH_ALL = "חיפוש בכל הספרים"
private const val SEARCH_SWITCH_BASE = "רק ספרי יסוד"
private const val SEARCH_TAB_ALL = "הכל"
private const val SEARCH_TABS_MORE = "עוד"
private const val SEARCH_TABS_LESS = "פחות"
private const val SEARCH_BACK_TO_ALL = "→ חזרה לכל התוצאות (מוצגות התוצאות מ%1\$s)"
private const val SEARCH_STOP = "בטל חיפוש"
private const val SEARCH_IN_PAGE = "הקלד כדי לחפש בעמוד..."
private const val FIND_PREVIOUS = "התוצאה הקודמת"
private const val FIND_NEXT = "התוצאה הבאה"
private const val FIND_CLOSE = "סגירת החיפוש"
private const val ZOOM_IN = "הגדל"
private const val ZOOM_IN_TOOLTIP = "הגדל גודל הטקסט"
private const val ZOOM_OUT = "הקטן"
private const val ZOOM_OUT_TOOLTIP = "הקטן גודל הטקסט"
private const val SEARCH_BOOK_DETAILS = "פרטי הספר"

// Android only: the engine failed (an I/O error, or Lucene unable to run on this device).
private const val SEARCH_FAILED = "החיפוש נכשל"

private fun String.fill(vararg args: Any): String = String.format(Locale.ROOT, this, *args)

/**
 * The search results page, a port of desktop 0.6.11 features/search/SearchResultScreen.kt (and TabsContent's search tab
 * wiring): one card with the logo's branch beside the shared smart bar, the category tabs, the way back from one book,
 * the status line, then the results grouped by runs of a book with their "more" links. From [TwoPanesMinWidth] the
 * selected result is read beside the list (PassagePreview, the knowledge panel above it), as on desktop; narrower, a
 * tap opens it. A result opens in a new tab at its line, with the query in the reader's find; on a phone Back
 * comes back here (the shell returns to the opener), at the same place. The title bar's find (Ctrl+F) finds within the
 * results. Tablets add the window's end rail (zoom, book details) and the selected result's book-details pane, as the
 * desktop search tab in its book shell; on a small screen the header slides away as the results scroll.
 */
@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun SearchScreen(tabId: String, destination: Destination.Search, wide: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val appVm = LocalAppViewModel.current ?: return
    val repo = remember(app) { app.libraryStore.repository() }
    // A tab brought back (session, workspace, reopened) returns to its category tab and place in the list
    val restore = remember(tabId) {
        appVm.savedSearchView(tabId)?.let { SearchViewRestore(it.searchCategoryIds, it.searchScrollIndex, it.searchScrollOffset, it.searchAnchorId) }
    }
    val vm: SearchViewModel = viewModel(key = "search-$tabId", factory = SearchViewModel.factory(repo, app.search, destination, restore))
    // The tab's own bar, opened where its search looked (desktop TabsContent "search-bar" + showScope)
    val barVm: SearchBarViewModel = viewModel(key = "search-bar-$tabId", factory = SearchBarViewModel.factory(repo, app.search, vm.searchScope))
    val state by vm.state.collectAsState()
    val breadcrumbs by vm.breadcrumbs.collectAsState()
    val entity by vm.entity.collectAsState()
    val textSize by app.prefs.textSize.collectAsState()
    val compactRails by app.prefs.compactRails.collectAsState()
    // One book-details setting for every tab, the reader's (desktop AppSettings.bookDetailsPaneFlow)
    val readerPrefs = remember(app) { ReaderPrefs.get(app) }
    val bookDetailsPref by readerPrefs.bookDetails.collectAsState()
    val c = Zayit.colors
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Searches run here go to the tab's title and to the history (the shell records them)
    val session = LocalTabSession.current
    val currentSession by rememberUpdatedState(session)
    LaunchedEffect(vm) { vm.executed.collect { currentSession?.reportSearchQuery(it.query, it.scope, it.globalExtended) } }

    // Find within the results: the title bar's magnifier and Ctrl+F (desktop findBarOpen per tab)
    var findOpen by rememberSaveable { mutableStateOf(false) }
    var findText by rememberSaveable { mutableStateOf("") }
    var findFocus by remember { mutableIntStateOf(0) }
    var currentHit by remember { mutableIntStateOf(-1) }
    LaunchedEffect(session?.tabId) {
        session?.findRequests?.collect {
            findOpen = true
            findFocus++
        }
    }
    val activeFind = if (findOpen && findText.trim().length >= 2) findText else ""

    // Narrowed to a book ("more from it"), or searched in one from the start (the bar's book or TOC scope): every
    // passage shows
    val inBook = state.bookFilterIds.isNotEmpty()
    val oneBook = inBook || state.scopeBook != null || state.scopeTocId != null
    val listItems = remember(state.results, state.bookCounts, oneBook) { flattenResults(state.results, state.bookCounts, oneBook) }
    val lineToItemIndex = remember(listItems) { HashMap<Long, Int>().apply { listItems.forEachIndexed { i, item -> put(item.hit.lineId, i) } } }

    BackHandler(enabled = findOpen) { findOpen = false }
    BackHandler(enabled = !findOpen && inBook) { vm.backFromBook() }

    fun openResult(result: SearchResult) {
        // Desktop openResult: a new tab at the line, with the find bar seeded with the query
        val q = state.query
        appVm.openInNewTab(Destination.Book(result.bookId, result.lineId, result.bookTitle, findQuery = q.takeIf { it.length >= 2 }), result.bookTitle)
    }

    fun openBookAt(bookId: Long, lineId: Long) {
        scope.launch {
            val title = vm.bookOf(bookId)?.title.orEmpty()
            appVm.openInNewTab(Destination.Book(bookId, lineId, title), title)
        }
    }

    // Citation links (seforim://line/<id>) in the passage preview open their line in a new tab, as in the reader
    // (desktop rememberRefLinkUriHandler, window-wide); any other link goes to the system
    val platformUri = LocalUriHandler.current
    val refUriHandler = remember(platformUri, repo) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val lineId = uri.takeIf { it.startsWith("seforim://line/") }?.removePrefix("seforim://line/")?.toLongOrNull()
                if (lineId == null) {
                    runCatching { platformUri.openUri(uri) }
                    return
                }
                scope.launch {
                    val line = runCatching { repo.getLine(lineId) }.getOrNull() ?: return@launch
                    openBookAt(line.bookId, line.id)
                }
            }
        }
    }

    val openAuthor: (Long, String) -> Unit = { id, name -> appVm.openInNewTab(Destination.Author(id, name), name) }

    // The home page's bar here: books, places, authors and links replace this tab; a text search runs in it, where the
    // bar says, in the base or all books as chosen (only "בכל הספרים" / Ctrl+Enter widens it)
    val barActions = remember(vm, appVm) {
        SearchBarActions(
            openBook = { book, line -> appVm.openInCurrentTab(Destination.Book(book.id, line, book.title), book.title) },
            openAuthor = { author ->
                val name = AuthorNames.display(author.name)
                appVm.openInCurrentTab(Destination.Author(author.id, name), name)
            },
            textSearch = { query, barScope, extended ->
                keyboard?.hide()
                focusManager.clearFocus()
                vm.searchFromBar(query, barScope, widen = extended)
            },
            openDestination = { d ->
                when (d) {
                    is Destination.Book -> appVm.openInCurrentTab(d, d.title)
                    is Destination.Search -> appVm.openInCurrentTab(d, d.query)
                    else -> Unit
                }
            },
        )
    }

    BoxWithConstraints(modifier.fillMaxSize().then(if (wide) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))) {
        // Wide windows read the selected passage beside the list, as a mail client. Measured on the whole page, rail
        // and side panes included: desktop measures what its docks leave, which on a 1280dp tablet with the book
        // details open would drop the passage
        val twoPanes = maxWidth >= TwoPanesMinWidth
        val compact = maxWidth < NarrowWidth
        val showToc = wide && state.tocTree != null
        val showDetails = wide && bookDetailsPref
        // Tablets: the end rail at the far side, as desktop's window bar beside its search tab
        val railWidth = if (!wide) 0.dp else if (compactRails) 48.dp + 8.dp else 64.dp + 8.dp
        val room = maxWidth - railWidth
        val sides = (if (showToc) TocPaneWidth else 0.dp) + (if (showDetails) DetailsPaneWidth else 0.dp)
        // The side panes give way so the list, and beside it the passage, keep their room (as the reader's text does)
        val mainMin = if (twoPanes) ListMinWidth + PreviewMinWidth else ListMinWidth
        val fit = if (sides > 0.dp && room - sides < mainMin) ((room - mainMin) / sides).coerceIn(0.6f, 1f) else 1f
        val listWidth = ((room - sides * fit) * 0.34f).coerceIn(ListMinWidth, ListMaxWidth)
        // The selection lives in the ViewModel, as the preview it drives; computed only in two panes
        val preview: PassagePreview? = if (twoPanes) vm.preview.collectAsState().value else null
        val selectedLineId = preview?.hit?.lineId
        // The selected result's book (the first result's in one pane, as desktop), while its pane is open
        val detailsBook: SearchEntity.BookEntity? = if (showDetails) vm.selectedBook.collectAsState().value else null
        // The query's book is not shown twice: it is already in the details pane when a result of it is (desktop)
        val panelEntity = entity.takeUnless { it is SearchEntity.BookEntity && detailsBook != null && it.book.id == detailsBook.book.id }
        val entityCard = !twoPanes && panelEntity != null
        val header = if (entityCard) 1 else 0
        // A small screen gives the results the room: the header slides away as they scroll (two panes are tall)
        val collapsible = !twoPanes && (compact || maxHeight < ShortHeight)
        val collapse = remember { CollapsingHeader() }
        LaunchedEffect(collapsible) { if (!collapsible) collapse.offset = 0f }
        // A new search or filter, the way back from a book, or nothing to scroll: the header shows again
        LaunchedEffect(state.scrollTarget.stamp, listItems.isEmpty()) { collapse.offset = 0f }
        val listState = rememberLazyListState(vm.scrollIndex, vm.scrollOffset)
        val listFocus = remember { FocusRequester() }

        // Where the list stands, kept as the user scrolls (not while a search loads)
        LaunchedEffect(listState, listItems, header) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .filter { !vm.state.value.isLoading }
                .collect { (index, offset) -> vm.onScroll(listItems.getOrNull(index - header)?.hit?.lineId ?: -1L, index, offset) }
        }
        // ... and for the session (desktop SearchPersistedState): the category tab and the place in the list
        val tabSession = LocalTabSession.current
        if (tabSession != null) {
            LaunchedEffect(tabSession, listState, listItems, header) {
                // Not while a search loads: a category tab is kept once its results show
                snapshotFlow { if (state.isLoading) null else Triple(state.selectedCategoryIds, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .debounce(500)
                    .collect { (categories, index, offset) ->
                        tabSession.reportSearchView(categories, index, offset, listItems.getOrNull(index - header)?.hit?.lineId ?: -1L)
                    }
            }
        }
        // A new search or filter starts at the top; the way back from a book returns where it was. Once per request
        var lastRestored by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(state.scrollTarget, listItems) {
            val target = state.scrollTarget
            if (listItems.isNotEmpty() && lastRestored != target.stamp) {
                val anchored = if (target.anchorId > 0) lineToItemIndex[target.anchorId]?.plus(header) else null
                // Cancelled (the items changed meanwhile), the request stays pending for the effect that replaces this one
                try {
                    listState.scrollToItem(anchored ?: target.index, target.offset)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                lastRestored = target.stamp
            }
        }
        // Infinite scroll: the next page loads within 10 items of the end (again after a page that grouped into few rows)
        LaunchedEffect(listState, state.hasMore, state.isLoadingMore, state.isLoading) {
            snapshotFlow {
                val info = listState.layoutInfo
                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 10
            }.distinctUntilChanged()
                .filter { it && state.hasMore && !state.isLoadingMore && !state.isLoading }
                .collect { vm.loadMore() }
        }

        fun navigateFind(next: Boolean) {
            val q = findText
            if (q.trim().length < 2) return
            // The rows the list shows (a book's later passages and repeated texts are behind "עוד"), as desktop walks
            // its visibleResults: the raw results scrolled to the top for each one hidden
            val visible = listItems
            if (visible.isEmpty()) return
            val size = visible.size
            var i = (if (currentHit in 0 until size) currentHit else 0).coerceIn(0, size - 1)
            val step = if (next) 1 else -1
            repeat(size) {
                i = (i + step + size) % size
                if (findAllMatches(buildAnnotatedFromHtml(visible[i].hit.snippet, 13f).text, q).isNotEmpty()) {
                    currentHit = i
                    scope.launch { listState.scrollToItem(i + header, 24) }
                    return
                }
            }
        }

        // Up and down move the selection through the list, Enter opens it (two panes, a hardware keyboard)
        fun onListKey(key: Key): Boolean {
            if (!twoPanes || listItems.isEmpty()) return false
            val index = listItems.indexOfFirst { it.hit.lineId == selectedLineId }.coerceAtLeast(0)
            val next = when (key) {
                Key.DirectionDown -> (index + 1).coerceAtMost(listItems.lastIndex)
                Key.DirectionUp -> (index - 1).coerceAtLeast(0)
                Key.Enter, Key.NumPadEnter -> {
                    preview?.let { openResult(it.hit) }
                    return true
                }
                else -> return false
            }
            vm.selectResult(listItems[next].hit.lineId)
            scope.launch { listState.animateScrollToItem(next + header) }
            return true
        }

        // What the bar is filled with: the executed query (the query to search until the first one ran)
        val barText = remember(state.executedQuery) { state.executedQuery.ifEmpty { state.query } }

        // RTL: the list first, on the right, as on desktop
        Row(Modifier.fillMaxSize()) {
            // A search in one book: its TOC with the results' counts at the start (desktop's TOC dock pane), on a tablet
            val tocTree = state.tocTree
            if (showToc && tocTree != null) {
                Island(Modifier.width(TocPaneWidth * fit).fillMaxHeight().padding(vertical = 6.dp, horizontal = 4.dp), title = TABLE_OF_CONTENTS) {
                    SearchTocPane(tocTree, state.tocCounts, state.selectedTocIds, state.scopeTocId, vm::setTocChecked)
                }
            }
            // One card: the bar, the tabs and the status over the list, as a mail client's search
            Column(
                (if (twoPanes) Modifier.width(listWidth) else Modifier.weight(1f))
                    .fillMaxHeight()
                    .card()
                    .then(if (collapsible) Modifier.nestedScroll(collapse.connection) else Modifier),
            ) {
                val sidePadding = if (compact) 10.dp else 14.dp
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        // Collapsed by the list's scroll: drawn shifted up, taking only its part still shown
                        .layout { measurable, constraints ->
                            if (!collapsible) {
                                val placeable = measurable.measure(constraints)
                                return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            }
                            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                            collapse.height = placeable.height
                            val shown = (placeable.height + collapse.offset.roundToInt()).coerceIn(0, placeable.height)
                            layout(placeable.width, shown.coerceAtMost(constraints.maxHeight)) { placeable.place(0, shown - placeable.height) }
                        }
                        .padding(start = sidePadding, end = sidePadding, top = if (compact) 8.dp else 12.dp, bottom = 6.dp),
                ) {
                    // The home page's smart bar, the logo's branch beside it, as Google's
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 10.dp)) {
                        if (!compact) LogoMark(Modifier.height(LogoHeight).aspectRatio(LOGO_RATIO))
                        UnifiedSearchBar(vm = barVm, actions = barActions, wide = wide, modifier = Modifier.weight(1f), initialText = barText, autoFocus = false)
                    }
                    // Always there, as Google's, even with one category: "הכל" also undoes a kept filter
                    if (state.tree.isNotEmpty()) {
                        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                        CategoryTabs(state.tree, state.selectedCategoryIds, compact, wide, vm::showOnlyCategory)
                    }
                    Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                    // Inside one book: the way back to all the results
                    if (inBook) {
                        ResultLink(
                            SEARCH_BACK_TO_ALL.fill(state.results.firstOrNull()?.bookTitle.orEmpty()),
                            c.accent,
                            13.sp,
                            minHeight = if (wide) 24.dp else 36.dp,
                            maxLines = 2,
                        ) { vm.backFromBook() }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (state.availability == SearchAvailability.Ready) StatusRow(state, oneBook, wide, compact, vm)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(listFocus)
                        .onPreviewKeyEvent { ev -> ev.type == KeyEventType.KeyDown && onListKey(ev.key) }
                        .focusable(),
                ) {
                    when {
                        state.availability == SearchAvailability.Checking -> LoadingBox(Modifier.fillMaxSize())
                        state.availability == SearchAvailability.IndexMissing -> {
                            val dir by produceState("") { value = withContext(Dispatchers.IO) { app.search.libraryDir.path } }
                            SearchIndexMissing(dir, Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                        }
                        state.failure != null && listItems.isEmpty() -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Text(SEARCH_FAILED, color = c.error, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            SelectionContainer { Text(state.failure.orEmpty(), color = c.textSecondary, fontSize = 12.sp) }
                        }
                        listItems.isEmpty() && !entityCard -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (state.isLoading) SEARCH_SEARCHING else SEARCH_NO_RESULTS, fontSize = (textSize * 0.875f).sp, color = c.text)
                        }
                        // The list follows the reading text's zoom, as the passage beside it (desktop 0.6.11)
                        else -> CompositionLocalProvider(LocalResultTextScale provides textSize / AppPrefs.DEFAULT_TEXT_SIZE) { LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = if (wide) 16.dp else 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                        ) {
                            // Narrower than desktop's two panes, the knowledge panel heads the list
                            // The value is captured here: an item composed after `entity` turned null must not read it again
                            val shownEntity = panelEntity
                            if (entityCard && shownEntity != null) {
                                item(key = "entity") { EntityPanel(shownEntity, ::openBookAt, openAuthor, Modifier.fillMaxWidth(), outlined = true) }
                            }
                            items(listItems, key = { it.hit.lineId }) { item ->
                                ResultView(
                                    item = item,
                                    selected = twoPanes && item.hit.lineId == selectedLineId,
                                    twoPanes = twoPanes,
                                    findQuery = activeFind,
                                    wide = wide,
                                    compact = compact,
                                    pieces = breadcrumbs[item.hit.lineId],
                                    onRequestBreadcrumb = vm::requestBreadcrumb,
                                    // Two panes: a tap shows the passage beside the list, a second tap (or a double tap) opens it
                                    onClick = { result ->
                                        if (twoPanes && result.lineId != selectedLineId) {
                                            vm.selectResult(result.lineId)
                                            runCatching { listFocus.requestFocus() }
                                        } else {
                                            openResult(result)
                                        }
                                    },
                                    onOpen = ::openResult,
                                    onMoreInBook = { vm.showMoreFromBook(item.hit.bookId) },
                                )
                            }
                            if (listItems.isEmpty() && entityCard) {
                                item(key = "empty") {
                                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Text(if (state.isLoading) SEARCH_SEARCHING else SEARCH_NO_RESULTS, fontSize = (textSize * 0.875f).sp, color = c.text)
                                    }
                                }
                            }
                            // The next page's spinner (lazy loading only)
                            if (state.isLoadingMore) item(key = "more") { LoadingBox(Modifier.fillMaxWidth().height(64.dp)) }
                        } }
                    }
                    // Loader over the shown results while a filter (category tab, one book) applies
                    if (state.isFiltering) {
                        Box(Modifier.fillMaxSize().background(c.panel.copy(alpha = 0.4f)).pointerInput(Unit) {}, contentAlignment = Alignment.Center) {
                            LoadingBox()
                        }
                    }
                }
            }
            if (twoPanes) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    panelEntity?.let { EntityPanel(it, ::openBookAt, openAuthor, Modifier.fillMaxWidth()) }
                    // Until the first result's passage is loaded, the pane says so
                    if (preview == null && (listItems.isNotEmpty() || state.isLoading)) {
                        LoadingBox(Modifier.weight(1f).fillMaxWidth().card())
                    }
                    preview?.let { shown ->
                        CompositionLocalProvider(LocalUriHandler provides refUriHandler) {
                            PassagePreviewPane(
                                preview = shown,
                                pieces = breadcrumbs[shown.hit.lineId],
                                textSize = textSize,
                                onOpen = { openResult(shown.hit) },
                                onOpenLine = { lineId -> openBookAt(shown.hit.bookId, lineId) },
                                // Two fingers on the passage zoom the text, as in the books (desktop 0.6.5 e3051846)
                                modifier = Modifier.weight(1f).pinchTextZoom { factor -> app.prefs.setTextSize(textSize * factor) },
                            )
                        }
                    }
                }
            }
            // The selected result's book, docked beside the passage on the rail's side (desktop's BookDetails pane)
            if (showDetails) {
                Island(Modifier.width(DetailsPaneWidth * fit).fillMaxHeight().padding(vertical = 6.dp, horizontal = 4.dp)) {
                    PaneHeader(SEARCH_BOOK_DETAILS, { readerPrefs.setBookDetails(false) })
                    when {
                        detailsBook != null -> SearchBookDetails(detailsBook, ::openBookAt, openAuthor, Modifier.weight(1f).fillMaxWidth())
                        listItems.isNotEmpty() || state.isLoading -> LoadingBox(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            if (wide) SearchRail(textSize, app.prefs::setTextSize, bookDetailsPref, readerPrefs::setBookDetails)
        }

        if (findOpen) {
            ResultsFindBar(
                text = findText,
                onText = { findText = it },
                focusRequest = findFocus,
                wide = wide,
                onNext = { navigateFind(true) },
                onPrev = { navigateFind(false) },
                onClose = { findOpen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).then(if (wide) Modifier else Modifier.fillMaxWidth()),
            )
        }
    }
}

/**
 * The results' header on a small screen: it slides up as the list scrolls down and comes back with the first scroll
 * up, as a phone browser's bar, so the results get the screen and the bar and the category tabs stay a flick away.
 * [offset] is how much of it is hidden, from 0 down to -[height] (px).
 */
private class CollapsingHeader {
    // Measured by the header's layout, read only while scrolling: not a state, a change must not recompose
    var height = 0
    var offset by mutableFloatStateOf(0f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val before = offset
            offset = (before + available.y).coerceIn(-height.toFloat(), 0f)
            return Offset(0f, offset - before)
        }

        // Never left half shown once the list stops: to the nearer end
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val target = if (offset < -height / 2f) -height.toFloat() else 0f
            if (offset != target) animate(offset, target) { value, _ -> offset = value }
            return Velocity.Zero
        }
    }
}

/**
 * The window's end rail beside a search tab (desktop EndVerticalBar with no book open): the passage's zoom, and at the
 * bottom the book-details pane's toggle, one setting with the reader's. Tablets.
 */
@Composable
private fun SearchRail(textSize: Float, onTextSize: (Float) -> Unit, bookDetails: Boolean, onBookDetails: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxHeight().padding(start = 2.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReaderRailButton(Icons.Outlined.ZoomIn, ZOOM_IN, enabled = textSize < AppPrefs.MAX_TEXT_SIZE, description = ZOOM_IN_TOOLTIP) { onTextSize(textSize + ZOOM_STEP) }
        ReaderRailButton(Icons.Outlined.ZoomOut, ZOOM_OUT, enabled = textSize > AppPrefs.MIN_TEXT_SIZE, description = ZOOM_OUT_TOOLTIP) { onTextSize(textSize - ZOOM_STEP) }
        Spacer(Modifier.weight(1f))
        ReaderRailButton(Icons.Outlined.Info, SEARCH_BOOK_DETAILS, selected = bookDetails) { onBookDetails(!bookDetails) }
    }
}

/**
 * The status line, the progress bar and the cancel button while a search runs (space kept for it otherwise, except on
 * a narrow phone, where the status needs the width more).
 */
@Composable
private fun StatusRow(state: SearchUiState, oneBook: Boolean, wide: Boolean, compact: Boolean, vm: SearchViewModel) {
    val c = Zayit.colors
    val loaded = maxOf(state.progressCurrent, state.results.size)
    val total = maxOf(loaded.toLong(), state.progressTotal ?: loaded.toLong())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // In one book, in an author's books, or in a category or the picked books and categories, where it searched: no
        // base/all books choice there. Desktop 0.6.5 still offers it in an author's books, which are all searched
        // whatever is chosen
        val scopeName = if (oneBook) {
            state.scopeBook?.title ?: state.results.firstOrNull()?.bookTitle
        } else {
            state.scopeAuthorName?.let { "ספרי $it" } ?: state.scopeLabel
        }
        if (state.isLoading) {
            SearchStatusLine(scopeName, total, true, state.globalExtended, state.baseBooksHadNoResults, wide, compact, vm::setGlobalExtended, Modifier.padding(end = 12.dp))
            // Only during the search itself, not while a page loads (that has its own spinner at the list's end)
            Box(Modifier.height(4.dp).weight(1f).clip(RoundedCornerShape(percent = 50)).background(c.textDisabled.copy(alpha = 0.3f))) {
                val known = (state.progressTotal ?: 0L) > 0L
                if (known) {
                    val fraction = (state.progressCurrent.toFloat() / state.progressTotal!!.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(c.accent))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxSize(), color = c.accent, trackColor = Color.Transparent)
                }
            }
        } else {
            SearchStatusLine(scopeName, total, false, state.globalExtended, state.baseBooksHadNoResults, wide, compact, vm::setGlobalExtended, Modifier.weight(1f))
        }
        // Space kept for the cancel button, so the line does not jump
        if (state.isLoading || !compact) {
            Box(Modifier.size(width = if (wide) 40.dp else 44.dp, height = if (wide) 28.dp else 44.dp), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    Box(Modifier.size(if (wide) 28.dp else 40.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = vm::cancel), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, SEARCH_STOP, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Where the search looked and how many it found, with the other choice as a link: the base books (the default) or all
 * of them; when the base books had nothing, says so. Wraps on a narrow phone, where only the link keeps a touch
 * target's height and the text's line is as tall as the text. Desktop SearchStatusLine.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchStatusLine(
    scope: String?,
    total: Long,
    isLoading: Boolean,
    globalExtended: Boolean,
    baseBooksHadNoResults: Boolean,
    wide: Boolean,
    compact: Boolean,
    onGlobalExtendedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grey = readingSecondary()
    val count = formatCount(total)
    val size = if (wide) 13.sp else 14.sp
    val rowHeight = if (wide) 20.dp else 36.dp
    val textHeight = if (compact) 24.dp else rowHeight
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        val text = when {
            isLoading -> SEARCH_SEARCHING
            scope != null -> SEARCH_STATUS_IN.fill(count, scope)
            baseBooksHadNoResults -> SEARCH_STATUS_FALLBACK.fill(count)
            globalExtended -> SEARCH_STATUS_ALL.fill(count)
            else -> SEARCH_STATUS_BASE.fill(count)
        }
        Box(Modifier.heightIn(min = textHeight), contentAlignment = Alignment.CenterStart) { Text(text, color = grey, fontSize = size) }
        if (!isLoading && !baseBooksHadNoResults && scope == null) {
            Box(Modifier.heightIn(min = textHeight), contentAlignment = Alignment.CenterStart) { Text("·", color = grey, fontSize = size) }
            ResultLink(if (globalExtended) SEARCH_SWITCH_BASE else SEARCH_SWITCH_ALL, Zayit.colors.accent, size, minHeight = rowHeight) {
                onGlobalExtendedChange(!globalExtended)
            }
        }
    }
}

/** The results' top categories as tabs (desktop CategoryTabs), the most frequent first. */
@Composable
private fun CategoryTabs(categories: List<SearchTreeCategory>, selectedIds: Set<Long>, compact: Boolean, wide: Boolean, onShowOnly: (Long?) -> Unit) {
    val sorted = remember(categories) { categories.sortedByDescending { it.count }.map { it.category.id to it.category.title } }
    val selected = sorted.firstOrNull { it.first in selectedIds }?.first
    FilterTabs(sorted, selected, compact, wide, onShowOnly)
}

/**
 * Tabs that filter a result list (desktop 0.6.11 FilterTabs), as Google's "All · Images · News": "הכל", then the first
 * five of [tabs] (id to title, most frequent first), the rest behind an "עוד" link. One at a time. On a narrow phone
 * ([compact]), all of them on one scrolling line. Shared by the results and the reader's places panes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterTabs(tabs: List<Pair<Long, String>>, selected: Long?, compact: Boolean, wide: Boolean, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var showAll by remember(tabs) { mutableStateOf(false) }
    if (compact) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
            item { FilterTab(SEARCH_TAB_ALL, selected == null, wide) { onSelect(null) } }
            items(tabs, key = { it.first }) { (id, title) -> FilterTab(title, selected == id, wide) { onSelect(id) } }
        }
        return
    }
    val shown = if (showAll) tabs else tabs.take(TOP_TABS) + tabs.drop(TOP_TABS).filter { it.first == selected }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        FilterTab(SEARCH_TAB_ALL, selected == null, wide) { onSelect(null) }
        shown.forEach { (id, title) -> FilterTab(title, selected == id, wide) { onSelect(id) } }
        // Not a tab: a link, in the accent as the other links of the results
        if (tabs.size > TOP_TABS) {
            ResultLink(if (showAll) SEARCH_TABS_LESS else SEARCH_TABS_MORE, Zayit.colors.accent, 13.sp, minHeight = if (wide) 28.dp else 34.dp) { showAll = !showAll }
        }
    }
}

@Composable
private fun FilterTab(label: String, selected: Boolean, wide: Boolean, onClick: () -> Unit) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = if (wide) 28.dp else 34.dp)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.textDisabled.copy(alpha = 0.5f), shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) c.accent else c.text, maxLines = 1)
    }
}

/** One result of the flat list: a passage, and how many more its book has ([more], on the book's first result only). */
@Immutable
private data class ResultItem(val hit: SearchResult, val more: Int)

/**
 * The results in relevance order, one passage per run of a book (its next ones are behind the "more" link), and a
 * passage whose text was already shown (the same prayer in two siddurim sections) dropped. A book may come back lower
 * with a later run. [bookCounts] (exact per-book totals) gives the "more in this book" count of a book's first result.
 * Desktop flattenResults; a line Lucene returned twice across pages shows once (the list is keyed by line).
 */
private fun flattenResults(results: List<SearchResult>, bookCounts: Map<Long, Int>, oneBook: Boolean): List<ResultItem> {
    val linesSeen = HashSet<Long>()
    if (oneBook) return results.filter { linesSeen.add(it.lineId) }.map { ResultItem(it, more = 0) }
    val items = ArrayList<ResultItem>()
    val booksSeen = HashSet<Long>()
    val textsSeen = HashSet<String>()
    var runBook = -1L
    for (r in results) {
        if (r.bookId == runBook) continue
        runBook = r.bookId
        if (!textsSeen.add(r.snippet.replace(HTML_TAG, "").searchKey())) continue
        if (!linesSeen.add(r.lineId)) continue
        val more = if (booksSeen.add(r.bookId)) ((bookCounts[r.bookId] ?: 1) - 1).coerceAtLeast(0) else 0
        items += ResultItem(r, more)
    }
    return items
}

/**
 * A result as on Google: the book as the bold title with the passage's place beside it in grey, the passage in grey
 * with the matched words bold, then a link to the book's other results. Desktop ResultView; on a phone one size up.
 */
@Composable
private fun ResultView(
    item: ResultItem,
    selected: Boolean,
    twoPanes: Boolean,
    findQuery: String,
    wide: Boolean,
    compact: Boolean,
    pieces: List<String>?,
    onRequestBreadcrumb: (SearchResult) -> Unit,
    onClick: (SearchResult) -> Unit,
    onOpen: (SearchResult) -> Unit,
    onMoreInBook: () -> Unit,
) {
    val hit = item.hit
    val currentOnRequestBreadcrumb by rememberUpdatedState(onRequestBreadcrumb)
    LaunchedEffect(hit.lineId) { if (pieces == null) currentOnRequestBreadcrumb(hit) }
    val place = remember(pieces, hit.bookTitle) { categoriesAndPlace(pieces, hit.bookTitle).second }
    // A double-tap handler holds every single tap back for the double-tap timeout: two panes only, where a tap
    // selects; on a phone a tap opens at once
    ResultRow(selected = selected, onClick = { onClick(hit) }, onDoubleClick = if (twoPanes) ({ onOpen(hit) }) else null) {
        ResultTitle(hit.bookTitle, place, wide)
        // Two lines of the passage (three on a narrow phone)
        SnippetText(hit.snippet, findQuery, wide, maxLines = if (compact) 3 else 2)
        if (item.more > 0) {
            ResultLink(SEARCH_MORE_FROM_BOOK.fill(item.more, hit.bookTitle), Zayit.colors.accent, resultSp(if (wide) 11f else 13f), minHeight = if (wide) 0.dp else 36.dp, onClick = onMoreInBook)
        }
    }
}

/**
 * Phones: the snippet keeps text on each side of the first hit, which pushes the hit past the two lines shown on a
 * narrow screen. Keep less before it; cuts are made at spaces outside <b>…</b>, so tags stay balanced.
 */
internal fun compactSnippet(html: String, before: Int = 40, after: Int = 200): String {
    val first = html.indexOf("<b>")
    if (first < 0) return html
    val spaces = ArrayList<Int>()
    var depth = 0
    var i = 0
    while (i < html.length) {
        when {
            html.startsWith("<b>", i) -> {
                depth++
                i += 3
            }
            html.startsWith("</b>", i) -> {
                depth--
                i += 4
            }
            else -> {
                if (html[i] == ' ' && depth == 0) spaces += i
                i++
            }
        }
    }
    val start = spaces.lastOrNull { it <= first - before }
    val end = spaces.firstOrNull { it >= first + after }
    if (start == null && end == null) return html
    return (if (start != null) "..." else "") + html.substring((start ?: -1) + 1, end ?: html.length) + (if (end != null) "..." else "")
}

/**
 * Find within the results (desktop FindInPageBar over the results): steps through the results whose snippet contains
 * the text, highlighted in them. Enter / the soft keyboard's search: next; Shift+Enter: previous; Esc or Back closes.
 */
@Composable
private fun ResultsFindBar(
    text: String,
    onText: (String) -> Unit,
    focusRequest: Int,
    wide: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(8.dp)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        delay(10)
        runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier
            .shadow(6.dp, shape)
            .background(c.panel, shape)
            .border(1.dp, c.panelBorder, shape)
            .height(if (wide) 36.dp else 48.dp)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box((if (wide) Modifier.width(200.dp) else Modifier.weight(1f)), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(SEARCH_IN_PAGE, color = c.textDisabled, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = if (wide) 13.sp else 15.sp, fontFamily = ZayitFonts.ui),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                if (ev.isShiftPressed) onPrev() else onNext()
                                true
                            }
                            Key.Escape -> {
                                onClose()
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
        Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(20.dp).background(c.divider))
        // Up/down, not left/right: unambiguous in a right-to-left layout
        FindButton(Icons.Outlined.KeyboardArrowUp, FIND_PREVIOUS, wide, onPrev)
        FindButton(Icons.Outlined.KeyboardArrowDown, FIND_NEXT, wide, onNext)
        FindButton(Icons.Outlined.Close, FIND_CLOSE, wide, onClose)
    }
}

@Composable
private fun FindButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, wide: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(if (wide) 28.dp else 40.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Zayit.colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}
