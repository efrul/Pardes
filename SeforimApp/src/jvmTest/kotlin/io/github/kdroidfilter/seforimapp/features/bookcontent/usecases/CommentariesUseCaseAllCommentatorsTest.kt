package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.TestFactories
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.commentatorSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.listedCommentators
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.withCommentatorSelection
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
 * "כל המפרשים" and the groups' checkboxes from line to line, as the commentaries pane drives the use case: a line is
 * selected (reapplied), its list reports what it shows in its first frame, then the reader checks or unchecks.
 */
class CommentariesUseCaseAllCommentatorsTest {
    private val bookId = 100L
    private val rishonim = "ראשונים על התנ״ך"
    private val chasidut = "חסידות"
    private val rashi = 1L
    private val ramban = 2L
    private val sforno = 3L
    private val chizkuni = 4L
    private val noamElimelech = 5L
    private val kedushatLevi = 6L

    private val names =
        mapOf(
            rashi to "רש\"י",
            ramban to "רמב\"ן",
            sforno to "ספורנו",
            chizkuni to "חזקוני",
            noamElimelech to "נועם אלימלך",
            kedushatLevi to "קדושת לוי",
        )
    private val groupOf =
        mapOf(
            rashi to rishonim,
            ramban to rishonim,
            sforno to rishonim,
            chizkuni to rishonim,
            noamElimelech to chasidut,
            kedushatLevi to chasidut,
        )

    // Each line's commentators
    private val lines =
        mapOf(
            10L to listOf(rashi, ramban),
            11L to listOf(rashi, ramban, sforno, noamElimelech),
            12L to listOf(rashi, chizkuni, kedushatLevi),
            // A heading lists its own; its section's lines (21, 22) have others
            20L to listOf(rashi),
            21L to listOf(sforno),
            22L to listOf(chizkuni),
        )

    private val store = TabPersistedStateStore()
    private val tabId = "tab-all-commentators"
    private lateinit var scope: CoroutineScope
    private lateinit var stateManager: BookContentStateManager
    private lateinit var useCase: CommentariesUseCase

    private fun groupsOf(ids: List<Long>): List<CommentatorGroup> =
        ids
            .groupBy { groupOf.getValue(it) }
            .map { (label, members) -> CommentatorGroup(label, members.map { CommentatorItem(names.getValue(it), it) }) }

    @BeforeTest
    fun setup() {
        scope = CoroutineScope(SupervisorJob())
        stateManager = BookContentStateManager(tabId, store)
        stateManager.updateNavigation { copy(selectedBook = TestFactories.createBook(id = bookId)) }
        useCase = spyk(CommentariesUseCase(mockk(relaxed = true), stateManager, scope))
        coEvery { useCase.getCommentatorGroups(any()) } answers { groupsOf(lines[firstArg()].orEmpty()) }
        coEvery { useCase.getCommentatorGroupsForLines(any()) } answers {
            groupsOf(firstArg<List<Long>>().flatMap { lines[it].orEmpty() }.distinct())
        }
        coEvery { useCase.commentatorGroupOf(any()) } answers { groupOf[firstArg()].orEmpty() }
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun listed(lineId: Long) = groupsOf(lines.getValue(lineId)).listedCommentators()

    private val content get() = stateManager.state.value.content

    /** What the line's list shows checked, as LineCommentsView derives it. */
    private fun shownOn(lineId: Long): Set<Long> = content.commentatorSelection(bookId).shownOf(listed(lineId))

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

    /** A checkbox over several commentators clicked: the list checks or unchecks them, then reports the event. */
    private suspend fun clickMany(
        lineId: Long,
        checked: Boolean,
        ids: Set<Long>,
        event: suspend () -> Unit,
    ) {
        val shown = if (checked) shownOn(lineId) + ids else shownOn(lineId) - ids
        event()
        useCase.updateSelectedCommentators(lineId, shown, listed(lineId))
    }

    private suspend fun checkAll(lineId: Long) =
        clickMany(lineId, true, listed(lineId).keys) { useCase.setAllCommentators(lineId, true, listed(lineId)) }

    private suspend fun uncheckAll(lineId: Long) =
        clickMany(lineId, false, listed(lineId).keys) { useCase.setAllCommentators(lineId, false, listed(lineId)) }

    private suspend fun clickGroup(
        lineId: Long,
        group: String,
        checked: Boolean,
    ) = clickMany(lineId, checked, listed(lineId).filterValues { it == group }.keys) {
        useCase.setCommentatorGroup(lineId, group, checked, listed(lineId))
    }

    @Test
    fun `all of them checked shows on the next lines also the commentators the line did not have`() =
        runBlocking {
            select(10)
            check(10, rashi)
            checkAll(10)

            select(11)
            assertEquals(setOf(rashi, ramban, sforno, noamElimelech), shownOn(11))
            select(12)
            assertEquals(setOf(rashi, chizkuni, kedushatLevi), shownOn(12))
        }

    @Test
    fun `without whole groups, a commentator no line where one checked them had stays unchecked`() =
        runBlocking {
            // Each of the line's commentators checked one by one, as before
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
            assertEquals(setOf(rashi, chizkuni, kedushatLevi), shownOn(12))
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
            uncheckAll(11)

            select(12)
            assertEquals(emptySet(), shownOn(12))
            select(10)
            assertEquals(emptySet(), shownOn(10))
        }

    @Test
    fun `a group checked shows on the next lines all of its commentators, those the line did not have included`() =
        runBlocking {
            select(10)
            clickGroup(10, rishonim, checked = true)

            select(11)
            assertEquals(setOf(rashi, ramban, sforno), shownOn(11))
            select(12)
            assertEquals(setOf(rashi, chizkuni), shownOn(12))
        }

    @Test
    fun `a commentator unchecked in a checked group stays hidden, and the group's checkbox brings it back`() =
        runBlocking {
            select(10)
            clickGroup(10, rishonim, checked = true)
            select(11)
            uncheck(11, sforno)

            select(12)
            assertEquals(setOf(rashi, chizkuni), shownOn(12))
            select(11)
            assertEquals(setOf(rashi, ramban), shownOn(11))
            clickGroup(11, rishonim, checked = true)
            assertEquals(setOf(rashi, ramban, sforno), shownOn(11))
        }

    @Test
    fun `a group unchecked hides its commentators on every line, those checked one by one on other lines too`() =
        runBlocking {
            stateManager.updateContent {
                withCommentatorSelection(bookId, CommentatorSelection(remembered = setOf(kedushatLevi)))
            }
            select(12)
            check(12, chizkuni)
            select(10)
            clickGroup(10, rishonim, checked = true)
            select(11)
            clickGroup(11, rishonim, checked = false)

            // Chizkuni, checked on line 12, is not on line 11: his group is found for him
            select(12)
            assertEquals(setOf(kedushatLevi), shownOn(12))
            select(10)
            assertEquals(emptySet(), shownOn(10))
        }

    @Test
    fun `a group unchecked while all of them are shown is hidden, the others shown`() =
        runBlocking {
            select(11)
            checkAll(11)
            clickGroup(11, chasidut, checked = false)

            select(12)
            assertEquals(setOf(rashi, chizkuni), shownOn(12))
            clickGroup(12, chasidut, checked = true)
            assertEquals(setOf(rashi, chizkuni, kedushatLevi), shownOn(12))
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
    fun `whole groups are kept with the tab`() =
        runBlocking {
            select(10)
            checkAll(10)
            select(11)
            uncheck(11, sforno)
            clickGroup(11, chasidut, checked = false)
            stateManager.saveAllStates()

            val restored = BookContentStateManager(tabId, store).state.value.content
            assertTrue(bookId in restored.allCommentatorsBooks)
            assertEquals(setOf(sforno), restored.excludedCommentatorsByBook[bookId])
            assertEquals(setOf(chasidut), restored.hiddenCommentatorGroupsByBook[bookId])
        }
}
