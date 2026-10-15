package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import kotlin.math.floor

/*
 * Justified text that selects where it is drawn (desktop justifies the book's text, BookContentView LineItem).
 *
 * Android justifies a line (JUSTIFICATION_MODE_INTER_WORD, what TextAlign.Justify becomes) only when it draws it:
 * Layout.getHorizontal measures the line unstretched, on every version up to the current one, and the selection
 * highlight, its handles, the long-press hit test, the selection bar's place and the note underlines all go through
 * it. On a justified line they landed toward the line's start by the width its spaces were stretched, so a
 * long-pressed word was selected whole but highlighted half on its neighbour. Here the text is laid out start-aligned
 * first, then each space inside a line (a paragraph's last line aside, as Justify) gets its share of the line's
 * leftover width as letter spacing, which the layout itself measures: what is drawn and every position agree.
 */

/** What a justified Text shows: [text], with its spaces widened, laid out with [textAlign]; [onLayout] goes in its onTextLayout. */
class JustifiedText(val text: AnnotatedString, val textAlign: TextAlign, val onLayout: (TextLayoutResult) -> Unit)

/**
 * [text] in [style], justified by the app for a Text exactly [width] px wide (from the BoxWithConstraints around it),
 * when [style] is justified, [inline] is false (placeholders would have to be measured too) and the text is not too
 * long for one span per space ([MAX_STRETCHED_SPACES]). The spacing is measured on what moves glyphs only, so a
 * change of colours (find-in-page, highlights, the theme) does not measure again. A layout that no longer matches the
 * plan (a font that finished loading after it was made) has it made again once; after that, and for any other text,
 * the platform's alignment is kept.
 */
@Composable
fun rememberJustifiedText(text: AnnotatedString, style: TextStyle, width: Int, inline: Boolean = false): JustifiedText {
    val spaces = remember(text.text) { text.text.count { it == STRETCHED_SPACE } }
    val justify = style.textAlign == TextAlign.Justify && !inline && width > 0 && width != Constraints.Infinity && spaces <= MAX_STRETCHED_SPACES
    val shape = remember(text, justify) { if (justify) text.glyphShape() else text }
    val layoutStyle = style.copy(color = Color.Unspecified, textAlign = TextAlign.Start)
    val attempt = remember(shape, layoutStyle, width) { mutableIntStateOf(0) }
    val made = attempt.intValue
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val plan = remember(shape, layoutStyle, width, justify, made, measurer, density) {
        if (!justify || made > MAX_RETRIES) {
            null
        } else {
            plan(shape, measurer.measure(shape, layoutStyle, constraints = Constraints.fixedWidth(width)), width, style.letterSpacing, style.fontSize, density)
        }
    }
    // Remembered, so the Text is not handed a new callback, and measured again, at every recomposition
    return remember(plan, text, style.textAlign, width) {
        JustifiedText(
            text = plan?.applyTo(text) ?: text,
            textAlign = if (plan != null) TextAlign.Start else style.textAlign,
            onLayout = { result -> if (plan != null && !plan.fits(result, width)) attempt.intValue = made + 1 },
        )
    }
}

/** How many times a plan is made again before the platform's Justify is kept. */
private const val MAX_RETRIES = 1

/**
 * Spaces past which a text keeps the platform's Justify. Android's SpannableString finds spans by linear scans, once
 * per span change in layout, intrinsics and drawing, so one span per space costs more than the square of their number:
 * on the API-33 emulator a 2,000-space paragraph stalled for 12 s against 3 s without them, while up to about 700
 * spaces it cost no more than before. Under 2% of the library's lines (long commentaries) are over this.
 */
private const val MAX_STRETCHED_SPACES = 600

/** Pixels a stretched line may fall short of the width before its plan counts as wrong (the floor loses under one). */
private const val SHORT_TOLERANCE_PX = 2f

/**
 * [this] without what does not move a glyph (colours, backgrounds, decorations, shadows) nor annotations (links,
 * which only colour and underline here), so the spacing is measured once for every colouring of the text.
 */
private fun AnnotatedString.glyphShape(): AnnotatedString {
    val spans = spanStyles.mapNotNull { range ->
        val glyphs = range.item.copy(color = Color.Unspecified, background = Color.Unspecified, textDecoration = null, shadow = null, drawStyle = null)
        if (glyphs == SpanStyle()) null else AnnotatedString.Range(glyphs, range.start, range.end)
    }
    return AnnotatedString(text, spans, paragraphStyles)
}

/** Where a text's lines end, which of them are stretched to the width, and the letter spacing of each widened space. */
private class Plan(private val lineEnds: IntArray, private val stretched: BooleanArray, private val spaces: IntArray, private val spacing: Array<TextUnit>) {
    /** [text] (the same characters the plan was made for, coloured in any way) with its spaces widened. */
    fun applyTo(text: AnnotatedString): AnnotatedString {
        if (spaces.isEmpty()) return text
        val b = AnnotatedString.Builder(text)
        for (k in spaces.indices) b.addStyle(SpanStyle(letterSpacing = spacing[k]), spaces[k], spaces[k] + 1)
        return b.toAnnotatedString()
    }

    /** Whether [result] broke the lines where the plan did and filled the stretched ones. */
    fun fits(result: TextLayoutResult, width: Int): Boolean {
        if (result.lineCount != lineEnds.size) return false
        for (line in lineEnds.indices) {
            if (result.getLineEnd(line) != lineEnds[line]) return false
            if (stretched[line] && result.getLineRight(line) - result.getLineLeft(line) < width - SHORT_TOLERANCE_PX) return false
        }
        return true
    }
}

/**
 * The plan that widens the spaces of each line of [text]'s start-aligned [layout], so the line fills [width] (to the
 * pixel below: whole pixels, shared evenly). A paragraph's last line (the text's last, or one ending in a line break)
 * is left as it is, as Justify does, and so are the spaces a line starts with (Android 15 trims half the letter
 * spacing at a line's edge) and the one it breaks at. A space's span replaces the style's own [letterSpacing], so
 * that is added back.
 */
private fun plan(text: AnnotatedString, layout: TextLayoutResult, width: Int, letterSpacing: TextUnit, fontSize: TextUnit, density: Density): Plan {
    val s = text.text
    val base = with(density) {
        when {
            letterSpacing.isSp -> letterSpacing.toPx()
            letterSpacing.isEm && fontSize.isSp -> letterSpacing.value * fontSize.toPx()
            else -> 0f
        }
    }
    val lines = layout.lineCount
    val lineEnds = IntArray(lines) { layout.getLineEnd(it) }
    val stretched = BooleanArray(lines)
    val spaces = ArrayList<Int>()
    val spacing = ArrayList<TextUnit>()
    for (line in 0 until lines - 1) {
        val end = lineEnds[line]
        val start = layout.getLineStart(line)
        if (end > start && s[end - 1] == '\n') continue
        var first = start
        while (first < end && s[first] == STRETCHED_SPACE) first++
        val visibleEnd = layout.getLineEnd(line, visibleEnd = true)
        val gaps = (first until visibleEnd).count { s.isGap(it) }
        val extra = floor(width - (layout.getLineRight(line) - layout.getLineLeft(line)) - FIT_SLACK_PX).toInt()
        if (gaps == 0 || extra <= 0) continue
        stretched[line] = true
        var left = extra
        var rest = gaps
        for (i in first until visibleEnd) {
            if (!s.isGap(i)) continue
            val px = left / rest
            left -= px
            rest--
            if (px > 0) {
                spaces += i
                spacing += with(density) { (base + px).toSp() }
            }
        }
    }
    return Plan(lineEnds, stretched, spaces.toIntArray(), spacing.toTypedArray())
}

/** The one space Android's inter-word justification stretches (TextLine, U+0020 only). */
private const val STRETCHED_SPACE = ' '

/** Kept between a line's natural width plus its spacing and the width, so summing the advances never wraps a word. */
private const val FIT_SLACK_PX = 0.05f

/**
 * A space between words that may be widened: not one carrying a stray mark (nikud on a space), which a span would
 * split from the mark it is shaped with.
 */
private fun String.isGap(i: Int): Boolean =
    this[i] == STRETCHED_SPACE && (i + 1 >= length || Character.getType(this[i + 1]) != Character.NON_SPACING_MARK.toInt())
