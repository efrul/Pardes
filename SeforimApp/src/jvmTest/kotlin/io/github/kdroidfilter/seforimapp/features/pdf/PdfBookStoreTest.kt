package io.github.kdroidfilter.seforimapp.features.pdf

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The user's PDF books, in folders of theirs read where they are, kept in the user DB, and the book tree they make. */
class PdfBookStoreTest {
    private val root = createTempDirectory("pdf-store").toFile()
    private val database = UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))
    private val store = PdfBookStore(database)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun pdf(path: String): File = File(root, path).apply { parentFile.mkdirs() }.apply { writeText(path) }

    private fun dir(path: String): File = File(root, path).absoluteFile

    @Test
    fun `a folder's books are read where they are, each subfolder a category under the user's books`() =
        runBlocking {
            val alef = pdf("ספרייה/א.pdf")
            val bet = pdf("ספרייה/הלכה/ב.pdf")
            pdf("ספרייה/הלכה/הערות.txt")
            val revision = store.revision.value

            store.addFolder(dir("ספרייה"))
            val library = store.library()

            assertTrue(store.revision.value > revision, "the tree reads the folders again")
            val folder = library.children.getValue(MY_BOOKS_CATEGORY).single()
            assertEquals("ספרייה", folder.title)
            assertEquals(folderCategoryId(dir("ספרייה")), folder.id)
            val halacha = library.children.getValue(folder.id).single()
            assertEquals("הלכה", halacha.title)
            assertEquals(folder.id, halacha.parentId)
            assertEquals(listOf("א" to folder.id, "ב" to halacha.id), library.books.map { it.title to it.categoryId })
            assertEquals(listOf(alef.absoluteFile, bet.absoluteFile), library.books.map { store.book(pdfIdOf(it.id)!!)?.file })
            assertEquals(listOf(2 to true), library.folders.map { it.books to it.found })
        }

    @Test
    fun `a book keeps its id when the folders are read again, and a file added is found then`() =
        runBlocking {
            pdf("ספרייה/א.pdf")
            store.addFolder(dir("ספרייה"))
            val before = store.library().books.single()

            pdf("ספרייה/ב.pdf")
            assertEquals(listOf("א"), store.library().books.map { it.title }, "read once a revision")
            store.rescan()
            val after = store.library().books

            assertEquals(listOf("א", "ב"), after.map { it.title })
            assertEquals(before.id, after.first().id)
        }

    @Test
    fun `a folder in one of the user's is not added again, and one that holds theirs takes their place`() =
        runBlocking {
            store.addFolder(dir("a/b"))
            store.addFolder(dir("a/b/c"))
            assertEquals(listOf(dir("a/b")), store.folders().map { it.dir })

            store.addFolder(dir("a"))
            assertEquals(listOf(dir("a")), store.folders().map { it.dir })
        }

    @Test
    fun `a folder taken out takes its books with it, and their files stay`() =
        runBlocking {
            val file = pdf("ספרייה/א.pdf")
            store.addFolder(dir("ספרייה"))
            val book = store.library().books.single()

            store.removeFolder(store.folders().single().id)

            assertEquals(PdfLibrary(), store.library())
            assertNull(store.book(pdfIdOf(book.id)!!))
            assertTrue(file.isFile)
        }

    @Test
    fun `a folder not found has no books, and says so`() =
        runBlocking {
            store.addFolder(dir("גדולה"))

            assertEquals(listOf(0 to false), store.library().folders.map { it.books to it.found })
        }

    @Test
    fun `a book added alone by build 45 is in the user's books themselves, and can be taken out`() =
        runBlocking {
            val file = pdf("לבד/ספר.pdf")
            database.pdfBooksQueries.insert(path = file.absolutePath, title = "ספר", addedAt = 0)
            store.addFolder(dir("ספרייה"))

            val book = store.library().books.single()
            val pdfBook = assertNotNull(store.book(pdfIdOf(book.id)!!))

            assertEquals(MY_BOOKS_CATEGORY, book.categoryId)
            assertTrue(store.isAlone(pdfBook))
            assertEquals(MY_BOOKS_CATEGORY, store.asBook(book.id)?.categoryId)
            store.remove(pdfBook.id)
            assertEquals(emptyList(), store.library().books)
            assertTrue(file.isFile)
        }

    @Test
    fun `a book of a folder opens in its folder's category, and is not taken out alone`() =
        runBlocking {
            pdf("ספרייה/הלכה/ב.pdf")
            store.addFolder(dir("ספרייה"))
            val book = store.library().books.single()

            assertEquals(folderCategoryId(dir("ספרייה/הלכה")), store.asBook(book.id)?.categoryId)
            assertFalse(store.isAlone(store.book(pdfIdOf(book.id)!!)!!))
        }

    @Test
    fun `where a book was read is kept`() =
        runBlocking {
            pdf("ספרייה/א.pdf")
            store.addFolder(dir("ספרייה"))
            val id =
                pdfIdOf(
                    store
                        .library()
                        .books
                        .single()
                        .id,
                )!!
            val place = PdfPlace(page = 41, offset = 120, zoom = 1.5f)

            store.setPlace(id, place)

            assertEquals(place, store.book(id)?.place)
        }

    @Test
    fun `the tree's rows are the row that adds a folder, then the books by their own ids`() =
        runBlocking {
            pdf("ספרייה/א.pdf")
            store.addFolder(dir("ספרייה"))

            val rows = myBooksRows(store.library(), add = "הוספת תיקייה…")

            assertEquals(listOf("הוספת תיקייה…", "א"), rows.map { it.title })
            assertEquals(MY_BOOKS_ADD, rows.first().id)
            assertTrue(rows.first().id < 0)
            // A book's id positive, as the reading screen opens those only, and far above the library's
            assertTrue(rows.drop(1).all { it.id > 1_000_000_000L && it.id < Int.MAX_VALUE && pdfIdOf(it.id) != null })
            assertEquals(MY_BOOKS_CATEGORY, myBooksCategory("הספרים שלי").id)
            assertNull(pdfIdOf(42), "a book of the library")
            assertNull(pdfIdOf(MY_BOOKS_CATEGORY))
            assertTrue(folderCategoryId(dir("ספרייה")) < -1_000_000L, "below the tree's own rows")
        }
}
