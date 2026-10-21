package io.github.kdroidfilter.seforimapp.legacy.ui

import android.os.Build
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.RelativeSizeSpan
import android.text.style.URLSpan
import android.text.style.UpdateAppearance
import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import kotlin.math.abs

/** How a line's HTML (h1, h2, big, strong, small, sup) is shown: Android's own HTML reader, which 4.4 has. */
object LineText {
    /** What stands between the words of a query, as the search splits it. */
    private const val SEPARATORS = "\\s\u05BE\u05C0\u05C3.,:;!?()\\[\\]{}\\-\u2013\u2014"

    fun stripMarks(html: String, diacritics: DiacriticsMode): String = diacritics.apply(html)

    private val HEADING_SIZES = mapOf(1.4f to 1.25f, 1.3f to 1.125f, 1.2f to 1f, 1.1f to 1f)

    fun isHeading(html: String): Boolean = html.startsWith("<h1") || html.startsWith("<h2") || html.startsWith("<h3")

    /**
     * The line as styled text, without the blank lines that Html.fromHtml leaves after a heading, and with the main
     * app's heading sizes (HtmlCompose: h1 1.5, h2 1.25, h3 1.125 times the text, h4 to h6 the text's own) where Html
     * makes them 1.5, 1.4, 1.3, 1.2 and 1.1 (<big>'s 1.25 and <small>'s 0.8 stay), and without its footnotes' markers
     * ([hideFootnoteMarkers]). With a [linkColor], its links (the citations, <a class="ref" href="seforim://line/<id>">)
     * are [Link]s in that colour.
     */
    fun render(html: String, diacritics: DiacriticsMode, linkColor: Int = 0): SpannableStringBuilder {
        val text = SpannableStringBuilder(fromHtml(stripMarks(hideFootnoteMarkers(html), diacritics)))
        if (linkColor != 0) {
            text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                text.removeSpan(span)
                text.setSpan(Link(span.url, linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
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

    /**
     * A link of a line, told by its [color] alone (HtmlCompose's refLinkStyles: an underline would cross the vowels under
     * the letters); the reader opens it when tapped (a citation in a new reader, another link in the system's app).
     */
    class Link(val url: String, private val color: Int) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            paint.color = color
            paint.isUnderlineText = false
        }
    }

    /** The citation's line ("seforim://line/<id>"), or null for another link. */
    fun citedLine(url: String): Long? = url.takeIf { it.startsWith(CITATION) }?.removePrefix(CITATION)?.toLongOrNull()

    private const val CITATION = "seforim://line/"

    /** Plain text of a line's HTML as the reader shows it (for copying and snippets). */
    fun plain(html: String): String = fromHtml(hideFootnoteMarkers(html)).toString().trim()

    /**
     * [html] without its footnotes' markers (<sup class="footnote-marker">), each leaving a space between its neighbours:
     * the main app hides them so (buildAnnotatedFromHtml's showFootnoteMarkers), and the find counts its matches on that
     * text (plainTextFromHtml), so a marker shown here would be lit without being counted or reached by ▲ ▼. Html.fromHtml
     * then folds the space into one next to another, as the main app adds none there.
     */
    fun hideFootnoteMarkers(html: String): String =
        if (html.contains(FOOTNOTE_MARKER_CLASS, ignoreCase = true)) FOOTNOTE_MARKER.replace(html, " ") else html

    private const val FOOTNOTE_MARKER_CLASS = "footnote-marker"

    /** A <sup> whose classes (as jsoup's hasClass reads them) include footnote-marker, with its content. */
    private val FOOTNOTE_MARKER = Regex(
        "<sup\\s[^>]*?class\\s*=\\s*([\"'])(?:[^\"']*\\s)?$FOOTNOTE_MARKER_CLASS(?:\\s[^\"']*)?\\1[^>]*>.*?</sup\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * A pattern that finds [query] in a line's text as the search compares them (pardes_match): any points,
     * cantillation marks, geresh, gershayim or quotes between the letters are skipped, and as a [phrase] its words are
     * found in order with whatever spaces, punctuation or maqaf stand between them; otherwise each word on its own.
     */
    fun highlighter(query: String, phrase: Boolean = false): Regex? {
        val words = query.split(Regex("[$SEPARATORS]+"))
            .map { w -> w.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val marks = "\u0591-\u05C7\u05F3\u05F4\"'`\u200E\u200F"
        fun word(w: String) = w.map { Regex.escape(it.toString()) }.joinToString("[$marks]*")
        if (phrase) return Regex(words.joinToString("[$SEPARATORS$marks]+") { word(it) }, RegexOption.IGNORE_CASE)
        val alternatives = words.distinct().sortedByDescending { it.length }.map { word(it) }
        return Regex(alternatives.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    /** [highlighter]'s word by word: one pattern for each word of [query], all of which a line the scan finds holds. */
    fun wordPatterns(query: String): List<Regex> =
        query.split(Regex("[$SEPARATORS]+")).map { w -> w.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }.distinct()
            .mapNotNull { highlighter(it) }
}
