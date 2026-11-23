package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the search from anywhere finds opens in a tab of its own (request 85 of stage 3). */
class SearchAnywhereTest {
    @Test
    fun `a text search opens its results in the new tab`() {
        val destination = newTabDestination(SearchHomeNavigationEvent.NavigateToSearch(query = "בין השמשות", tabId = "new"))
        assertEquals(TabsDestination.Search(searchQuery = "בין השמשות", tabId = "new"), destination)
    }

    @Test
    fun `a book or a reference opens in the new tab, at its line`() {
        val book = newTabDestination(SearchHomeNavigationEvent.NavigateToBookContent(bookId = 382, tabId = "new", lineId = null))
        assertEquals(TabsDestination.BookContent(bookId = 382, tabId = "new", lineId = null), book)
        val place = newTabDestination(SearchHomeNavigationEvent.NavigateToBookContent(bookId = 382, tabId = "new", lineId = 77))
        assertEquals(TabsDestination.BookContent(bookId = 382, tabId = "new", lineId = 77), place)
    }

    @Test
    fun `a link or an author's page opens as it is`() {
        val author = TabsDestination.Author(tabId = "new", authorId = 5)
        assertEquals(author, newTabDestination(SearchHomeNavigationEvent.NavigateToDeepLink(author)))
    }

    @Test
    fun `a desktop's link is not a tab`() {
        assertNull(newTabDestination(SearchHomeNavigationEvent.OpenDesktop(name = "לימוד", tabs = WindowSnapshot())))
    }
}
