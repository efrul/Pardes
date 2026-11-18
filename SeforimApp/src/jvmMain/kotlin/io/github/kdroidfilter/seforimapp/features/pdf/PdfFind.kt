package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.pdfium.PageTextLayout
import dev.nucleusframework.pdfium.PdfReaderState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.components.FindInPageBar
import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesNormalized
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_find_no_text
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Pardes (request 48 of docs/DESKTOP_STAGE3_PLAN.md: "מה אפשר לשפר במציג ה-PDF, ומה עם חיפוש?"): a find in a PDF book,
// in its pages' text as the PDF library reads it (`PdfReaderState.pageTextLayout`, every character with its box), not
// by calls of Pardes's own to PDFium: its text needs fonts, which PDFium cannot load from two threads at once.

/**
 * A match of the find in a PDF book: its [page] (from 0), and its boxes there, a box a line it runs over, each a
 * fraction of the page's width and height from its top left (the page drawn at any size marks it at once).
 */
@Immutable
data class PdfMatch(
    val page: Int,
    val boxes: List<Rect>,
)

/**
 * A page's characters and their boxes in points from its bottom left, [width] by [height] points: as the PDF library
 * reads them ([chars] of its `PageTextLayout`), or as a test writes them.
 */
internal interface PageChars {
    val count: Int
    val width: Float
    val height: Float

    fun codepoint(i: Int): Int

    fun left(i: Int): Float

    fun bottom(i: Int): Float

    fun right(i: Int): Float

    fun top(i: Int): Float
}

/** The characters of this page's text layout. */
internal fun PageTextLayout.chars(): PageChars =
    object : PageChars {
        override val count = charCount
        override val width = pageSize.widthPoints
        override val height = pageSize.heightPoints

        override fun codepoint(i: Int) = this@chars.codepoint(i)

        override fun left(i: Int) = charLeft(i)

        override fun bottom(i: Int) = charBottom(i)

        override fun right(i: Int) = charRight(i)

        override fun top(i: Int) = charTop(i)
    }

/**
 * The matches of [query] in the text of the page [index] ([page]), matched as a book's lines are (nikud and final
 * letters aside, `findAllMatchesNormalized`), each match's characters' boxes joined line by line; when none, the query
 * backwards, for a PDF that keeps its Hebrew from left to right.
 */
internal fun pageMatches(
    page: PageChars,
    index: Int,
    query: String,
): List<PdfMatch> {
    val q = normalizeQueryForHebrew(query)
    if (q.length < MIN_QUERY) return emptyList()
    val (text, chars) = pageText(page)
    return textMatches(text, q).map { range -> PdfMatch(index, boxesOf(page, range.mapNotNull { chars.getOrNull(it) })) }
}

// The matches of the normalized query [q] in a page's [text]; when none, of [q] backwards
private fun textMatches(
    text: String,
    q: String,
): List<IntRange> = findAllMatchesNormalized(text, q).ifEmpty { findAllMatchesNormalized(text, q.reversed()) }

/** The page's characters as a text, and the character of each of its letters (a code point may be two). */
internal fun pageText(page: PageChars): Pair<String, IntArray> {
    val text = StringBuilder()
    val chars = ArrayList<Int>()
    for (i in 0 until page.count) {
        val letters = Character.toChars(page.codepoint(i).takeIf { Character.isValidCodePoint(it) } ?: ' '.code)
        text.append(letters)
        repeat(letters.size) { chars += i }
    }
    return text.toString() to chars.toIntArray()
}

/**
 * The boxes of the characters [chars] of [page], joined a box a line, as fractions of the page from its top left;
 * characters with no box (a line break PDFium adds) left aside.
 */
internal fun boxesOf(
    page: PageChars,
    chars: List<Int>,
): List<Rect> {
    val width = page.width.takeIf { it > 0f } ?: return emptyList()
    val height = page.height.takeIf { it > 0f } ?: return emptyList()
    val lines = ArrayList<Rect>()
    for (i in chars.distinct()) {
        val box =
            Rect(
                left = min(page.left(i), page.right(i)) / width,
                top = (height - max(page.top(i), page.bottom(i))) / height,
                right = max(page.left(i), page.right(i)) / width,
                bottom = (height - min(page.top(i), page.bottom(i))) / height,
            )
        if (box.width <= 0f || box.height <= 0f) continue
        val last = lines.lastOrNull()
        // On the same line: its middle within the last box's height
        if (last != null && abs(box.center.y - last.center.y) < max(last.height, box.height) / 2) {
            lines[lines.lastIndex] =
                Rect(min(last.left, box.left), min(last.top, box.top), max(last.right, box.right), max(last.bottom, box.bottom))
        } else {
            lines += box
        }
    }
    return lines
}

/** The find's shortest query, as a book's. */
private const val MIN_QUERY = 2

/** A box of a match drawn over its page: a fraction of the page from its top left, and whether it is the current one. */
@Immutable
data class PageMark(
    val box: Rect,
    val current: Boolean,
)

/**
 * The find of a PDF book's reader as it goes: its [query], the [matches] found so far, page by page, the [current]
 * one (-1 before Enter), whether every page was read ([done]) and whether the book has no text at all ([noText]: a
 * scan with no text layer). The pages' text is read once a book, the boxes of a page's matches once a query.
 */
@Stable
internal class PdfFind {
    var query by mutableStateOf("")
        private set
    var matches by mutableStateOf<List<PdfMatch>>(emptyList())
        private set
    var current by mutableIntStateOf(-1)
        private set
    var done by mutableStateOf(false)
        private set
    var noText by mutableStateOf(false)
        private set

    // Each page's text and the character of each of its letters, read once
    private val texts = HashMap<Int, Pair<String, IntArray>>()

    /** The marks of each page with matches, the current one marked so. */
    val marks: Map<Int, List<PageMark>>
        get() =
            matches
                .withIndex()
                .groupBy({ it.value.page }) { (index, match) -> match.boxes.map { PageMark(it, index == current) } }
                .mapValues { (_, boxes) -> boxes.flatten() }

    /** Finds [query] in [reader]'s pages, one after the other, the matches told as each page's come. */
    suspend fun run(
        reader: PdfReaderState,
        query: String,
    ) {
        this.query = query
        matches = emptyList()
        current = -1
        done = false
        noText = false
        val q = normalizeQueryForHebrew(query)
        if (q.length < MIN_QUERY) return
        val found = ArrayList<PdfMatch>()
        var letters = 0
        for (page in 0 until reader.pageCount) {
            val (text, chars) =
                texts[page] ?: (runSuspendCatching { reader.pageTextLayout(page) }.getOrNull()?.let { pageText(it.chars()) } ?: continue)
                    .also { texts[page] = it }
            letters += text.length
            val ranges = withContext(Dispatchers.Default) { textMatches(text, q) }
            if (ranges.isEmpty()) continue
            // The boxes, from the page's layout again: only a page with matches keeps them
            val layout = runSuspendCatching { reader.pageTextLayout(page) }.getOrNull()?.chars() ?: continue
            ranges.mapTo(found) { range -> PdfMatch(page, boxesOf(layout, range.mapNotNull { chars.getOrNull(it) })) }
            matches = found.toList()
        }
        noText = letters == 0
        done = true
    }

    /** The match after the current one, or before it ([forward] false), round the book; null with none. */
    fun step(forward: Boolean): PdfMatch? {
        val all = matches.ifEmpty { return null }
        current = if (current < 0) (if (forward) 0 else all.lastIndex) else (current + if (forward) 1 else -1).mod(all.size)
        return all[current]
    }
}

/**
 * A PDF book's find, while its tab's find bar is open (Ctrl/Cmd+F, as a book's): Zayit's [FindInPageBar] over the
 * pages, top end, its text the tab's find query, its counter the matches of [find], Enter and Shift+Enter (and the
 * window's Ctrl/Cmd+G and F3) going from match to match: its page's row in [listState] ([spreads]), the match a third
 * of the view down. A book with no text says so under the bar.
 */
@Composable
internal fun PdfFindBar(
    find: PdfFind,
    reader: PdfReaderState,
    tabId: String,
    listState: LazyListState,
    spreads: Boolean,
    modifier: Modifier = Modifier,
) {
    val appSettings = LocalAppGraph.current.appSettings
    val open by appSettings.findBarOpenFlow(tabId).collectAsState()
    val query by appSettings.findQueryFlow(tabId).collectAsState()
    val focusRequest by appSettings.findFocusRequestFlow(tabId).collectAsState()
    val scope = rememberCoroutineScope()
    val list by rememberUpdatedState(listState)
    val twoPages by rememberUpdatedState(spreads)
    LaunchedEffect(reader, query, open) { if (open) find.run(reader, query) }

    suspend fun reveal(match: PdfMatch) {
        val row = rowOf(match.page, twoPages)
        list.scrollToItem(row)
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row } ?: return
        val down = item.size * (match.boxes.firstOrNull()?.top ?: 0f)
        list.scrollBy(down - list.layoutInfo.viewportSize.height / REVEAL_THIRD)
    }

    fun step(forward: Boolean) {
        scope.launch { find.step(forward)?.let { reveal(it) } }
    }
    LaunchedEffect(tabId) { appSettings.findStepRequests(tabId).collect { forward -> step(forward) } }
    if (!open) return
    val field = remember(tabId) { TextFieldState(query) }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { typed ->
            appSettings.setFindQuery(tabId, typed.takeIf { it.length >= MIN_QUERY }.orEmpty())
        }
    }
    Column(modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
        FindInPageBar(
            state = field,
            onEnterNext = { step(true) },
            onEnterPrev = { step(false) },
            onClose = { appSettings.closeFindBar(tabId) },
            focusRequest = focusRequest,
            matchCount = find.matches.size.takeIf { find.query == query && (find.done || it > 0) },
            matchPosition = (find.current + 1).takeIf { it > 0 },
            showCounter = true,
        )
        if (find.done && find.noText) {
            Text(
                stringResource(Res.string.pdf_find_no_text),
                Modifier.padding(top = 6.dp),
                color = JewelTheme.globalColors.text.info,
                fontSize = 12.sp,
            )
        }
    }
}

/** Draws a page's find [marks] over what it draws, outside the dark theme's colours (request 42). */
internal fun Modifier.findMarks(marks: List<PageMark>?): Modifier =
    if (marks.isNullOrEmpty()) {
        this
    } else {
        drawWithContent {
            drawContent()
            for (mark in marks) {
                drawRect(
                    color = if (mark.current) CURRENT_MARK else MARK,
                    topLeft = Offset(mark.box.left * size.width, mark.box.top * size.height),
                    size = Size(mark.box.width * size.width, mark.box.height * size.height),
                )
            }
        }
    }

private val MARK = Color(0x66FFD54F)
private val CURRENT_MARK = Color(0x99FF9800)

// A match revealed a third of the view down, as a book's find puts its match in sight
private const val REVEAL_THIRD = 3
