package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): a book of the Tanakh's daf set verse by verse (DafStyle.Verses), as
// the Mikraot Gedolot sets it, after the printed edition the user sent on 2026-11-13: each verse at the top of its part,
// in the text's square letters on the right, with its targum on the left under the targum's name; then each
// commentator's comments on it, under its name, in two columns balanced on their last line; then the next verse.

/** The verse's column, of the page's width: the targum has the rest. */
private const val VERSE_SHARE = 0.62f

/** Between two verses' parts and above a commentator's name, to a commentary line's box; above a heading, to its own. */
private const val PART_SPACE = 1.2f
private const val NAME_SPACE = 0.6f
private const val HEADING_SPACE = 0.4f

/** Pages of a book's daf set verse by verse, [BOOK_DAF_WIDTH] points wide, with the [targum] beside each verse. */
internal class DafVersesSetter(
    private val widths: WordWidths,
    private val targum: DafTargum?,
) : DafPageSetter {
    private val sizes = DafSizes.of(BOOK_DAF_WIDTH)
    private val width = BOOK_DAF_WIDTH
    private val verseLeft = width * (1 - VERSE_SHARE)
    private val half = (width - BOOK_DAF_GAP) / 2

    /** [text] with the comments of [commentaries] on each of its verses, as a page: its lines as they are read. */
    override fun page(
        text: List<DafSegment>,
        commentaries: List<DafCommentary>,
    ): SetPage {
        val page = VersesPage()
        for (segment in text) {
            if (segment.isHeading) {
                page.heading(segment)
                continue
            }
            page.verse(segment)
            for (commentary in commentaries) commentary.comments[segment.lineId]?.let { page.commentary(commentary.name, it) }
        }
        return SetPage(page.lines, page.bottom)
    }

    /** A page being set from its top down: its lines so far, where the next part goes ([y]) and its lines' bottom. */
    private inner class VersesPage {
        val lines = ArrayList<DafLine>()
        var y = 0f
        var bottom = 0f

        // A heading of the text, alone in the middle of the page
        fun heading(segment: DafSegment) {
            if (lines.isNotEmpty()) y += sizes.gemara * HEADING_SPACE
            y = paragraph(setWords(segment), TEXT_COLUMN, 0f to width, y, centred = true)
        }

        // A verse at its part's top: on the right with its targum on the left under the targum's name, or across alone
        fun verse(segment: DafSegment) {
            if (lines.isNotEmpty()) y += sizes.commentary * PART_SPACE
            val top = y
            val beside = targum?.verses?.get(segment.lineId).orEmpty()
            val verseBottom = paragraph(setWords(segment), TEXT_COLUMN, (if (beside.isEmpty()) 0f else verseLeft) to width, top)
            var targumBottom = top
            if (targum != null && beside.isNotEmpty()) {
                val column = 0f to verseLeft - BOOK_DAF_GAP
                val name = paragraph(nameWords(targum.name), NAME_COLUMN, column, top, centred = true)
                targumBottom = paragraph(beside.flatMap(::setWords), TARGUM_COLUMN, column, name)
            }
            y = maxOf(verseBottom, targumBottom)
        }

        // A commentator's comments on the verse, under its name, run on in two columns: the right one takes the first
        // half of their lines, and the odd one
        fun commentary(
            name: String,
            comments: List<DafSegment>,
        ) {
            y += sizes.commentary * NAME_SPACE
            y = paragraph(nameWords(name), NAME_COLUMN, 0f to width, y, centred = true)
            val words = comments.flatMap(::setWords)
            val ranges = ArrayList<IntRange>()
            var first = 0
            while (first < words.size) {
                val (last, _) = widths.fit(words, first, half * LINE_FILL, square = false)
                ranges += first..last
                first = last + 1
            }
            val right = (ranges.size + 1) / 2
            val top = y
            place(words, ranges.subList(0, right), RIGHT_COLUMN, width - half to width, top, isEnd = right == ranges.size)
            place(words, ranges.subList(right, ranges.size), LEFT_COLUMN, 0f to half, top, isEnd = true)
            y = top + right * sizes.commentary * LINE_PITCH
        }

        // The words broken into lines of [column] from [top] down: where the next line would go below them
        fun paragraph(
            words: List<SetWord>,
            code: String,
            column: Pair<Float, Float>,
            top: Float,
            centred: Boolean = false,
        ): Float {
            val (square, scale, box) = lettersOf(code)
            var lineTop = top
            var first = 0
            while (first < words.size) {
                val (last, lineWidth) = widths.fit(words, first, (column.second - column.first) * LINE_FILL, square, scale)
                val (x0, x1) = lineSpan(column.first, column.second, lineWidth, centred, isEnd = last == words.lastIndex)
                add(dafLine(code, words.subList(first, last + 1), listOf(x0, lineTop, x1, lineTop + box)))
                lineTop += box * LINE_PITCH
                first = last + 1
            }
            return lineTop
        }

        // Lines of [words] already broken ([ranges]) in [column] from [top] down; the last a paragraph's ([isEnd])
        fun place(
            words: List<SetWord>,
            ranges: List<IntRange>,
            code: String,
            column: Pair<Float, Float>,
            top: Float,
            isEnd: Boolean,
        ) {
            val (square, scale, box) = lettersOf(code)
            ranges.forEachIndexed { i, range ->
                val lineWords = words.subList(range.first, range.last + 1)
                val lineWidth = widths.fit(lineWords, 0, Float.MAX_VALUE, square, scale).second
                val (x0, x1) = lineSpan(column.first, column.second, lineWidth, centred = false, isEnd = isEnd && i == ranges.lastIndex)
                val lineTop = top + i * box * LINE_PITCH
                add(dafLine(code, lineWords, listOf(x0, lineTop, x1, lineTop + box)))
            }
        }

        private fun add(line: DafLine) {
            lines += line
            bottom = maxOf(bottom, line.b[3])
        }
    }

    // A column's letters: square or Rashi script, their size to the text's, and a line's box height
    private fun lettersOf(code: String): Triple<Boolean, Float, Float> =
        when (code) {
            TEXT_COLUMN, NAME_COLUMN -> Triple(true, 1f, sizes.gemara)
            TARGUM_COLUMN -> Triple(true, sizes.commentary / sizes.gemara, sizes.commentary)
            else -> Triple(false, 1f, sizes.commentary)
        }
}
