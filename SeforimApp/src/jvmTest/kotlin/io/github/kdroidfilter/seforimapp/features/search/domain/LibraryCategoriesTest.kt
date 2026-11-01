package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryCategoriesTest {
    private fun book(
        id: Long,
        categoryId: Long,
        base: Boolean,
    ) = CatalogBook(id = id, title = "$id", categoryId = categoryId, isBaseBook = base)

    // תלמוד › בבלי, its tractates (base books) in סדר מועד and a commentary in ראשונים
    private val catalog =
        PrecomputedCatalog(
            rootCategories =
                listOf(
                    CatalogCategory(
                        id = 12L,
                        title = "תלמוד",
                        level = 0,
                        books = emptyList(),
                        subcategories =
                            listOf(
                                CatalogCategory(
                                    id = 13L,
                                    title = "בבלי",
                                    level = 1,
                                    parentId = 12L,
                                    books = emptyList(),
                                    subcategories =
                                        listOf(
                                            CatalogCategory(
                                                15L,
                                                "סדר מועד",
                                                2,
                                                13L,
                                                listOf(book(1L, 15L, true), book(2L, 15L, true)),
                                                emptyList(),
                                            ),
                                            CatalogCategory(212L, "ראשונים", 2, 13L, listOf(book(3L, 212L, false)), emptyList()),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    private val catalogCache = mockk<CatalogCache> { every { getCatalog() } returns catalog }

    // No database: the catalog answers everything
    private val categories = LibraryCategories(catalogCache, mockk())

    @Test
    fun `a category's books are its subcategories' too, all of them or its base books alone`() =
        runBlocking {
            assertEquals(listOf(1L, 2L, 3L), categories.bookIds(12L))
            assertEquals(listOf(1L, 2L), categories.bookIds(12L, baseBooksOnly = true))
            assertEquals(listOf(3L), categories.bookIds(212L))
            assertEquals(emptyList(), categories.bookIds(212L, baseBooksOnly = true))
            assertEquals(emptyList(), categories.bookIds(999L))
        }

    @Test
    fun `the categories come in the library's order with their paths, those holding both kinds marked`() =
        runBlocking {
            val hits = categories.categories()
            assertEquals(listOf(12L, 13L, 15L, 212L), hits.map { it.id })
            assertEquals(listOf("תלמוד", "בבלי", "ראשונים"), hits.last().path)
            assertEquals(listOf(true, true, false, false), hits.map { it.hasBaseBooksAndOthers })
            assertEquals(
                CategoryHit(12L, listOf("תלמוד"), baseBooksOnly = true, hasBaseBooksAndOthers = true),
                categories.hit(12L, baseBooksOnly = true),
            )
        }
}
