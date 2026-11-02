package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * An amud drawn from its layout: every line in its box, its words justified from the box's right edge to its left
 * one, as printed, a dibbur hamatchil in bold. The Gemara in [gemara] (a square letter), Rashi and Tosafot in [rashi]
 * (Rashi script); each family should have a bold face, or Compose makes one. The page takes the width it is given and
 * the height its block needs at that width.
 */
@Composable
fun DafPage(
    amud: DafAmud,
    gemara: FontFamily,
    rashi: FontFamily,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 1024)
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val scale = width / (amud.block[2] - amud.block[0])
        val height = (amud.block[3] - amud.block[1]) * scale
        val placed =
            remember(amud, width, gemara, rashi, ink, density) {
                layoutAmud(amud, scale, measurer, density, gemara, rashi, ink)
            }
        Canvas(Modifier.fillMaxWidth().height(with(density) { height.toDp() })) {
            for (line in placed) {
                for (i in line.words.indices) drawText(line.words[i], topLeft = Offset(line.xs[i], line.top))
            }
        }
    }
}

/** A line laid out: its words, the left edge of each, and the top of the line. */
internal class PlacedLine(
    val words: List<TextLayoutResult>,
    val xs: FloatArray,
    val top: Float,
    val boxLeft: Float,
    val boxRight: Float,
)

/** The size of a line's letters to its box's height: the box spans the letters' ascenders and descenders. */
private const val GEMARA_SIZE = 0.92f
private const val RASHI_SIZE = 0.95f

/**
 * Lays out every line of [amud] at [scale] pixels a point: the words' size from the box's height, made smaller while
 * they do not fit its width with a normal space between them, then spread so the line fills its box, as printed.
 */
internal fun layoutAmud(
    amud: DafAmud,
    scale: Float,
    measurer: TextMeasurer,
    density: Density,
    gemara: FontFamily,
    rashi: FontFamily,
    ink: Color,
): List<PlacedLine> {
    val bx = amud.block[0]
    val by = amud.block[1]
    return amud.lines.mapNotNull { line ->
        val words = line.t.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return@mapNotNull null
        val x0 = (line.b[0] - bx) * scale
        val x1 = (line.b[2] - bx) * scale
        val y0 = (line.b[1] - by) * scale
        val y1 = (line.b[3] - by) * scale
        val boxWidth = x1 - x0
        var size = (y1 - y0) * if (line.isGemara) GEMARA_SIZE else RASHI_SIZE
        val bold = line.d.toSet()
        var layouts: List<TextLayoutResult>
        var tries = 0
        while (true) {
            val style =
                TextStyle(
                    fontFamily = if (line.isGemara) gemara else rashi,
                    fontSize = with(density) { size.toSp() },
                    color = ink,
                )
            val boldStyle = style.copy(fontWeight = FontWeight.Bold)
            layouts =
                words.mapIndexed { i, word ->
                    measurer.measure(
                        AnnotatedString(word),
                        if (i in bold) boldStyle else style,
                        softWrap = false,
                        maxLines = 1,
                        layoutDirection = LayoutDirection.Rtl,
                        density = density,
                    )
                }
            val natural = layouts.sumOf { it.size.width } + size * 0.25f * (words.size - 1)
            if (natural <= boxWidth || size <= 4f || ++tries >= 12) break
            size *= 0.95f
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
        PlacedLine(layouts, xs, top, x0, x1)
    }
}
