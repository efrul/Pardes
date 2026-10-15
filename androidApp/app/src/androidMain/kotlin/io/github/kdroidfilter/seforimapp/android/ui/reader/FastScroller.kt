package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** Books under this many lines scroll by hand well enough: no thumb. */
private const val MIN_LINES_FOR_THUMB = 120

/** The text's jumps while the thumb is dragged: one a frame at most (desktop FAR_DRAG_THROTTLE). */
private const val DRAG_FRAME_MS = 16L

/** How long the thumb rests on a place before the bubble asks the book what it is. */
private const val LABEL_DELAY_MS = 40L

/**
 * The text's scrollbar for long books (desktop views/ContentScrollbar.kt, a content-aware thumb over the whole book,
 * loaded or not): placed by the share of the book's characters above the first visible line (desktop's
 * bookCharCounts), it shows while scrolling and can be dragged anywhere. The text follows the drag ([onDrag],
 * throttled as desktop's far drags) and lands exactly on release ([onDrop]); a bubble names the place under the thumb
 * ([labelAt]). A 48 dp wide touch strip on the end edge (left in RTL).
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    totalLines: Int,
    charCounts: IntArray?,
    labelAt: suspend (Int) -> String?,
    onDrag: suspend (Int) -> Unit,
    onDrop: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalLines < MIN_LINES_FOR_THUMB) return
    val c = Zayit.colors
    val density = LocalDensity.current
    // Prefix sums of the characters: the thumb moves through the book as the text does, not line by line
    val prefix = remember(charCounts) {
        charCounts?.takeIf { it.size == totalLines }?.let { counts ->
            LongArray(counts.size + 1).also { p -> for (i in counts.indices) p[i + 1] = p[i] + counts[i].coerceAtLeast(1) }
        }
    }

    fun fractionOf(index: Int): Float = if (prefix != null) prefix[index.coerceIn(0, totalLines)].toFloat() / prefix.last() else index.toFloat() / totalLines

    fun indexAt(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        if (prefix == null) return (f * (totalLines - 1)).roundToInt()
        val target = (f * prefix.last()).toLong()
        var lo = 0
        var hi = totalLines
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (prefix[mid + 1] <= target) lo = mid + 1 else hi = mid
        }
        return lo.coerceIn(0, totalLines - 1)
    }

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress || dragging }.collect { active ->
            if (active) visible = true else {
                delay(1_500)
                visible = false
            }
        }
    }
    val fraction by remember(prefix) { derivedStateOf { fractionOf(listState.firstVisibleItemIndex) } }
    // The text follows the thumb (desktop farDragFlow): at most one jump a frame, the latest target winning; the list
    // has an item for every line of the book, so any index is a cheap jump, the lines there loading behind it
    val drag by rememberUpdatedState(onDrag)
    LaunchedEffect(listState, prefix, totalLines) {
        snapshotFlow { if (dragging) indexAt(dragFraction) else -1 }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .conflate()
            .collect { index ->
                drag(index)
                delay(DRAG_FRAME_MS)
            }
    }
    val dragIndex = if (dragging) indexAt(dragFraction) else -1
    // The bubble names the line under the thumb, loaded or not; asked once the thumb pauses for a moment
    var label by remember { mutableStateOf<String?>(null) }
    val placeOf by rememberUpdatedState(labelAt)
    LaunchedEffect(dragIndex) {
        if (dragIndex < 0) {
            label = null
            return@LaunchedEffect
        }
        delay(LABEL_DELAY_MS)
        placeOf(dragIndex)?.let { if (isActive) label = it }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val thumbHeight = 44.dp
        val trackPx = with(density) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)
        val shown = if (dragging) dragFraction else fraction
        val y = (shown * trackPx).roundToInt()
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, y) }) {
            Box(
                Modifier
                    .size(48.dp, thumbHeight)
                    .pointerInput(trackPx, prefix) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragFraction = fraction
                            },
                            onDragEnd = {
                                dragging = false
                                onDrop(indexAt(dragFraction))
                            },
                            onDragCancel = { dragging = false },
                        ) { change, dy ->
                            change.consume()
                            dragFraction = (dragFraction + dy / trackPx).coerceIn(0f, 1f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(6.dp, thumbHeight - 8.dp).clip(RoundedCornerShape(3.dp)).background(if (dragging) c.accent else c.textSecondary.copy(alpha = 0.6f)))
            }
        }
        if (dragging) {
            // The bubble beside the thumb, towards the text
            label?.takeIf { it.isNotBlank() }?.let { place ->
                Text(
                    place,
                    color = c.text,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 52.dp)
                        .offset { IntOffset(0, y) }
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.panel)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
