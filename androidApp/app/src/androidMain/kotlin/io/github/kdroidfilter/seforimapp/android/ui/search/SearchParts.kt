package io.github.kdroidfilter.seforimapp.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

// Pieces shared by the results page, its preview and its knowledge panel: desktop 0.6.1 SearchResultScreen.kt's
// card(), readingSecondary() and ResultLink, core/presentation/text HebrewSearch.kt's
// diacritic-insensitive matching (find within the results) and Highlighting.kt's withBackground.

/** The page's panes (desktop card(), as the book's PaneCard): a small gap around, rounded, on the panel colour, no border. */
@Composable
internal fun Modifier.card(): Modifier = padding(vertical = 6.dp, horizontal = 4.dp).clip(RoundedCornerShape(12.dp)).background(Zayit.colors.panel)

/** Secondary text a dark grey: the theme's grey is too faint to read a passage in (desktop readingSecondary). */
@Composable
internal fun readingSecondary(): Color = Zayit.colors.text.copy(alpha = 0.72f)

/** A text link in [color] (desktop ResultLink); [minHeight] gives it a touch target. */
@Composable
internal fun ResultLink(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
    maxLines: Int = 1,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier.heightIn(min = minHeight).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** A copy of this text with a [color] background over each of [ranges]. */
internal fun AnnotatedString.withBackground(ranges: List<IntRange>, color: Color): AnnotatedString {
    if (ranges.isEmpty()) return this
    val builder = AnnotatedString.Builder()
    builder.append(this)
    for (r in ranges) {
        val start = r.first.coerceIn(0, length)
        val end = (r.last + 1).coerceAtMost(length)
        if (end > start) builder.addStyle(SpanStyle(background = color), start, end)
    }
    return builder.toAnnotatedString()
}

private fun isNikudOrTeamim(c: Char): Boolean = c.code in 0x0591..0x05AF || c.code in 0x05B0..0x05BD || c.code == 0x05C1 || c.code == 0x05C2 || c.code == 0x05C7

private fun replaceFinalsWithBase(text: String): String =
    text.replace('ך', 'כ').replace('ם', 'מ').replace('ן', 'נ').replace('ף', 'פ').replace('ץ', 'צ')

/** Desktop normalizeQueryForHebrew: no teamim or nikud, maqaf as a space, no geresh or gershayim, base letters, lower case. */
private fun normalizeQueryForHebrew(raw: String): String {
    if (raw.isBlank()) return ""
    var s = raw.trim()
    s = s.replace("[֑-֯]".toRegex(), "")
    s = s.replace("[ְֱֲֳִֵֶַָׇֹֻּֽׁׂ]".toRegex(), "")
    s = s.replace('־', ' ').replace("״", "").replace("׳", "")
    return replaceFinalsWithBase(s).lowercase()
}

/**
 * Every diacritic-insensitive occurrence of [query] in [text], as ranges of [text] (desktop findAllMatchesOriginal):
 * what find within the results steps through and highlights.
 */
internal fun findAllMatches(text: String, query: String): List<IntRange> {
    val q = normalizeQueryForHebrew(query)
    if (q.length < 2) return emptyList()
    val plain = StringBuilder(text.length)
    val map = IntArray(text.length)
    var count = 0
    for (i in text.indices) {
        val ch = text[i]
        if (!isNikudOrTeamim(ch) && ch != '״' && ch != '׳') {
            plain.append(ch)
            map[count++] = i
        }
    }
    if (count == 0) return emptyList()
    val search = replaceFinalsWithBase(plain.toString()).lowercase()
    val out = ArrayList<IntRange>()
    var from = 0
    while (from <= search.length - q.length) {
        val idx = search.indexOf(q, startIndex = from)
        if (idx == -1) break
        val start = map[idx.coerceIn(0, count - 1)]
        val end = map[(idx + q.length - 1).coerceIn(0, count - 1)] + 1
        if (end > start) out += start until end
        from = idx + q.length
    }
    return out
}

/** Desktop highlightAnnotated: [query]'s matches (from 2 letters) on a [color] background. */
internal fun AnnotatedString.highlightMatches(query: String?, color: Color): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.length < 2) return this
    return withBackground(findAllMatches(text, q), color)
}

/** Shown in place of the results when the library has no full-text index (Android only: it is never built here). */
@Composable
fun SearchIndexMissing(libraryDir: String, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(SEARCH_INDEX_MISSING, color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(SEARCH_INDEX_MISSING_TEXT, color = c.text, fontSize = 15.sp, lineHeight = 22.sp)
        Text(SEARCH_LIBRARY_FOLDER, color = c.textSecondary, fontSize = 13.sp)
        LibraryPathText(libraryDir)
    }
}

/** A file path: selectable, monospace, always left-to-right. */
@Composable
fun LibraryPathText(path: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Text(
            path,
            fontSize = 12.sp,
            color = Zayit.colors.textSecondary,
            fontFamily = FontFamily.Monospace,
            style = TextStyle(textDirection = TextDirection.Ltr),
            modifier = modifier.fillMaxWidth(),
        )
    }
}
