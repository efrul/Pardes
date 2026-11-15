package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookFilterResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BookSuggestion
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.search.MIN_BOOK_QUERY_LENGTH
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.my_pdf_books

// Pardes (requests 51 and 54 of docs/DESKTOP_STAGE2.md): the user's PDF books found by the book tree's search, "שיוכלו
// למצוא את הספרים שלי בחיפוש ספרים", and suggested by the search bar ("כתיבה בשורת החיפוש שם ספר pdf").

/** [search] with the user's PDF books its query names added to its result ([withMyPdfBooks]). */
@Composable
fun myPdfBooksIn(search: PaneSearchState<BookFilterResult>?): PaneSearchState<BookFilterResult>? {
    val library = myPdfLibrary()
    val title = stringResource(Res.string.my_pdf_books)
    return remember(search, library, title) {
        search?.let { s -> s.copy(result = s.result?.let { withMyPdfBooks(it, s.resultQuery.orEmpty(), library, title) }) }
    }
}

/** The user's PDF books that the text [typed] in the search bar names, as it suggests books ([pdfBookSuggestions]). */
@Composable
internal fun myPdfBookSuggestions(typed: CharSequence): List<BookSuggestion> {
    val library = myPdfLibrary()
    val title = stringResource(Res.string.my_pdf_books)
    val query = typed.toString()
    return remember(query, library, title) { pdfBookSuggestions(query, library, title) }
}

// The user's books as the book tree has them, read again when a folder is added, removed or read again
@Composable
private fun myPdfLibrary(): PdfLibrary {
    val store = LocalAppGraph.current.pdfBookStore
    val revision by store.revision.collectAsState()
    val library by produceState(PdfLibrary(), revision) { value = store.library() }
    return library
}

/**
 * The books of [library] whose titles hold every word of [query] ([pdfBooksNamed]), [MOST_SUGGESTED] at most, each with
 * its path as the search bar shows a book's: "הספרים שלי" ([title]), its folders, its title. Nothing for a query
 * shorter than the bar's books want ([MIN_BOOK_QUERY_LENGTH]).
 */
internal fun pdfBookSuggestions(
    query: String,
    library: PdfLibrary,
    title: String,
): List<BookSuggestion> {
    if (query.trim().length < MIN_BOOK_QUERY_LENGTH) return emptyList()
    val byId = library.categoriesById()
    return pdfBooksNamed(query, library).take(MOST_SUGGESTED).map { book ->
        val folders = generateSequence(byId[book.categoryId]) { it.parentId?.let(byId::get) }.map { it.title }.toList().asReversed()
        BookSuggestion(book, listOf(title) + folders + book.title)
    }
}

/**
 * [result] with the books of [library] whose titles hold every word of [query] (their points, quotes and case left
 * aside) shown before the library's: under "הספרים שלי" ([title]) and their folders' categories, all open, and first
 * among the matches the arrows and Enter go through. The first of them is the best match, unless the library's is
 * named exactly as typed: a long list of the library's books, found by acronyms and beginnings of words, hid them.
 */
internal fun withMyPdfBooks(
    result: BookFilterResult,
    query: String,
    library: PdfLibrary,
    title: String,
): BookFilterResult {
    val found = pdfBooksNamed(query, library)
    if (found.isEmpty()) return result
    // Each book's categories up to "הספרים שלי", in the order the tree has them
    val byId = library.categoriesById()
    val shown = HashSet<Long>()
    for (book in found) {
        var id: Long? = book.categoryId
        while (id != null && id != MY_BOOKS_CATEGORY && shown.add(id)) id = byId[id]?.parentId
    }
    val children = result.children.toMutableMap()
    for ((parent, kids) in library.children) {
        kids.filter { it.id in shown }.takeIf { it.isNotEmpty() }?.let { children[parent] = it }
    }
    val libraryBest = result.books.firstOrNull { it.id == result.bestMatchId }
    return result.copy(
        roots = listOf(myBooksCategory(title)) + result.roots,
        children = children,
        books = result.books + found,
        categoryIds = result.categoryIds + MY_BOOKS_CATEGORY + shown,
        matchIds = found.map { it.id } + result.matchIds,
        bestMatchId = libraryBest?.takeIf { plain(it.title) == plain(query) }?.id ?: found.first().id,
    )
}

/** The books of [library] whose titles hold every word of [query], their points, quotes and case left aside. */
internal fun pdfBooksNamed(
    query: String,
    library: PdfLibrary,
): List<Book> {
    val words = plain(query).split(' ').filter { it.isNotEmpty() }
    return if (words.isEmpty()) emptyList() else library.books.filter { book -> plain(book.title).let { t -> words.all(t::contains) } }
}

// The folders' categories by their id, "הספרים שלי" itself not among them
private fun PdfLibrary.categoriesById() =
    children.values
        .flatten()
        .associateBy { it.id }

// A title or a query as compared: no points or cantillation, no quotes, one case, single spaces
private fun plain(text: String): String =
    text
        .filterNot { it in '֑'..'ׇ' || it in QUOTES }
        .lowercase()
        .replace(SPACES, " ")
        .trim()

private const val QUOTES = "\"'״׳`"

// As many of the user's books as the search bar suggests after the library's
private const val MOST_SUGGESTED = 8
private val SPACES = Regex("\\s+")
