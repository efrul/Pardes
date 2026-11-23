package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): any book as a daf, its text with the commentators the commentaries
// pane shows for its lines, set in the reader's style (DafStyle, BookDafStyle.kt): in bands under the text as the
// Mikraot Gedolot (BookDafBands, the user's rules), or around the text as the Vilna Shas sets the Gemara
// (DafAroundSetter). The pages are set here as the scans give the Talmud's (DafAmud: each printed line's box and words,
// each word tied to its library line), and drawn, clicked and selected by the daf's own code (BookDafView).

/**
 * A library line as a book's daf sets it: its [words] (those of [bold] in bold), and whether it is a heading, set alone
 * in the middle of its column.
 */
internal data class DafSegment(
    val lineId: Long,
    val words: List<String>,
    val bold: Set<Int> = emptySet(),
    val isHeading: Boolean = false,
)

/**
 * A commentator of a book's daf: its book, its [name] over its part of each page, the side it is set on (around the
 * text), and its [comments] on the book's lines, by the id of the line each is set with, in the order they are read.
 */
internal data class DafCommentary(
    val bookId: Long,
    val name: String,
    val onRight: Boolean,
    val comments: Map<Long, List<DafSegment>>,
)

/** The targum set beside the text (in bands): its [name], and its lines on each line of the text, by that line's id. */
internal data class DafTargum(
    val name: String,
    val verses: Map<Long, List<DafSegment>>,
)

/** How wide a word is in points, at a book's daf's letters: square (the text's and the names') or Rashi script. */
internal interface WordWidths {
    fun of(
        word: String,
        square: Boolean,
        bold: Boolean,
    ): Float

    fun space(square: Boolean): Float
}

/**
 * The columns of a book's daf, as its lines name them: the text, the commentaries (right and left of it around the text,
 * every band's in the right one's name), the commentators' names, and the targum beside the text (square letters).
 */
internal const val TEXT_COLUMN = "G"
internal const val RIGHT_COLUMN = "R"
internal const val LEFT_COLUMN = "L"
internal const val NAME_COLUMN = "H"
internal const val TARGUM_COLUMN = "O"

/** A page's width in points: the Talmud's block, which the daf's letter sizes are made for. */
internal const val BOOK_DAF_WIDTH = 412f

/** A page holds as many lines of the text as keep it no taller than this (points), one at least. */
internal const val BOOK_DAF_HEIGHT = BOOK_DAF_WIDTH * 1.5f

/** The space between two columns (points). */
internal const val BOOK_DAF_GAP = 9f

/** A line's pitch to its box's height. */
internal const val LINE_PITCH = 1.25f

/** A line is broken before the word that would take it past this much of its box: the setting's letters may differ. */
internal const val LINE_FILL = 0.97f

/** A paragraph's last line narrower than this much of its box keeps its own width, from the box's right edge. */
private const val SHORT_LAST = 0.85f

/**
 * A book's lines [text] in pages around the text ([DafAroundSetter]), each with the comments of [commentaries] on its
 * lines: as many lines a page as keep it no taller than [BOOK_DAF_HEIGHT], one at least, and a heading never last but on
 * the last page.
 */
internal fun dafPagesAround(
    text: List<DafSegment>,
    commentaries: List<DafCommentary>,
    widths: WordWidths,
): List<SetPage> {
    val setter = DafAroundSetter(widths)
    val pages = ArrayList<SetPage>()
    var start = 0
    while (start < text.size) {
        var end = start + 1
        var page = setter.page(text.subList(start, end), commentaries)
        while (end < text.size) {
            val longer = setter.page(text.subList(start, end + 1), commentaries)
            if (longer.height > BOOK_DAF_HEIGHT && !text[end - 1].isHeading) break
            page = longer
            end++
        }
        pages += page
        start = end
    }
    return pages
}

/** A page as set: its lines, in the order they are read, its height, and the rules drawn between its parts. */
internal class SetPage(
    val lines: List<DafLine>,
    val height: Float,
    val rules: List<PageRule> = emptyList(),
    /** The words that open a comment, printed in the text's letters (in bands): by their line's index in [lines]. */
    val openings: Map<Int, Set<Int>> = emptyMap(),
) {
    fun amud(name: String) =
        DafAmud(
            amud = name,
            page = listOf(BOOK_DAF_WIDTH, height),
            block = listOf(0f, 0f, BOOK_DAF_WIDTH, height),
            lines = lines,
        )
}

/**
 * A word as set: its text, the library line it is of (none for a name), whether it is bold, and whether it opens a
 * comment in the text's letters ([lead], in bands).
 */
internal class SetWord(
    val text: String,
    val lineId: Long?,
    val bold: Boolean,
    val lead: Boolean = false,
)

/** The words of [segment] as set, tied to its line. */
internal fun setWords(segment: DafSegment): List<SetWord> =
    segment.words.mapIndexed { i, w -> SetWord(w, segment.lineId, i in segment.bold) }

/** A name's words as set, bold and tied to no line. */
internal fun nameWords(name: String): List<SetWord> = name.split(' ').filter { it.isNotEmpty() }.map { SetWord(it, null, bold = true) }

/**
 * The words of [words] from [first] on that fit a line [room] points wide, one at least: the last of them, and their
 * width, in square letters or Rashi script ([square]) [scale] times the size of the page's.
 */
internal fun WordWidths.fit(
    words: List<SetWord>,
    first: Int,
    room: Float,
    square: Boolean,
    scale: Float = 1f,
): Pair<Int, Float> {
    val space = space(square) * scale
    var last = first
    var width = of(words[first].text, square, words[first].bold) * scale
    while (last + 1 < words.size) {
        val next = space + of(words[last + 1].text, square, words[last + 1].bold) * scale
        if (width + next > room) break
        width += next
        last++
    }
    return last to width
}

/**
 * Where a line of words [width] points wide is set between [x0] and [x1]: across, spread over the line as printed; in
 * the middle ([centred], a heading or a name); or, a paragraph's short last line ([isEnd]), from the right.
 */
internal fun lineSpan(
    x0: Float,
    x1: Float,
    width: Float,
    centred: Boolean,
    isEnd: Boolean,
): Pair<Float, Float> =
    when {
        centred -> ((x0 + x1) / 2).let { mid -> mid - width / LINE_FILL / 2 to mid + width / LINE_FILL / 2 }
        isEnd && width < (x1 - x0) * SHORT_LAST -> x1 - width / LINE_FILL to x1
        else -> x0 to x1
    }

/** A printed line of [words] in [box]: its bold words, and each library line's words among them. */
internal fun dafLine(
    column: String,
    words: List<SetWord>,
    box: List<Float>,
): DafLine {
    val spans = ArrayList<List<Long>>()
    words.forEachIndexed { i, word ->
        val id = word.lineId ?: return@forEachIndexed
        val last = spans.lastOrNull()
        if (last != null && last[0] == id && last[2] == i - 1L) {
            spans[spans.lastIndex] = listOf(id, last[1], i.toLong())
        } else {
            spans += listOf(id, i.toLong(), i.toLong())
        }
    }
    return DafLine(
        c = column,
        b = box,
        t = words.joinToString(" ") { it.text },
        d = words.indices.filter { words[it].bold },
        u = spans,
    )
}
