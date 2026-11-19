package io.github.kdroidfilter.seforimapp.features.bookcontent.find

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.plainTextFromHtml
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesNormalized
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.listMove
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.pageRows
import io.github.kdroidfilter.seforimapp.features.search.ResultRow
import io.github.kdroidfilter.seforimapp.features.search.SnippetText
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SnippetSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.find_results_by_meaning
import seforimapp.seforimapp.generated.resources.find_results_count
import seforimapp.seforimapp.generated.resources.find_results_none

// Pardes (request 55 of docs/DESKTOP_STAGE3_PLAN.md): every match of a book's find in a pane beside the text, under the
// entries of its table of contents, each line with a piece of it around its match.

/**
 * The find of the tab [tabId] in its book [bookId] ([BookFindHub.found]) as a list: under each entry of the book's
 * table of contents its lines, each with its text around its first match and how many it has, the current match's
 * line marked and kept in view; a line clicked is gone to in the text ([BookFindHub.goTo]). With the keyboard's focus,
 * as the search's results: up and down go from line to line, the text with them, Home, End and a page up and down as
 * the book tree's, and Enter or Escape give the focus back to the text ([onLeave]). A word on the find when the tab
 * has none.
 */
@Composable
fun FindResultsPane(
    tabId: String,
    bookId: Long,
    modifier: Modifier = Modifier,
    onLeave: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val found by graph.bookFindHub.found.collectAsState()
    val listing = found[tabId]?.takeIf { it.bookId == bookId }
    val info = JewelTheme.globalColors.text.info
    if (listing == null) {
        Box(modifier.padding(16.dp)) { Text(stringResource(Res.string.find_results_none), color = info, fontSize = 13.sp) }
        return
    }
    val lines = remember(listing.matches) { listing.matches.foundLines() }
    val titles by produceState(emptyMap<Long, String>(), bookId) {
        value = runSuspendCatching { entryTitles(graph.repository, bookId) }.getOrDefault(emptyMap())
    }
    val rows = remember(lines, titles) { findRows(lines, titles) }
    val current = listing.current?.lineIndex
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    // The line the keys asked for, ahead of the text's answer: keys pressed fast go on from it
    var asked by remember(listing.matches) { mutableStateOf<Int?>(null) }
    LaunchedEffect(current) { if (current == asked) asked = null }

    fun goTo(line: FoundLine) {
        asked = line.lineIndex
        graph.bookFindHub.goTo(tabId, line.lineIndex)
    }

    // Up and down from line to line, as the search's results; Enter and Escape back to the text, as the book tree's
    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || lines.isEmpty()) return false
        val at = (asked ?: current)?.let { lineIndex -> lines.indexOfFirst { it.lineIndex == lineIndex } } ?: -1
        val next =
            when (event.key) {
                Key.DirectionDown -> (at + 1).coerceAtMost(lines.lastIndex)
                Key.DirectionUp -> (at - 1).coerceAtLeast(0)
                Key.Enter, Key.NumPadEnter, Key.Escape -> {
                    onLeave()
                    return true
                }
                else -> listMove(lines.size, at.coerceAtLeast(0), event.key, listState.pageRows()) ?: return false
            }
        goTo(lines[next])
        return true
    }
    // The current match's line kept in view, as the text goes from match to match
    LaunchedEffect(current, rows) {
        val index = rows.indexOfFirst { it is FindRow.Found && it.line.lineIndex == current }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
    }
    Column(modifier) {
        val count =
            if (listing.smart) {
                stringResource(Res.string.find_results_by_meaning, lines.size)
            } else {
                stringResource(Res.string.find_results_count, listing.matches.occurrences, lines.size)
            }
        Text(count, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = info, fontSize = 12.sp)
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent(::onKey)
                .focusable(),
            state = listState,
        ) {
            items(rows.size) { index ->
                when (val row = rows[index]) {
                    is FindRow.Entry ->
                        Text(
                            row.title,
                            Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    is FindRow.Found ->
                        FoundLineRow(row.line, listing, selected = row.line.lineIndex == (asked ?: current)) {
                            goTo(row.line)
                            // The keys go on from the line clicked
                            runCatching { focus.requestFocus() }
                        }
                }
            }
        }
    }
}

// A line found: its text around its first match, the match marked (the find's words; by meaning, the passage closest in
// meaning, bold, as the search's results show it), and its count
@Composable
private fun FoundLineRow(
    line: FoundLine,
    listing: FindListing,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val bookFontCode by graph.appSettings.bookFontCodeFlow.collectAsState()
    // Read once it is on screen, off the UI thread
    val snippet by produceState("", line.lineId, listing.query, listing.smart) {
        value =
            withContext(Dispatchers.Default) {
                runSuspendCatching {
                    val content =
                        graph.repository
                            .getLine(line.lineId)
                            ?.content
                            .orEmpty()
                    if (listing.smart) {
                        meaningSnippet(graph.searchEngine, content, listing.query)
                    } else {
                        findSnippet(plainTextFromHtml(content), listing.query)
                    }
                }.getOrNull().orEmpty()
            }
    }
    ResultRow(selected = selected, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { SnippetText(snippet, listing.query.takeUnless { listing.smart }, bookFontCode) }
            if (line.count > 1) Text("(${line.count})", color = JewelTheme.globalColors.text.info, fontSize = 12.sp)
        }
    }
}

/** A row of the results pane: an entry of the table of contents, or a line found under it. */
internal sealed interface FindRow {
    data class Entry(
        val title: String,
    ) : FindRow

    data class Found(
        val line: FoundLine,
    ) : FindRow
}

/** The lines found, each under the entry of the table of contents it belongs to ([titles], by line), once a run. */
internal fun findRows(
    lines: List<FoundLine>,
    titles: Map<Long, String>,
): List<FindRow> =
    buildList {
        var last: String? = null
        for (line in lines) {
            val title = titles[line.lineId]
            if (title != null && title != last) add(FindRow.Entry(title))
            last = title ?: last
            add(FindRow.Found(line))
        }
    }

/**
 * A piece of [text] around the first match of [query] ([findAllMatchesNormalized]: nikud and final letters aside),
 * cut at words, with an ellipsis where it is cut; the text's start when nothing matches (a line found by meaning).
 */
internal fun findSnippet(
    text: String,
    query: String,
): String {
    val match = findAllMatchesNormalized(text, normalizeQueryForHebrew(query)).firstOrNull() ?: return cut(text, 0, SNIPPET_SPAN)
    return cut(text, match.first - SNIPPET_SPAN / 2, match.last + SNIPPET_SPAN / 2)
}

// [text] from [start] to [end], at the words around them, an ellipsis where it is cut
private fun cut(
    text: String,
    start: Int,
    end: Int,
): String {
    val from = if (start <= 0) 0 else text.lastIndexOf(' ', start).takeIf { it >= 0 }?.plus(1) ?: 0
    val to = if (end >= text.length) text.length else text.indexOf(' ', end).takeIf { it >= 0 } ?: text.length
    val piece = text.substring(from, to.coerceAtLeast(from)).trim()
    return (if (from > 0) "…" else "") + piece + (if (to < text.length) "…" else "")
}

/**
 * A line's [html] as the search's results show a line found by meaning (the user, 2026-11-19: "אין הדגשות במצב
 * סמנטי"): a piece around the passage of it closest in meaning to [query] ([SearchEngine.highlights], the rule every
 * view highlights a found line with), bold ([SearchEngine.rangeSnippet]); [findSnippet] of its text when the search
 * highlights nothing in it.
 */
internal suspend fun meaningSnippet(
    engine: SearchEngine,
    html: String,
    query: String,
): String {
    val text = SnippetSources.clean(html)
    val ranges =
        engine
            .highlights(listOf(text), query)
            .singleOrNull()
            ?.ranges
            .orEmpty()
    return if (ranges.isEmpty()) findSnippet(plainTextFromHtml(html), query) else engine.rangeSnippet(text, ranges)
}

/** The title of the table of contents' entry of each line of the book [bookId], by the line's id. */
internal suspend fun entryTitles(
    repository: SeforimRepository,
    bookId: Long,
): Map<Long, String> {
    val titles = repository.getBookToc(bookId).associate { it.id to it.text }
    return repository
        .getLineTocMappingsForBook(bookId)
        .mapNotNull { mapping ->
            titles[mapping.tocEntryId]?.let { mapping.lineId to it }
        }.toMap()
}

// The letters a line's piece holds around its match, half on each side
private const val SNIPPET_SPAN = 120
