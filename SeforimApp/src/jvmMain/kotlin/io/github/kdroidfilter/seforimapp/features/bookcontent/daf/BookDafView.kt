package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.CommentatorsListAndTexts
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.CommentatorsSidebarToggleButton
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_daf_continued
import seforimapp.seforimapp.generated.resources.book_daf_no_commentators
import kotlin.math.abs
import kotlin.math.roundToInt

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): a book's own daf, in the text view's frame as a tractate's is (its
// zoom, keys, find bar, selection and menu): the book's units one below the other (BookDafSource), each laid out in
// pages when it comes into view (BookDafPlaces), its text with the commentators the commentaries pane shows, in the
// reader's style (BookDafStyle), drawn and clicked as a tractate's amudim are (LinkedAmud). A check in the pane, or a
// change of the style in the settings, sets the pages again.

/** The units kept laid out: one scrolled back to from farther is laid out again. */
private const val KEPT_UNITS = 16

/** A unit's place while it is laid out. */
private val LoadingHeight = 240.dp

/** How long a line brought into view waits for its unit to be laid out. */
private const val LAY_WAIT_MS = 10_000L

/** A page up or down moves this much of the view, as a tractate's. */
private const val PAGE_STEP = 0.9f

/**
 * [book] as its own daf, inside the text view's frame: its units' pages one below the other, its text with the
 * [commentators] shown, its words in the text's [diacritics], at the daf's [zoom] (a size, as a tractate's). The
 * [tab]'s selection is marked on the pages and its line brought into view, as a tractate's; the words' [clicks] select
 * as the text's; the [find] in page's matches are marked. Its place, the text line read at its top, is told to
 * [onScroll] once it rests there.
 */
@Composable
internal fun BookDafView(
    book: Book,
    commentators: DafCommentators,
    diacritics: DiacriticsMode,
    listState: LazyListState,
    control: DafControl,
    zoom: Float,
    tab: DafTab,
    clicks: DafClicks,
    find: DafFind,
    onScroll: (lineId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val units by produceState(emptyList<DafUnit>(), book.id) {
        value = runSuspendCatching { unitsOf(graph.repository, book) }.getOrDefault(emptyList())
    }
    val style = rememberBookDafStyle(book)
    val letters = rememberDafLetters(style)
    // The book's commentators on its pages: the reader's own, the commentaries pane's the first time (request 92)
    val own = rememberBookDafCommentators(book, commentators, units, tab.openAt ?: tab.lineId)
    val chooser by graph.appSettings.flow(BookDafSettings.chooser).collectAsState()
    // The units laid out, by their place in the list: those in view, and one on each side of them
    val laid = remember(book.id) { mutableStateMapOf<Int, LaidUnit>() }
    Box(modifier.fillMaxSize()) {
        if (own == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            // The list of the pages' commentators beside them, as the commentaries pane's stands beside its texts
            CommentatorsListAndTexts(
                isListVisible = chooser,
                compact = false,
                list = { BookDafChooser(book, own, laid) },
                texts = {
                    Column(Modifier.fillMaxSize()) {
                        // No commentator chosen for the pages: the text alone, and where to choose them
                        if (own.shown.isNullOrEmpty()) {
                            InlineInformationBanner(
                                text = stringResource(Res.string.book_daf_no_commentators),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val view = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                            // Where the pages follow the view's width, its size is theirs, and they are set again for it
                            val look = BookDafLook(style, rememberWidePage(view, zoom, style), own)
                            val setter = rememberUnitSetter(book, commentators, diacritics, letters, look)
                            LaunchedEffect(setter, units) {
                                if (units.isNotEmpty()) {
                                    snapshotFlow {
                                        listState.layoutInfo.visibleItemsInfo.map { it.index }
                                    }.collectLatest { layUnits(it, units, setter, laid) }
                                }
                            }
                            val shown = BookDafShown(book, units, laid, rememberUpdatedState(setter), letters, view, own, commentators)
                            BookDafPages(shown, listState, control, zoom, tab, clicks, find, onScroll)
                        }
                    }
                },
            )
        }
        // The pages' style and the list of their commentators, behind two buttons at their corner
        BookDafAdjust(book, Modifier.align(Alignment.TopEnd)) {
            CommentatorsSidebarToggleButton(isVisible = chooser, onToggle = { graph.appSettings[BookDafSettings.chooser] = !chooser })
        }
    }
}

/**
 * What a book's pages show: its [units], those [laid] out (by the daf's [setter]), in [letters], in a [view]; the
 * reader's commentators for them ([own]), and what a commentator's name asks of the tab ([commentators]).
 */
private class BookDafShown(
    val book: Book,
    val units: List<DafUnit>,
    val laid: Map<Int, LaidUnit>,
    val setter: State<UnitSetter>,
    val letters: DafLetters,
    val view: Size,
    val own: BookDafCommentators,
    val commentators: DafCommentators,
)

/**
 * Lays out those of [units] in view (the list's items [visible]), and one on each side of them, into [laid]. When the
 * pages are set otherwise (a new [setter]), those in view stay as they were until laid out again, so that the list does
 * not run on under units shrunk to their place meanwhile; the others are let go of.
 */
private suspend fun layUnits(
    visible: List<Int>,
    units: List<DafUnit>,
    setter: UnitSetter,
    laid: MutableMap<Int, LaidUnit>,
) {
    val first = visible.minOrNull() ?: 0
    val last = visible.maxOrNull() ?: 0
    val near = (first - 1)..(last + 1)
    laid.keys.filter { it !in near && laid[it]?.setter !== setter }.forEach { laid.remove(it) }
    for (i in ((first..last) + (last + 1) + (first - 1)).filter { it in units.indices && laid[it]?.setter !== setter }) {
        val unit =
            runSuspendCatching { setter.lay(units[i]) }
                .onFailure { warnln { "[BookDaf] the unit at the line ${units[i].first} could not be laid out: $it" } }
                .getOrNull() ?: continue
        laid[i] = unit
        // The farthest from it let go of, beyond the units kept
        if (laid.size > KEPT_UNITS) laid.keys.maxByOrNull { abs(it - i) }?.let { laid.remove(it) }
    }
}

/** The pages of a book's daf ([shown]): its units in a list, those laid out as pages, the others in their place meanwhile. */
@Composable
private fun BookDafPages(
    shown: BookDafShown,
    listState: LazyListState,
    control: DafControl,
    zoom: Float,
    tab: DafTab,
    clicks: DafClicks,
    find: DafFind,
    onScroll: (Long) -> Unit,
) {
    val book = shown.book
    val units = shown.units
    val laid = shown.laid
    val setter = shown.setter
    val graph = LocalAppGraph.current
    val keys = remember { FocusRequester() }
    // The words selected on a page, one page's at a time (its name and range), copied with Ctrl or Cmd + C
    var selection by remember(setter.value) { mutableStateOf<Pair<String, DafRange>?>(null) }
    val copyToClipboard = rememberCopyToClipboard()
    // A line the page selected itself, by a click: where the pointer is, not to be scrolled to; the comment a click on
    // its own words selected, which the arrows step from
    var clicked by remember { mutableStateOf<Long?>(null) }
    var focused by remember(setter.value) { mutableStateOf<Long?>(null) }
    val pageClicks = clicks.noting(onClick = { clicked = it }, onFocus = { focused = it })
    val heads = rememberHeads(shown)
    val copy: () -> Unit = {
        selection?.let { (name, range) ->
            laid.values
                .flatMap { it.pages }
                .firstOrNull { it.amud.amud == name }
                ?.let { copyToClipboard(it.amud.textOf(range)) }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(keys)
            .selectionKeys(selection != null, copy) { selection = null }
            .focusable(),
    ) {
        val height = shown.view.height
        val scale = pagesScale(control, zoom, shown.view, setter.value)
        val places =
            BookDafList(listState, units, laid, setter, scale.perPoint, scale.frame, height) { id ->
                runSuspendCatching { graph.repository.getLine(id) }.getOrNull()?.takeIf { it.bookId == book.id }?.lineIndex
            }
        val current by rememberUpdatedState(places)
        KeepPlaceAtZoom(control, places)
        BookDafControl(control, current, tab, focused, pageClicks) { focused = it }
        RevealTabLine(control, current, tab, units, setter.value, clicked, onRelease = { clicked = null }, onScroll = onScroll)
        val ink = JewelTheme.globalColors.text.normal
        val look = DafLook(scale.perPoint, shown.letters.gemara, shown.letters.rashi, ink, JewelTheme.globalColors.outlines.focused)
        val marks =
            DafMarks(
                marked = if (tab.isTocEntrySelection) emptySet() else tab.selectedLineIds,
                passage = tab.selectedPassage?.targetLineIds.orEmpty(),
                find = find,
            )
        UnitsList(places, scale) { page ->
            val name = page.amud.amud
            val pageSelection =
                AmudSelection(
                    range = selection?.takeIf { it.first == name }?.second,
                    onRange = { range -> selection = range?.let { name to it } },
                    onPress = { keys.requestFocus() },
                )
            BookDafPage(book, page, look, marks, pageSelection, pageClicks, heads)
        }
    }
}

/**
 * What the commentators' names on the pages of [shown] do (BookDafHeads): a change of the book's commentators is saved,
 * and sets the pages again; a click opens the commentator's book at its comment on the page's lines; a commentator
 * taken out leaves the pages for a pane of its own.
 */
@Composable
private fun rememberHeads(shown: BookDafShown): BookDafHeadsState {
    val settings = LocalAppGraph.current.appSettings
    // The name whose letters are being set: kept over the pages, which every change sets again
    var setting by remember(shown.book.id) { mutableStateOf<Pair<Long, String>?>(null) }
    val bookId = shown.book.id
    return BookDafHeadsState(
        own = shown.own,
        hasSide = shown.setter.value.hasSide,
        settings = setting,
        onSettings = { setting = it },
        onOpen = { id, page -> shown.commentators.onOpen(id, page.lines) },
        onDetach = { id ->
            val names =
                shown.laid.values
                    .flatMap { it.groups }
                    .flatMap { it.commentators }
            names.firstOrNull { it.bookId == id }?.let { shown.commentators.onDetach(it.name) }
            settings.setBookDafCommentators(bookId, shown.own.checked(listOf(id), checked = false))
        },
        onChange = { settings.setBookDafCommentators(bookId, it) },
    )
}

/** These clicks with the line each selects told first ([onClick]), and the comment one focuses ([onFocus]). */
private fun DafClicks.noting(
    onClick: (Long) -> Unit,
    onFocus: (Long?) -> Unit,
) = DafClicks(
    onSelectLine = { id, withModifier ->
        onClick(id)
        onSelectLine(id, withModifier)
    },
    onSelectPassage = { id, comments, books ->
        onClick(id)
        onSelectPassage(id, comments, books)
    },
    onFocusComment = onFocus,
    onContextLine = onContextLine,
    onWordInText = onWordInText,
)

/** The keys of the words selected on a page ([selected]: some are): Ctrl or Cmd + C copies them, Escape lets go of them. */
private fun Modifier.selectionKeys(
    selected: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit,
): Modifier =
    onKeyEvent { event ->
        when {
            !selected || event.type != KeyEventType.KeyDown -> false
            (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.C -> {
                onCopy()
                true
            }
            event.key == Key.Escape -> {
                onClear()
                true
            }
            else -> false
        }
    }

/**
 * The list of a book's daf's units ([places]), each laid out as its pages (drawn by [page]) in rows of the [scale]'s
 * columns, a row's first page on the right in a view laid out from the right, as a book lies open; a unit not laid out
 * yet in its place meanwhile. Wider than the view, it scrolls sideways.
 */
@Composable
private fun UnitsList(
    places: BookDafList,
    scale: PagesScale,
    page: @Composable (LaidPage) -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val hScroll = rememberScrollState()
    Box(Modifier.fillMaxSize().horizontalScroll(hScroll).sidewaysPan(hScroll, rtl)) {
        LazyColumn(
            state = places.listState,
            modifier = Modifier.width(with(density) { scale.contentWidth.toDp() }).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(places.units, key = { _, unit -> unit.first }) { index, _ ->
                val unit = places.laid[index]
                if (unit == null) {
                    Box(Modifier.fillMaxWidth().height(LoadingHeight), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    UnitRows(index, unit, places, scale.columns, page)
                }
            }
        }
    }
}

/** How far beyond the view a unit's rows of pages are composed, on each side: a part of the view's height. */
private const val ROWS_AHEAD = 0.5f

/**
 * A unit laid out, in its item [index] of the list ([places]): an item as tall as all its rows of pages (of [columns]),
 * of which those in the view or near it alone are composed, each where the rows above it leave it. A chapter with many
 * commentators is hundreds of pages: composed and measured together they held the window, and the memory until the app
 * was let go of (the testers' build of 2026-11-23, Bereshit 1 with all its commentators checked).
 */
@Composable
private fun UnitRows(
    index: Int,
    unit: LaidUnit,
    places: BookDafList,
    columns: Int,
    page: @Composable (LaidPage) -> Unit,
) {
    val frame = places.frame
    val rows = remember(unit, columns) { unit.rows(columns) }
    val tops = remember(unit, places.perPoint, frame.head, frame.gap, columns) { unit.rowTops(places.perPoint, frame) }
    val listState = places.listState
    val view = places.viewportHeight
    val shown by remember(index, tops, listState, view) {
        derivedStateOf {
            val top = listState.topIn(index, tops.last() - view)
            rowsIn(tops, top - view * ROWS_AHEAD, top + view * (1 + ROWS_AHEAD))
        }
    }
    Layout(
        content = {
            for (row in shown) {
                key(row) { Row(Modifier.layoutId(row)) { for (laidPage in rows[row]) page(laidPage) } }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { (it.layoutId as Int) to it.measure(Constraints()) }
        layout(constraints.maxWidth, tops.last().roundToInt()) {
            for ((row, placeable) in placeables) {
                placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, tops[row].roundToInt())
            }
        }
    }
}

// The pixels down the list's item [index] at the view's top: where the list has it, or is about to (a jump into it);
// an item not in view yet as it comes into it, from its top below the view, from its end ([last]) above it
private fun LazyListState.topIn(
    index: Int,
    last: Float,
): Float {
    if (index == firstVisibleItemIndex) return firstVisibleItemScrollOffset.toFloat()
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    return when {
        item != null -> -item.offset.toFloat()
        index < firstVisibleItemIndex -> last
        else -> 0f
    }
}

/**
 * A page of a book's daf: its running head, then its text and commentaries, tied to the library, with the rules between
 * its parts.
 */
@Composable
private fun BookDafPage(
    book: Book,
    page: LaidPage,
    look: DafLook,
    marks: DafMarks,
    selection: AmudSelection,
    clicks: DafClicks,
    heads: BookDafHeadsState,
    modifier: Modifier = Modifier,
) {
    val rule = JewelTheme.globalColors.borders.normal
    // Laid out as it comes near the view, off the UI thread, as a tractate's amud is: its place meanwhile is its size
    val layout by produceState(page.layoutIfLaid, page) {
        value = page.layoutIfLaid ?: withContext(Dispatchers.Default) { page.layout() }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(BookDafHead), contentAlignment = Alignment.BottomCenter) {
            Text(
                if (page.isContinued) stringResource(Res.string.book_daf_continued, page.head) else page.head,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = JewelTheme.globalColors.text.info,
            )
        }
        Box(Modifier.padding(horizontal = BookDafPadding).padding(bottom = BookDafGap)) {
            LinkedAmud(
                book = book,
                amud = page.amud,
                // (the page's own: a unit set otherwise gives this place another page before its layout comes)
                layout = layout?.takeIf { it.amud === page.amud },
                look = look,
                marks = marks,
                selection = selection,
                clicks = clicks,
                modifier =
                    Modifier.drawBehind {
                        // The page's own coordinates, left to right, whatever the view's direction
                        for (r in page.rules) {
                            drawLine(rule, Offset(r.x0, r.y0) * look.perPoint, Offset(r.x1, r.y1) * look.perPoint, strokeWidth = density)
                        }
                    },
                passages = page.passages,
            )
            // Its commentators' names, each over where it is printed: a click, two buttons, a drag (requests 91 and 93)
            BookDafHeads(page, look.perPoint, heads, Modifier.matchParentSize())
        }
    }
}

/**
 * A book's daf's list as it is now: its [units], those [laid] out (by the daf's [setter], or by the one before it while
 * they are laid out again), its scale ([perPoint] pixels a point) and its pages' [frame]; the index of a line of the
 * book ([indexOf], null for another's).
 */
private class BookDafList(
    val listState: LazyListState,
    val units: List<DafUnit>,
    val laid: Map<Int, LaidUnit>,
    val setter: State<UnitSetter>,
    val perPoint: Float,
    val frame: PageFrame,
    val viewportHeight: Float,
    val indexOf: suspend (Long) -> Int?,
) {
    /** The unit a library line is on: a line of the book's by its index, a comment's among the units laid out. */
    suspend fun unitOf(id: Long): Int? {
        laid.entries.firstOrNull { (_, unit) -> unit.pages.any { id in it.tops } }?.let { return it.key }
        val index = indexOf(id) ?: return null
        return units.indexOfFirst { index >= it.first && index < it.end }.takeIf { it >= 0 }
    }

    /**
     * The pixels down the item of the unit [index] to where the library line [id] is printed first, once laid out (the
     * map of those laid out is the one the list keeps, filled as the unit is laid out).
     */
    suspend fun offsetOf(
        index: Int,
        id: Long,
    ): Float? {
        val unit =
            laid[index]?.takeIf { it.setter === setter.value } ?: run {
                // Not in view: brought there to be laid out; in view, as it was until laid out again
                if (index !in laid) listState.scrollToItem(index)
                withTimeoutOrNull(LAY_WAIT_MS) {
                    snapshotFlow { laid[index]?.takeIf { it.setter === setter.value } }.filterNotNull().first()
                }
            } ?: return null
        return unit.offsetOf(id, perPoint, frame)
    }

    /**
     * Brings the library line [id] into view, a page's head below the view's top ([atTop]: at the very top), unless it is
     * in view; a line in a page's first lines, the page from its own top, with its head.
     */
    suspend fun reveal(
        id: Long,
        always: Boolean,
        atTop: Boolean = false,
    ) {
        val index = unitOf(id) ?: return
        val y = offsetOf(index, id) ?: return
        if (!always && isShown(index, y)) return
        val unit = laid[index] ?: return
        val at = unit.pageOpenedBy(id, perPoint, frame) ?: if (atTop) y else y - frame.head
        // The list measures a unit just laid out in a frame to come: an offset into it before then would run on through
        // the places of the units after it
        if (!isMeasured(index, unit)) {
            listState.scrollToItem(index)
            withTimeoutOrNull(LAY_WAIT_MS) { snapshotFlow { isMeasured(index, unit) }.first { it } }
        }
        listState.scrollToItem(index, at.toInt().coerceAtLeast(0))
    }

    /** Whether the list has measured the item of the unit [index] as [unit] laid out: its height, give or take a pixel a page. */
    fun isMeasured(
        index: Int,
        unit: LaidUnit,
    ): Boolean {
        val size =
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == index }
                ?.size ?: return false
        return abs(size - unit.height(perPoint, frame)) <= unit.pages.size + 2
    }

    /** Whether [y] pixels down the item of the unit [index] is in view. */
    fun isShown(
        index: Int,
        y: Float,
    ): Boolean {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
        return item.offset + y >= 0 && item.offset + y + frame.head <= listState.layoutInfo.viewportEndOffset
    }

    /** Whether the library line [id] is in view, on a unit laid out. */
    fun isShown(id: Long): Boolean {
        val (index, unit) = laid.entries.firstOrNull { (_, unit) -> unit.pages.any { id in it.tops } } ?: return false
        return unit.offsetOf(id, perPoint, frame)?.let { isShown(index, it) } == true
    }

    /** The text line read at the view's top. */
    fun topLine(): Long? {
        val index = listState.firstVisibleItemIndex
        val unit = laid[index] ?: return null
        val (page, points) = unit.placeAt(listState.firstVisibleItemScrollOffset.toFloat(), perPoint, frame)
        return unit.textAt(page, points)
    }

    /** The unit laid out that has the comment [id], and that unit. */
    fun unitWithComment(id: Long): LaidUnit? = laid.values.firstOrNull { it.hasComment(id) }
}

/** The same place at the view's top when the pages' scale changes (the zoom, the view's width): the list keeps pixels. */
@Composable
private fun KeepPlaceAtZoom(
    control: DafControl,
    places: BookDafList,
) {
    SideEffect {
        val shown = control.scale
        val from = shown.perPoint
        shown.perPoint = places.perPoint
        if (from == 0f || from == places.perPoint) return@SideEffect
        val listState = places.listState
        val index = listState.firstVisibleItemIndex
        val unit = places.laid[index] ?: return@SideEffect
        val offset = listState.firstVisibleItemScrollOffset
        // A zoom's frames do not add up each one's rounding: the place kept is the one the last frame was moved to
        val (page, points) =
            if (index == shown.index &&
                offset == shown.offset
            ) {
                shown.page to shown.points
            } else {
                unit.placeAt(offset.toFloat(), from, places.frame)
            }
        shown.index = index
        shown.page = page
        shown.points = points
        shown.offset = unit.offset(page, points, places.perPoint, places.frame).toInt()
        listState.requestScrollToItem(index, shown.offset)
    }
}

/**
 * What the reader's keys and find bar ask of a book's daf ([control]): a page up or down, the book's first or last
 * page, a line brought into view, a step through the commentary of the comment [focused] (selected by a click on
 * itself; [onFocus] given the one stepped to), and where the daf is.
 */
@Composable
private fun BookDafControl(
    control: DafControl,
    places: BookDafList,
    tab: DafTab,
    focused: Long?,
    clicks: DafClicks,
    onFocus: (Long?) -> Unit,
) {
    val currentPassage by rememberUpdatedState(tab.selectedPassage)
    val currentLine by rememberUpdatedState(tab.lineId)
    val currentFocused by rememberUpdatedState(focused)
    val stepping = remember { Mutex() }
    val listState = places.listState
    control.page = { forward -> listState.animateScrollBy((if (forward) 1 else -1) * places.viewportHeight * PAGE_STEP) }
    control.edge = { end ->
        if (end) {
            listState.scrollToItem((places.units.size - 1).coerceAtLeast(0))
            listState.scrollBy(1_000_000f)
        } else {
            listState.scrollToItem(0)
        }
    }
    control.toLine = { id -> places.reveal(id, always = false) }
    control.step = step@{ forward ->
        val from = currentFocused ?: return@step false
        // One step at a time: a key held down waits for the selection it asked for
        if (!stepping.tryLock()) return@step true
        try {
            // The selection moved on some other way (the table of contents, a search): the keys are the text's again
            if (!awaitPassage(from) { currentPassage }) {
                onFocus(null)
                return@step false
            }
            val next = places.unitWithComment(from)?.next(from, forward) ?: return@step true
            onFocus(next.id)
            clicks.onSelectPassage(next.lineId, setOf(next.id), setOf(next.bookId))
            places.reveal(next.id, always = false)
            awaitPassage(next.id) { currentPassage }
            true
        } finally {
            stepping.unlock()
        }
    }
    control.where = where@{
        val top = places.topLine() ?: return@where null
        val shown = currentLine?.let(places::isShown) == true
        DafPlace(top, shown, onComment = currentFocused?.let { it in currentPassage?.targetLineIds.orEmpty() } == true)
    }
}

/**
 * The tab's line in view: once the units are known, then whenever it changes or the tab asks. At first the place the
 * tab was at (the text's top line, or the daf's own when it was last shown), the selection where it is; not a line the
 * page selected itself by a click ([clicked], let go of with [onRelease]). When the pages are set again ([setter]: a
 * check in the commentaries pane, the book's default commentators as it opens), the line read at the top stays there.
 * Once there, the daf's place is told to [onScroll] whenever it rests, for the tab to come back to it.
 */
@Suppress("ComposeUnstableCollections") // as BookDafPages
@Composable
private fun RevealTabLine(
    control: DafControl,
    places: BookDafList,
    tab: DafTab,
    units: List<DafUnit>,
    setter: UnitSetter,
    clicked: Long?,
    onRelease: () -> Unit,
    onScroll: (Long) -> Unit,
) {
    val current by rememberUpdatedState(places)
    val currentClicked by rememberUpdatedState(clicked)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var revealed by remember(units) { mutableStateOf(false) }
    // The line read at the top, once the daf is where the tab was: none before, which would be the book's start
    val top = remember(units) { arrayOf<Long?>(null) }
    LaunchedEffect(tab.lineId, tab.scrollToLineTimestamp, units) {
        if (units.isEmpty()) return@LaunchedEffect
        val opening = tab.openAt?.takeIf { !revealed && it > 0 }
        // Back from the text, which was not scrolled: the daf is where it was left
        val id = (opening ?: tab.lineId)?.takeIf { it > 0 && !(opening != null && it == control.where()?.topLineId) }
        when {
            id == null -> Unit
            id == currentClicked && revealed -> currentOnRelease()
            else -> current.reveal(id, always = !revealed, atTop = opening != null)
        }
        revealed = true
        top[0] = current.topLine() ?: id
    }
    LaunchedEffect(setter) {
        val at = top[0]
        if (at != null && revealed) current.reveal(at, always = true, atTop = true)
    }
    // Not before: a place told while the daf still goes to the tab's would become the tab's
    val currentOnScroll by rememberUpdatedState(onScroll)
    LaunchedEffect(units) {
        snapshotFlow { current.listState.isScrollInProgress to revealed }
            .filter { (scrolling, placed) -> placed && !scrolling }
            .collect {
                withFrameNanos { }
                control.where()?.let {
                    top[0] = it.topLineId
                    currentOnScroll(it.topLineId)
                }
            }
    }
}
