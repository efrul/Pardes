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
import io.github.kdroidfilter.seforimapp.icons.PageFitTwoThirds
import io.github.kdroidfilter.seforimapp.icons.PageFitWidth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.page_fit
import seforimapp.seforimapp.generated.resources.page_fit_tooltip
import seforimapp.seforimapp.generated.resources.page_fit_two_thirds
import seforimapp.seforimapp.generated.resources.page_fit_two_thirds_tooltip
import seforimapp.seforimapp.generated.resources.page_fit_width
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

/**
 * The zooms that fit pages in their view: as wide as it ([width]), a whole page in it ([whole]), and two thirds of a
 * page in its height ([twoThirds], request 81), none wider than the view.
 */
@Immutable
internal data class PageFit(
    val width: Float,
    val whole: Float,
    val twoThirds: Float = whole,
)

/**
 * The [PageFit] of [columns] pages a row, each [share] wide at the view's width (its share of it), in a view [height]
 * tall: a page of [aspect] (its height over its width) whole in it, and two thirds of it, the room [around] it (its
 * title, the gaps) left aside, and no wider than the view; as wide as the view while the page's [aspect] is not known.
 * In [one]'s unit.
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
    if (aspect == null || aspect <= 0f) return PageFit(width, width, width)
    val tall = (height - around).coerceAtLeast(1f) / aspect * columns / one
    return PageFit(width, minOf(width, tall), minOf(width, tall * TWO_THIRDS_TALL))
}

/**
 * The fits the end bar's button goes through, one a press (request 81 of docs/DESKTOP_STAGE3_PLAN.md: "להוסיף לגודל צורת
 * הדף מצב של שתי שליש מהעמוד נראה בגובה"): a whole page, two thirds of it, the view's width.
 */
internal enum class PageFitMode { WHOLE, TWO_THIRDS, WIDTH }

/** The zoom of [mode]. */
internal fun PageFit.zoomOf(mode: PageFitMode): Float =
    when (mode) {
        PageFitMode.WHOLE -> whole
        PageFitMode.TWO_THIRDS -> twoThirds
        PageFitMode.WIDTH -> width
    }

/**
 * The fits the button goes through, in their order, each zoom once, as near as the zoom's ends let them be: two thirds
 * of a page only between a whole page and the view's width (no wider than the view, it is its width), the width only
 * when wider than a whole page.
 */
internal fun PageFit.modes(): List<PageFitMode> {
    val whole = shown(PageFitMode.WHOLE)
    val twoThirds = shown(PageFitMode.TWO_THIRDS)
    val width = shown(PageFitMode.WIDTH)
    return buildList {
        add(PageFitMode.WHOLE)
        if (twoThirds - whole >= FIT_SLACK && width - twoThirds >= FIT_SLACK) add(PageFitMode.TWO_THIRDS)
        if (width - whole >= FIT_SLACK) add(PageFitMode.WIDTH)
    }
}

/** The fit pages at [zoom] are at, as near as the zoom's ends let them be; null at another zoom. */
internal fun PageFit.modeAt(zoom: Float): PageFitMode? = modes().firstOrNull { abs(zoom - shown(it)) < FIT_SLACK }

// The zoom of [mode] the pages can be shown at, within the zoom's ends
private fun PageFit.shown(mode: PageFitMode): Float = zoomOf(mode).coerceIn(AppSettings.MIN_DAF_ZOOM, AppSettings.MAX_DAF_ZOOM)

/** The fit a press of the button goes to from [zoom]: the one after the fit it is at, a whole page from none. */
internal fun PageFit.nextMode(zoom: Float): PageFitMode {
    val modes = modes()
    val at = modeAt(zoom)?.let(modes::indexOf) ?: -1
    return modes[(at + 1) % modes.size]
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
 * The end bar's button under two pages', while the tab of [uiState] shows pages ([showsPages]): a press fits them in
 * the view, a whole page, then two thirds of it in the view's height, then the view's width, and again, as a PDF
 * reader's fit (request 81). On, its icon and its name those of the fit they are at; off at another zoom, a whole
 * page's, which a press gives; its tooltip says where a press goes.
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
    val at = fit?.modeAt(zoom)
    val shown = at ?: PageFitMode.WHOLE
    val next = fit?.nextMode(zoom) ?: PageFitMode.WHOLE
    val name =
        when (shown) {
            PageFitMode.WHOLE -> Res.string.page_fit
            PageFitMode.TWO_THIRDS -> Res.string.page_fit_two_thirds
            PageFitMode.WIDTH -> Res.string.page_fit_width
        }
    val tooltip =
        when (next) {
            PageFitMode.WHOLE -> Res.string.page_fit_tooltip
            PageFitMode.TWO_THIRDS -> Res.string.page_fit_two_thirds_tooltip
            PageFitMode.WIDTH -> Res.string.page_fit_width_tooltip
        }
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(tooltip),
        onClick = { fit?.let { graph.appSettings.setDafZoom(it.zoomOf(next)) } },
        isSelected = at != null,
        icon =
            when (shown) {
                PageFitMode.WHOLE -> PageFit
                PageFitMode.TWO_THIRDS -> PageFitTwoThirds
                PageFitMode.WIDTH -> PageFitWidth
            },
        label = stringResource(name),
        modifier = modifier,
        enabled = fit != null,
        iconDescription = stringResource(name),
    )
}

// A zoom this close to a fit is at it
private const val FIT_SLACK = 0.005f

// Two thirds of a page in the view: the page half again as tall as the view
private const val TWO_THIRDS_TALL = 1.5f
