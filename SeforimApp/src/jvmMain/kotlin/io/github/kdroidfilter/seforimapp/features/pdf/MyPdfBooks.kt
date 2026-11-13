package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.my_pdf_books
import seforimapp.seforimapp.generated.resources.my_pdf_books_add
import java.io.File

// Pardes (request 28 of docs/DESKTOP_STAGE2.md): the user's PDF books in the book tree, "הספרים שלי", after the
// library's categories (the user's decision of 2026-11-12).

// The ids of "הספרים שלי" in the book tree: negative, as the library's are positive and its search groups' larger
internal const val MY_BOOKS_CATEGORY = -28L
internal const val MY_BOOKS_ADD = -29L
private const val FIRST_MY_BOOK = -1_000L

/** The tree's id of the PDF book [pdfId] (the user DB's ids start at 1). */
internal fun myBookId(pdfId: Long): Long = FIRST_MY_BOOK - pdfId

/** The PDF book a tree's [bookId] is, or null for another row. */
internal fun pdfIdOf(bookId: Long): Long? = (FIRST_MY_BOOK - bookId).takeIf { bookId < FIRST_MY_BOOK }

/** The category "הספרים שלי", [title], last among the roots. */
internal fun myBooksCategory(title: String): Category = Category(id = MY_BOOKS_CATEGORY, title = title, order = Int.MAX_VALUE)

/** The category's rows: the user's [books] (by title, as the store gives them), then the row that adds one, [add]. */
internal fun myBooksRows(
    books: List<PdfBook>,
    add: String,
): List<Book> =
    books.map { Book(id = myBookId(it.id), categoryId = MY_BOOKS_CATEGORY, sourceId = 0, title = it.title) } +
        Book(id = MY_BOOKS_ADD, categoryId = MY_BOOKS_CATEGORY, sourceId = 0, title = add)

/**
 * The book tree's [content], given [navigation] with "הספרים שלי" after the library's categories (once they are there),
 * and a click for its books: a PDF book opens in its tab, "הוספת PDF…" picks files and adds them (asking first, at the
 * first one, where books added are kept), and any other book goes to [onBookClick].
 */
@Composable
fun MyPdfBooks(
    navigation: NavigationState,
    onBookClick: (Book) -> Unit,
    content: @Composable (navigation: NavigationState, onBookClick: (Book) -> Unit) -> Unit,
) {
    val appGraph = LocalAppGraph.current
    val store = appGraph.pdfBookStore
    val appSettings = appGraph.appSettings
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val scope = rememberCoroutineScope()
    val revision by store.revision.collectAsState()
    val books by produceState(emptyList<PdfBook>(), revision) { value = store.books() }
    val title = stringResource(Res.string.my_pdf_books)
    val add = stringResource(Res.string.my_pdf_books_add)
    // Kept while their inputs are: the tree groups its books again whenever it gets new ones
    val roots = navigation.rootCategories
    val shownRoots = remember(roots, title) { if (roots.isEmpty()) roots else roots + myBooksCategory(title) }
    val shownBooks = remember(navigation.booksInCategory, books, add) { navigation.booksInCategory + myBooksRows(books, add) }
    // The files picked at the first book added, while the user is asked where books added are kept
    var asking by remember { mutableStateOf<List<File>?>(null) }

    fun addAll(
        files: List<File>,
        answer: PdfKeepAnswer,
    ) {
        scope.launch {
            val folder = appSettings.pdfFolderFor(answer)
            val added = files.mapNotNull { file -> runSuspendCatching { store.add(file, folder) }.getOrNull() }
            added.lastOrNull()?.let { openPdfBookTab(tabsViewModel, it.id) }
        }
    }

    val click: (Book) -> Unit = { book ->
        val pdfId = pdfIdOf(book.id)
        when {
            pdfId != null -> openPdfBookTab(tabsViewModel, pdfId)
            book.id == MY_BOOKS_ADD ->
                scope.launch {
                    // Off the GTK event-loop thread, as the data screen's pickers (see pickDatabaseParts)
                    val picked =
                        withContext(Dispatchers.IO) {
                            FileKit.openFilePicker(type = FileKitType.File(extensions = setOf("pdf")), mode = FileKitMode.Multiple())
                        }?.map { File(it.path) }
                    if (picked.isNullOrEmpty()) return@launch
                    val answer = appSettings.pdfKeep()
                    if (answer == null) asking = picked else addAll(picked, answer)
                }
            else -> onBookClick(book)
        }
    }
    content(navigation.copy(rootCategories = shownRoots, booksInCategory = shownBooks), click)
    asking?.let { files ->
        PdfKeepDialog(
            onAnswer = { answer ->
                appSettings.setPdfKeep(answer)
                asking = null
                addAll(files, answer)
            },
            onCancel = { asking = null },
        )
    }
}
