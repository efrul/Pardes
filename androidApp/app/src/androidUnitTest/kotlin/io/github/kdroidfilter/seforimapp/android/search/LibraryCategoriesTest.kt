package io.github.kdroidfilter.seforimapp.android.search

import io.github.kdroidfilter.seforimapp.features.search.domain.CategoryHit
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pardes desktop's LibraryCategoriesTest, on the port's catalog reading. */
class LibraryCategoriesTest {
    private fun book(id: Long, categoryId: Long, base: Boolean) = CatalogBook(id = id, title = "$id", categoryId = categoryId, isBaseBook = base)

    // תלמוד › בבלי, its tractates (base books) in סדר מועד and a commentary in ראשונים
    private val categories = LibraryCategories.of(
        PrecomputedCatalog(
            rootCategories = listOf(
                CatalogCategory(
                    12L, "תלמוד", 0, null, emptyList(),
                    listOf(
                        CatalogCategory(
                            13L, "בבלי", 1, 12L, emptyList(),
                            listOf(
                                CatalogCategory(15L, "סדר מועד", 2, 13L, listOf(book(1L, 15L, true), book(2L, 15L, true)), emptyList()),
                                CatalogCategory(212L, "ראשונים", 2, 13L, listOf(book(3L, 212L, false)), emptyList()),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `a category's books are its subcategories' too, all of them or its base books alone`() {
        assertEquals(listOf(1L, 2L, 3L), categories.bookIds(12L))
        assertEquals(listOf(1L, 2L), categories.bookIds(12L, baseBooksOnly = true))
        assertEquals(listOf(3L), categories.bookIds(212L))
        // Not offered in its base books alone: a choice kept from before searches all of it, not nothing
        assertEquals(listOf(3L), categories.bookIds(212L, baseBooksOnly = true))
        assertEquals(emptyList(), categories.bookIds(999L))
    }

    @Test
    fun `the categories come in the library's order with their paths, those holding both kinds marked`() {
        val hits = categories.categories()
        assertEquals(listOf(12L, 13L, 15L, 212L), hits.map { it.id })
        assertEquals(listOf("תלמוד", "בבלי", "ראשונים"), hits.last().path)
        assertEquals(listOf(true, true, false, false), hits.map { it.hasBaseBooksAndOthers })
        assertEquals(CategoryHit(12L, listOf("תלמוד"), baseBooksOnly = true, hasBaseBooksAndOthers = true), categories.hit(12L, baseBooksOnly = true))
    }
}
