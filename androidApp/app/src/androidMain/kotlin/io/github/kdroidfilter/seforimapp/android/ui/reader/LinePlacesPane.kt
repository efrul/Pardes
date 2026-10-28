package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.ui.components.EmptyHint
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.android.ui.search.FilterTabs
import io.github.kdroidfilter.seforimapp.android.ui.search.LocalResultTextScale
import io.github.kdroidfilter.seforimapp.android.ui.search.ResultLink
import io.github.kdroidfilter.seforimapp.android.ui.search.ResultRow
import io.github.kdroidfilter.seforimapp.android.ui.search.ResultTitle
import io.github.kdroidfilter.seforimapp.android.ui.search.SnippetText
import io.github.kdroidfilter.seforimapp.android.ui.search.readingSecondary
import io.github.kdroidfilter.seforimapp.android.ui.search.resultSp
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The [kind] places of the selected line (the places that refer to it, or its sources), as the search's results: a
 * book at a time, its first passage cut around the quote, its other places behind "עוד N מקומות", tabs by category
 * when the books are many. A tap opens the book there, in a new tab. Port of desktop 0.6.11 LinePlacesView
 * (views/LinePlacesView.kt): its places are a heading's whole section, or a multi-selection, as the commentaries'.
 *
 * Beside the text it is one column; under the text (the tablet's row, or a phone held sideways) as many columns as its
 * width holds.
 */
@Composable
fun LinePlacesPane(
    kind: LinePlaces,
    vm: ReaderViewModel,
    state: ReaderUiState,
    textSize: Float,
    wide: Boolean,
    actions: PaneActions,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val lineIds = remember(state.selectedLineIds, state.primaryLineId) { vm.placesLineIds(state) }
    val references by produceState<LineReferences?>(null, kind, lineIds) {
        value = null
        value = if (lineIds.isEmpty()) LineReferences.Empty else vm.linePlaces(kind, lineIds)
    }
    Column(modifier) {
        header()
        val shown = references
        when {
            lineIds.isEmpty() -> EmptyHint(kind.selectLine, Modifier.fillMaxSize())
            shown == null -> LoadingBox(Modifier.fillMaxSize())
            shown.books.isEmpty() -> EmptyHint(kind.empty, Modifier.fillMaxSize())
            // The results follow the reading text's zoom, as the other line panes' texts do
            else -> CompositionLocalProvider(LocalResultTextScale provides textSize / AppPrefs.DEFAULT_TEXT_SIZE) {
                ReferencesList(shown, kind.tabsMinBooks, state.diacritics, wide, onOpen = { place -> actions.openBookAt(place.line.bookId, place.line.id) })
            }
        }
    }
}

@Composable
private fun ReferencesList(references: LineReferences, tabsMinBooks: Int, diacritics: DiacriticsMode, wide: Boolean, onOpen: (ReferencePlace) -> Unit) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    // The search's own matching finds each passage's quote; null without the full-text index (no quote then)
    val searchEngine by produceState<SearchEngine?>(null) { value = runCatching { app.search.engine() }.getOrNull() }
    var tab by remember(references) { mutableStateOf<Long?>(null) }
    var expanded by remember(references) { mutableStateOf(emptySet<String>()) }
    val books = remember(references, tab) { references.books.filter { tab == null || it.tabId == tab } }
    val listState = rememberLazyListState()
    LaunchedEffect(tab) { listState.scrollToItem(0) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        val compact = maxWidth < NARROW_WIDTH
        // Under the text the pane is wide: its books side by side, each column as wide as in a side pane. Zoomed in, a
        // column needs more room: fewer columns rather than cramped ones
        val columns = (maxWidth / (COLUMN_MIN_WIDTH * LocalResultTextScale.current)).toInt().coerceIn(1, MAX_COLUMNS)
        val rows = remember(books, columns) { books.chunked(columns) }
        // A few books, all side by side: the room is theirs, their passages read longer
        val passageLines = if (references.books.size <= columns) FEW_BOOKS_LINES else SNIPPET_LINES
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "header") {
                ReferencesHeader(references, tabsMinBooks, tab, compact, wide) { tab = it }
            }
            items(rows, key = { row -> row.first().key }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP), verticalAlignment = Alignment.Top) {
                    row.forEach { book ->
                        ReferenceBookView(
                            book = book,
                            expanded = book.key in expanded,
                            diacritics = diacritics,
                            searchEngine = searchEngine,
                            passageLines = passageLines,
                            wide = wide,
                            onExpand = { expanded = if (it) expanded + book.key else expanded - book.key },
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // A last row short of books keeps the columns' width
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** How many books and places refer to the line, and the tabs that filter them by category. */
@Composable
private fun ReferencesHeader(references: LineReferences, tabsMinBooks: Int, tab: Long?, compact: Boolean, wide: Boolean, onTab: (Long?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(REFERENCES_SUMMARY.fill(references.books.size, references.placeCount), fontSize = 12.sp, color = readingSecondary())
        if (references.books.size >= tabsMinBooks) {
            val tabs = remember(references) { references.kindTabs.map { it.tabId to it.label!! } + references.categoryTabs }
            if (tabs.isNotEmpty()) FilterTabs(tabs, tab, compact, wide, onTab)
        }
    }
}

/** A book's places: its first one, the others once [expanded]; each a result row that opens the book there. */
@Composable
private fun ReferenceBookView(
    book: ReferenceBook,
    expanded: Boolean,
    diacritics: DiacriticsMode,
    searchEngine: SearchEngine?,
    passageLines: Int,
    wide: Boolean,
    onExpand: (Boolean) -> Unit,
    onOpen: (ReferencePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Zayit.colors.accent
    val places = if (expanded) book.places else book.places.take(1)
    // The kind of a ruling or a parallel passage beside its place: it is no plain reference
    val kind = book.kind.label
    Column(modifier) {
        places.forEachIndexed { index, place ->
            ResultRow(selected = false, onClick = { onOpen(place) }) {
                if (index == 0) {
                    ResultTitle(book.title, listOfNotNull(kind, place.place).joinToString(" · ").ifEmpty { null }, wide)
                } else if (place.place != null) {
                    Text(place.place, fontSize = resultSp(if (wide) 12f else 13f), color = readingSecondary(), maxLines = 1)
                }
                // The passage's start until its quote is found, off the UI thread, for the places on screen only
                val snippet by produceState(diacritics.apply(place.start), place, diacritics, searchEngine) {
                    value = withContext(Dispatchers.Default) { diacritics.apply(place.snippet(searchEngine)) }
                }
                SnippetText(snippet, findQuery = "", wide = wide, maxLines = passageLines)
                val more = book.places.size - 1
                val linkSize = resultSp(if (wide) 11f else 13f)
                val linkHeight = if (wide) 0.dp else 36.dp
                when {
                    index == 0 && !expanded && more > 0 -> {
                        val label = if (more == 1) REFERENCES_ONE_MORE_FROM_BOOK.fill(book.title) else REFERENCES_MORE_FROM_BOOK.fill(more, book.title)
                        ResultLink(label, accent, linkSize, minHeight = linkHeight) { onExpand(true) }
                    }
                    expanded && index == places.lastIndex && more > 0 ->
                        ResultLink(SEARCH_COLLAPSE, accent, linkSize, minHeight = linkHeight) { onExpand(false) }
                }
            }
        }
    }
}

/** The lines a places pane lists the places of (desktop placesLines): every selected line, else the primary line. */
internal fun placesLineIds(selectedLineIds: Set<Long>, primaryLineId: Long?): List<Long> =
    if (selectedLineIds.size > 1) selectedLineIds.sorted() else listOfNotNull(primaryLineId)

private val LinePlaces.selectLine: String
    get() = if (this == LinePlaces.Sources) SELECT_LINE_FOR_SOURCES else SELECT_LINE_FOR_REFERENCES

private val LinePlaces.empty: String
    get() = if (this == LinePlaces.Sources) NO_SOURCES_FOR_LINE else NO_REFERENCES_FOR_LINE

/**
 * Under this many books the filter tabs are hidden: a handful of references need no filter; the sources keep them,
 * which tell the kind of each (חומש, תלמוד, הלכה) at a glance.
 */
private val LinePlaces.tabsMinBooks: Int
    get() = if (this == LinePlaces.Sources) 0 else REFERENCES_TABS_MIN_BOOKS

/** A book's key in the list: one book may refer to the line as a ruling and as a plain reference. */
private val ReferenceBook.key: String get() = "${kind.name}-$bookId"

private val ReferenceKind.label: String?
    get() =
        when (this) {
            ReferenceKind.EinMishpat -> REFERENCES_EIN_MISHPAT
            ReferenceKind.MesoratHashas -> REFERENCES_MESORAT_HASHAS
            ReferenceKind.Reference -> null
        }

private fun String.fill(vararg args: Any): String = String.format(Locale.ROOT, this, *args)

// Desktop 0.6.11 strings
internal const val REFERENCES = "מראי מקומות"
internal const val SHOW_REFERENCES_TOOLTIP = "הצג את הספרים שמזכירים שורה זו"
internal const val NO_REFERENCES_FOR_LINE = "אין מראי מקומות לשורה זו"
internal const val NO_SOURCES_FOR_LINE = "אין מקורות זמינים לשורה זו"
private const val SELECT_LINE_FOR_REFERENCES = "בחר שורה כדי לראות אילו ספרים מזכירים אותה"
private const val SELECT_LINE_FOR_SOURCES = "בחר שורה כדי לצפות במקורות"
private const val REFERENCES_SUMMARY = "%1\$d ספרים · %2\$d מקומות"
private const val REFERENCES_MORE_FROM_BOOK = "עוד %1\$d מקומות מ%2\$s"
private const val REFERENCES_ONE_MORE_FROM_BOOK = "עוד מקום אחד מ%1\$s"
private const val REFERENCES_EIN_MISHPAT = "עין משפט"
private const val REFERENCES_MESORAT_HASHAS = "מסורת הש״ס"
private const val SEARCH_COLLAPSE = "הסתר"

// A column of places as wide as the pane beside the text holds it; never more than four side by side
private val COLUMN_MIN_WIDTH = 300.dp
private val COLUMN_GAP = 12.dp
private const val MAX_COLUMNS = 4

// Below this width the filter tabs scroll in one line, as the search page's
private val NARROW_WIDTH = 360.dp

// The passage's lines: two as the search's, more when a few books have the pane to themselves
private const val SNIPPET_LINES = 2
private const val FEW_BOOKS_LINES = 6

private const val REFERENCES_TABS_MIN_BOOKS = 6
