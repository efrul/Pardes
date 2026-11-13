package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.db.Pdf_book
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// Pardes (requests 28, 44 and 45 of docs/DESKTOP_STAGE2.md): the user's PDF books, in folders of theirs read where they
// are, as Zayita and Otzaria read theirs (the user's decision of 2026-11-13).

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

/** A folder of the user's PDF books, [dir], read where it is. */
@Immutable
data class PdfFolder(
    val id: Long,
    val dir: File,
)

/** A folder of the user's as the settings show it: its [books] (in it and below it), and whether its folder was [found]. */
@Immutable
data class PdfFolderShown(
    val folder: PdfFolder,
    val books: Int,
    val found: Boolean,
)

/**
 * The user's PDF books as the book tree shows them under "הספרים שלי" ([MY_BOOKS_CATEGORY]): the categories by their
 * parent ([children]: the user's folders under "הספרים שלי", each folder's subfolders under it), the [books] (each in
 * its folder's category; one added alone by build 45 in "הספרים שלי" itself), and the [folders] as the settings show
 * them.
 */
@Immutable
data class PdfLibrary(
    val children: Map<Long, List<Category>> = emptyMap(),
    val books: List<Book> = emptyList(),
    val folders: List<PdfFolderShown> = emptyList(),
)

/** The pages of a PDF book shown in a tab: how many ([count]), and the first read at the top ([page], from 0). */
@Immutable
data class PdfPagesShown(
    val count: Int,
    val page: Int,
)

/** The book of the library's kind this PDF book is, in the category [categoryId] of the book tree, and in the reading screen. */
fun PdfBook.asTreeBook(categoryId: Long = MY_BOOKS_CATEGORY): Book =
    Book(id = myBookId(id), categoryId = categoryId, sourceId = 0, title = title)

/**
 * The user's PDF books, in the user DB, so the backup carries the lists (not the files): the folders they added
 * (table pdf_folder), and each book found in them (table pdf_book, by its file's path), with where it was last read.
 * The book tree's "הספרים שלי" ([MyPdfBooks]) shows them ([library]), the reading screen reads them
 * ([PdfBookReader]), and the settings list the folders ([PdfBooksCard]). [revision] goes up when a folder is added or
 * removed, a book removed or the folders read again ([rescan]), not when a book is read further.
 */
@Inject
@SingleIn(AppScope::class)
class PdfBookStore(
    database: UserSettingsDb,
) {
    private val queries = database.pdfBooksQueries
    private val folderQueries = database.pdfFoldersQueries

    // Where a book was read is kept off the tab's scope: a tab closed right after a scroll still keeps it
    @StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    // The folders as last read, and the revision they were read at: every tab's tree asks for them
    private val reading = Mutex()
    private var read: Pair<Long, PdfLibrary>? = null

    // The books shown by the tabs' readers, for the tabs' contents panes, and the pages those panes ask them to go to
    private val _shown = MutableStateFlow(emptyMap<String, PdfPagesShown>())
    private val _pageRequests = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = PAGE_REQUESTS)

    /** The PDF books shown, by their tabs: their pages, which the contents pane lists ([PdfBookToc]). */
    val shown: StateFlow<Map<String, PdfPagesShown>> = _shown.asStateFlow()

    /** The pages the contents panes ask the tabs' readers to go to: the tab, and the page (from 0). */
    val pageRequests: SharedFlow<Pair<String, Int>> = _pageRequests.asSharedFlow()

    /** The reader of the tab [tabId] shows a book's [pages], or none any more (null). */
    fun showPages(
        tabId: String,
        pages: PdfPagesShown?,
    ) {
        _shown.update { if (pages == null) it - tabId else it + (tabId to pages) }
    }

    /** Asks the reader of the tab [tabId] to go to [page] (from 0). */
    fun goToPage(
        tabId: String,
        page: Int,
    ) {
        _pageRequests.tryEmit(tabId to page)
    }

    /** The book [id], or null once it was removed. */
    suspend fun book(id: Long): PdfBook? = withContext(Dispatchers.IO) { queries.selectById(id).executeAsOneOrNull()?.toBook() }

    /** The user's folders, by path. */
    suspend fun folders(): List<PdfFolder> =
        withContext(Dispatchers.IO) { folderQueries.selectAll().executeAsList().map { PdfFolder(it.id, File(it.path)) } }

    /**
     * Adds the folder [dir], read where it is: nothing when one of the user's folders holds it already, and the user's
     * folders it holds become its subfolders.
     */
    suspend fun addFolder(dir: File) {
        val folder = dir.absoluteFile.normalize()
        val added =
            withContext(Dispatchers.IO) {
                val folders = folders()
                if (folders.any { it.dir == folder || PdfBookFiles.isIn(folder, it.dir) }) return@withContext false
                folderQueries.transaction {
                    folders.filter { PdfBookFiles.isIn(it.dir, folder) }.forEach { folderQueries.delete(it.id) }
                    folderQueries.insert(path = folder.path, addedAt = System.currentTimeMillis())
                }
                true
            }
        if (added) _revision.update { it + 1 }
    }

    /** Takes the folder [id] out of the list, and its books with it; the files stay where they are. */
    suspend fun removeFolder(id: Long) {
        withContext(Dispatchers.IO) {
            val folders = folders()
            val folder = folders.firstOrNull { it.id == id } ?: return@withContext
            val others = folders - folder
            queries.transaction {
                queries
                    .selectAll()
                    .executeAsList()
                    .filter { row ->
                        File(row.path).let {
                            PdfBookFiles.isIn(it, folder.dir) &&
                                others.none { o -> PdfBookFiles.isIn(it, o.dir) }
                        }
                    }.forEach { queries.delete(it.id) }
                folderQueries.delete(id)
            }
        }
        _revision.update { it + 1 }
    }

    /** Reads the folders again, for books added to them or taken out since. */
    fun rescan() {
        _revision.update { it + 1 }
    }

    /** The books as the book tree shows them, the folders read once a revision. */
    suspend fun library(): PdfLibrary =
        reading.withLock {
            val at = _revision.value
            read?.takeIf { it.first == at }?.second ?: withContext(Dispatchers.IO) { scan() }.also { read = at to it }
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

    /** Takes the book [id], added alone by build 45, out of the list; its file stays where it is. */
    suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) { queries.delete(id) }
        _revision.update { it + 1 }
    }

    /** Whether [book] was added alone (by build 45), in none of the user's folders: it can be taken out by itself. */
    suspend fun isAlone(book: PdfBook): Boolean = folders().none { PdfBookFiles.isIn(book.file, it.dir) }

    /** The book of the library's kind that the PDF book of the book id [bookId] is, for the reading screen; else null. */
    suspend fun asBook(bookId: Long): Book? {
        val book = pdfIdOf(bookId)?.let { book(it) } ?: return null
        val inFolder = folders().any { PdfBookFiles.isIn(book.file, it.dir) }
        return book.asTreeBook(if (inFolder) folderCategoryId(book.file.parentFile) else MY_BOOKS_CATEGORY)
    }

    // The folders read: each PDF file a book (a row added for a file found the first time), each folder that holds some
    // a category, and the books added alone in "הספרים שלי" itself
    private fun scan(): PdfLibrary {
        val folders = folderQueries.selectAll().executeAsList().map { PdfFolder(it.id, File(it.path)) }
        val rows = queries.selectAll().executeAsList().associateByTo(HashMap()) { it.path }
        val children = HashMap<Long, MutableList<Category>>()
        val books = ArrayList<Book>()
        val shown = ArrayList<PdfFolderShown>()

        fun add(
            tree: PdfFolderTree,
            parent: Long,
            level: Int,
        ): Int {
            val category =
                Category(id = folderCategoryId(tree.dir), parentId = parent, title = tree.dir.name.ifEmpty { tree.dir.path }, level = level)
            children.getOrPut(parent) { ArrayList() } += category
            for (file in tree.books) {
                val row =
                    rows[file.path] ?: run {
                        queries.insert(path = file.path, title = PdfBookFiles.titleOf(file), addedAt = System.currentTimeMillis())
                        queries.selectByPath(file.path).executeAsOne().also { rows[file.path] = it }
                    }
                books += row.toBook().asTreeBook(category.id)
            }
            return tree.books.size + tree.folders.sumOf { add(it, category.id, level + 1) }
        }
        queries.transaction {
            for (folder in folders) {
                val tree = folder.dir.takeIf { it.isDirectory }?.let { PdfBookFiles.treeOf(it) }
                shown +=
                    PdfFolderShown(folder, books = tree?.let { add(it, MY_BOOKS_CATEGORY, level = 1) } ?: 0, found = folder.dir.isDirectory)
            }
        }
        val alone =
            rows.values
                .filter { row -> folders.none { PdfBookFiles.isIn(File(row.path), it.dir) } }
                .sortedWith(compareBy({ it.title }, { it.id }))
                .map { it.toBook().asTreeBook() }
        children[MY_BOOKS_CATEGORY]?.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        return PdfLibrary(children, alone + books, shown)
    }

    private fun Pdf_book.toBook(): PdfBook = PdfBook(id, File(path), title, PdfPlace(page.toInt(), pageOffset.toInt(), zoom.toFloat()))

    private companion object {
        // A click on a page waits for no one: a few kept while a reader catches up
        const val PAGE_REQUESTS = 8
    }
}
