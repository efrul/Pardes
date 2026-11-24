package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.math.ceil
import kotlin.math.floor

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): the parts of a page of bands as they are set, each from where the page
// is down (BookDafBands decides what a page takes): the text's pieces at its top with what stands beside them (their
// targum, or the first lines of the commentator put there), then a commentator's band under its name, in its own
// letters and columns.

/** Above a heading of the text that is not at the page's top, to a line of the text. */
private const val HEADING_SPACE = 0.4f

/** The commentator beside the text has this many lines there at least, where it has them: none alone under its name. */
private const val SIDE_ROWS = 2

/**
 * A page being set from its top down: its lines, its rules, the words that open a comment, its commentators' names,
 * and where its next part goes.
 */
internal class BandsPage {
    val lines = ArrayList<DafLine>()
    val rules = ArrayList<PageRule>()
    val openings = HashMap<Int, Set<Int>>()
    val heads = ArrayList<BandHead>()
    var y = 0f

    /** The name just set is the commentator [bookId]'s, in the line [y] across [x] ([isBeside]: beside the text). */
    fun head(
        bookId: Long,
        x: Pair<Float, Float>,
        y: Pair<Float, Float>,
        isBeside: Boolean = false,
    ) {
        val name = lines.last().b
        heads += BandHead(bookId, x.first, y.first, x.second, y.second, name[0], name[2], isBeside)
    }

    /** A printed line of a band's [column]: its words that open a comment are noted. */
    fun add(
        column: String,
        words: List<SetWord>,
        box: List<Float>,
    ) {
        val opening = words.indices.filterTo(HashSet()) { words[it].lead }
        if (opening.isNotEmpty()) openings[lines.size] = opening
        lines += dafLine(column, words, box)
    }
}

/**
 * A commentator's band: its place among the bands, its book and [name], its [letters], its comments as one [run] of
 * words, and how many of them are on each piece of the text or on one before it ([ends]): what may be set once that
 * piece is (the rules' "never before its verse"). [beside]: the commentator put beside the text, its run in the lines of
 * the column there, where its first lines on a page stand; the others go on under the text, in its band.
 */
internal class Band(
    val index: Int,
    val bookId: Long,
    val name: String,
    val letters: BandLetters,
    val run: WordRun,
    val ends: IntArray,
    val beside: WordRun? = null,
)

/** Where a band's lines go: from [top] down, [rows] to a column, the first column's right at [right], each [width] wide. */
private class Columns(
    val top: Float,
    val rows: Int,
    val right: Float,
    val width: Float,
)

/** A column a paragraph is set in: its lines' code and letters, its left and right, and its letters to the page's. */
private class Column(
    val code: String,
    val letters: Letters,
    val x0: Float,
    val x1: Float,
    val square: Boolean,
    val scale: Float,
)

/**
 * What sets the parts of a unit's pages of bands: the text's [pieces], and what stands beside them: their [targum] (by
 * the piece each part of it is set with), or the commentator put in its place ([side], its band). [fixedColumn]: a
 * paragraph may be cut between pages, so the text's column is one width on every page, beside the side's column even
 * where that is empty.
 */
internal class BandsPrinter(
    private val widths: WordWidths,
    private val measures: BandsMeasures,
    private val pieces: List<TextPiece>,
    private val targum: Map<Int, List<SetWord>>,
    private val fixedColumn: Boolean,
    private val side: Band? = null,
) {
    private val width = measures.width
    private val hasBeside = targum.isNotEmpty() || side != null

    private fun square(
        code: String,
        letters: Letters,
        x0: Float,
    ) = Column(code, letters, x0, width, square = true, scale = letters.size / measures.text.size)

    // The text's column, right of what stands beside it where anything does
    private fun textColumn(narrow: Boolean) = square(TEXT_COLUMN, measures.text, if (narrow) measures.sideWidth + measures.gap else 0f)

    private fun run(
        words: List<SetWord>,
        column: Column,
    ) = WordRun(words, widths, column.square, column.scale, (column.x1 - column.x0) * LINE_FILL)

    /**
     * The text's pieces [first] to [last] at the page's top: its headings alone in the middle, the others in paragraphs
     * with what stands beside them, each heading beginning a part of its own. The commentator beside the text goes on
     * from the word [sideAt] of its run: where that run is after the lines set here.
     */
    fun text(
        first: Int,
        last: Int,
        page: BandsPage,
        sideAt: Int = 0,
    ): Int {
        var at = sideAt
        var i = first
        while (i <= last) {
            if (pieces[i].isHeading) {
                if (page.lines.isNotEmpty()) page.y += measures.text.pitch * HEADING_SPACE
                page.y = paragraph(pieces[i].words, square(TEXT_COLUMN, measures.text, 0f), page.y, LastLine.Centred, page)
                i++
                continue
            }
            val from = i
            while (i <= last && !pieces[i].isHeading) i++
            at = part(from, i - 1, page, at)
        }
        page.y += measures.textFoot
        return at
    }

    // The text's pieces [first] to [last], none a heading, with what stands beside them at the page's left: their
    // targum, or the side commentator's lines from the word [sideAt] of its run. Where that run is after them.
    private fun part(
        first: Int,
        last: Int,
        page: BandsPage,
        sideAt: Int,
    ): Int {
        val aside = (first..last).flatMap { targum[it].orEmpty() }
        val waiting = side?.takeIf { it.ends[last] > sideAt }
        val column = textColumn(narrow = aside.isNotEmpty() || waiting != null || (fixedColumn && hasBeside))
        val top = page.y
        var bottom = top
        for ((words, end) in paragraphs(first, last)) bottom = paragraph(words, column, bottom, end, page)
        var at = sideAt
        var sideBottom = top
        if (aside.isNotEmpty()) {
            val letters =
                Column(
                    TARGUM_COLUMN,
                    measures.side,
                    0f,
                    measures.sideWidth,
                    square = true,
                    measures.side.size / measures.text.size,
                )
            sideBottom = paragraph(aside, letters, top + measures.sideInset, LastLine.Centred, page)
        } else if (waiting != null) {
            beside(waiting, sideAt until waiting.ends[last], top to bottom, page).let { (end, under) ->
                at = end
                sideBottom = under
            }
        }
        if (sideBottom > top) {
            bottom = maxOf(bottom, sideBottom)
            val x = measures.sideWidth + measures.gap / 2
            page.rules += PageRule(x, top, x, bottom)
        }
        page.y = bottom
        return at
    }

    /**
     * The side commentator's lines beside a part of the text ([span], its top and bottom), of the [words] of its run
     * waiting: under its name, set once a page, as many lines as the text is tall, [SIDE_ROWS] at least. Where the run
     * is after them (the rest go on under the text, in its band), and their bottom.
     */
    private fun beside(
        band: Band,
        words: IntRange,
        span: Pair<Float, Float>,
        page: BandsPage,
    ): Pair<Int, Float> {
        val run = band.beside ?: return words.first to span.first
        var top = span.first + measures.sideInset
        if (page.heads.none { it.bookId == band.bookId }) {
            val name = Column(NAME_COLUMN, measures.name, 0f, measures.sideWidth, square = true, measures.name.size / measures.text.size)
            val under = paragraph(nameWords(band.name), name, span.first, LastLine.Centred, page)
            page.head(band.bookId, 0f to measures.sideWidth, span.first to under, isBeside = true)
            top = under + measures.nameFoot
        }
        val pitch = band.letters.letters.pitch
        val rows = maxOf(SIDE_ROWS, floor((span.second - top) / pitch).toInt())
        val lines = run.lines(words.first, run.endOf(words.first, words.last + 1, rows))
        set(band, run, lines, Columns(top, lines.size, measures.sideWidth, measures.sideWidth), page)
        return lines.last().last + 1 to top + lines.size * pitch
    }

    // The [lines] of a band's [run] in its columns ([at]); the last of them is short where a comment ends with it
    private fun set(
        band: Band,
        run: WordRun,
        lines: List<IntRange>,
        at: Columns,
        page: BandsPage,
    ) {
        val letters = band.letters.letters
        val code = bandColumn(band.index, band.letters.square)
        val until = lines.last().last + 1
        val closes = until == run.words.size || run.words[until].lineId != run.words[until - 1].lineId
        lines.forEachIndexed { i, range ->
            val x1 = at.right - (i / at.rows) * (at.width + measures.gap)
            val (left, right) = lineSpan(x1 - at.width, x1, run.width(range), centred = false, isEnd = closes && i == lines.lastIndex)
            val lineTop = at.top + (i % at.rows) * letters.pitch + (letters.pitch - letters.box) / 2
            page.add(code, run.words.subList(range.first, range.last + 1), listOf(left, lineTop, right, lineTop + letters.box))
        }
    }

    /**
     * The paragraphs of the text's pieces [first] to [last], each with how its last line is set. A line of the library
     * is a paragraph, its pieces on the page set together; where the text runs on, a paragraph goes on to the line that
     * closes it (a petuchah). Its last line is short; one the page leaves open ends in the middle where verses run on,
     * and goes across where a paragraph is cut.
     */
    private fun paragraphs(
        first: Int,
        last: Int,
    ): List<Pair<List<SetWord>, LastLine>> {
        val paragraphs = ArrayList<Pair<List<SetWord>, LastLine>>()
        var words = ArrayList<SetWord>()
        for (i in first..last) {
            val piece = pieces[i]
            words += piece.words
            val ends = if (measures.runOn) piece.words.lastOrNull()?.text == PETUCHAH else piece.closes
            if (ends) {
                paragraphs += words to LastLine.Short
                words = ArrayList()
            }
        }
        if (words.isNotEmpty()) paragraphs += words to if (measures.runOn) LastLine.Centred else LastLine.Across
        return paragraphs
    }

    /**
     * A band: its rule, its commentator's name, then the words [from] until [until] of its run in lines, the first of
     * them in the right column and as many in each column (the odd ones in the first). A line is set across its column,
     * but the band's last when a comment ends with it. Not [named]: the side commentator's, going on under the text
     * from its lines beside it, with no rule and no name again.
     */
    fun band(
        band: Band,
        from: Int,
        until: Int,
        page: BandsPage,
        named: Boolean = true,
    ) {
        page.y += measures.bandGap
        if (named) {
            page.rules += PageRule(0f, page.y, width, page.y)
            page.y += measures.rulePad
            paragraph(nameWords(band.name), square(NAME_COLUMN, measures.name, 0f), page.y, LastLine.Centred, page)
            page.head(band.bookId, 0f to width, page.y to page.y + measures.name.pitch, isBeside = band.beside != null)
            page.y += measures.name.pitch + measures.nameFoot
        }
        val lines = band.run.lines(from, until)
        val rows = ceil(lines.size / band.letters.columns.toFloat()).toInt()
        val top = page.y
        set(band, band.run, lines, Columns(top, rows, width, band.letters.columnWidth), page)
        page.y = top + rows * band.letters.letters.pitch
        for (column in 1 until ceil(lines.size / rows.toFloat()).toInt()) {
            val x = width - column * (band.letters.columnWidth + measures.gap) + measures.gap / 2
            page.rules += PageRule(x, top, x, page.y)
        }
    }

    // [words] in lines of [column] from [top] down; its last line as [end] says. Where its next line would go.
    private fun paragraph(
        words: List<SetWord>,
        column: Column,
        top: Float,
        end: LastLine,
        page: BandsPage,
    ): Float {
        if (words.isEmpty()) return top
        val run = run(words, column)
        val letters = column.letters
        var lineTop = top
        val lines = run.lines(0, words.size)
        lines.forEachIndexed { i, range ->
            val isLast = i == lines.lastIndex
            val centred = isLast && end == LastLine.Centred
            val (left, right) = lineSpan(column.x0, column.x1, run.width(range), centred, isEnd = isLast && end == LastLine.Short)
            val boxTop = lineTop + (letters.pitch - letters.box) / 2
            page.lines +=
                dafLine(column.code, words.subList(range.first, range.last + 1), listOf(left, boxTop, right, boxTop + letters.box))
            lineTop += letters.pitch
        }
        return lineTop
    }
}
