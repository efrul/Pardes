package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeItemsTest {
    private fun book(
        id: Long,
        title: String,
    ) = ScopeItem.BookItem(Book(id = id, categoryId = 13L, sourceId = 1L, title = title))

    private val berakhot = book(1L, "ברכות")
    private val shabbat = book(2L, "שבת")
    private val rashiTorah = ScopeItem.CategoryItem(CategoryHit(1142L, listOf("תנ״ך", "ראשונים", "רש״י", "תורה")))

    @Test
    fun `items are added once each, in the order picked`() {
        val items = ScopeItems.add(ScopeItems.add(listOf(berakhot), rashiTorah), shabbat)
        assertEquals(listOf(berakhot, rashiTorah, shabbat), items)
        // The same book again, even as another object of it
        assertEquals(items, ScopeItems.add(items, book(1L, "ברכות")))
    }

    @Test
    fun `an item is removed by its book or category`() {
        assertEquals(listOf(rashiTorah), ScopeItems.remove(listOf(berakhot, rashiTorah), book(1L, "ברכות")))
    }

    @Test
    fun `one book or one category is searched alone, more together`() {
        assertEquals(SearchScope.Global, ScopeItems.scopeOf(emptyList()))
        assertEquals(SearchScope.Book(1L), ScopeItems.scopeOf(listOf(berakhot)))
        assertEquals(SearchScope.Category(1142L), ScopeItems.scopeOf(listOf(rashiTorah)))
        assertEquals(
            SearchScope.Multi(bookIds = listOf(1L, 2L), categoryIds = listOf(1142L)),
            ScopeItems.scopeOf(listOf(berakhot, rashiTorah, shabbat)),
        )
    }

    @Test
    fun `a chip names a category with its parent`() {
        assertEquals("רש״י › תורה", rashiTorah.label)
        assertEquals("ברכות", berakhot.label)
    }
}
