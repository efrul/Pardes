package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetachedCommentatorsTest {
    @Test
    fun `detach appends the commentator once`() {
        assertEquals(listOf("רש\"י"), DetachedCommentators.detach(emptyList(), "רש\"י"))
        assertEquals(listOf("ספורנו", "רמב\"ן"), DetachedCommentators.detach(listOf("ספורנו"), "רמב\"ן"))
        assertEquals(listOf("א"), DetachedCommentators.detach(listOf("א"), "א"))
        assertEquals(listOf("א"), DetachedCommentators.detach(listOf("א"), " "))
    }

    @Test
    fun `detach stops at the cap`() {
        val full = (1..DetachedCommentators.MAX).map { "מפרש $it" }
        assertEquals(full, DetachedCommentators.detach(full, "עוד אחד"))
        assertFalse(DetachedCommentators.canDetach(full))
        assertTrue(DetachedCommentators.canDetach(full.drop(1)))
    }

    @Test
    fun `reattach removes the commentator and keeps the others in order`() {
        assertEquals(listOf("ב", "ג"), DetachedCommentators.reattach(listOf("א", "ב", "ג"), "א"))
        assertEquals(listOf("א", "ג"), DetachedCommentators.reattach(listOf("א", "ב", "ג"), "ב"))
        assertEquals(emptyList(), DetachedCommentators.reattach(listOf("ב"), "ב"))
        assertEquals(listOf("א"), DetachedCommentators.reattach(listOf("א"), "ז"))
    }

    @Test
    fun `a list saved by desktop 1_0_5 reads as its names`() {
        // 1.0.5 kept four slots, "" for a free one, trimmed at the last used one.
        assertEquals(listOf("ספורנו", "אור החיים"), DetachedCommentators.names(listOf("", "ספורנו", "", "אור החיים")))
        assertEquals(listOf("ב", "ג"), DetachedCommentators.detach(listOf("", "ב"), "ג"))
        assertEquals(listOf("ב"), DetachedCommentators.reattach(listOf("א", "", "ב"), "א"))
        assertTrue(DetachedCommentators.canDetach(listOf("", "", "", "ד")))
    }

    @Test
    fun `what is written is a plain list 1_0_5 reads as its first slots`() {
        val written = DetachedCommentators.detach(DetachedCommentators.detach(emptyList(), "א"), "ב")
        // No free slots in it: 1.0.5 gives "א" its first pane and "ב" its second.
        assertEquals(listOf("א", "ב"), written)
        assertTrue(written.none { it.isEmpty() })
    }
}
