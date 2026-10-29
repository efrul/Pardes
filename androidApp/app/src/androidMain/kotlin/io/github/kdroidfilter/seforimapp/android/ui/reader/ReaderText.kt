package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.HtmlImageContentBuilder
import io.github.kdroidfilter.seforimapp.android.data.UserHighlight
import io.github.kdroidfilter.seforimapp.android.data.UserNote
import kotlin.math.max
import kotlin.math.min

// Text helpers of the reader, ported from desktop 0.6.10 core/presentation/text: DiacriticsMode.kt, HebrewSearch.kt,
// Highlighting.kt, Notes.kt, and htmlparser's SkiaHtmlImageBuilder (here on BitmapFactory).

/** Which Hebrew diacritics the reader displays; the diacritics button cycles through them (desktop DiacriticsMode). */
enum class DiacriticsMode {
    /** Nikud and teamim. */
    All,

    /** Nikud only: teamim are hidden, and so is the meteg, which mostly marks the silluq. */
    NikudOnly,

    /** Neither nikud nor teamim. */
    None,
    ;

    /** The mode the button moves to; [NikudOnly] is skipped when [hasTeamim] is false, as it would show no change. */
    fun next(hasTeamim: Boolean = true): DiacriticsMode {
        val next = entries[(ordinal + 1) % entries.size]
        return if (next == NikudOnly && !hasTeamim) next.next() else next
    }

    /** True when this mode drops [c] from the displayed text. */
    fun hides(c: Char): Boolean = when (this) {
        All -> false
        NikudOnly -> isTeamim(c) || c == METEG
        None -> isNikudOrTeamim(c)
    }

    /** [text] as displayed in this mode. */
    fun apply(text: String): String = if (this == All) text else text.filterNot(::hides)

    /** As desktop's category_display_settings stores it: 0 none, 1 all, 2 nikud only. */
    val stored: Long
        get() = when (this) {
            All -> 1L
            NikudOnly -> 2L
            None -> 0L
        }

    /** Diacritic layers shown, for the button's dots: nikud and teamim, nikud only, none. */
    val shownLevel: Int
        get() = when (this) {
            All -> 2
            NikudOnly -> 1
            None -> 0
        }

    companion object {
        fun fromStored(value: Long?): DiacriticsMode = when (value) {
            0L -> None
            2L -> NikudOnly
            else -> All
        }
    }
}

/** What the diacritics button does next, as desktop's tooltip says it (DiacriticsButton). */
fun DiacriticsMode.nextActionLabel(hasTeamim: Boolean): String = when (next(hasTeamim)) {
    DiacriticsMode.All -> "הצג ניקוד וטעמים"
    DiacriticsMode.NikudOnly -> "הסתר טעמים"
    DiacriticsMode.None -> if (this == DiacriticsMode.NikudOnly) "הסתר ניקוד" else "הסתר ניקוד וטעמים"
}

private const val METEG = 'ֽ'

private fun isTeamim(c: Char): Boolean = c.code in 0x0591..0x05AF

/** True for nikud (vowel points, meteg included) and ta'amim (cantillation) characters. */
internal fun isNikudOrTeamim(c: Char): Boolean =
    isTeamim(c) || (c.code in 0x05B0..0x05BD) || c == 'ׁ' || c == 'ׂ' || c == 'ׇ'

/** True when [text] carries nikud or teamim (desktop HebrewTextUtils.containsNikud/containsTeamim). */
fun hasDiacritics(text: String): Boolean = text.any(::isNikudOrTeamim)

// ---- Hebrew-aware matching (HebrewSearch.kt) ----

/** [src] without nikud, teamim, geresh and gershayim, and the map from plain index to original index. */
fun stripDiacriticsWithMap(src: String): Pair<String, IntArray> {
    val out = StringBuilder(src.length)
    val map = IntArray(src.length)
    var count = 0
    for (i in src.indices) {
        val ch = src[i]
        if (!isNikudOrTeamim(ch) && ch != '״' && ch != '׳') {
            out.append(ch)
            map[count++] = i
        }
    }
    return out.toString() to (if (count == map.size) map else map.copyOf(count))
}

/** Strips what [mode] hides (keeping ׳ ״), as [DiacriticsMode.apply], with the map stripped index -> original index. */
fun stripNikudTeamimWithMap(src: String, mode: DiacriticsMode = DiacriticsMode.None): Pair<String, IntArray> {
    val out = StringBuilder(src.length)
    val map = IntArray(src.length)
    var count = 0
    for (i in src.indices) {
        val ch = src[i]
        if (!mode.hides(ch)) {
            out.append(ch)
            map[count++] = i
        }
    }
    return out.toString() to (if (count == map.size) map else map.copyOf(count))
}

internal fun replaceFinalsWithBase(text: String): String =
    text.replace('ך', 'כ').replace('ם', 'מ').replace('ן', 'נ').replace('ף', 'פ').replace('ץ', 'צ')

private val TEAMIM_REGEX = Regex("[֑-֯]")
private val NIKUD_REGEX = Regex("[ְֱֲֳִֵֶַָׇֹֻּֽׁׂ]")

internal fun normalizeQueryForHebrew(raw: String): String {
    if (raw.isBlank()) return ""
    var s = raw.trim()
    s = s.replace(TEAMIM_REGEX, "")
    s = s.replace(NIKUD_REGEX, "")
    s = s.replace('־', ' ')
    s = s.replace("״", "").replace("׳", "")
    s = replaceFinalsWithBase(s)
    return s.lowercase()
}

internal fun mapToOrigIndex(mapToOrig: IntArray, plainIndex: Int): Int {
    if (mapToOrig.isEmpty()) return plainIndex
    return mapToOrig[plainIndex.coerceIn(0, mapToOrig.size - 1)]
}

/** Original index -> displayed index in [mode], -1 where the character is hidden. */
fun createOriginalToStrippedMap(src: String, mode: DiacriticsMode = DiacriticsMode.None): IntArray {
    val result = IntArray(src.length) { -1 }
    var strippedIndex = 0
    for (i in src.indices) {
        if (!mode.hides(src[i])) result[i] = strippedIndex++
    }
    return result
}

/** Maps an offset of the text with diacritics to the displayed text (the next shown character when it is hidden). */
fun mapOriginalToStripped(originalOffset: Int, originalToStrippedMap: IntArray): Int {
    if (originalToStrippedMap.isEmpty()) return originalOffset
    val strippedLength = originalToStrippedMap.count { it >= 0 }
    if (originalOffset >= originalToStrippedMap.size) return strippedLength
    val safe = originalOffset.coerceAtLeast(0)
    if (originalToStrippedMap[safe] >= 0) return originalToStrippedMap[safe]
    for (i in safe until originalToStrippedMap.size) if (originalToStrippedMap[i] >= 0) return originalToStrippedMap[i]
    return strippedLength
}

/** Diacritic- and final-letter-insensitive matches of [query] in [text], as ranges of [text]. */
internal fun findAllMatchesOriginal(text: String, query: String): List<IntRange> = findAllMatchesNormalized(text, normalizeQueryForHebrew(query))

/** [findAllMatchesOriginal] for a query already through [normalizeQueryForHebrew]. */
internal fun findAllMatchesNormalized(text: String, q: String): List<IntRange> {
    if (q.length < 2) return emptyList()
    val (plain, map) = stripDiacriticsWithMap(text)
    val plainSearch = replaceFinalsWithBase(plain).lowercase()
    val out = mutableListOf<IntRange>()
    var from = 0
    while (from <= plainSearch.length - q.length) {
        val idx = plainSearch.indexOf(q, startIndex = from)
        if (idx == -1) break
        val startOrig = mapToOrigIndex(map, idx)
        val endOrig = mapToOrigIndex(map, idx + q.length - 1) + 1
        if (endOrig > startOrig) out += (startOrig until endOrig)
        from = idx + q.length
    }
    return out
}

// ---- Highlighting (Highlighting.kt) ----

/** Background of the current find-in-page match, set apart from the others as in Chrome. */
val CurrentFindMatchColor = Color(0xCCFF9632)

/** The palette offered to the user (desktop HighlightColors), in its order; [Color.Transparent] clears. */
val HighlightPalette = listOf(Color(0xFFFFEB3B), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFFFF9800))

/** A copy of this text with a [color] background over each of [ranges]. */
fun AnnotatedString.withBackground(ranges: List<IntRange>, color: Color): AnnotatedString {
    if (ranges.isEmpty()) return this
    val b = AnnotatedString.Builder(this)
    for (r in ranges) {
        val start = r.first.coerceIn(0, length)
        val end = (r.last + 1).coerceAtMost(length)
        if (end > start) b.addStyle(SpanStyle(background = color), start, end)
    }
    return b.toAnnotatedString()
}

/** Every match of [query] highlighted, the [currentIndex]-th (from 0) in [currentColor] (desktop highlightAnnotatedWithCurrent). */
fun highlightAnnotatedWithCurrent(annotated: AnnotatedString, query: String?, baseColor: Color, currentColor: Color, currentIndex: Int? = null): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.length < 2) return annotated
    val ranges = findAllMatchesOriginal(annotated.text, q)
    if (ranges.isEmpty()) return annotated
    val b = AnnotatedString.Builder(annotated)
    ranges.forEachIndexed { i, r ->
        val start = r.first.coerceIn(0, annotated.length)
        val end = (r.last + 1).coerceAtMost(annotated.length)
        if (end > start) b.addStyle(SpanStyle(background = if (i == currentIndex) currentColor else baseColor), start, end)
    }
    return b.toAnnotatedString()
}

/**
 * Applies the user's [highlights] of one line, stored against the text with diacritics: when some are hidden,
 * [originalText] remaps them onto the displayed text (desktop applyUserHighlights, alpha 0.4).
 */
fun applyUserHighlights(annotated: AnnotatedString, highlights: List<UserHighlight>, originalText: String?, diacritics: DiacriticsMode, alpha: Float = 0.4f): AnnotatedString {
    if (highlights.isEmpty()) return annotated
    val map = if (diacritics != DiacriticsMode.All && originalText != null) createOriginalToStrippedMap(originalText, diacritics) else null
    val b = AnnotatedString.Builder(annotated)
    for (h in highlights) {
        val start = map?.let { mapOriginalToStripped(h.start, it) } ?: h.start
        val end = map?.let { mapOriginalToStripped(h.end, it) } ?: h.end
        val s = start.coerceIn(0, annotated.length)
        val e = end.coerceAtMost(annotated.length)
        if (e > s) b.addStyle(SpanStyle(background = Color(h.colorArgb.toInt()).copy(alpha = alpha)), s, e)
    }
    return b.toAnnotatedString()
}

/** Ranges of the displayed text the [notes] cover (desktop noteDisplayRanges). */
fun noteDisplayRanges(notes: List<UserNote>, originalText: String?, diacritics: DiacriticsMode, displayLength: Int): List<IntRange> {
    if (notes.isEmpty()) return emptyList()
    val map = if (diacritics != DiacriticsMode.All && originalText != null) createOriginalToStrippedMap(originalText, diacritics) else null
    return notes.mapNotNull { n ->
        val start = (map?.let { mapOriginalToStripped(n.startOffset, it) } ?: n.startOffset).coerceIn(0, displayLength)
        val end = (map?.let { mapOriginalToStripped(n.endOffset, it) } ?: n.endOffset).coerceAtMost(displayLength)
        if (end > start) start until end else null
    }
}

/** A dotted grey underline under each of [ranges], through wrapped lines and RTL text (desktop drawNoteUnderlines). */
fun DrawScope.drawNoteUnderlines(layout: TextLayoutResult, ranges: List<IntRange>, color: Color) {
    if (ranges.isEmpty()) return
    val stroke = density
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f * stroke, 3f * stroke), 0f)
    for (range in ranges) {
        layout.forEachRowSpan(range.first, range.last + 1) { line, _, _, x1, x2 ->
            val y = layout.getLineBottom(line) - stroke
            drawLine(color, Offset(min(x1, x2), y), Offset(max(x1, x2), y), strokeWidth = stroke, pathEffect = dash)
        }
    }
}

/**
 * Calls [action] for each visual row the range [start] until [end] covers, as laid out (wrapped, RTL): the row, the
 * part of the range on it ([from] until [to]) and its horizontal positions at [from] and [to], in either order
 * (desktop 0.6.12 forEachRowSpan, Notes.kt; the range is kept inside the text, as drawNoteUnderlines did).
 */
inline fun TextLayoutResult.forEachRowSpan(start: Int, end: Int, action: (row: Int, from: Int, to: Int, xFrom: Float, xTo: Float) -> Unit) {
    val length = layoutInput.text.length
    if (end <= start || start >= length) return
    val firstRow = getLineForOffset(start)
    val lastRow = getLineForOffset((end - 1).coerceIn(start, length - 1))
    for (row in firstRow..lastRow) {
        val from = if (row == firstRow) start else getLineStart(row)
        val to = if (row == lastRow) end.coerceAtMost(length) else getLineEnd(row, visibleEnd = true)
        action(row, from, to, getHorizontalPosition(from, usePrimaryDirection = true), getHorizontalPosition(to, usePrimaryDirection = true))
    }
}

// ---- Inline images (htmlparser SkiaHtmlImageBuilder, Android decoder) ----

/** Inverts RGB, keeps alpha: black-on-white glyph images in dark mode. */
val InvertColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

private class ImageKey(val bytes: ByteArray) {
    private val hash = bytes.contentHashCode()

    override fun equals(other: Any?): Boolean = other is ImageKey && other.hash == hash && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = hash
}

private val bitmapCache = object : LinkedHashMap<ImageKey, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ImageKey, ImageBitmap>): Boolean = size > 64
}

private fun decodeImage(bytes: ByteArray): ImageBitmap? {
    val key = ImageKey(bytes)
    synchronized(bitmapCache) { bitmapCache[key] }?.let { return it }
    val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() ?: return null
    synchronized(bitmapCache) { bitmapCache[key] = bitmap }
    return bitmap
}

/** The images of the texts (data URIs), at their pixel size in sp as desktop, inverted in dark mode. */
fun readerImageBuilder(dark: Boolean): HtmlImageContentBuilder = builder@{ element, _ ->
    val bytes = element.imageBytes ?: return@builder null
    val bitmap = decodeImage(bytes) ?: return@builder null
    val w = (element.imageWidth ?: bitmap.width).coerceAtLeast(1)
    val h = (element.imageHeight ?: bitmap.height).coerceAtLeast(1)
    InlineTextContent(Placeholder(w.sp, h.sp, PlaceholderVerticalAlign.TextCenter)) {
        Image(BitmapPainter(bitmap), null, contentScale = ContentScale.Fit, colorFilter = if (dark) InvertColorFilter else null)
    }
}
