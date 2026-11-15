package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookFilterResult
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The user's PDF books found by the book tree's search and suggested by the search bar, under "הספרים שלי" and their folders. */
class MyPdfBooksSearchTest {
    private val folder = Category(id = -1_000_001, parentId = MY_BOOKS_CATEGORY, title = "ספרייה", level = 1)
    private val halacha = Category(id = -1_000_002, parentId = folder.id, title = "הלכה", level = 2)
    private val shulchanAruch = Book(id = myBookId(1), categoryId = halacha.id, sourceId = 0, title = "שֻׁלְחָן עָרוּךְ, אורח חיים")
    private val ketzot = Book(id = myBookId(2), categoryId = folder.id, sourceId = 0, title = "קצות החושן")
    private val alone = Book(id = myBookId(3), categoryId = MY_BOOKS_CATEGORY, sourceId = 0, title = "ברכות PDF")
    private val library =
        PdfLibrary(
            children = mapOf(MY_BOOKS_CATEGORY to listOf(folder), folder.id to listOf(halacha)),
            books = listOf(alone, shulchanAruch, ketzot),
        )
    private val berakhot = Book(id = 103, categoryId = 14, sourceId = 1, title = "ברכות")
    private val talmud = Category(id = 14, title = "תלמוד")
    private val found =
        BookFilterResult(
            roots = listOf(talmud),
            books = setOf(berakhot),
            categoryIds = setOf(14),
            matchIds = listOf(103),
            bestMatchId = 103,
        )

    @Test
    fun `a book whose title holds every word of the query is found first, its points and case left aside, under its folders`() {
        val result = withMyPdfBooks(found, "ערוך שלחן", library, "הספרים שלי")

        assertEquals(listOf(MY_BOOKS_CATEGORY, talmud.id), result.roots.map { it.id })
        assertEquals(setOf(berakhot, shulchanAruch), result.books)
        assertEquals(setOf(14L, MY_BOOKS_CATEGORY, folder.id, halacha.id), result.categoryIds)
        assertEquals(listOf(folder), result.children[MY_BOOKS_CATEGORY])
        assertEquals(listOf(halacha), result.children[folder.id])
        assertEquals(listOf(shulchanAruch.id, 103L), result.matchIds)
        assertEquals(shulchanAruch.id, result.bestMatchId, "the user's book, the library's not named as typed")
    }

    @Test
    fun `the library's book named exactly as typed stays the best match, the user's shown above it`() {
        val result = withMyPdfBooks(found, "ברכות", library, "הספרים שלי")

        assertEquals(listOf(alone.id, 103L), result.matchIds)
        assertEquals(103L, result.bestMatchId)
    }

    @Test
    fun `only the folders of the books found are opened`() {
        val result = withMyPdfBooks(BookFilterResult(), "קצות", library, "הספרים שלי")

        assertEquals(setOf(ketzot), result.books)
        assertEquals(setOf(MY_BOOKS_CATEGORY, folder.id), result.categoryIds)
        assertEquals(null, result.children[folder.id])
        assertEquals(ketzot.id, result.bestMatchId, "the first book found when the library has none")
    }

    @Test
    fun `the search bar suggests a book of the user's under my books and its folders`() {
        val suggested = pdfBookSuggestions("שלחן", library, "הספרים שלי")

        assertEquals(listOf(shulchanAruch), suggested.map { it.book })
        assertEquals(listOf("הספרים שלי", "ספרייה", "הלכה", shulchanAruch.title), suggested.single().path)
        assertEquals(listOf("הספרים שלי", alone.title), pdfBookSuggestions("ברכות", library, "הספרים שלי").single().path)
        assertEquals(emptyList(), pdfBookSuggestions("ש", library, "הספרים שלי"), "a letter alone suggests no book, as in the bar")
    }

    @Test
    fun `a query no book of the user's answers leaves the result as it is`() {
        assertSame(found, withMyPdfBooks(found, "רמב\"ם", library, "הספרים שלי"))
        assertSame(found, withMyPdfBooks(found, "  ", library, "הספרים שלי"))
        assertEquals(setOf(alone), withMyPdfBooks(BookFilterResult(), "ברכות pdf", library, "הספרים שלי").books)
    }
}
