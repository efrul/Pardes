package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.math.ceil
import kotlin.math.floor

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): a book's daf set in bands (DafStyle.Bands), by the user's rules for a
// page of text and commentaries (docs/BOOK_DAF_RULES.md; the rules named here are its "כללי העימוד"): pages of one
// size, the text at the top with its targum beside it, then each commentator in a band of its own under its name, in
// balanced columns. A commentator's comments are one run of words that goes on from page to page by itself, never
// ahead of the text it is on. Lines are broken here, by the words' measured widths, so a line count is exact and the
// rules' search for the most words that fit is one walk down the lines.
//
// Since request 98 of docs/DESKTOP_STAGE3_PLAN.md the text waits for its commentaries (BookDafStyle.together), as a
// footnoted book's does: a page takes a new piece of the text only when the comments on the pieces before it end on
// that page, so a comment begins on the page of its text, and where they are longer than a page the next pages are
// theirs alone. The rules' third ("פסוק חדש אחד לפחות בכל עמוד"), which let a long commentary lag pages behind, is the
// other way to set them, kept in the style.
//
// A commentator the reader put beside the text (request 93) is a band as the others, the first of them: on a page its
// first lines stand beside the text, as many as the text is tall, and the rest go on under the text across the page, as
// Rashi does under the Gemara; on a page of commentaries alone it is a band under its name.

/** Rule 7: a band of fewer lines a column than this waits for the next page. */
private const val MIN_ROWS = 2

/** Rule 8: a page with more than this much of it empty takes one more line of the text. */
private const val FILL = 0.15f

/** Rule 9: the lines of commentary left free at a page's bottom. */
private const val SAFETY_LINES = 1

/** A line of the text that ends with this closes its paragraph (a parashah petuchah), where the text runs on. */
internal const val PETUCHAH = "{פ}"

/**
 * A unit of a book ([text], its lines in order) set in pages of bands: its [targum] (or the commentator put in its
 * place) beside its text, each of [commentaries] (in the order of their bands, which is their turn for a page's room
 * too, rule 5) under it. [anchors]: each anchored comment's place in its line of the text, the first of the words its
 * anchor puts it on (BookDafSource's passages).
 */
internal class DafBandsSetter(
    private val widths: WordWidths,
    text: List<DafSegment>,
    commentaries: List<DafCommentary>,
    targum: DafTargum?,
    private val measures: BandsMeasures,
    anchors: Map<Long, Int> = emptyMap(),
) {
    // A paragraph is cut between pages where the text waits for its commentaries, and does not run on as verses do
    private val cuts = measures.together && !measures.runOn
    private val pieces: List<TextPiece>
    private val printer: BandsPrinter
    private val bands: List<Band>

    init {
        val beside = targum?.takeIf { measures.sideWidth > 0f }
        val side = beside?.verses.orEmpty()
        val narrow = text.any { side[it.lineId].orEmpty().isNotEmpty() }
        val anchored = text.filter { line -> (commentaries.map { it.comments } + side).any { it.anchors(line, anchors) } }
        // A paragraph's printed lines are those of the text's column, which what stands beside it narrows
        val rows = { words: List<SetWord> -> measures.textRows(words, widths, narrow) }
        val all = TextPieces(text, anchors, anchored.mapTo(HashSet()) { it.lineId }, rows.takeIf { cuts })
        pieces = all.list
        val placed = all.placed(text, side, atEnd = false).sortedBy { it.first }
        // The commentator beside the text: a band after the others' in name, the first of them in turn
        val sideBand =
            beside?.bookId?.takeIf { narrow }?.let { bookId ->
                band(
                    commentaries.size,
                    DafCommentary(bookId, beside.name, onRight = true, emptyMap(), beside.style),
                    placed,
                    isBeside = true,
                )
            }
        val targumWords = placed.takeIf { sideBand == null }.orEmpty().groupBy({ it.first }) { setWords(it.second) }
        printer = BandsPrinter(widths, measures, pieces, targumWords.mapValues { it.value.flatten() }, cuts, sideBand)
        bands =
            listOfNotNull(sideBand) +
            commentaries.mapIndexed { index, commentary ->
                band(index, commentary, all.placed(text, commentary.comments, atEnd = true).sortedBy { it.first })
            }
    }

    // A commentator's band: its comments, each with the piece of the text it is set with ([placed]), as one run of
    // words; [isBeside]: with its run in the lines of the column beside the text too
    private fun band(
        index: Int,
        commentary: DafCommentary,
        placed: List<Pair<Int, DafSegment>>,
        isBeside: Boolean = false,
    ): Band {
        val letters = measures.band(commentary.style)
        val size = letters.letters.size
        val words = placed.map { (_, comment) -> commentWords(comment, letters.square) }
        val counts = IntArray(pieces.size)
        placed.forEachIndexed { i, (piece, _) -> counts[piece] += words[i].size }
        var count = 0
        val ends = IntArray(pieces.size) { i -> (count + counts[i]).also { count = it } }
        val scale = size / if (letters.square) measures.text.size else measures.commentary.size
        val all = words.flatten()
        val run = { width: Float ->
            WordRun(all, widths, letters.square, scale, width * LINE_FILL, size * DIBBUR_SIZE / measures.text.size)
        }
        val beside = if (isBeside) run(measures.sideWidth) else null
        return Band(index, commentary.bookId, commentary.name, letters, run(letters.columnWidth), ends, beside)
    }

    // A comment's words: its first bold ones open it, in the text's letters where its band is in Rashi script
    private fun commentWords(
        comment: DafSegment,
        square: Boolean,
    ): List<SetWord> {
        val opening =
            if (square) {
                0
            } else {
                comment.words.indices
                    .takeWhile { it in comment.bold }
                    .size
            }
        return comment.words.mapIndexed { i, word -> SetWord(word, comment.lineId, i in comment.bold, lead = i < opening) }
    }

    // The words of a band set once the text's piece [piece] is; none before the first
    private fun endAt(
        band: Band,
        piece: Int,
    ) = if (piece < 0) 0 else band.ends[piece]

    /**
     * The unit's pages, in order; [beforeEachPage] is called before each is set (a chapter with all its commentators is
     * thousands of pages, let go of when the pages are set otherwise meanwhile).
     */
    fun pages(beforeEachPage: () -> Unit = {}): List<SetPage> {
        val pages = ArrayList<SetPage>()
        val at = IntArray(bands.size)
        var next = 0
        while (next < pieces.size || bands.indices.any { at[it] < bands[it].run.words.size }) {
            beforeEachPage()
            val set = next + at.sum()
            val last = lastPiece(next, at)
            val page = BandsPage()
            val wraps = last >= next && setText(next, last, page, at)
            val taken = setBands(at, maxOf(last, next - 1), page, wraps)
            // Nothing new on the page (its room too small for a band's least): the first band waiting is set all the same
            if (last < next && !taken) force(at, page)
            // The line of the text that goes on from the page before, by the first of its words here
            val starts = pieces.getOrNull(next)?.takeIf { last >= next && !it.opens }?.let { mapOf(it.lineId to it.from) }
            next = maxOf(next, last + 1)
            // A piece or a word at least on each page: the unit ends
            check(next + at.sum() > set) { "a page of bands took nothing in" }
            val isLast = next >= pieces.size && bands.indices.all { at[it] >= bands[it].run.words.size }
            // Pages of one size (a line taller than a page makes its own longer); a unit's last ends where its lines do
            val height = if (isLast) page.y else maxOf(measures.height, page.y)
            pages += SetPage(page.lines, height, page.rules, page.openings, page.heads, starts.orEmpty(), measures.width)
        }
        return pages
    }

    // A page's room: its height less the lines left free at its bottom (rule 9)
    private val room = measures.height - measures.commentary.pitch * SAFETY_LINES

    // The band of the commentator beside the text, the first where there is one
    private val side = bands.firstOrNull()?.takeIf { it.beside != null }

    // The text's pieces [first] to [last] at the top of [page], with the side commentator's lines beside them, its run
    // going on in [at]. Whether it has lines there: the rest of its words then go on under the text with no name.
    private fun setText(
        first: Int,
        last: Int,
        page: BandsPage,
        at: IntArray,
    ): Boolean {
        val end = printer.text(first, last, page, if (side == null) 0 else at[0])
        if (side == null || end == at[0]) return false
        at[0] = end
        return true
    }

    // What a band takes above its lines: its rule and its name, or, the side commentator's going on from beside the
    // text ([wraps]), a gap alone
    private fun head(
        band: Band,
        wraps: Boolean,
    ) = if (wraps && band === side) measures.bandGap else measures.bandHead

    /**
     * The last piece of the text a page that begins at the piece [first] takes, with each band's run at [at]: piece after
     * piece while the text, what stands beside it and all the comments waiting up to it fit (rule 2); one more when much
     * of the page would be left empty (rule 8), or the next line of a paragraph cut there; a heading is not a page's
     * last. Where nothing fits, one piece all the same (rule 3) — but, the text waiting for its commentaries, only when
     * none of them waits from the pieces before: else the page is theirs alone. [first] less one with no piece taken.
     */
    private fun lastPiece(
        first: Int,
        at: IntArray,
    ): Int {
        if (first >= pieces.size) return first - 1
        val tops = HashMap<Int, PageTop>()

        // The text of the pieces up to [last] at the page's top, set once for each
        fun top(last: Int): PageTop {
            if (last < first) return PageTop(0f, at, wraps = false)
            return tops.getOrPut(last) {
                val after = at.copyOf()
                val page = BandsPage()
                val wraps = setText(first, last, page, after)
                PageTop(page.y, after, wraps)
            }
        }

        fun used(last: Int) = top(last).let { it.height + waiting(it, last, room - it.height) }
        var last = first - 1
        while (last < pieces.lastIndex && used(last + 1) <= room) last++
        if (last < first && (!measures.together || used(first - 1) == 0f)) last = first
        if (last < pieces.lastIndex && fills(last, room - used(last), top(last + 1).height - top(last).height, top(last).at)) last++
        return offHeading(first, last)
    }

    // The height of the bands of the comments waiting up to the piece [last] under a page's [top], counted while they
    // fit in [spare]: with many commentators waiting, the first few are more than a page's room
    private fun waiting(
        top: PageTop,
        last: Int,
        spare: Float,
    ): Float {
        var height = 0f
        for (c in bands.indices) {
            if (height > spare) break
            height += bandHeight(bands[c], top.at[c], endAt(bands[c], last), head(bands[c], top.wraps))
        }
        return height
    }

    /**
     * Whether a page whose text ends at the piece [last], with [spare] room left, takes one more piece, [oneMore] tall,
     * its comments to go on from the page's bottom. Rule 8: when much of the page would be left empty. And a paragraph
     * cut between pages gives its next line whenever the comments on it may begin under it.
     */
    private fun fills(
        last: Int,
        spare: Float,
        oneMore: Float,
        at: IntArray,
    ): Boolean {
        val least = MIN_ROWS * measures.commentary.pitch
        if (!cuts || pieces[last + 1].opens) return spare > FILL * room && oneMore < spare - least
        // A band already on the page goes on under its name; another needs room for its own
        val head = if (bands.indices.any { endAt(bands[it], last) > at[it] }) 0f else measures.bandHead
        return oneMore < spare - least - head
    }

    // The last piece of a page that begins at [first] and would end at [last], when that is a heading: the piece before
    // it, or, a heading alone, the pieces after it up to one that is none
    private fun offHeading(
        first: Int,
        last: Int,
    ): Int {
        if (last < first) return last
        var end = last
        while (end > first && end < pieces.lastIndex && pieces[end].isHeading) end--
        while (end < pieces.lastIndex && pieces[end].isHeading) end++
        return end
    }

    // The height of a band of the words [from] until [until] under its [head]; none with no words. A band taller than
    // a page's room is counted no further (a commentator's comments waiting may be a chapter's)
    private fun bandHeight(
        band: Band,
        from: Int,
        until: Int,
        head: Float,
    ): Float {
        if (until <= from) return 0f
        val pitch = band.letters.letters.pitch
        val most = (floor(room / pitch).toInt() + 2) * band.letters.columns
        val lines = band.run.lineCount(from, until, most)
        return head + ceil(lines / band.letters.columns.toFloat()) * pitch
    }

    /**
     * The bands of a page whose text ends at the piece [last]: each commentator in turn takes as many of its words
     * waiting as the room left holds (rule 5), none when that is fewer lines a column than a band's least (rule 7).
     * [wraps]: the side commentator has lines beside the text of this page, and goes on under it. Whether any took
     * words.
     */
    private fun setBands(
        at: IntArray,
        last: Int,
        page: BandsPage,
        wraps: Boolean,
    ): Boolean {
        var taken = false
        for ((c, band) in bands.withIndex()) {
            val most = endAt(band, last)
            val goesOn = wraps && band === side
            val rows = floor((room - page.y - head(band, wraps)) / band.letters.letters.pitch).toInt()
            // Going on from beside the text, a line is no band's beginning: the least is one
            if (most <= at[c] || rows < if (goesOn) 1 else MIN_ROWS) continue
            val until = band.run.endOf(at[c], most, rows * band.letters.columns)
            printer.band(band, at[c], until, page, named = !goesOn)
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
        val c = at.indices.first { at[it] < bands[it].run.words.size }
        val band = bands[c]
        val until = band.run.endOf(at[c], band.run.words.size, MIN_ROWS * band.letters.columns)
        printer.band(band, at[c], until, page)
        at[c] = until
    }
}

/** A page's top as set: the text's [height], each band's run after it ([at]), and whether the side commentator [wraps]. */
private class PageTop(
    val height: Float,
    val at: IntArray,
    val wraps: Boolean,
)

// Each of [comments] on the lines of [text], with the piece of the text it is set with (TextPieces.of)
private fun TextPieces.placed(
    text: List<DafSegment>,
    comments: Map<Long, List<DafSegment>>,
    atEnd: Boolean,
): List<Pair<Int, DafSegment>> = text.flatMap { line -> comments[line.lineId].orEmpty().map { of(line.lineId, it.lineId, atEnd) to it } }

// Whether any of these comments on [line] is anchored inside it
private fun Map<Long, List<DafSegment>>.anchors(
    line: DafSegment,
    anchors: Map<Long, Int>,
): Boolean = this[line.lineId].orEmpty().any { it.lineId in anchors }
