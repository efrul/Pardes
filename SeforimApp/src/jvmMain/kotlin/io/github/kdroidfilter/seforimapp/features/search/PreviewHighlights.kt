package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine

/**
 * The ranges the passage preview highlights in one of its lines, [text]: in the [found] line, the search's own
 * highlights for [query] (SearchEngine.highlights: the passage closest in meaning, or the query's words); in the other
 * lines, the query's words, or nothing when the search highlights by meaning ([byMeaning]). Tied to the text, the
 * query and [found]: a line drawn where another line was (the next result's preview) never shows that line's ranges,
 * which would fall anywhere in its own text.
 */
@Composable
internal fun rememberPreviewHighlights(
    searchEngine: SearchEngine,
    text: String,
    query: String,
    found: Boolean,
    byMeaning: Boolean,
): List<IntRange> {
    val words = remember(text, query, byMeaning) { if (byMeaning) emptyList() else searchEngine.highlightRanges(text, query) }
    var own by remember(text, query, found) { mutableStateOf<List<IntRange>?>(null) }
    LaunchedEffect(text, query, found) {
        if (found) {
            runSuspendCatching { searchEngine.highlights(listOf(text), query).single().ranges }.onSuccess { own = it }
        }
    }
    return own ?: words
}
