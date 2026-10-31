package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes

import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentatorPaneIdTest {
    @Test
    fun `a commentator's pane id is plain and gives the name back`() {
        for (name in listOf("רש\"י", "אבן עזרא", "ר' חיים בן עטר (אור החיים)", "Rashi")) {
            val id = commentatorPaneId(name)
            assertTrue(id.matches(Regex("commentator-[0-9a-f]+")), id)
            assertEquals(name, commentatorNameOf(id))
        }
        assertNotEquals(commentatorPaneId("רש\"י"), commentatorPaneId("רשב\"ם"))
    }

    @Test
    fun `the other panes' ids are no commentator's`() {
        for (pane in ReaderPane.standard) assertNull(commentatorNameOf(pane.id), pane.id)
        assertNull(commentatorNameOf("commentator-"))
        assertNull(commentatorNameOf("commentator-abc"))
        assertNull(commentatorNameOf("commentator-zz"))
    }

    @Test
    fun `a satellite id of the window resolves to its commentator pane`() {
        val pane = ReaderPane.Commentator("רמב\"ן")
        assertEquals(pane, ReaderPane.commentatorOf("w1", pane.idIn("w1")))
        assertNull(ReaderPane.commentatorOf("w1", ReaderPane.Comments.idIn("w1")))
        assertNull(ReaderPane.commentatorOf("w2", pane.idIn("w1")), "another window's satellite")
    }

    @Test
    fun `the dock opening or closing a commentator's pane is followed on the tab`() {
        val pane = ReaderPane.Commentator("ספורנו")
        assertEquals(BookContentEvent.DetachCommentator("ספורנו"), pane.followEvent(opened = true))
        assertEquals(BookContentEvent.CloseCommentatorPane("ספורנו"), pane.followEvent(opened = false))
        assertEquals(ReaderPane.Notes.toggle, ReaderPane.Notes.followEvent(opened = true))
    }

    @Test
    fun `commentator panes spread around the text by default`() {
        assertEquals(
            listOf("Left", "Right", "Bottom", "Bottom"),
            (0..3).map { commentatorHome(it).side.name },
        )
    }
}
