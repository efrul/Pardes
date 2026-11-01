package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentatorSelectionTest {
    private val rashi = 1L
    private val ramban = 2L
    private val sforno = 3L
    private val chizkuni = 4L
    private val orHachaim = 5L

    @Test
    fun `a list shows on a line only the commentators remembered`() {
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno))

        assertEquals(setOf(rashi, sforno), selection.shownOf(listOf(rashi, ramban, sforno, chizkuni)))
    }

    @Test
    fun `all of them shows on every line all its commentators, also those no line before had`() {
        val selection = CommentatorSelection().allChecked(listOf(rashi, ramban))

        assertEquals(setOf(rashi, ramban, sforno, chizkuni), selection.shownOf(listOf(rashi, ramban, sforno, chizkuni)))
        assertEquals(setOf(orHachaim), selection.shownOf(listOf(orHachaim)))
    }

    @Test
    fun `a commentator unchecked while all of them are shown stays hidden on the next lines, the others shown`() {
        val line1 = setOf(rashi, ramban)
        val selection = CommentatorSelection().allChecked(line1)

        val unchecked = selection.changed(previous = selection.shownOf(line1), selected = setOf(rashi))

        assertTrue(unchecked.all)
        assertEquals(setOf(rashi, sforno), unchecked.shownOf(listOf(rashi, ramban, sforno)))
    }

    @Test
    fun `a commentator checked again while all of them are shown is shown again`() {
        val line = setOf(rashi, ramban)
        val selection = CommentatorSelection(all = true, excluded = setOf(ramban))

        val checked = selection.changed(previous = selection.shownOf(line), selected = line)

        assertEquals(emptySet(), checked.excluded)
        assertEquals(setOf(rashi, ramban, sforno), checked.shownOf(listOf(rashi, ramban, sforno)))
    }

    @Test
    fun `unchecking all of them after they were shown leaves none on any line`() {
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno)).allChecked(listOf(rashi, ramban))

        val none = selection.allUnchecked(listOf(rashi, ramban))

        assertFalse(none.all)
        assertEquals(emptySet(), none.shownOf(listOf(rashi, ramban, sforno, chizkuni)))
    }

    @Test
    fun `unchecking all of a line whose own were all checked by chance keeps the list's others`() {
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno))

        val unchecked = selection.allUnchecked(listOf(rashi))

        assertEquals(setOf(sforno), unchecked.remembered)
    }

    @Test
    fun `a change on a line where none of the list was checked keeps the list`() {
        // The line showed none checked: checking one of its commentators adds it, the others stay remembered
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno))
        val line = listOf(orHachaim)

        val checked = selection.changed(previous = selection.shownOf(line), selected = setOf(orHachaim))

        assertEquals(setOf(rashi, sforno, orHachaim), checked.remembered)
    }

    @Test
    fun `a list checked line by line still leaves out a commentator no such line had`() {
        // As before "כל המפרשים" was kept: each line adds only its own
        val line1 = listOf(rashi, ramban)
        val selection = CommentatorSelection(remembered = setOf(rashi))
        val afterLine1 = selection.changed(previous = selection.shownOf(line1), selected = line1.toSet())

        assertEquals(setOf(rashi, ramban), afterLine1.shownOf(listOf(rashi, ramban, chizkuni)))
    }

    @Test
    fun `an unchanged list is the same selection`() {
        val selection = CommentatorSelection(remembered = setOf(rashi))

        assertEquals(selection, selection.changed(previous = setOf(rashi), selected = setOf(rashi)))
    }

    @Test
    fun `a tab's content keeps each book's selection apart`() {
        val selection = CommentatorSelection(remembered = setOf(rashi), all = true, excluded = setOf(ramban))
        val content = ContentState().withCommentatorSelection(10L, selection)

        assertEquals(selection, content.commentatorSelection(10L))
        assertEquals(CommentatorSelection(), content.commentatorSelection(11L))
        // Back to none: nothing is left of the book in the content
        val cleared = content.withCommentatorSelection(10L, CommentatorSelection())
        assertEquals(emptyMap(), cleared.selectedCommentatorsByBook)
        assertEquals(emptySet(), cleared.allCommentatorsBooks)
        assertEquals(emptyMap(), cleared.excludedCommentatorsByBook)
    }
}
