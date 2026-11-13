package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.pdfium.PdfPage
import dev.nucleusframework.pdfium.PdfReaderState
import dev.nucleusframework.pdfium.rememberPdfReaderState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
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

// Pardes (request 41 of docs/DESKTOP_STAGE2.md): the scan of the page in the daf view, in the daf's place while the end
// bar's button says so (DafScanButton), "גם כי יש כאלה שמעדיפים מקור וגם להשוואה בקלות".

/** The page of [amud] (`35b`) in a tractate's scan, whose first page is 2a; null for none. */
internal fun scanPageOf(amud: String): Int? = amudIndex(amud).takeIf { it != Int.MAX_VALUE }?.minus(FIRST_AMUD)?.takeIf { it >= 0 }

/** The amud (`35b`) on page [page] of a tractate's scan. */
internal fun amudOfScanPage(page: Int): String = "${page / 2 + 2}${if (page % 2 == 0) 'a' else 'b'}"

/** The order of 2a, a scan's first page. */
private val FIRST_AMUD = amudIndex("2a")

private sealed interface ScanLoad {
    data object Looking : ScanLoad

    data object None : ScanLoad

    data class Found(
        val file: File,
        val linked: Boolean,
    ) : ScanLoad
}

/**
 * The tractate [book]'s scan in the daf's place: its pages one under the other, each under its amud's name as the
 * daf's are ([tractateHe]), [pageWidth] wide in a column [contentWidth] wide. It opens at the amud at the top of the
 * daf's list ([listState], whose items are [order]'s amudim), follows it when the tab moves it (its table of contents,
 * a search), and moves it to the amud at its own top once a scroll of its own rests, so that the daf is there when the
 * scan is turned off. The reader's keys turn its pages ([control]). Without a scan, a word on where it is looked for and
 * a button that links a file to the tractate; with one, a line at the foot to change it. Only the tab in view holds the
 * file: every tab stays composed, and a scan's pages take memory as its file does.
 */
@Composable
internal fun DafScanView(
    book: Book,
    tractateHe: String,
    order: List<String>,
    listState: LazyListState,
    control: DafControl,
    pageWidth: Dp,
    contentWidth: Dp,
) {
    val scans = LocalAppGraph.current.dafScans
    val scope = rememberCoroutineScope()
    // Bumped by a link, to look for the scan again
    var links by remember { mutableIntStateOf(0) }
    val load by produceState<ScanLoad>(ScanLoad.Looking, book.title, links) {
        val file = runSuspendCatching { scans.of(book.title) }.getOrNull()
        value =
            file?.let { ScanLoad.Found(it, linked = runSuspendCatching { scans.linked(book.title) }.getOrNull() != null) } ?: ScanLoad.None
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
    Box(Modifier.width(contentWidth).fillMaxHeight()) {
        when (val state = load) {
            ScanLoad.Looking -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ScanLoad.None -> NoScan(book.title, onChoose = choose, modifier = Modifier.align(Alignment.Center))
            is ScanLoad.Found -> {
                if (LocalTabSelected.current) {
                    ScanPages(state.file, tractateHe, order, listState, control, pageWidth)
                }
                ScanLine(
                    linked = state.linked,
                    onChange = choose,
                    onUnlink = {
                        scope.launch {
                            runSuspendCatching { scans.unlink(book.title) }
                            links++
                        }
                    },
                    // At the other end from the daf's own line (DafUpdateLine)
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

/** The scan [file]'s pages, kept in step with the daf's list as [DafScanView] says. */
@Composable
private fun ScanPages(
    file: File,
    tractateHe: String,
    order: List<String>,
    listState: LazyListState,
    control: DafControl,
    pageWidth: Dp,
) {
    val reader = rememberPdfReaderState()
    var failure by remember(file) { mutableStateOf<String?>(null) }
    var opened by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        // The binding reads the whole file into memory; a missing native library is an Error, caught too
        runSuspendCatching { reader.open(withContext(Dispatchers.IO) { file.readBytes() }) }
            .onSuccess { opened = true }
            .onFailure { failure = it.message ?: it::class.simpleName }
    }
    val error = failure ?: reader.error?.message
    when {
        error != null ->
            Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.daf_scan_failed, error), Modifier.padding(24.dp))
            }
        !opened ->
            Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> ScanList(reader, tractateHe, order, listState, control, pageWidth)
    }
}

@Composable
private fun ScanList(
    reader: PdfReaderState,
    tractateHe: String,
    order: List<String>,
    listState: LazyListState,
    control: DafControl,
    pageWidth: Dp,
) {
    val pages = reader.pageCount
    // At the amud at the daf's top
    val start = remember(reader) { order.getOrNull(listState.firstVisibleItemIndex)?.let(::scanPageOf)?.coerceAtMost(pages - 1) ?: 0 }
    val scanState = rememberLazyListState(start.coerceAtLeast(0))
    LazyColumn(
        state = scanState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(pages) { page ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(SCAN_TITLE_HEIGHT), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        "$tractateHe ${amudTitle(amudOfScanPage(page))}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = JewelTheme.globalColors.text.info,
                    )
                }
                PdfPage(reader, page, Modifier.width(pageWidth).padding(bottom = 12.dp), selectableText = true)
            }
        }
    }
    // The tab moved the daf (its table of contents, a search): the scan follows
    LaunchedEffect(order, pages) {
        snapshotFlow { order.getOrNull(listState.firstVisibleItemIndex) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { amud ->
                val page = scanPageOf(amud)?.coerceAtMost(pages - 1) ?: return@collect
                if (amudOfScanPage(scanState.firstVisibleItemIndex) != amud) scanState.scrollToItem(page)
            }
    }
    // A scroll of the scan's own rested: the daf is moved to its amud, unseen, for the scan turned off to leave it there
    LaunchedEffect(order) {
        snapshotFlow { scanState.isScrollInProgress }
            .distinctUntilChanged()
            .drop(1)
            .filter { scrolling -> !scrolling }
            .collect {
                val index = order.indexOf(amudOfScanPage(scanState.firstVisibleItemIndex))
                if (index >= 0 && index != listState.firstVisibleItemIndex) listState.requestScrollToItem(index)
            }
    }
    // The reader's keys turn the scan's pages while it is shown
    SideEffect {
        control.page = { forward ->
            val height = scanState.layoutInfo.viewportSize.height
            scanState.animateScrollBy((if (forward) 1 else -1) * height * SCAN_PAGE_STEP)
        }
        control.edge = { end ->
            if (end) {
                scanState.scrollToItem((pages - 1).coerceAtLeast(0))
                scanState.scrollBy(SCAN_END_SCROLL)
            } else {
                scanState.scrollToItem(0)
            }
        }
    }
}

/** The height of an amud's name above its page, as the daf's. */
private val SCAN_TITLE_HEIGHT = 36.dp

/** A page up or down moves this much of the view, as the daf's. */
private const val SCAN_PAGE_STEP = 0.9f

/** Past the last page's end: the list stops scrolling there. */
private const val SCAN_END_SCROLL = 1_000_000f
