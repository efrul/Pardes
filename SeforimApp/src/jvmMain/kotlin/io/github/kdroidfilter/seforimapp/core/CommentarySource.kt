package io.github.kdroidfilter.seforimapp.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimapp.core.selection.CommentaryLineRef
import io.github.kdroidfilter.seforimapp.core.selection.SelectionContext
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LocatedRange
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

// Pardes (request 40 of docs/DESKTOP_STAGE2.md): "העתק עם מקור" of a selection made in a commentary cites the
// commentary itself, its own book and lines ("רשב״א על בבא בתרא ב."), not the book open in the tab, among whose lines
// the selection is not found, which left the book's title alone. The commentaries and targumim panes publish their
// column on a press (SelectionContext.activeCommentaryColumn): the selection is found among them. The daf view's
// selection, Rashi's and Tosafot's as well as the Gemara's, is not the text of the library's lines: it cites the lines
// it locates itself (LocatedSelection).

/**
 * The first and last commentaries of [column] that [selectedText] covers; null when none. A commentary is covered when
 * one text holds the other, or the selection starts with the commentary's end or ends with its start: a selection
 * begun or ended inside a commentary, wherever in it (Zayit's match for a book's lines takes only the last or first
 * eight letters of such a line).
 */
fun commentaryRangeOf(
    selectedText: String,
    column: List<CommentaryLineRef>,
): Pair<CommentaryLineRef, CommentaryLineRef>? {
    val selection = plainForMatch(selectedText)
    if (column.isEmpty() || selection.isEmpty()) return null
    val covered = column.filter { overlaps(plainForMatch(Jsoup.parse(it.content).text()), selection) }
    return covered.firstOrNull()?.let { it to covered.last() }
}

private val SPACES = Regex("\\s+")

// The least a selection must share with a commentary it begins or ends in, as Zayit's match of a book's lines
private const val MIN_OVERLAP = 8

private fun plainForMatch(text: String): String = HebrewTextUtils.removeAllDiacritics(text).replace(SPACES, " ").trim()

/**
 * Whether [line] and [selection] share text: one holds the other, or one's end is the other's start, over
 * [MIN_OVERLAP] letters at least. Found from the occurrences of the selection's first or last letters in the line, in
 * linear time: a selection of a whole book is checked on a click.
 */
private fun overlaps(
    line: String,
    selection: String,
): Boolean {
    if (line.isEmpty()) return false
    if (selection.contains(line) || line.contains(selection)) return true
    if (line.length < MIN_OVERLAP || selection.length < MIN_OVERLAP) return false
    // Begun inside the line: the line's end, from an occurrence of the selection's first letters, is its start
    val head = selection.take(MIN_OVERLAP)
    var at = line.indexOf(head)
    while (at >= 0) {
        if (selection.regionMatches(0, line, at, line.length - at)) return true
        at = line.indexOf(head, at + 1)
    }
    // Ended inside the line: the line's start, up to an occurrence of the selection's last letters, is its end
    val tail = selection.takeLast(MIN_OVERLAP)
    at = line.indexOf(tail)
    while (at >= 0) {
        val end = at + MIN_OVERLAP
        if (end <= selection.length && selection.regionMatches(selection.length - end, line, 0, end)) return true
        at = line.indexOf(tail, at + 1)
    }
    return false
}

/**
 * [selectedText] with the source of the commentaries of [column] it covers, read from the library: their book's
 * reference and their lines', as Zayit's copy with source cites a book's lines (formatHebrewSourceReference). Across the
 * commentaries of two books, the first one's. Null when the selection is not among them, or their book is not found.
 */
suspend fun commentaryCopyWithSource(
    selectedText: String,
    column: List<CommentaryLineRef>,
    repository: SeforimRepository,
    catalogCache: CatalogCache,
): String? {
    val (first, last) = commentaryRangeOf(selectedText, column) ?: return null
    val firstLine = repository.getLine(first.lineId) ?: return null
    val lastLine = last.takeIf { it.bookId == first.bookId }?.let { repository.getLine(it.lineId) } ?: firstLine
    return copyWithSourceOf(selectedText, firstLine to lastLine, repository, catalogCache)
}

/**
 * The first and last lines, in their book's order, of the library lines [ranges] locate in the first one's book (the
 * daf view's selection: the Gemara's, Rashi's or Tosafot's); null when none is found.
 */
suspend fun locatedLinesOf(
    ranges: List<LocatedRange>,
    repository: SeforimRepository,
): Pair<Line, Line>? {
    val bookId = ranges.firstOrNull()?.bookId ?: return null
    val lines =
        ranges
            .filter { it.bookId == bookId }
            .map { it.lineId }
            .distinct()
            .mapNotNull { repository.getLine(it) }
    val first = lines.minByOrNull { it.lineIndex } ?: return null
    return first to lines.maxBy { it.lineIndex }
}

/**
 * [selectedText] with the source of the lines [range], first to last, in their own book: its reference and theirs, as
 * Zayit's copy with source cites a book's lines (formatHebrewSourceReference). Null when their book is not found.
 */
suspend fun copyWithSourceOf(
    selectedText: String,
    range: Pair<Line, Line>,
    repository: SeforimRepository,
    catalogCache: CatalogCache,
): String? {
    val book = repository.getBookCore(range.first.bookId) ?: return null
    val rootTitle = catalogCache.getRootForBook(book)?.title
    return "$selectedText\n\n(${formatHebrewSourceReference(book, rootTitle, range.first, range.second)})"
}

/** A selection's copy with its own source, read from the library; null when its book or lines are not found. */
typealias OwnSourceCopy = suspend (repository: SeforimRepository, catalogCache: CatalogCache) -> String?

/**
 * The copy with source of the book text's menu for [selectedText] when it is not the text of the book's lines: the
 * lines the content located itself ([ranges], the daf view's), or the commentaries or targumim of [column] it is found
 * in. Null to leave it to Zayit's (buildCopyWithSourcePayload), which cites the book's lines.
 */
fun menuOwnSourceOf(
    selectedText: String,
    ranges: List<LocatedRange>,
    column: List<CommentaryLineRef>,
): OwnSourceCopy? =
    when {
        ranges.isNotEmpty() -> { repository, catalog ->
            locatedLinesOf(ranges, repository)?.let { copyWithSourceOf(selectedText, it, repository, catalog) }
        }
        commentaryRangeOf(selectedText, column) != null -> { repository, catalog ->
            commentaryCopyWithSource(selectedText, column, repository, catalog)
        }
        else -> null
    }

/**
 * The window's Ctrl/Cmd+Alt+C, for the selection of its tab whose id it is given: the copy with its own source when
 * Zayit's shortcut (selectionCopyShortcut) would cite the book open in the tab instead ([ownSourceOf]), read off the
 * key event; true when it took the key.
 */
@Composable
fun rememberCommentaryCopyShortcut(): (keyEvent: KeyEvent, tabId: String) -> Boolean {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val copyToClipboard = rememberCopyToClipboard()
    return remember(graph, scope, copyToClipboard) {
        { keyEvent, tabId ->
            val source = if (keyEvent.isCopyWithSource()) ownSourceOf(graph.selectionContext, tabId) else null
            if (source == null) {
                false
            } else {
                scope.launch {
                    runSuspendCatching { source(graph.repository, graph.catalogCache) }.getOrNull()?.let(copyToClipboard)
                }
                true
            }
        }
    }
}

private fun KeyEvent.isCopyWithSource(): Boolean =
    type == KeyEventType.KeyDown && key == Key.C && (isCtrlPressed || isMetaPressed) && isAltPressed && !isShiftPressed

/**
 * How the selection of the tab [tabId] is copied with its own source: its lines when known in another book than the
 * tab's (the daf view's Rashi and Tosafot), or the commentaries or targumim of the column published last that it is
 * found in rather than among the book's lines. Null to leave it to Zayit's shortcut, which cites the tab's book.
 */
internal fun ownSourceOf(
    context: SelectionContext,
    tabId: String,
): OwnSourceCopy? {
    val text = context.selectedTextIn(tabId).takeIf { it.isNotBlank() } ?: return null
    val range = context.selectedLineRange.value
    if (range != null) {
        val tabBook = context.activeBook.value?.takeIf { it.tabId == tabId }
        if (range.first.bookId == tabBook?.book?.id) return null
        return { repository, catalog -> copyWithSourceOf(text, range, repository, catalog) }
    }
    val column = context.activeCommentaryColumn.value
    val inBook = resolveLineRangeFromSelection(text, context.visibleLines.value.lines) != null
    if (inBook || commentaryRangeOf(text, column) == null) return null
    return { repository, catalog -> commentaryCopyWithSource(text, column, repository, catalog) }
}
