package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorSelection
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): the Chumash as Mikraot Gedolot, a prototype to be shown before other
// books follow (docs/DESKTOP_STAGE2_PLAN.md). As a printed edition sets it (the user's example of 2026-11-13): pages of
// one size, one under the other; each verse at the top of where its part starts, in big letters with Onkelos beside it,
// then each commentator under its name in two columns, going on to the next pages until the next verse. The pages'
// boxes are laid out here, as the daf's come from the scans (DafAmud), and drawn by the daf's own setting and drawing
// (layoutAmud, DafAmudText): the verse and Onkelos in square letters, the commentaries in Rashi script.

/** A verse as the pages set it: its line, its words, Onkelos' on it, and each commentator's comments on it, by book. */
internal data class MikraotVerse(
    val lineId: Long,
    val heRef: String,
    val words: List<String>,
    val targum: List<String>,
    val comments: Map<Long, MikraotComment>,
)

/** A commentator's comments on a verse, together: their words, and the indices of those in bold (a dibbur). */
internal data class MikraotComment(
    val words: List<String>,
    val bold: Set<Int> = emptySet(),
)

/** A commentator of the pages: its book's [id] and its name ([title]), without the book it is on. */
internal data class MikraotCommentator(
    val id: Long,
    val title: String,
)

/** How wide a word and a space are, in points, in square letters or in Rashi script, at the page's letters. */
internal interface WordWidths {
    fun of(
        word: String,
        square: Boolean,
    ): Float

    fun space(square: Boolean): Float
}

/** A name over a part of a page, between [left] and [right], its top at [top] (points). */
internal data class MikraotHeading(
    val text: String,
    val left: Float,
    val right: Float,
    val top: Float,
)

/**
 * A page as laid out: its running head ([head]: the verses whose parts it holds, "בראשית א, א–ב"), the verses it starts
 * ([verseIds]), and its lines in three amudim, each
 * the page's size: the verses' ([verse], their letters [VERSE_SCALE] times Onkelos'), Onkelos' ([targum]) and the
 * commentaries' ([comments]); the commentators' names over their parts ([headings]).
 */
internal class MikraotPageText(
    val head: String,
    val verseIds: List<Long>,
    val verse: DafAmud,
    val targum: DafAmud,
    val comments: DafAmud,
    val headings: List<MikraotHeading>,
)

/** A page's width and height in points: the Talmud's block, which the daf's sizes are made for, and a book's page. */
internal const val MIKRAOT_PAGE_WIDTH = 412f
internal const val MIKRAOT_PAGE_HEIGHT = MIKRAOT_PAGE_WIDTH * 1.45f

/** Room at the page's top for its running head, and at its foot (points). */
private const val PAGE_TOP = 20f
private const val PAGE_FOOT = 6f

/** The verse's letters to Onkelos'; the verse's column, of the page's width. */
internal const val VERSE_SCALE = 1.45f
private const val VERSE_SHARE = 0.62f

/** A line's pitch to its box's height; the space between two columns (points); between two parts, in lines. */
private const val PITCH = 1.25f
private const val COLUMN_GAP = 14f
private const val PART_GAP = 0.8f

/** A commentator's name: the height it takes, to its letters' box. */
private const val HEADING_HEIGHT = 1.6f

/** A line is broken before the word that would take it past this much of its box: the setting's letters may differ. */
private const val FILL = 0.97f

/** A part's last line narrower than this much of its box keeps its own width, from the box's right edge. */
private const val SHORT_LAST = 0.85f

/** The fewest lines of a column a part starts with at a page's foot; with less room it starts on the next page. */
private const val LEAST_LINES = 2

/** The name over Onkelos. */
private const val TARGUM_TITLE = "אונקלוס"

/**
 * [verses] set as pages, one after the other: each verse at the top of where its part starts (on a new page when its
 * block does not fit the room left), in big letters on the right with Onkelos on the left, then each commentator of
 * [commentators] that comments on it, under its name, in two columns balanced on its last page, going on to the next
 * pages as long as it runs. A line is broken where its next word would not fit, as [widths] measures them; the daf's
 * setting spreads each line over its box.
 */
internal fun mikraotPages(
    verses: List<MikraotVerse>,
    commentators: List<MikraotCommentator>,
    widths: WordWidths,
): List<MikraotPageText> = PageSetter(widths).apply { verses.forEach { set(it, commentators) } }.pages()

/** The pages being set: the one being filled, its lines so far, and the height filled. */
private class PageSetter(
    private val widths: WordWidths,
) {
    private val sizes = DafSizes.of(MIKRAOT_PAGE_WIDTH)
    private val pages = mutableListOf<MikraotPageText>()

    // The verses whose parts the page holds, in order: its running head's; the verse being set
    private var refs = linkedSetOf<String>()
    private var current = ""
    private var verseIds = mutableListOf<Long>()
    private var verse = mutableListOf<DafLine>()
    private var targum = mutableListOf<DafLine>()
    private var comments = mutableListOf<DafLine>()
    private var headings = mutableListOf<MikraotHeading>()
    private var y = PAGE_TOP
    private val bottom = MIKRAOT_PAGE_HEIGHT - PAGE_FOOT

    private val squarePitch get() = sizes.gemara * PITCH
    private val rashiPitch get() = sizes.commentary * PITCH
    private val headingHeight get() = sizes.gemara * HEADING_HEIGHT

    fun set(
        v: MikraotVerse,
        commentators: List<MikraotCommentator>,
    ) {
        setVerse(v)
        for (commentator in commentators) {
            val comment = v.comments[commentator.id] ?: continue
            setComment(commentator.title, comment)
        }
    }

    fun pages(): List<MikraotPageText> {
        if (hasContent()) closePage()
        return pages
    }

    private fun setVerse(v: MikraotVerse) {
        val textLeft = MIKRAOT_PAGE_WIDTH * (1 - VERSE_SHARE)
        val targumRight = textLeft - COLUMN_GAP
        val verseLines = breakLines(v.words, MIKRAOT_PAGE_WIDTH - textLeft, square = true, VERSE_SCALE)
        val targumLines = breakLines(v.targum, targumRight, square = true, 1f)
        val verseHeight = verseLines.size * sizes.gemara * VERSE_SCALE * PITCH
        val targumHeight = if (targumLines.isEmpty()) 0f else headingHeight + targumLines.size * squarePitch
        val height = maxOf(verseHeight, targumHeight)
        if (y + height > bottom && hasContent()) closePage()
        current = v.heRef
        refs += current
        verseIds += v.lineId
        place(verse, Words(v.words), verseLines, textLeft, MIKRAOT_PAGE_WIDTH, y, square = true, VERSE_SCALE, isEnd = true)
        if (targumLines.isNotEmpty()) {
            headings += MikraotHeading(TARGUM_TITLE, 0f, targumRight, y)
            place(targum, Words(v.targum), targumLines, 0f, targumRight, y + headingHeight, square = true, 1f, isEnd = true)
        }
        y += height + squarePitch * PART_GAP
    }

    private fun setComment(
        title: String,
        comment: MikraotComment,
    ) {
        val middle = MIKRAOT_PAGE_WIDTH / 2
        val columnWidth = middle - COLUMN_GAP / 2
        val words = Words(comment.words, comment.bold)
        var lines = breakLines(comment.words, columnWidth, square = false, 1f)
        var first = true
        while (lines.isNotEmpty()) {
            val top = if (first) y + headingHeight else y
            val rows = ((bottom - top) / rashiPitch).toInt()
            if (rows < minOf(LEAST_LINES, (lines.size + 1) / 2) && hasContent()) {
                closePage()
                continue
            }
            // A page's whole height is always room for a line a column
            val room = rows.coerceAtLeast(1)
            if (first) headings += MikraotHeading(title, 0f, MIKRAOT_PAGE_WIDTH, y)
            refs += current
            // Balanced on its last page, the right column taking the odd line; else both columns as long as the room
            val here = minOf(lines.size, room * 2)
            val isEnd = here == lines.size
            val right = if (isEnd) (here + 1) / 2 else room
            val left = here - right
            place(comments, words, lines.subList(0, right), middle + COLUMN_GAP / 2, MIKRAOT_PAGE_WIDTH, top, false, 1f, isEnd && left == 0)
            if (left > 0) place(comments, words, lines.subList(right, here), 0f, columnWidth, top, false, 1f, isEnd)
            y = top + right * rashiPitch + rashiPitch * PART_GAP
            lines = lines.drop(here)
            first = false
        }
    }

    private fun hasContent() = verse.isNotEmpty() || targum.isNotEmpty() || comments.isNotEmpty()

    private fun closePage() {
        val head = refs.first().let { first -> if (refs.size == 1) first else "$first–${refs.last().substringAfterLast(", ")}" }

        fun amud(lines: List<DafLine>) =
            DafAmud(
                amud = head,
                page = listOf(MIKRAOT_PAGE_WIDTH, MIKRAOT_PAGE_HEIGHT),
                block = listOf(0f, 0f, MIKRAOT_PAGE_WIDTH, MIKRAOT_PAGE_HEIGHT),
                lines = lines,
            )
        pages += MikraotPageText(head, verseIds, amud(verse), amud(targum), amud(comments), headings)
        refs = linkedSetOf()
        verseIds = mutableListOf()
        verse = mutableListOf()
        targum = mutableListOf()
        comments = mutableListOf()
        headings = mutableListOf()
        y = PAGE_TOP
    }

    /** The words of [words] in lines no wider than [width], their letters [scale] times the page's: each line's range. */
    private fun breakLines(
        words: List<String>,
        width: Float,
        square: Boolean,
        scale: Float,
    ): List<IntRange> {
        val room = width * FILL
        val space = widths.space(square) * scale
        val ranges = mutableListOf<IntRange>()
        var start = 0
        while (start < words.size) {
            var end = start
            var lineWidth = widths.of(words[start], square) * scale
            while (end + 1 < words.size && lineWidth + space + widths.of(words[end + 1], square) * scale <= room) {
                end++
                lineWidth += space + widths.of(words[end], square) * scale
            }
            ranges += start..end
            start = end + 1
        }
        return ranges
    }

    /**
     * The lines [ranges] of [words] from [top] down, [left] to [right], one below the other at their pitch; the part's
     * last line ([isEnd]) as wide as its words when short, from the right, as a book prints it.
     */
    private fun place(
        into: MutableList<DafLine>,
        words: Words,
        ranges: List<IntRange>,
        left: Float,
        right: Float,
        top: Float,
        square: Boolean,
        scale: Float,
        isEnd: Boolean,
    ) {
        val height = (if (square) sizes.gemara else sizes.commentary) * scale
        ranges.forEachIndexed { i, range ->
            val lineWords = words.all.subList(range.first, range.last + 1)
            val width =
                (lineWords.sumOf { widths.of(it, square).toDouble() }.toFloat() + widths.space(square) * (lineWords.size - 1)) * scale
            val last = isEnd && i == ranges.lastIndex
            val x0 = if (last && width < (right - left) * SHORT_LAST) right - width / FILL else left
            val lineTop = top + i * height * PITCH
            into +=
                DafLine(
                    c = if (square) "G" else "R",
                    b = listOf(x0, lineTop, right, lineTop + height),
                    t = lineWords.joinToString(" "),
                    d = range.filter { it in words.bold }.map { it - range.first },
                )
        }
    }
}

/** A part's words and the indices of those in bold. */
private class Words(
    val all: List<String>,
    val bold: Set<Int> = emptySet(),
)

/** The words of [html] and the indices of those in bold (a dibbur hamatchil), in the order they are read. */
internal fun htmlWords(html: String): Pair<List<String>, Set<Int>> {
    val words = mutableListOf<String>()
    val bold = mutableSetOf<Int>()

    fun walk(
        element: Element,
        inBold: Boolean,
    ) {
        for (node in element.childNodes()) {
            when (node) {
                is TextNode ->
                    node.text().split(WHITESPACE).filter { it.isNotEmpty() }.forEach { word ->
                        if (inBold) bold += words.size
                        words += word
                    }
                is Element -> walk(node, inBold || node.tagName() in BOLD_TAGS)
            }
        }
    }
    walk(Jsoup.parse(html).body(), inBold = false)
    return words to bold
}

private val WHITESPACE = Regex("\\s+")
private val BOLD_TAGS = setOf("b", "strong")

/** The commentators of the pages when the commentaries pane shows none: the example's first pages'. */
private val DEFAULT_COMMENTATORS = listOf("רש\"י", "אבן עזרא", "רשב\"ם", "רמב\"ן", "ספורנו")

/** The targum the pages set beside the verse. */
private const val ONKELOS = "אונקלוס"

/** The daf's layout scale, pixels a point: a page is laid out once at it and drawn at the view's width. */
private const val LAYOUT_SCALE = 2f

/** The letters' size to a line's box, as the daf's setting has them. */
private const val SQUARE_LETTERS = 0.92f
private const val RASHI_LETTERS = 0.95f

/** The size words are measured at, to be scaled to the page's letters. */
private const val MEASURE_SIZE = 100f

/** The running head's letters, in points. */
private const val HEAD_SIZE = 9f

/** The widest a page is drawn, and the space between two pages. */
private val MAX_PAGE_WIDTH = 900.dp
private val PAGE_SPACING = 20.dp

/** A page as drawn: its text, and its three amudim laid out by the daf's setting. */
private class LaidPage(
    val text: MikraotPageText,
    val verse: AmudLayout,
    val targum: AmudLayout,
    val comments: AmudLayout,
)

/**
 * The Chumash [book] as pages of Mikraot Gedolot, scrolled down from the chapter of its verse [lineId] (the book's first
 * when null or not a verse), on to the next chapters as they are reached: Onkelos and the commentators the commentaries
 * pane shows for the verse ([selection] of those [listed] for it; the example's when none).
 */
@Composable
internal fun MikraotView(
    book: Book,
    lineId: Long?,
    selection: CommentatorSelection,
    listed: suspend (lineId: Long) -> Map<Long, String>,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val repository = graph.repository
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val gemaraCode by graph.appSettings.dafGemaraFontCodeFlow.collectAsState()
    val commentaryCode by graph.appSettings.dafCommentaryFontCodeFlow.collectAsState()
    val gemara = dafFamily(gemaraCode)
    val rashi = dafFamily(commentaryCode)
    val currentListed by rememberUpdatedState(listed)
    val pages = remember(book.id, selection, gemara, rashi) { mutableStateListOf<LaidPage>() }
    // Where the next chapter starts (a line index), null at the book's end; the commentators, read once
    var next by remember(book.id, selection, gemara, rashi) { mutableStateOf<Int?>(null) }
    var commentators by remember(book.id, selection) { mutableStateOf<List<MikraotCommentator>?>(null) }
    val listState = rememberLazyListState()

    suspend fun load(chapter: Chapter) {
        val widths = measured(measurer, gemara, rashi)
        val shown = commentators ?: commentatorsOf(repository, chapter.verses.first(), selection, currentListed).also { commentators = it }
        val texts = withContext(Dispatchers.Default) { mikraotPages(versesOf(repository, chapter.verses), shown, widths) }
        val base = DafSizes.of(MIKRAOT_PAGE_WIDTH)
        for (text in texts) {
            pages +=
                withContext(Dispatchers.Default) {
                    LaidPage(
                        text,
                        layoutAmud(
                            text.verse,
                            LAYOUT_SCALE,
                            measurer,
                            density,
                            gemara,
                            rashi,
                            base.copy(gemara = base.gemara * VERSE_SCALE),
                        ),
                        layoutAmud(text.targum, LAYOUT_SCALE, measurer, density, gemara, rashi, base),
                        layoutAmud(text.comments, LAYOUT_SCALE, measurer, density, gemara, rashi, base),
                    )
                }
        }
        next = chapter.next
    }

    LaunchedEffect(book.id, selection, gemara, rashi) {
        runSuspendCatching {
            val first = chapterOf(repository, book, lineId) ?: return@runSuspendCatching
            load(first)
            pages.indexOfFirst { lineId in it.text.verseIds }.takeIf { it > 0 }?.let { listState.scrollToItem(it + 1) }
        }
    }

    BoxWithConstraints(modifier.background(JewelTheme.globalColors.panelBackground)) {
        if (pages.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val pageWidth = min(maxWidth - 32.dp, MAX_PAGE_WIDTH)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PAGE_SPACING),
        ) {
            item { Box(Modifier.size(1.dp)) }
            itemsIndexed(pages, key = { index, _ -> index }) { _, page -> PageView(page, pageWidth, gemara, rashi) }
            next?.let { start ->
                item(key = "next-$start") {
                    LaunchedEffect(start) {
                        runSuspendCatching { chapterAt(repository, book, start)?.let { load(it) } ?: run { next = null } }
                    }
                    CircularProgressIndicator(Modifier.padding(16.dp))
                }
            }
        }
    }
}

/** A page as a book's, [width] wide: its running head, its verses, Onkelos, the commentators' names and their parts. */
@Composable
private fun PageView(
    page: LaidPage,
    width: Dp,
    gemara: FontFamily,
    rashi: FontFamily,
) {
    val density = LocalDensity.current
    val perPoint = with(density) { width.toPx() } / MIKRAOT_PAGE_WIDTH
    val height = with(density) { (MIKRAOT_PAGE_HEIGHT * perPoint).toDp() }
    val ink = JewelTheme.globalColors.text.normal
    val paper = if (JewelTheme.isDark) Color(PAPER_DARK) else Color.White
    var range by remember(page) { mutableStateOf<DafRange?>(null) }

    fun points(value: Float): Dp = with(density) { (value * perPoint).toDp() }
    Box(
        Modifier
            .size(width, height)
            .background(paper)
            .border(1.dp, JewelTheme.globalColors.borders.normal),
        // The page's points are counted from its left, as the daf's: its names placed from there whatever the direction
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        Text(
            page.text.head,
            Modifier.fillMaxWidth().padding(top = points(PAGE_TOP / 4)),
            fontSize = with(density) { (HEAD_SIZE * perPoint).toSp() },
            textAlign = TextAlign.Center,
            color = JewelTheme.globalColors.text.info,
        )
        val headingSize = with(density) { (DafSizes.of(MIKRAOT_PAGE_WIDTH).gemara * SQUARE_LETTERS * perPoint).toSp() }
        for (heading in page.text.headings) {
            Text(
                heading.text,
                Modifier.absoluteOffset(x = points(heading.left), y = points(heading.top)).width(points(heading.right - heading.left)),
                fontFamily = gemara,
                fontWeight = FontWeight.Bold,
                fontSize = headingSize,
                textAlign = TextAlign.Center,
                color = ink,
            )
        }
        val k = perPoint / LAYOUT_SCALE
        DafAmudText(page.verse, k, gemara, rashi, ink, Modifier.size(width, height))
        DafAmudText(page.targum, k, gemara, rashi, ink, Modifier.size(width, height))
        // On top, the commentaries: their words are the ones selected
        DafAmudText(
            page.comments,
            k,
            gemara,
            rashi,
            ink,
            Modifier.size(width, height),
            range = range,
            selectionColor = LocalTextSelectionColors.current.backgroundColor,
            onRange = { range = it },
        )
    }
}

private const val PAPER_DARK = 0xFF26282B

/** A chapter's verses, in order, and the line index the next chapter starts at (null at the book's end). */
private class Chapter(
    val verses: List<Line>,
    val next: Int?,
)

/** A verse's chapter: its reference but the verse ("בראשית א, א" is in "בראשית א"). */
private fun chapterKey(heRef: String): String = heRef.substringBeforeLast(',').trim()

private const val CHAPTER_WINDOW = 400

/** The chapter of [book]'s verse [lineId], or of its first verse when it is none. */
private suspend fun chapterOf(
    repository: SeforimRepository,
    book: Book,
    lineId: Long?,
): Chapter? {
    val line = lineId?.let { repository.getLine(it) }?.takeIf { it.bookId == book.id && !it.heRef.isNullOrBlank() }
    val around = line?.lineIndex ?: 0
    val lines = repository.getLines(book.id, (around - CHAPTER_WINDOW).coerceAtLeast(0), around + CHAPTER_WINDOW)
    val anchor = line ?: lines.firstOrNull { !it.heRef.isNullOrBlank() } ?: return null
    val key = chapterKey(anchor.heRef.orEmpty())
    val verses = lines.filter { !it.heRef.isNullOrBlank() && chapterKey(it.heRef.orEmpty()) == key }
    val after = verses.lastOrNull()?.lineIndex?.plus(1)
    return Chapter(verses, after?.takeIf { lines.any { l -> l.lineIndex >= it && !l.heRef.isNullOrBlank() } })
}

/** The chapter whose first verse is at line [start] or after it, or null past the book's end. */
private suspend fun chapterAt(
    repository: SeforimRepository,
    book: Book,
    start: Int,
): Chapter? {
    val first = repository.getLines(book.id, start, start + CHAPTER_WINDOW).firstOrNull { !it.heRef.isNullOrBlank() } ?: return null
    return chapterOf(repository, book, first.id)
}

/** The commentators of the pages: those the commentaries pane shows for [verse], else the example's that comment on it. */
private suspend fun commentatorsOf(
    repository: SeforimRepository,
    verse: Line,
    selection: CommentatorSelection,
    listed: suspend (Long) -> Map<Long, String>,
): List<MikraotCommentator> {
    val shown = runSuspendCatching { selection.shownOf(listed(verse.id)) }.getOrDefault(emptySet())
    val commentaries =
        repository
            .getCommentariesForLines(listOf(verse.id))
            .filter { it.link.connectionType == ConnectionType.COMMENTARY }
            .distinctBy { it.link.targetBookId }
            .map { MikraotCommentator(it.link.targetBookId, shortTitle(it.targetBookTitle)) }
    val picked = if (shown.isEmpty()) commentaries.filter { it.title in DEFAULT_COMMENTATORS } else commentaries.filter { it.id in shown }
    return picked.sortedBy { order(it.title) }
}

/** [lines] as the pages' verses: each one's words, Onkelos' on it and each commentator's comments on it. */
private suspend fun versesOf(
    repository: SeforimRepository,
    lines: List<Line>,
): List<MikraotVerse> {
    val links = repository.getCommentariesForLines(lines.map { it.id }).groupBy { it.link.sourceLineId }
    return lines.map { line ->
        val ofLine = links[line.id].orEmpty().sortedBy { it.link.targetLineIndex }
        val targum =
            ofLine
                .filter { it.link.connectionType == ConnectionType.TARGUM && ONKELOS in it.targetBookTitle }
                .flatMap { htmlWords(it.targetText).first }
        val comments =
            ofLine
                .filter { it.link.connectionType == ConnectionType.COMMENTARY }
                .groupBy { it.link.targetBookId }
                .mapValues { (_, ofBook) ->
                    val words = mutableListOf<String>()
                    val bold = mutableSetOf<Int>()
                    ofBook.forEach { comment ->
                        val (w, b) = htmlWords(comment.targetText)
                        bold += b.map { it + words.size }
                        words += w
                    }
                    MikraotComment(words, bold)
                }
        MikraotVerse(line.id, line.heRef.orEmpty(), htmlWords(line.content).first, targum, comments)
    }
}

/** A commentary's name without the book it is on: "רש\"י על בראשית" is "רש\"י". */
private fun shortTitle(title: String): String = title.substringBefore(" על ").trim()

/** The example's commentators first, in its order, then the others as listed. */
private fun order(title: String): Int = DEFAULT_COMMENTATORS.indexOf(title).let { if (it < 0) DEFAULT_COMMENTATORS.size else it }

/** Words measured with [measurer] in the daf's letters, at the page's sizes. */
private fun measured(
    measurer: TextMeasurer,
    gemara: FontFamily,
    rashi: FontFamily,
): WordWidths {
    val sizes = DafSizes.of(MIKRAOT_PAGE_WIDTH)
    val one = Density(1f)
    val cache = HashMap<Pair<String, Boolean>, Float>()

    fun at100(
        text: String,
        square: Boolean,
    ): Float =
        measurer
            .measure(
                AnnotatedString(text),
                TextStyle(fontFamily = if (square) gemara else rashi, fontSize = MEASURE_SIZE.sp),
                density = one,
            ).size.width
            .toFloat()

    fun letters(square: Boolean) = if (square) sizes.gemara * SQUARE_LETTERS else sizes.commentary * RASHI_LETTERS
    return object : WordWidths {
        override fun of(
            word: String,
            square: Boolean,
        ): Float = cache.getOrPut(word to square) { at100(word, square) / MEASURE_SIZE * letters(square) }

        override fun space(square: Boolean): Float =
            cache.getOrPut(" " to square) { (at100("א א", square) - 2 * at100("א", square)) / MEASURE_SIZE * letters(square) }
    }
}
