package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetachedCommentatorsTest {
    @Test
    fun `detach takes the first free slot`() {
        assertEquals(listOf("רש\"י"), DetachedCommentators.detach(emptyList(), "רש\"י"))
        assertEquals(listOf("רמב\"ן", "ספורנו"), DetachedCommentators.detach(listOf("", "ספורנו"), "רמב\"ן"))
    }

    @Test
    fun `detach keeps a commentator already detached and stops at the last slot`() {
        val full = listOf("א", "ב", "ג", "ד")
        assertEquals(full, DetachedCommentators.detach(full, "ה"))
        assertEquals(listOf("א"), DetachedCommentators.detach(listOf("א"), "א"))
        assertEquals(listOf("א"), DetachedCommentators.detach(listOf("א"), " "))
    }

    @Test
    fun `reattach frees its slot and leaves the others where they are`() {
        assertEquals(listOf("", "ב"), DetachedCommentators.reattach(listOf("א", "ב"), "א"))
        assertEquals(listOf("א"), DetachedCommentators.reattach(listOf("א", "ב"), "ב"))
        assertEquals(emptyList(), DetachedCommentators.reattach(listOf("", "ב"), "ב"))
    }

    @Test
    fun `clear frees a slot closed from the dock`() {
        assertEquals(listOf("א", "", "ג"), DetachedCommentators.clear(listOf("א", "ב", "ג"), 1))
        assertEquals(listOf("א"), DetachedCommentators.clear(listOf("א", "ב"), 1))
        assertEquals(listOf("א"), DetachedCommentators.clear(listOf("א"), 3))
    }

    @Test
    fun `canDetach counts the slots in use`() {
        assertTrue(DetachedCommentators.canDetach(listOf("", "", "", "ד")))
        assertFalse(DetachedCommentators.canDetach(listOf("א", "ב", "ג", "ד")))
    }
}
