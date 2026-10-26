package io.github.kdroidfilter.seforimapp.android.ui.reader

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import org.jsoup.Jsoup
import java.net.URLEncoder

// Ports of desktop 0.6.10 core/CopyWithSource.kt, core/BookErrorReport.kt and core/annotations/HighlightSelectionResolver.kt.

private val WHITESPACE_REGEX = Regex("\\s+")
private const val MIN_EDGE_OVERLAP = 8

// Title of the Talmud root category: its citations drop the in-page line ("סוכה ב., ב" → "סוכה ב.").
private const val TALMUD_ROOT_TITLE = "תלמוד"

private fun normalizeForMatch(text: String): String = HebrewTextUtils.removeAllDiacritics(text).replace(WHITESPACE_REGEX, " ").trim()

private fun stripHtmlToPlainText(html: String): String = normalizeForMatch(Jsoup.parse(html).text())

/**
 * The first and last of [lines] a free-form [selectedText] overlaps, edges partially (desktop resolveLineRangeFromSelection).
 * Looked for from each end of the lines in order, which gives the same pair as testing them all: a selection the edge
 * scroll drew out spans thousands of lines, each test scans the whole selection, and the ends are found at once.
 */
fun resolveLineRangeFromSelection(selectedText: String, lines: List<Line>): Pair<Line, Line>? {
    val cleaned = normalizeForMatch(selectedText)
    if (cleaned.isEmpty() || lines.isEmpty()) return null
    fun matches(line: Line): Boolean {
        val plain = stripHtmlToPlainText(line.content)
        return plain.isNotEmpty() && (
            cleaned.contains(plain) || plain.contains(cleaned) ||
                (plain.length >= MIN_EDGE_OVERLAP && cleaned.endsWith(plain.take(MIN_EDGE_OVERLAP))) ||
                (plain.length >= MIN_EDGE_OVERLAP && cleaned.startsWith(plain.takeLast(MIN_EDGE_OVERLAP)))
            )
    }
    val sorted = lines.sortedBy { it.lineIndex }
    val first = sorted.firstOrNull(::matches) ?: return null
    return first to (sorted.lastOrNull(::matches) ?: first)
}

/** "בראשית א, ב – בראשית א, ה", "ברכות ב." (desktop formatHebrewSourceReference). */
fun formatHebrewSourceReference(book: Book, rootTitle: String?, firstLine: Line, lastLine: Line): String {
    val bookRef = book.heRef?.takeIf { it.isNotBlank() } ?: book.title
    val isTalmud = rootTitle == TALMUD_ROOT_TITLE
    val prefixes = listOfNotNull(book.heRef, book.title).filter { it.isNotBlank() }.distinct()

    fun fullRefFor(heRef: String?): String? {
        if (heRef.isNullOrBlank()) return null
        val base = if (prefixes.any { heRef.startsWith(it) }) heRef else "$bookRef $heRef"
        val trimmed = if (isTalmud) base.substringBeforeLast(',').trim() else base.trim()
        return trimmed.replace(WHITESPACE_REGEX, " ").ifEmpty { null }
    }

    val first = fullRefFor(firstLine.heRef)
    val last = fullRefFor(lastLine.heRef)
    return when {
        first == null && last == null -> bookRef
        first == null -> last!!
        last == null || first == last -> first
        else -> "$first – $last"
    }
}

/** "$text\n\n(<reference>)" (desktop buildCopyWithSourcePayload). */
fun buildCopyWithSourcePayload(selectedText: String, book: Book, rootTitle: String?, loadedLines: List<Line>, lineRange: Pair<Line, Line>? = null): String {
    val range = lineRange ?: resolveLineRangeFromSelection(selectedText, loadedLines)
    val reference = range?.let { formatHebrewSourceReference(book, rootTitle, it.first, it.second) } ?: book.heRef?.takeIf { it.isNotBlank() } ?: book.title
    return "$selectedText\n\n($reference)"
}

private const val SEFARIA_SOURCE = "Sefaria"
private const val SEFARIA_CORRECTIONS_EMAIL = "corrections@sefaria.org"
private const val OTZARIA_CORRECTIONS_EMAIL = "otzaria.200@gmail.com"
private const val MAX_QUOTED_CHARS = 400

/**
 * The mailto reporting an error in [book] at [lines] (desktop bookErrorReportMailto): Sefaria's books go to Sefaria
 * with a pre-filled message, every other one came through Otzaria and goes to its team.
 */
suspend fun bookErrorReportMailto(repository: SeforimRepository, book: Book, rootTitle: String?, lines: Pair<Line, Line>?, selectedText: String): String {
    val reference = lines?.let { formatHebrewSourceReference(book, rootTitle, it.first, it.second) } ?: book.heRef?.takeIf { it.isNotBlank() } ?: book.title
    if (repository.getSourceById(book.sourceId)?.name != SEFARIA_SOURCE) {
        return mailto(OTZARIA_CORRECTIONS_EMAIL, "טעות ב$reference", body = null)
    }
    val quoted = selectedText.trim().ifEmpty { lines?.first?.let { Jsoup.parse(it.content).text() }.orEmpty() }
    val versions = repository.getBookEditions(book.id).map { it.title }.distinct()
    return mailto(SEFARIA_CORRECTIONS_EMAIL, "טעות ב$reference", sefariaReportBody(reference, versions, quoted))
}

internal fun sefariaReportBody(reference: String, versions: List<String>, quoted: String): String = buildString {
    appendLine("שלום לצוות ספריא,")
    appendLine()
    appendLine("מצאתי טעות בטקסט הבא.")
    appendLine()
    appendLine("מקור: $reference")
    if (versions.isNotEmpty()) appendLine("מהדורה: ${versions.joinToString(" / ")}")
    appendLine()
    appendLine("הנוסח הנוכחי:")
    appendLine(if (quoted.length > MAX_QUOTED_CHARS) quoted.take(MAX_QUOTED_CHARS) + "…" else quoted)
    appendLine()
    appendLine("הטעות:")
    appendLine("[נא לתאר כאן את הטעות]")
    appendLine()
    appendLine("התיקון המוצע:")
    appendLine("[נא לכתוב כאן את הנוסח הנכון]")
    appendLine()
    append("נשלח מתוך פרדס, אפליקציה לאנדרואיד המבוססת על זית")
}

private fun mailto(email: String, subject: String, body: String?): String {
    val query = listOfNotNull("subject" to subject, body?.let { "body" to it })
    return "mailto:$email?" + query.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8").replace("+", "%20")}" }
}

// ---- Highlight ranges from a selection (HighlightSelectionResolver.kt) ----

/** A resolved highlight range for one line, in original (with-diacritics) coordinates. */
data class LineHighlightRange(val lineId: Long, val range: IntRange)

/** Where [selectedText] lies in [linePlainText] (with diacritics), in its coordinates; null when not found. */
fun resolveHighlightRange(linePlainText: String, selectedText: String, diacritics: DiacriticsMode): IntRange? {
    val needle = selectedText.trim()
    if (needle.isEmpty() || linePlainText.isEmpty()) return null
    if (diacritics == DiacriticsMode.All) {
        val start = linePlainText.indexOf(needle)
        if (start < 0) return null
        return start until (start + needle.length)
    }
    val (stripped, strippedToOriginal) = stripNikudTeamimWithMap(linePlainText, diacritics)
    val start = stripped.indexOf(needle)
    if (start < 0) return null
    val end = start + needle.length
    val originalStart = if (start < strippedToOriginal.size) strippedToOriginal[start] else linePlainText.length
    val originalEnd = if (end < strippedToOriginal.size) strippedToOriginal[end] else linePlainText.length
    if (originalEnd <= originalStart) return null
    return originalStart until originalEnd
}

/**
 * The ranges a (possibly multi-line) selection covers in [sortedVisibleLines] — (lineId, plain text with diacritics)
 * in reading order: the first segment anchored on the line ending with it, the next ones on the following lines.
 */
fun resolveHighlightRangesForSelection(sortedVisibleLines: List<Pair<Long, String>>, selectedText: String, diacritics: DiacriticsMode): List<LineHighlightRange> {
    if (sortedVisibleLines.isEmpty()) return emptyList()
    val segments = selectedText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    if (segments.isEmpty()) return emptyList()

    fun displayed(plain: String): String = diacritics.apply(plain)

    if (segments.size == 1) {
        val seg = segments.first()
        val line = sortedVisibleLines.firstOrNull { displayed(it.second).contains(seg) } ?: return emptyList()
        val range = resolveHighlightRange(line.second, seg, diacritics) ?: return emptyList()
        return listOf(LineHighlightRange(line.first, range))
    }
    val firstSeg = segments.first()
    var startIndex = sortedVisibleLines.indexOfFirst { displayed(it.second).trimEnd().endsWith(firstSeg) }
    if (startIndex < 0) startIndex = sortedVisibleLines.indexOfFirst { displayed(it.second).contains(firstSeg) }
    if (startIndex < 0) return emptyList()
    val result = mutableListOf<LineHighlightRange>()
    for (offset in segments.indices) {
        val lineIndex = startIndex + offset
        if (lineIndex > sortedVisibleLines.lastIndex) break
        val (lineId, plain) = sortedVisibleLines[lineIndex]
        result += LineHighlightRange(lineId, resolveHighlightRange(plain, segments[offset], diacritics) ?: (0 until plain.length))
    }
    return result
}
