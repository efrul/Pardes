package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A scroll over the strip moves the tabs on show as far as it goes, a tab cut at each end of them on its way, and leaves
 * them on a whole tab; the selected tab stays selected (request 83 of stage 3).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ScrolledTabWindowTest {
    private val tabs = ('a'..'j').map(Char::toString)

    private fun scrolling() = ScrolledTabWindow().apply { scrolls = true }

    @Test
    fun `without a scroll the run keeps to the selected tab, as Zayit's`() {
        val zayit = TabWindow()
        val window = scrolling()
        // The strip narrower and wider, the selection here and there.
        listOf(3 to 0, 3 to 5, 3 to 9, 4 to 2, 2 to 2, 3 to -1, 3 to 4).forEach { (count, selected) ->
            assertEquals(zayit.follow(tabs, count, selected), window.follow(tabs, count, selected))
            assertFalse(window.between)
        }
    }

    @Test
    fun `a scroll moves the run by a part of a tab, and the tab after the run comes in`() {
        val window = scrolling()
        assertEquals(listOf("a", "b", "c"), window.follow(tabs, 3, 0))
        assertFalse(window.arrives("d"))
        assertTrue(window.scroll(0.25f))
        assertTrue(window.between)
        assertEquals(0.25f, window.fraction)
        // The run is the same three: d comes in, still one of the strip's hidden tabs.
        assertEquals(listOf("a", "b", "c"), window.follow(tabs, 3, 0))
        assertTrue(window.arrives("d"))
        assertTrue(window.startsWith("a"))
        assertTrue(window.moves("d"))
        assertFalse(window.moves("e"))
        assertTrue(window.scroll(0.75f))
        // On a whole tab the strip is as at rest, the selected tab, a, out of the run.
        assertFalse(window.between)
        assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 0))
        assertFalse(window.arrives("e"))
    }

    @Test
    fun `the run stops at the strip's ends`() {
        val window = scrolling()
        window.follow(tabs, 3, 0)
        assertFalse(window.scroll(-1f))
        assertTrue(window.scroll(50f))
        assertEquals(listOf("h", "i", "j"), window.follow(tabs, 3, 0))
        assertFalse(window.between)
        assertFalse(window.scroll(0.5f))
        assertTrue(window.scroll(-1f))
        assertEquals(listOf("g", "h", "i"), window.follow(tabs, 3, 0))
    }

    @Test
    fun `another tab selected brings the run back to it, whole`() {
        val window = scrolling()
        window.follow(tabs, 3, 0)
        window.scroll(5.5f)
        assertEquals(listOf("f", "g", "h"), window.follow(tabs, 3, 0))
        assertTrue(window.arrives("i"))
        assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 1))
        assertFalse(window.between)
        // And it keeps to it again while the strip narrows.
        assertEquals(listOf("b", "c"), window.follow(tabs, 2, 1))
    }

    @Test
    fun `a closed tab leaves the run inside the strip`() {
        val window = scrolling()
        window.follow(tabs, 3, 0)
        window.scroll(7f)
        assertEquals(listOf("g", "h", "i"), window.follow(tabs.dropLast(1), 3, 0))
    }

    @Test
    fun `a strip with all its tabs on show stands on no part of a tab`() {
        val window = scrolling()
        window.follow(tabs, 3, 0)
        window.scroll(0.5f)
        assertTrue(window.between)
        window.scrolls = false
        assertFalse(window.between)
    }

    // A strip read from the right, a tab 100 px wide, under a touchpad's pan as Nucleus's Tao windows send it
    private inner class PannedStrip(
        val window: ScrolledTabWindow = scrolling(),
    ) : AutoCloseable {
        private var time = 0L
        private val scene =
            ImageComposeScene(400, 60, Density(1f), LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().tabStripWheel(window, enabled = true, rtl = true, tabWidth = 100.dp))
            }

        init {
            window.follow(tabs, 3, 0)
            frames(2)
        }

        fun shown() = window.follow(tabs, 3, 0)

        fun pan(
            dx: Float,
            dy: Float = 0f,
            steps: Int = 1,
        ) {
            send(PointerEventType.PanStart, Offset.Zero)
            repeat(steps) { send(PointerEventType.PanMove, Offset(dx, dy)) }
        }

        fun lift() {
            send(PointerEventType.PanEnd, Offset.Zero)
            frames(60)
        }

        private fun send(
            type: PointerEventType,
            delta: Offset,
        ) {
            time += 16
            scene.sendPointerEvent(
                eventType = type,
                pointers = listOf(ComposeScenePointer(PointerId(0), Offset(200f, 30f), pressed = false, type = PointerType.Mouse)),
                timeMillis = time,
                panGestureOffset = delta,
            )
            scene.render(time * 1_000_000)
        }

        private fun frames(count: Int) {
            repeat(count) {
                time += 16
                scene.render(time * 1_000_000)
            }
        }

        override fun close() = scene.close()
    }

    @Test
    fun `two fingers move the run as far as they travel, and it rests on the nearest tab`() =
        PannedStrip().use { strip ->
            // Read from the right, the later tabs are to the left: a pan to the left brings them.
            strip.pan(dx = -50f)
            assertEquals(0.5f, strip.window.fraction)
            assertEquals(listOf("a", "b", "c"), strip.shown())
            assertTrue(strip.window.arrives("d"))
            strip.pan(dx = -30f)
            assertTrue(strip.window.arrives("d"))
            strip.lift()
            assertFalse(strip.window.between)
            assertEquals(listOf("b", "c", "d"), strip.shown())
        }

    @Test
    fun `fingers lifted before half a tab leave the run where it was`() =
        PannedStrip().use { strip ->
            strip.pan(dx = -30f)
            assertTrue(strip.window.between)
            strip.lift()
            assertFalse(strip.window.between)
            assertEquals(listOf("a", "b", "c"), strip.shown())
        }

    @Test
    fun `a pan to the right brings the earlier tabs back`() =
        PannedStrip().use { strip ->
            strip.window.scroll(3f)
            assertEquals(listOf("d", "e", "f"), strip.shown())
            strip.pan(dx = 40f, steps = 5)
            strip.lift()
            assertEquals(listOf("b", "c", "d"), strip.shown())
        }

    @Test
    fun `a pan up and down moves the run as the wheel does`() =
        PannedStrip().use { strip ->
            strip.pan(dx = 0f, dy = 50f, steps = 2)
            strip.lift()
            assertEquals(listOf("b", "c", "d"), strip.shown())
        }

    @Test
    fun `a slanted pan goes by its longer way`() =
        PannedStrip().use { strip ->
            strip.pan(dx = -40f, dy = 15f, steps = 5)
            strip.lift()
            assertEquals(listOf("c", "d", "e"), strip.shown())
        }

    @Composable
    private fun WheeledStrip(
        window: ScrolledTabWindow,
        enabled: Boolean = true,
    ) {
        Box(Modifier.size(400.dp, 40.dp).tabStripWheel(window, enabled = enabled, rtl = true, tabWidth = 100.dp).testTag("strip"))
    }

    @Test
    fun `a notch of the wheel slides the run a tab, down for on and up for back`() =
        runDesktopComposeUiTest(width = 400, height = 60) {
            val window = ScrolledTabWindow()
            window.follow(tabs, 3, 0)
            setContent { WheeledStrip(window) }
            onNodeWithTag("strip").performMouseInput { scroll(1f) }
            mainClock.advanceTimeBy(1_000)
            assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 0))
            // A second notch before the first has slid goes on from where that one goes.
            onNodeWithTag("strip").performMouseInput {
                scroll(1f)
                scroll(1f)
            }
            mainClock.advanceTimeBy(1_000)
            assertEquals(listOf("d", "e", "f"), window.follow(tabs, 3, 0))
            onNodeWithTag("strip").performMouseInput { scroll(-1f) }
            mainClock.advanceTimeBy(1_000)
            assertEquals(listOf("c", "d", "e"), window.follow(tabs, 3, 0))
            assertFalse(window.between)
        }

    @Test
    fun `a sideways turn follows the strip's direction`() =
        runDesktopComposeUiTest(width = 400, height = 60) {
            val window = ScrolledTabWindow()
            window.follow(tabs, 3, 0)
            setContent { WheeledStrip(window) }
            // Read from the right, the strip's later tabs are to the left.
            onNodeWithTag("strip").performMouseInput { scroll(-1f, ScrollWheel.Horizontal) }
            mainClock.advanceTimeBy(1_000)
            assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 0))
        }

    @Test
    fun `a fine scroll moves the run at once, and it rests on the nearest tab once the scroll ends`() =
        runDesktopComposeUiTest(width = 400, height = 60) {
            val window = ScrolledTabWindow()
            window.follow(tabs, 3, 0)
            setContent { WheeledStrip(window) }
            onNodeWithTag("strip").performMouseInput { scroll(0.5f) }
            assertEquals(listOf("a", "b", "c"), window.follow(tabs, 3, 0))
            assertTrue(window.arrives("d"))
            onNodeWithTag("strip").performMouseInput { scroll(0.25f) }
            assertEquals(0.75f, window.fraction)
            mainClock.advanceTimeBy(1_000)
            assertFalse(window.between)
            assertEquals(listOf("b", "c", "d"), window.follow(tabs, 3, 0))
        }

    @Test
    fun `a scroll does nothing while every tab is on show`() =
        runDesktopComposeUiTest(width = 400, height = 60) {
            val window = ScrolledTabWindow()
            window.follow(tabs, 3, 0)
            setContent { WheeledStrip(window, enabled = false) }
            onNodeWithTag("strip").performMouseInput { scroll(2f) }
            mainClock.advanceTimeBy(1_000)
            assertEquals(listOf("a", "b", "c"), window.follow(tabs, 3, 0))
        }

    // The strip's row as TabsView lays it: a pinned tab, then the run, each tab 100 dp wide
    @Composable
    private fun StripRow(
        window: ScrolledTabWindow,
        rtl: Boolean,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Row(Modifier.width(600.dp)) {
                val shown = remember(window.first, window.between) { window.follow(tabs, 3, 0) }
                Row(Modifier.scrolledTabRun(window, 100.dp).testTag("row")) {
                    (listOf("pin") + tabs.filter { it in shown || window.arrives(it) }).forEach { key ->
                        Box(
                            Modifier
                                .scrolledTab(window, key, rtl)
                                .size(100.dp, 40.dp)
                                .background(colorOf(key))
                                .testTag(key),
                        )
                    }
                }
            }
        }
    }

    private fun DpRect.sides() = left to right

    private fun colorOf(key: String) =
        when {
            key == "pin" -> Color.Red
            tabs.indexOf(key) % 2 == 0 -> Color.Blue
            else -> Color.Green
        }

    // The colour drawn at [x] of the strip: whose tab is seen there
    private fun ComposeUiTest.colorAt(x: Int): Color = onRoot().captureToImage().toPixelMap()[x, 20]

    @Test
    fun `on a scroll's way the row keeps its width, its first tab cut at the pinned one and the coming one at its end`() =
        runDesktopComposeUiTest(width = 600, height = 60) {
            val window = scrolling()
            setContent { StripRow(window, rtl = false) }
            assertEquals(0.dp to 400.dp, onNodeWithTag("row").getBoundsInRoot().sides())
            window.scroll(0.25f)
            waitForIdle()
            assertEquals(0.dp to 400.dp, onNodeWithTag("row").getBoundsInRoot().sides())
            assertEquals(0.dp to 100.dp, onNodeWithTag("pin").getBoundsInRoot().sides())
            // b and c stand a quarter of a tab back; d shows its first quarter.
            assertEquals(175.dp to 275.dp, onNodeWithTag("b").getBoundsInRoot().sides())
            assertEquals(275.dp to 375.dp, onNodeWithTag("c").getBoundsInRoot().sides())
            assertEquals(375.dp to 400.dp, onNodeWithTag("d").getBoundsInRoot().sides())
            // a is cut where the pinned tab ends, and nothing of d is drawn past the row.
            assertEquals(Color.Red, colorAt(90))
            assertEquals(Color.Blue, colorAt(110))
            assertEquals(Color.Green, colorAt(180))
            assertEquals(Color.Green, colorAt(390))
            assertNotEquals(Color.Green, colorAt(410))
            window.scroll(0.75f)
            waitForIdle()
            assertEquals(100.dp to 200.dp, onNodeWithTag("b").getBoundsInRoot().sides())
            assertEquals(300.dp to 400.dp, onNodeWithTag("d").getBoundsInRoot().sides())
            onNodeWithTag("a").assertDoesNotExist()
        }

    @Test
    fun `read from the right, the row is cut the other way round`() =
        runDesktopComposeUiTest(width = 600, height = 60) {
            val window = scrolling()
            setContent { StripRow(window, rtl = true) }
            window.scroll(0.25f)
            waitForIdle()
            // The row at the window's right: the pinned tab first, from the right.
            assertEquals(200.dp to 600.dp, onNodeWithTag("row").getBoundsInRoot().sides())
            assertEquals(500.dp to 600.dp, onNodeWithTag("pin").getBoundsInRoot().sides())
            assertEquals(325.dp to 425.dp, onNodeWithTag("b").getBoundsInRoot().sides())
            assertEquals(200.dp to 225.dp, onNodeWithTag("d").getBoundsInRoot().sides())
            assertEquals(Color.Red, colorAt(510))
            assertEquals(Color.Blue, colorAt(490))
            assertEquals(Color.Green, colorAt(210))
            assertNotEquals(Color.Green, colorAt(190))
        }
}
