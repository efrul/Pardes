package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Chrome's tab shortcuts in the strip's order as read (request 47). Four tabs, index 0 the strip's left end: read from
 * the right, they are 3, 2, 1, 0.
 */
class TabKeysTest {
    private fun action(
        key: Key,
        shift: Boolean = false,
        current: Int = 2,
        rtl: Boolean = true,
    ) = tabKeyAction(key, shift, current, count = 4, rtl = rtl)

    @Test
    fun `a digit selects the tab at that place as read, nine the last`() {
        assertEquals(TabKeyAction.Select(3), action(Key.One))
        assertEquals(TabKeyAction.Select(1), action(Key.Three))
        assertEquals(TabKeyAction.Select(0), action(Key.Nine))
        assertNull(action(Key.Five))
    }

    @Test
    fun `tab and page down go on to the left, round the strip`() {
        assertEquals(TabKeyAction.Select(1), action(Key.Tab))
        assertEquals(TabKeyAction.Select(1), action(Key.PageDown))
        assertEquals(TabKeyAction.Select(3), action(Key.Tab, current = 0))
    }

    @Test
    fun `shift tab and page up go back to the right`() {
        assertEquals(TabKeyAction.Select(3), action(Key.Tab, shift = true))
        assertEquals(TabKeyAction.Select(3), action(Key.PageUp))
        assertEquals(TabKeyAction.Select(0), action(Key.PageUp, current = 3))
    }

    @Test
    fun `shift and page down or up move the tab one place, not round`() {
        assertEquals(TabKeyAction.Move(1), action(Key.PageDown, shift = true))
        assertEquals(TabKeyAction.Move(3), action(Key.PageUp, shift = true))
        assertNull(action(Key.PageDown, shift = true, current = 0))
        assertNull(action(Key.PageUp, shift = true, current = 3))
    }

    @Test
    fun `left to right, the order is the index's`() {
        assertEquals(TabKeyAction.Select(0), action(Key.One, rtl = false))
        assertEquals(TabKeyAction.Select(3), action(Key.Tab, rtl = false))
        assertNull(tabKeyAction(Key.Tab, shift = false, current = 0, count = 0, rtl = true))
        assertNull(action(Key.A))
    }
}
