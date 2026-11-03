package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import io.github.kdroidfilter.seforimapp.features.search.domain.CategoryHit
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlin.test.Test
import kotlin.test.assertEquals

class CatBookRowsTest {
    private fun book(
        id: Long,
        title: String,
    ) = BookSuggestion(Book(id = id, categoryId = 45L, sourceId = 1L, title = title), listOf("הלכה", "משנה תורה", title))

    private val mishnehTorah = CategoryHit(45L, listOf("הלכה", "משנה תורה"), hasBaseBooksAndOthers = true)
    private val kesefMishneh = CategoryHit(604L, listOf("הלכה", "משנה תורה", "מפרשים", "כסף משנה"))
    private val categories = listOf(mishnehTorah, mishnehTorah.copy(baseBooksOnly = true), kesefMishneh)
    private val books = listOf(book(1L, "משנה תורה, מסירת תורה שבעל פה"), book(2L, "משנה תורה, הלכות יסודי התורה"))

    @Test
    fun `a category named as typed comes before the books whose titles begin so`() {
        val rows = catBookRows("משנה תורה", emptyList(), textSearch = true, authors = emptyList(), books = books, categories = categories)
        assertEquals(
            listOf(
                CatBookRow.Category(categories[0]),
                CatBookRow.Category(categories[1]),
                CatBookRow.TextSearch,
                CatBookRow.Category(kesefMishneh),
                CatBookRow.Book(books[0]),
                CatBookRow.Book(books[1]),
            ),
            rows,
        )
    }

    @Test
    fun `a category named as typed comes before the books an acronym names so, a book's own title before it`() {
        // A library where the books of משנה תורה are called so too, as their acronym
        val byAcronym = books.map { it.copy(exactAcronym = true) }
        val titled = BookSuggestion(Book(id = 3L, categoryId = 9L, sourceId = 1L, title = "משנה תורה"), listOf("משנה תורה"))
        val rows =
            catBookRows(
                "משנה תורה",
                emptyList(),
                textSearch = true,
                authors = emptyList(),
                books = byAcronym + titled,
                categories = categories,
            )
        assertEquals(
            listOf(
                CatBookRow.Book(titled),
                CatBookRow.Category(categories[0]),
                CatBookRow.Category(categories[1]),
                CatBookRow.Book(byAcronym[0]),
                CatBookRow.Book(byAcronym[1]),
                CatBookRow.TextSearch,
                CatBookRow.Category(kesefMishneh),
            ),
            rows,
        )
    }

    @Test
    fun `the books an acronym names come first when no category is named so`() {
        val orachChaim = Book(id = 20L, categoryId = 30L, sourceId = 1L, title = "שולחן ערוך, אורח חיים")
        val shulchanAruch = BookSuggestion(orachChaim, listOf("שולחן ערוך"), exactAcronym = true)
        val rows =
            catBookRows(
                "שוע",
                emptyList(),
                textSearch = true,
                authors = emptyList(),
                books = listOf(shulchanAruch),
                categories = listOf(kesefMishneh),
            )
        assertEquals(listOf(CatBookRow.Book(shulchanAruch), CatBookRow.TextSearch, CatBookRow.Category(kesefMishneh)), rows)
    }

    @Test
    fun `a title being typed comes first when no category is named so`() {
        val bavaKama = BookSuggestion(Book(id = 10L, categoryId = 17L, sourceId = 1L, title = "בבא קמא"), listOf("בבא קמא"))
        val rows = catBookRows("בבא ק", emptyList(), textSearch = true, authors = emptyList(), books = listOf(bavaKama))
        assertEquals(listOf(CatBookRow.Book(bavaKama), CatBookRow.TextSearch), rows)
    }

    @Test
    fun `in a book, a category named as typed comes before the other books to add`() {
        assertEquals(
            listOf(
                CatBookRow.Category(categories[0]),
                CatBookRow.Category(categories[1]),
                CatBookRow.Book(books[0]),
                CatBookRow.Book(books[1]),
                CatBookRow.Category(kesefMishneh),
            ),
            tocAddRows("משנה תורה", books, categories),
        )
    }
}
