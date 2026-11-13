package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.nucleusframework.pdfium.PdfPage
import dev.nucleusframework.pdfium.PdfReaderState
import dev.nucleusframework.pdfium.rememberPdfReaderState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.texteffects.rememberTextZoom
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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

// Pardes (request 28 of docs/DESKTOP_STAGE2.md): a PDF book of the user's in a tab, its first version, for reading,
// drawn by Nucleus's PDFium binding as the experiment found it works in Pardes's window and in the Windows build
// (PdfReaderTool.kt).

/**
 * The PDF book [file], its pages one under the other, as wide as the view at a zoom of 1 (wider past it, scrolled
 * sideways), their text selectable. A bar says the page in view, goes to a page typed, and zooms, as the daf's zoom: its
 * levels, Ctrl + wheel, a pinch, and the zoom keys while the pages have the focus; [actions] end it. It opens at [place],
 * and tells its place to [onPlace] once it rests there, and as it leaves.
 */
@Composable
internal fun PdfBookView(
    file: File,
    place: PdfPlace,
    onPlace: (PdfPlace) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
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
    val listState = rememberLazyListState(place.page, place.offset)
    var zoom by remember(file) { mutableFloatStateOf(place.zoom.coerceIn(AppSettings.MIN_DAF_ZOOM, AppSettings.MAX_DAF_ZOOM)) }
    TellPlace(listState, { zoom }, onPlace)
    val error = failure ?: reader.error?.message
    Column(modifier.fillMaxSize()) {
        PdfBar(
            pages = if (opened) reader.pageCount else 0,
            listState = listState,
            zoom = zoom,
            onZoom = { zoom = it },
            actions = actions,
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                !file.isFile -> Text(stringResource(Res.string.pdf_book_missing, file.path), Modifier.padding(16.dp))
                error != null -> Text(stringResource(Res.string.pdf_book_failed, error), Modifier.padding(16.dp))
                !opened -> CircularProgressIndicator()
                else -> PdfPages(reader, listState, zoom, onZoom = { zoom = it })
            }
        }
    }
}

/**
 * The pages one under the other, [zoom] times the view's width, centered and scrolled sideways past it. The daf's
 * gestures zoom them, and the keys turn them as the text's: Page Up and Down, Ctrl + Home and End.
 */
@Composable
private fun PdfPages(
    reader: PdfReaderState,
    listState: LazyListState,
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
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(pinch.modifier)
            .focusRequester(keys)
            .onPreviewKeyEvent { event -> pageKey(event, listState, scope, reader.pageCount, zoom, currentOnZoom) }
            .focusable(),
    ) {
        val pagesWidth = maxWidth * (pinch.textSize / PERCENT)
        // Narrower than the view, the pages are centered in a list as wide as it, which the wheel scrolls anywhere
        val listWidth = max(maxWidth, pagesWidth)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(listWidth).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(reader.pageCount) { index ->
                    PdfPage(reader, index, Modifier.width(pagesWidth), selectableText = true)
                }
            }
        }
    }
    LaunchedEffect(Unit) { keys.requestFocus() }
}

/** A key over the pages: Page Up and Down move a view, Ctrl + Home and End go to the first and last page, Ctrl + = and - zoom. */
private fun pageKey(
    event: KeyEvent,
    listState: LazyListState,
    @StructuredScope scope: CoroutineScope,
    pages: Int,
    zoom: Float,
    onZoom: (Float) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    val height = listState.layoutInfo.viewportSize.height * PAGE_STEP
    when {
        event.key == Key.PageDown -> scope.launch { listState.animateScrollBy(height) }
        event.key == Key.PageUp -> scope.launch { listState.animateScrollBy(-height) }
        ctrl && event.key == Key.MoveHome -> scope.launch { listState.scrollToItem(0) }
        ctrl && event.key == Key.MoveEnd -> scope.launch { listState.scrollToItem((pages - 1).coerceAtLeast(0)) }
        ctrl && event.key in ZOOM_IN_KEYS -> onZoom(zoomStep(zoom, closer = false))
        ctrl && event.key in ZOOM_OUT_KEYS -> onZoom(zoomStep(zoom, closer = true))
        else -> return false
    }
    return true
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

/** The bar over the pages: the page in view of [pages], a field to go to a page, the zoom's buttons, and [actions]. */
@Composable
private fun PdfBar(
    pages: Int,
    listState: LazyListState,
    zoom: Float,
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
                stringResource(Res.string.pdf_book_page_of, listState.firstVisibleItemIndex + 1, pages),
                color = JewelTheme.globalColors.text.info,
            )
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
                        scope.launch { listState.scrollToItem((page - 1).coerceIn(0, pages - 1)) }
                        true
                    },
                placeholder = { Text(stringResource(Res.string.pdf_book_go_to_page)) },
            )
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = { onZoom(zoomStep(zoom, closer = true)) }, enabled = zoom > AppSettings.MIN_DAF_ZOOM) {
            Icon(AllIconsKeys.General.ZoomOut, contentDescription = stringResource(Res.string.pdf_book_zoom_out))
        }
        IconButton(onClick = { onZoom(zoomStep(zoom, closer = false)) }, enabled = zoom < AppSettings.MAX_DAF_ZOOM) {
            Icon(AllIconsKeys.General.ZoomIn, contentDescription = stringResource(Res.string.pdf_book_zoom_in))
        }
        actions()
    }
}

/**
 * The place told to [onPlace] once the pages rest there ([PLACE_DEBOUNCE]) after a move, with the [zoom] then, and as
 * the view leaves, when it moved since.
 */
@OptIn(FlowPreview::class)
@Composable
private fun TellPlace(
    listState: LazyListState,
    zoom: () -> Float,
    onPlace: (PdfPlace) -> Unit,
) {
    val currentOnPlace by rememberUpdatedState(onPlace)
    val currentZoom by rememberUpdatedState(zoom)
    val told = remember { Told() }

    fun placeNow() = PdfPlace(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, currentZoom())

    fun tell(place: PdfPlace) {
        told.place = place
        currentOnPlace(place)
    }
    LaunchedEffect(listState) {
        told.place = placeNow()
        snapshotFlow { placeNow() }
            .distinctUntilChanged()
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

private const val PERCENT = 100f

// A zoom a pinch left this close to a level is at that level, as the daf's
private const val ZOOM_SLACK = 0.005f

// A page key moves this much of the view
private const val PAGE_STEP = 0.9f

private val GO_TO_WIDTH = 72.dp
private val PLACE_DEBOUNCE = 400.milliseconds
private val ZOOM_IN_KEYS = setOf(Key.Equals, Key.Plus, Key.NumPadAdd)
private val ZOOM_OUT_KEYS = setOf(Key.Minus, Key.NumPadSubtract)
