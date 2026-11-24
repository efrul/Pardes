package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tabs' list of Ctrl+Tab: shown from a press of Tab on while Ctrl stays down. */
class TabSwitcherTest {
    @Test
    fun `the tabs are listed from Ctrl+Tab until Ctrl is let go`() {
        assertTrue(tabSwitcherShown(shown = false, held = true, tabPressed = true), "Ctrl+Tab")
        assertTrue(tabSwitcherShown(shown = true, held = true, tabPressed = true), "Tab again")
        assertTrue(tabSwitcherShown(shown = true, held = true, tabPressed = false), "Tab let go, or Shift pressed")
        assertFalse(tabSwitcherShown(shown = true, held = false, tabPressed = false), "Ctrl let go")
    }

    @Test
    fun `another key with Ctrl lists nothing, and a key without it hides a list left open`() {
        assertFalse(tabSwitcherShown(shown = false, held = true, tabPressed = false), "Ctrl+W")
        assertFalse(tabSwitcherShown(shown = false, held = false, tabPressed = true), "Tab alone")
        assertFalse(tabSwitcherShown(shown = true, held = false, tabPressed = true), "a let go that was missed")
    }
}
