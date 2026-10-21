package io.github.kdroidfilter.seforimapp.legacy.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.SpanWatcher
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.widget.TextView
import kotlin.math.floor

/**
 * Justified text below Android 8.0, as the main app justifies the book's lines and the panes' texts (Justify.kt):
 * Android justifies a line itself (Layout.JUSTIFICATION_MODE_INTER_WORD) only from API 26, and before API 21 it has no
 * letter spacing either. Here a view lays its text out start-aligned, as it always has; then each space inside a line
 * (a paragraph's last line aside, as Justify does) gets a [Gap] of its share of the line's leftover width, whole pixels
 * shared evenly, so the line fills the width to the pixel below. The gaps go on the view's own text after its layout:
 * a layout keeps its line breaks and measures a line's runs as it draws them, so the lines are drawn justified without
 * being laid out again (a second layout per line cost five times the line's own rendering on the Android 4.4 AVD).
 * Measuring the lines still costs about as much as the line's rendering, and a justified line is drawn word by word:
 * the reader keeps a line's gaps, and leaves the lines a fling brings in until it stops.
 *
 * A text with more spaces than [MAX_SPACES] is left as it is (the main app's MAX_STRETCHED_SPACES: one span per space
 * costs more than the square of their number).
 */
object Justify {
    /** This Android justifies text itself, and the views ask it to (JUSTIFICATION_MODE_INTER_WORD). */
    val byPlatform: Boolean = Build.VERSION.SDK_INT >= 26

    /** Spaces past which a text is left start-aligned (the main app's MAX_STRETCHED_SPACES). */
    private const val MAX_SPACES = 600

    /** Kept between a stretched line and the width: the line is measured here a little apart from how it is drawn. */
    private const val FIT_SLACK_PX = 0.5f

    /** Where a text's gaps go and how wide each is ([at] and [px] side by side). */
    class Gaps(val at: IntArray, val px: IntArray)

    /** A layout's lines as justifying reads them: its width, and where each line starts, ends and shows its last character. */
    class Lines(val width: Int, val starts: IntArray, val ends: IntArray, val visibleEnds: IntArray)

    /**
     * Prepares a view for justified text: from Android 8.0 the platform justifies; from Android 6.0 the view breaks its
     * lines one at a time, as Android 4.4 to 5.1 do, rather than by the paragraph (a paragraph's balanced lines leave
     * short lines to stretch).
     */
    fun prepare(view: TextView) {
        if (byPlatform) {
            view.justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
        } else if (Build.VERSION.SDK_INT >= 23) {
            view.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            view.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
    }

    /** [layout]'s lines, or null when there is nothing to justify (a single line). */
    fun lines(layout: Layout): Lines? {
        val count = layout.lineCount
        if (count < 2) return null
        return Lines(
            layout.width,
            IntArray(count) { layout.getLineStart(it) },
            IntArray(count) { layout.getLineEnd(it) },
            IntArray(count) { layout.getLineVisibleEnd(it) },
        )
    }

    /**
     * The gaps that justify [lines], [text]'s start-aligned layout by [paint], or null when nothing is widened (a text too long, no line to stretch). Lines that start before [from] (the headings
     * above a book's line) are left as they are, and so is one holding a replaced character (an image).
     */
    fun gaps(text: Spanned, paint: TextPaint, lines: Lines, from: Int = 0): Gaps? {
        if (byPlatform || lines.width <= 0) return null
        val chars = text.toString()
        var spaces = 0
        for (i in from until chars.length) if (chars[i] == SPACE && ++spaces > MAX_SPACES) return null
        if (spaces == 0) return null
        // The spans that change a glyph's width (bold, a size), read once: most lines have none
        val sized = text.getSpans(0, text.length, MetricAffectingSpan::class.java).filter { it !is Gap }
        val work = TextPaint()
        val plainSpace = paint.measureText(" ")
        val at = IntArray(spaces)
        val px = IntArray(spaces)
        var count = 0
        for (line in 0 until lines.starts.size - 1) {
            val start = lines.starts[line]
            val end = lines.ends[line]
            if (start < from || (end > start && chars[end - 1] == '\n')) continue
            var first = start
            while (first < end && chars[first] == SPACE) first++
            val visibleEnd = lines.visibleEnds[line]
            val styled = sized.filter { text.getSpanStart(it) < visibleEnd && text.getSpanEnd(it) > start }
            if (styled.any { it is ReplacementSpan }) continue
            var gaps = 0
            var natural = 0f
            for (i in first until visibleEnd) {
                if (!isGap(chars, i)) continue
                gaps++
                natural += if (styled.isEmpty()) plainSpace else widthOf(text, chars, styled, i, i + 1, paint, work)
            }
            if (gaps == 0) continue
            val lineWidth = if (styled.isEmpty()) paint.measureText(chars, start, visibleEnd) else widthOf(text, chars, styled, start, visibleEnd, paint, work)
            // What the gaps take together once widened, to the pixel below the width
            val total = floor(lines.width - (lineWidth - natural) - FIT_SLACK_PX).toInt()
            if (total <= natural) continue
            var left = total
            var rest = gaps
            for (i in first until visibleEnd) {
                if (!isGap(chars, i)) continue
                val w = left / rest
                left -= w
                rest--
                at[count] = i
                px[count] = w
                count++
            }
        }
        if (count == 0) return null
        return Gaps(at.copyOf(count), px.copyOf(count))
    }

    /**
     * Sets [gaps] on [text], the view's own (its layout keeps its lines and draws them justified), then the view is drawn
     * again. The view's watcher of its text goes first: told of each span, it measured the view again for each (a tenth
     * of a second for a line on the Android 4.4 AVD), where the gaps change no line. A gap inside a background span (a
     * find match) draws that background itself: Android draws a replaced character with its span alone.
     */
    fun apply(view: TextView, text: Spannable, gaps: Gaps) {
        for (watcher in text.getSpans(0, text.length, SpanWatcher::class.java)) text.removeSpan(watcher)
        val lit = text.getSpans(0, text.length, BackgroundColorSpan::class.java).isNotEmpty()
        for (k in gaps.at.indices) {
            val i = gaps.at[k]
            if (i >= text.length) break
            val background = if (lit) text.getSpans(i, i + 1, BackgroundColorSpan::class.java).lastOrNull()?.backgroundColor ?: 0 else 0
            text.setSpan(Gap(gaps.px[k], background), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        view.invalidate()
    }

    private const val SPACE = ' '

    /**
     * A space that may be widened: not one carrying a stray mark (nikud on a space), which the span would part from the
     * mark it is shaped with (the main app's isGap).
     */
    private fun isGap(text: String, i: Int): Boolean =
        text[i] == SPACE && (i + 1 >= text.length || Character.getType(text[i + 1]) != Character.NON_SPACING_MARK.toInt())

    /** The width of [start] until [end] as laid out: run by run, each in the size or font its [styled] spans give it. */
    private fun widthOf(text: Spanned, chars: String, styled: List<MetricAffectingSpan>, start: Int, end: Int, paint: TextPaint, work: TextPaint): Float {
        var width = 0f
        var runStart = start
        while (runStart < end) {
            var runEnd = end
            for (span in styled) {
                val s = text.getSpanStart(span)
                val e = text.getSpanEnd(span)
                if (s in (runStart + 1) until runEnd) runEnd = s
                if (e in (runStart + 1) until runEnd) runEnd = e
            }
            work.set(paint)
            for (span in styled) {
                if (text.getSpanStart(span) <= runStart && text.getSpanEnd(span) >= runEnd) span.updateMeasureState(work)
            }
            width += work.measureText(chars, runStart, runEnd)
            runStart = runEnd
        }
        return width
    }

    /** A widened space: [width] px, drawn as nothing but its [background] (a find match's) when it has one. */
    private class Gap(private val width: Int, private val background: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            // The line's height stays the text's (a span that leaves these unset would take another run's)
            if (fm != null) paint.getFontMetricsInt(fm)
            return width
        }

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            if (background == 0) return
            val color = paint.color
            val style = paint.style
            paint.color = background
            paint.style = Paint.Style.FILL
            canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), paint)
            paint.color = color
            paint.style = style
        }
    }
}
