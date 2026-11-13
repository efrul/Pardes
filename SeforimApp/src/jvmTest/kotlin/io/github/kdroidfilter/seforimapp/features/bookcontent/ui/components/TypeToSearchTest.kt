package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A character typed with the pointer over a side pane goes to its search ([TypeToSearch]). */
class TypeToSearchTest {
    @AfterTest
    fun tearDown() = TypeToSearch.point("w1", null)

    @Test
    fun `a letter or digit is typed into the search, a shortcut or another key is not`() {
        assertEquals("א", TypeToSearch.textOf('א'.code, ctrl = false, alt = false, meta = false))
        assertEquals("b", TypeToSearch.textOf('b'.code, ctrl = false, alt = false, meta = false))
        assertEquals("7", TypeToSearch.textOf('7'.code, ctrl = false, alt = false, meta = false))
        assertNull(TypeToSearch.textOf('a'.code, ctrl = true, alt = false, meta = false))
        assertNull(TypeToSearch.textOf('a'.code, ctrl = false, alt = true, meta = false))
        assertNull(TypeToSearch.textOf('a'.code, ctrl = false, alt = false, meta = true))
        assertNull(TypeToSearch.textOf(' '.code, ctrl = false, alt = false, meta = false))
        assertNull(TypeToSearch.textOf('.'.code, ctrl = false, alt = false, meta = false))
        assertNull(TypeToSearch.textOf(0, ctrl = false, alt = false, meta = false))
    }

    @Test
    fun `the text goes to the pane under the pointer in its own window only`() {
        val typed = mutableListOf<String>()
        assertFalse(TypeToSearch.route("א", "w1"), "no pane under the pointer")

        TypeToSearch.point("w1") { typed += it }
        assertTrue(TypeToSearch.route("א", "w1"))
        assertFalse(TypeToSearch.route("ב", "w2"), "typed in another window")
        assertFalse(TypeToSearch.route(null, "w1"), "not a letter or digit")
        TypeToSearch.point("w1", null)
        assertFalse(TypeToSearch.route("ג", "w1"), "the pointer left the pane")

        assertEquals(listOf("א"), typed)
    }

    @Test
    fun `typing opens a closed search, and goes on in an open one`() {
        val focus = PaneSearchFocus()
        var opened = 0

        focus.type("א", isOpen = false) { opened++ }
        focus.type("ב", isOpen = true) { opened++ }

        assertEquals(1, opened)
        assertEquals("אב", focus.typed)
        assertTrue(focus.requested)
    }
}
