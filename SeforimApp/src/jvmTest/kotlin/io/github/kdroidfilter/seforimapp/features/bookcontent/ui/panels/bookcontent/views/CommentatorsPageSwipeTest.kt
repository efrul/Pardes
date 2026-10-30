package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A touchpad's swipe with two fingers turns the commentators' page, one page a swipe. On Windows it comes as a pan
 * (Nucleus's Tao windows), in px the way a scroll goes, as a Windows touchpad showed: in RTL a pan to the left brings
 * the next page.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CommentatorsPageSwipeTest {
    private val pagerState = PagerState(currentPage = 0) { 3 }
    private var time = 0L
    private val scene =
        ImageComposeScene(400, 300, Density(1f), LayoutDirection.Rtl) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().pageSwipe(pagerState),
                userScrollEnabled = false,
            ) { Box(Modifier.fillMaxSize()) }
        }

    @AfterTest
    fun close() = scene.close()

    // A swipe of [steps] moves of the fingers by (dx, dy) px each, as a touchpad's pan
    private fun swipe(
        dx: Float,
        dy: Float = 0f,
        steps: Int = 5,
    ) {
        pan(PointerEventType.PanStart, Offset.Zero)
        repeat(steps) { pan(PointerEventType.PanMove, Offset(dx, dy)) }
        pan(PointerEventType.PanEnd, Offset.Zero)
        settle()
    }

    private fun pan(
        type: PointerEventType,
        delta: Offset,
    ) {
        time += 16
        scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(ComposeScenePointer(PointerId(0), Offset(200f, 150f), pressed = false, type = PointerType.Mouse)),
            timeMillis = time,
            panGestureOffset = delta,
        )
        scene.render(time * 1_000_000)
    }

    // Frames until the page has turned
    private fun settle() {
        repeat(120) {
            time += 16
            scene.render(time * 1_000_000)
        }
    }

    @Test
    fun `a pan to the left brings the next page in RTL, to the right the previous one`() {
        settle()
        swipe(dx = -20f)
        assertEquals(1, pagerState.currentPage)
        swipe(dx = 20f)
        assertEquals(0, pagerState.currentPage)
    }

    @Test
    fun `a long swipe turns one page only`() {
        settle()
        swipe(dx = -20f, steps = 30)
        assertEquals(1, pagerState.currentPage)
    }

    @Test
    fun `an up-and-down swipe, the column's scroll, turns no page`() {
        settle()
        swipe(dx = 4f, dy = 20f)
        assertEquals(0, pagerState.currentPage)
    }

    @Test
    fun `a short swipe turns no page`() {
        settle()
        swipe(dx = 8f, steps = 3)
        assertEquals(0, pagerState.currentPage)
    }
}
