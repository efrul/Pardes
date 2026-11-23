package io.github.kdroidfilter.seforimapp.features.bookcontent.find

import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.plainTextFromHtml
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesNormalized
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneSearchButton
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.TypeToSearchTarget
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.listMove
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.pageRows
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.revealItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.type
import io.github.kdroidfilter.seforimapp.features.pdf.PdfOutlineEntry
import io.github.kdroidfilter.seforimapp.features.pdf.PdfOutlines
import io.github.kdroidfilter.seforimapp.features.pdf.pdfIdOf
import io.github.kdroidfilter.seforimapp.features.search.ResultRow
import io.github.kdroidfilter.seforimapp.features.search.SnippetText
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
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
import seforimapp.seforimapp.generated.resources.find_results_pages_count
import seforimapp.seforimapp.generated.resources.find_results_pane
import seforimapp.seforimapp.generated.resources.pdf_book_toc_page
import seforimapp.seforimapp.generated.resources.pdf_find_no_text
import seforimapp.seforimapp.generated.resources.toc_search

// Pardes (request 55 of docs/DESKTOP_STAGE3_PLAN.md): every match of a book's find in a pane beside the text, under the
// entries of its table of contents, each line with a piece of it around its match.

/**
 * The find of the tab [tabId] in its book [bookId] ([BookFindHub.found]) as a list: under each entry of the book's
 * table of contents its lines, each with its text around its first match and how many it has, the current match's
 * line marked and kept in view; a line clicked is gone to in the text ([BookFindHub.goTo]). With the keyboard's focus,
 * as the search's results: up and down go from line to line, the text with them, Home, End and a page up and down as
 * the book tree's, and Enter or Escape give the focus back to the text ([onLeave]). A word on the find when the tab
 * has none. Pardes (request 62 of docs/DESKTOP_STAGE3_PLAN.md): under a header as the other panes' (PaneHeader, its
 * grip), which hides it ([onHide]) and searches it by its entries' titles as the contents' search does
 * ([FindRowsSearchLayout]), also when a letter is typed over it.
 */
@Composable
fun FindResultsPane(
    tabId: String,
    bookId: Long,
    modifier: Modifier = Modifier,
    onHide: () -> Unit = {},
    onLeave: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val found by graph.bookFindHub.found.collectAsState()
    val pdfFound by graph.bookFindHub.pdfFound.collectAsState()
    val listing = found[tabId]?.takeIf { it.bookId == bookId }
    // Pardes: a PDF book's find, listed as a book's (request 48 of stage 3)
    val pdf = pdfFound[tabId]?.takeIf { listing == null }
    val paneHoverSource = remember { MutableInteractionSource() }
    val search = rememberFindResultsSearch(tabId)
    val searchable = listing != null || (pdf != null && !(pdf.done && pdf.noText))
    if (searchable) {
        TypeToSearchTarget(paneHoverSource) { text -> search.focus.type(text, isOpen = search.query != null, open = search::toggle) }
    }
    Column(modifier.hoverable(paneHoverSource)) {
        PaneHeader(
            label = stringResource(Res.string.find_results_pane),
            interactionSource = paneHoverSource,
            onHide = onHide,
            actions = {
                if (searchable) {
                    PaneSearchButton(
                        isOpen = search.query != null,
                        focus = search.focus,
                        onClick = search::toggle,
                        contentDescription = stringResource(Res.string.toc_search),
                    )
                }
            },
        )
        val body = Modifier.weight(1f).padding(horizontal = 8.dp)
        when {
            listing != null -> BookFindResults(tabId, bookId, listing, search, body, onLeave)
            pdf != null -> PdfFindResults(tabId, bookId, pdf, search, body, onLeave)
            else ->
                Box(body.padding(8.dp)) {
                    Text(stringResource(Res.string.find_results_none), color = JewelTheme.globalColors.text.info, fontSize = 13.sp)
                }
        }
    }
}

// A book's find [listing], as FindResultsPane lists it
@Composable
private fun BookFindResults(
    tabId: String,
    bookId: Long,
    listing: FindListing,
    search: FindResultsSearch,
    modifier: Modifier = Modifier,
    onLeave: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val info = JewelTheme.globalColors.text.info
    val lines = remember(listing.matches) { listing.matches.foundLines() }
    // The book's entries, and those of the other books its matches hold lines of (the daf's commentaries)
    val titles by produceState(emptyMap<Long, String>(), bookId, listing.books) {
        value =
            runSuspendCatching {
                (listOf(bookId) + listing.books).fold(emptyMap<Long, String>()) { all, id -> all + entryTitles(graph.repository, id) }
            }.getOrDefault(emptyMap())
    }
    val rows = remember(lines, titles) { findRows(lines, titles) }
    val current = listing.current?.lineIndex
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    // The line the keys asked for, ahead of the text's answer: keys pressed fast go on from it
    var asked by remember(listing.matches) { mutableStateOf<Int?>(null) }
    LaunchedEffect(current) { if (current == asked) asked = null }
    // A line chosen in the results' search: the keys go on from it once the list is back
    var refocus by remember { mutableStateOf(false) }

    fun goTo(line: FoundLine) {
        asked = line.lineIndex
        graph.bookFindHub.goTo(tabId, line.lineIndex)
    }

    // Up and down from line to line, as the search's results; Enter and Escape back to the text, as the book tree's
    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || lines.isEmpty()) return false
        val at = (asked ?: current)?.let { lineIndex -> lines.indexOfFirst { it.lineIndex == lineIndex } } ?: -1
        if (event.key in LEAVE_KEYS) {
            onLeave()
            return true
        }
        goTo(lines[findListMove(event.key, lines.size, at, listState.pageRows()) ?: return false])
        return true
    }
    // The current match's line kept in view by the least scroll, as the text goes from match to match: a step down as
    // a step up, not a page's jump (request 77 of stage 3), and in view as the rows read their text (request 78)
    LaunchedEffect(current, rows) {
        val index = rows.indexOfFirst { it is FindRow.Found && it.line.lineIndex == current }
        if (index >= 0) listState.revealSettled(index)
    }
    FindRowsSearchLayout(
        search = search,
        rows = rows,
        onSelect = { id ->
            lines.firstOrNull { it.lineIndex.toLong() == id }?.let(::goTo)
            refocus = true
        },
        modifier = modifier,
        regular = {
            Column {
                val count =
                    if (listing.smart) {
                        stringResource(Res.string.find_results_by_meaning, lines.size)
                    } else {
                        stringResource(Res.string.find_results_count, listing.matches.occurrences, lines.size)
                    }
                Text(count, Modifier.padding(horizontal = 8.dp, vertical = 8.dp), color = info, fontSize = 12.sp)
                LaunchedEffect(refocus) {
                    if (refocus) runCatching { focus.requestFocus() }
                    refocus = false
                }
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
                            is FindRow.Entry -> EntryTitle(row.title)
                            is FindRow.Found ->
                                FoundLineRow(row.line, listing, selected = row.line.lineIndex == (asked ?: current)) {
                                    goTo(row.line)
                                    // The keys go on from the line clicked
                                    runCatching { focus.requestFocus() }
                                }
                            // A PDF book's, listed by PdfFindResults
                            is FindRow.InPage -> Unit
                        }
                    }
                }
            }
        },
    ) { row, selected, onClick -> if (row is FindRow.Found) FoundLineRow(row.line, listing, selected, onClick) }
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
    // Read once it is on screen, off the UI thread; with the title of its book when it is another's (a comment of Rashi
    // or Tosafot, found on the daf)
    val shown by produceState("" to null as String?, line.lineId, listing.query, listing.smart) {
        value =
            withContext(Dispatchers.Default) {
                runSuspendCatching {
                    val found = graph.repository.getLine(line.lineId)
                    val content = found?.content.orEmpty()
                    val snippet =
                        if (listing.smart) {
                            meaningSnippet(graph.searchEngine, content, listing.query)
                        } else {
                            findSnippet(plainTextFromHtml(content), listing.query)
                        }
                    snippet to found?.bookId?.takeIf { it != listing.bookId }?.let { graph.repository.getBook(it)?.title }
                }.getOrNull() ?: ("" to null)
            }
    }
    val (snippet, source) = shown
    ResultRow(selected = selected, onClick = onClick) {
        if (source != null) Text(source, color = JewelTheme.globalColors.text.info, fontSize = 11.sp)
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

    /** A match of a PDF book's find: its place in the find's matches, and its page and piece. */
    data class InPage(
        val index: Int,
        val found: PdfFound,
    ) : FindRow
}

/**
 * A row's match, by which the results' search goes to it ([FindRowsSearchLayout]): a line's index, or a PDF book's
 * match's place in its find; null for an entry.
 */
internal val FindRow.matchId: Long?
    get() =
        when (this) {
            is FindRow.Entry -> null
            is FindRow.Found -> line.lineIndex.toLong()
            is FindRow.InPage -> index.toLong()
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
    return withOwnNumber(text, pieceAround(text, match))
}

/**
 * Pardes (request 62 of docs/DESKTOP_STAGE3_PLAN.md): a [piece] of [text] cut after the line's own number, the "(יז)"
 * of a verse or a se'if, with it first, as the user asked each result to say its verse with the level above it
 * ("בראשית פרק כ,יז": the entry it comes under says the rest).
 */
internal fun withOwnNumber(
    text: String,
    piece: String,
): String {
    if (!piece.startsWith("…") && !piece.startsWith("...")) return piece
    val number = OWN_NUMBER.find(text)?.value?.trim() ?: return piece
    return "$number $piece"
}

// A line's own number at its start, as the books write it: a few Hebrew letters in parentheses, a geresh or gershayim
private val OWN_NUMBER = Regex("""^\s*\([\u05D0-\u05EA"'\u05F3\u05F4]{1,6}\)""")

/** A piece of [text] around [range], cut at words, with an ellipsis where it is cut: a found line's, or a PDF page's. */
internal fun pieceAround(
    text: String,
    range: IntRange,
): String = cut(text, range.first - SNIPPET_SPAN / 2, range.last + SNIPPET_SPAN / 2)

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
    return if (ranges.isEmpty()) findSnippet(plainTextFromHtml(html), query) else withOwnNumber(text, engine.rangeSnippet(text, ranges))
}

/**
 * The entry of the table of contents of each line of the book [bookId], as its place reads ([entryPaths]), by the
 * line's id.
 */
internal suspend fun entryTitles(
    repository: SeforimRepository,
    bookId: Long,
): Map<Long, String> {
    val paths = entryPaths(repository.getBookToc(bookId), repository.getBookCore(bookId)?.title)
    return repository
        .getLineTocMappingsForBook(bookId)
        .mapNotNull { mapping ->
            paths[mapping.tocEntryId]?.let { mapping.lineId to it }
        }.toMap()
}

/**
 * Pardes (request 62 of docs/DESKTOP_STAGE3_PLAN.md): each entry of the table of contents [entries] by its place, as
 * the search's results write a line's (GetBreadcrumbPiecesUseCase): the titles from the top entry down to it, joined by
 * commas, the top one left out when it is the book's title [bookTitle] over another (רש"י על בראשית › פרק כ › פסוק יז:
 * "פרק כ, פסוק יז"), as the user asked each result's title with the level above it ("בראשית פרק כ,יז").
 */
internal fun entryPaths(
    entries: List<TocEntry>,
    bookTitle: String?,
): Map<Long, String> {
    val byId = entries.associateBy { it.id }
    return entries.associate { entry ->
        val path = ArrayList<String>()
        var at: TocEntry? = entry
        // Bounded, as the search's breadcrumb is, against an entry that would be its own ancestor
        while (at != null && path.size < MAX_DEPTH) {
            path += at.text
            at = at.parentId?.let(byId::get)
        }
        path.reverse()
        if (path.size > 1 && path.first() == bookTitle) path.removeAt(0)
        entry.id to path.joinToString(", ")
    }
}

// The deepest entry a place is read to
private const val MAX_DEPTH = 64

// The letters a line's piece holds around its match, half on each side
private const val SNIPPET_SPAN = 120

// Pardes (request 48 of docs/DESKTOP_STAGE3_PLAN.md): a PDF book's find in the same pane, as the user showed it empty
// after the second testers' build.

/**
 * The find [pdf] of the tab [tabId] in its PDF book [bookId], listed as a book's: "12 תוצאות ב־5 עמודים", then each
 * match under the book's bookmark it comes after, or under its page when the book has none ([pdfFindRows]), with the
 * piece of the page around it, the find's words marked; the current match marked and kept in view, a match clicked gone
 * to on its page ([BookFindHub.goToPdfMatch]), and the same keys as a book's list. A scan with no text says so.
 */
@Composable
private fun PdfFindResults(
    tabId: String,
    bookId: Long,
    pdf: PdfFindListing,
    search: FindResultsSearch,
    modifier: Modifier = Modifier,
    onLeave: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val info = JewelTheme.globalColors.text.info
    if (pdf.done && pdf.noText) {
        Box(modifier.padding(8.dp)) { Text(stringResource(Res.string.pdf_find_no_text), color = info, fontSize = 13.sp) }
        return
    }
    val bookmarks by produceState(emptyList<PdfOutlineEntry>(), bookId) {
        value =
            runSuspendCatching {
                pdfIdOf(bookId)?.let { graph.pdfBookStore.book(it) }?.let { PdfOutlines.read(it.file) }
            }.getOrNull().orEmpty()
    }
    val label = stringResource(Res.string.pdf_book_toc_page)
    val rows = remember(pdf.matches, bookmarks, label) { pdfFindRows(pdf.matches, bookmarks) { label.format(it + 1) } }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    // The match the keys asked for, ahead of the reader's answer
    var asked by remember(pdf.query) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pdf.current) { if (pdf.current == asked) asked = null }
    val current = asked ?: pdf.current
    LaunchedEffect(current, rows) {
        val index = rows.indexOfFirst { it is FindRow.InPage && it.index == current }
        if (index >= 0) listState.revealItem(index)
    }

    fun goTo(index: Int) {
        asked = index
        graph.bookFindHub.goToPdfMatch(tabId, index)
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || pdf.matches.isEmpty()) return false
        if (event.key in LEAVE_KEYS) {
            onLeave()
            return true
        }
        goTo(findListMove(event.key, pdf.matches.size, current, listState.pageRows()) ?: return false)
        return true
    }
    val bookFontCode by graph.appSettings.bookFontCodeFlow.collectAsState()
    // A match chosen in the results' search: the keys go on from it once the list is back
    var refocus by remember { mutableStateOf(false) }
    FindRowsSearchLayout(
        search = search,
        rows = rows,
        onSelect = { id ->
            goTo(id.toInt())
            refocus = true
        },
        modifier = modifier,
        regular = {
            Column {
                val pages = remember(pdf.matches) { pdf.matches.distinctBy { it.page }.size }
                Text(
                    stringResource(Res.string.find_results_pages_count, pdf.matches.size, pages),
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    color = info,
                    fontSize = 12.sp,
                )
                LaunchedEffect(refocus) {
                    if (refocus) runCatching { focus.requestFocus() }
                    refocus = false
                }
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
                            is FindRow.Entry -> EntryTitle(row.title)
                            is FindRow.InPage ->
                                ResultRow(selected = row.index == current, onClick = {
                                    goTo(row.index)
                                    runCatching { focus.requestFocus() }
                                }) { SnippetText(row.found.piece, pdf.query, bookFontCode) }
                            // A book's, listed by BookFindResults
                            is FindRow.Found -> Unit
                        }
                    }
                }
            }
        },
    ) { row, selected, onClick ->
        if (row is FindRow.InPage) {
            ResultRow(
                selected = selected,
                onClick = onClick,
            ) { SnippetText(row.found.piece, pdf.query, bookFontCode) }
        }
    }
}

/**
 * A PDF book's [matches], each under the last of its [bookmarks] (read in order, at any depth) whose page is not after
 * the match's, once a run; under its page's title ([pageTitle], from its page from 0) before the first bookmark and in
 * a book with none.
 */
internal fun pdfFindRows(
    matches: List<PdfFound>,
    bookmarks: List<PdfOutlineEntry>,
    pageTitle: (Int) -> String,
): List<FindRow> {
    val marks = buildList { bookmarks.flattenInto(this) }.sortedBy { it.first }
    return buildList {
        var last: String? = null
        matches.forEachIndexed { index, found ->
            val title = marks.lastOrNull { it.first <= found.page }?.second ?: pageTitle(found.page)
            if (title != last) add(FindRow.Entry(title))
            last = title
            add(FindRow.InPage(index, found))
        }
    }
}

// The bookmarks with a page, depth first in reading order, as (page, title)
private fun List<PdfOutlineEntry>.flattenInto(out: MutableList<Pair<Int, String>>) {
    for (entry in this) {
        entry.page?.let { out += it to entry.title }
        entry.children.flattenInto(out)
    }
}

/** Where up, down, Home, End and a page up and down go in a list of [count] from [at] (-1 for none); null for another key. */
internal fun findListMove(
    key: Key,
    count: Int,
    at: Int,
    page: Int,
): Int? =
    when (key) {
        Key.DirectionDown -> (at + 1).coerceAtMost(count - 1)
        Key.DirectionUp -> (at - 1).coerceAtLeast(0)
        else -> listMove(count, at.coerceAtLeast(0), key, page)
    }

// The keys that give the focus back to the text, as the book tree's
private val LEAVE_KEYS = setOf(Key.Enter, Key.NumPadEnter, Key.Escape)
