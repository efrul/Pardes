package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookFilterResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.my_pdf_books

// Pardes (request 51 of docs/DESKTOP_STAGE2.md): the user's PDF books found by the book tree's search, "שיוכלו למצוא את
// הספרים שלי בחיפוש ספרים".

/** [search] with the user's PDF books its query names added to its result ([withMyPdfBooks]). */
@Composable
fun myPdfBooksIn(search: PaneSearchState<BookFilterResult>?): PaneSearchState<BookFilterResult>? {
    val store = LocalAppGraph.current.pdfBookStore
    val revision by store.revision.collectAsState()
    val library by produceState(PdfLibrary(), revision) { value = store.library() }
    val title = stringResource(Res.string.my_pdf_books)
    return remember(search, library, title) {
        search?.let { s -> s.copy(result = s.result?.let { withMyPdfBooks(it, s.resultQuery.orEmpty(), library, title) }) }
    }
}

/**
 * [result] with the books of [library] whose titles hold every word of [query] (their points, quotes and case left
 * aside) added after the library's: under "הספרים שלי" ([title]) and their folders' categories, all open, and among the
 * matches the arrows and Enter go through, the first of them the best match when the library has none.
 */
internal fun withMyPdfBooks(
    result: BookFilterResult,
    query: String,
    library: PdfLibrary,
    title: String,
): BookFilterResult {
    val words = plain(query).split(' ').filter { it.isNotEmpty() }
    val found = if (words.isEmpty()) emptyList() else library.books.filter { book -> plain(book.title).let { t -> words.all(t::contains) } }
    if (found.isEmpty()) return result
    // Each book's categories up to "הספרים שלי", in the order the tree has them
    val byId =
        library.children.values
            .flatten()
            .associateBy { it.id }
    val shown = HashSet<Long>()
    for (book in found) {
        var id: Long? = book.categoryId
        while (id != null && id != MY_BOOKS_CATEGORY && shown.add(id)) id = byId[id]?.parentId
    }
    val children = result.children.toMutableMap()
    for ((parent, kids) in library.children) {
        kids.filter { it.id in shown }.takeIf { it.isNotEmpty() }?.let { children[parent] = it }
    }
    return result.copy(
        roots = result.roots + myBooksCategory(title),
        children = children,
        books = result.books + found,
        categoryIds = result.categoryIds + MY_BOOKS_CATEGORY + shown,
        matchIds = result.matchIds + found.map { it.id },
        bestMatchId = result.bestMatchId ?: found.first().id,
    )
}

// A title or a query as compared: no points or cantillation, no quotes, one case, single spaces
private fun plain(text: String): String =
    text
        .filterNot { it in '֑'..'ׇ' || it in QUOTES }
        .lowercase()
        .replace(SPACES, " ")
        .trim()

private const val QUOTES = "\"'״׳`"
private val SPACES = Regex("\\s+")
