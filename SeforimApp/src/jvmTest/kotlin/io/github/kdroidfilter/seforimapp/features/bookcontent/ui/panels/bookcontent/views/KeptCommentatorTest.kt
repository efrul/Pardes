package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeptCommentatorTest {
    private var clock = 0L
    private val kept = KeptCommentator { clock }
    private val all = listOf("רש״י", "רשב״א", "ריטב״א")

    @Test
    fun `the page the reader turns to is his choice, the page the pane leaves is not`() {
        kept.turns()
        assertEquals("רשב״א", kept.settledOn("רשב״א", all, focus = null))
        assertEquals("רשב״א", kept.name)

        // A line he is not on: the pages settle on another, he stays the reader's
        val without = listOf("רש״י", "ריטב״א")
        assertEquals("ריטב״א", kept.settledOn("ריטב״א", without, focus = null))
        assertNull(kept.among(without))
        assertEquals("רשב״א", kept.name)
        // The next line he is on: the pages follow him again, wherever they settled
        assertEquals("רשב״א", kept.settledOn("רש״י", all, focus = null))
    }

    @Test
    fun `a passage's commentator is followed without replacing the reader's`() {
        kept.chose("רשב״א")

        assertEquals("רש״י", kept.settledOn("רש״י", all, focus = "רש״י"))
        assertEquals("רשב״א", kept.name)
        // The reader turns the page on a passage: his choice all the same
        kept.turns()
        assertEquals("ריטב״א", kept.settledOn("ריטב״א", all, focus = "רש״י"))
        assertEquals("ריטב״א", kept.name)
    }

    @Test
    fun `a turn that never settled is forgotten`() {
        kept.chose("רשב״א")
        kept.turns()
        clock += 10_000

        assertEquals("רשב״א", kept.settledOn("רש״י", all, focus = null))
        assertEquals("רשב״א", kept.name)
    }

    @Test
    fun `a commentator ticked is told from one who comes back with a line`() {
        kept.listedOn(line = 1)
        assertTrue(kept.tickedOn(line = 1))
        assertFalse(kept.tickedOn(line = 2))
        // The same commentators on the next line, then one ticked there
        kept.listedOn(line = 3)
        assertTrue(kept.tickedOn(line = 3))
    }
}
