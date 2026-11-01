package io.github.kdroidfilter.seforimapp.android.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pardes desktop's CommentatorSelectionTest on the port, with the reader's own case: a line with none of the book's. */
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
    fun `unchecking all of them after they were shown leaves none on any line`() {
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno)).allChecked(listOf(rashi, ramban))

        val none = selection.allUnchecked(listOf(rashi, ramban))

        assertFalse(none.all)
        assertEquals(emptySet(), none.shownOf(listOf(rashi, ramban, sforno, chizkuni)))
    }

    @Test
    fun `checking on a line where none of the book's commentators is listed keeps the book's`() {
        // The reader used to take the whole remembered set as this line's previous selection, and forgot it all
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno))
        val line = listOf(orHachaim)

        val checked = selection.changed(previous = selection.shownOf(line), selected = setOf(orHachaim))
        val all = selection.allChecked(line)

        assertEquals(setOf(rashi, sforno, orHachaim), checked.remembered)
        assertEquals(setOf(rashi, ramban, sforno), all.shownOf(listOf(rashi, ramban, sforno)))
    }

    @Test
    fun `unchecking all of a line whose own were all checked by chance keeps the list's others`() {
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno))

        assertEquals(setOf(sforno), selection.allUnchecked(listOf(rashi)).remembered)
    }
}
