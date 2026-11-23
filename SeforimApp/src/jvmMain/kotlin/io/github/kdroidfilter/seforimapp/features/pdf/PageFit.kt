package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.PageFit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.page_fit
import seforimapp.seforimapp.generated.resources.page_fit_tooltip
import seforimapp.seforimapp.generated.resources.page_fit_width_tooltip
import kotlin.math.abs

// Pardes (request 63 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md: "צריך שגודל החלון יתאים
// לגודל הדף המוצג"; request 73 of the list after the public release: "כאשר סרגל צד נפתח למשל או מוסיפים מפרשים היחס של
// הדף משתנה... גם הגדרת רוחב וגובה נקבעת כיום לפי יחס המצב הנוכחי ולא אבסלוטית"): the pages' zoom is a size, as the
// text's is; the view's width and a whole page are zooms it is set to, once, as a PDF reader fits a page. The pages
// shown first fit their view's width. A pane opened beside them narrows them only as much as it must, and closed gives
// them their size back, unless they were zoomed wider than their view (request 79: "בפתיחת דברים בצד הגודל כן יקטן אבל
// רק כמה שצריך וידע להתאים את עצמו").

/** The width of a row of pages (a page, or two side by side) at the zoom's 1: the zoom is a size, not a share of the view. */
internal val ZOOM_ONE_WIDTH = 1000.dp

/** The width of each of [columns] pages a row at the daf's [zoom], in [one]'s unit ([ZOOM_ONE_WIDTH], dp by default). */
internal fun pageWidthAt(
    zoom: Float,
    columns: Int,
    one: Float = ZOOM_ONE_WIDTH.value,
): Float = zoom * one / columns

/** The zooms that fit pages in their view: as wide as it ([width]), and a whole page in it ([whole], no wider). */
@Immutable
internal data class PageFit(
    val width: Float,
    val whole: Float,
)

/**
 * The [PageFit] of [columns] pages a row, each [share] wide at the view's width (its share of it), in a view [height]
 * tall: a page of [aspect] (its height over its width) whole in it, the room [around] it (its title, the gaps) left
 * aside, and no wider than the view; as wide as the view while the page's [aspect] is not known. In [one]'s unit.
 */
internal fun pageFit(
    share: Float,
    height: Float,
    aspect: Float?,
    columns: Int,
    around: Float = 0f,
    one: Float = ZOOM_ONE_WIDTH.value,
): PageFit {
    val width = share * columns / one
    if (aspect == null || aspect <= 0f) return PageFit(width, width)
    return PageFit(width, minOf(width, (height - around).coerceAtLeast(1f) / aspect * columns / one))
}

/** The fit of each tab's pages in their view, by the tab's id, as their view lays them out: a daf, a scan, a PDF book. */
@Inject
@SingleIn(AppScope::class)
class PageFits {
    private val shown = MutableStateFlow<Map<String, PageFit>>(emptyMap())

    internal val fits: StateFlow<Map<String, PageFit>> = shown.asStateFlow()

    /** The pages of [tabId] fit their view so, or are no longer shown ([fit] null). */
    internal fun show(
        tabId: String,
        fit: PageFit?,
    ) {
        shown.update { fits ->
            when {
                fit == null -> fits - tabId
                fits[tabId] == fit -> fits
                else -> fits + (tabId to fit)
            }
        }
    }
}

/**
 * The pages of [tabId] fit their view so ([fit]) while they are shown, for the end bar's button and for the zoom they
 * are shown at ([shownPageZoom]); the zoom never set yet, it is set to their view's width.
 */
@Composable
internal fun TellPageFit(
    tabId: String,
    fit: PageFit,
) {
    val graph = LocalAppGraph.current
    SideEffect {
        graph.pageFits.show(tabId, fit)
        graph.appSettings.showPageWidth(tabId, fit.width)
    }
    DisposableEffect(tabId) {
        onDispose {
            graph.pageFits.show(tabId, null)
            graph.appSettings.showPageWidth(tabId, null)
        }
    }
    LaunchedEffect(Unit) { if (!graph.appSettings.hasDafZoom()) graph.appSettings.setDafZoom(fit.width) }
}

/**
 * The zoom the pages of [tabId] are shown at: the daf's, no wider than their view unless it was set wider
 * ([AppSettings.shownDafZoom]), from the width their view tells ([TellPageFit]); the daf's while it tells none.
 */
@Composable
internal fun shownPageZoom(tabId: String): Float {
    val settings = LocalAppGraph.current.appSettings
    val zoom by settings.dafZoomFlow.collectAsState()
    val wide by settings.dafZoomWideFlow.collectAsState()
    val widths by settings.pageWidthsFlow.collectAsState()
    return AppSettings.shownDafZoom(zoom, wide, widths[tabId])
}

/**
 * The end bar's button under two pages', while the tab of [uiState] shows pages ([showsPages]): a whole page in the
 * view, and pressed again, pages as wide as the view, as a PDF reader's fit; on while a whole page is in view.
 */
@Composable
fun PageFitButton(
    uiState: BookContentState,
    modifier: Modifier = Modifier,
) {
    if (!showsPages(uiState)) return
    val graph = LocalAppGraph.current
    val fits by graph.pageFits.fits.collectAsState()
    val zoom = shownPageZoom(uiState.tabId)
    val fit = fits[uiState.tabId]
    val whole = fit != null && isWhole(zoom, fit)
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(if (whole) Res.string.page_fit_width_tooltip else Res.string.page_fit_tooltip),
        onClick = { fit?.let { graph.appSettings.setDafZoom(if (whole) it.width else it.whole) } },
        isSelected = whole,
        icon = PageFit,
        label = stringResource(Res.string.page_fit),
        modifier = modifier,
        enabled = fit != null,
        iconDescription = stringResource(Res.string.page_fit),
    )
}

/** Whether pages at [zoom] are a whole page in their view ([fit]), as near as the zoom's ends let them be. */
internal fun isWhole(
    zoom: Float,
    fit: PageFit,
): Boolean = abs(zoom - fit.whole.coerceIn(AppSettings.MIN_DAF_ZOOM, AppSettings.MAX_DAF_ZOOM)) < FIT_SLACK

// A zoom this close to a fit is at it
private const val FIT_SLACK = 0.005f
