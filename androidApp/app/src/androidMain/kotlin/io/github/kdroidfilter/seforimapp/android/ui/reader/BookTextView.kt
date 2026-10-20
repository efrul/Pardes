package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.UserHighlight
import io.github.kdroidfilter.seforimapp.android.data.UserNote
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.android.ui.components.pinchTextZoom
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The book's text: desktop 0.6.1 features/bookcontent/ui/panels/bookcontent/views/BookContentView.kt on the reader's
 * index-paged lines. Selection bars (thick for the primary line, thin for its section), inline alt headings, the marked
 * passage, shnayim mikra, highlights and notes, find-in-page matches, text selection with desktop's menu, pinch zoom,
 * and a fast scroller over the whole book. Prefetches the visible lines' connections for the rails and panes.
 */
@OptIn(FlowPreview::class)
@Composable
fun BookText(
    vm: ReaderViewModel,
    state: ReaderUiState,
    listState: LazyListState,
    textSize: Float,
    selection: SelectionMenuSpec,
    textSelection: ListSelection,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** The find bar spans the text's top (phone), not floats at its corner (tablet). */
    findBarBand: Boolean = true,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    if (state.loading || state.book == null) {
        // Most books are ready sooner: no spinner flashing in and out
        LoadingBox(modifier, delayMillis = OPENING_SPINNER_DELAY_MS)
        return
    }
    val c = Zayit.colors
    val bookId = state.book.id
    LaunchedEffect(bookId) {
        app.userData.loadHighlights(bookId)
        app.userData.loadNotes(bookId)
    }
    val highlightsByBook by app.userData.highlightsByBook.collectAsState()
    val notesByBook by app.userData.notesByBook.collectAsState()
    val highlightsByLine = remember(highlightsByBook, bookId) { highlightsByBook[bookId].orEmpty().groupBy { it.lineId } }
    val notesByLine = remember(notesByBook, bookId, state.noteDraft) {
        val saved = notesByBook[bookId].orEmpty().groupBy { it.lineId }
        // The draft's range is underlined at once, before it is saved (desktop)
        val d = state.noteDraft ?: return@remember saved
        saved + (d.lineId to (saved[d.lineId].orEmpty() + UserNote(DRAFT_NOTE_ID, bookId, d.lineId, "", "", 0L, d.startOffset, d.endOffset)))
    }
    val targum by produceState(emptyMap<String, String>(), bookId, state.shnayimMikra) {
        value = if (state.shnayimMikra) loadShnayimMikraTargum(app, bookId) else emptyMap()
    }
    val charCounts by vm.charCounts.collectAsState()

    // The visible lines' connections (desktop prefetch of the viewport, debounced)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { vm.lines[it.index]?.id } }
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .debounce(150)
            .collect { vm.prefetchConnections(it) }
    }
    // Lines around what is on screen, the top of the screen asked last (the latest ask is read first)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { first ->
            vm.ensureLoaded(first + 40)
            vm.ensureLoaded(first)
        }
    }
    val density = LocalDensity.current
    val findOpen by rememberUpdatedState(state.find.open)
    val locator = remember { FindMatchLocator() }
    val noteLocator = remember { FindMatchLocator() }

    // A line put at the top sits flush with the card's edge, the line before it wholly above (else the last row of its
    // nikud showed in the top padding); with the find bar open, the padding is the bar's room and the line sits below it
    fun topOffset(index: Int): Int = if (index > 0 && !findOpen) with(density) { TextTopPadding.roundToPx() } else 0

    // The text's first frame already stands at the line it opens on: the request's effect below runs only after a frame
    // of the book's start, which then jumped
    val opening = remember(bookId) { state.scrollRequest?.takeIf { it.top } }
    if (opening != null) {
        val placed = remember(bookId) { BooleanArray(1) }
        if (!placed[0]) {
            SideEffect {
                placed[0] = true
                listState.requestScrollToItem(opening.index, topOffset(opening.index))
            }
        }
    }

    // Desktop revealFindMatch: the match itself (its line may be taller than the screen) to the middle of what the find
    // bar leaves; with [onlyIfHidden], one already readable stays. False while its line is not laid out.
    suspend fun revealFindMatch(lineId: Long, onlyIfHidden: Boolean): Boolean {
        val top = locator.matchTop(lineId) ?: return false
        val viewport = listState.layoutInfo.viewportSize.height
        val start = with(density) { FindBarRoom.toPx() }
        val end = viewport - with(density) { FindBottomReserve.toPx() }
        if (onlyIfHidden && top in start..end) return true
        if (onlyIfHidden && top !in -viewport.toFloat()..viewport * 2f) return false // far off: jump instead
        listState.scrollBy(top - if (end > start) (start + end) / 2f else start)
        return true
    }

    // Requests from the reader (open at a line, TOC, find, keys): the line at the top, the find match readable, or the
    // line brought into view
    LaunchedEffect(state.scrollRequest) {
        val req = state.scrollRequest ?: return@LaunchedEffect
        vm.ensureLoaded(req.index)
        when {
            req.top -> listState.scrollToItem(req.index, topOffset(req.index))
            state.find.open && state.find.current?.lineId == req.lineId -> {
                awaitNextLayout() // the line recomposed with the current match publishes where it is
                if (!revealFindMatch(req.lineId, onlyIfHidden = true)) {
                    listState.scrollToItem(req.index)
                    // The line may still be loading: retry until it is laid out
                    withTimeoutOrNull(FIND_JUMP_TIMEOUT_MS) {
                        do awaitNextLayout() while (!revealFindMatch(req.lineId, onlyIfHidden = false))
                    }
                }
            }
            else -> {
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == req.index }
                val fullyVisible = item != null && item.offset >= 0 && item.offset + item.size <= info.viewportEndOffset
                if (!fullyVisible) listState.scrollToItem(req.index, topOffset(req.index))
            }
        }
        vm.consumeScrollRequest(req)
    }

    // The row a note's passage starts on, readable (below the find bar when it is open, a little under the top);
    // false while its line is not laid out
    suspend fun revealNotePassage(lineId: Long): Boolean {
        val row = noteLocator.matchRow(lineId) ?: return false
        val start = if (findOpen) with(density) { FindBarRoom.toPx() } else 0f
        val end = listState.layoutInfo.viewportSize.height.toFloat()
        if (row.start >= start && row.endInclusive <= end) return true
        listState.scrollBy(row.start - start - with(density) { NotePassageMargin.toPx() })
        return true
    }

    // A note being written: on a phone the notes pane, then the keyboard, shrink the text under it (the list keeps its
    // first line); the noted line is kept in view at each resize, and when the strip is shorter than the line, the
    // row of the passage noted rather than the line's first
    LaunchedEffect(state.noteDraft?.lineId) {
        val lineId = state.noteDraft?.lineId ?: return@LaunchedEffect
        val index = vm.lineIndexOf(lineId) ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .distinctUntilChanged()
            .collect {
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                val top = if (findOpen) 0 else -info.beforeContentPadding
                val room = info.viewportEndOffset - top
                if (item != null && item.offset >= top && item.offset + item.size <= info.viewportEndOffset) return@collect
                if (item == null || item.size <= room) {
                    listState.scrollToItem(index, topOffset(index))
                    awaitNextLayout()
                }
                // The line taller than the strip, or just laid out: its passage's row (the line's top when unknown)
                if (!revealNotePassage(lineId)) {
                    awaitNextLayout()
                    if (!revealNotePassage(lineId) && item != null) listState.scrollToItem(index, topOffset(index))
                }
            }
    }

    val searchRanges: suspend (String, String) -> List<IntRange> = remember(vm) { { text, q -> vm.searchRanges(text, q) } }
    // Desktop's smart find on a search result, while the bar holds its query: the search's highlighting on the lines
    // the find counts (all of them until those are known), and not the literal query's (desktop LineItem
    // highlightQuery/smartQuery); typing another query is the literal find
    val smart = state.find.open && state.find.smart
    val searchQuery = state.searchQuery?.takeIf { smart }
    val smartLines = state.find.smartLines
    val findQuery = if (smart) "" else state.find.activeQuery
    val current = state.find.current
    val markEnd = if (state.shnayimMikra) "סוף הקריאה" else "סוף הלימוד"

    // The selection's texts scrolled away, read as their Texts show them (LineItem, ShnayimMikraTargum)
    val diacritics = state.diacritics
    val shnayimMikra = state.shnayimMikra
    val textOf: (Long) -> String? = { unit ->
        val line = vm.lines[(unit / LINE_UNITS).toInt()]
        val verse = shnayimMikra && !line?.heRef.isNullOrBlank()
        when {
            line == null -> null
            unit % LINE_UNITS == LINE_PART_TEXT || (unit % LINE_UNITS == LINE_PART_AGAIN && verse) ->
                buildHtmlAnnotation(line.content, textSize, diacritics, c.isDark, c.accent).text.text
            unit % LINE_UNITS == LINE_PART_TARGUM && verse -> targum[normalizedRef(line.heRef!!)]?.let {
                buildHtmlAnnotation(it, shnayimMikraTargumSize(textSize), diacritics, c.isDark, c.accent).text.text
            }
            else -> null
        }
    }

    Box(modifier) {
        SelectableArea(
            selection,
            textSelection,
            listState,
            textOf,
            resetKey = Triple(bookId, diacritics, shnayimMikra),
            modifier = Modifier.fillMaxSize(),
            // The phone's find bar spans the top of the text: nothing under it is selected, and the edge scroll starts
            // below it (the tablet's floats at a corner, beside visible text)
            topInset = if (state.find.open && findBarBand) FindBarRoom else 0.dp,
            prepare = { units -> vm.loadRange((units.first / LINE_UNITS).toInt(), (units.last / LINE_UNITS).toInt()) },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().onGloballyPositioned {
                    locator.list = it
                    noteLocator.list = it
                }.pinchTextZoom(onZoom),
                // The find bar floats over the top of the text: while it is open the text can scroll clear of it, the
                // book's first lines included, and a line put at the top lands just below it
                contentPadding = PaddingValues(start = 8.dp, end = 22.dp, top = if (state.find.open) FindBarRoom else TextTopPadding, bottom = 56.dp),
            ) {
                items(count = state.totalLines, key = { it }, contentType = { "line" }) { index ->
                    val line = vm.lines[index]
                    if (line == null) {
                        // After a moment: a fast-scroller drag passes thousands of lines, which ask nothing on the way
                        LaunchedEffect(index) {
                            delay(PLACEHOLDER_LOAD_DELAY_MS)
                            vm.ensureLoaded(index)
                        }
                        LinePlaceholder(textSize)
                        return@items
                    }
                    val selected = line.id in state.selectedLineIds
                    val thick = shouldUseThickBar(line.id, state.primaryLineId, state.isTocEntrySelection)
                    val next = vm.lines[index + 1]?.id
                    val extend = selected && next != null && next in state.selectedLineIds && shouldUseThickBar(next, state.primaryLineId, state.isTocEntrySelection) == thick
                    val prevSelected = vm.lines[index - 1]?.id?.let { it in state.selectedLineIds } == true
                    val headings = state.altToc.lineHeadings[line.id].orEmpty()
                    val headingsInBar = selected && prevSelected && headings.isNotEmpty()
                    val barColor = if (thick) c.accent else c.panelBorder
                    val marked = state.markedRange?.contains(line.lineIndex) == true
                    if (headings.isNotEmpty() && !headingsInBar) {
                        Column(Modifier.padding(start = SelectionBarWidth + 12.dp, end = 8.dp)) {
                            headings.forEach { h -> AltHeading(h.text, h.level, textSize) { vm.onLineTap(line) } }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (marked) Modifier.background(c.accent.copy(alpha = 0.07f)) else Modifier)
                            .selectionBar(selected, extend, barColor, thick),
                    ) {
                        Spacer(Modifier.width(SelectionBarWidth + 8.dp))
                        Column(Modifier.weight(1f)) {
                            if (headingsInBar) headings.forEach { h -> AltHeading(h.text, h.level, textSize) { vm.onLineTap(line) } }
                            val tap: (Boolean) -> Unit = { ctrl -> if (ctrl) vm.toggleLine(line) else vm.onLineTap(line) }
                            val lineHighlights: List<UserHighlight> = highlightsByLine[line.id] ?: emptyList()
                            val lineNotes: List<UserNote> = notesByLine[line.id] ?: emptyList()
                            val unit = index * LINE_UNITS
                            LineItem(
                                html = line.content,
                                textSize = textSize,
                                fontFamily = Zayit.fonts.book,
                                diacritics = state.diacritics,
                                highlights = lineHighlights,
                                notes = lineNotes,
                                findQuery = findQuery,
                                currentMatchOrdinal = current?.takeIf { it.lineId == line.id }?.ordinal,
                                searchQuery = searchQuery?.takeIf { smartLines == null || smartLines.hasLine(line.lineIndex, line.id) },
                                searchRanges = searchRanges,
                                onTap = tap,
                                modifier = Modifier.padding(vertical = LineVerticalPadding),
                                findLocator = locator.takeIf { state.find.open && current?.lineId == line.id },
                                noteLocator = noteLocator.takeIf { state.noteDraft?.lineId == line.id },
                                lineId = line.id,
                                selectable = rememberSelectableText(textSelection, unit + LINE_PART_TEXT),
                            )
                            // The verse read again, then its targum (headings have no reference)
                            if (state.shnayimMikra && !line.heRef.isNullOrBlank()) {
                                LineItem(
                                    html = line.content,
                                    textSize = textSize,
                                    fontFamily = Zayit.fonts.book,
                                    diacritics = state.diacritics,
                                    highlights = lineHighlights,
                                    notes = lineNotes,
                                    findQuery = "",
                                    currentMatchOrdinal = null,
                                    searchQuery = null,
                                    searchRanges = searchRanges,
                                    onTap = tap,
                                    modifier = Modifier.padding(vertical = LineVerticalPadding),
                                    selectable = rememberSelectableText(textSelection, unit + LINE_PART_AGAIN),
                                )
                                targum[normalizedRef(line.heRef)]?.let {
                                    ShnayimMikraTargum(it, textSize, state.diacritics, rememberSelectableText(textSelection, unit + LINE_PART_TARGUM))
                                }
                            }
                            if (state.markedRange?.last == line.lineIndex) MarkedEnd(markEnd)
                        }
                    }
                }
            }
        }
        FastScroller(
            listState = listState,
            totalLines = state.totalLines,
            charCounts = charCounts,
            labelAt = { i -> vm.placeLabel(i) },
            onDrag = { i -> listState.scrollToItem(i, topOffset(i)) },
            onDrop = vm::scrollToIndex,
            modifier = Modifier.fillMaxSize(),
        )
        overlay()
    }
}

/**
 * Each line's selectable texts in [ListSelection] units (index × [LINE_UNITS] + part): the line, then in shnayim mikra
 * the verse read again and its targum.
 */
internal const val LINE_UNITS = 4L
private const val LINE_PART_TEXT = 0L
private const val LINE_PART_AGAIN = 1L
private const val LINE_PART_TARGUM = 2L

/** The text's top padding (the find bar's room replaces it while the bar is open). */
private val TextTopPadding = 10.dp

/**
 * The find bar's room over the top of the text (desktop FIND_BAR_RESERVE): the phone's bar and its band (6 + 48 + 6dp)
 * and the tablet's floating bar (10 + 48dp) end at it; a match above it is not readable.
 */
internal val FindBarRoom = 60.dp

/** Bottom margin under which a find match counts as hidden (desktop FIND_BOTTOM_RESERVE). */
private val FindBottomReserve = 48.dp

/** Room left above a noted passage's row brought into a strip shorter than its line. */
private val NotePassageMargin = 4.dp

/** How long a line not yet loaded stays on screen before it asks for its chunk (frames a drag only passes through). */
private const val PLACEHOLDER_LOAD_DELAY_MS = 50L

/** Longest wait for a far find match's line to load and lay out before giving up on centring it. */
private const val FIND_JUMP_TIMEOUT_MS = 3_000L

/** A book read sooner than this (most are) shows no spinner before its text. */
private const val OPENING_SPINNER_DELAY_MS = 300L

/** A frame callback runs before that frame's layout: the second one follows a completed layout. */
private suspend fun awaitNextLayout() = repeat(2) { withFrameNanos { } }

private val SPACES = Regex("""\s+""")

private fun normalizedRef(ref: String) = ref.replace(SPACES, " ").trim()

/**
 * The book's first default targum (Onkelos on the Chumash) by verse reference: "בראשית א, א" for "תרגום אונקלוס על
 * בראשית א, א" (desktop rememberShnayimMikraTargum). The targum's every line, mapped once.
 */
private suspend fun loadShnayimMikraTargum(app: ZayitApplication, bookId: Long): Map<String, String> = runCatching {
    val repository = app.libraryStore.repository()
    val book = repository.getBookCore(bookId) ?: return emptyMap()
    val targumBook = repository.getDefaultTargumIdsForBook(bookId).firstOrNull()?.let { repository.getBookCore(it) } ?: return emptyMap()
    val prefix = "${book.title} "
    repository.getLines(targumBook.id, 0, targumBook.totalLines - 1).mapNotNull { line ->
        val ref = line.heRef?.let(::normalizedRef) ?: return@mapNotNull null
        val start = ref.indexOf(prefix).takeIf { it >= 0 } ?: return@mapNotNull null
        ref.substring(start) to line.content
    }.toMap()
}.getOrElse { emptyMap() }
