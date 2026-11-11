package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.search.domain.LibraryCategories
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A desktop's link pasted into the search bar opens that desktop instead of a search of its text. */
class DesktopLinkPasteTest {
    // A library of one book, ברכות (12)
    private val catalog =
        PrecomputedCatalog(
            rootCategories =
                listOf(
                    CatalogCategory(
                        id = 1L,
                        title = "תלמוד",
                        level = 0,
                        books = listOf(CatalogBook(id = 12L, title = "ברכות", categoryId = 1L, isBaseBook = true)),
                        subcategories = emptyList(),
                    ),
                ),
        )
    private val catalogCache = mockk<CatalogCache> { every { getCatalog() } returns catalog }
    private val library = LibraryCategories(catalogCache, mockk(), mockk { every { counted() } returns emptyMap() })

    private fun bar() =
        SearchHomeViewModel(
            TabPersistedStateStore(),
            mockk(relaxed = true),
            mockk(relaxed = true),
            testAppSettings(),
            library,
            observeProfile = false,
        )

    @Test
    fun `a desktop's link opens the desktop with the tabs the library has`() =
        runTest {
            val bar = bar()

            bar.submitSearch(" zayit://desktop/D2?t=book/12/line/345&t=book/99&t=search/q&s=2 ", "tab")

            val event = assertIs<SearchHomeNavigationEvent.OpenDesktop>(withTimeout(1_000) { bar.navigationEvents.first() })
            assertEquals("D2", event.name)
            val tabs = event.tabs.destinations
            assertEquals(listOf(12L to 345L), tabs.filterIsInstance<TabsDestination.BookContent>().map { it.bookId to it.lineId })
            assertEquals(listOf("q"), tabs.filterIsInstance<TabsDestination.Search>().map { it.searchQuery })
            // The search was the third tab, and is the second of those kept
            assertEquals(1, event.tabs.selectedIndex)
            assertEquals("ברכות", event.tabs.titles[tabs.first().tabId]?.title)
        }

    @Test
    fun `a desktop's link to books the library lacks is searched as any text`() =
        runTest {
            val bar = bar()

            bar.submitSearch("zayit://desktop/D2?t=book/99", "tab")

            assertIs<SearchHomeNavigationEvent.NavigateToSearch>(withTimeout(1_000) { bar.navigationEvents.first() })
        }
}
