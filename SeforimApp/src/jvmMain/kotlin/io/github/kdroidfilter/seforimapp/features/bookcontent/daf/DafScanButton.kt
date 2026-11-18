package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.ScanPage
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan
import seforimapp.seforimapp.generated.resources.daf_scan_tooltip

/**
 * Pardes (requests 41, 46 and 56 of docs/DESKTOP_STAGE2.md): the end bar's button of a Talmud tractate, under the daf's
 * own: the tractate's scan ([DafScanReader]) in place of its text or its daf, while [selected]; [onClick] turns it.
 * While the text or the daf of the tractate [book] is shown, the scan's page of the line read ([lineId]) and the next
 * are decoded ahead ([DafScanPages.prepare]), so that the scan shows them in the next frame once cut.
 */
@Composable
fun DafScanButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    book: Book? = null,
    lineId: Long? = null,
) {
    if (!selected && book != null) ScanPageAhead(book, lineId)
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.daf_scan_tooltip),
        onClick = onClick,
        isSelected = selected,
        icon = ScanPage,
        label = stringResource(Res.string.daf_scan),
        modifier = modifier,
        iconDescription = stringResource(Res.string.daf_scan),
    )
}

// The scan's page of the line read made ready, once the reader rests on it: a line scrolled past costs nothing
@Composable
private fun ScanPageAhead(
    book: Book,
    lineId: Long?,
) {
    val graph = LocalAppGraph.current
    val folder by graph.dafScans.folder
        .collectAsState()
    val scan by produceState<ScanSource?>(null, book.title, folder) {
        value = runSuspendCatching { graph.dafScans.of(book.title) }.getOrNull()
    }
    LaunchedEffect(scan, lineId) {
        val source = scan ?: return@LaunchedEffect
        delay(AHEAD_AFTER_MS)
        val page = lineId?.takeIf { it > 0 }?.let { amudOfLine(graph.repository, it) }?.let(::scanPageOf) ?: 0
        // The file's pages: the tractate's daf ב. need not be its first (request 52 of docs/DESKTOP_STAGE3_PLAN.md)
        graph.dafScanPages.prepare(source.file, listOf(page, page + 1).map { source.first + it })
    }
}

// How long the line read rests before its page is made ready
private const val AHEAD_AFTER_MS = 300L
