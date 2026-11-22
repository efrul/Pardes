package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.features.search.domain.LibraryCategories
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/** An author picked in the search bar (Tab on their row) lists all their books, whatever was typed to find them. */
class AuthorPickTest {
    private val books =
        listOf("משנה ברורה", "חפץ חיים", "שמירת הלשון").mapIndexed { i, title ->
            Book(id = i + 1L, categoryId = 7, sourceId = 0, title = title, order = i.toFloat())
        }
    private val repository =
        mockk<SeforimRepository>(relaxed = true) {
            coEvery { getAuthorDetails(10L) } returns
                AuthorDetails(10L, "רבי ישראל מאיר הכהן", 1839, false, 1933, false, "AH", listOf("חפץ חיים"), null, books)
        }
    private val catalogCache = mockk<CatalogCache> { every { getCatalog() } returns PrecomputedCatalog(rootCategories = emptyList()) }
    private val library = LibraryCategories(catalogCache, mockk(), mockk { every { counted() } returns emptyMap() })

    @Test
    fun `the author found by an alias lists all their books, not those the alias names`() =
        runBlocking {
            val bar = SearchHomeViewModel(TabPersistedStateStore(), repository, mockk(relaxed = true), testAppSettings(), library, false)
            bar.onReferenceQueryChanged("חפץ חיים")
            // The bar clears its field without telling the view model (HomeView's skipNextReferenceQuery), then picks
            bar.onPickAuthor(AuthorHit(id = 10L, name = "רבי ישראל מאיר הכהן", bookCount = 3, alias = "חפץ חיים"))

            val listed = withTimeout(5_000) { bar.uiState.first { it.bookSuggestions.size == books.size } }
            assertEquals(books.map { it.title }, listed.bookSuggestions.map { it.book.title })
        }
}
