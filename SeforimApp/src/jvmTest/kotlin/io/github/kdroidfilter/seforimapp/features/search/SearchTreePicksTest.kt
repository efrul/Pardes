package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What is checked in a search's tree of books: where its bar searches again. */
class SearchTreePicksTest {
    @Test
    fun `one category or one book checked is the bar's, several are searched together`() {
        val picks = SearchTreePicks()
        assertNull(picks.scope(), "nothing checked: everywhere")
        assertEquals(SearchScope.Category(7), picks.category(7, checked = true))
        assertEquals(SearchScope.Multi(bookIds = listOf(40), categoryIds = listOf(7)), picks.book(40, checked = true))
        assertEquals(SearchScope.Multi(bookIds = listOf(40), categoryIds = listOf(7, 9)), picks.category(9, checked = true))
        assertEquals(SearchScope.Multi(bookIds = listOf(40), categoryIds = listOf(9)), picks.category(7, checked = false))
        assertEquals(SearchScope.Book(40), picks.category(9, checked = false))
        assertNull(picks.book(40, checked = false))
    }

    @Test
    fun `the tabs picked are the categories checked, and none at a new search`() {
        val picks = SearchTreePicks()
        picks.book(40, checked = true)
        picks.only(listOf(3, 5))
        assertEquals(SearchScope.Multi(bookIds = emptyList(), categoryIds = listOf(3, 5)), picks.scope())
        assertEquals(SearchScope.Multi(bookIds = listOf(41), categoryIds = listOf(3, 5)), picks.book(41, checked = true))
        picks.only()
        assertNull(picks.scope())
    }
}
