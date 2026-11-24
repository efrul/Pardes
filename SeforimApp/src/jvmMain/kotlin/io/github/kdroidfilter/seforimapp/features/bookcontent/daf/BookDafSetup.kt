package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.features.pdf.TellPageFit
import io.github.kdroidfilter.seforimapp.features.pdf.ZOOM_ONE_WIDTH
import io.github.kdroidfilter.seforimapp.features.pdf.pageFit
import io.github.kdroidfilter.seforimapp.features.pdf.pageWidthAt
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): what a book's pages are set by and shown at (BookDafView): their
// letters, what lays their units out, their scale on the screen, and, since request 89 of docs/DESKTOP_STAGE3_PLAN.md
// ("הגדרות תצוגת רוחב ספר יהיו אמיתיות כלומר ישנו את הפריסה שלו ולא רק את הגודל שלו"), the page's own size where the
// pages follow the view's width: a page of the view's size at the zoom's letters, set again for it.

/** A page's running head, the room on its sides and below it. */
internal val BookDafHead = 36.dp
internal val BookDafPadding = 24.dp
internal val BookDafGap = 12.dp

/** The least a page that follows the view is, in points: a narrower or a shorter view scrolls it. */
private const val LEAST_PAGE_WIDTH = 240f
private const val LEAST_PAGE_HEIGHT = 280f

/** How long the view's size rests before the pages are set again for it: a pane dragged, the zoom's steps. */
private const val PAGE_SIZE_REST_MS = 150L

/** The daf's letters: the text's square ones, and the commentaries' ([style]: as one script around the text). */
@Composable
internal fun rememberDafLetters(style: BookDafStyle): DafLetters {
    val graph = LocalAppGraph.current
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val gemaraCode by graph.appSettings.dafGemaraFontCodeFlow.collectAsState()
    val commentaryCode by graph.appSettings.dafCommentaryFontCodeFlow.collectAsState()
    val gemara = dafFamily(gemaraCode)
    // Around the text the commentaries' columns are Rashi's and Tosafot's, in one script; a band has its own (BandLetters)
    val rashi = if (style.squareCommentary && style.layout == DafStyle.Around) gemara else dafFamily(commentaryCode)
    return remember(resolver, density, gemara, rashi) {
        DafLetters(TextMeasurer(resolver, density, LayoutDirection.Rtl, cacheSize = 0), density, gemara, rashi)
    }
}

/**
 * What lays out [book]'s units: a new one when the pages are to be set otherwise (the text's [diacritics], the fonts,
 * the [look]: the style, the page's size, the reader's commentators for the pages, their order and letters).
 */
@Composable
internal fun rememberUnitSetter(
    book: Book,
    commentators: DafCommentators,
    diacritics: DiacriticsMode,
    letters: DafLetters,
    look: BookDafLook,
): UnitSetter {
    val repository = LocalAppGraph.current.repository
    // Each commentator's place, kept while the book is shown: a check moves none of the others
    val ranks = remember(book.id) { ConcurrentHashMap<Long, Int>() }
    return remember(book.id, diacritics, letters, look) {
        UnitSetter(repository, book, commentators, diacritics, ranks, letters, look)
    }
}

/**
 * The page's size where the pages follow the view's width (the end bar's fit at "רוחב החלונית", BookDafSettings.wide):
 * as wide as the [view] (half of it with two pages side by side) and as tall, in points at the [zoom]'s letters, which
 * are a page's of [BOOK_DAF_WIDTH] at that zoom. So the fit sets the pages again for the view, more words in a line
 * and the wide page's columns, where it only made a page of the rules' size larger. Null where the pages are the
 * rules' own size; around the text they always are. The size is taken once the view has rested at it.
 */
@Composable
internal fun rememberWidePage(
    view: Size,
    zoom: Float,
    style: BookDafStyle,
): DafPageSize? {
    val settings = LocalAppGraph.current.appSettings
    val wide by settings.flow(BookDafSettings.wide).collectAsState()
    val spreads by settings.pageSpreadsFlow.collectAsState()
    val density = LocalDensity.current
    if (!wide || style.layout != DafStyle.Bands) return null
    val perPoint = with(density) { ZOOM_ONE_WIDTH.toPx() } * zoom / BOOK_DAF_WIDTH
    val around = with(density) { Size(2 * BookDafPadding.toPx(), (BookDafHead + BookDafGap).toPx()) }
    val target =
        DafPageSize(
            width = floor((view.width / (if (spreads) 2 else 1) - around.width) / perPoint).coerceAtLeast(LEAST_PAGE_WIDTH),
            height = floor((view.height - around.height) / perPoint).coerceAtLeast(LEAST_PAGE_HEIGHT),
        )
    var page by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        delay(PAGE_SIZE_REST_MS)
        page = target
    }
    return page
}

/** A page's size on the screen: [perPoint] pixels a point, [columns] pages a row, their list [contentWidth] pixels wide. */
internal class PagesScale(
    val perPoint: Float,
    val columns: Int,
    val contentWidth: Float,
    val frame: PageFrame,
)

/**
 * The scale of the pages of [setter] in the [view], at the daf's [zoom]: a size, which no pane opened beside the pages
 * changes (request 73 of stage 3), two pages side by side sharing the view as a book open (the end bar's button, as a
 * tractate's amudim); where the pages follow the view's width, the zoom is their letters' size alone. The zooms that
 * fit a page of the rules' size in the view are told for the tab of [control].
 */
@Composable
internal fun pagesScale(
    control: DafControl,
    zoom: Float,
    view: Size,
    setter: UnitSetter,
): PagesScale {
    val density = LocalDensity.current
    val spreads by LocalAppGraph.current.appSettings.pageSpreadsFlow
        .collectAsState()
    val columns = if (spreads) 2 else 1
    val padding = with(density) { BookDafPadding.toPx() }
    val frame = PageFrame(with(density) { BookDafHead.toPx() }, with(density) { BookDafGap.toPx() }, columns)
    val one = with(density) { ZOOM_ONE_WIDTH.toPx() }
    val fit = pageFit(view.width / columns - 2 * padding, view.height, setter.tallAspect, columns, frame.head + frame.gap, one)
    TellPageFit(control.tabId, fit, capped = !setter.followsView)
    val perPoint = (if (setter.followsView) zoom * one else pageWidthAt(zoom, columns, one)).coerceAtLeast(1f) / BOOK_DAF_WIDTH
    return PagesScale(perPoint, columns, maxOf(view.width, (setter.width * perPoint + 2 * padding) * columns), frame)
}
