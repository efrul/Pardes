package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.db.Pdf_book
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Pardes (request 28 of docs/DESKTOP_STAGE2.md): the user's PDF books.

/** Where a PDF book is read: the page at the view's top, its pixels above the view, and the zoom (1: the view's width). */
@Immutable
data class PdfPlace(
    val page: Int = 0,
    val offset: Int = 0,
    val zoom: Float = 1f,
)

/** A PDF book of the user's, its [file], [title], and where it was last read. */
@Immutable
data class PdfBook(
    val id: Long,
    val file: File,
    val title: String,
    val place: PdfPlace = PdfPlace(),
)

/** The book of the library's kind this PDF book is, under "הספרים שלי": in the tree, and in the reading screen. */
fun PdfBook.asTreeBook(): Book = Book(id = myBookId(id), categoryId = MY_BOOKS_CATEGORY, sourceId = 0, title = title)

/**
 * The user's PDF books, in the user DB (table pdf_book), so the backup carries the list (not the files): the book
 * tree's "הספרים שלי" ([MyPdfBooks]) and the reading screen ([PdfBookReader]). [revision] goes up when a book is added or
 * removed, not when one is read further.
 */
@Inject
@SingleIn(AppScope::class)
class PdfBookStore(
    database: UserSettingsDb,
) {
    private val queries = database.pdfBooksQueries

    // Where a book was read is kept off the tab's scope: a tab closed right after a scroll still keeps it
    @StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Every book, by title. */
    suspend fun books(): List<PdfBook> = withContext(Dispatchers.IO) { queries.selectAll().executeAsList().map { it.toBook() } }

    /** The book [id], or null once it was removed. */
    suspend fun book(id: Long): PdfBook? = withContext(Dispatchers.IO) { queries.selectById(id).executeAsOneOrNull()?.toBook() }

    /**
     * Adds [source], kept in [folder] as [PdfBookFiles.keep] says (null: in place), and gives its book: the one already
     * listed when its file is.
     */
    suspend fun add(
        source: File,
        folder: File?,
    ): PdfBook =
        withContext(Dispatchers.IO) {
            val file = PdfBookFiles.keep(source, folder)
            queries.insert(path = file.path, title = PdfBookFiles.titleOf(source), addedAt = System.currentTimeMillis())
            _revision.update { it + 1 }
            queries.selectByPath(file.path).executeAsOne().toBook()
        }

    /** Keeps [place] as where the book [id] was last read, in the background ([setPlace]). */
    fun keepPlace(
        id: Long,
        place: PdfPlace,
    ) {
        scope.launch { setPlace(id, place) }
    }

    /** Sets [place] as where the book [id] was last read. */
    suspend fun setPlace(
        id: Long,
        place: PdfPlace,
    ) {
        withContext(Dispatchers.IO) { queries.updatePlace(place.page.toLong(), place.offset.toLong(), place.zoom.toDouble(), id) }
    }

    /** Takes the book [id] out of the list; its file stays where it is. */
    suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) { queries.delete(id) }
        _revision.update { it + 1 }
    }

    /** The book of the library's kind that the PDF book of the book id [bookId] is, for the reading screen; else null. */
    suspend fun asBook(bookId: Long): Book? = pdfIdOf(bookId)?.let { book(it) }?.asTreeBook()

    private fun Pdf_book.toBook(): PdfBook = PdfBook(id, File(path), title, PdfPlace(page.toInt(), pageOffset.toInt(), zoom.toFloat()))
}
