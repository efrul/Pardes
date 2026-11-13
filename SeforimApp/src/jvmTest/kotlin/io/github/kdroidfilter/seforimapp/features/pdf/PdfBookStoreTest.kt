package io.github.kdroidfilter.seforimapp.features.pdf

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The user's PDF books, kept in the user DB, and their rows in the book tree's "הספרים שלי". */
class PdfBookStoreTest {
    private val root = createTempDirectory("pdf-store").toFile()
    private val store = PdfBookStore(UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)))

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun pdf(name: String): File = File(root, "picked/$name").apply { parentFile.mkdirs() }.apply { writeText(name) }

    @Test
    fun `a book is added copied, listed by title, and found again when added twice`() =
        runBlocking {
            val folder = File(root, "my-books")
            val revision = store.revision.value

            val shulchanAruch = store.add(pdf("שולחן ערוך.pdf"), folder)
            val ketzot = store.add(pdf("קצות החושן.pdf"), folder)
            val again = store.add(pdf("שולחן ערוך.pdf"), folder)

            assertEquals(File(folder, "שולחן ערוך.pdf").absoluteFile, shulchanAruch.file)
            assertEquals(shulchanAruch, again)
            assertEquals(listOf("קצות החושן", "שולחן ערוך"), store.books().map { it.title })
            assertEquals(ketzot, store.book(ketzot.id))
            assertTrue(store.revision.value > revision, "the tree reads the list again")
        }

    @Test
    fun `a book kept in place is its own file`() =
        runBlocking {
            val source = pdf("book.pdf")

            assertEquals(source.absoluteFile, store.add(source, null).file)
        }

    @Test
    fun `where a book was read is kept, and a book removed is gone but its file stays`() =
        runBlocking {
            val book = store.add(pdf("book.pdf"), File(root, "my-books"))
            val place = PdfPlace(page = 41, offset = 120, zoom = 1.5f)

            store.setPlace(book.id, place)

            assertEquals(place, store.book(book.id)?.place)
            store.remove(book.id)
            assertNull(store.book(book.id))
            assertEquals(emptyList(), store.books())
            assertTrue(book.file.isFile)
        }

    @Test
    fun `the tree's rows are the books by their own ids, then the row that adds one`() {
        val books = listOf(PdfBook(1, File("/a.pdf"), "א"), PdfBook(7, File("/b.pdf"), "ב"))

        val rows = myBooksRows(books, add = "הוספת PDF…")

        assertEquals(listOf("א", "ב", "הוספת PDF…"), rows.map { it.title })
        assertEquals(listOf(1L, 7L, null), rows.map { pdfIdOf(it.id) })
        assertEquals(MY_BOOKS_ADD, rows.last().id)
        assertTrue(rows.all { it.categoryId == MY_BOOKS_CATEGORY && it.id < 0 })
        assertEquals(MY_BOOKS_CATEGORY, myBooksCategory("הספרים שלי").id)
        assertNull(pdfIdOf(42), "a book of the library")
        assertNull(pdfIdOf(MY_BOOKS_CATEGORY))
    }
}
