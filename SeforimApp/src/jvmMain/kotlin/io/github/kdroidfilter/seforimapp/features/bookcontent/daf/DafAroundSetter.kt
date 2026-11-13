package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): a book's daf set around its text (DafStyle.Around), as the Vilna page
// sets the Gemara with Rashi and Tosafot. The text is in the middle, and the commentators are on both sides of it: beside
// it while it runs, then under it, each side on its half. A side that ends first leaves its place to the text, and the
// other side takes the whole width once both the text and it are alone.

/** The text's column, of the page's width. */
private const val TEXT_SHARE = 0.5f

/** Above a commentator's name but the first of its side, and above a heading of the text, to their box's height. */
private const val NAME_SPACE = 0.6f
private const val HEADING_SPACE = 0.4f

/** Below a column's last line, to a commentary line's box: room the columns that widen into its place keep from it. */
private const val END_SPACE = 0.6f

/**
 * A paragraph of a column: its words run on from line to line, or set alone in the middle of the column ([centred], a
 * heading or a name), with [space] (points) above it but at the column's top.
 */
private class Paragraph(
    val words: List<SetWord>,
    val column: String,
    val centred: Boolean,
    val space: Float,
)

/** A column's paragraphs set down the page, line by line: where its next line goes ([y]) and its lines so far. */
private class Flow(
    val paragraphs: List<Paragraph>,
) {
    var y = 0f
    var paragraph = 0
    var word = 0

    /** The bottom of its last line, and where the columns beside it may widen into its place once it is [done]. */
    var bottom = 0f
    var end = 0f
    val lines = ArrayList<DafLine>()
    val done get() = paragraph >= paragraphs.size

    /** Whether it has ended above [top]: its place is free there. */
    fun freeAt(top: Float) = done && end <= top
}

/** Pages of a book's daf set around their text, [BOOK_DAF_WIDTH] points wide, their words measured with [widths]. */
internal class DafAroundSetter(
    private val widths: WordWidths,
) : DafPageSetter {
    private val sizes = DafSizes.of(BOOK_DAF_WIDTH)
    private val width = BOOK_DAF_WIDTH
    private val side = (width - width * TEXT_SHARE) / 2 - BOOK_DAF_GAP

    /** [text] with the comments of [commentaries] on its lines, as a page: its lines the text's, the right's, the left's. */
    override fun page(
        text: List<DafSegment>,
        commentaries: List<DafCommentary>,
    ): SetPage {
        val ids = text.map { it.lineId }
        val main = Flow(textParagraphs(text))
        val right = Flow(sideParagraphs(commentaries.filter { it.onRight }, ids, RIGHT_COLUMN))
        val left = Flow(sideParagraphs(commentaries.filterNot { it.onRight }, ids, LEFT_COLUMN))
        val flows = listOf(main, right, left)
        // The column whose next line is highest goes on: what is above it is set, and so where its neighbours ended
        while (true) {
            val flow = flows.filterNot { it.done }.minByOrNull { it.y } ?: break
            val (x0, x1) =
                when (flow) {
                    main -> textRange(main.y, right, left)
                    right -> sideRange(right.y, main, left, onRight = true)
                    else -> sideRange(left.y, main, right, onRight = false)
                }
            setLine(flow, x0, x1)
        }
        return SetPage(main.lines + right.lines + left.lines, flows.maxOf { it.bottom })
    }

    // The text's column, widened into a side's place where that side has ended above
    private fun textRange(
        y: Float,
        right: Flow,
        left: Flow,
    ): Pair<Float, Float> =
        (if (left.freeAt(y)) 0f else side + BOOK_DAF_GAP) to (if (right.freeAt(y)) width else width - side - BOOK_DAF_GAP)

    // A side's column beside the text; under it once it has ended, on the side's half, or the whole width when the
    // other side has ended too
    private fun sideRange(
        y: Float,
        main: Flow,
        other: Flow,
        onRight: Boolean,
    ): Pair<Float, Float> =
        when {
            !main.freeAt(y) -> if (onRight) width - side to width else 0f to side
            other.freeAt(y) -> 0f to width
            onRight -> (width + BOOK_DAF_GAP) / 2 to width
            else -> 0f to (width - BOOK_DAF_GAP) / 2
        }

    // The text's lines in paragraphs: its headings each alone, the lines between them run on
    private fun textParagraphs(text: List<DafSegment>): List<Paragraph> {
        val out = ArrayList<Paragraph>()
        val run = ArrayList<SetWord>()

        fun flush() {
            if (run.isNotEmpty()) out += Paragraph(run.toList(), TEXT_COLUMN, centred = false, space = 0f)
            run.clear()
        }
        for (segment in text) {
            if (segment.isHeading) {
                flush()
                out += Paragraph(setWords(segment), TEXT_COLUMN, centred = true, space = sizes.gemara * HEADING_SPACE)
            } else {
                run += setWords(segment)
            }
        }
        flush()
        return out
    }

    // A side's commentators that comment on the lines [ids], each its name and then its comments on them, run on
    private fun sideParagraphs(
        commentaries: List<DafCommentary>,
        ids: List<Long>,
        column: String,
    ): List<Paragraph> =
        commentaries.flatMap { commentary ->
            val comments = ids.flatMap { commentary.comments[it].orEmpty() }
            if (comments.isEmpty()) return@flatMap emptyList()
            listOf(
                Paragraph(nameWords(commentary.name), NAME_COLUMN, centred = true, space = sizes.gemara * NAME_SPACE),
                Paragraph(comments.flatMap(::setWords), column, centred = false, space = 0f),
            )
        }

    // The next line of [flow] at its top, from [x0] to [x1]: as many of its paragraph's words as fit
    private fun setLine(
        flow: Flow,
        x0: Float,
        x1: Float,
    ) {
        val paragraph = flow.paragraphs[flow.paragraph]
        val square = paragraph.column == TEXT_COLUMN || paragraph.column == NAME_COLUMN
        val box = if (square) sizes.gemara else sizes.commentary
        val first = flow.word
        val (last, lineWidth) = widths.fit(paragraph.words, first, (x1 - x0) * LINE_FILL, square)
        val isEnd = last == paragraph.words.lastIndex
        val (left, right) = lineSpan(x0, x1, lineWidth, paragraph.centred, isEnd)
        flow.lines += dafLine(paragraph.column, paragraph.words.subList(first, last + 1), listOf(left, flow.y, right, flow.y + box))
        flow.bottom = flow.y + box
        flow.y += box * LINE_PITCH
        flow.word = last + 1
        if (!isEnd) return
        flow.paragraph++
        flow.word = 0
        if (flow.done) {
            flow.end = flow.bottom + sizes.commentary * END_SPACE
        } else {
            flow.y += flow.paragraphs[flow.paragraph].space
        }
    }
}
