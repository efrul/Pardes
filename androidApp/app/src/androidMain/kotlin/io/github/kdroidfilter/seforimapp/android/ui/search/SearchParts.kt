package io.github.kdroidfilter.seforimapp.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import java.text.NumberFormat
import java.util.Locale

// Pieces shared by the results page, its preview and its knowledge panel, and by the reader's places panes: desktop
// 0.6.11 SearchResultScreen.kt's card(), readingSecondary(), ResultLink, ResultRow, ResultTitle, SnippetText,
// LocalResultTextScale and categoriesAndPlace, core/presentation/text HebrewSearch.kt's diacritic-insensitive matching
// (find within the results) and Highlighting.kt's withBackground.

/** Desktop SearchStatusLine count: grouped digits (NumberFormat.getIntegerInstance). */
internal fun formatCount(n: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(n)

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

/**
 * How much a result list's results are scaled: the reading text's size over its default, so that they follow the zoom
 * as the panes' texts do (desktop 0.6.11 LocalResultTextScale). Only the results: the tabs and the status above them
 * keep their size. 1 where nothing provides it.
 */
internal val LocalResultTextScale = compositionLocalOf { 1f }

/** [size] sp, scaled with the result list it is in ([LocalResultTextScale]). */
@Composable
internal fun resultSp(size: Float): TextUnit = (size * LocalResultTextScale.current).sp

/**
 * A row of a result list (desktop 0.6.11 ResultRow): the selected row's tint and its bar on its start side, a divider
 * under it. Shared by the search results and the places that refer to a line. [onDoubleClick] only where a tap selects
 * (two panes): a double-tap handler holds every single tap back for the double-tap timeout.
 */
@Composable
internal fun ResultRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zayit.colors
    val accent = c.accent
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .drawBehind {
                // The selected row's bar, on its start side
                if (selected) {
                    val bar = 3.dp.toPx()
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - bar else 0f
                    drawRect(accent, topLeft = Offset(x, 0f), size = Size(bar, size.height))
                }
            }
            .combinedClickable(onDoubleClick = onDoubleClick, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), content = content)
        Box(Modifier.fillMaxWidth().padding(start = 8.dp).height(1.dp).background(c.panelBorder.copy(alpha = 0.5f)))
    }
}

/** A result's book as its bold title, the passage's place beside it in grey (desktop ResultTitle); on a phone one size up. */
@Composable
internal fun ResultTitle(title: String, place: String?, wide: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Zayit.colors.text, fontSize = resultSp(if (wide) 14f else 15f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (place != null) {
            Text("· $place", color = readingSecondary(), fontSize = resultSp(if (wide) 12f else 13f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

/**
 * A passage ([snippet], HTML; desktop SnippetText): [maxLines] lines of grey text, the matched words (`<b>`) bold in the
 * text's colour, [findQuery] lit. A phone shows fewer words a line: the text before the first bold run is shortened so
 * that it is in view ([compactSnippet]).
 */
@Composable
internal fun SnippetText(snippet: String, findQuery: String, wide: Boolean, maxLines: Int) {
    val c = Zayit.colors
    val ink = c.text
    val size = (if (wide) 13f else 14f) * LocalResultTextScale.current
    val findHighlight = c.accent.copy(alpha = 0.12f)
    val display = remember(snippet, size, ink, wide, findQuery, findHighlight) {
        val html = if (wide) snippet else compactSnippet(snippet)
        buildAnnotatedFromHtml(html, size, boldColor = ink).highlightMatches(findQuery, findHighlight)
    }
    Text(
        text = display,
        color = readingSecondary(),
        fontSize = size.sp,
        fontFamily = ZayitFonts.ui,
        lineHeight = (size * 1.65f).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** The result's categories (הלכה › ראשונים) and its place in the book (סימן קד), once its breadcrumb is known. */
internal fun categoriesAndPlace(pieces: List<String>?, bookTitle: String): Pair<String, String?> {
    val bookIndex = pieces?.indexOf(bookTitle) ?: -1
    if (pieces == null || bookIndex < 0) return "" to null
    val place = pieces.drop(bookIndex + 1).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
    return pieces.take(bookIndex).joinToString(" › ") to place
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
        Text("אינדקס החיפוש של הספרייה חסר", color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(SEARCH_INDEX_MISSING_TEXT, color = c.text, fontSize = 15.sp, lineHeight = 22.sp)
        Text("תיקיית הספרייה:", color = c.textSecondary, fontSize = 13.sp)
        LibraryPathText(libraryDir)
    }
}

const val SEARCH_INDEX_MISSING_TEXT =
    "החיפוש בטקסט המלא משתמש באותו אינדקס שהאפליקציה למחשב שומרת לצד מסד הנתונים: התיקיות seforim.db.lucene " +
        "ו־seforim.db.lookup.lucene והקובץ lexical.db. האינדקס אינו נבנה על המכשיר. כדי לקבל אותו, העבירו את הקבצים מהמחשב " +
        "לתיקיית הספרייה בעזרת סקריפט ההעברה (push-desktop-library), או התקינו את הספרייה מחדש (החבילה המלאה כוללת אותו): " +
        "\"איפוס האפליקציה\" בהגדרות › ניהול נתונים מחזיר למסך ההתקנה, ומוחק גם את ההערות וההדגשות, לכן ייצאו אותן קודם."

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
