// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): of Compose and AndroidX
// lifecycle only @Immutable, ViewModel, viewModelScope and ViewModelProvider.Factory, which that edition stands in for
// (legacy/src/*/kotlin/androidx), and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug).
package io.github.kdroidfilter.seforimapp.android.ui.search

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.android.search.searchKey
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import java.util.Locale

// The results' view logic (moved from SearchScreen.kt, shared with the Android 4.4 edition).

// Desktop 0.6.1 strings.
internal const val SEARCH_SEARCHING = "מחפש..."
internal const val SEARCH_NO_RESULTS = "לא נמצאו תוצאות"
internal const val SEARCH_MORE_FROM_BOOK = "עוד %1\$d תוצאות מ%2\$s"
internal const val SEARCH_STATUS_BASE = "כ־%1\$s תוצאות בספרי יסוד"
internal const val SEARCH_STATUS_ALL = "כ־%1\$s תוצאות בכל הספרים"
internal const val SEARCH_STATUS_IN = "כ־%1\$s תוצאות ב%2\$s"
internal const val SEARCH_STATUS_FALLBACK = "לא נמצא בספרי יסוד · מוצגות %1\$s תוצאות מכל הספרים"
internal const val SEARCH_SWITCH_ALL = "חיפוש בכל הספרים"
internal const val SEARCH_SWITCH_BASE = "רק ספרי יסוד"
internal const val SEARCH_TAB_ALL = "הכל"
internal const val SEARCH_TABS_MORE = "עוד"
internal const val SEARCH_TABS_LESS = "פחות"
internal const val SEARCH_BACK_TO_ALL = "→ חזרה לכל התוצאות (מוצגות התוצאות מ%1\$s)"
internal const val SEARCH_STOP = "בטל חיפוש"
internal const val SEARCH_IN_PAGE = "הקלד כדי לחפש בעמוד..."
internal const val FIND_PREVIOUS = "התוצאה הקודמת"
internal const val FIND_NEXT = "התוצאה הבאה"
internal const val FIND_CLOSE = "סגירת החיפוש"
internal const val ZOOM_IN = "הגדל"
internal const val ZOOM_IN_TOOLTIP = "הגדל גודל הטקסט"
internal const val ZOOM_OUT = "הקטן"
internal const val ZOOM_OUT_TOOLTIP = "הקטן גודל הטקסט"
internal const val SEARCH_BOOK_DETAILS = "פרטי הספר"

// Android only: the engine failed (an I/O error, or Lucene unable to run on this device).
internal const val SEARCH_FAILED = "החיפוש נכשל"

internal fun String.fill(vararg args: Any): String = String.format(Locale.ROOT, this, *args)

/** One result of the flat list: a passage, and how many more its book has ([more], on the book's first result only). */
@Immutable
internal data class ResultItem(val hit: SearchResult, val more: Int)

/**
 * The results in relevance order, one passage per run of a book (its next ones are behind the "more" link), and a
 * passage whose text was already shown (the same prayer in two siddurim sections) dropped. A book may come back lower
 * with a later run. [bookCounts] (exact per-book totals) gives the "more in this book" count of a book's first result.
 * Desktop flattenResults; a line Lucene returned twice across pages shows once (the list is keyed by line).
 */
internal fun flattenResults(results: List<SearchResult>, bookCounts: Map<Long, Int>, oneBook: Boolean): List<ResultItem> {
    val linesSeen = HashSet<Long>()
    if (oneBook) return results.filter { linesSeen.add(it.lineId) }.map { ResultItem(it, more = 0) }
    val items = ArrayList<ResultItem>()
    val booksSeen = HashSet<Long>()
    val textsSeen = HashSet<String>()
    var runBook = -1L
    for (r in results) {
        if (r.bookId == runBook) continue
        runBook = r.bookId
        if (!textsSeen.add(r.snippet.replace(HTML_TAG, "").searchKey())) continue
        if (!linesSeen.add(r.lineId)) continue
        val more = if (booksSeen.add(r.bookId)) ((bookCounts[r.bookId] ?: 1) - 1).coerceAtLeast(0) else 0
        items += ResultItem(r, more)
    }
    return items
}

/**
 * Phones: the snippet keeps text on each side of the first hit, which pushes the hit past the two lines shown on a
 * narrow screen. Keep less before it; cuts are made at spaces outside <b>…</b>, so tags stay balanced.
 */
internal fun compactSnippet(html: String, before: Int = 40, after: Int = 200): String {
    val first = html.indexOf("<b>")
    if (first < 0) return html
    val spaces = ArrayList<Int>()
    var depth = 0
    var i = 0
    while (i < html.length) {
        when {
            html.startsWith("<b>", i) -> {
                depth++
                i += 3
            }
            html.startsWith("</b>", i) -> {
                depth--
                i += 4
            }
            else -> {
                if (html[i] == ' ' && depth == 0) spaces += i
                i++
            }
        }
    }
    val start = spaces.lastOrNull { it <= first - before }
    val end = spaces.firstOrNull { it >= first + after }
    if (start == null && end == null) return html
    return (if (start != null) "..." else "") + html.substring((start ?: -1) + 1, end ?: html.length) + (if (end != null) "..." else "")
}
