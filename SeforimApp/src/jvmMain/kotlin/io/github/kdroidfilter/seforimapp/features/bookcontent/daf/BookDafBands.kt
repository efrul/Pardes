package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.math.ceil
import kotlin.math.floor

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): a book's daf set in bands (DafStyle.Bands), by the user's rules for a
// page of text and commentaries (docs/BOOK_DAF_RULES.md; the rules named here are its "כללי העימוד"): pages of one
// size, the text at the top with its targum beside it, then each commentator in a band of its own under its name, in
// balanced columns. A commentator's comments are one run of words that goes on from page to page by itself, never
// ahead of the text it is on. Lines are broken here, by the words' measured widths, so a line count is exact and the
// rules' search for the most words that fit is one walk down the lines.

/** A line's pitch to its letters: the text's, the targum's beside it, a commentary's, a commentator's name's. */
private const val TEXT_PITCH = 1.6f
private const val SIDE_PITCH = 1.62f
private const val COMMENTARY_PITCH = 1.52f
private const val NAME_PITCH = 1.385f

/** The targum's letters to the text's, and a commentator's name's to its commentary's. */
private const val SIDE_SIZE = 0.64f
private const val NAME_SIZE = 1.04f

/** The words that open a comment (its dibbur hamatchil), in the text's square letters, to their line's letters. */
internal const val DIBBUR_SIZE = 0.94f

/**
 * In the rules' unit: between the text and its targum and between a band's columns; under the text; above a band's
 * rule, between the rule and the name, under the name; and how far down its column the targum begins.
 */
private const val COLUMN_GAP = 20f
private const val TEXT_FOOT = 6f
private const val BAND_GAP = 8f
private const val RULE_PAD = 4f
private const val NAME_FOOT = 2f
private const val SIDE_INSET = 4f

/** Above a heading of the text that is not at the page's top, to a line of the text. */
private const val HEADING_SPACE = 0.4f

/** Rule 7: a band of fewer lines a column than this waits for the next page. */
private const val MIN_ROWS = 2

/** Rule 8: a page with more than this much of it empty takes one more line of the text. */
private const val FILL = 0.15f

/** Rule 9: the lines of commentary left free at a page's bottom. */
private const val SAFETY_LINES = 1

/** A line of the text that ends with this closes its paragraph (a parashah petuchah), where the text runs on. */
internal const val PETUCHAH = "{פ}"

/** A column's letters: their [size] in points, a line's [box] (which they take [share] of) and its [pitch]. */
internal class Letters(
    val size: Float,
    share: Float,
    pitchRatio: Float,
) {
    val box = size / share
    val pitch = size * pitchRatio
}

/**
 * The measures a page of bands is set by, in the page's points ([BOOK_DAF_WIDTH] wide), from the reader's [style];
 * [runOn]: the text's lines run on in one paragraph, as verses do.
 */
internal class BandsMeasures(
    style: BookDafStyle,
    val runOn: Boolean,
) {
    private val unit = BOOK_DAF_WIDTH / BookDafStyle.RULES_WIDTH
    val width = BOOK_DAF_WIDTH
    val height = BOOK_DAF_WIDTH * style.aspect
    val columns = style.columns
    val text = Letters(style.textSize * unit, GEMARA_SIZE, TEXT_PITCH)
    val side = Letters(style.textSize * SIDE_SIZE * unit, GEMARA_SIZE, SIDE_PITCH)
    val commentary = Letters(style.commentarySize * unit, RASHI_SIZE, COMMENTARY_PITCH)

    /** Whether a comment opens in the text's square letters: where the commentaries are in Rashi script. */
    val squareOpenings = !style.squareCommentary
    val name = Letters(style.commentarySize * NAME_SIZE * unit, GEMARA_SIZE, NAME_PITCH)
    val gap = COLUMN_GAP * unit

    /** The targum's column, at the page's left; 0 with none beside the text. */
    val sideWidth = if (style.sideShare > 0f) width * style.sideShare - gap / 2 else 0f
    val sideInset = SIDE_INSET * unit
    val textFoot = TEXT_FOOT * unit
    val bandGap = BAND_GAP * unit
    val rulePad = RULE_PAD * unit
    val nameFoot = NAME_FOOT * unit

    /** A band's column's width, and what a band takes above its lines: its rule and its commentator's name. */
    val columnWidth = (width - gap * (columns - 1)) / columns
    val bandHead = bandGap + rulePad + name.pitch + nameFoot
}

/** A rule drawn on a page (points): above a band, between two columns. */
internal class PageRule(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
)

/**
 * Words set in lines [room] wide, in letters [scale] times the page's: a paragraph, the targum, a commentator's run,
 * whose comments open in the text's letters, [leadScale] times the page's.
 */
private class WordRun(
    val words: List<SetWord>,
    widths: WordWidths,
    square: Boolean,
    scale: Float,
    private val room: Float,
    leadScale: Float = scale,
) {
    private val wide =
        FloatArray(words.size) { i ->
            val word = words[i]
            if (word.lead) widths.of(word.text, true, true) * leadScale else widths.of(word.text, square, word.bold) * scale
        }
    private val space = widths.space(square) * scale

    /** Where the line that begins at the word [from] ends (the word after its last), no further than [until]. */
    fun lineEnd(
        from: Int,
        until: Int,
    ): Int {
        var end = from + 1
        var width = wide[from]
        while (end < until && width + space + wide[end] <= room) {
            width += space + wide[end]
            end++
        }
        return end
    }

    /** The lines of the words [from] until [until], each its words. */
    fun lines(
        from: Int,
        until: Int,
    ): List<IntRange> {
        val lines = ArrayList<IntRange>()
        var at = from
        while (at < until) {
            val end = lineEnd(at, until)
            lines += at until end
            at = end
        }
        return lines
    }

    /** How many lines the words [from] until [until] are, counted no further than [most]. */
    fun lineCount(
        from: Int,
        until: Int,
        most: Int,
    ): Int {
        var count = 0
        var at = from
        while (at < until && count < most) {
            at = lineEnd(at, until)
            count++
        }
        return count
    }

    /** Where [count] lines from the word [from] end, no further than [until]. */
    fun endOf(
        from: Int,
        until: Int,
        count: Int,
    ): Int {
        var at = from
        var left = count
        while (at < until && left-- > 0) at = lineEnd(at, until)
        return at
    }

    /** The width of a line of the words [range]. */
    fun width(range: IntRange): Float {
        var width = space * (range.last - range.first)
        for (i in range) width += wide[i]
        return width
    }
}

/** A page being set from its top down: its lines, its rules, the words that open a comment, where its next part goes. */
private class BandsPage {
    val lines = ArrayList<DafLine>()
    val rules = ArrayList<PageRule>()
    val openings = HashMap<Int, Set<Int>>()
    var y = 0f

    /** A line of a commentary's column: its words that open a comment are noted. */
    fun add(
        words: List<SetWord>,
        box: List<Float>,
    ) {
        val opening = words.indices.filterTo(HashSet()) { words[it].lead }
        if (opening.isNotEmpty()) openings[lines.size] = opening
        lines += dafLine(RIGHT_COLUMN, words, box)
    }
}

/** How a paragraph's last line is set: across as the others, from the right when short, or in the middle. */
private enum class LastLine { Across, Short, Centred }

/**
 * A unit of a book ([text], its lines in order) set in pages of bands: its [targum] beside its text, each of
 * [commentaries] (in the order of their bands, which is their turn for a page's room too, rule 5) under it.
 */
internal class DafBandsSetter(
    private val widths: WordWidths,
    private val text: List<DafSegment>,
    private val commentaries: List<DafCommentary>,
    private val targum: DafTargum?,
    private val measures: BandsMeasures,
) {
    private val width = measures.width

    // Each commentator's comments as one run of words, and for each line of the text how many of them are on it or
    // on a line before it: what may be set once that line is (rule 1's "never before its verse")
    private val runs =
        commentaries.map { c ->
            val opening = measures.commentary.size * DIBBUR_SIZE / measures.text.size
            WordRun(text.flatMap { commentWords(c, it) }, widths, false, 1f, measures.columnWidth * LINE_FILL, opening)
        }
    private val ends =
        commentaries.map { c ->
            var count = 0
            IntArray(text.size) { i -> (count + commentWords(c, text[i]).size).also { count = it } }
        }

    // A commentator's comments on a line of the text: each one's first bold words open it, in the text's letters
    private fun commentWords(
        commentary: DafCommentary,
        line: DafSegment,
    ) = commentary.comments[line.lineId].orEmpty().flatMap { comment ->
        val opening =
            if (measures.squareOpenings) {
                comment.words.indices
                    .takeWhile { it in comment.bold }
                    .size
            } else {
                0
            }
        comment.words.mapIndexed { i, word -> SetWord(word, comment.lineId, i in comment.bold, lead = i < opening) }
    }

    // The words of a commentator set once the text's line [line] is; none before the first
    private fun endAt(
        commentary: Int,
        line: Int,
    ) = if (line < 0) 0 else ends[commentary][line]

    /**
     * The unit's pages, in order; [beforeEachPage] is called before each is set (a chapter with all its commentators is
     * thousands of pages, let go of when the pages are set otherwise meanwhile).
     */
    fun pages(beforeEachPage: () -> Unit = {}): List<SetPage> {
        val pages = ArrayList<SetPage>()
        val at = IntArray(runs.size)
        var next = 0
        while (next < text.size || at.indices.any { at[it] < runs[it].words.size }) {
            beforeEachPage()
            val set = next + at.sum()
            val last = lastLine(next, at)
            val page = BandsPage()
            if (last >= next) setText(next, last, page)
            val taken = setBands(at, maxOf(last, next - 1), page)
            // Nothing new on the page (its room too small for a band's least): the first band waiting is set all the same
            if (last < next && !taken) force(at, page)
            next = maxOf(next, last + 1)
            // A line or a word at least on each page: the unit ends
            check(next + at.sum() > set) { "a page of bands took nothing in" }
            val isLast = next >= text.size && at.indices.all { at[it] >= runs[it].words.size }
            // Pages of one size (a line taller than a page makes its own longer); a unit's last ends where its lines do
            pages += SetPage(page.lines, if (isLast) page.y else maxOf(measures.height, page.y), page.rules, page.openings)
        }
        return pages
    }

    // A page's room: its height less the lines left free at its bottom (rule 9)
    private val room = measures.height - measures.commentary.pitch * SAFETY_LINES

    // A band of this many lines is taller than a page's room: none is counted further (a commentator's comments waiting
    // may be a chapter's)
    private val mostLines = (floor(room / measures.commentary.pitch).toInt() + 2) * measures.columns

    /**
     * The last line of the text a page that begins at the line [first] takes, with each commentator's run at [at]: line
     * after line while the text, its targum and all the comments waiting up to it fit (rule 2), one at least while lines
     * are left (rule 3), one more when much of the page would be left empty (rule 8); a heading is not a page's last line.
     * [first] less one with no line left.
     */
    private fun lastLine(
        first: Int,
        at: IntArray,
    ): Int {
        if (first >= text.size) return first - 1
        val heights = HashMap<Int, Float>()

        fun textHeight(last: Int) = if (last < first) 0f else heights.getOrPut(last) { BandsPage().also { setText(first, last, it) }.y }

        // The bands counted while they fit: with many commentators waiting, the first few are more than the page's room
        fun used(last: Int): Float {
            var height = textHeight(last)
            for (c in runs.indices) {
                if (height > room) break
                height += bandHeight(c, at[c], endAt(c, last))
            }
            return height
        }
        var last = first - 1
        while (last < text.lastIndex && used(last + 1) <= room) last++
        if (last < first) last = first
        if (last < text.lastIndex && fills(room - used(last), textHeight(last + 1) - textHeight(last))) last++
        return offHeading(first, last)
    }

    // Rule 8: whether a page with [spare] room left takes one more line of the text, [oneMore] tall, its comments to go
    // on from the page's bottom
    private fun fills(
        spare: Float,
        oneMore: Float,
    ) = spare > FILL * room && oneMore < spare - MIN_ROWS * measures.commentary.pitch

    // The last line of a page that begins at [first] and would end at [last], when that is a heading: the line before
    // it, or, a heading alone, the lines after it up to one that is none
    private fun offHeading(
        first: Int,
        last: Int,
    ): Int {
        var end = last
        while (end > first && end < text.lastIndex && text[end].isHeading) end--
        while (end < text.lastIndex && text[end].isHeading) end++
        return end
    }

    // The height of a commentator's band of the words [from] until [until]; none with no words
    private fun bandHeight(
        commentary: Int,
        from: Int,
        until: Int,
    ): Float {
        if (until <= from) return 0f
        val lines = runs[commentary].lineCount(from, until, mostLines)
        return measures.bandHead + ceil(lines / measures.columns.toFloat()) * measures.commentary.pitch
    }

    /**
     * The bands of a page whose text ends at the line [last]: each commentator in turn takes as many of its words waiting
     * as the room left holds (rule 5), none when that is fewer lines a column than a band's least (rule 7). Whether any
     * took words.
     */
    private fun setBands(
        at: IntArray,
        last: Int,
        page: BandsPage,
    ): Boolean {
        var taken = false
        for (c in runs.indices) {
            val most = endAt(c, last)
            val rows = floor((room - page.y - measures.bandHead) / measures.commentary.pitch).toInt()
            if (most <= at[c] || rows < MIN_ROWS) continue
            val until = runs[c].endOf(at[c], most, rows * measures.columns)
            setBand(c, at[c], until, page)
            at[c] = until
            taken = true
        }
        return taken
    }

    // The first commentator with words waiting set in a band of the least lines, whatever the page's room
    private fun force(
        at: IntArray,
        page: BandsPage,
    ) {
        val c = at.indices.first { at[it] < runs[it].words.size }
        val until = runs[c].endOf(at[c], runs[c].words.size, MIN_ROWS * measures.columns)
        setBand(c, at[c], until, page)
        at[c] = until
    }

    /**
     * The text's lines [first] to [last] at the page's top: its headings alone in the middle, the others in paragraphs
     * with their targum beside them, each heading beginning a part of its own.
     */
    private fun setText(
        first: Int,
        last: Int,
        page: BandsPage,
    ) {
        var i = first
        while (i <= last) {
            if (text[i].isHeading) {
                if (page.lines.isNotEmpty()) page.y += measures.text.pitch * HEADING_SPACE
                page.y = paragraph(setWords(text[i]), TEXT_COLUMN, measures.text, 0f to width, page.y, LastLine.Centred, page)
                i++
                continue
            }
            val from = i
            while (i <= last && !text[i].isHeading) i++
            setPart(from, i - 1, page)
        }
        page.y += measures.textFoot
    }

    // The text's lines [first] to [last], none a heading, with their targum beside them at the page's left
    private fun setPart(
        first: Int,
        last: Int,
        page: BandsPage,
    ) {
        val beside =
            if (measures.sideWidth >
                0f
            ) {
                (first..last).flatMap { targum?.verses?.get(text[it].lineId).orEmpty() }.flatMap(::setWords)
            } else {
                emptyList()
            }
        val column = (if (beside.isEmpty()) 0f else measures.sideWidth + measures.gap) to width
        val top = page.y
        var bottom = top
        for ((words, end) in paragraphs(first, last)) bottom = paragraph(words, TEXT_COLUMN, measures.text, column, bottom, end, page)
        if (beside.isNotEmpty()) {
            val side = 0f to measures.sideWidth
            val sideBottom = paragraph(beside, TARGUM_COLUMN, measures.side, side, top + measures.sideInset, LastLine.Centred, page)
            bottom = maxOf(bottom, sideBottom)
            val x = measures.sideWidth + measures.gap / 2
            page.rules += PageRule(x, top, x, bottom)
        }
        page.y = bottom
    }

    /**
     * The paragraphs of the text's lines [first] to [last], each with how its last line is set: a line a paragraph, or,
     * where the text runs on, the lines up to one that closes its paragraph, whose last line is short; one left open, as a
     * page's last is, ends in the middle.
     */
    private fun paragraphs(
        first: Int,
        last: Int,
    ): List<Pair<List<SetWord>, LastLine>> {
        if (!measures.runOn) return (first..last).map { setWords(text[it]) to LastLine.Short }
        val paragraphs = ArrayList<Pair<List<SetWord>, LastLine>>()
        var words = ArrayList<SetWord>()
        for (i in first..last) {
            words += setWords(text[i])
            if (text[i].words.lastOrNull() == PETUCHAH) {
                paragraphs += words to LastLine.Short
                words = ArrayList()
            }
        }
        if (words.isNotEmpty()) paragraphs += words to LastLine.Centred
        return paragraphs
    }

    /**
     * A band: its rule, its commentator's name, then the words [from] until [until] of its run in lines, the first of them
     * in the right column and as many in each column (the odd ones in the first). A line is set across its column, but
     * the band's last when a comment ends with it.
     */
    private fun setBand(
        commentary: Int,
        from: Int,
        until: Int,
        page: BandsPage,
    ) {
        val run = runs[commentary]
        page.y += measures.bandGap
        page.rules += PageRule(0f, page.y, width, page.y)
        page.y += measures.rulePad
        paragraph(nameWords(commentaries[commentary].name), NAME_COLUMN, measures.name, 0f to width, page.y, LastLine.Centred, page)
        page.y += measures.name.pitch + measures.nameFoot
        val lines = run.lines(from, until)
        val rows = ceil(lines.size / measures.columns.toFloat()).toInt()
        val top = page.y
        val letters = measures.commentary
        val closes = until == run.words.size || run.words[until].lineId != run.words[until - 1].lineId
        lines.forEachIndexed { i, range ->
            val column = i / rows
            val x1 = width - column * (measures.columnWidth + measures.gap)
            val x0 = x1 - measures.columnWidth
            val isShort = closes && i == lines.lastIndex
            val (left, right) = lineSpan(x0, x1, run.width(range), centred = false, isEnd = isShort)
            val lineTop = top + (i % rows) * letters.pitch + (letters.pitch - letters.box) / 2
            page.add(run.words.subList(range.first, range.last + 1), listOf(left, lineTop, right, lineTop + letters.box))
        }
        page.y = top + rows * letters.pitch
        for (column in 1 until ceil(lines.size / rows.toFloat()).toInt()) {
            val x = width - column * (measures.columnWidth + measures.gap) + measures.gap / 2
            page.rules += PageRule(x, top, x, page.y)
        }
    }

    /**
     * [words] in lines of [column] (its left and right) from [top] down, in [letters]; its last line as [end] says. Where
     * its next line would go.
     */
    private fun paragraph(
        words: List<SetWord>,
        code: String,
        letters: Letters,
        column: Pair<Float, Float>,
        top: Float,
        end: LastLine,
        page: BandsPage,
    ): Float {
        if (words.isEmpty()) return top
        val (x0, x1) = column
        val run = WordRun(words, widths, square = true, scale = letters.size / measures.text.size, room = (x1 - x0) * LINE_FILL)
        var lineTop = top
        val lines = run.lines(0, words.size)
        lines.forEachIndexed { i, range ->
            val isLast = i == lines.lastIndex
            val (left, right) =
                lineSpan(x0, x1, run.width(range), centred = isLast && end == LastLine.Centred, isEnd = isLast && end == LastLine.Short)
            val boxTop = lineTop + (letters.pitch - letters.box) / 2
            page.lines += dafLine(code, words.subList(range.first, range.last + 1), listOf(left, boxTop, right, boxTop + letters.box))
            lineTop += letters.pitch
        }
        return lineTop
    }
}
