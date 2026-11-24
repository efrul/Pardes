package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): what a book's page of bands is measured by (BookDafBands): each column's
// letters, a commentator's own where the reader set them apart, the page's size (the rules' own, or the view's where the
// pages follow its width), and a run of words broken into lines by their measured widths.

/** A line's pitch to its letters: the text's, the targum's beside it, a commentary's, a commentator's name's. */
private const val TEXT_PITCH = 1.6f
private const val SIDE_PITCH = 1.62f
private const val COMMENTARY_PITCH = 1.52f
private const val NAME_PITCH = 1.385f

/** The targum's letters to the text's, and a commentator's name's to the commentaries'. */
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
 * A commentator's band's letters: its lines', how many [columns] they are set in and how wide each is, and whether they
 * are the text's [square] letters rather than Rashi script.
 */
internal class BandLetters(
    val letters: Letters,
    val columns: Int,
    val columnWidth: Float,
    val square: Boolean,
)

/** A page's block in points, where it is not the rules' own: the view's, when the pages follow its width. */
internal data class DafPageSize(
    val width: Float,
    val height: Float,
)

/**
 * The measures a page of bands is set by, in points, from the reader's [style]: the letters are those of a page
 * [BOOK_DAF_WIDTH] wide, whatever the [page]'s own size (a wider page holds more of them in a line). [runOn]: the
 * text's lines run on in one paragraph, as verses do.
 */
internal class BandsMeasures(
    private val style: BookDafStyle,
    val runOn: Boolean,
    page: DafPageSize? = null,
) {
    private val unit = BOOK_DAF_WIDTH / BookDafStyle.RULES_WIDTH
    val width = page?.width ?: BOOK_DAF_WIDTH
    val height = page?.height ?: (BOOK_DAF_WIDTH * style.aspect)

    /** A page wider than it is tall, as a screen is: its commentaries are in the columns of a wide page. */
    val isWide = width > height
    val columns = if (isWide) style.wideColumns else style.columns

    /** Whether the text waits for its commentaries, and a paragraph may be cut between two pages where they end. */
    val together = style.together
    val text = Letters(style.textSize * unit, GEMARA_SIZE, TEXT_PITCH)
    val side = Letters(style.textSize * SIDE_SIZE * unit, GEMARA_SIZE, SIDE_PITCH)
    val commentary = Letters(style.commentarySize * unit, RASHI_SIZE, COMMENTARY_PITCH)
    val name = Letters(style.commentarySize * NAME_SIZE * unit, GEMARA_SIZE, NAME_PITCH)
    val gap = COLUMN_GAP * unit

    /** The column beside the text, at the page's left (its targum's, or a commentator's put there); 0 with none. */
    val sideWidth = if (style.sideShare > 0f) width * style.sideShare - gap / 2 else 0f
    val sideInset = SIDE_INSET * unit
    val textFoot = TEXT_FOOT * unit
    val bandGap = BAND_GAP * unit
    val rulePad = RULE_PAD * unit
    val nameFoot = NAME_FOOT * unit

    /** A band's column's width in the style's columns, and what a band takes above its lines: its rule and its name. */
    val columnWidth = (width - gap * (columns - 1)) / columns
    val bandHead = bandGap + rulePad + name.pitch + nameFoot

    /** A commentator's band's letters: the style's, or its [own] where the reader set them apart. */
    fun band(own: BandStyle? = null): BandLetters {
        val square = own?.square ?: style.squareCommentary
        val size = (own?.size ?: style.commentarySize) * unit
        val columns = (own?.columns ?: columns).coerceIn(BookDafStyle.BAND_COLUMNS)
        val letters = Letters(size, if (square) GEMARA_SIZE else RASHI_SIZE, COMMENTARY_PITCH)
        return BandLetters(letters, columns, (width - gap * (columns - 1)) / columns, square)
    }

    /**
     * The letters of what stands beside the text: the targum's ([isTargum]), square and smaller than the text's; or
     * those of a commentator put in its place, a band's ([own] where the reader set them apart).
     */
    fun beside(
        own: BandStyle? = null,
        isTargum: Boolean = true,
    ): BandLetters {
        if (isTargum) return BandLetters(side, 1, sideWidth, square = true)
        val square = own?.square ?: style.squareCommentary
        val size = (own?.size ?: style.commentarySize) * unit
        return BandLetters(Letters(size, if (square) GEMARA_SIZE else RASHI_SIZE, COMMENTARY_PITCH), 1, sideWidth, square)
    }

    /**
     * The printed lines a paragraph's [words] break into in the text's column ([narrow]: beside what stands at its
     * left), as a page sets them.
     */
    fun textRows(
        words: List<SetWord>,
        widths: WordWidths,
        narrow: Boolean,
    ): List<IntRange> {
        val x0 = if (narrow) sideWidth + gap else 0f
        return WordRun(words, widths, square = true, scale = 1f, room = (width - x0) * LINE_FILL).lines(0, words.size)
    }
}

/** A rule drawn on a page (points): above a band, between two columns. */
internal class PageRule(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
)

/** How a paragraph's last line is set: across as the others, from the right when short, or in the middle. */
internal enum class LastLine { Across, Short, Centred }

/**
 * Words set in lines [room] wide, in letters [scale] times the page's ([square]: the text's, else the commentaries'): a
 * paragraph, the targum, a commentator's run, whose comments open in the text's letters, [leadScale] times the page's.
 */
internal class WordRun(
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
