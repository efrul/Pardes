package io.github.kdroidfilter.seforimapp.core.deeplink

import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.session.BookContentPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A whole desktop as one link: built from its tabs where they stand, read back, and opened with what the library has. */
class DesktopLinkTest {
    private fun book(
        id: Long,
        line: Long? = null,
    ) = TabsDestination.BookContent(bookId = id, tabId = "book-$id", lineId = line)

    private fun search(query: String) = TabsDestination.Search(searchQuery = query, tabId = "search-$query")

    private fun reading(
        book: Long = -1L,
        top: Long = -1L,
        selected: Long = -1L,
    ) = TabPersistedState(
        bookContent = BookContentPersistedState(selectedBookId = book, contentAnchorLineId = top, primarySelectedLineId = selected),
    )

    // What a link says of its tabs, whatever ids they carry
    private fun List<TabsDestination>.places() =
        map {
            when (it) {
                is TabsDestination.BookContent -> "book ${it.bookId} line ${it.lineId}"
                is TabsDestination.Search -> "search ${it.searchQuery}"
                else -> it::class.simpleName.orEmpty()
            }
        }

    @Test
    fun `a desktop's link names it and lists its tabs as their own links, and reads back`() {
        val link = desktopShareLink("D2", listOf(book(12, 345), book(7), search("a b&c=d/e")), selected = 2)

        assertEquals("zayit://desktop/D2?t=book/12/line/345&t=book/7&t=search/a+b%26c%3Dd%2Fe&s=2", link)
        val read = parseDesktopLink(link.orEmpty())
        assertEquals("D2", read?.name)
        assertEquals(listOf("book 12 line 345", "book 7 line null", "search a b&c=d/e"), read?.tabs?.places())
        assertEquals(2, read?.selected)
        // Tabs of their own, not the ones the link was made from
        val ids = read?.tabs.orEmpty().map { it.tabId }
        assertEquals(3, ids.distinct().size)
        assertEquals(emptyList(), ids.filter { it.startsWith("book-") || it.startsWith("search-") })
    }

    @Test
    fun `a Hebrew name and Hebrew words read back as they were`() {
        val link = desktopShareLink("עיון א׳", listOf(search("שור שנגח את הפרה"), book(3)))

        val read = parseDesktopLink(link.orEmpty())
        assertEquals("עיון א׳", read?.name)
        assertEquals(listOf("search שור שנגח את הפרה", "book 3 line null"), read?.tabs?.places())
        assertEquals(0, read?.selected)
    }

    @Test
    fun `tabs with no link of their own are left out, and the tab shown is still the one shown`() {
        val tabs = listOf(TabsDestination.Home("home"), book(3), TabsDestination.History("history"), book(5, 9), book(-1))

        assertEquals("zayit://desktop/D?t=book/3&t=book/5/line/9&s=1", desktopShareLink("D", tabs, selected = 3))
        // The Home shown: the link opens on its first tab
        assertEquals("zayit://desktop/D?t=book/3&t=book/5/line/9", desktopShareLink("D", tabs, selected = 0))
        assertNull(desktopShareLink("D", listOf(TabsDestination.Home("home"), book(-1))))
    }

    @Test
    fun `a link carries no more than its first forty tabs, and reads no more`() {
        val many = (1L..45L).map { book(it) }

        val link = desktopShareLink("D", many, selected = 44).orEmpty()
        assertEquals((1L..40L).map { "book $it line null" }, parseDesktopLink(link)?.tabs?.places())
        // The tab shown is past them
        assertEquals(0, parseDesktopLink(link)?.selected)

        val written = "zayit://desktop/D?" + (1..45).joinToString("&") { "t=book/$it" }
        assertEquals(MAX_LINKED_TABS, parseDesktopLink(written)?.tabs?.size)
    }

    @Test
    fun `what is no desktop's link reads as none, and a careless one is read as far as it goes`() {
        assertNull(parseDesktopLink("zayit://book/5"))
        assertNull(parseDesktopLink("desktop/D?t=book/5"))
        assertNull(parseDesktopLink("zayit://desktop/D"))
        assertNull(parseDesktopLink("zayit://desktop/D?t=nothing/5&t=book/x"))
        assertNull(parseDesktopLink("שור שנגח"))

        val careless = parseDesktopLink("zayit://desktop/a+very+long+name?x=1&t=book/5&t=nothing&s=9")
        assertEquals("a very l", careless?.name)
        assertEquals(listOf("book 5 line null"), careless?.tabs?.places())
        assertEquals(0, careless?.selected)
        assertEquals("", parseDesktopLink("zayit://desktop/?t=book/5")?.name)
    }

    @Test
    fun `a tab is linked to where it is read, not where it was opened`() {
        // The line at the top of the screen, before the line selected
        assertEquals(book(12, 400), book(12, 345).asItStands(reading(book = 12, top = 400, selected = 380)))
        assertEquals(book(12, 380), book(12, 345).asItStands(reading(book = 12, selected = 380)))
        // Nothing kept of where it is read: where it was opened
        assertEquals(book(12, 345), book(12, 345).asItStands(reading(book = 12)))
        assertEquals(book(12, 345), book(12, 345).asItStands(null))
        // Another book read in the tab since: that book, and not the first one's line
        assertEquals(book(12, 345).copy(bookId = 30, lineId = 77), book(12, 345).asItStands(reading(book = 30, top = 77)))
        assertEquals(book(12, 345).copy(bookId = 30, lineId = null), book(12, 345).asItStands(reading(book = 30)))

        val searched = TabPersistedState(search = SearchPersistedState(query = "אחרי כן"))
        assertEquals(search("תחילה").copy(searchQuery = "אחרי כן"), search("תחילה").asItStands(searched))
        assertEquals(search("תחילה"), search("תחילה").asItStands(TabPersistedState(search = SearchPersistedState())))
        assertEquals(TabsDestination.Home("home"), TabsDestination.Home("home").asItStands(reading(book = 5)))
    }

    @Test
    fun `a desktop's snapshot gives its link, its windows' tabs in order, each where it stands`() {
        val snapshot =
            DesktopTabsSnapshot(
                tabStates = mapOf("book-12" to reading(book = 12, top = 400), "search-a" to TabPersistedState()),
                windows =
                    listOf(
                        WindowSnapshot(destinations = listOf(book(12), TabsDestination.Home("home"), book(7)), selectedIndex = 2),
                        WindowSnapshot(destinations = listOf(search("a")), selectedIndex = 0),
                    ),
            )

        assertEquals("zayit://desktop/D?t=book/12/line/400&t=book/7&t=search/a&s=1", snapshot.shareLink("D"))
        assertNull(DesktopTabsSnapshot().shareLink("D"))
    }

    @Test
    fun `a link opens with the tabs whose books the library has, titled`() {
        val library = mapOf(12L to Book(id = 12, categoryId = 1, sourceId = 1, title = "ברכות"))
        val link = DesktopLink("D", listOf(book(99), book(12, 345), search("שור")), selected = 2)

        val window = link.asWindow(library)

        assertEquals(listOf(book(12, 345), search("שור")), window?.destinations)
        assertEquals(1, window?.selectedIndex)
        assertEquals("ברכות", window?.titles?.get("book-12")?.title)
        assertEquals(TabType.BOOK, window?.titles?.get("book-12")?.tabType)
        assertEquals("שור", window?.titles?.get("search-שור")?.title)
        // The tab shown is of a book the library lacks: the first one
        assertEquals(0, link.copy(selected = 0).asWindow(library)?.selectedIndex)
        assertNull(DesktopLink("D", listOf(book(99))).asWindow(library))
    }
}
