package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.TestFactories
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "כל המפרשים" from line to line, as the commentaries pane drives the use case: a line is selected (reapplied), its
 * list reports what it shows in its first frame, then the reader checks or unchecks.
 */
class CommentariesUseCaseAllCommentatorsTest {
    private val bookId = 100L
    private val rashi = 1L
    private val ramban = 2L
    private val sforno = 3L
    private val chizkuni = 4L

    // Each line's commentators list
    private val lists =
        mapOf(
            10L to mapOf("רש\"י" to rashi, "רמב\"ן" to ramban),
            11L to mapOf("רש\"י" to rashi, "רמב\"ן" to ramban, "ספורנו" to sforno),
            12L to mapOf("רש\"י" to rashi, "חזקוני" to chizkuni),
            // A heading lists its own; its section's lines (21, 22) have others
            20L to mapOf("רש\"י" to rashi),
            21L to mapOf("ספורנו" to sforno),
            22L to mapOf("חזקוני" to chizkuni),
        )

    private val store = TabPersistedStateStore()
    private val tabId = "tab-all-commentators"
    private lateinit var scope: CoroutineScope
    private lateinit var stateManager: BookContentStateManager
    private lateinit var useCase: CommentariesUseCase

    @BeforeTest
    fun setup() {
        scope = CoroutineScope(SupervisorJob())
        stateManager = BookContentStateManager(tabId, store)
        stateManager.updateNavigation { copy(selectedBook = TestFactories.createBook(id = bookId)) }
        useCase = spyk(CommentariesUseCase(mockk(relaxed = true), stateManager, scope))
        coEvery { useCase.getAvailableCommentators(any()) } answers { lists[firstArg()].orEmpty() }
        coEvery { useCase.getAvailableCommentatorsForLines(any()) } answers {
            firstArg<List<Long>>().flatMap { lists[it].orEmpty().entries }.associate { it.key to it.value }
        }
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun listed(lineId: Long) = lists.getValue(lineId).values.toSet()

    private val content get() = stateManager.state.value.content

    /** What the line's list shows checked, as LineCommentsView derives it. */
    private fun shownOn(lineId: Long): Set<Long> {
        val listed = listed(lineId)
        return if (bookId in content.allCommentatorsBooks) {
            listed - content.excludedCommentatorsByBook[bookId].orEmpty()
        } else {
            content.selectedCommentatorsByLine[lineId]
                .orEmpty()
                .ifEmpty { content.selectedCommentatorsByBook[bookId].orEmpty() } intersect listed
        }
    }

    /** The line selected, and its list's first frame reported. */
    private suspend fun select(lineId: Long) {
        useCase.reapplySelectedCommentators(TestFactories.createLine(id = lineId, bookId = bookId))
        useCase.updateSelectedCommentators(lineId, shownOn(lineId), listed(lineId))
    }

    private suspend fun check(
        lineId: Long,
        vararg ids: Long,
    ) = useCase.updateSelectedCommentators(lineId, shownOn(lineId) + ids.toSet(), listed(lineId))

    private suspend fun uncheck(
        lineId: Long,
        vararg ids: Long,
    ) = useCase.updateSelectedCommentators(lineId, shownOn(lineId) - ids.toSet(), listed(lineId))

    private suspend fun checkAll(lineId: Long) {
        useCase.setAllCommentators(lineId, checked = true, availableIds = listed(lineId))
        useCase.updateSelectedCommentators(lineId, listed(lineId), listed(lineId))
    }

    @Test
    fun `all of them checked shows on the next lines also the commentators the line did not have`() =
        runBlocking {
            select(10)
            check(10, rashi)
            checkAll(10)

            select(11)
            assertEquals(setOf(rashi, ramban, sforno), shownOn(11))
            select(12)
            assertEquals(setOf(rashi, chizkuni), shownOn(12))
        }

    @Test
    fun `without all of them, a commentator no line where one checked them had stays unchecked`() =
        runBlocking {
            // Each of the line's commentators checked one by one: the list mode, as before
            select(10)
            check(10, rashi, ramban)

            select(11)
            assertEquals(setOf(rashi, ramban), shownOn(11))
        }

    @Test
    fun `a commentator unchecked with all of them stays hidden further on, and back on a line already seen`() =
        runBlocking {
            select(10)
            checkAll(10)
            select(11)
            uncheck(11, ramban)

            select(12)
            assertEquals(setOf(rashi, chizkuni), shownOn(12))
            select(10)
            assertEquals(setOf(rashi), shownOn(10))
            assertEquals(setOf(rashi), content.selectedCommentatorsByLine[10L])
        }

    @Test
    fun `all of them unchecked leaves none on any line`() =
        runBlocking {
            select(10)
            checkAll(10)
            select(11)
            useCase.setAllCommentators(11, checked = false, availableIds = listed(11))
            useCase.updateSelectedCommentators(11, emptySet(), listed(11))

            select(12)
            assertEquals(emptySet(), shownOn(12))
            select(10)
            assertEquals(emptySet(), shownOn(10))
        }

    @Test
    fun `a list does not uncheck the commentators remembered for its line that it does not show`() =
        runBlocking {
            select(21)
            check(21, sforno)
            select(22)
            check(22, chizkuni)

            // A selection of several lines reapplies their union to its primary line, whose list here shows its own
            useCase.reapplySelectedCommentatorsForLines(listOf(20L, 21L, 22L), 20L, bookId)
            useCase.updateSelectedCommentators(20, shownOn(20), listed(20))
            check(20, rashi)

            select(21)
            assertEquals(setOf(sforno), shownOn(21))
            select(22)
            assertEquals(setOf(chizkuni), shownOn(22))
        }

    @Test
    fun `all of them is kept with the tab`() =
        runBlocking {
            select(10)
            checkAll(10)
            select(11)
            uncheck(11, sforno)
            stateManager.saveAllStates()

            val restored = BookContentStateManager(tabId, store).state.value.content
            assertTrue(bookId in restored.allCommentatorsBooks)
            assertEquals(setOf(sforno), restored.excludedCommentatorsByBook[bookId])
        }
}
