package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.nucleusframework.pdfium.PdfReaderState
import dev.nucleusframework.pdfium.rememberPdfReaderState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.features.pdf.PdfPageList
import io.github.kdroidfilter.seforimapp.features.pdf.firstPageOf
import io.github.kdroidfilter.seforimapp.features.pdf.rowCount
import io.github.kdroidfilter.seforimapp.features.pdf.rowOf
import io.github.kdroidfilter.seforimapp.features.pdf.spreadPageWidth
import io.github.kdroidfilter.seforimapp.features.pdf.turnPage
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan_change
import seforimapp.seforimapp.generated.resources.daf_scan_choose
import seforimapp.seforimapp.generated.resources.daf_scan_failed
import seforimapp.seforimapp.generated.resources.daf_scan_missing
import seforimapp.seforimapp.generated.resources.daf_scan_missing_hint
import seforimapp.seforimapp.generated.resources.daf_scan_unlink
import java.io.File

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

private sealed interface ScanLoad {
    data object Looking : ScanLoad

    data object None : ScanLoad

    data class Found(
        val file: File,
        val linked: Boolean,
    ) : ScanLoad
}

/**
 * Where the scan is, for what the reader asks of it ([DafControl]): the first line of the amud read at its top (the
 * tab's place, where the text and the daf open), the page read there, and its list of rows while it is shown.
 */
private class ScanPlace(
    var topLineId: Long?,
) {
    var page = 0
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
 * Without a scan, a word on where it is looked for and a button that links a file to the tractate; with one, a line
 * over its foot to change it. Only the tab in view holds the file.
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
    // Bumped by a link, to look for the scan again
    var links by remember { mutableIntStateOf(0) }
    val load by produceState<ScanLoad>(ScanLoad.Looking, book.title, links) {
        val file = runSuspendCatching { scans.of(book.title) }.getOrNull()
        value =
            file?.let { ScanLoad.Found(it, linked = runSuspendCatching { scans.linked(book.title) }.getOrNull() != null) }
                ?: ScanLoad.None
    }
    val choose: () -> Unit = {
        scope.launch {
            // Off the GTK event-loop thread, as the data screen's pickers (see pickDatabaseParts)
            val picked =
                withContext(Dispatchers.IO) { FileKit.openFilePicker(type = FileKitType.File(extensions = setOf("pdf"))) }
                    ?: return@launch
            runSuspendCatching { scans.link(book.title, File(picked.path)) }
            links++
        }
    }
    Box(modifier.fillMaxSize()) {
        when (val state = load) {
            ScanLoad.Looking -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ScanLoad.None -> NoScan(book.title, onChoose = choose, modifier = Modifier.align(Alignment.Center))
            is ScanLoad.Found -> {
                if (LocalTabSelected.current) ScanPages(state.file, book, place, zoom, lineId, scrollToLineTimestamp, onScroll)
                ScanLine(
                    linked = state.linked,
                    onChange = choose,
                    onUnlink = {
                        scope.launch {
                            runSuspendCatching { scans.unlink(book.title) }
                            links++
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
            }
        }
    }
}

/** No scan of [tractate]: where it is looked for, and a button that links a file to it ([onChoose]). */
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
 * The line at the scan's foot, over its page as the daf's own line is (DafUpdateLine): another file for it
 * ([onChange]), and, for a file linked, Otzaria's again ([onUnlink]).
 */
@Composable
private fun ScanLine(
    linked: Boolean,
    onChange: () -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .clip(shape)
            .background(JewelTheme.globalColors.panelBackground)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Link(stringResource(Res.string.daf_scan_change), onClick = onChange)
        if (linked) Link(stringResource(Res.string.daf_scan_unlink), onClick = onUnlink)
    }
}

/** The scan [file], opened, at the amud of the tab's place. */
@Composable
private fun ScanPages(
    file: File,
    book: Book,
    place: ScanPlace,
    zoom: Float,
    lineId: Long?,
    scrollToLineTimestamp: Long,
    onScroll: (lineId: Long) -> Unit,
) {
    val repository = LocalAppGraph.current.repository
    val reader = rememberPdfReaderState()
    var failure by remember(file) { mutableStateOf<String?>(null) }
    var opened by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        // The binding reads the whole file into memory; a missing native library is an Error, caught too
        runSuspendCatching { reader.open(withContext(Dispatchers.IO) { file.readBytes() }) }
            .onSuccess { opened = true }
            .onFailure { failure = it.message ?: it::class.simpleName }
    }
    // The page the scan opens at: the amud of the tab's place
    var placed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        place.page = place.topLineId?.let { amudOfLine(repository, it) }?.let(::scanPageOf) ?: 0
        placed = true
    }
    val error = failure ?: reader.error?.message
    when {
        error != null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.daf_scan_failed, error), Modifier.padding(24.dp))
            }
        !opened || !placed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> ScanList(reader, book, place, zoom, lineId, scrollToLineTimestamp, onScroll)
    }
}

@Composable
private fun ScanList(
    reader: PdfReaderState,
    book: Book,
    place: ScanPlace,
    zoom: Float,
    lineId: Long?,
    scrollToLineTimestamp: Long,
    onScroll: (lineId: Long) -> Unit,
) {
    val graph = LocalAppGraph.current
    val repository = graph.repository
    val pages = reader.pageCount
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
        PdfPageList(
            reader = reader,
            listState = listState,
            pageWidth = base * zoom,
            spreads = spreads,
            selectableText = false,
            title = { page -> "${book.title} ${amudTitle(amudOfScanPage(page))}" },
        )
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
 * Once a scroll rests, the first line of the amud read at the top told as the tab's place ([onScroll]), as the daf tells
 * its own.
 */
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
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .drop(1)
            .filter { scrolling -> !scrolling }
            .collect {
                withFrameNanos { }
                val page = firstPageOf(listState.firstVisibleItemIndex, spreads)
                val lineId = lines[amudOfScanPage(page)] ?: return@collect
                place.topLineId = lineId
                currentOnScroll(lineId)
            }
    }
}

/** The row of the scan's [page], within its rows. */
private fun ScanPlace.rowOfPage(page: Int): Int = rowOf(page, spreads).coerceIn(0, (rows - 1).coerceAtLeast(0))

/** Scrolls to [row] unless it is in view. */
private suspend fun LazyListState.reveal(row: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == row }) scrollToItem(row)
}

/** The amud (`35b`) of the line [lineId], by its entry in the table of contents, else by its reference. */
private suspend fun amudOfLine(
    repository: SeforimRepository,
    lineId: Long,
): String? =
    runSuspendCatching {
        repository.getTocEntryForLine(lineId)?.text?.let(::amudOf) ?: repository.getLine(lineId)?.heRef?.let(::amudOf)
    }.getOrNull()
