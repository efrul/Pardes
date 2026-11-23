package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.features.bookcontent.segments.SelectedPassage
import io.github.kdroidfilter.seforimapp.features.bookcontent.segments.commentAnchorIndex
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): a book's daf's units laid out (BookDafLayout) and where its lines are on
// them, for its list (BookDafView): a line brought into view, the line read at the view's top, the place kept at a zoom.

/**
 * A page of a book's daf as shown: its amud and its [layout], its running [head] (the references it holds; those of the
 * page before it when it has commentaries alone, [isContinued]), the [rules] drawn between its parts, the passages
 * Zayit's anchors mark on it, where each library line is printed first ([tops], points down the page), and the text's
 * printed lines top down ([textTops]: each one's top and the first line of the text that begins on it).
 */
internal class LaidPage(
    val amud: DafAmud,
    val layout: AmudLayout,
    val head: String,
    val passages: Map<Long, List<Pair<IntRange, Set<Long>>>>,
    val tops: Map<Long, Float>,
    val textTops: List<Pair<Float, Long>>,
    val rules: List<PageRule> = emptyList(),
    val isContinued: Boolean = false,
) {
    val height: Float get() = amud.block[3] - amud.block[1]

    /** The page under the head of the page before it, whose text its commentaries go on from. */
    fun continuing(head: String) = LaidPage(amud, layout, head, passages, tops, textTops, rules, isContinued = true)
}

/** The tab's line, selection and place, as a book's daf takes them from the text view (DafView's). */
internal class DafTab(
    val lineId: Long?,
    val selectedLineIds: Set<Long>,
    val isTocEntrySelection: Boolean,
    val selectedPassage: SelectedPassage?,
    val scrollToLineTimestamp: Long,
    val openAt: Long?,
)

/** A line this near a page's top (points: two of the text's lines) opens it: brought into view, the page is, with its head. */
private const val OPENING = 26f

/**
 * What frames each page of a unit, in pixels: its running head above it, the room below it; and how many pages stand
 * side by side in a row ([columns]: two as a book open).
 */
internal class PageFrame(
    val head: Float,
    val gap: Float,
    val columns: Int = 1,
)

/** A comment of a book's daf: its line, the text line it is set with, and its commentator's book. */
internal data class DafComment(
    val id: Long,
    val lineId: Long,
    val bookId: Long,
)

/**
 * A unit laid out: its pages, each commentator's comments on it as read ([comments], the arrows step through them), and
 * the [setter] it was laid out by, which the pages are set otherwise than once it is no longer the daf's.
 */
internal class LaidUnit(
    val pages: List<LaidPage>,
    val comments: List<List<DafComment>>,
    val setter: UnitSetter,
) {
    /** The pages in rows of [columns], as the unit's item shows them: each row's pages, the one read first first. */
    fun rows(columns: Int): List<List<LaidPage>> = pages.chunked(columns.coerceAtLeast(1))

    // The pixels down the unit's item to the top of its row [row]: the rows above it, each as tall as its tallest page
    private fun rowTop(
        row: Int,
        perPoint: Float,
        frame: PageFrame,
    ): Float =
        rows(frame.columns)
            .take(row.coerceAtLeast(0))
            .sumOf { pages -> (frame.head + pages.maxOf { it.height } * perPoint + frame.gap).toDouble() }
            .toFloat()

    /** The pixels down the unit's item to [points] down its page [page], [perPoint] pixels a point. */
    fun offset(
        page: Int,
        points: Float,
        perPoint: Float,
        frame: PageFrame,
    ): Float = rowTop(page.coerceIn(0, pages.size) / frame.columns, perPoint, frame) + frame.head + points * perPoint

    /** The unit's item's height in pixels, [perPoint] pixels a point: its rows, each with its head and the room below it. */
    fun height(
        perPoint: Float,
        frame: PageFrame,
    ): Float = rowTop(rows(frame.columns).size, perPoint, frame)

    /** The pixels down the unit's item to where the library line [lineId] is printed first, or null when not on it. */
    fun offsetOf(
        lineId: Long,
        perPoint: Float,
        frame: PageFrame,
    ): Float? {
        val page = pages.indexOfFirst { lineId in it.tops }
        if (page < 0) return null
        return offset(page, pages[page].tops.getValue(lineId), perPoint, frame)
    }

    /**
     * The page at [offset] pixels down the unit's item (of a row of pages, the one read first), and the points down it
     * (none above it, in its head).
     */
    fun placeAt(
        offset: Float,
        perPoint: Float,
        frame: PageFrame,
    ): Pair<Int, Float> {
        val rows = rows(frame.columns)
        var y = 0f
        rows.forEachIndexed { i, row ->
            val bottom = y + frame.head + row.maxOf { it.height } * perPoint + frame.gap
            if (offset < bottom || i == rows.lastIndex) return i * frame.columns to ((offset - y - frame.head) / perPoint).coerceAtLeast(0f)
            y = bottom
        }
        return 0 to 0f
    }

    /** The text line read at [points] down the page [page]: the first printed there or below, or on a later page. */
    fun textAt(
        page: Int,
        points: Float,
    ): Long? {
        pages
            .getOrNull(page)
            ?.textTops
            ?.firstOrNull { it.first >= points }
            ?.let { return it.second }
        return pages.drop(page + 1).firstNotNullOfOrNull { it.textTops.firstOrNull()?.second }
    }

    /** The comment read after [commentId] in its commentary, or before it ([forward] false); null at either end. */
    fun next(
        commentId: Long,
        forward: Boolean,
    ): DafComment? {
        for (list in comments) {
            val i = list.indexOfFirst { it.id == commentId }
            if (i >= 0) return list.getOrNull(if (forward) i + 1 else i - 1)
        }
        return null
    }

    /**
     * The pixels down the unit's item to the top of the page the library line [lineId] begins, with its head, when the
     * line is printed in the page's first lines ([OPENING]); null for a line further down its page, or on none.
     */
    fun pageOpenedBy(
        lineId: Long,
        perPoint: Float,
        frame: PageFrame,
    ): Float? {
        val page = pages.indexOfFirst { lineId in it.tops }
        if (page < 0 || pages[page].tops.getValue(lineId) > OPENING) return null
        return offset(page, 0f, perPoint, frame) - frame.head
    }

    /** Whether [commentId] is one of the unit's comments. */
    fun hasComment(commentId: Long): Boolean = comments.any { list -> list.any { it.id == commentId } }
}

/** The daf's letters: the text's and the names' square ones, the commentaries' Rashi script, measured with [measurer]. */
internal class DafLetters(
    val measurer: TextMeasurer,
    val density: Density,
    val gemara: FontFamily,
    val rashi: FontFamily,
)

/** The size words are measured at, to be scaled to the page's letters. */
private const val MEASURE_SIZE = 100f

/**
 * Words measured in [letters] at a book's daf's [sizes] (its lines' boxes), each once: as the daf's setting sets their
 * lines' letters.
 */
private fun measuredWidths(
    letters: DafLetters,
    sizes: DafSizes,
): WordWidths {
    val one = Density(1f)
    val cache = HashMap<Triple<String, Boolean, Boolean>, Float>()

    fun at100(
        text: String,
        square: Boolean,
        bold: Boolean,
    ): Float {
        val style =
            TextStyle(
                fontFamily = if (square) letters.gemara else letters.rashi,
                fontSize = MEASURE_SIZE.sp,
                fontWeight = if (bold) FontWeight.Bold else null,
            )
        return letters.measurer
            .measure(AnnotatedString(text), style, density = one)
            .size.width
            .toFloat()
    }

    fun size(square: Boolean) = if (square) sizes.gemara * GEMARA_SIZE else sizes.commentary * RASHI_SIZE
    return object : WordWidths {
        override fun of(
            word: String,
            square: Boolean,
            bold: Boolean,
        ): Float = cache.getOrPut(Triple(word, square, bold)) { at100(word, square, bold) / MEASURE_SIZE * size(square) }

        override fun space(square: Boolean): Float =
            cache.getOrPut(Triple(" ", square, false)) {
                (at100("א א", square, false) - 2 * at100("א", square, false)) / MEASURE_SIZE * size(square)
            }
    }
}

/**
 * The units of [book] laid out, one at a time (their reads and the commentators' order as they come): their text and
 * the [commentators] shown with it, from the library, in [letters] and the reader's [style]; their words with the
 * [diacritics] the text shows.
 */
internal class UnitSetter(
    private val repository: SeforimRepository,
    private val book: Book,
    private val commentators: DafCommentators,
    diacritics: DiacriticsMode,
    /** Each commentator's place among those met, kept while the book is shown: its band, or its side. */
    ranks: MutableMap<Long, Int>,
    private val letters: DafLetters,
    style: BookDafStyle = BookDafStyle(),
) {
    // In bands, the rules' measures; around the text, the letters of the Talmud's block, which the page is as wide as
    private val bands = if (style.layout == DafStyle.Bands) BandsMeasures(style, style.runOn ?: book.isTanakh) else null
    private val sizes = bands?.let { DafSizes(it.text.box, it.commentary.box) } ?: DafSizes.of(BOOK_DAF_WIDTH)
    private val look = DafUnitLook(diacritics, commentAnchorIndex(book.id), ranks, targum = bands != null && bands.sideWidth > 0f)
    private val widths = measuredWidths(letters, sizes)
    private val mutex = Mutex()

    /** A page's usual height to its width, for a whole page fitted in the view. */
    val aspect: Float = (bands?.height ?: BOOK_DAF_HEIGHT) / BOOK_DAF_WIDTH

    /** The unit [unit] laid out, its pages named after it. */
    suspend fun lay(unit: DafUnit): LaidUnit =
        mutex.withLock {
            val text = loadDafUnit(repository, book.id, unit, commentators.selection, commentators.connections, look)
            withContext(Dispatchers.Default) {
                val pages =
                    bands?.let { DafBandsSetter(widths, text.text, text.commentaries, text.targum, it).pages() }
                        ?: dafPagesAround(text.text, text.commentaries, widths)
                val laid = ArrayList<LaidPage>()
                pages.forEachIndexed { i, page ->
                    ensureActive()
                    val set = laidPage(page, "${book.id}/${unit.first}/$i", text)
                    // A page of commentaries alone goes on from the text of the page before it
                    laid += if (set.head.isEmpty() && laid.isNotEmpty()) set.continuing(laid.last().head) else set
                }
                val comments =
                    text.commentaries.map { c ->
                        text.text.flatMap { segment ->
                            c.comments[segment.lineId].orEmpty().map { DafComment(it.lineId, segment.lineId, c.bookId) }
                        }
                    }
                LaidUnit(laid, comments, this@UnitSetter)
            }
        }

    private fun laidPage(
        page: SetPage,
        name: String,
        text: DafUnitText,
    ): LaidPage {
        val amud = page.amud(name)
        val openings = page.openings.takeIf { it.isNotEmpty() }?.let { DafOpenings(it, DIBBUR_SIZE) }
        val layout = layoutAmud(amud, LAYOUT_SCALE, letters.measurer, letters.density, letters.gemara, letters.rashi, sizes, openings)
        val tops = HashMap<Long, Float>()
        val textTops = ArrayList<Pair<Float, Long>>()
        for (line in amud.lines) {
            val top = line.b[1]
            for (id in line.lineIds) tops.putIfAbsent(id, top)
            if (line.isGemara &&
                line.lineIds.isNotEmpty()
            ) {
                textTops += top to (line.lineIds.firstOrNull { tops[it] == top } ?: line.lineIds.first())
            }
        }
        val ids = amud.lines.filter { it.isGemara }.flatMapTo(LinkedHashSet()) { it.lineIds }
        val refs = ids.mapNotNull { text.refs[it] }
        val head =
            if (refs.isEmpty()) {
                text.text
                    .firstOrNull { it.lineId in ids }
                    ?.words
                    ?.joinToString(" ")
                    .orEmpty()
            } else {
                refRange(refs.first(), refs.last())
            }
        return LaidPage(amud, layout, head, text.passages.filterKeys { it in ids }, tops, textTops.sortedBy { it.first }, page.rules)
    }
}

/** The references [first] to [last] as one: "בראשית א, א–ה", "בראשית א, כט – ב, ג" across a chapter. */
internal fun refRange(
    first: String,
    last: String,
): String {
    if (first == last) return first
    val chapter = first.substringBeforeLast(", ", "")
    if (chapter.isNotEmpty() && chapter == last.substringBeforeLast(", ", "")) return "$first–${last.substringAfterLast(", ")}"
    val common = first.commonPrefixWith(last).substringBeforeLast(' ', "")
    return "$first – ${last.removePrefix(common).trim()}"
}

/** The units of [book] (BookDafSource), from the lines its table of contents' entries begin at. */
internal suspend fun unitsOf(
    repository: SeforimRepository,
    book: Book,
): List<DafUnit> {
    val ids = repository.getTocEntriesForBook(book.id).mapNotNull { it.lineId }
    val starts = repository.getLinesByIds(ids).map { it.lineIndex }
    return dafUnits(starts, book.totalLines)
}
