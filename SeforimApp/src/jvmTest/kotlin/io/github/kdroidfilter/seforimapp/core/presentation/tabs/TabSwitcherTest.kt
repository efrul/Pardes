package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The tabs' list of Ctrl+Tab: a tab marked from a press of Tab on while Ctrl stays down, selected when it is let go. */
class TabSwitcherTest {
    @Test
    fun `Ctrl+Tab marks a tab, and the list stays while Ctrl is held`() {
        assertEquals(2, tabSwitcherMark(mark = null, held = true, next = 2), "Ctrl+Tab")
        assertEquals(3, tabSwitcherMark(mark = 2, held = true, next = 3), "Tab again")
        assertEquals(3, tabSwitcherMark(mark = 3, held = true, next = null), "Tab let go, or Shift pressed")
        assertNull(tabSwitcherMark(mark = 3, held = false, next = null), "Ctrl let go: the tab marked is selected")
    }

    @Test
    fun `another key with Ctrl lists nothing, and a key without it lets a list go`() {
        assertNull(tabSwitcherMark(mark = null, held = true, next = null), "Ctrl+W")
        assertNull(tabSwitcherMark(mark = null, held = false, next = null), "Tab alone")
        assertNull(tabSwitcherMark(mark = 1, held = false, next = null), "a let go that was missed")
    }
}
