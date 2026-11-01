package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentatorSelectionTest {
    private val rishonim = "ראשונים על התנ״ך"
    private val chasidut = "חסידות"

    private val rashi = 1L
    private val ramban = 2L
    private val sforno = 3L
    private val chizkuni = 4L
    private val orHachaim = 5L
    private val noamElimelech = 6L
    private val kedushatLevi = 7L

    private val groupOf =
        mapOf(
            rashi to rishonim,
            ramban to rishonim,
            sforno to rishonim,
            chizkuni to rishonim,
            orHachaim to "אחרונים על התנ״ך",
            noamElimelech to chasidut,
            kedushatLevi to chasidut,
        )

    /** A line's list: [ids] with their groups. */
    private fun line(vararg ids: Long): Map<Long, String> = ids.associateWith { groupOf.getValue(it) }

    @Test
    fun `a list shows on a line only the commentators remembered`() {
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno))

        assertEquals(setOf(rashi, sforno), selection.shownOf(line(rashi, ramban, sforno, chizkuni)))
    }

    @Test
    fun `all of them shows on every line all its commentators, also those no line before had`() {
        val selection = CommentatorSelection().allChecked()

        assertEquals(setOf(rashi, ramban, sforno, chizkuni), selection.shownOf(line(rashi, ramban, sforno, chizkuni)))
        assertEquals(setOf(orHachaim), selection.shownOf(line(orHachaim)))
    }

    @Test
    fun `a commentator unchecked while all of them are shown stays hidden on the next lines, the others shown`() {
        val line1 = line(rashi, ramban)
        val selection = CommentatorSelection().allChecked()

        val unchecked = selection.changed(previous = selection.shownOf(line1), selected = setOf(rashi), listed = line1)

        assertTrue(unchecked.all)
        assertEquals(setOf(rashi, sforno), unchecked.shownOf(line(rashi, ramban, sforno)))
    }

    @Test
    fun `a commentator checked again while all of them are shown is shown again`() {
        val line = line(rashi, ramban)
        val selection = CommentatorSelection(all = true, excluded = setOf(ramban))

        val checked = selection.changed(previous = selection.shownOf(line), selected = line.keys, listed = line)

        assertEquals(emptySet(), checked.excluded)
        assertEquals(setOf(rashi, ramban, sforno), checked.shownOf(line(rashi, ramban, sforno)))
    }

    @Test
    fun `unchecking all of them leaves none on any line`() {
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno), groups = setOf(chasidut)).allChecked()

        val none = selection.allUnchecked()

        assertFalse(none.all)
        assertTrue(none.isEmpty)
        assertEquals(emptySet(), none.shownOf(line(rashi, ramban, sforno, noamElimelech)))
    }

    @Test
    fun `a group checked shows on every line all of its commentators, also those no line before had`() {
        val selection = CommentatorSelection(remembered = setOf(orHachaim)).groupChecked(rishonim, listOf(rashi, ramban))

        // Chizkuni was not on the line the group was checked on; the other groups keep to the list
        assertEquals(setOf(rashi, sforno, chizkuni, orHachaim), selection.shownOf(line(rashi, sforno, chizkuni, orHachaim, noamElimelech)))
    }

    @Test
    fun `a commentator unchecked in a checked group stays hidden, and checking the group brings it back`() {
        val line1 = line(rashi, ramban)
        val selection = CommentatorSelection().groupChecked(rishonim, line1.keys)
        val unchecked = selection.changed(previous = selection.shownOf(line1), selected = setOf(rashi), listed = line1)

        assertEquals(setOf(rashi, sforno), unchecked.shownOf(line(rashi, ramban, sforno)))
        // Checked again from a line without him: the caller adds the group's commentators it knows elsewhere
        val again = unchecked.groupChecked(rishonim, listOf(sforno, ramban))
        assertEquals(setOf(rashi, ramban, sforno), again.shownOf(line(rashi, ramban, sforno)))
    }

    @Test
    fun `a group unchecked hides all of its commentators on every line, those checked one by one too`() {
        val selection = CommentatorSelection(remembered = setOf(rashi, orHachaim)).groupChecked(rishonim, listOf(ramban))

        // Rashi, checked one by one before, is one of the group's commentators the caller knows
        val unchecked = selection.groupUnchecked(rishonim, listOf(ramban, rashi))

        assertEquals(setOf(orHachaim), unchecked.shownOf(line(rashi, ramban, sforno, orHachaim)))
        assertEquals(emptySet(), unchecked.groups)
    }

    @Test
    fun `a group unchecked while all of them are shown is hidden, the others shown`() {
        val selection = CommentatorSelection().allChecked().groupUnchecked(chasidut, listOf(noamElimelech))

        assertEquals(setOf(rashi, orHachaim), selection.shownOf(line(rashi, orHachaim, noamElimelech, kedushatLevi)))
        // Checked again, it is shown again; all of them again, too
        val chasidutLine = line(noamElimelech, kedushatLevi)
        assertEquals(chasidutLine.keys, selection.groupChecked(chasidut, emptyList()).shownOf(chasidutLine))
        assertEquals(chasidutLine.keys, selection.allChecked().shownOf(chasidutLine))
    }

    @Test
    fun `a commentator checked one by one in a hidden group is shown`() {
        val line = line(noamElimelech, kedushatLevi)
        val selection = CommentatorSelection().allChecked().groupUnchecked(chasidut, line.keys)

        val checked = selection.changed(previous = selection.shownOf(line), selected = setOf(kedushatLevi), listed = line)

        assertEquals(setOf(kedushatLevi), checked.shownOf(line))
    }

    @Test
    fun `a change on a line where none of the list was checked keeps the list`() {
        // The line showed none checked: checking one of its commentators adds it, the others stay remembered
        val selection = CommentatorSelection(remembered = setOf(rashi, sforno))
        val line = line(orHachaim)

        val checked = selection.changed(previous = selection.shownOf(line), selected = setOf(orHachaim), listed = line)

        assertEquals(setOf(rashi, sforno, orHachaim), checked.remembered)
    }

    @Test
    fun `a list checked line by line still leaves out a commentator no such line had`() {
        // Without whole groups: each line adds only its own
        val line1 = line(rashi, ramban)
        val selection = CommentatorSelection(remembered = setOf(rashi))
        val afterLine1 = selection.changed(previous = selection.shownOf(line1), selected = line1.keys, listed = line1)

        assertEquals(setOf(rashi, ramban), afterLine1.shownOf(line(rashi, ramban, chizkuni)))
    }

    @Test
    fun `an unchanged list is the same selection`() {
        val selection = CommentatorSelection(remembered = setOf(rashi))

        assertEquals(selection, selection.changed(previous = setOf(rashi), selected = setOf(rashi), listed = line(rashi)))
    }

    @Test
    fun `a line's list reads each commentator with its group, the first group listing him`() {
        val groups =
            listOf(
                CommentatorGroup(rishonim, listOf(CommentatorItem("רש\"י", rashi), CommentatorItem("רמב\"ן", ramban))),
                CommentatorGroup(chasidut, listOf(CommentatorItem("נועם אלימלך", noamElimelech), CommentatorItem("רש\"י", rashi))),
            )

        assertEquals(mapOf(rashi to rishonim, ramban to rishonim, noamElimelech to chasidut), groups.listedCommentators())
    }

    @Test
    fun `a tab's content keeps each book's selection apart`() {
        val selection =
            CommentatorSelection(
                remembered = setOf(rashi),
                all = true,
                groups = setOf(rishonim),
                hiddenGroups = setOf(chasidut),
                excluded = setOf(ramban),
            )
        val content = ContentState().withCommentatorSelection(10L, selection)

        assertEquals(selection, content.commentatorSelection(10L))
        assertEquals(CommentatorSelection(), content.commentatorSelection(11L))
        // Back to none: nothing is left of the book in the content
        val cleared = content.withCommentatorSelection(10L, CommentatorSelection())
        assertEquals(emptyMap(), cleared.selectedCommentatorsByBook)
        assertEquals(emptySet(), cleared.allCommentatorsBooks)
        assertEquals(emptyMap(), cleared.commentatorGroupsByBook)
        assertEquals(emptyMap(), cleared.hiddenCommentatorGroupsByBook)
        assertEquals(emptyMap(), cleared.excludedCommentatorsByBook)
    }
}
