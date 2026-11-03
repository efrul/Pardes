package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafMode
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.isBavli
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.LinePlaces
import io.github.kdroidfilter.seforimapp.features.bookcontent.segments.SelectedPassage
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookTabUi
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.ContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.Providers
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.commentatorSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.listedCommentators
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.*
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes.NoteDraftAnchor
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import kotlin.time.Duration.Companion.milliseconds

/**
 * Whether the window's panes for a tab are in place (see `PaneSync`): a book's text first lays out
 * only then, so its initial scroll anchoring happens in its final frame. Always true outside a window.
 */
val LocalBookTextReady = staticCompositionLocalOf<(String) -> Boolean> { { true } }

@OptIn(ExperimentalSplitPaneApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun BookContentPanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
    isRestoringSession: Boolean = false,
    searchUi: SearchHomeUiState = SearchHomeUiState(),
    searchCallbacks: HomeSearchCallbacks =
        HomeSearchCallbacks(
            onReferenceQueryChanged = {},
            onTocQueryChanged = {},
            onGlobalExtendedChange = {},
            onSubmitTextSearch = {},
            onOpenReference = {},
            onPickBook = {},
            onPickToc = {},
        ),
    isSelected: Boolean = true,
    bookCharCounts: IntArray? = null,
    noteDraft: NoteDraftAnchor? = null,
    tabUi: BookTabUi? = null,
) {
    val homeCardModifier =
        Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
    Box(modifier = modifier.fillMaxSize()) {
        when {
            // If no book is selected
            uiState.navigation.selectedBook == null -> {
                // If we're actively loading a book for this tab, avoid flashing the Home screen.
                // Show a minimal loader until the selected book is ready.
                if (uiState.isLoading || isRestoringSession) {
                    LoaderPanel()
                } else {
                    HomeView(
                        onEvent = onEvent,
                        searchUi = searchUi,
                        searchCallbacks = searchCallbacks,
                        modifier = homeCardModifier,
                    )
                }
            }

            // Book is selected but providers are not ready yet (initialization in progress)
            // Show a centered loader to avoid flash of partial content.
            uiState.providers == null || uiState.isLoading -> {
                LoaderPanel()
            }

            // Main content when book and providers are ready
            else -> {
                BookContentPanelContent(
                    uiState = uiState,
                    onEvent = onEvent,
                    diacritics = diacritics,
                    isSelected = isSelected,
                    bookCharCounts = bookCharCounts,
                    noteDraft = noteDraft,
                    tabUi = tabUi ?: viewModel { BookTabUi() },
                )
            }
        }
    }
}

@Composable
private fun BookContentPanelContent(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
    isSelected: Boolean,
    bookCharCounts: IntArray?,
    noteDraft: NoteDraftAnchor?,
    tabUi: BookTabUi,
) {
    val providers = uiState.providers ?: return
    val selectedBook = uiState.navigation.selectedBook ?: return
    // Latched: once laid out, the text stays composed whatever the panes do next.
    var laidOut by remember(selectedBook.id) { mutableStateOf(false) }
    if (!laidOut && LocalBookTextReady.current(uiState.tabId)) laidOut = true
    if (!laidOut) {
        LoaderPanel()
        return
    }
    var isBookContentZoomInProgress by remember { mutableStateOf(false) }

    // Create LazyListState AFTER loading check, so anchorId is correctly set
    // When restoring with an anchor, use the computed anchorIndex which accounts for
    // lines near the beginning of the book (where target isn't at INITIAL_LOAD_SIZE/2)
    val bookListState =
        remember(selectedBook.id) {
            val hasAnchor = uiState.content.anchorId != -1L
            val initialIndex = if (hasAnchor) uiState.content.anchorIndex else uiState.content.scrollIndex
            LazyListState(
                firstVisibleItemIndex = initialIndex.coerceAtLeast(0),
                firstVisibleItemScrollOffset = uiState.content.scrollOffset.coerceAtLeast(0),
            )
        }

    // Shared with the links / commentaries / sources panes of the window showing this tab.
    val connectionsCache = tabUi.connections(selectedBook.id)
    // The commentators in panes of their own (kept by name), as books of this one's lines the text has loaded
    val detachedNames = uiState.content.detachedCommentators
    val detachedCommentatorIds: Set<Long> by remember(detachedNames, connectionsCache) {
        derivedStateOf<Set<Long>> {
            val names = detachedNames.filterTo(HashSet()) { it.isNotEmpty() }
            if (names.isEmpty()) {
                emptySet()
            } else {
                connectionsCache.values.flatMapTo(HashSet()) { snapshot ->
                    snapshot.commentatorGroups.flatMap { group -> group.commentators.filter { it.name in names }.map { it.bookId } }
                }
            }
        }
    }
    val prefetchConnections =
        remember(tabUi, selectedBook.id, providers) {
            { ids: List<Long> -> tabUi.prefetch(selectedBook.id, providers, ids) }
        }

    // Warm the text of the commentators that were open for the selected line, so that a background
    // tab (composed but not displayed) has its open commentaries ready and the on-demand pager load
    // is instant once the tab is shown. Gated on the pane actually being open.
    val openCommentatorIds = uiState.content.selectedCommentatorIds
    LaunchedEffect(uiState.content.primarySelectedLineId, openCommentatorIds, uiState.content.showCommentaries) {
        val lineId = uiState.content.primarySelectedLineId ?: return@LaunchedEffect
        if (!uiState.content.showCommentaries || openCommentatorIds.isEmpty()) return@LaunchedEffect
        providers.prefetchCommentaries(lineId, openCommentatorIds)
    }

    // A hovered line is likely the next one clicked: load its commentaries (the book's commentators, which a newly
    // selected line shows) once the pointer rests on it, so they show in the frame of the click.
    val hoveredLine = remember(selectedBook.id) { MutableStateFlow<Long?>(null) }
    val bookCommentatorIds =
        uiState.content.selectedCommentatorsByBook[selectedBook.id]
            .orEmpty()
            .ifEmpty { openCommentatorIds }
    // Whole groups checked ("כל המפרשים", "ראשונים על התנ״ך"): a line shows its own of them, but the unchecked ones
    val bookSelection = uiState.content.commentatorSelection(selectedBook.id)
    LaunchedEffect(hoveredLine, bookCommentatorIds, bookSelection, uiState.content.showCommentaries) {
        if (!uiState.content.showCommentaries) return@LaunchedEffect
        if (!bookSelection.showsGroups && bookCommentatorIds.isEmpty()) return@LaunchedEffect
        hoveredLine
            .filterNotNull()
            .debounce(HOVER_PREFETCH_DELAY)
            .collectLatest { lineId ->
                val ids =
                    if (bookSelection.showsGroups) {
                        bookSelection.shownOf(providers.getCommentatorGroupsForLine(lineId).listedCommentators())
                    } else {
                        bookCommentatorIds
                    }
                providers.prefetchCommentaries(lineId, ids)
            }
    }

    val hasBottomPane = uiState.content.showCommentaries || uiState.content.showSources
    val panelBackground = JewelTheme.globalColors.panelBackground
    val paneCardModifier =
        remember(hasBottomPane, panelBackground) {
            islandsCardModifier(panelBackground, bottom = if (hasBottomPane) 3.dp else 6.dp)
        }

    // Collect paging data here to keep BookContentView skippable
    val lazyPagingItems = providers.linesPagingData.collectAsLazyPagingItems()

    CompositionLocalProvider(LocalBookContentZoomInProgress provides isBookContentZoomInProgress) {
        // The links / commentaries / sources panes dock around this text and the breadcrumb sits
        // under them (see the window body), as the split panes had it.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                // A Talmud tractate as its Vilna page, at the tab's line: its clicks select as the text's, and the
                // panes around it (the text's, as they are, wherever they are docked) follow
                run {
                    val repository = LocalAppGraph.current.repository
                    val scope = rememberCoroutineScope()
                    val content by rememberUpdatedState(uiState.content)
                    // A Talmud tractate as its Vilna page: the same view, its items amudim
                    val daf =
                        if (uiState.content.dafView && selectedBook.isBavli) {
                            DafMode(
                                book = selectedBook,
                                onSelectLine = { lineId, withModifier ->
                                    scope.launch {
                                        runSuspendCatching { repository.getLine(lineId) }.getOrNull()?.let {
                                            onEvent(BookContentEvent.LineSelected(it, withModifier))
                                        }
                                    }
                                },
                                onSelectPassage = { lineId, comments, commentBooks ->
                                    scope.launch {
                                        selectDafPassage(
                                            repository,
                                            providers,
                                            content,
                                            selectedBook.id,
                                            lineId,
                                            comments,
                                            commentBooks,
                                            onEvent,
                                        )
                                    }
                                },
                            )
                        } else {
                            null
                        }
                    BookContentView(
                        bookId = selectedBook.id,
                        lazyPagingItems = lazyPagingItems,
                        selectedLineIds = uiState.content.selectedLineIds,
                        primarySelectedLineId = uiState.content.primarySelectedLineId,
                        selectedPassage = uiState.content.selectedPassage,
                        // Only the passages of the commentators shown answer: the pane's page (none while it is hidden),
                        // and those in panes of their own
                        passageCommentatorIds =
                            (if (uiState.content.showCommentaries) uiState.content.visibleCommentatorIds else emptySet()) +
                                detachedCommentatorIds,
                        isTocEntrySelection = uiState.content.isTocEntrySelection,
                        markedLines =
                            uiState.content.markedRange
                                ?.takeIf { it.bookId == selectedBook.id }
                                ?.let { it.first..it.last },
                        // The Chumash's only: another book opened in the tab, by any way, reads as usual
                        shnayimMikra = uiState.content.shnayimMikra && selectedBook.isChumash,
                        onLineSelect = { line, isModifier ->
                            onEvent(BookContentEvent.LineSelected(line, isModifier))
                        },
                        onEvent = onEvent,
                        tabId = uiState.tabId,
                        diacritics = diacritics,
                        draftNote = noteDraft,
                        modifier = paneCardModifier,
                        preservedListState = bookListState,
                        scrollIndex = uiState.content.scrollIndex,
                        scrollOffset = uiState.content.scrollOffset,
                        scrollToLineTimestamp = uiState.content.scrollToLineTimestamp,
                        anchorId = uiState.content.anchorId,
                        anchorIndex = uiState.content.anchorIndex,
                        topAnchorLineId = uiState.content.topAnchorLineId,
                        topAnchorTimestamp = uiState.content.topAnchorRequestTimestamp,
                        onScroll = { bookId, anchorId, anchorIndex, scrollIndex, scrollOffset ->
                            onEvent(
                                BookContentEvent.ContentScrolled(
                                    bookId = bookId,
                                    anchorId = anchorId,
                                    anchorIndex = anchorIndex,
                                    scrollIndex = scrollIndex,
                                    scrollOffset = scrollOffset,
                                ),
                            )
                        },
                        altHeadingsByLineId = uiState.altToc.lineHeadingsByLineId.asStableAltHeadings(),
                        lineConnections = connectionsCache,
                        onPrefetchLineConnections = prefetchConnections,
                        onLineHover = { hoveredLine.value = it },
                        isSelected = isSelected,
                        bookCharCounts = bookCharCounts,
                        onPointerZoomInProgressChange = { isBookContentZoomInProgress = it },
                        focusRequest = uiState.content.focusTextRequest,
                        daf = daf,
                    )
                }
            }
        }
    }
}

/**
 * A passage the daf view selected ([comments] on the Gemara line [lineId]): their commentators shown in the
 * commentaries pane, as when checked in its list, and the pane open; then the passage, as a click on one in the text
 * selects it (its line too), which the pane brings in view.
 */
private suspend fun selectDafPassage(
    repository: SeforimRepository,
    providers: Providers,
    content: ContentState,
    bookId: Long,
    lineId: Long,
    comments: Set<Long>,
    commentBooks: Set<Long>,
    onEvent: (BookContentEvent) -> Unit,
) {
    val line = runSuspendCatching { repository.getLine(lineId) }.getOrNull() ?: return
    val listed = runSuspendCatching { providers.getCommentatorGroupsForLine(lineId).listedCommentators() }.getOrDefault(emptyMap())
    val shown = content.commentatorSelection(bookId).shownOf(listed)
    val missing = commentBooks.filter { it in listed && it !in shown }
    if (missing.isNotEmpty()) onEvent(BookContentEvent.SelectedCommentatorsChanged(lineId, shown + missing, listed))
    if (!content.showCommentaries) onEvent(BookContentEvent.ToggleCommentaries)
    // Anchors of its own: a daf passage is none of the text's (the Bavli's text has no anchors)
    val anchors = comments.mapTo(HashSet()) { CommentAnchor(DAF_PASSAGE, it.toInt()) }
    onEvent(BookContentEvent.PassageSelected(line, SelectedPassage(lineId, anchors, comments, commentBooks)))
}

private const val DAF_PASSAGE = "daf"

/** The breadcrumb under a book's text and its line panes; drawn by the window under its inner dock. */
@Composable
fun BookBreadcrumb(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    BreadcrumbSection(
        uiState = uiState,
        onEvent = onEvent,
        verticalPadding = 8.dp,
    )
}

/** Whether [uiState]'s tab shows a book's text (not Home, not a loader): its breadcrumb and line panes follow. */
fun isBookTextShown(uiState: BookContentState): Boolean =
    uiState.navigation.selectedBook != null && uiState.providers != null && !uiState.isLoading

@Composable
private fun LoaderPanel(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun CommentsPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LineCommentsView(
            uiState = uiState,
            onEvent = onEvent,
            lineConnections = lineConnections,
            diacritics = diacritics,
        )
    }
}

/** One commentator split off the commentaries pane ([name], DetachedCommentators), in a pane of its own. */
@Composable
fun DetachedCommentatorPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    name: String,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        DetachedCommentatorView(
            uiState = uiState,
            onEvent = onEvent,
            commentator = name,
            lineConnections = lineConnections,
            diacritics = diacritics,
        )
    }
}

@Composable
fun SourcesPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LinePlacesView(kind = LinePlaces.Sources, uiState = uiState, onEvent = onEvent, diacritics = diacritics)
    }
}

@Composable
fun ReferencesPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LinePlacesView(kind = LinePlaces.References, uiState = uiState, onEvent = onEvent, diacritics = diacritics)
    }
}

@Composable
fun TargumPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LineTargumView(
            uiState = uiState,
            onEvent = onEvent,
            lineConnections = lineConnections,
            diacritics = diacritics,
        )
    }
}

@Composable
private fun BreadcrumbSection(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    verticalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val sectionModifier =
        modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp, start = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
    Column(modifier = sectionModifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreadcrumbView(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
            BreadcrumbActionsView(uiState = uiState)
        }
    }
}

private fun islandsCardModifier(
    panelBackground: androidx.compose.ui.graphics.Color,
    top: Dp = 6.dp,
    bottom: Dp = 6.dp,
): Modifier =
    Modifier
        .fillMaxSize()
        .padding(top = top, bottom = bottom, start = 4.dp, end = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(panelBackground)

private val HOVER_PREFETCH_DELAY = 60.milliseconds
