package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.TestFactories
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorDefaults
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.commentatorSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.withCommentatorSelection
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The commentators a book opens with: those of its tab, else the reader's own default ([CommentatorDefaults]), else the
 * library's; and the default set from the list, and let go.
 */
class CommentariesUseCaseReaderDefaultTest {
    private val bookId = 100L
    private val libraryDefault = listOf(1L, 2L)
    private val repository = mockk<SeforimRepository>(relaxed = true)
    private val readerDefaults = mockk<CommentatorDefaults>(relaxed = true)
    private lateinit var scope: CoroutineScope
    private lateinit var stateManager: BookContentStateManager
    private lateinit var useCase: CommentariesUseCase

    private val selection: CommentatorSelection
        get() {
            val content = stateManager.state.value.content
            return content.commentatorSelection(bookId)
        }

    @BeforeTest
    fun setup() {
        scope = CoroutineScope(SupervisorJob())
        stateManager = BookContentStateManager("tab-reader-default", TabPersistedStateStore())
        stateManager.updateNavigation { copy(selectedBook = TestFactories.createBook(id = bookId)) }
        useCase = CommentariesUseCase(repository, stateManager, scope, readerDefaults)
        coEvery { repository.getDefaultCommentatorIdsForBook(bookId) } returns libraryDefault
        coEvery { readerDefaults.of(bookId) } returns null
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the library's default when the reader set none`() =
        runBlocking {
            useCase.applyDefaultCommentatorsForBook(bookId)

            assertEquals(CommentatorSelection(remembered = libraryDefault.toSet()), selection)
        }

    @Test
    fun `the reader's default comes before the library's`() =
        runBlocking {
            val own = CommentatorSelection(remembered = setOf(7L), groups = setOf("ראשונים על התנ״ך"))
            coEvery { readerDefaults.of(bookId) } returns own

            useCase.applyDefaultCommentatorsForBook(bookId)

            assertEquals(own, selection)
        }

    @Test
    fun `a reader's default of no commentator shows none, not the library's`() =
        runBlocking {
            coEvery { readerDefaults.of(bookId) } returns CommentatorSelection()

            useCase.applyDefaultCommentatorsForBook(bookId)

            assertEquals(CommentatorSelection(), selection)
        }

    @Test
    fun `the tab's own commentators stay`() =
        runBlocking {
            val chosen = CommentatorSelection(remembered = setOf(9L))
            stateManager.updateContent { withCommentatorSelection(bookId, chosen) }
            coEvery { readerDefaults.of(bookId) } returns CommentatorSelection(all = true)

            useCase.applyDefaultCommentatorsForBook(bookId)

            assertEquals(chosen, selection)
        }

    @Test
    fun `the default is what the book shows when set, and letting it go leaves what the tab shows`() =
        runBlocking {
            val chosen = CommentatorSelection(remembered = setOf(9L), all = true)
            stateManager.updateContent { withCommentatorSelection(bookId, chosen) }

            useCase.setCommentatorsDefault()
            coVerify(exactly = 1) { readerDefaults.set(bookId, chosen) }

            useCase.clearCommentatorsDefault()
            coVerify(exactly = 1) { readerDefaults.clear(bookId) }
            assertEquals(chosen, selection)
        }
}
