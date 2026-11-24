package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The parts of a window the focus keys stop at: which one Shift with an arrow goes to, and F6's round of them. */
class FocusStopsTest {
    // A right-to-left reader: the book tree at the right, the contents beside it, the text at their left
    private val tree = Rect(1200f, 40f, 1440f, 900f)
    private val contents = Rect(1000f, 40f, 1200f, 900f)
    private val text = Rect(60f, 40f, 1000f, 900f)
    private val parts = listOf(text, tree, contents)

    @Test
    fun `Shift with an arrow goes to the nearest part on that side`() {
        assertEquals(2, stopBeside(parts, from = 0, toLeft = false), "from the text to the contents, not past them to the tree")
        assertEquals(1, stopBeside(parts, from = 2, toLeft = false))
        assertNull(stopBeside(parts, from = 1, toLeft = false), "nothing right of the tree")
        assertEquals(2, stopBeside(parts, from = 1, toLeft = true))
        assertEquals(0, stopBeside(parts, from = 2, toLeft = true))
        assertNull(stopBeside(parts, from = 0, toLeft = true))
        assertNull(stopBeside(parts, from = -1, toLeft = true), "from no part")
    }

    @Test
    fun `of two parts as near the one more level is taken`() {
        val above = Rect(1000f, 40f, 1200f, 300f)
        val below = Rect(1000f, 300f, 1200f, 900f)
        assertEquals(2, stopBeside(listOf(text, above, below), from = 0, toLeft = false), "the text's middle is beside the lower one")
    }

    @Test
    fun `F6 goes round the parts as they are read, and Shift goes back`() {
        assertEquals(1, stopAfter(parts, from = -1, forward = true, rtl = true), "from none, the first read: the tree at the right")
        assertEquals(2, stopAfter(parts, from = 1, forward = true, rtl = true))
        assertEquals(0, stopAfter(parts, from = 2, forward = true, rtl = true))
        assertEquals(1, stopAfter(parts, from = 0, forward = true, rtl = true), "round to the first")
        assertEquals(0, stopAfter(parts, from = 1, forward = false, rtl = true), "Shift+F6, back round")
        assertEquals(0, stopAfter(parts, from = -1, forward = true, rtl = false), "from the left in a left-to-right window")
        assertNull(stopAfter(emptyList(), from = -1, forward = true, rtl = true))
    }
}
