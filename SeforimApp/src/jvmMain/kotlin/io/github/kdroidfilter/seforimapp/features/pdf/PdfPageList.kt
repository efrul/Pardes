package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import dev.nucleusframework.pdfium.PdfPage
import dev.nucleusframework.pdfium.PdfReaderState
import dev.nucleusframework.pdfium.RenderQuality
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.math.roundToInt

// Pardes (requests 28 and 41 to 47 of docs/DESKTOP_STAGE2.md): the pages of a PDF one under the other, or two side by
// side, as a PDF book of the user's and a tractate's scan show them. Its speed is learnt from Otzaria and Zayita
// (requests 43 and 44): a page is drawn no larger than so many pixels and scaled up beyond them, and the pages after
// the ones in view are drawn ahead of time.

/** The widest a page is drawn at, in pixels: wider, it is scaled up, as Otzaria's 2,000 pixels on the long side. */
private const val MOST_DRAWN_PX = 1600

/** The rows after the first in view whose pages are drawn ahead, at the width they are shown at. */
private const val ROWS_AHEAD = 2

/** The space between two pages, one under the other or side by side. */
private val PAGE_GAP = 8.dp

/** The height of a page's title above it, as the daf's amudim have theirs. */
private val PAGE_TITLE_HEIGHT = 36.dp

/**
 * The rows of [pages] pages: a page each, or with [spreads] two side by side as a book read right to left opens, the
 * first page alone on the left, then each odd page on the right and the next on the left (in a tractate's scan, whose
 * first page is ב., amud b on the right and amud a on the left, as printed).
 */
internal fun rowCount(
    pages: Int,
    spreads: Boolean,
): Int = if (spreads && pages > 0) pages / 2 + 1 else pages

/** The row of [page] ([rowCount]). */
internal fun rowOf(
    page: Int,
    spreads: Boolean,
): Int = if (spreads) (page + 1) / 2 else page

/** The first page of [row] read ([rowCount]): its page on the right, or its only one. */
internal fun firstPageOf(
    row: Int,
    spreads: Boolean,
): Int = if (spreads) (row * 2 - 1).coerceAtLeast(0) else row

/** The width of each of two pages side by side in [room]. */
internal fun spreadPageWidth(room: Dp): Dp = (room - PAGE_GAP) / 2

/**
 * [reader]'s pages in rows ([rowCount], the items of [listState]), each page [pageWidth] wide, centered in a list as
 * wide as the view (wider rows scroll sideways), each under its [title] when one is given; their text selectable when
 * [selectableText]. A page wider than [MOST_DRAWN_PX] pixels is drawn at that width and scaled up, and the pages of the
 * [ROWS_AHEAD] rows after the first in view are drawn ahead of time, at their width, so that a page scrolled to is
 * mostly drawn already.
 */
@Composable
internal fun PdfPageList(
    reader: PdfReaderState,
    listState: LazyListState,
    pageWidth: Dp,
    modifier: Modifier = Modifier,
    spreads: Boolean = false,
    selectableText: Boolean = true,
    title: ((page: Int) -> String)? = null,
) {
    val pages = reader.pageCount
    val rows = rowCount(pages, spreads)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val drawnPx = with(density) { pageWidth.roundToPx() }.coerceAtMost(MOST_DRAWN_PX)
    LaunchedEffect(reader, drawnPx, spreads) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { first ->
                for (row in first + 1..minOf(first + ROWS_AHEAD, rows - 1)) {
                    pagesOf(row, pages, spreads).forEach { page -> reader.prefetch(page, drawnPx, RenderQuality.FULL) }
                }
            }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Narrower than the view, the pages are centered in a list as wide as it, which the wheel scrolls anywhere
        val rowWidth = if (spreads) pageWidth * 2 + PAGE_GAP else pageWidth
        val listWidth = max(maxWidth, rowWidth)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(listWidth).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = PAGE_GAP),
                verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(rows) { row ->
                    if (!spreads) {
                        TitledPage(reader, row, pageWidth, selectableText, title)
                    } else {
                        // The right page first in a view laid out from the right, the left one first otherwise
                        Row(horizontalArrangement = Arrangement.spacedBy(PAGE_GAP)) {
                            val (right, left) = row * 2 - 1 to row * 2
                            TitledPage(reader, if (rtl) right else left, pageWidth, selectableText, title)
                            TitledPage(reader, if (rtl) left else right, pageWidth, selectableText, title)
                        }
                    }
                }
            }
        }
    }
}

/** The pages of [row] ([rowCount]). */
private fun pagesOf(
    row: Int,
    pages: Int,
    spreads: Boolean,
): List<Int> = if (spreads) listOf(row * 2 - 1, row * 2).filter { it in 0 until pages } else listOf(row)

/** The page [page], [width] wide, under its [title] when one is given; its room, empty, for a page the PDF has not. */
@Composable
private fun TitledPage(
    reader: PdfReaderState,
    page: Int,
    width: Dp,
    selectableText: Boolean,
    title: ((page: Int) -> String)?,
) {
    if (page !in 0 until reader.pageCount) {
        Spacer(Modifier.width(width))
        return
    }
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        if (title != null) {
            Box(Modifier.fillMaxWidth().height(PAGE_TITLE_HEIGHT), contentAlignment = Alignment.BottomCenter) {
                Text(
                    title(page),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = JewelTheme.globalColors.text.info,
                )
            }
        }
        PdfPage(reader, page, Modifier.width(width).drawnAtMost(MOST_DRAWN_PX), selectableText = selectableText)
    }
}

/**
 * Lays its content out [px] pixels wide at most and scales it up to the width given: a page wider than that is drawn
 * at [px] and shown at its width, its taps scaled with it.
 */
internal fun Modifier.drawnAtMost(px: Int): Modifier =
    layout { measurable, constraints ->
        val width = constraints.maxWidth
        if (width == Constraints.Infinity || width <= px) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val scale = width.toFloat() / px
            val placeable = measurable.measure(Constraints.fixedWidth(px))
            layout(width, (placeable.height * scale).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }

/**
 * A key over the [rows] of pages: Page Up and Down move most of a view, Ctrl + Home and End go to the first and last
 * row.
 */
internal fun pageListKey(
    event: KeyEvent,
    listState: LazyListState,
    @StructuredScope scope: CoroutineScope,
    rows: Int,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    when {
        event.key == Key.PageDown -> scope.launch { listState.turnPage(forward = true) }
        event.key == Key.PageUp -> scope.launch { listState.turnPage(forward = false) }
        ctrl && event.key == Key.MoveHome -> scope.launch { listState.scrollToItem(0) }
        ctrl && event.key == Key.MoveEnd -> scope.launch { listState.scrollToItem((rows - 1).coerceAtLeast(0)) }
        else -> return false
    }
    return true
}

/** Scrolls most of a view down ([forward]) or up, as the text's page keys. */
internal suspend fun LazyListState.turnPage(forward: Boolean) {
    animateScrollBy((if (forward) 1 else -1) * layoutInfo.viewportSize.height * PAGE_STEP)
}

/** A page key moves this much of the view, as the text's. */
private const val PAGE_STEP = 0.9f
