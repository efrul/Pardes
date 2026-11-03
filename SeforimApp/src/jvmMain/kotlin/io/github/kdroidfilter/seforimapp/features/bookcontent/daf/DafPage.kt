package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt

/** A word of an amud: the index of its line in [DafAmud.lines], and its own among the line's words. */
data class DafWord(
    val line: Int,
    val word: Int,
)

/** The color a word of the amud is marked with (behind it, as a highlighter), or null for none. */
fun interface DafHighlight {
    fun colorOf(word: DafWord): Color?

    companion object {
        val NONE = DafHighlight { null }
    }
}

/**
 * A printed line in a run: the amud's line [index], its [words] (those in [bold] a dibbur hamatchil), its letters
 * [size] pixels, set [indent] pixels in from the run's right edge, with [extra] pixels more after each of its spaces so
 * that it fills its box, as printed. [unsure] when the alignment is not sure of its first or last word.
 */
internal class RunLine(
    val index: Int,
    val words: List<String>,
    val bold: Set<Int>,
    val size: Float,
    val indent: Float,
    val extra: Float,
    val unsure: Boolean,
)

/**
 * Lines of one column one below the other, [pitch] apart, from [top] down: drawn as one text, so a selection goes down
 * the column as it is read, each line a paragraph of its own (null, a line's place left empty). Its box is [left] and
 * [width] wide; [size] is its letters', [isGemara] whether they are the Gemara's (a square letter) or a commentary's,
 * [isBig] for a column's big first word. All in pixels at the scale the amud was laid out at.
 */
internal class TextRun(
    val left: Float,
    val top: Float,
    val width: Float,
    val pitch: Float,
    val size: Float,
    val isGemara: Boolean,
    val isBig: Boolean,
    val lines: List<RunLine?>,
) {
    /** The run's words, a space between two of them and between two lines, which a copy keeps. */
    val text: String

    /** Where each line's paragraph starts in [text], and each of its words. */
    private val lineStarts = IntArray(lines.size)
    private val wordStarts: Array<IntArray>

    init {
        val sb = StringBuilder()
        wordStarts =
            Array(lines.size) { slot ->
                lineStarts[slot] = sb.length
                val line = lines[slot]
                val starts =
                    if (line == null) {
                        sb.append(' ')
                        IntArray(0)
                    } else {
                        IntArray(line.words.size) { i ->
                            if (i > 0) sb.append(' ')
                            sb.length.also { sb.append(line.words[i]) }
                        }
                    }
                if (slot < lines.lastIndex) sb.append(' ')
                starts
            }
        text = sb.toString()
    }

    /** The characters of [word], a word of the line in [slot]. */
    fun rangeOf(
        slot: Int,
        word: Int,
    ): IntRange {
        val start = wordStarts[slot][word]
        return start until start + lines[slot]!!.words[word].length
    }

    /** The word at character [offset] of [text] (or the space after it), or null in an empty line's place. */
    fun wordAt(offset: Int): DafWord? {
        val slot = lineStarts.binarySearchFloor(offset)
        val line = lines.getOrNull(slot) ?: return null
        val word = wordStarts[slot].binarySearchFloor(offset).coerceAtLeast(0)
        return DafWord(line.index, word)
    }

    /** The text drawn [k] times its laid-out size, the [density] the run was laid out with. */
    fun annotated(
        k: Float,
        density: Density,
        unsureInk: Color,
    ): AnnotatedString =
        with(density) {
            val lineHeight = (pitch * k).toSp()
            buildAnnotatedString {
                append(text)
                lines.forEachIndexed { slot, line ->
                    val start = lineStarts[slot]
                    val end = lineStarts.getOrNull(slot + 1) ?: text.length
                    val indent = line?.indent?.takeIf { it > 0f }?.let { TextIndent(firstLine = (it * k).toSp()) }
                    addStyle(ParagraphStyle(textIndent = indent, lineHeight = lineHeight, lineHeightStyle = CENTERED), start, end)
                    if (line == null) return@forEachIndexed
                    if (abs(line.size - size) > SAME_SIZE) addStyle(SpanStyle(fontSize = (line.size * k).toSp()), start, end)
                    if (line.unsure) addStyle(SpanStyle(color = unsureInk), start, end)
                    val starts = wordStarts[slot]
                    for (i in line.words.indices) {
                        val wordEnd = starts[i] + line.words[i].length
                        if (i in line.bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), starts[i], wordEnd)
                        if (i < line.words.lastIndex && line.extra != 0f) {
                            addStyle(SpanStyle(letterSpacing = (line.extra * k).toSp()), wordEnd, wordEnd + 1)
                        }
                    }
                }
            }
        }

    private companion object {
        val CENTERED = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

        /** Sizes this near are one (pixels). */
        const val SAME_SIZE = 0.01f

        /** The index of the last value not above [value]. */
        fun IntArray.binarySearchFloor(value: Int): Int {
            var lo = 0
            var hi = size - 1
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (this[mid] <= value) {
                    found = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return found
        }
    }
}

/** An amud laid out at [scale] pixels a point (its block [width] by [height] pixels): its runs of lines. */
internal class AmudLayout(
    val amud: DafAmud,
    val scale: Float,
    val width: Float,
    val height: Float,
    val runs: List<TextRun>,
)

/** The size of a line's letters to its box's height: the box spans the letters' ascenders and descenders. */
private const val GEMARA_SIZE = 0.92f
private const val RASHI_SIZE = 0.95f
private const val BIG_SIZE = 0.9f

/** Below this a line's letters are not made smaller (pixels), and no more tries than this to make them fit. */
private const val MIN_SIZE = 4f
private const val MAX_TRIES = 3

/** Each try after the first makes the letters at least this much smaller. */
private const val FIT_STEP = 0.995f

/** Two lines one below the other: their centres a line apart give or take this much of it, and across overlapping this. */
private const val PITCH_SLACK = 0.3f
private const val SAME_COLUMN = 0.4f

/** A line's edge this near (points) to its run's is the run's: a run is a box as wide as its lines. */
private const val EDGE_SLACK = 10f

/** A column's lines are more than this apart, to their height, to be one below the other (and less than the most). */
private const val LEAST_PITCH = 0.75f
private const val MOST_PITCH = 1.8f

/** A line the alignment is not sure of is drawn lighter: its words may not all be the print's. */
internal const val UNSURE_INK = 0.55f

/**
 * Lays out every line of [amud] at [scale] pixels a point, in runs of lines (a column's lines one below the other at
 * its pitch) that are each drawn as one text: one size of letters for a column (its usual line's), made smaller for a
 * line only while its words do not fit its box with a normal space between them, then spread so the line fills its
 * box, as printed. A column's big first word is a run of its own, in square letters as printed. [beforeEachLine] is
 * called before each line is measured, to stop the layout by throwing (a coroutine's ensureActive).
 */
internal fun layoutAmud(
    amud: DafAmud,
    scale: Float,
    measurer: TextMeasurer,
    density: Density,
    gemara: FontFamily,
    rashi: FontFamily,
    beforeEachLine: () -> Unit = {},
): AmudLayout {
    val bx = amud.block[0]
    val by = amud.block[1]
    val lines = amud.lines

    fun box(i: Int) =
        lines[i].b.let { floatArrayOf((it[0] - bx) * scale, (it[1] - by) * scale, (it[2] - bx) * scale, (it[3] - by) * scale) }

    fun centre(i: Int) = box(i).let { (it[1] + it[3]) / 2f }

    fun overlap(
        a: Int,
        b: Int,
    ): Float {
        val x = box(a)
        val y = box(b)
        val inter = minOf(x[2], y[2]) - maxOf(x[0], y[0])
        return inter / maxOf(1e-3f, minOf(x[2] - x[0], y[2] - y[0]))
    }

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

    // One line, measured at [size] and made to fit [boxWidth]: its letters' size and the extra after each space
    fun fit(
        i: Int,
        words: List<String>,
        family: FontFamily,
        size: Float,
        boxWidth: Float,
    ): Pair<Float, Float> {
        val bold = lines[i].d.toSet()
        var s = size
        var natural = width(words, bold, family, s)
        var tries = 0
        while (natural > boxWidth && s > MIN_SIZE && tries++ < MAX_TRIES) {
            s = maxOf(MIN_SIZE, s * minOf(boxWidth / natural, FIT_STEP))
            natural = width(words, bold, family, s)
        }
        val gaps = words.size - 1
        return s to if (gaps > 0) (boxWidth - natural) / gaps else 0f
    }

    val runs = ArrayList<TextRun>()
    // Drawn last, on top of the runs whose lines they stand beside
    val bigRuns = ArrayList<TextRun>()
    val edgeSlack = EDGE_SLACK * scale
    for (column in listOf("G", "R", "T")) {
        val indices = lines.indices.filter { lines[it].c == column && lines[it].t.isNotBlank() }
        val isGemara = column == "G"
        val family = if (isGemara) gemara else rashi
        // A column's big first word: square letters, as printed, its box's height
        for (i in indices.filter { lines[it].isBig }) {
            beforeEachLine()
            val b = box(i)
            val words = lines[i].t.split(' ').filter { it.isNotEmpty() }
            val (size, extra) = fit(i, words, gemara, (b[3] - b[1]) * BIG_SIZE, b[2] - b[0])
            bigRuns +=
                TextRun(
                    b[0],
                    b[1],
                    b[2] - b[0],
                    b[3] - b[1],
                    size,
                    isGemara = true,
                    isBig = true,
                    listOf(
                        RunLine(
                            i,
                            words,
                            lines[i].d.toSet(),
                            size,
                            0f,
                            extra,
                            lines[i].s != 1,
                        ),
                    ),
                )
        }
        val normal = indices.filter { !lines[it].isBig }
        if (normal.isEmpty()) continue
        // The column's letters, from its usual line's height (its sure lines', those the alignment placed best)
        val heights = (normal.filter { lines[it].s == 1 }.ifEmpty { normal }).map { box(it).let { b -> b[3] - b[1] } }.sorted()
        val usual = heights[heights.size / 2]
        val size = usual * if (isGemara) GEMARA_SIZE else RASHI_SIZE
        // Its pitch: the usual distance between two of its lines one below the other
        val gaps =
            normal
                .zipWithNext()
                .map { (a, b) -> centre(b) - centre(a) }
                .filter { it > LEAST_PITCH * usual && it < MOST_PITCH * usual }
                .sorted()
        val pitch = gaps.getOrNull(gaps.size / 2) ?: (usual / LEAST_PITCH)
        // Runs: lines one below the other a line apart, or two with a line's place left empty between, all as wide
        // (a run is a box no other column's lines are in: a commentary's lines above the Gemara, wider, are a run of
        // their own); a line beside a big first word is as wide as the others with the big word
        val bigBoxes = indices.filter { lines[it].isBig }.map { box(it) }
        val slots = ArrayList<Int?>()
        var runLeft = 0f
        var runRight = 0f

        fun besideBig(i: Int): Boolean {
            val b = box(i)
            return bigBoxes.any { big -> big[1] < b[3] && big[3] > b[1] && big[0] >= b[2] - edgeSlack }
        }

        fun asWide(i: Int): Boolean {
            val b = box(i)
            return abs(b[0] - runLeft) <= edgeSlack && (abs(b[2] - runRight) <= edgeSlack || (b[2] < runRight && besideBig(i)))
        }

        fun flush() {
            val real = slots.filterNotNull()
            if (real.isEmpty()) return
            val left = real.minOf { box(it)[0] }
            val right = maxOf(real.maxOf { box(it)[2] }, runRight)
            val top = centre(real.first()) - pitch / 2f
            val runLines =
                slots.map { i ->
                    i?.let {
                        beforeEachLine()
                        val b = box(it)
                        val words = lines[it].t.split(' ').filter { w -> w.isNotEmpty() }
                        val (s, extra) = fit(it, words, family, size, b[2] - b[0])
                        RunLine(it, words, lines[it].d.toSet(), s, right - b[2], extra, lines[it].s != 1)
                    }
                }
            runs += TextRun(left, top, right - left, pitch, size, isGemara, isBig = false, runLines)
            slots.clear()
        }
        var previous = -1
        for (i in normal) {
            if (previous >= 0) {
                val gap = centre(i) - centre(previous)
                val n = (gap / pitch).roundToInt()
                val below = n in 1..2 && abs(gap - n * pitch) < PITCH_SLACK * pitch && overlap(previous, i) > SAME_COLUMN
                if (!below || !asWide(i)) {
                    flush()
                } else if (n == 2) {
                    slots += null
                }
            }
            if (slots.isEmpty()) {
                // A run's edges are its first line's, but for a line beside a big word, its right edge the big word's
                val b = box(i)
                runLeft = b[0]
                runRight = if (besideBig(i)) bigBoxes.filter { it[1] < b[3] && it[3] > b[1] }.maxOf { it[2] } else b[2]
            }
            slots += i
            previous = i
        }
        flush()
    }
    return AmudLayout(amud, scale, (amud.block[2] - bx) * scale, (amud.block[3] - by) * scale, runs + bigRuns)
}

/**
 * An amud drawn from its [layout] at [k] times the size it was laid out at, in the text's own components: each run of
 * lines is a text (selectable, copied, given the text's menu, as the text view's lines are), its words in [gemara] (a
 * square letter) or [rashi] (Rashi script). [highlight] marks words behind them; a click on a word, not a drag that
 * selects, gives it to [onWordClick] (with Ctrl or Cmd held, as the text's lines take them), a right-click to
 * [onContextWord] first, as the menu opens.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DafAmudText(
    layout: AmudLayout,
    k: Float,
    gemara: FontFamily,
    rashi: FontFamily,
    ink: Color,
    modifier: Modifier = Modifier,
    highlight: DafHighlight = DafHighlight.NONE,
    onWordClick: (word: DafWord, withModifier: Boolean) -> Unit = { _, _ -> },
    onContextWord: (DafWord) -> Unit = {},
) {
    val density = LocalDensity.current
    val currentOnWordClick by rememberUpdatedState(onWordClick)
    val currentOnContextWord by rememberUpdatedState(onContextWord)
    val unsureInk = ink.copy(alpha = ink.alpha * UNSURE_INK)
    // Each run where it is printed, as the page's coordinates go (left to right): a layout of its own, as a right to left
    // one mirrors a Box's offsets
    Layout(
        modifier = with(density) { modifier.size((layout.width * k).toDp(), (layout.height * k).toDp()) },
        content = {
            for (run in layout.runs) {
                val text = remember(run, k, density, unsureInk) { run.annotated(k, density, unsureInk) }
                val style =
                    remember(run, k, density, ink, gemara, rashi) {
                        TextStyle(
                            fontFamily = if (run.isGemara) gemara else rashi,
                            fontSize = with(density) { (run.size * k).toSp() },
                            fontWeight = if (run.isBig) FontWeight.Bold else null,
                            color = ink,
                            textDirection = TextDirection.Rtl,
                        )
                    }
                val marks = remember(run, highlight) { marksOf(run, highlight) }
                var textLayout by remember(run) { mutableStateOf<TextLayoutResult?>(null) }

                fun wordAt(position: Offset): DafWord? {
                    val result = textLayout ?: return null
                    if (position.y < 0f || position.y > result.size.height) return null
                    val line = result.getLineForVerticalPosition(position.y)
                    if (position.x < result.getLineLeft(line) - HIT_SLACK || position.x > result.getLineRight(line) + HIT_SLACK) return null
                    return run.wordAt(result.getOffsetForPosition(position))
                }
                // A selection of its own: Compose orders a container's texts by their tops, which would take a drag
                // down one column into another's lines between
                SelectionContainer {
                    BasicText(
                        text = text,
                        style = style,
                        softWrap = false,
                        onTextLayout = { textLayout = it },
                        modifier =
                            Modifier
                                .drawBehind { textLayout?.let { drawMarks(it, marks, run.pitch * k) } }
                                .pointerInput(run) {
                                    awaitEachGesture {
                                        // A click, as the text's lines take it: a drag is the selection's, which consumes it
                                        val down = awaitPointerEvent(PointerEventPass.Main)
                                        val isTouch = down.changes.any { it.type == PointerType.Touch && it.pressed }
                                        if (!down.buttons.isPrimaryPressed && !isTouch) return@awaitEachGesture
                                        if (down.keyboardModifiers.isShiftPressed) return@awaitEachGesture
                                        val withModifier = down.keyboardModifiers.isCtrlPressed || down.keyboardModifiers.isMetaPressed
                                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                        if (up.isConsumed) return@awaitEachGesture
                                        if ((up.position - down.changes.first().position).getDistance() > viewConfiguration.touchSlop) {
                                            return@awaitEachGesture
                                        }
                                        wordAt(up.position)?.let { currentOnWordClick(it, withModifier) }
                                    }
                                }.onPointerEvent(PointerEventType.Press) { event ->
                                    if (event.buttons.isSecondaryPressed) {
                                        wordAt(event.changes.first().position)?.let { currentOnContextWord(it) }
                                    }
                                },
                    )
                }
            }
        },
    ) { measurables, _ ->
        // Room to spare on the left for the space after each line but the last (a copy keeps it between two lines): a
        // text a line of which is wider than it is laid out as wide as that line, past its right edge
        fun spare(run: TextRun) = run.size * RUN_SPARE * k
        val placeables =
            measurables.mapIndexed { i, measurable ->
                val run = layout.runs[i]
                val width = (run.width * k + spare(run)).roundToInt()
                measurable.measure(Constraints(minWidth = width, maxWidth = width))
            }
        layout((layout.width * k).roundToInt(), (layout.height * k).roundToInt()) {
            placeables.forEachIndexed { i, placeable ->
                val run = layout.runs[i]
                placeable.place((run.left * k - spare(run)).roundToInt(), (run.top * k).roundToInt())
            }
        }
    }
}

/** How far off a line a point still counts as on it (pixels), and the room to spare left of a run (to its letters). */
private const val HIT_SLACK = 3f
private const val RUN_SPARE = 0.6f

/** A run's marked words: for each run of words of one color in a line, its first and last characters and the color. */
private fun marksOf(
    run: TextRun,
    highlight: DafHighlight,
): List<Pair<IntRange, Color>> {
    if (highlight === DafHighlight.NONE) return emptyList()
    val out = ArrayList<Pair<IntRange, Color>>()
    run.lines.forEachIndexed { slot, line ->
        if (line == null) return@forEachIndexed
        var i = 0
        while (i < line.words.size) {
            val color = highlight.colorOf(DafWord(line.index, i))
            if (color == null) {
                i++
                continue
            }
            var j = i
            while (j + 1 < line.words.size && highlight.colorOf(DafWord(line.index, j + 1)) == color) j++
            out += (run.rangeOf(slot, i).first..run.rangeOf(slot, j).last) to color
            i = j + 1
        }
    }
    return out
}

/** How far a mark reaches past its words, to the line's height, and how much of the line's height it leaves. */
private const val MARK_PAD = 0.12f
private const val MARK_INSET = 0.08f

/** Marks behind a run's words: one rounded box for each run of words of one color, the spaces between them too. */
private fun DrawScope.drawMarks(
    layout: TextLayoutResult,
    marks: List<Pair<IntRange, Color>>,
    pitch: Float,
) {
    val pad = pitch * MARK_PAD
    for ((range, color) in marks) {
        if (range.last >= layout.layoutInput.text.length) continue
        val line = layout.getLineForOffset(range.first)
        // Right to left: the first character is the right end
        val first = layout.getBoundingBox(range.first)
        val last = layout.getBoundingBox(range.last)
        val left = minOf(first.left, last.left) - pad
        val right = maxOf(first.right, last.right) + pad
        val top = layout.getLineTop(line) + pitch * MARK_INSET
        val bottom = layout.getLineBottom(line) - pitch * MARK_INSET
        drawRoundRect(color, Offset(left, top), Size(right - left, bottom - top), CornerRadius(pad * 2, pad * 2))
    }
}
