package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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
 * An amud drawn from its layout: every line in its box, its words justified from the box's right edge to its left
 * one, as printed, a dibbur hamatchil in bold. The Gemara in [gemara] (a square letter), Rashi and Tosafot in [rashi]
 * (Rashi script); each family should have a bold face, or Compose makes one. The page takes the width it is given and
 * the height its block needs at that width.
 *
 * [highlight] marks words behind them; a click on a word gives it to [onWordClick] (with Ctrl or Cmd held, as the
 * text's lines take them), and the word under the pointer goes to [onHover] (null off the words).
 *
 * An amud is some 1,500 words measured one by one, a tenth of a second or more: it is laid out off the UI thread, and
 * until it is, the same amud laid out at another width (the window being resized) is drawn scaled to this one.
 */
@Composable
fun DafPage(
    amud: DafAmud,
    gemara: FontFamily,
    rashi: FontFamily,
    ink: Color,
    modifier: Modifier = Modifier,
    highlight: DafHighlight = DafHighlight.NONE,
    onWordClick: (word: DafWord, withModifier: Boolean) -> Unit = { _, _ -> },
    onHover: (DafWord?) -> Unit = {},
) {
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val currentOnWordClick by rememberUpdatedState(onWordClick)
    val currentOnHover by rememberUpdatedState(onHover)
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val scale = width / (amud.block[2] - amud.block[0])
        val height = (amud.block[3] - amud.block[1]) * scale
        var laidOut by remember { mutableStateOf<LaidOutPage?>(null) }
        var hovered by remember(amud) { mutableStateOf<DafWord?>(null) }
        LaunchedEffect(amud, width, gemara, rashi, ink, density, resolver) {
            val lines =
                withContext(Dispatchers.Default) {
                    val measurer = TextMeasurer(resolver, density, LayoutDirection.Rtl, cacheSize = MEASURE_CACHE)
                    layoutAmud(amud, scale, measurer, density, gemara, rashi, ink, beforeEachLine = { ensureActive() })
                }
            laidOut = LaidOutPage(amud, width, lines)
        }

        // The word at a point of the canvas, [canvasWidth] wide now (not the width of the composition the pointer's
        // handlers started in): the page may be laid out at another width, drawn scaled
        fun wordAt(
            position: Offset,
            canvasWidth: Float,
        ): DafWord? {
            val page = laidOut?.takeIf { it.amud === amud } ?: return null
            val k = canvasWidth / page.width
            return page.lines.wordAt(position.x / k, position.y / k)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(with(density) { height.toDp() })
                .pointerHoverIcon(if (hovered != null) PointerIcon.Hand else PointerIcon.Default)
                .pointerInput(amud) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val word =
                                when (event.type) {
                                    PointerEventType.Exit -> null
                                    PointerEventType.Move, PointerEventType.Enter ->
                                        wordAt(event.changes.first().position, size.width.toFloat())
                                    else -> continue
                                }
                            if (word != hovered) {
                                hovered = word
                                currentOnHover(word)
                            }
                        }
                    }
                }.pointerInput(amud) {
                    awaitEachGesture {
                        val down = awaitPointerEvent(PointerEventPass.Main)
                        val isTouch = down.changes.any { it.type == PointerType.Touch && it.pressed }
                        if (!down.buttons.isPrimaryPressed && !isTouch) return@awaitEachGesture
                        val withModifier = down.keyboardModifiers.isCtrlPressed || down.keyboardModifiers.isMetaPressed
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (up.isConsumed) return@awaitEachGesture
                        wordAt(up.position, size.width.toFloat())?.let { currentOnWordClick(it, withModifier) }
                    }
                },
        ) {
            val page = laidOut?.takeIf { it.amud === amud } ?: return@Canvas
            scale(width / page.width, pivot = Offset.Zero) {
                for (line in page.lines) drawHighlights(line, highlight)
                for (line in page.lines) {
                    for (i in line.words.indices) drawText(line.words[i], topLeft = Offset(line.xs[i], line.top))
                }
            }
        }
    }
}

/** Marks behind a line's words: one rounded box for each run of words of one color, the spaces between them too. */
private fun DrawScope.drawHighlights(
    line: PlacedLine,
    highlight: DafHighlight,
) {
    var i = 0
    val pad = (line.boxBottom - line.boxTop) * HIGHLIGHT_PAD
    while (i < line.words.size) {
        val color = highlight.colorOf(DafWord(line.index, i))
        if (color == null) {
            i++
            continue
        }
        var j = i
        while (j + 1 < line.words.size && highlight.colorOf(DafWord(line.index, j + 1)) == color) j++
        // The words go right to left: the run's first word is its right end
        val right = line.xs[i] + line.words[i].size.width + pad
        val left = line.xs[j] - pad
        drawRoundRect(
            color = color,
            topLeft = Offset(left, line.boxTop),
            size = Size(right - left, line.boxBottom - line.boxTop),
            cornerRadius = CornerRadius(pad * 2, pad * 2),
        )
        i = j + 1
    }
}

/** How far a mark reaches past its words, to the line's height. */
private const val HIGHLIGHT_PAD = 0.12f

/** How far off a line's box a point still counts as on it (pixels). */
private const val HIT_SLACK = 3f

/**
 * The word at [x], [y] among these laid-out lines: in the line whose box holds the point (the nearest to its middle,
 * as two lines' boxes may overlap a little), the word nearest to x; else none.
 */
internal fun List<PlacedLine>.wordAt(
    x: Float,
    y: Float,
): DafWord? {
    val line =
        filter {
            y >= it.boxTop - HIT_SLACK && y <= it.boxBottom + HIT_SLACK && x >= it.boxLeft - HIT_SLACK && x <= it.boxRight + HIT_SLACK
        }.minByOrNull { kotlin.math.abs((it.boxTop + it.boxBottom) / 2f - y) } ?: return null
    var best = -1
    var bestDistance = Float.MAX_VALUE
    for (i in line.words.indices) {
        val left = line.xs[i]
        val right = left + line.words[i].size.width
        val distance =
            when {
                x < left -> left - x
                x > right -> x - right
                else -> 0f
            }
        if (distance < bestDistance) {
            bestDistance = distance
            best = i
        }
    }
    return if (best >= 0) DafWord(line.index, best) else null
}

/** An amud's lines as laid out at [width] pixels. */
private class LaidOutPage(
    val amud: DafAmud,
    val width: Float,
    val lines: List<PlacedLine>,
)

/** Words measured are kept for the amud's other lines (a word is often on several, and the same size on a column's). */
private const val MEASURE_CACHE = 512

/** A line laid out: its index in the amud's lines, its words, the left edge of each, the top of its words, its box. */
internal class PlacedLine(
    val index: Int,
    val words: List<TextLayoutResult>,
    val xs: FloatArray,
    val top: Float,
    val boxLeft: Float,
    val boxRight: Float,
    val boxTop: Float,
    val boxBottom: Float,
)

/** The size of a line's letters to its box's height: the box spans the letters' ascenders and descenders. */
private const val GEMARA_SIZE = 0.92f
private const val RASHI_SIZE = 0.95f

/** The narrowest space between two words, to their letters' size. */
private const val SPACE = 0.25f

/** Below this a line's letters are not made smaller (pixels), and no more tries than this to make them fit. */
private const val MIN_SIZE = 4f
private const val MAX_TRIES = 4

/** Each try after the first makes the letters at least this much smaller. */
private const val FIT_STEP = 0.99f

/**
 * A line's box is no taller than this to its column's usual line on the page (the median of its sure lines): a box
 * the alignment made of two lines would draw its words twice their size.
 */
private const val TALLEST_BOX = 1.15f

/** A line the alignment is not sure of is drawn lighter: its words may not all be the print's. */
private const val UNSURE_INK = 0.55f

/**
 * Lays out every line of [amud] at [scale] pixels a point: the words' size from the box's height, made smaller while
 * they do not fit its width with a normal space between them, then spread so the line fills its box, as printed.
 * A box much taller than its column's lines counts as theirs, and a line the alignment is not sure of is lighter.
 * [beforeEachLine] is called before each line, to stop the layout by throwing (a coroutine's ensureActive).
 */
internal fun layoutAmud(
    amud: DafAmud,
    scale: Float,
    measurer: TextMeasurer,
    density: Density,
    gemara: FontFamily,
    rashi: FontFamily,
    ink: Color,
    beforeEachLine: () -> Unit = {},
): List<PlacedLine> {
    val bx = amud.block[0]
    val by = amud.block[1]
    // The usual height of a line of the Gemara, and of Rashi and Tosafot (one size), on this page
    val usual =
        amud.lines
            .filter { it.s == 1 }
            .groupBy { it.isGemara }
            .mapValues { (_, lines) -> lines.map { it.b[3] - it.b[1] }.sorted().let { it[it.size / 2] } }
    return amud.lines.mapIndexedNotNull { index, line ->
        beforeEachLine()
        val words = line.t.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return@mapIndexedNotNull null
        val x0 = (line.b[0] - bx) * scale
        val x1 = (line.b[2] - bx) * scale
        val y0 = (line.b[1] - by) * scale
        val y1 = (line.b[3] - by) * scale
        val boxWidth = x1 - x0
        val family = if (line.isGemara) gemara else rashi
        val color = if (line.s == 1) ink else ink.copy(alpha = ink.alpha * UNSURE_INK)
        val bold = line.d.toSet()

        fun measureAt(size: Float): List<TextLayoutResult> {
            val style = TextStyle(fontFamily = family, fontSize = with(density) { size.toSp() }, color = color)
            val boldStyle = style.copy(fontWeight = FontWeight.Bold)
            return words.mapIndexed { i, word ->
                measurer.measure(
                    AnnotatedString(word),
                    if (i in bold) boldStyle else style,
                    softWrap = false,
                    maxLines = 1,
                    layoutDirection = LayoutDirection.Rtl,
                    density = density,
                )
            }
        }

        fun naturalWidth(
            layouts: List<TextLayoutResult>,
            size: Float,
        ): Float = layouts.sumOf { it.size.width } + size * SPACE * (words.size - 1)

        val boxHeight = usual[line.isGemara]?.let { minOf(y1 - y0, it * scale * TALLEST_BOX) } ?: (y1 - y0)
        var size = boxHeight * if (line.isGemara) GEMARA_SIZE else RASHI_SIZE
        var layouts = measureAt(size)
        var natural = naturalWidth(layouts, size)
        // The words' width goes with their size: straight to the size that fits, then a little less while the
        // rounding of the letters' widths still keeps the line too wide
        var tries = 0
        while (natural > boxWidth && size > MIN_SIZE && tries++ < MAX_TRIES) {
            size = maxOf(MIN_SIZE, size * minOf(boxWidth / natural, FIT_STEP))
            layouts = measureAt(size)
            natural = naturalWidth(layouts, size)
        }
        val total = layouts.sumOf { it.size.width }.toFloat()
        val gap = if (words.size > 1) (boxWidth - total) / (words.size - 1) else 0f
        val xs = FloatArray(words.size)
        var x = x1
        for (i in words.indices) {
            xs[i] = x - layouts[i].size.width
            x = xs[i] - gap
        }
        val top = (y0 + y1) / 2f - layouts.maxOf { it.size.height } / 2f
        PlacedLine(index, layouts, xs, top, x0, x1, y0, y1)
    }
}
