package io.github.kdroidfilter.seforimapp.legacy.ui

import android.os.Build
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import kotlin.math.abs

/** How a line's HTML (h1, h2, big, strong, small, sup) is shown: Android's own HTML reader, which 4.4 has. */
object LineText {
    fun stripMarks(html: String, diacritics: DiacriticsMode): String = diacritics.apply(html)

    private val HEADING_SIZES = mapOf(1.4f to 1.25f, 1.3f to 1.125f, 1.2f to 1f, 1.1f to 1f)

    fun isHeading(html: String): Boolean = html.startsWith("<h1") || html.startsWith("<h2") || html.startsWith("<h3")

    /**
     * The line as styled text, without the blank lines that Html.fromHtml leaves after a heading, and with the main
     * app's heading sizes (HtmlCompose: h1 1.5, h2 1.25, h3 1.125 times the text, h4 to h6 the text's own) where Html
     * makes them 1.5, 1.4, 1.3, 1.2 and 1.1 (<big>'s 1.25 and <small>'s 0.8 stay).
     */
    fun render(html: String, diacritics: DiacriticsMode): SpannableStringBuilder {
        val text = SpannableStringBuilder(fromHtml(stripMarks(html, diacritics)))
        text.getSpans(0, text.length, RelativeSizeSpan::class.java).forEach { span ->
            val size = HEADING_SIZES.entries.firstOrNull { abs(it.key - span.sizeChange) < 0.01f }?.value ?: return@forEach
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            text.removeSpan(span)
            if (size != 1f) text.setSpan(RelativeSizeSpan(size), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        if (end < text.length) text.delete(end, text.length)
        var start = 0
        while (start < text.length && text[start].isWhitespace()) start++
        if (start > 0) text.delete(0, start)
        return text
    }

    @Suppress("DEPRECATION")
    fun fromHtml(html: String): Spanned =
        if (Build.VERSION.SDK_INT >= 24) Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(html)

    /** Plain text of a line's HTML (for copying and snippets). */
    fun plain(html: String): String = fromHtml(html).toString().trim()

    /**
     * A pattern that finds [query] in a line's text as the search compares them (pardes_match): any points,
     * cantillation marks, geresh, gershayim or quotes between the letters are skipped, and as a [phrase] its words are
     * found in order with whatever spaces, punctuation or maqaf stand between them; otherwise each word on its own.
     */
    fun highlighter(query: String, phrase: Boolean = false): Regex? {
        val separators = "\\s\u05BE\u05C0\u05C3.,:;!?()\\[\\]{}\\-\u2013\u2014"
        val words = query.split(Regex("[$separators]+"))
            .map { w -> w.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val marks = "\u0591-\u05C7\u05F3\u05F4\"'`\u200E\u200F"
        fun word(w: String) = w.map { Regex.escape(it.toString()) }.joinToString("[$marks]*")
        if (phrase) return Regex(words.joinToString("[$separators$marks]+") { word(it) }, RegexOption.IGNORE_CASE)
        val alternatives = words.distinct().sortedByDescending { it.length }.map { word(it) }
        return Regex(alternatives.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    fun highlight(text: Spannable, pattern: Regex?, color: Int) {
        pattern ?: return
        pattern.findAll(text).forEach { match ->
            text.setSpan(BackgroundColorSpan(color), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
