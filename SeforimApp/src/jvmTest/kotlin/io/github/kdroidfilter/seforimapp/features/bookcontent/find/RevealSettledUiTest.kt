package io.github.kdroidfilter.seforimapp.features.bookcontent.find

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The results pane's current row kept in view while the rows read their text ([revealSettled]): a page down brought it
 * in at the view's bottom, then the rows above it grew and pushed it below the view; brought back by a jump to it and
 * back, the rows above read their text again, grew again and pushed it again (request 78 of the list after the public
 * release, docs/DESKTOP_STAGE3_PLAN.md: "בדפדוף בפאנל הצד הפוקוס נעלם למטה").
 */
@OptIn(ExperimentalTestApi::class)
class RevealSettledUiTest {
    @Test
    fun `a row brought in from below stays whole in view as it and the rows above it grow`() =
        runComposeUiTest {
            val listState = LazyListState()
            var target by mutableIntStateOf(-1)
            setContent {
                Box(Modifier.height(200.dp)) {
                    LazyColumn(state = listState) {
                        items(100) {
                            // As a found line's row: a line high until it has read its text, then three
                            var read by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(READ_MS)
                                read = true
                            }
                            Box(Modifier.fillMaxWidth().height(if (read) READ_HEIGHT else 20.dp))
                        }
                    }
                }
                LaunchedEffect(target) { if (target >= 0) listState.revealSettled(target) }
            }
            waitForIdle()
            for (next in listOf(30, 60, 90)) {
                target = next
                waitForIdle()
                // The rows read their text, a while after they are on screen
                repeat(ROUNDS) {
                    mainClock.advanceTimeBy(READ_MS)
                    waitForIdle()
                }
                val info = listState.layoutInfo
                val row = info.visibleItemsInfo.firstOrNull { it.index == next }
                assertTrue(
                    row != null && row.offset >= info.viewportStartOffset && row.offset + row.size <= info.viewportEndOffset,
                    "row $next whole in view: ${row?.offset}, ${row?.size} in ${info.viewportEndOffset}",
                )
                // Settled: the rows in view have read their text, none starting again after each reveal
                val read = with(density) { READ_HEIGHT.roundToPx() }
                assertTrue(info.visibleItemsInfo.all { it.size == read }, "rows in view: ${info.visibleItemsInfo.map { it.size }}")
            }
        }

    @Test
    fun `the row is not brought back once the reader scrolls the list away from it`() =
        runComposeUiTest {
            val listState = LazyListState()
            var target by mutableIntStateOf(-1)
            setContent {
                Box(Modifier.height(200.dp)) {
                    LazyColumn(state = listState) {
                        items(100) {
                            var read by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(READ_MS)
                                read = true
                            }
                            Box(Modifier.fillMaxWidth().height(if (read) READ_HEIGHT else 20.dp))
                        }
                    }
                }
                LaunchedEffect(target) { if (target >= 0) listState.revealSettled(target) }
            }
            waitForIdle()
            target = 30
            waitForIdle()
            repeat(ROUNDS) {
                mainClock.advanceTimeBy(READ_MS)
                waitForIdle()
            }
            // Scrolled down as a trackpad does, no animation: the rows that come in read their text and grow
            runOnIdle { listState.dispatchRawDelta(with(density) { 600.dp.toPx() }) }
            waitForIdle()
            repeat(ROUNDS) {
                mainClock.advanceTimeBy(READ_MS)
                waitForIdle()
            }
            assertTrue(listState.firstVisibleItemIndex > 30, "left where the reader scrolled: ${listState.firstVisibleItemIndex}")
        }

    private companion object {
        const val READ_MS = 100L

        // Long past the time the rows take to read their text
        const val ROUNDS = 30
        val READ_HEIGHT = 60.dp
    }
}
