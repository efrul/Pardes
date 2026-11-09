package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt

/** The size of a line's letters to its box's height: the box spans the letters' ascenders and descenders. */
private const val GEMARA_SIZE = 0.92f
private const val RASHI_SIZE = 0.95f
private const val BIG_SIZE = 0.9f

/**
 * A line's usual box height to its tractate's block width, as printed: the Shas is set in one size of square letters
 * and one of Rashi script, some 86 of the Gemara's letters to the block's width, whatever a scan's own scale.
 */
private const val GEMARA_BOX = 10f / 412f
private const val COMMENTARY_BOX = 7.5f / 412f

/**
 * A line's letters are not made smaller than this much of its column's for its box's sake (a big word's than
 * [MIN_SIZE] pixels): its spaces then narrower by at most [SQUEEZE] of one, and the line past its box's left edge into
 * the room there (its box the alignment's, too narrow for its words), smaller only when that room is too narrow too.
 * No more tries than [MAX_TRIES] to make them fit, each after the first at least [FIT_STEP] smaller.
 */
private const val LEAST_SIZE = 0.75f
private const val MIN_SIZE = 4f
private const val SQUEEZE = 0.5f
private const val MAX_TRIES = 3
private const val FIT_STEP = 0.995f

/** Two lines one below the other: their centres a line apart give or take this much of it, and across overlapping this. */
private const val PITCH_SLACK = 0.3f
private const val SAME_COLUMN = 0.4f

/** A line's edge this near (points) to its run's is the run's: a run is a box as wide as its lines. */
private const val EDGE_SLACK = 10f

/** A column's lines are more than this apart, to their height, to be one below the other (and less than the most). */
private const val LEAST_PITCH = 0.75f
private const val MOST_PITCH = 1.8f

/**
 * A box this much wider than its words at its column's usual spaces is the alignment's (its left edge in another
 * column, or words it missed): the line is set as wide as its words at those spaces, from its right edge.
 */
private const val SPARSE = 1.5f

/**
 * A big word a line's box takes this much of the line's height of is in the line (its words on both sides of it, as
 * printed: a מתני׳ in the middle of a line); the spaces of the words beside it are widened to at most [MOST_EXTRA] of
 * a letter each.
 */
private const val ACROSS = 0.3f
private const val MOST_EXTRA = 1.5f

/**
 * A line whose words at its column's usual spaces are this much wider than its box holds two printed lines' words, when
 * a line's place beside it is empty (no text in it more than [EMPTY_SLACK] of a line high) and inside its column; it is
 * then as wide as its column's lines within [NEAR_LINES] lines of it. Else a line too wide for its box takes the room
 * left of it, up to [COLUMN_GAP] points from the text there.
 */
private const val OVERFULL = 1.7f
private const val EMPTY_SLACK = 0.3f
private const val NEAR_LINES = 3f
private const val COLUMN_GAP = 3f

/** A letter whose width is a space's, by the width two of them gain with one between them. */
private const val SPACE_PROBE = "א"

/**
 * The usual height of a line's box in a tractate, in points: the [gemara]'s and a [commentary]'s. One for all its
 * amudim, whose own boxes are as tall as each scan made them: its letters are then one size on every amud.
 */
internal data class DafSizes(
    val gemara: Float,
    val commentary: Float,
) {
    companion object {
        /** A tractate's, from its usual block's width in points. */
        fun of(blockWidth: Float) = DafSizes(blockWidth * GEMARA_BOX, blockWidth * COMMENTARY_BOX)
    }
}

/**
 * Lays out every line of [amud] at [scale] pixels a point, in runs of lines (a column's lines one below the other at
 * its pitch) that are each drawn as one text: one size of letters for a column (its tractate's with [sizes], else its
 * own usual line's), made smaller for a line only while its words do not fit its box with a normal space between them,
 * then spread so the line fills its box, as printed. A column's big first word is a run of its own, in square letters
 * as printed, and a line it stands in has its words on both sides of it. Where the alignment's box is wrong for its
 * words, the line is still read: its letters no smaller than [LEAST_SIZE] of the column's but for room's sake (the
 * line past its box), its spaces no wider than its column's usual ones ([SPARSE]), two printed lines' words in one set
 * in two ([OVERFULL]). [beforeEachLine] is called before each line is measured, to stop the layout by throwing (a
 * coroutine's ensureActive).
 */
internal fun layoutAmud(
    amud: DafAmud,
    scale: Float,
    measurer: TextMeasurer,
    density: Density,
    gemara: FontFamily,
    rashi: FontFamily,
    sizes: DafSizes? = null,
    beforeEachLine: () -> Unit = {},
): AmudLayout {
    val setter = AmudSetter(amud, scale, measurer, density, beforeEachLine)
    val columns =
        listOf("G", "R", "T").mapNotNull { column ->
            val isGemara = column == "G"
            val usual = sizes?.let { if (isGemara) it.gemara else it.commentary }
            setter.column(column, if (isGemara) gemara else rashi, isGemara, usual?.times(scale))
        }
    // Drawn last, on top of the runs whose lines they stand beside
    val bigRuns = columns.flatMap { it.bigRuns(gemara) }
    // Where every column's words are first, the boxes wrong for them made right: then the room a line may take is known
    columns.forEach { it.place() }
    return AmudLayout(amud, scale, setter.width, setter.height, columns.flatMap { it.runs() } + bigRuns)
}

/** A line as set: its letters' [size], the [extra] after each of its spaces (or [gaps], each space's own), its [width]. */
private class LineSet(
    val size: Float,
    val extra: Float,
    val gaps: FloatArray?,
    val width: Float,
)

/**
 * Words of the amud's [line], from its word [first] on ([bold] the dibbur's among them), the [box] they are set in
 * (the line's, but where that is wrong for them) and how they are [set] there.
 */
private class Piece(
    val line: Int,
    val first: Int,
    val words: List<String>,
    val bold: Set<Int>,
    val box: FloatArray,
    val set: LineSet,
) {
    val centre get() = (box[1] + box[3]) / 2f

    /** The piece as a line of a run whose right edge is [right]. */
    fun runLine(
        right: Float,
        unsure: Boolean,
    ) = RunLine(line, words, bold, set.size, right - box[2], set.extra, unsure, set.gaps, first)
}

/** An amud's lines being set: their boxes in the block's pixels, and where their words are. */
private class AmudSetter(
    amud: DafAmud,
    scale: Float,
    private val measurer: TextMeasurer,
    private val density: Density,
    val beforeEachLine: () -> Unit,
) {
    val lines = amud.lines
    val width = (amud.block[2] - amud.block[0]) * scale
    val height = (amud.block[3] - amud.block[1]) * scale
    val edgeSlack = EDGE_SLACK * scale
    private val columnGap = COLUMN_GAP * scale

    /** Each line's box, in the block's pixels. */
    val boxes =
        Array(lines.size) { i ->
            val (x0, y0) = amud.block
            lines[i].b.let { floatArrayOf((it[0] - x0) * scale, (it[1] - y0) * scale, (it[2] - x0) * scale, (it[3] - y0) * scale) }
        }

    /** Where each line's words are: its box, but where that is wrong for them; and where some of a line's moved to. */
    val places = boxes.copyOf()
    val moved = ArrayList<FloatArray>()

    // A space's width at a size
    private val spaces = HashMap<Pair<FontFamily, Float>, Float>()

    fun centre(i: Int) = boxes[i].let { (it[1] + it[3]) / 2f }

    fun words(i: Int) = lines[i].t.split(' ').filter { it.isNotEmpty() }

    /**
     * The column [column]'s lines, in [family] ([isGemara]: the Gemara's square letters), or null when it has none.
     * [usual]: its tractate's usual box height in pixels, when its letters are the tractate's size.
     */
    fun column(
        column: String,
        family: FontFamily,
        isGemara: Boolean,
        usual: Float? = null,
    ): ColumnSetter? {
        val indices = lines.indices.filter { lines[it].c == column && lines[it].t.isNotBlank() }
        return if (indices.isEmpty()) null else ColumnSetter(this, column, family, isGemara, indices, usual)
    }

    /** The width of [words] at [size], a normal space between two, those in [bold] bold. */
    fun width(
        words: List<String>,
        bold: Set<Int>,
        family: FontFamily,
        size: Float,
    ): Float {
        val text =
            buildAnnotatedString {
                words.forEachIndexed { i, w ->
                    if (i > 0) append(' ')
                    if (i in bold) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(w)
                        pop()
                    } else {
                        append(w)
                    }
                }
            }
        val style = TextStyle(fontFamily = family, fontSize = with(density) { size.toSp() })
        val result = measurer.measure(text, style, softWrap = false, maxLines = 1, layoutDirection = LayoutDirection.Rtl, density = density)
        return result.multiParagraph.getLineRight(0) - result.multiParagraph.getLineLeft(0)
    }

    fun space(
        family: FontFamily,
        size: Float,
    ): Float =
        spaces.getOrPut(family to size) {
            width(listOf(SPACE_PROBE, SPACE_PROBE), emptySet(), family, size) -
                width(listOf(SPACE_PROBE + SPACE_PROBE), emptySet(), family, size)
        }

    /**
     * [words] set in [boxWidth] from its right edge: letters [size] but smaller while they do not fit (not below
     * [least]), then narrower spaces, then past the box; wider spaces to fill it. [natural]: their width at [size].
     */
    fun set(
        words: List<String>,
        bold: Set<Int>,
        family: FontFamily,
        size: Float,
        least: Float,
        boxWidth: Float,
        natural: Float = width(words, bold, family, size),
    ): LineSet {
        var s = size
        var w = natural
        var tries = 0
        while (w > boxWidth && s > least && tries++ < MAX_TRIES) {
            s = maxOf(least, s * minOf(boxWidth / w, FIT_STEP))
            w = width(words, bold, family, s)
        }
        val gaps = words.size - 1
        if (gaps <= 0) return LineSet(s, 0f, null, w)
        val extra = ((boxWidth - w) / gaps).let { if (it < 0f) maxOf(it, -SQUEEZE * space(family, s)) else it }
        return LineSet(s, extra, null, w + extra * gaps)
    }

    /** Where the text nearest left of the box [b] at its height ends (not the line [self]'s; else the block's edge). */
    fun leftLimit(
        b: FloatArray,
        self: Int,
    ): Float {
        fun isLeft(o: FloatArray) = o[2] <= b[0] + edgeSlack && minOf(o[3], b[3]) - maxOf(o[1], b[1]) > ACROSS * (b[3] - b[1])
        val left = lines.indices.filter { it != self && lines[it].t.isNotBlank() }.map { places[it] } + moved
        return left.filter(::isLeft).maxOfOrNull { it[2] + columnGap } ?: 0f
    }

    /** Whether no words (but the line [self]'s) are in [place] more than [EMPTY_SLACK] of [pitch] high. */
    fun isEmpty(
        place: FloatArray,
        self: Int,
        pitch: Float,
    ): Boolean {
        fun takes(o: FloatArray) = o[0] < place[2] && o[2] > place[0] && minOf(o[3], place[3]) - maxOf(o[1], place[1]) > EMPTY_SLACK * pitch
        return lines.indices.none { it != self && lines[it].t.isNotBlank() && takes(places[it]) } && moved.none(::takes)
    }
}

/** A column of an amud being set, its lines' [indices]: one size of letters ([size]), one pitch. */
private class ColumnSetter(
    private val amud: AmudSetter,
    private val column: String,
    private val family: FontFamily,
    private val isGemara: Boolean,
    indices: List<Int>,
    tractateUsual: Float? = null,
) {
    private val lines = amud.lines
    private val bigLines = indices.filter { lines[it].isBig }
    private val normal = indices.filter { !lines[it].isBig }
    private val bigBoxes = bigLines.map { amud.boxes[it] }
    private val words = normal.associateWith(amud::words)

    // The column's letters, from its usual line's height: its tractate's, else its own sure lines' (those the
    // alignment placed best)
    private val usual =
        tractateUsual
            ?: (normal.filter { lines[it].s == 1 }.ifEmpty { normal })
                .map { amud.boxes[it].let { b -> b[3] - b[1] } }
                .sorted()
                .let { it.getOrElse(it.size / 2) { 0f } }
    private val size = usual * if (isGemara) GEMARA_SIZE else RASHI_SIZE
    private val least = size * LEAST_SIZE

    // Its pitch: the usual distance between two of its lines one below the other
    private val pitch =
        normal
            .zipWithNext()
            .map { (a, b) -> amud.centre(b) - amud.centre(a) }
            .filter { it > LEAST_PITCH * usual && it < MOST_PITCH * usual }
            .sorted()
            .let { it.getOrNull(it.size / 2) } ?: (usual / LEAST_PITCH)

    // Each line's words' width at the column's size, and the big word it runs across
    private val natural =
        normal.associateWith { i ->
            amud.beforeEachLine()
            amud.width(words.getValue(i), lines[i].d.toSet(), family, size)
        }
    private val across = normal.associateWith { acrossOf(amud.boxes[it], bigBoxes, amud.edgeSlack) }

    // The usual width of a sure line's box to its words' (the print's spaces, and its letters' width to ours)
    private val usualRatio =
        normal
            .filter { lines[it].s == 1 && across[it] == null && words.getValue(it).size > 1 }
            .map { amud.boxes[it].let { b -> (b[2] - b[0]) / natural.getValue(it) } }
            .sorted()
            .let { it.getOrNull(it.size / 2) } ?: 1f

    // A line on both sides of the big word it runs across: where, and how (null: as any line there)
    private val arounds = HashMap<Int, Pair<FloatArray, LineSet?>>()

    /** The column's big first words: each a run of its own in square letters as printed, its box's height. */
    fun bigRuns(gemara: FontFamily): List<TextRun> =
        bigLines.map { i ->
            amud.beforeEachLine()
            val b = amud.boxes[i]
            val words = amud.words(i)
            val bold = lines[i].d.toSet()
            val set = amud.set(words, bold, gemara, (b[3] - b[1]) * BIG_SIZE, MIN_SIZE, b[2] - b[0])
            val line = RunLine(i, words, bold, set.size, 0f, set.extra, lines[i].s != 1)
            TextRun(column, b[0], b[1], b[2] - b[0], b[3] - b[1], set.size, isGemara = true, isBig = true, listOf(line))
        }

    /** Where its lines' words are, where the alignment's boxes are wrong for them: beside a big word, or too wide. */
    fun place() {
        for (i in normal) {
            val b = amud.boxes[i]
            val big = across[i]
            if (big != null) {
                arounds[i] = around(i, b, big).also { amud.places[i] = it.first }
            } else if (isSparse(i)) {
                amud.places[i] = floatArrayOf(b[2] - wide(i), b[1], b[2], b[3])
            }
        }
    }

    /** Its lines set, in runs. */
    fun runs(): List<TextRun> = group(pieces())

    // The line's words' width at the column's usual spaces
    private fun wide(i: Int) = natural.getValue(i) * usualRatio

    private fun isSparse(i: Int) = words.getValue(i).size > 1 && amud.boxes[i].let { it[2] - it[0] } > wide(i) * SPARSE

    // Each line in the pieces it is set in: one where its words are, or two for two printed lines' words in one
    private fun pieces(): List<Piece> =
        normal.flatMap { i ->
            val words = words.getValue(i)
            val bold = lines[i].d.toSet()
            val place = amud.places[i]
            val around = arounds[i]
            val split = around?.second
            when {
                split != null -> listOf(Piece(i, 0, words, bold, place, split))
                around == null && !isSparse(i) && words.size > 1 && wide(i) > (place[2] - place[0]) * OVERFULL ->
                    inTwo(i, words, bold) ?: listOf(Piece(i, 0, words, bold, place, setIn(i, words, bold, place, natural.getValue(i))))
                else -> listOf(Piece(i, 0, words, bold, place, setIn(i, words, bold, place, natural.getValue(i))))
            }
        }

    // Two printed lines' words in the line [i] (its box too narrow for them) beside an empty line's place: the first
    // half in the upper line, the others in the lower, each as wide as its column's lines near them; null with no place
    private fun inTwo(
        i: Int,
        words: List<String>,
        bold: Set<Int>,
    ): List<Piece>? {
        val b = amud.boxes[i]
        val extent = extentNear(i)
        val direction = listOf(-1, 1).firstOrNull { emptyBeside(i, it, extent) } ?: return null
        val heads = FloatArray(words.size) { if (it == 0) 0f else amud.width(words.subList(0, it), bold, family, size) }
        val j = (1 until words.size).minBy { abs(heads[it] - natural.getValue(i) / 2f) }
        val h = b[3] - b[1]
        val other = amud.centre(i) + direction * pitch
        val own = floatArrayOf(extent[0], b[1], extent[2], b[3])
        val moved = floatArrayOf(extent[0], other - h / 2f, extent[2], other + h / 2f)
        amud.places[i] = own
        amud.moved += moved
        val (upper, lower) = if (direction < 0) moved to own else own to moved
        val first = words.subList(0, j)
        val rest = words.subList(j, words.size)
        val firstBold = bold.filter { it < j }.toSet()
        val restBold = bold.filter { it >= j }.map { it - j }.toSet()
        return listOf(
            Piece(i, 0, first, firstBold, upper, setIn(i, first, firstBold, upper)),
            Piece(i, j, rest, restBold, lower, setIn(i, rest, restBold, lower)),
        )
    }

    // The column's edges near the line [i]: the usual ones of its lines within a few lines of it, and its own
    private fun extentNear(i: Int): FloatArray {
        val b = amud.boxes[i]
        val near = normal.filter { it != i && abs(amud.centre(it) - amud.centre(i)) <= NEAR_LINES * pitch }.map { amud.places[it] }
        if (near.isEmpty()) return b
        val lefts = near.map { it[0] }.sorted()
        val rights = near.map { it[2] }.sorted()
        return floatArrayOf(minOf(b[0], lefts[lefts.size / 2]), b[1], maxOf(b[2], rights[rights.size / 2]), b[3])
    }

    // Whether the line's place [direction] of the line [i] (-1 above, 1 below), across [extent], is empty of any words
    // and inside the column (a line of it beyond)
    private fun emptyBeside(
        i: Int,
        direction: Int,
        extent: FloatArray,
    ): Boolean {
        val c = amud.centre(i) + direction * pitch
        val place = floatArrayOf(extent[0], c - pitch / 2f, extent[2], c + pitch / 2f)
        if (place[1] < 0f || place[3] > amud.height) return false
        val inside = normal.any { abs(amud.centre(it) - (c + direction * pitch)) < pitch / 2f }
        return inside && amud.isEmpty(place, i, pitch)
    }

    // [words] set in [b] (the line [i]'s place): past its left edge into the room there when they are too wide for it,
    // their letters smaller only for room's sake. [natural]: their width at the column's size
    private fun setIn(
        i: Int,
        words: List<String>,
        bold: Set<Int>,
        b: FloatArray,
        natural: Float = amud.width(words, bold, family, size),
    ): LineSet {
        val set = amud.set(words, bold, family, size, least, b[2] - b[0], natural)
        if (set.width <= b[2] - b[0]) return set
        val room = b[2] - amud.leftLimit(b, i)
        return if (set.width <= room) set else amud.set(words, bold, family, size, MIN_SIZE, room, natural)
    }

    // The line [i]'s words in its box [b] with the big word [big] among them: as many of them right of it as fill the
    // place there best, the others left of it, each side filling its place, a space between them and the big word. The
    // box the line takes, with how it is set there, or null to set it there as any line (all its words on one side)
    private fun around(
        i: Int,
        b: FloatArray,
        big: FloatArray,
    ): Pair<FloatArray, LineSet?> {
        val words = words.getValue(i)
        val bold = lines[i].d.toSet()
        val n = words.size
        val sw = amud.space(family, size)
        val rightRoom = b[2] - big[2] - sw
        val leftRoom = big[0] - sw - b[0]

        fun head(
            j: Int,
            s: Float,
        ) = if (j == 0) 0f else amud.width(words.subList(0, j), bold, family, s)

        fun tail(
            j: Int,
            s: Float,
        ) = if (j == n) 0f else amud.width(words.subList(j, n), bold.mapNotNull { (it - j).takeIf { at -> at >= 0 } }.toSet(), family, s)
        val heads = FloatArray(n + 1) { head(it, size) }
        val j = (0..n).minBy { maxOf(fullness(heads[it], rightRoom), fullness(heads[n] - heads[it], leftRoom)) }
        if (j == 0) return floatArrayOf(b[0], b[1], big[0] - sw, b[3]) to null
        if (j == n) return floatArrayOf(big[2] + sw, b[1], b[2], b[3]) to null
        var s = size
        var right = heads[j]
        var left = tail(j, s)
        val fit = minOf(rightRoom / right, leftRoom / left)
        if (fit < 1f) {
            s = maxOf(least, size * fit * FIT_STEP)
            right = head(j, s)
            left = tail(j, s)
        }
        val space = amud.space(family, s)
        val rightExtra = spread(rightRoom - right, j - 1, -SQUEEZE * space, MOST_EXTRA * s)
        val leftExtra = spread(leftRoom - left, n - j - 1, -SQUEEZE * space, MOST_EXTRA * s)
        // From the left end of the words right of the big word to the right end of those left of it
        val rightEnd = b[2] - right - rightExtra * (j - 1)
        val leftStart = big[0] - sw
        val jump = maxOf(0f, rightEnd - leftStart - space)
        val gaps =
            FloatArray(n - 1) { g ->
                when {
                    g < j - 1 -> rightExtra
                    g == j - 1 -> jump
                    else -> leftExtra
                }
            }
        val width = b[2] - minOf(leftStart, rightEnd - space - jump) + left + leftExtra * (n - j - 1)
        return b to LineSet(s, 0f, gaps, width)
    }

    // Runs: lines one below the other a line apart, or two with a line's place left empty between, all as wide (a run
    // is a box no other column's lines are in: a commentary's lines above the Gemara, wider, are a run of their own); a
    // line beside a big first word is as wide as the others with the big word
    private fun group(pieces: List<Piece>): List<TextRun> {
        val runs = ArrayList<TextRun>()
        val slots = ArrayList<Piece?>()
        var runLeft = 0f
        var runRight = 0f

        fun asWide(b: FloatArray): Boolean =
            abs(b[0] - runLeft) <= amud.edgeSlack && (abs(b[2] - runRight) <= amud.edgeSlack || (b[2] < runRight && besideBig(b)))

        fun flush() {
            val real = slots.filterNotNull()
            if (real.isEmpty()) return
            // As wide as its lines as set, one past its box too
            val left = real.minOf { minOf(it.box[0], it.box[2] - it.set.width) }
            val right = maxOf(real.maxOf { it.box[2] }, runRight)
            val runLines = slots.map { it?.runLine(right, lines[it.line].s != 1) }
            runs += TextRun(column, left, real.first().centre - pitch / 2f, right - left, pitch, size, isGemara, isBig = false, runLines)
            slots.clear()
        }
        var previous: Piece? = null
        for (piece in pieces) {
            val b = piece.box
            previous?.let { above ->
                val below = linesBelow(above, piece)
                if (below == null || !asWide(b)) {
                    flush()
                } else if (below == 2) {
                    slots += null
                }
            }
            if (slots.isEmpty()) {
                // A run's edges are its first line's, but for a line beside a big word, its right edge the big word's
                runLeft = b[0]
                runRight = if (besideBig(b)) bigBoxes.filter { it[1] < b[3] && it[3] > b[1] }.maxOf { it[2] } else b[2]
            }
            slots += piece
            previous = piece
        }
        flush()
        return runs
    }

    private fun besideBig(b: FloatArray) = bigBoxes.any { big -> big[1] < b[3] && big[3] > b[1] && big[0] >= b[2] - amud.edgeSlack }

    // How many lines below [above] the piece [piece] is, one or two (a line's place left empty between), side by side
    // with it; null when it is not
    private fun linesBelow(
        above: Piece,
        piece: Piece,
    ): Int? {
        val gap = piece.centre - above.centre
        val n = (gap / pitch).roundToInt()
        val sideBySide = minOf(above.box[2], piece.box[2]) - maxOf(above.box[0], piece.box[0])
        val narrower = maxOf(1e-3f, minOf(above.box[2] - above.box[0], piece.box[2] - piece.box[0]))
        return n.takeIf { it in 1..2 && abs(gap - n * pitch) < PITCH_SLACK * pitch && sideBySide / narrower > SAME_COLUMN }
    }
}

/** How full words [width] wide make a [room]: 0 for none, more than 1 too full, the most for no room. */
private fun fullness(
    width: Float,
    room: Float,
) = when {
    width == 0f -> 0f
    room <= 0f -> Float.MAX_VALUE
    else -> width / room
}

/** The extra after each of [gaps] spaces to fill [spare] room more than their words take, between [least] and [most]. */
private fun spread(
    spare: Float,
    gaps: Int,
    least: Float,
    most: Float,
) = if (gaps <= 0) 0f else (spare / gaps).coerceIn(least, most)

/**
 * The big word of [bigBoxes] the line in the box [b] runs across, its words on both sides of it (not one it starts
 * beside: a column's big first word), or null: the one that takes most of its height, at least [ACROSS] of it.
 */
private fun acrossOf(
    b: FloatArray,
    bigBoxes: List<FloatArray>,
    edgeSlack: Float,
): FloatArray? {
    fun overlap(big: FloatArray) = minOf(big[3], b[3]) - maxOf(big[1], b[1])
    // Across it, not above or below it: a big word is about two lines tall, and the line above or below its own
    // overlaps its box too, but its middle is outside it (144 such lines were pushed aside)
    val middle = (b[1] + b[3]) / 2
    return bigBoxes
        .filter { big ->
            overlap(big) > ACROSS * (b[3] - b[1]) && middle in big[1]..big[3] && big[0] < b[2] - edgeSlack && big[2] > b[0]
        }.maxByOrNull(::overlap)
}
