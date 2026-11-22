package io.github.kdroidfilter.seforimapp.features.bookcontent.find

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.text.SearchHighlightColor
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneSearchFocus
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneSearchLayout
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.revealItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.TocTextMatch
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.matchTocText
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.tocQueryTokens
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.toc_search
import seforimapp.seforimapp.generated.resources.toc_search_no_results

// Pardes (request 62 of docs/DESKTOP_STAGE3_PLAN.md): the results pane searched as the contents and the book tree are, by
// the titles of its entries, as the user asked after the third testers' build ("יוכלו למזער ולחפש בו כמו בתוכן העניינים
// וספרים").

/**
 * The results pane's search, as the contents keep theirs (TocState.search) for the pane's life: its bar shown ([query]
 * not null) with what is typed in it, and its field's [focus].
 */
@Stable
internal class FindResultsSearch {
    var query by mutableStateOf<String?>(null)
        private set

    val focus = PaneSearchFocus()

    /** Shows the bar, or hides it. */
    fun toggle() {
        query = if (query == null) "" else null
    }

    fun close() {
        query = null
    }

    /** What the field holds now; nothing once the bar is hidden (a query flushed by a closing field, or after Escape). */
    fun queryChanged(text: String) {
        if (query != null) query = text
    }
}

/** The results' search of the pane of the tab [tabId]. */
@Composable
internal fun rememberFindResultsSearch(tabId: String): FindResultsSearch = remember(tabId) { FindResultsSearch() }

/**
 * What the results' search keeps of the pane's rows ([filterFindRows]): the rows under the entries whose title matches,
 * in their order, with the ranges of each entry's title it matched ([highlights], by the entry's place in [rows]).
 */
@Immutable
internal data class FindFilterResult(
    val rows: List<FindRow> = emptyList(),
    val highlights: Map<Int, List<IntRange>> = emptyMap(),
    override val matchIds: List<Long> = emptyList(),
    override val bestMatchId: Long? = null,
) : PaneSearchResult

/**
 * The [rows] under the entries whose title matches [query] as the contents' search matches theirs ([matchTocText]:
 * each word of the query the start of a word of the title, nikud, quotes and final letters aside). Its matches are the
 * lines (or a PDF book's matches) under them, [FindRow.matchId]; Enter goes to the first under the entry with the most
 * whole words matched, as the contents' search opens its entry.
 */
internal fun filterFindRows(
    rows: List<FindRow>,
    query: String,
): FindFilterResult {
    val tokens = tocQueryTokens(query)
    if (tokens.isEmpty()) return FindFilterResult()
    val kept = ArrayList<FindRow>()
    val highlights = HashMap<Int, List<IntRange>>()
    val ids = ArrayList<Long>()
    var entry: TocTextMatch? = null
    var best: Long? = null
    var bestWords = -1
    for (row in rows) {
        val match = entry
        val id = row.matchId
        when {
            row is FindRow.Entry ->
                entry =
                    matchTocText(row.title, tokens)?.also {
                        highlights[kept.size] = it.ranges
                        kept += row
                    }
            // A row under an entry that matches
            match != null && id != null -> {
                if (match.exactWords > bestWords) {
                    best = id
                    bestWords = match.exactWords
                }
                kept += row
                ids += id
            }
        }
    }
    return FindFilterResult(kept, highlights, ids, best)
}

/**
 * The pane's [regular] list under the results' bar of [search] ([PaneSearchLayout], as the contents' search): while a
 * title is typed, the [rows] [filterFindRows] keeps, each drawn by [row], the active one marked and kept in view; one
 * chosen, by a click or Enter, is gone to ([onSelect], by its [FindRow.matchId]) and the bar hidden, as the contents'
 * search opens its entry and hides.
 */
@Composable
internal fun FindRowsSearchLayout(
    search: FindResultsSearch,
    rows: List<FindRow>,
    onSelect: (Long) -> Unit,
    regular: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (row: FindRow, selected: Boolean, onClick: () -> Unit) -> Unit,
) {
    val query = search.query
    val state = remember(rows, query) { query?.let { PaneSearchState(it, filterFindRows(rows, it), it) } }
    val select = { id: Long ->
        onSelect(id)
        search.close()
    }
    PaneSearchLayout(
        search = state,
        focus = search.focus,
        placeholder = stringResource(Res.string.toc_search),
        noResults = stringResource(Res.string.toc_search_no_results),
        onQueryChange = search::queryChanged,
        onSelect = select,
        onClose = search::close,
        regular = regular,
        modifier = modifier,
    ) { result, activeId ->
        val listState = rememberLazyListState()
        LaunchedEffect(result, activeId) {
            val index = result.rows.indexOfFirst { it.matchId == activeId }
            if (index >= 0) listState.revealItem(index)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(result.rows.size) { index ->
                when (val found = result.rows[index]) {
                    is FindRow.Entry -> EntryTitle(found.title, result.highlights[index])
                    else -> row(found, found.matchId == activeId) { found.matchId?.let(select) }
                }
            }
        }
    }
}

/**
 * An entry of the contents over its lines, as the pane lists them; the words of it the results' search matched
 * ([highlights]) marked as the contents' search marks a title's.
 */
@Composable
internal fun EntryTitle(
    title: String,
    highlights: List<IntRange>? = null,
) {
    val text =
        remember(title, highlights) {
            buildAnnotatedString {
                append(title)
                highlights?.forEach { range ->
                    val end = (range.last + 1).coerceAtMost(length)
                    if (end > range.first) addStyle(SpanStyle(background = SearchHighlightColor), range.first, end)
                }
            }
        }
    Text(
        text,
        Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    )
}
