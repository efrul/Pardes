package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseBooksTest {
    private fun book(
        id: Long,
        title: String,
        base: Boolean = false,
    ) = CatalogBook(id = id, title = title, categoryId = 0L, isBaseBook = base)

    private fun category(
        id: Long,
        title: String,
        books: List<CatalogBook> = emptyList(),
        subcategories: List<CatalogCategory> = emptyList(),
    ) = CatalogCategory(id = id, title = title, level = 0, books = books, subcategories = subcategories)

    // מחשבת ישראל › מהר״ל מפראג: his books, notes on two of them (one titled so, one not), no curated base book
    private val maharal =
        category(
            2L,
            "מהר״ל מפראג",
            listOf(
                book(10L, "גבורות השם"),
                book(11L, "נצח ישראל"),
                book(12L, "פירושים והערות מהרב יהושע הרטמן על גבורות השם"),
                book(13L, "מקורות לנצח ישראל"),
            ),
        )

    // A curated corpus, with notes curated by mistake below its מפרשים
    private val midrash =
        category(
            3L,
            "מדרש לקח טוב",
            listOf(book(20L, "מדרש לקח טוב", base = true), book(21L, "מדרש לקח טוב על אסתר", base = true)),
            listOf(category(4L, "מפרשים", listOf(book(22L, "תיקונים על מדרש לקח טוב", base = true), book(23L, "מאיר עין", base = true)))),
        )

    // קבלה › זהר: the זהר comments on the Torah, outside the category; a commentary on it inside
    private val zohar = category(5L, "זהר", listOf(book(30L, "ספר הזהר"), book(31L, "הסולם על ספר הזהר")))

    private val roots = listOf(category(1L, "מחשבת ישראל", subcategories = listOf(maharal)), midrash, zohar)

    private val commented =
        mapOf(
            12L to longArrayOf(10L),
            13L to longArrayOf(11L),
            22L to longArrayOf(20L),
            23L to longArrayOf(20L),
            30L to longArrayOf(900L),
            31L to longArrayOf(30L),
        )

    private val books = BaseBooks.of(roots, commented)

    @Test
    fun `a category without curated base books is told apart by the commentaries on its own books`() {
        assertEquals(listOf(10L, 11L), books.getValue(2L).base.asList())
        assertTrue(books.getValue(2L).hasBaseBooksAndOthers)
        // Its parent too: the same books
        assertEquals(listOf(10L, 11L), books.getValue(1L).base.asList())
    }

    @Test
    fun `a commentary on a book of another category is a base book here`() {
        assertEquals(listOf(30L), books.getValue(5L).base.asList())
    }

    @Test
    fun `curated base books stay, but for those below a category of commentaries`() {
        assertEquals(listOf(20L, 21L), books.getValue(3L).base.asList())
        // In the category of commentaries itself, they are the texts searched: nothing to tell apart
        assertFalse(books.getValue(4L).hasBaseBooksAndOthers)
        assertEquals(listOf(22L, 23L), books.getValue(4L).base.asList())
    }

    @Test
    fun `without the links, only the curated base books count`() {
        val curatedOnly = BaseBooks.of(roots)
        assertFalse(curatedOnly.getValue(2L).hasBaseBooksAndOthers)
        assertEquals(listOf(10L, 11L, 12L, 13L), curatedOnly.getValue(2L).base.asList())
        assertEquals(listOf(20L, 21L), curatedOnly.getValue(3L).base.asList())
    }

    @Test
    fun `too few commentaries on its own books, and a category isn't offered in its base books alone`() {
        val many = (1L..40L).map { book(it, "שו״ת $it") }
        val responsa = BaseBooks.of(listOf(category(6L, "שו״ת", many)), mapOf(40L to longArrayOf(1L)))
        assertFalse(responsa.getValue(6L).hasBaseBooksAndOthers)
    }

    @Test
    fun `a commentary's title`() {
        assertTrue(BaseBooks.isCommentaryTitle("הערות בובר על מדרש משלי"))
        assertTrue(BaseBooks.isCommentaryTitle("פירוש הרמק לספר יצירה"))
        assertFalse(BaseBooks.isCommentaryTitle("ביאור"))
        assertFalse(BaseBooks.isCommentaryTitle("ספר הזהר"))
    }
}
