package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.math.ceil

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): the parts of a page of bands as they are set, each from where the page
// is down (BookDafBands decides what a page takes): the text's pieces at its top with what stands beside them, then a
// commentator's band under its name, in its own letters and columns.

/** Above a heading of the text that is not at the page's top, to a line of the text. */
private const val HEADING_SPACE = 0.4f

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
 * piece is (the rules' "never before its verse").
 */
internal class Band(
    val index: Int,
    val bookId: Long,
    val name: String,
    val letters: BandLetters,
    val run: WordRun,
    val ends: IntArray,
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
 * What sets the parts of a unit's pages of bands: the text's [pieces], and what stands beside them ([beside], by the
 * piece each part of it is set with, in [besideLetters]). [fixedColumn]: a paragraph may be cut between pages, so the
 * text's column is one width on every page, beside the side's column even where that is empty.
 */
internal class BandsPrinter(
    private val widths: WordWidths,
    private val measures: BandsMeasures,
    private val pieces: List<TextPiece>,
    private val beside: Map<Int, List<SetWord>>,
    private val besideLetters: BandLetters?,
    private val fixedColumn: Boolean,
) {
    private val width = measures.width
    private val hasBeside = besideLetters != null && beside.isNotEmpty()

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
     * with what stands beside them, each heading beginning a part of its own.
     */
    fun text(
        first: Int,
        last: Int,
        page: BandsPage,
    ) {
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
            part(from, i - 1, page)
        }
        page.y += measures.textFoot
    }

    // The text's pieces [first] to [last], none a heading, with what stands beside them at the page's left
    private fun part(
        first: Int,
        last: Int,
        page: BandsPage,
    ) {
        val aside = if (besideLetters == null) emptyList() else (first..last).flatMap { beside[it].orEmpty() }
        val column = textColumn(narrow = aside.isNotEmpty() || (fixedColumn && hasBeside))
        val top = page.y
        var bottom = top
        for ((words, end) in paragraphs(first, last)) bottom = paragraph(words, column, bottom, end, page)
        if (besideLetters != null && aside.isNotEmpty()) {
            val base = if (besideLetters.square) measures.text.size else measures.commentary.size
            val code = if (besideLetters.square) TARGUM_COLUMN else SIDE_RASHI_COLUMN
            val side = Column(code, besideLetters.letters, 0f, measures.sideWidth, besideLetters.square, besideLetters.letters.size / base)
            bottom = maxOf(bottom, paragraph(aside, side, top + measures.sideInset, LastLine.Centred, page))
            val x = measures.sideWidth + measures.gap / 2
            page.rules += PageRule(x, top, x, bottom)
        }
        page.y = bottom
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
     * but the band's last when a comment ends with it.
     */
    fun band(
        band: Band,
        from: Int,
        until: Int,
        page: BandsPage,
    ) {
        val run = band.run
        val letters = band.letters.letters
        page.y += measures.bandGap
        page.rules += PageRule(0f, page.y, width, page.y)
        page.y += measures.rulePad
        page.heads += BandHead(band.bookId, 0f, page.y, width, page.y + measures.name.pitch)
        paragraph(nameWords(band.name), square(NAME_COLUMN, measures.name, 0f), page.y, LastLine.Centred, page)
        page.y += measures.name.pitch + measures.nameFoot
        val lines = run.lines(from, until)
        val rows = ceil(lines.size / band.letters.columns.toFloat()).toInt()
        val top = page.y
        val code = bandColumn(band.index, band.letters.square)
        val stride = band.letters.columnWidth + measures.gap
        val closes = until == run.words.size || run.words[until].lineId != run.words[until - 1].lineId
        lines.forEachIndexed { i, range ->
            val x1 = width - (i / rows) * stride
            val isShort = closes && i == lines.lastIndex
            val (left, right) = lineSpan(x1 - band.letters.columnWidth, x1, run.width(range), centred = false, isEnd = isShort)
            val lineTop = top + (i % rows) * letters.pitch + (letters.pitch - letters.box) / 2
            page.add(code, run.words.subList(range.first, range.last + 1), listOf(left, lineTop, right, lineTop + letters.box))
        }
        page.y = top + rows * letters.pitch
        for (column in 1 until ceil(lines.size / rows.toFloat()).toInt()) {
            val x = width - column * stride + measures.gap / 2
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
