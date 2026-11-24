package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.desktop.TabSplit
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the search from anywhere finds opens as a tab, in the place of the one it was opened over (request 85 of stage 3). */
class SearchAnywhereTest {
    @Test
    fun `a text search opens its results under the bar's tab id`() {
        val destination = tabDestination(SearchHomeNavigationEvent.NavigateToSearch(query = "בין השמשות", tabId = "new"))
        assertEquals(TabsDestination.Search(searchQuery = "בין השמשות", tabId = "new"), destination)
    }

    @Test
    fun `a book or a reference opens under the bar's tab id, at its line`() {
        val book = tabDestination(SearchHomeNavigationEvent.NavigateToBookContent(bookId = 382, tabId = "new", lineId = null))
        assertEquals(TabsDestination.BookContent(bookId = 382, tabId = "new", lineId = null), book)
        val place = tabDestination(SearchHomeNavigationEvent.NavigateToBookContent(bookId = 382, tabId = "new", lineId = 77))
        assertEquals(TabsDestination.BookContent(bookId = 382, tabId = "new", lineId = 77), place)
    }

    @Test
    fun `a link or an author's page opens as it is`() {
        val author = TabsDestination.Author(tabId = "new", authorId = 5)
        assertEquals(author, tabDestination(SearchHomeNavigationEvent.NavigateToDeepLink(author)))
    }

    @Test
    fun `a desktop's link is not a tab`() {
        assertNull(tabDestination(SearchHomeNavigationEvent.OpenDesktop(name = "לימוד", tabs = WindowSnapshot())))
    }

    @Test
    fun `the tab that takes a split tab's place keeps its half and the split's share`() {
        val split = TabSplit(first = "left", second = "right", ratio = 0.3f)
        assertEquals(TabSplit(first = "new", second = "right", ratio = 0.3f), split.with("new", inPlaceOf = "left"))
        assertEquals(TabSplit(first = "left", second = "new", ratio = 0.3f), split.with("new", inPlaceOf = "right"))
    }
}
