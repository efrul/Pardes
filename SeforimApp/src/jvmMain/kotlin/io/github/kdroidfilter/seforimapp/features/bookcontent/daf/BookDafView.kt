package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimapp.features.bookcontent.segments.SelectedPassage
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_daf_no_commentators
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): a book's own daf, in the text view's frame as a tractate's is (its
// zoom, keys, find bar, selection and menu): the book's units one below the other (BookDafSource), each laid out in
// pages when it comes into view (BookDafPlaces), its text with the commentators the commentaries pane shows around it,
// drawn and clicked as a tractate's amudim are (LinkedAmud). A check in the pane sets the pages again.

/** The tab's line, selection and place, as a book's daf takes them from the text view (DafView's). */
internal class DafTab(
    val lineId: Long?,
    val selectedLineIds: Set<Long>,
    val isTocEntrySelection: Boolean,
    val selectedPassage: SelectedPassage?,
    val scrollToLineTimestamp: Long,
    val openAt: Long?,
)

/** The units kept laid out: one scrolled back to from farther is laid out again. */
private const val KEPT_UNITS = 16

/** The page's widest at the zoom's 1 (and no wider than the view), as a tractate's. */
private const val MAX_PAGE_WIDTH = 1100f

/** A page's running head, the room on its sides and below it, and a unit's place while it is laid out. */
private val HeadHeight = 36.dp
private val PagePadding = 24.dp
private val PageGap = 12.dp
private val LoadingHeight = 240.dp

/** How long a line brought into view waits for its unit to be laid out. */
private const val LAY_WAIT_MS = 10_000L

/** A page up or down moves this much of the view, as a tractate's. */
private const val PAGE_STEP = 0.9f

/**
 * [book] as its own daf, inside the text view's frame: its units' pages one below the other, its text with the
 * [commentators] shown around it, its words in the text's [diacritics], at the daf's [zoom] (as a tractate's, the pages
 * as wide as the view at 1). The [tab]'s selection is marked on the pages and its line brought into view, as a
 * tractate's; the words' [clicks] select as the text's; the [find] in page's matches are marked. Its place, the text
 * line read at its top, is told to [onScroll] once it rests there.
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
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val gemaraCode by graph.appSettings.dafGemaraFontCodeFlow.collectAsState()
    val commentaryCode by graph.appSettings.dafCommentaryFontCodeFlow.collectAsState()
    val gemara = dafFamily(gemaraCode)
    val rashi = dafFamily(commentaryCode)
    val units by produceState(emptyList<DafUnit>(), book.id) {
        value = runSuspendCatching { unitsOf(graph.repository, book) }.getOrDefault(emptyList())
    }
    // Each commentator's side, kept while the book is shown: a check in the pane moves none of the others
    val ranks = remember(book.id) { ConcurrentHashMap<Long, Int>() }
    val letters =
        remember(resolver, density, gemara, rashi) {
            DafLetters(TextMeasurer(resolver, density, LayoutDirection.Rtl, cacheSize = 0), density, gemara, rashi)
        }
    val setter =
        remember(book.id, commentators.selection, diacritics, letters) {
            UnitSetter(graph.repository, book, commentators, diacritics, ranks, letters)
        }
    // The units laid out, by their place in the list: those in view, and one on each side of them. When the pages are
    // set otherwise (a check in the pane, the diacritics, the fonts), those in view stay as they were until laid out
    // again, so that the list does not run on under units shrunk to their place meanwhile; the others are let go of
    val laid = remember(book.id) { mutableStateMapOf<Int, LaidUnit>() }
    LaunchedEffect(setter, units) {
        if (units.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }.collectLatest { visible ->
            val first = visible.minOrNull() ?: 0
            val last = visible.maxOrNull() ?: 0
            val near = (first - 1)..(last + 1)
            laid.keys.filter { it !in near && laid[it]?.setter !== setter }.forEach { laid.remove(it) }
            for (i in (first..last) + (last + 1) + (first - 1)) {
                if (i !in units.indices || laid[i]?.setter === setter) continue
                laid[i] = runSuspendCatching { setter.lay(units[i]) }.getOrNull() ?: continue
                // The farthest from it let go of, beyond the units kept
                if (laid.size > KEPT_UNITS) laid.keys.maxByOrNull { abs(it - i) }?.let { laid.remove(it) }
            }
        }
    }
    Column(modifier.fillMaxSize()) {
        // Nothing checked in the commentaries pane: the text alone, and how to set commentators around it
        if (commentators.selection.isEmpty) {
            InlineInformationBanner(
                text = stringResource(Res.string.book_daf_no_commentators),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        BookDafPages(book, units, laid, rememberUpdatedState(setter), listState, control, zoom, tab, clicks, find, onScroll, letters)
    }
}

/** The pages of a book's daf: its [units] in a list, those [laid] out as pages, the others in their place meanwhile. */
@Suppress("ComposeUnstableCollections") // the units and those laid out, as a tractate's daf takes its amudim
@Composable
private fun BookDafPages(
    book: Book,
    units: List<DafUnit>,
    laid: Map<Int, LaidUnit>,
    setter: State<UnitSetter>,
    listState: LazyListState,
    control: DafControl,
    zoom: Float,
    tab: DafTab,
    clicks: DafClicks,
    find: DafFind,
    onScroll: (Long) -> Unit,
    letters: DafLetters,
) {
    val graph = LocalAppGraph.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val keys = remember { FocusRequester() }
    // The words selected on a page, one page's at a time (its name and range), copied with Ctrl or Cmd + C
    var selection by remember(setter.value) { mutableStateOf<Pair<String, DafRange>?>(null) }
    val copyToClipboard = rememberCopyToClipboard()
    // A line the page selected itself, by a click: where the pointer is, not to be scrolled to; the comment a click on
    // its own words selected, which the arrows step from
    var clicked by remember { mutableStateOf<Long?>(null) }
    var focused by remember(setter.value) { mutableStateOf<Long?>(null) }
    val pageClicks =
        DafClicks(
            onSelectLine = { id, withModifier ->
                clicked = id
                clicks.onSelectLine(id, withModifier)
            },
            onSelectPassage = { id, comments, books ->
                clicked = id
                clicks.onSelectPassage(id, comments, books)
            },
            onFocusComment = { focused = it },
            onContextLine = clicks.onContextLine,
            onWordInText = clicks.onWordInText,
        )
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .focusRequester(keys)
            .onKeyEvent { event ->
                val (name, range) = selection ?: return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.C -> {
                        laid.values
                            .flatMap { it.pages }
                            .firstOrNull { it.amud.amud == name }
                            ?.let { copyToClipboard(it.amud.textOf(range)) }
                        true
                    }
                    event.key == Key.Escape -> {
                        selection = null
                        true
                    }
                    else -> false
                }
            }.focusable(),
    ) {
        val viewport = constraints.maxWidth.toFloat()
        val padding = with(density) { PagePadding.toPx() }
        val base = minOf(viewport - 2 * padding, with(density) { MAX_PAGE_WIDTH.dp.toPx() }).coerceAtLeast(1f)
        val pageWidth = base * zoom
        val perPoint = pageWidth / BOOK_DAF_WIDTH
        val contentWidth = maxOf(viewport, pageWidth + 2 * padding)
        val frame = PageFrame(with(density) { HeadHeight.toPx() }, with(density) { PageGap.toPx() })
        val places =
            BookDafList(listState, units, laid, setter, perPoint, frame, constraints.maxHeight.toFloat()) { id ->
                runSuspendCatching { graph.repository.getLine(id) }.getOrNull()?.takeIf { it.bookId == book.id }?.lineIndex
            }
        val current by rememberUpdatedState(places)
        KeepPlaceAtZoom(control, places)
        BookDafControl(control, current, tab, focused, pageClicks) { focused = it }
        RevealTabLine(control, current, tab, units, setter.value, clicked, onRelease = { clicked = null }, onScroll = onScroll)
        val hScroll = rememberScrollState()
        val look =
            DafLook(perPoint, letters.gemara, letters.rashi, JewelTheme.globalColors.text.normal, JewelTheme.globalColors.outlines.focused)
        val marks =
            DafMarks(
                marked = if (tab.isTocEntrySelection) emptySet() else tab.selectedLineIds,
                passage = tab.selectedPassage?.targetLineIds.orEmpty(),
                find = find,
            )
        Box(Modifier.fillMaxSize().horizontalScroll(hScroll).sidewaysPan(hScroll, rtl)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(with(density) { contentWidth.toDp() }).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(units, key = { _, unit -> unit.first }) { index, _ ->
                    val unit = laid[index]
                    if (unit == null) {
                        Box(Modifier.fillMaxWidth().height(LoadingHeight), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            for (page in unit.pages) {
                                val name = page.amud.amud
                                BookDafPage(
                                    book = book,
                                    page = page,
                                    look = look,
                                    marks = marks,
                                    selection =
                                        AmudSelection(
                                            range = selection?.takeIf { it.first == name }?.second,
                                            onRange = { range -> selection = range?.let { name to it } },
                                            onPress = { keys.requestFocus() },
                                        ),
                                    clicks = pageClicks,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A page of a book's daf: its running head, then its text and commentaries, tied to the library. */
@Composable
private fun BookDafPage(
    book: Book,
    page: LaidPage,
    look: DafLook,
    marks: DafMarks,
    selection: AmudSelection,
    clicks: DafClicks,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(HeadHeight), contentAlignment = Alignment.BottomCenter) {
            Text(
                page.head,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = JewelTheme.globalColors.text.info,
            )
        }
        LinkedAmud(
            book = book,
            amud = page.amud,
            layout = page.layout,
            look = look,
            marks = marks,
            selection = selection,
            clicks = clicks,
            modifier = Modifier.padding(horizontal = PagePadding).padding(bottom = PageGap),
            passages = page.passages,
        )
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
