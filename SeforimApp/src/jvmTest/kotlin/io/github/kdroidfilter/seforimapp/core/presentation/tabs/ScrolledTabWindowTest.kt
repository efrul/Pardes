package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The wheel over the strip moves the tabs on show, and the selected tab stays selected (request 83 of stage 3). */
@OptIn(ExperimentalTestApi::class)
class ScrolledTabWindowTest {
    private val tabs = ('a'..'j').map(Char::toString)

    @Test
    fun `without the wheel the run keeps to the selected tab, as Zayit's`() {
        val zayit = TabWindow()
        val window = ScrolledTabWindow()
        // The strip narrower and wider, the selection here and there.
        listOf(3 to 0, 3 to 5, 3 to 9, 4 to 2, 2 to 2, 3 to -1, 3 to 4).forEach { (count, selected) ->
            assertEquals(zayit.follow(tabs, count, selected), window.follow(tabs, count, selected))
        }
    }

    @Test
    fun `the wheel moves the run and leaves the selected tab out of it`() {
        val window = ScrolledTabWindow()
        assertEquals(listOf("a", "b", "c"), window.follow(tabs, 3, 0))
        assertTrue(window.scroll(2))
        assertEquals(listOf("c", "d", "e"), window.follow(tabs, 3, 0))
        assertTrue(window.scroll(1))
        // The selected tab, a, is out of the run, and the run stays.
        assertEquals(listOf("d", "e", "f"), window.follow(tabs, 3, 0))
        assertEquals(2, window.steps)
    }

    @Test
    fun `the run stops at the strip's ends`() {
        val window = ScrolledTabWindow()
        window.follow(tabs, 3, 0)
        assertFalse(window.scroll(-1))
        assertTrue(window.scroll(50))
        assertEquals(listOf("h", "i", "j"), window.follow(tabs, 3, 0))
        assertFalse(window.scroll(1))
        assertTrue(window.scroll(-1))
        assertEquals(listOf("g", "h", "i"), window.follow(tabs, 3, 0))
    }

    @Test
    fun `another tab selected brings the run back to it`() {
        val window = ScrolledTabWindow()
        window.follow(tabs, 3, 0)
        window.scroll(6)
        assertEquals(listOf("g", "h", "i"), window.follow(tabs, 3, 0))
        assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 1))
        // And it keeps to it again while the strip narrows.
        assertEquals(listOf("b", "c"), window.follow(tabs, 2, 1))
    }

    @Test
    fun `a closed tab leaves the run inside the strip`() {
        val window = ScrolledTabWindow()
        window.follow(tabs, 3, 0)
        window.scroll(7)
        assertEquals(listOf("g", "h", "i"), window.follow(tabs.dropLast(1), 3, 0))
    }

    @Test
    fun `a notch of the wheel is a tab, down for on and up for back`() =
        runDesktopComposeUiTest(width = 400, height = 60) {
            val window = ScrolledTabWindow()
            window.follow(tabs, 3, 0)
            setContent { Box(Modifier.size(400.dp, 40.dp).tabStripWheel(window, enabled = true, rtl = true).testTag("strip")) }
            onNodeWithTag("strip").performMouseInput { scroll(2f) }
            assertEquals(listOf("c", "d", "e"), window.follow(tabs, 3, 0))
            onNodeWithTag("strip").performMouseInput { scroll(-1f) }
            assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 0))
        }

    @Test
    fun `a sideways turn follows the strip's direction, and two halves of a notch make one`() =
        runDesktopComposeUiTest(width = 400, height = 60) {
            val window = ScrolledTabWindow()
            window.follow(tabs, 3, 0)
            setContent { Box(Modifier.size(400.dp, 40.dp).tabStripWheel(window, enabled = true, rtl = true).testTag("strip")) }
            // Read from the right, the strip's later tabs are to the left.
            onNodeWithTag("strip").performMouseInput { scroll(-1f, ScrollWheel.Horizontal) }
            assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 0))
            onNodeWithTag("strip").performMouseInput { scroll(0.5f) }
            assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 0))
            onNodeWithTag("strip").performMouseInput { scroll(0.5f) }
            assertEquals(listOf("c", "d", "e"), window.follow(tabs, 3, 0))
        }

    @Test
    fun `the wheel does nothing while every tab is on show`() =
        runDesktopComposeUiTest(width = 400, height = 60) {
            val window = ScrolledTabWindow()
            window.follow(tabs, 3, 0)
            setContent { Box(Modifier.size(400.dp, 40.dp).tabStripWheel(window, enabled = false, rtl = true).testTag("strip")) }
            onNodeWithTag("strip").performMouseInput { scroll(2f) }
            assertEquals(0, window.steps)
        }
}
