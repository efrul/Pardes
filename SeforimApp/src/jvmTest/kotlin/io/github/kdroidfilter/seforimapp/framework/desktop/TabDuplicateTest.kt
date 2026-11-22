package io.github.kdroidfilter.seforimapp.framework.desktop

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.session.BookContentPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import kotlin.test.Test
import kotlin.test.assertEquals

/** A copy of a tab opens where the tab is, from the state copied with it (request 59). */
class TabDuplicateTest {
    @Test
    fun `a book tab's copy opens the book read last, with no line of its own`() {
        val opened = TabsDestination.BookContent(bookId = 1, tabId = "a", lineId = 10, endLineId = 12, openNotes = true)
        val state = TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 7))

        val copy = duplicateDestination(opened, state)

        assertEquals(TabsDestination.BookContent(bookId = 7, tabId = "a"), copy)
    }

    @Test
    fun `a book tab never moved keeps its book`() {
        val opened = TabsDestination.BookContent(bookId = 3, tabId = "a", shnayimMikra = true)

        val copy = duplicateDestination(opened, TabPersistedState())

        assertEquals(TabsDestination.BookContent(bookId = 3, tabId = "a"), copy)
    }

    @Test
    fun `a search tab's copy takes its scope from the state, not from the destination`() {
        val opened = TabsDestination.Search("קל וחומר", "a", scope = SearchScope.Book(5), globalExtended = true)

        val copy = duplicateDestination(opened, TabPersistedState())

        assertEquals(TabsDestination.Search("קל וחומר", "a"), copy)
    }

    @Test
    fun `another tab is copied as it is`() {
        val notes = TabsDestination.Notes("a")

        assertEquals(notes, duplicateDestination(notes, TabPersistedState()))
    }
}
