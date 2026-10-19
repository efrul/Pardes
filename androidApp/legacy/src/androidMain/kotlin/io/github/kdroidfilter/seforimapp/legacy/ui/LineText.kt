package io.github.kdroidfilter.seforimapp.legacy.ui

import android.os.Build
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import io.github.kdroidfilter.seforimapp.legacy.data.Diacritics

/** How a line's HTML (h1, h2, big, strong, small, sup) is shown: Android's own HTML reader, which 4.4 has. */
object LineText {
    /** Cantillation marks and meteg: dropped to show the niqqud alone. */
    private val CANTILLATION = Regex("[\u0591-\u05AF\u05BD]")

    /** Every point and mark but maqaf, paseq, sof pasuq and nun hafukha: no niqqud. */
    private val ALL_MARKS = Regex("[\u0591-\u05BD\u05BF\u05C1\u05C2\u05C4\u05C5\u05C7]")

    fun stripMarks(html: String, diacritics: Diacritics): String = when (diacritics) {
        Diacritics.All -> html
        Diacritics.Niqqud -> html.replace(CANTILLATION, "")
        Diacritics.None -> html.replace(ALL_MARKS, "")
    }

    fun isHeading(html: String): Boolean = html.startsWith("<h1") || html.startsWith("<h2") || html.startsWith("<h3")

    /** The line as styled text, without the blank lines that Html.fromHtml leaves after a heading. */
    fun render(html: String, diacritics: Diacritics): SpannableStringBuilder {
        val text = SpannableStringBuilder(fromHtml(stripMarks(html, diacritics)))
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
