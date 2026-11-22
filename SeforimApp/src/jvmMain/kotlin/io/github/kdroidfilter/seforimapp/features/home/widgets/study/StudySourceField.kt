package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BookSuggestion
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.CatBookRow
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.SuggestionKind
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.SuggestionRow
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.baseBooksDetail
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.catBookRows
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.dedupAdjacent
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.scope_base_books_label

// Pardes (request 64 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md): the study plan editor's
// field (StudyPlanEditor.kt) that finds a book or a category as the search's bar does.

/**
 * Pardes (request 64 of stage 3): the books and categories [text] names, as the search's bar lists them: the books its
 * lookup suggests (acronyms too: שוע), each with its categories' titles, and the categories it names, in the bar's
 * order ([catBookRows]), a few of each; not the commentaries on a book by their time, which it searches as categories.
 */
private suspend fun sourcesNamed(
    graph: AppGraph,
    text: String,
): List<CatBookRow> =
    withContext(Dispatchers.Default) {
        val repository = graph.repository
        val books =
            graph.lookupSearch.suggestBooks(text, limit = BOOK_SUGGESTIONS).map { hit ->
                BookSuggestion(hit.toBook(), repository.pathOf(hit.categoryId) + hit.title, hit.exactAcronym)
            }
        val categories = graph.libraryCategories.named(text, limit = CATEGORY_SUGGESTIONS).filter { repository.getCategory(it.id) != null }
        catBookRows(text, jumps = emptyList(), textSearch = false, authors = emptyList(), books = books, categories = categories)
            .filter { it is CatBookRow.Book || it is CatBookRow.Category }
    }

private const val BOOK_SUGGESTIONS = 6
private const val CATEGORY_SUGGESTIONS = 4

/**
 * Pardes (request 64 of stage 3, the user's word: "זה יהיה אוטומטי כמו בחיפוש הרגיל, הוא יזהה ספר או קטגוריה"): the book
 * or category [chosen] (its title), or a field that finds either as the search's bar does ([sourcesNamed]), each in a
 * row of the bar's ([SuggestionRow]: its icon, its path, and a category in its base books alone so marked); up and down
 * mark one, Enter or a click picks it, Escape lets them go and puts the title picked back.
 */
@Composable
internal fun SourceField(
    chosen: String?,
    onPickBook: (Book) -> Unit,
    onPickCategory: (Category, Boolean) -> Unit,
) {
    val graph = LocalAppGraph.current
    val repository = graph.repository
    val scope = rememberCoroutineScope()
    val field = remember { TextFieldState(chosen.orEmpty()) }
    // The title picked last, in the field: nothing to look for while it is there
    var picked by remember { mutableStateOf(chosen.orEmpty()) }
    var rows by remember { mutableStateOf<List<CatBookRow>>(emptyList()) }
    var marked by remember(rows) { mutableIntStateOf(0) }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString().trim() }.collectLatest { text ->
            if (text.length < 2 || text == picked) {
                rows = emptyList()
                return@collectLatest
            }
            delay(SEARCH_DELAY_MS)
            rows = runSuspendCatching { sourcesNamed(graph, text) }.getOrNull().orEmpty()
        }
    }

    val baseBooksLabel = stringResource(Res.string.scope_base_books_label)

    fun pick(row: CatBookRow) {
        val title = row.pickedTitle(baseBooksLabel) ?: return
        rows = emptyList()
        picked = title
        field.edit { replace(0, length, title) }
        scope.launch { pickSource(row, repository, onPickBook, onPickCategory) }
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || rows.isEmpty()) return false
        when (event.key) {
            // Round from the last row to the first and back, as every popup of the app
            Key.DirectionDown -> marked = (marked + 1).mod(rows.size)
            Key.DirectionUp -> marked = (marked - 1).mod(rows.size)
            Key.Enter, Key.NumPadEnter -> pick(rows[marked])
            // The text typed goes, the title picked comes back
            Key.Escape -> {
                rows = emptyList()
                if (picked.isNotEmpty()) field.edit { replace(0, length, picked) }
            }
            else -> return false
        }
        return true
    }
    Column(Modifier.fillMaxWidth()) {
        TextField(
            field,
            Modifier.fillMaxWidth().testTag("study-source").onPreviewKeyEvent(::onKey),
            placeholder = { Text("שם של ספר או של קטגוריה") },
        )
        SourceRows(rows, marked, onPick = ::pick)
    }
}

// The books and categories found, [marked] the one the arrows mark, each in a row of the search's bar: its icon, its
// path, and a category in its base books alone so marked
@Composable
private fun SourceRows(
    rows: List<CatBookRow>,
    marked: Int,
    onPick: (CatBookRow) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            when (row) {
                is CatBookRow.Book ->
                    SuggestionRow(
                        parts = dedupAdjacent(row.suggestion.path),
                        onClick = { onPick(row) },
                        kind = SuggestionKind.BOOK,
                        highlighted = index == marked,
                    )
                is CatBookRow.Category ->
                    SuggestionRow(
                        parts = row.hit.path,
                        onClick = { onPick(row) },
                        kind = SuggestionKind.CATEGORY,
                        highlighted = index == marked,
                        detail = baseBooksDetail(row.hit),
                    )
                else -> Unit
            }
        }
    }
}

// The title a row picked puts in the field: a book's, or a category's, in its base books alone as the bar's chip names
// it ([baseBooksLabel]); null for a row that is neither
private fun CatBookRow.pickedTitle(baseBooksLabel: String): String? =
    when (this) {
        is CatBookRow.Book -> suggestion.book.title
        is CatBookRow.Category -> if (hit.baseBooksOnly) baseBooksLabel.format(hit.title) else hit.title
        else -> null
    }

// The book or the category a row picked, read from the library, given to [onPickBook] or [onPickCategory]
private suspend fun pickSource(
    row: CatBookRow,
    repository: SeforimRepository,
    onPickBook: (Book) -> Unit,
    onPickCategory: (Category, Boolean) -> Unit,
) {
    when (row) {
        is CatBookRow.Book ->
            onPickBook(
                runSuspendCatching { repository.getBookCore(row.suggestion.book.id) }.getOrNull() ?: row.suggestion.book,
            )
        is CatBookRow.Category ->
            runSuspendCatching { repository.getCategory(row.hit.id) }.getOrNull()?.let { onPickCategory(it, row.hit.baseBooksOnly) }
        else -> Unit
    }
}

private const val SEARCH_DELAY_MS = 150L
