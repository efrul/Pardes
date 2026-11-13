package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.my_pdf_books
import seforimapp.seforimapp.generated.resources.my_pdf_books_add
import java.io.File

// Pardes (requests 28 and 44 of docs/DESKTOP_STAGE2.md): the user's PDF books in the book tree, "הספרים שלי", after the
// library's categories (the user's decision of 2026-11-12): their folders, each subfolder a category, as Zayita and
// Otzaria show theirs, in place of the books added one by one (the user's decision of 2026-11-13).

// The ids of "הספרים שלי" in the book tree: the category and the row that adds a folder negative, as no book of the
// library has them, and the folders' categories further down, by their paths; a PDF book's positive and far above the
// library's (its tens of thousands), as the reading screen opens a book of a positive id only, and below the largest
// Int, as some code takes ids as Ints
internal const val MY_BOOKS_CATEGORY = -28L
internal const val MY_BOOKS_ADD = -29L
private const val PDF_BOOK_BASE = 2_100_000_000L
private const val FOLDER_CATEGORY_BASE = 1_000_000L

/** The book id of the PDF book [pdfId] (the user DB's ids start at 1). */
fun myBookId(pdfId: Long): Long = PDF_BOOK_BASE + pdfId

/** The PDF book a book id is, or null for another book. */
fun pdfIdOf(bookId: Long): Long? = (bookId - PDF_BOOK_BASE).takeIf { bookId > PDF_BOOK_BASE }

/** The id of the book tree's category for the folder [dir] of the user's, by its path: the same at every start. */
internal fun folderCategoryId(dir: File): Long =
    -(
        FOLDER_CATEGORY_BASE + (
            dir.absoluteFile
                .normalize()
                .path
                .hashCode()
                .toLong() and Int.MAX_VALUE.toLong()
        )
    )

/** The category "הספרים שלי", [title], last among the roots. */
internal fun myBooksCategory(title: String): Category = Category(id = MY_BOOKS_CATEGORY, title = title, order = Int.MAX_VALUE)

/** The category's own rows: the row that adds a folder, [add], first, then the books of [library]. */
internal fun myBooksRows(
    library: PdfLibrary,
    add: String,
): List<Book> = listOf(Book(id = MY_BOOKS_ADD, categoryId = MY_BOOKS_CATEGORY, sourceId = 0, title = add)) + library.books

/**
 * The book tree's [content], given [navigation] with "הספרים שלי" after the library's categories (once they are there),
 * the user's folders under it and their subfolders under them ([PdfBookStore.library]), and a click for its books:
 * "הוספת תיקייה…" picks a folder and adds it, read where it is; any other book, a PDF book too, goes to [onBookClick],
 * opened as the library's are.
 */
@Composable
fun MyPdfBooks(
    navigation: NavigationState,
    onBookClick: (Book) -> Unit,
    content: @Composable (navigation: NavigationState, onBookClick: (Book) -> Unit) -> Unit,
) {
    val store = LocalAppGraph.current.pdfBookStore
    val scope = rememberCoroutineScope()
    val revision by store.revision.collectAsState()
    val library by produceState(PdfLibrary(), revision) { value = store.library() }
    val title = stringResource(Res.string.my_pdf_books)
    val add = stringResource(Res.string.my_pdf_books_add)
    // Kept while their inputs are: the tree groups its books again whenever it gets new ones
    val roots = navigation.rootCategories
    val shownRoots = remember(roots, title) { if (roots.isEmpty()) roots else roots + myBooksCategory(title) }
    val shownChildren = remember(navigation.categoryChildren, library) { navigation.categoryChildren + library.children }
    val shownBooks = remember(navigation.booksInCategory, library, add) { navigation.booksInCategory + myBooksRows(library, add) }
    val click: (Book) -> Unit = { book ->
        if (book.id == MY_BOOKS_ADD) {
            scope.launch { pickPdfFolder()?.let { store.addFolder(it) } }
        } else {
            onBookClick(book)
        }
    }
    content(navigation.copy(rootCategories = shownRoots, categoryChildren = shownChildren, booksInCategory = shownBooks), click)
}

/** A folder picked with the system's picker, off the GTK event-loop thread as the data screen's pickers; null for none. */
internal suspend fun pickPdfFolder(): File? = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }?.let { File(it.path) }
