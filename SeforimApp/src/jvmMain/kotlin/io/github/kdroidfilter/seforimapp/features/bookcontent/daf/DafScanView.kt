package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.nucleusframework.pdfium.rememberPdfReaderState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabShown
import io.github.kdroidfilter.seforimapp.features.pdf.PAGES_CACHE_BYTES
import io.github.kdroidfilter.seforimapp.features.pdf.PdfPageList
import io.github.kdroidfilter.seforimapp.features.pdf.firstPageOf
import io.github.kdroidfilter.seforimapp.features.pdf.rememberPdfiumLoaded
import io.github.kdroidfilter.seforimapp.features.pdf.rowCount
import io.github.kdroidfilter.seforimapp.features.pdf.rowOf
import io.github.kdroidfilter.seforimapp.features.pdf.spreadPageWidth
import io.github.kdroidfilter.seforimapp.features.pdf.turnPage
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan_choose
import seforimapp.seforimapp.generated.resources.daf_scan_cut_no
import seforimapp.seforimapp.generated.resources.daf_scan_cut_question
import seforimapp.seforimapp.generated.resources.daf_scan_cut_question_hint
import seforimapp.seforimapp.generated.resources.daf_scan_cut_yes
import seforimapp.seforimapp.generated.resources.daf_scan_failed
import seforimapp.seforimapp.generated.resources.daf_scan_missing
import seforimapp.seforimapp.generated.resources.daf_scan_missing_hint
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

// Pardes (requests 41, 43, 46 and 47 of docs/DESKTOP_STAGE2.md): a Talmud tractate shown as its scan, the third of its
// views beside its text and its daf, "גם כי יש כאלה שמעדיפים מקור וגם להשוואה בקלות".

/** The page of [amud] (`35b`) in a tractate's scan, whose first page is 2a; null for none. */
internal fun scanPageOf(amud: String): Int? = amudIndex(amud).takeIf { it != Int.MAX_VALUE }?.minus(FIRST_AMUD)?.takeIf { it >= 0 }

/** The amud (`35b`) on page [page] of a tractate's scan. */
internal fun amudOfScanPage(page: Int): String = "${page / 2 + 2}${if (page % 2 == 0) 'a' else 'b'}"

/** The order of 2a, a scan's first page. */
private val FIRST_AMUD = amudIndex("2a")

/** The page's widest at the zoom's 1 (and no wider than the view), as the daf's. */
private val MOST_PAGE_WIDTH = 1100.dp

/** The room on each side of the pages, as the daf's. */
private val PAGE_PADDING = 24.dp

/** How long the pages rest before their place is told, as a PDF book's. */
private val PLACE_DEBOUNCE = 400.milliseconds

private sealed interface ScanLoad {
    data object Looking : ScanLoad

    data object None : ScanLoad

    data class Found(
        val source: ScanSource,
    ) : ScanLoad
}

/**
 * Where the scan is, for what the reader asks of it ([DafControl]): the first line of the amud read at its top (the
 * tab's place, where the text and the daf open), the page read there, and its list of rows while it is shown;
 * [placedAnew] once the user set where the tractate begins in its file, so that the amud the list opens at is told.
 */
private class ScanPlace(
    var topLineId: Long?,
) {
    var page = 0
    var placedAnew = false
    var list: LazyListState? = null
    var rows = 0
    var spreads = false
}

/**
 * The tractate [book] as its scan, in the daf's place while the tab shows it so (BookContentState's `scanView`): its
 * pages one under the other, or two side by side as printed (the end bar's button), each under its amud's name, as wide
 * as the daf's at the daf's [zoom]. The tab's place is the text's and the daf's: the scan opens at the amud of the tab's
 * top line ([openAt], else [lineId]), follows the tab's line when it is chosen ([scrollToLineTimestamp]: the table of
 * contents, a search, the arrows), and once a scroll rests tells the first line of the amud read at its top
 * ([onScroll]), where the text and the daf open. The reader's keys turn its pages through [control], as the daf's.
 * Without a scan, a word on where it is looked for and a button that chooses the folder of scanned tractates, as
 * Settings › צורת הדף does. Only the tab in view holds the file.
 */
@Composable
internal fun DafScanReader(
    book: Book,
    control: DafControl,
    zoom: Float,
    lineId: Long?,
    scrollToLineTimestamp: Long,
    modifier: Modifier = Modifier,
    openAt: Long? = null,
    onScroll: (lineId: Long) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val scans = graph.dafScans
    val repository = graph.repository
    val scope = rememberCoroutineScope()
    val place = remember(book.id) { ScanPlace(openAt ?: lineId) }
    // What the reader asks of the daf asked of the scan while it is shown; where it was, after (the text opens there)
    SideEffect {
        control.page = { forward -> place.list?.turnPage(forward) }
        control.edge = { end -> place.list?.scrollToItem(if (end) (place.rows - 1).coerceAtLeast(0) else 0) }
        control.toLine = { id ->
            val page = amudOfLine(repository, id)?.let(::scanPageOf)
            if (page != null) place.list?.reveal(place.rowOfPage(page))
        }
        control.step = { false }
        control.where = { place.topLineId?.let { DafPlace(it, selectionShown = false, onComment = false) } }
    }
    // Looked for again when the folder of scans changes, or a tractate's first page in its file is set
    val folder by scans.folder.collectAsState()
    val firstPages by scans.firstPages.collectAsState()
    val load by produceState<ScanLoad>(ScanLoad.Looking, book.title, folder, firstPages) {
        value = runSuspendCatching { scans.of(book.title) }.getOrNull()?.let(ScanLoad::Found) ?: ScanLoad.None
    }
    Box(modifier.fillMaxSize()) {
        when (val state = load) {
            ScanLoad.Looking -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ScanLoad.None ->
                NoScan(
                    book.title,
                    onChoose = { scope.launch { pickScansFolder()?.let(scans::setFolder) } },
                    modifier = Modifier.align(Alignment.Center),
                )
            is ScanLoad.Found -> {
                if (LocalTabShown.current) ScanPages(state.source, book, place, zoom, lineId, scrollToLineTimestamp, onScroll)
            }
        }
    }
}

/** A folder picked with the system's picker, off the GTK event-loop thread as the data screen's pickers; null for none. */
internal suspend fun pickScansFolder(): File? = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }?.let { File(it.path) }

/** No scan of [tractate]: where it is looked for, and a button that chooses the folder of scans ([onChoose]). */
@Composable
private fun NoScan(
    tractate: String,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.daf_scan_missing, tractate), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(Res.string.daf_scan_missing_hint),
            color = JewelTheme.globalColors.text.info,
            textAlign = TextAlign.Center,
        )
        DefaultButton(onClick = onChoose) { Text(stringResource(Res.string.daf_scan_choose)) }
    }
}

/**
 * Whether to cut the scans into images, asked in the scan's place the first time one opens: what it gives, the room it
 * takes and where, and the two answers ([onAnswer]), which the settings change (request 51 of DESKTOP_STAGE3_PLAN.md).
 */
@Composable
private fun CutQuestion(
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.daf_scan_cut_question), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(Res.string.daf_scan_cut_question_hint),
            color = JewelTheme.globalColors.text.info,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultButton(onClick = { onAnswer(true) }) { Text(stringResource(Res.string.daf_scan_cut_yes)) }
            OutlinedButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.daf_scan_cut_no)) }
        }
    }
}

// The cut of a scan as its view waits for it: being read or begun, ready, or none (PDFium not called here), with the
// tractate's place in its file
private sealed interface CutLoad {
    data object Opening : CutLoad

    data class None(
        val source: ScanSource,
    ) : CutLoad

    data class Ready(
        val cut: ScanCut,
        val source: ScanSource,
    ) : CutLoad
}

/**
 * The scan [source] at the amud of the tab's place: drawn from its pages cut into images ([DafScanPages], request 56),
 * or, where PDFium cannot be called by Pardes, by the binding's reader as before ([BindingScan]). A file of the user's
 * folder has a line above its pages that says where the tractate's daf ב. is in it, and sets it (request 52).
 */
@Composable
private fun ScanPages(
    source: ScanSource,
    book: Book,
    place: ScanPlace,
    zoom: Float,
    lineId: Long?,
    scrollToLineTimestamp: Long,
    onScroll: (lineId: Long) -> Unit,
) {
    val graph = LocalAppGraph.current
    // Cut into images only by the user's word, asked the first time a scan opens (request 51 of DESKTOP_STAGE3_PLAN.md)
    val cutting by graph.dafScans.cut.collectAsState()
    if (cutting == null) {
        Box(Modifier.fillMaxSize()) { CutQuestion(onAnswer = graph.dafScans::setCut, modifier = Modifier.align(Alignment.Center)) }
        return
    }
    // PDFium loaded by the binding, if no PDF was opened yet, before the cut asks it for the scan
    val loaded = rememberPdfiumLoaded()
    val file = source.file
    val cut by produceState<CutLoad>(CutLoad.Opening, source, loaded, cutting) {
        value =
            when (loaded) {
                null -> CutLoad.Opening
                false -> CutLoad.None(source)
                // Not cut by the user's word: a cut kept whole still shows, else the binding draws the file
                true -> {
                    val placed = graph.dafScans.byBookmarks(source, book.title)
                    runSuspendCatching {
                        if (cutting == true) graph.dafScanPages.open(file) else graph.dafScanPages.whole(file)
                    }.getOrNull()?.let { CutLoad.Ready(it, placed) } ?: CutLoad.None(placed)
                }
            }
    }
    val placed = rememberPlaced(file, place)
    val shown = (cut as? CutLoad.Ready)?.source ?: (cut as? CutLoad.None)?.source
    Column(Modifier.fillMaxSize()) {
        if (shown != null && shown.personal) {
            ScanFirstPageBar(
                shown,
                topPage = { shown.first + place.page },
                onSet = { first, page ->
                    place.page = page
                    place.placedAnew = true
                    graph.dafScans.setFirstPage(firstPageKey(file, book.title), first)
                },
            )
        }
        Box(Modifier.weight(1f)) {
            when (val state = cut) {
                // Nothing drawn for the moment the cut is read: the page follows in the next frames
                CutLoad.Opening -> Box(Modifier.fillMaxSize())
                is CutLoad.None -> BindingScan(state.source, book, place, zoom, lineId, scrollToLineTimestamp, onScroll)
                is CutLoad.Ready ->
                    if (placed) {
                        val pages = state.source.pagesIn(state.cut.pages)
                        // Opened again at the place's page when the tractate's first page in the file changes
                        key(state.source.first, pages) {
                            ScanList(
                                pages,
                                book,
                                place,
                                zoom,
                                lineId,
                                scrollToLineTimestamp,
                                onScroll,
                            ) { listState, width, spreads, title ->
                                ScanPageList(
                                    file,
                                    state.cut,
                                    listState,
                                    width,
                                    spreads = spreads,
                                    title = title,
                                    first = state.source.first,
                                    count = pages,
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
            }
        }
    }
}

// Whether the page the scan opens at is known: the amud of the tab's place, set in [place]
@Composable
private fun rememberPlaced(
    file: File,
    place: ScanPlace,
): Boolean {
    val repository = LocalAppGraph.current.repository
    var placed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        place.page = place.topLineId?.let { amudOfLine(repository, it) }?.let(::scanPageOf) ?: 0
        placed = true
    }
    return placed
}

/** The scan [source] read whole and drawn by the binding's reader, where Pardes cannot call PDFium itself. */
@Composable
private fun BindingScan(
    source: ScanSource,
    book: Book,
    place: ScanPlace,
    zoom: Float,
    lineId: Long?,
    scrollToLineTimestamp: Long,
    onScroll: (lineId: Long) -> Unit,
) {
    val file = source.file
    val reader = rememberPdfReaderState(cacheBytes = PAGES_CACHE_BYTES)
    var failure by remember(file) { mutableStateOf<String?>(null) }
    var opened by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        // The binding reads the whole file into memory; a missing native library is an Error, caught too
        runSuspendCatching { reader.open(withContext(Dispatchers.IO) { file.readBytes() }) }
            .onSuccess { opened = true }
            .onFailure { failure = it.message ?: it::class.simpleName }
    }
    val placed = rememberPlaced(file, place)
    val error = failure ?: reader.error?.message
    when {
        error != null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.daf_scan_failed, error), Modifier.padding(24.dp))
            }
        !opened || !placed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> {
            val pages = source.pagesIn(reader.pageCount)
            // Opened again at the place's page when the tractate's first page in the file changes
            key(source.first, pages) {
                ScanList(pages, book, place, zoom, lineId, scrollToLineTimestamp, onScroll) { listState, width, spreads, title ->
                    PdfPageList(
                        reader,
                        listState,
                        width,
                        spreads = spreads,
                        selectableText = false,
                        title = title,
                        first = source.first,
                        count = pages,
                    )
                }
            }
        }
    }
}

/**
 * The scan's [pages] in their list ([pagesList]: its state, each page's width, two side by side or not, and each one's
 * title), at the page of [place], following the tab and telling it where it is.
 */
@Composable
private fun ScanList(
    pages: Int,
    book: Book,
    place: ScanPlace,
    zoom: Float,
    lineId: Long?,
    scrollToLineTimestamp: Long,
    onScroll: (lineId: Long) -> Unit,
    pagesList: @Composable (listState: LazyListState, width: Dp, spreads: Boolean, title: (Int) -> String) -> Unit,
) {
    val graph = LocalAppGraph.current
    val repository = graph.repository
    val spreads by graph.appSettings.pageSpreadsFlow
        .collectAsState()
    val rows = rowCount(pages, spreads)
    // At the page read at the top, kept as it scrolls: the rows changed (a page each, or two), a new list opens there
    val listState = remember(spreads) { LazyListState(rowOf(place.page.coerceIn(0, (pages - 1).coerceAtLeast(0)), spreads)) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { place.page = firstPageOf(it, spreads) }
    }
    SideEffect {
        place.list = listState
        place.rows = rows
        place.spreads = spreads
    }
    DisposableEffect(place) { onDispose { place.list = null } }
    // The first line of every amud, by the table of contents: where the text and the daf open for an amud of the scan
    val amudLines by produceState(emptyMap<String, Long>(), book.id) {
        value = runSuspendCatching { firstLines(repository.getTocEntriesForBook(book.id)) }.getOrDefault(emptyMap())
    }
    FollowTheTab(listState, place, lineId, scrollToLineTimestamp)
    TellThePlace(listState, spreads, place, amudLines, onScroll)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val room = maxWidth - PAGE_PADDING * 2
        val base = if (spreads) min(spreadPageWidth(room), MOST_PAGE_WIDTH) else min(room, MOST_PAGE_WIDTH)
        pagesList(listState, base * zoom, spreads) { page -> "${book.title} ${amudTitle(amudOfScanPage(page))}" }
    }
}

/**
 * The tab's line ([lineId]) brought into view when it changes or the tab asks ([scrollToLineTimestamp]): the table of
 * contents, a search, a step of the arrows. Not at first (the scan opened at the tab's place), nor for the line the scan
 * told itself.
 */
@Composable
private fun FollowTheTab(
    listState: LazyListState,
    place: ScanPlace,
    lineId: Long?,
    scrollToLineTimestamp: Long,
) {
    val repository = LocalAppGraph.current.repository
    val list by rememberUpdatedState(listState)
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(lineId, scrollToLineTimestamp) {
        if (!opened) {
            opened = true
            return@LaunchedEffect
        }
        val id = lineId?.takeIf { it > 0 && it != place.topLineId } ?: return@LaunchedEffect
        val page = amudOfLine(repository, id)?.let(::scanPageOf) ?: return@LaunchedEffect
        list.reveal(place.rowOfPage(page))
    }
}

/**
 * The first line of the amud read at the top told as the tab's place ([onScroll]), as the daf tells its own: once the
 * pages rest there ([PLACE_DEBOUNCE]) after any move (a scroll, a page turned, the table of contents, a search), and as
 * the view leaves when they moved since. The table of contents' jump is not a scroll the list reports in progress, and
 * was not told (request 60 of docs/DESKTOP_STAGE3_PLAN.md).
 */
@OptIn(FlowPreview::class)
@Composable
private fun TellThePlace(
    listState: LazyListState,
    spreads: Boolean,
    place: ScanPlace,
    amudLines: Map<String, Long>,
    onScroll: (lineId: Long) -> Unit,
) {
    val lines by rememberUpdatedState(amudLines)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val told = remember(listState) { ToldLine() }

    fun lineNow(): Long? = lines[amudOfScanPage(firstPageOf(listState.firstVisibleItemIndex, spreads))]

    fun tell(lineId: Long) {
        told.line = lineId
        place.topLineId = lineId
        currentOnScroll(lineId)
    }
    LaunchedEffect(listState) {
        snapshotFlow { lineNow() }
            .filterNotNull()
            .distinctUntilChanged()
            // The amud the scan opened at is the tab's already, unless its pages were placed anew in their file
            .onEach {
                if (!told.opened) {
                    told.opened(it)
                    if (place.placedAnew) {
                        place.placedAnew = false
                        tell(it)
                    }
                }
            }.drop(1)
            .debounce(PLACE_DEBOUNCE)
            .collect { tell(it) }
    }
    DisposableEffect(listState) {
        onDispose { lineNow()?.takeIf { told.opened && it != told.line }?.let(::tell) }
    }
}

// The line last told, from the amud the scan opened at: leaving tells a newer one only
private class ToldLine {
    var line: Long? = null
    var opened = false
        private set

    fun opened(lineId: Long) {
        opened = true
        line = lineId
    }
}

/** The row of the scan's [page], within its rows. */
private fun ScanPlace.rowOfPage(page: Int): Int = rowOf(page, spreads).coerceIn(0, (rows - 1).coerceAtLeast(0))

/** Scrolls to [row] unless it is in view. */
private suspend fun LazyListState.reveal(row: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == row }) scrollToItem(row)
}

/** The amud (`35b`) of the line [lineId], by its entry in the table of contents, else by its reference. */
internal suspend fun amudOfLine(
    repository: SeforimRepository,
    lineId: Long,
): String? =
    runSuspendCatching {
        repository.getTocEntryForLine(lineId)?.text?.let(::amudOf) ?: repository.getLine(lineId)?.heRef?.let(::amudOf)
    }.getOrNull()
