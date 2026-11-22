package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.PageFit
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.page_fit
import seforimapp.seforimapp.generated.resources.page_fit_tooltip

// Pardes (request 63 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md): "בהצגת צורת הדף - צריך
// שגודל החלון יתאים לגודל הדף המוצג, אין טעם ברווח גדול בשוליים של החלון": the pages as wide as the view at the zoom's
// 1, with no widest, and a whole page in the view when asked, as a PDF reader fits a page.

/**
 * The width of a page at the zoom's 1 in a view [room] wide (a page's share of it) and [height] tall: [room], and with
 * [whole] no wider than leaves a page of [aspect] (its height over its width) whole in the view, the room [around] it
 * (its title, the gaps) left aside. [room] when the page's [aspect] is not known yet.
 */
internal fun fittedPageWidth(
    room: Float,
    height: Float,
    aspect: Float?,
    whole: Boolean,
    around: Float = 0f,
): Float = if (!whole || aspect == null || aspect <= 0f) room else minOf(room, (height - around).coerceAtLeast(1f) / aspect)

/** Whether a whole page is fitted in the view ([PageFitSettings.whole]), as a state. */
@Composable
internal fun rememberWholePage(): Boolean {
    val whole by LocalAppGraph.current.appSettings
        .flow(PageFitSettings.whole)
        .collectAsState()
    return whole
}

/**
 * The end bar's button under two pages', while the tab of [uiState] shows pages ([showsPages]): a whole page in the
 * view, in every tab.
 */
@Composable
fun PageFitButton(
    uiState: BookContentState,
    modifier: Modifier = Modifier,
) {
    if (!showsPages(uiState)) return
    val appSettings = LocalAppGraph.current.appSettings
    val whole = rememberWholePage()
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.page_fit_tooltip),
        onClick = { appSettings[PageFitSettings.whole] = !whole },
        isSelected = whole,
        icon = PageFit,
        label = stringResource(Res.string.page_fit),
        modifier = modifier,
        iconDescription = stringResource(Res.string.page_fit),
    )
}
