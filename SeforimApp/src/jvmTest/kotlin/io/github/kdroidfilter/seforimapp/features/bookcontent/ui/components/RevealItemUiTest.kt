package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row brought into view by the least scroll, a step down as a step up (request 75 of the list after the public
 * release, docs/DESKTOP_STAGE3_PLAN.md: "למעלה אחד אחד ולמטה מחליף הרבה בבת אחת"): a row below the view comes in at its
 * bottom, not at its top, which made a step down from the row at the bottom a page's jump.
 */
@OptIn(ExperimentalTestApi::class)
class RevealItemUiTest {
    @Test
    fun `a step down from the row at the bottom scrolls a row, as a step up from the top does`() =
        runComposeUiTest {
            val listState = LazyListState()
            var target by mutableIntStateOf(0)
            setContent {
                // Rows 30 high in a view 100 high: three and a third in view
                Box(Modifier.height(100.dp)) {
                    LazyColumn(state = listState) {
                        items(50) { Box(Modifier.fillMaxWidth().height(30.dp)) }
                    }
                }
                LaunchedEffect(target) { listState.revealItem(target) }
            }

            fun shownLast() = listState.layoutInfo.visibleItemsInfo.last { it.offset + it.size <= listState.layoutInfo.viewportEndOffset }

            // Down, row by row: each comes in at the view's bottom, the list a row on, not a page
            for (next in 3..8) {
                target = next
                waitForIdle()
                assertEquals(next, shownLast().index, "row $next whole at the bottom")
                assertEquals(listState.layoutInfo.viewportEndOffset, shownLast().offset + shownLast().size)
                // Three rows above it, the first partly in view
                assertEquals(next - 3, listState.firstVisibleItemIndex, "row $next: the list moved by a row")
            }
            // Up, row by row: each comes in at the view's top
            for (next in 5 downTo 2) {
                target = next
                waitForIdle()
                assertEquals(next, listState.firstVisibleItemIndex, "row $next at the top")
                assertEquals(0, listState.firstVisibleItemScrollOffset)
            }
        }

    @Test
    fun `a row far below the view comes in at its bottom, and one taller than the view at its top`() =
        runComposeUiTest {
            val listState = LazyListState()
            var target by mutableIntStateOf(0)
            setContent {
                Box(Modifier.height(100.dp)) {
                    LazyColumn(state = listState) {
                        items(50) { index -> Box(Modifier.fillMaxWidth().height(if (index == 40) 300.dp else 30.dp)) }
                    }
                }
                LaunchedEffect(target) { listState.revealItem(target) }
            }
            target = 20
            waitForIdle()
            val info = listState.layoutInfo
            val shown = info.visibleItemsInfo.first { it.index == 20 }
            assertEquals(info.viewportEndOffset, shown.offset + shown.size, "row 20 at the bottom")

            target = 40
            waitForIdle()
            assertEquals(40, listState.firstVisibleItemIndex, "a row taller than the view: its top")
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
}
