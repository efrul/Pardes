package io.github.kdroidfilter.seforimapp.android.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Two fingers zoom the text, the same in the books and in the search's passage preview (desktop 0.6.5 e3051846,
 * rememberAppTextZoom): [onZoom] gets the pinch's factor each time it passes about 12 %, not on every frame. Only
 * while two fingers are down, as desktop's TextZoom: a one-finger drag, straight or at an angle, is left to the
 * scrolling content inside. Put it outside that content.
 */
@Composable
fun Modifier.pinchTextZoom(onZoom: (factor: Float) -> Unit): Modifier {
    val zoom by rememberUpdatedState(onZoom)
    return pointerInput(Unit) {
        var pinch = 1f
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    val z = event.calculateZoom()
                    if (z != 1f) {
                        pinch *= z
                        if (pinch > 1.12f || pinch < 0.89f) {
                            zoom(pinch)
                            pinch = 1f
                        }
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
