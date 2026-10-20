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

// Text helpers of the reader, ported from desktop 0.6.1 core/presentation/text: Highlighting.kt, Notes.kt, and
// htmlparser's SkiaHtmlImageBuilder (here on BitmapFactory). DiacriticsMode.kt and HebrewSearch.kt are in Diacritics.kt.

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
    val length = layout.layoutInput.text.length
    for (range in ranges) {
        val startOffset = range.first
        val endOffset = range.last + 1
        if (endOffset <= startOffset || startOffset >= length) continue
        val firstLine = layout.getLineForOffset(startOffset)
        val lastLine = layout.getLineForOffset((endOffset - 1).coerceIn(startOffset, length - 1))
        for (line in firstLine..lastLine) {
            val segStart = if (line == firstLine) startOffset else layout.getLineStart(line)
            val segEnd = if (line == lastLine) endOffset.coerceAtMost(length) else layout.getLineEnd(line, visibleEnd = true)
            val x1 = layout.getHorizontalPosition(segStart, usePrimaryDirection = true)
            val x2 = layout.getHorizontalPosition(segEnd, usePrimaryDirection = true)
            val y = layout.getLineBottom(line) - stroke
            drawLine(color, Offset(min(x1, x2), y), Offset(max(x1, x2), y), strokeWidth = stroke, pathEffect = dash)
        }
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
