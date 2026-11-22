package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import io.github.kdroidfilter.seforimapp.features.pdf.PageRows
import io.github.kdroidfilter.seforimapp.features.pdf.ROWS_AHEAD
import io.github.kdroidfilter.seforimapp.features.pdf.pagesOf
import io.github.kdroidfilter.seforimapp.features.pdf.rememberPageColors
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import java.io.File

// Pardes (request 56 of docs/DESKTOP_STAGE2.md): a tractate's scan drawn by Pardes from its pages cut into images, with
// no wait and no preview, in the next frame once an image is in memory (docs/scan-speed).

/**
 * The pages of [scan] as cut ([cut], [DafScanPages]), in the rows of a PDF's pages ([PageRows]): one under the other
 * or two side by side, each [pageWidth] wide under its [title]. Each image is decoded off the UI thread, and those of
 * the [ROWS_AHEAD] rows after the first in view ahead of time; a page not cut yet is cut before the others, and shown
 * once it is. The tractate's pages are [count] from the file's page [first] (request 52 of docs/DESKTOP_STAGE3_PLAN.md:
 * a file with pages before its daf ב., or of several tractates); the rows and titles count from it.
 */
@Composable
internal fun ScanPageList(
    scan: File,
    cut: ScanCut,
    listState: LazyListState,
    pageWidth: Dp,
    modifier: Modifier = Modifier,
    spreads: Boolean = false,
    title: ((page: Int) -> String)? = null,
    first: Int = 0,
    count: Int = cut.pages,
) {
    val pages = LocalAppGraph.current.dafScanPages
    LaunchedEffect(scan, spreads, first, count) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            // The rows ahead of the latest place only: a scroll that flies on leaves the ones it passed (request 48)
            .collectLatest { top ->
                pages.prepare(scan, (top + 1..top + ROWS_AHEAD).flatMap { pagesOf(it, count, spreads) }.map { first + it })
            }
    }
    PageRows(count, listState, pageWidth, modifier, spreads, title) { page, width -> ScanPage(scan, cut, first + page, width) }
}

// A page as an image, smoothed as it is drawn smaller than cut; the paper, and a wait, while it is cut
@Composable
private fun ScanPage(
    scan: File,
    cut: ScanCut,
    page: Int,
    width: Dp,
) {
    val pages = LocalAppGraph.current.dafScanPages
    val image by produceState(pages.shownImage(scan, page), scan, page) {
        if (value == null) value = pages.revision.mapNotNull { pages.image(scan, page) }.first()
    }
    // In the dark theme, its text on its panel, the paper too (request 42 of docs/DESKTOP_STAGE3_PLAN.md)
    val colors = rememberPageColors()
    val paper = if (colors == null) Color.White else JewelTheme.globalColors.panelBackground
    Box(Modifier.width(width).aspectRatio(cut.width.toFloat() / cut.heightOf(page)).background(paper)) {
        val shown = image
        if (shown == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Image(
                bitmap = shown,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Medium,
                colorFilter = colors,
            )
        }
    }
}
