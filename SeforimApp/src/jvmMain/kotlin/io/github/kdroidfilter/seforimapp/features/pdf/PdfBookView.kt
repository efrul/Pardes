package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.nucleusframework.pdfium.PdfReaderState
import dev.nucleusframework.pdfium.rememberPdfReaderState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.texteffects.rememberTextZoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_book_failed
import seforimapp.seforimapp.generated.resources.pdf_book_go_to_page
import seforimapp.seforimapp.generated.resources.pdf_book_missing
import seforimapp.seforimapp.generated.resources.pdf_book_page_of
import seforimapp.seforimapp.generated.resources.pdf_book_zoom_in
import seforimapp.seforimapp.generated.resources.pdf_book_zoom_out
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

// Pardes (requests 28, 45 and 47 of docs/DESKTOP_STAGE2.md): a PDF book of the user's, for reading, drawn by Nucleus's
// PDFium binding as the experiment found it works in Pardes's window and in the Windows build (PdfReaderTool.kt).

/**
 * The PDF book [file], its pages one under the other, or two side by side as a book open (the end bar's button), as wide
 * as the view at a zoom of 1 (wider past it, scrolled sideways), their text selectable. Its zoom is the daf's, which the
 * tab [tabId] zooms as it would its daf: the end bar's buttons and the zoom keys, Ctrl + wheel and a pinch. Its pages
 * are told to the tab's contents pane, which lists them ([PdfBookToc]), and it goes to the page clicked there. A bar
 * says the page in view, goes to a page typed and zooms too when [inTab] (in a tab of its own, which has no end bar nor
 * contents pane), and [actions] end it. It opens at [place], and tells its place to [onPlace] once it rests there, and
 * as it leaves.
 */
@Composable
internal fun PdfBookView(
    file: File,
    place: PdfPlace,
    onPlace: (PdfPlace) -> Unit,
    tabId: String,
    modifier: Modifier = Modifier,
    inTab: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val appSettings = LocalAppGraph.current.appSettings
    val store = LocalAppGraph.current.pdfBookStore
    val reader = rememberPdfReaderState()
    var failure by remember(file) { mutableStateOf<String?>(null) }
    var opened by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        if (!file.isFile) return@LaunchedEffect
        // The binding reads the whole file into memory; a missing native library is an Error, caught too
        runSuspendCatching { reader.open(withContext(Dispatchers.IO) { file.readBytes() }) }
            .onSuccess { opened = true }
            .onFailure { failure = it.message ?: it::class.simpleName }
    }
    DisposableEffect(tabId) {
        appSettings.dafShown(tabId)
        onDispose { appSettings.dafHidden(tabId) }
    }
    val zoom by appSettings.dafZoomFlow.collectAsState()
    val spreads by appSettings.pageSpreadsFlow.collectAsState()
    // At the page read at the top, kept as it scrolls: the rows changed (a page each, or two), a new list opens there
    val top = remember(file) { Top(place.page, place.offset) }
    val listState = remember(file, spreads) { LazyListState(rowOf(top.page, spreads), top.offset).also { top.offset = 0 } }
    val pages = if (opened) reader.pageCount else 0
    TellPlace(
        listState,
        spreads,
        { zoom },
        onTop = { page ->
            top.page = page
            if (pages > 0) store.showPages(tabId, PdfPagesShown(pages, page))
        },
        onPlace = onPlace,
    )
    // The contents pane lists the pages once they are known, and sends the page clicked there
    DisposableEffect(tabId) { onDispose { store.showPages(tabId, null) } }
    LaunchedEffect(tabId, pages) { if (pages > 0) store.showPages(tabId, PdfPagesShown(pages, top.page.coerceIn(0, pages - 1))) }
    LaunchedEffect(tabId, listState, pages) {
        if (pages == 0) return@LaunchedEffect
        store.pageRequests
            .filter { (tab, _) -> tab == tabId }
            .collect { (_, page) -> listState.scrollToItem(rowOf(page.coerceIn(0, pages - 1), spreads)) }
    }
    val error = failure ?: reader.error?.message
    Column(modifier.fillMaxSize()) {
        PdfBar(
            pages = pages,
            listState = listState,
            spreads = spreads,
            goTo = inTab,
            zoom = zoom.takeIf { inTab },
            onZoom = appSettings::zoomDaf,
            actions = actions,
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                !file.isFile -> Text(stringResource(Res.string.pdf_book_missing, file.path), Modifier.padding(16.dp))
                error != null -> Text(stringResource(Res.string.pdf_book_failed, error), Modifier.padding(16.dp))
                !opened -> CircularProgressIndicator()
                else -> PdfPages(reader, listState, spreads, zoom, onZoom = appSettings::zoomDaf)
            }
        }
    }
}

/**
 * The pages one under the other, or two side by side with [spreads], [zoom] times the view's width (two, half of it
 * each), centered and scrolled sideways past it. The daf's gestures zoom them, and the keys turn them as the text's:
 * Page Up and Down, Ctrl + Home and End; the zoom keys are the window's, which zoom the daf of the tab.
 */
@Composable
private fun PdfPages(
    reader: PdfReaderState,
    listState: LazyListState,
    spreads: Boolean,
    zoom: Float,
    onZoom: (Float) -> Unit,
) {
    val keys = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val currentOnZoom by rememberUpdatedState(onZoom)
    val pinch =
        rememberTextZoom(
            textSize = zoom * PERCENT,
            minTextSize = AppSettings.MIN_DAF_ZOOM * PERCENT,
            maxTextSize = AppSettings.MAX_DAF_ZOOM * PERCENT,
            onTextSize = { currentOnZoom(it / PERCENT) },
            animate = LocalTabSelected.current,
        )
    val rows = rowCount(reader.pageCount, spreads)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(pinch.modifier)
            .focusRequester(keys)
            .onPreviewKeyEvent { event -> pageListKey(event, listState, scope, rows) }
            .focusable(),
    ) {
        val base = if (spreads) spreadPageWidth(maxWidth) else maxWidth
        PdfPageList(reader, listState, pageWidth = base * (pinch.textSize / PERCENT), spreads = spreads)
    }
    LaunchedEffect(Unit) { keys.requestFocus() }
}

/** The next of the daf's zoom levels from [zoom]: out when [closer] is false, in when it is true. */
internal fun zoomStep(
    zoom: Float,
    closer: Boolean,
): Float =
    if (closer) {
        AppSettings.DAF_ZOOM_LEVELS.lastOrNull { it < zoom - ZOOM_SLACK } ?: AppSettings.MIN_DAF_ZOOM
    } else {
        AppSettings.DAF_ZOOM_LEVELS.firstOrNull { it > zoom + ZOOM_SLACK } ?: AppSettings.MAX_DAF_ZOOM
    }

/**
 * The bar over the pages: the page in view of [pages], a field to go to a page with [goTo], the zoom's buttons when
 * there is a [zoom] to show, and [actions].
 */
@Composable
private fun PdfBar(
    pages: Int,
    listState: LazyListState,
    spreads: Boolean,
    goTo: Boolean,
    zoom: Float?,
    onZoom: (Float) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val target = rememberTextFieldState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pages > 0) {
            Text(
                stringResource(Res.string.pdf_book_page_of, firstPageOf(listState.firstVisibleItemIndex, spreads) + 1, pages),
                color = JewelTheme.globalColors.text.info,
            )
            if (goTo) {
                TextField(
                    state = target,
                    modifier =
                        Modifier.width(GO_TO_WIDTH).onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) return@onPreviewKeyEvent false
                            val page =
                                target.text
                                    .toString()
                                    .trim()
                                    .toIntOrNull() ?: return@onPreviewKeyEvent true
                            // Gone to, the field is empty again: the page in view is said beside it
                            target.clearText()
                            scope.launch { listState.scrollToItem(rowOf((page - 1).coerceIn(0, pages - 1), spreads)) }
                            true
                        },
                    placeholder = { Text(stringResource(Res.string.pdf_book_go_to_page)) },
                )
            }
        }
        Box(Modifier.weight(1f))
        if (zoom != null) {
            IconButton(onClick = { onZoom(zoomStep(zoom, closer = true)) }, enabled = zoom > AppSettings.MIN_DAF_ZOOM) {
                Icon(AllIconsKeys.General.ZoomOut, contentDescription = stringResource(Res.string.pdf_book_zoom_out))
            }
            IconButton(onClick = { onZoom(zoomStep(zoom, closer = false)) }, enabled = zoom < AppSettings.MAX_DAF_ZOOM) {
                Icon(AllIconsKeys.General.ZoomIn, contentDescription = stringResource(Res.string.pdf_book_zoom_in))
            }
        }
        actions()
    }
}

/**
 * The place told to [onPlace] once the pages rest there ([PLACE_DEBOUNCE]) after a move, with the [zoom] then, and as
 * the view leaves, when it moved since: the first page read in the row at the top ([spreads]), told to [onTop] at once.
 */
@OptIn(FlowPreview::class)
@Composable
private fun TellPlace(
    listState: LazyListState,
    spreads: Boolean,
    zoom: () -> Float,
    onTop: (page: Int) -> Unit,
    onPlace: (PdfPlace) -> Unit,
) {
    val currentOnPlace by rememberUpdatedState(onPlace)
    val currentOnTop by rememberUpdatedState(onTop)
    val currentZoom by rememberUpdatedState(zoom)
    val told = remember { Told() }

    fun placeNow() = PdfPlace(firstPageOf(listState.firstVisibleItemIndex, spreads), listState.firstVisibleItemScrollOffset, currentZoom())

    fun tell(place: PdfPlace) {
        told.place = place
        currentOnPlace(place)
    }
    LaunchedEffect(listState) {
        told.place = placeNow()
        snapshotFlow { placeNow() }
            .distinctUntilChanged()
            .onEach { currentOnTop(it.page) }
            .drop(1)
            .debounce(PLACE_DEBOUNCE)
            .collect { tell(it) }
    }
    DisposableEffect(listState) {
        onDispose { placeNow().takeIf { it != told.place }?.let(::tell) }
    }
}

// The place last told, so that leaving tells a newer one only
private class Told(
    var place: PdfPlace? = null,
)

// The page read at the top, and the pixels the first list opens above it (a new list for other rows opens at its top)
private class Top(
    var page: Int,
    var offset: Int,
)

private const val PERCENT = 100f

// A zoom a pinch left this close to a level is at that level, as the daf's
private const val ZOOM_SLACK = 0.005f

private val GO_TO_WIDTH = 72.dp
private val PLACE_DEBOUNCE = 400.milliseconds
