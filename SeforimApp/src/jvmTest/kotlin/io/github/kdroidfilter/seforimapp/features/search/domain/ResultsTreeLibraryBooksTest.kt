package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The results' tree takes the library's books as they are known ([BuildSearchTreeUseCase]'s `libraryBooks`). */
class ResultsTreeLibraryBooksTest {
    private fun book(
        id: Long,
        title: String,
        order: Float,
    ) = Book(id = id, categoryId = 13L, sourceId = 0L, title = title, order = order)

    // תלמוד › בבלי
    private val repository =
        mockk<SeforimRepository> {
            coEvery { getCategory(12L) } returns Category(id = 12L, parentId = null, title = "תלמוד", level = 0)
            coEvery { getCategory(13L) } returns Category(id = 13L, parentId = 12L, title = "בבלי", level = 1)
        }
    private val categoryCounts = mapOf(12L to 5, 13L to 5)

    @Test
    fun `the tree's books are the library's own, none read from the database`() =
        runTest {
            val library = mapOf(1L to book(1L, "ברכות", 1f), 2L to book(2L, "שבת", 2f), 3L to book(3L, "עירובין", 3f))
            val useCase = BuildSearchTreeUseCase(repository) { library }

            val tree = useCase.invoke(facetCategoryCounts = categoryCounts, facetBookCounts = mapOf(2L to 3, 1L to 2))

            val bavli = tree.single().children.single()
            assertEquals(listOf("ברכות" to 2, "שבת" to 3), bavli.books.map { it.book.title to it.count })
            assertEquals(listOf(12L, 13L), useCase.categoryIdsOf(3L))
            coVerify(exactly = 0) { repository.getBookCore(any()) }
        }

    @Test
    fun `a book the library's list lacks is read as before`() =
        runTest {
            coEvery { repository.getBookCore(3L) } returns book(3L, "עירובין", 3f)
            val useCase = BuildSearchTreeUseCase(repository) { mapOf(1L to book(1L, "ברכות", 1f)) }

            val tree = useCase.invoke(facetCategoryCounts = categoryCounts, facetBookCounts = mapOf(3L to 1, 1L to 4))

            val bavli = tree.single().children.single()
            assertEquals(listOf("ברכות", "עירובין"), bavli.books.map { it.book.title })
            coVerify(exactly = 1) { repository.getBookCore(3L) }
        }
}
