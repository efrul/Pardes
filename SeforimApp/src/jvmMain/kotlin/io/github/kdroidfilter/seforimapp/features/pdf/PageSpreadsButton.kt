package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.isBavli
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
    val book = uiState.navigation.selectedBook ?: return
    // A tractate as its daf or its scan, or a PDF book
    val tractatePages = book.isBavli && (uiState.content.dafView || uiState.content.scanView)
    if (!tractatePages && pdfIdOf(book.id) == null) return
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
