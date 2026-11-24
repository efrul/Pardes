package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.isBavli
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.rememberDafTractate
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.showsBookDaf
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.PageSpread
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.page_spreads
import seforimapp.seforimapp.generated.resources.page_spreads_tooltip

/**
 * Pardes (request 47 of docs/DESKTOP_STAGE2.md): the end bar's button under the zoom's, while the tab of [uiState] shows
 * pages (a tractate's daf or scan, a PDF book of the user's): two pages side by side, as a book open, in every tab.
 */
@Composable
fun PageSpreadsButton(
    uiState: BookContentState,
    modifier: Modifier = Modifier,
) {
    if (!showsPages(uiState)) return
    val appSettings = LocalAppGraph.current.appSettings
    val spreads by appSettings.pageSpreadsFlow.collectAsState()
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.page_spreads_tooltip),
        onClick = { appSettings.setPageSpreads(!spreads) },
        isSelected = spreads,
        icon = PageSpread,
        label = stringResource(Res.string.page_spreads),
        modifier = modifier,
        iconDescription = stringResource(Res.string.page_spreads),
    )
}

/**
 * Whether the tab of [uiState] shows pages rather than lines: printed ones ([showsPrintedPages]), or another book's own
 * (BookDafView), which the end bar's buttons set side by side and fit in the view as they do the others.
 */
@Composable
fun showsPages(uiState: BookContentState): Boolean = showsPrintedPages(uiState) || showsBookDaf(uiState)

/**
 * Whether the tab of [uiState] shows pages as printed: a tractate as its daf or its scan, a commentary printed on a
 * tractate's daf as that daf (request 82), or a PDF book. The end bar's diacritics have nothing to do on them (request
 * 49 of docs/DESKTOP_STAGE3_PLAN.md).
 */
@Composable
fun showsPrintedPages(uiState: BookContentState): Boolean {
    val book = uiState.navigation.selectedBook
    val dafTractate = rememberDafTractate(book)
    if (book == null) return false
    val tractatePages = book.isBavli && (uiState.content.dafView || uiState.content.scanView)
    val commentaryPages = dafTractate != null && uiState.content.dafView
    return tractatePages || commentaryPages || pdfIdOf(book.id) != null
}
