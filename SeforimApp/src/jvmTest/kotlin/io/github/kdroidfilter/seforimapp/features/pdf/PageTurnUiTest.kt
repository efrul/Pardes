package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A page key turns most of a view down or up, the same both ways, in the text as in the daf's and a PDF book's pages
 * (request 75 of the list after the public release, docs/DESKTOP_STAGE3_PLAN.md: "למה יש הבדל בין דפדוף למעלה ללמטה").
 */
@OptIn(ExperimentalTestApi::class)
class PageTurnUiTest {
    @Test
    fun `pages turn through a row taller than the view, and up as far as down`() =
        runComposeUiTest {
            val listState = LazyListState(firstVisibleItemIndex = 3)
            // Each turn asked: forward, or back; the count tells one from the next of the same way
            var turns by mutableStateOf(emptyList<Boolean>())
            setContent {
                Box(Modifier.height(400.dp)) {
                    LazyColumn(state = listState) {
                        // Rows of other heights, the fourth taller than two views
                        items(30) { i -> Box(Modifier.fillMaxWidth().height(if (i == 3) 900.dp else (20 + i % 5 * 30).dp)) }
                    }
                }
                LaunchedEffect(turns) { turns.lastOrNull()?.let { listState.turnPage(it) } }
            }
            waitForIdle()
            val page = listState.layoutInfo.viewportSize.height * 0.9f

            fun assertAt(offset: Float) =
                assertTrue(
                    listState.firstVisibleItemIndex == 3 && abs(listState.firstVisibleItemScrollOffset - offset) <= 1f,
                    "row 3 at ${listState.firstVisibleItemScrollOffset}, not $offset (${listState.firstVisibleItemIndex})",
                )

            turns = turns + true
            waitForIdle()
            assertAt(page)
            turns = turns + true
            waitForIdle()
            assertAt(page * 2)
            turns = turns + false
            waitForIdle()
            assertAt(page)
            turns = turns + false
            waitForIdle()
            assertEquals(3, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset, "up as far as down")
        }
}
