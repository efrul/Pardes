package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
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
 * that it fills its box, as printed, or [gaps] after each its own (a line on both sides of a big word: one of them
 * the big word's width). [unsure] when the alignment is not sure of its first or last word. Its words are its line's
 * from [firstWord] on (a line whose words are printed in two).
 */
internal class RunLine(
    val index: Int,
    val words: List<String>,
    val bold: Set<Int>,
    val size: Float,
    val indent: Float,
    val extra: Float,
    val unsure: Boolean,
    val gaps: FloatArray? = null,
    val firstWord: Int = 0,
)

/**
 * Lines of [column] one below the other, [pitch] apart, from [top] down: drawn as one text, each line a paragraph of its
 * own (null, a line's place left empty). Its box is [left] and
 * [width] wide; [size] is its letters', [isGemara] whether they are the Gemara's (a square letter) or a commentary's,
 * [isBig] for a column's big first word. All in pixels at the scale the amud was laid out at.
 */
internal class TextRun(
    val column: String,
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
        return DafWord(line.index, line.firstWord + word)
    }

    /**
     * The text drawn [k] times its laid-out size, the [density] the run was laid out with; its lines the alignment is
     * not sure of in [unsureInk], when given.
     */
    fun annotated(
        k: Float,
        density: Density,
        unsureInk: Color?,
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
                    if (line.unsure && unsureInk != null) addStyle(SpanStyle(color = unsureInk), start, end)
                    val starts = wordStarts[slot]
                    for (i in line.words.indices) {
                        val wordEnd = starts[i] + line.words[i].length
                        if (i in line.bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), starts[i], wordEnd)
                        if (i == line.words.lastIndex) continue
                        val extra = line.gaps?.get(i) ?: line.extra
                        if (extra != 0f) addStyle(SpanStyle(letterSpacing = (extra * k).toSp()), wordEnd, wordEnd + 1)
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

/** A line the alignment is not sure of is drawn lighter, when asked: its words may not all be the print's. */
internal const val UNSURE_INK = 0.55f

/** A word's place in the order an amud is read: column by column, line by line, word by word. */
private val READING = compareBy<DafWord>({ it.line }, { it.word })

/**
 * A selection of an amud's words, from [anchor] (where it began) to [end], in the order they are read: down a column's
 * lines from one part of it to the next (above the Gemara, beside it, below it), not into another column's.
 */
data class DafRange(
    val anchor: DafWord,
    val end: DafWord,
) {
    val first: DafWord get() = minOf(anchor, end, READING)
    val last: DafWord get() = maxOf(anchor, end, READING)

    operator fun contains(word: DafWord): Boolean = READING.compare(first, word) <= 0 && READING.compare(word, last) <= 0
}

/** The words of [range] as printed, one space between two, the lines' too. */
internal fun DafAmud.textOf(range: DafRange): String {
    val (first, last) = range.first to range.last
    return (first.line..last.line)
        .joinToString(" ") { i ->
            val words = lines[i].t.split(' ').filter { it.isNotEmpty() }
            val from = if (i == first.line) first.word else 0
            val to = if (i == last.line) last.word else words.lastIndex
            words.subList(from.coerceIn(0, words.size), (to + 1).coerceIn(0, words.size)).joinToString(" ")
        }.trim()
}

/** The words of this amud's line of the library [word] is of, from its first to its last as printed here. */
internal fun DafAmud.lineRangeOf(word: DafWord): DafRange? {
    val id = lines.getOrNull(word.line)?.lineIdAt(word.word) ?: return DafRange(word, word)
    val column = lines[word.line].c
    var first: DafWord? = null
    var last: DafWord? = null
    lines.forEachIndexed { i, line ->
        if (line.c != column) return@forEachIndexed
        for (span in line.u) {
            if (span.size != SPAN || span[0] != id) continue
            val a = DafWord(i, span[1].toInt())
            val b = DafWord(i, span[2].toInt())
            if (first == null || READING.compare(a, first!!) < 0) first = a
            if (last == null || READING.compare(b, last!!) > 0) last = b
        }
    }
    return first?.let { DafRange(it, last!!) }
}

private const val SPAN = 3

/**
 * An amud drawn from its [layout] at [k] times the size it was laid out at, in the text's own components: each run of
 * lines is a text, its words in [gemara] (a square letter) or [rashi] (Rashi script). [highlight] marks words behind
 * them. The amud holds its own selection, [range], in the order a column is read (a container of Compose's would order
 * its texts by their tops, and take a drag down one column into another's lines between): a drag selects from the
 * word it starts on, a double click a word, a triple click the word's comment or segment, a Shift+click to a word;
 * [onRange] is given each change. A click on a word, not a drag, also goes to [onWordClick] (with Ctrl or Cmd held, as
 * the text's lines take them); a right-click selects the word under it unless it is in the selection, gives it to
 * [onContextWord] and opens the text's menu where it was ([onMenu]). [onPress] comes first on any press. With
 * [markUnsure], the lines the alignment is not sure of are drawn lighter.
 */
@Composable
internal fun DafAmudText(
    layout: AmudLayout,
    k: Float,
    gemara: FontFamily,
    rashi: FontFamily,
    ink: Color,
    modifier: Modifier = Modifier,
    highlight: DafHighlight = DafHighlight.NONE,
    range: DafRange? = null,
    selectionColor: Color = Color.Transparent,
    onRange: (DafRange?) -> Unit = {},
    onWordClick: (word: DafWord, withModifier: Boolean) -> Unit = { _, _ -> },
    onContextWord: (DafWord) -> Unit = {},
    onMenu: (Offset) -> Unit = {},
    onPress: () -> Unit = {},
    markUnsure: Boolean = false,
    underlined: Set<DafWord> = emptySet(),
    underlineColor: Color = Color.Unspecified,
) {
    val density = LocalDensity.current
    val currentRange by rememberUpdatedState(range)
    val currentOnRange by rememberUpdatedState(onRange)
    val currentOnWordClick by rememberUpdatedState(onWordClick)
    val currentOnContextWord by rememberUpdatedState(onContextWord)
    val currentOnMenu by rememberUpdatedState(onMenu)
    val currentOnPress by rememberUpdatedState(onPress)
    val unsureInk = if (markUnsure) ink.copy(alpha = ink.alpha * UNSURE_INK) else null
    // Each run's text as laid out, and where it is (the page's pixels): what the pointer is on
    val results = remember(layout, k) { arrayOfNulls<TextLayoutResult>(layout.runs.size) }

    fun spare(run: TextRun) = run.size * RUN_SPARE * k

    fun originOf(i: Int): Offset {
        val run = layout.runs[i]
        return Offset((run.left * k - spare(run)).roundToInt().toFloat(), (run.top * k).roundToInt().toFloat())
    }

    // The word at [position], on its line (none between two columns or past a line's end)
    fun wordAt(position: Offset): DafWord? {
        for (i in layout.runs.indices.reversed()) {
            val result = results[i] ?: continue
            val local = position - originOf(i)
            if (local.x < 0f || local.y < 0f || local.x > result.size.width || local.y > result.size.height) continue
            val line = result.getLineForVerticalPosition(local.y)
            if (local.x < result.getLineLeft(line) - HIT_SLACK || local.x > result.getLineRight(line) + HIT_SLACK) continue
            return layout.runs[i].wordAt(result.getOffsetForPosition(local))
        }
        return null
    }

    // The word of [column] nearest to [position] (of any column when null): a drag goes on down its column whatever it
    // passes over, and one begun beside the words (under a big first word, between two lines) begins at the nearest
    fun nearestIn(
        column: String?,
        position: Offset,
    ): DafWord? {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in layout.runs.indices) {
            val run = layout.runs[i]
            val result = results[i] ?: continue
            if ((column != null && run.column != column) || run.isBig) continue
            val local = position - originOf(i)
            val dy = maxOf(0f, -local.y, local.y - result.size.height)
            val dx = maxOf(0f, -local.x, local.x - result.size.width)
            val distance = dy * VERTICAL_WEIGHT + dx
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        if (best < 0) return null
        val result = results[best]!!
        val local = position - originOf(best)
        val clamped = Offset(local.x.coerceIn(0f, result.size.width.toFloat()), local.y.coerceIn(0f, result.size.height - 1f))
        return layout.runs[best].wordAt(result.getOffsetForPosition(clamped))
    }
    val pointer =
        Modifier.pointerInput(layout, k) {
            var lastClickAt = 0L
            var lastClickAt2 = Offset.Zero
            var clicks = 0
            awaitEachGesture {
                val down = awaitPointerEvent(PointerEventPass.Main)
                if (down.type != PointerEventType.Press) return@awaitEachGesture
                val change = down.changes.first()
                val at = change.position
                currentOnPress()
                if (down.buttons.isSecondaryPressed) {
                    // The text's menu: on the selection when the pointer is in it, else on the word under it
                    val word = wordAt(at)
                    if (word != null && currentRange?.contains(word) != true) currentOnRange(DafRange(word, word))
                    word?.let { currentOnContextWord(it) }
                    currentOnMenu(at)
                    change.consume()
                    return@awaitEachGesture
                }
                val isTouch = change.type == PointerType.Touch
                if (!down.buttons.isPrimaryPressed && !isTouch) return@awaitEachGesture
                val word = wordAt(at)
                val near = (at - lastClickAt2).getDistance() < viewConfiguration.touchSlop * 2
                clicks = if (change.uptimeMillis - lastClickAt < viewConfiguration.doubleTapTimeoutMillis && near) clicks + 1 else 1
                lastClickAt = change.uptimeMillis
                lastClickAt2 = at
                val withModifier = down.keyboardModifiers.isCtrlPressed || down.keyboardModifiers.isMetaPressed
                val extend = down.keyboardModifiers.isShiftPressed && currentRange != null && word != null
                if (extend) currentOnRange(DafRange(currentRange!!.anchor, word!!))
                val anchor = if (extend) currentRange!!.anchor else word ?: nearestIn(null, at)
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val moved = event.changes.firstOrNull { it.id == change.id } ?: break
                    if (!moved.pressed) break
                    if (!dragging && (moved.position - at).getDistance() > viewConfiguration.touchSlop) dragging = true
                    if (dragging && anchor != null) {
                        nearestIn(layout.amud.lines[anchor.line].c, moved.position)?.let { currentOnRange(DafRange(anchor, it)) }
                        moved.consume()
                    }
                }
                if (dragging || extend || word == null) return@awaitEachGesture
                when (clicks) {
                    1 -> {
                        currentOnRange(null)
                        currentOnWordClick(word, withModifier)
                    }
                    2 -> currentOnRange(DafRange(word, word))
                    else -> currentOnRange(layout.amud.lineRangeOf(word))
                }
            }
        }
    // Each run where it is printed, as the page's coordinates go (left to right): a layout of its own, as a right to left
    // one mirrors a Box's offsets
    Layout(
        modifier = with(density) { modifier.size((layout.width * k).toDp(), (layout.height * k).toDp()) }.then(pointer),
        content = {
            layout.runs.forEachIndexed { i, run ->
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
                val selected =
                    remember(run, range, selectionColor) {
                        range?.let { r -> marksOf(run) { word -> selectionColor.takeIf { word in r } } }.orEmpty()
                    }
                val underlines =
                    remember(run, underlined, underlineColor) {
                        if (underlined.isEmpty()) emptyList() else marksOf(run) { word -> underlineColor.takeIf { word in underlined } }
                    }
                var textLayout by remember(run) { mutableStateOf<TextLayoutResult?>(null) }
                BasicText(
                    text = text,
                    style = style,
                    softWrap = false,
                    onTextLayout = {
                        textLayout = it
                        results[i] = it
                    },
                    modifier =
                        Modifier
                            .pointerHoverIcon(PointerIcon.Text)
                            .drawBehind {
                                textLayout?.let {
                                    drawMarks(it, marks, run.pitch * k)
                                    drawMarks(it, selected, run.pitch * k, rounded = false)
                                    drawUnderlines(it, underlines, run.pitch * k)
                                }
                            },
                )
            }
        },
    ) { measurables, _ ->
        // Room to spare on the left for the space after each line but the last (a copy keeps it between two lines): a
        // text a line of which is wider than it is laid out as wide as that line, past its right edge
        val placeables =
            measurables.mapIndexed { i, measurable ->
                val run = layout.runs[i]
                val width = (run.width * k + spare(run)).roundToInt()
                measurable.measure(Constraints(minWidth = width, maxWidth = width))
            }
        layout((layout.width * k).roundToInt(), (layout.height * k).roundToInt()) {
            placeables.forEachIndexed { i, placeable ->
                val origin = originOf(i)
                placeable.place(origin.x.roundToInt(), origin.y.roundToInt())
            }
        }
    }
}

/** A word above or below a run is this much farther from it than one beside it, for a drag's nearest word. */
private const val VERTICAL_WEIGHT = 4f

/** How far off a line a point still counts as on it (pixels), and the room to spare left of a run (to its letters). */
private const val HIT_SLACK = 3f
private const val RUN_SPARE = 0.6f

/** A run's marked words: for each run of words of one color in a line, its first and last characters and the color. */
private fun marksOf(
    run: TextRun,
    highlight: DafHighlight,
): List<Pair<IntRange, Color>> {
    if (highlight === DafHighlight.NONE) return emptyList()
    // (a lambda passes for a DafHighlight: the selection's marks are drawn by the same code)
    val out = ArrayList<Pair<IntRange, Color>>()
    run.lines.forEachIndexed { slot, line ->
        if (line == null) return@forEachIndexed
        var i = 0
        while (i < line.words.size) {
            val color = highlight.colorOf(DafWord(line.index, line.firstWord + i))
            if (color == null) {
                i++
                continue
            }
            var j = i
            while (j + 1 < line.words.size && highlight.colorOf(DafWord(line.index, line.firstWord + j + 1)) == color) j++
            out += (run.rangeOf(slot, i).first..run.rangeOf(slot, j).last) to color
            i = j + 1
        }
    }
    return out
}

/** A dotted line under each run of [marks]' words (the words a note is on), as the text draws a note's. */
private fun DrawScope.drawUnderlines(
    layout: TextLayoutResult,
    marks: List<Pair<IntRange, Color>>,
    pitch: Float,
) {
    if (marks.isEmpty()) return
    val stroke = density
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f * stroke, 3f * stroke), 0f)
    for ((range, color) in marks) {
        if (range.last >= layout.layoutInput.text.length) continue
        val line = layout.getLineForOffset(range.first)
        val first = layout.getBoundingBox(range.first)
        val last = layout.getBoundingBox(range.last)
        val y = layout.getLineBottom(line) - pitch * MARK_INSET
        drawLine(
            color = color,
            start = Offset(minOf(first.left, last.left), y),
            end = Offset(maxOf(first.right, last.right), y),
            strokeWidth = stroke,
            pathEffect = dash,
        )
    }
}

/** How far a mark reaches past its words, to the line's height, and how much of the line's height it leaves. */
private const val MARK_PAD = 0.12f
private const val MARK_INSET = 0.08f

/**
 * Marks behind a run's words: one box for each run of words of one color, the spaces between them too, rounded (the
 * page's marks) or square (a selection, as the text's).
 */
private fun DrawScope.drawMarks(
    layout: TextLayoutResult,
    marks: List<Pair<IntRange, Color>>,
    pitch: Float,
    rounded: Boolean = true,
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
        val corner = if (rounded) CornerRadius(pad * 2, pad * 2) else CornerRadius.Zero
        drawRoundRect(color, Offset(left, top), Size(right - left, bottom - top), corner)
    }
}
