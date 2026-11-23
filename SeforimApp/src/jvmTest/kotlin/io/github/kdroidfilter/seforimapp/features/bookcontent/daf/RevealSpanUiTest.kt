package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

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
 * A segment or a comment of the daf brought into view by the least scroll ([revealSpan]): the next one below the view
 * comes in at the view's bottom, its end there, not at the view's top (request 80 of docs/DESKTOP_STAGE3_PLAN.md: "הקטע
 * הבא עדיין נכנס בראש התצוגה. המפה של צורת הדף יודעת איפה קטע מתחיל, אבל לא איפה הוא נגמר").
 */
@OptIn(ExperimentalTestApi::class)
class RevealSpanUiTest {
    @Test
    fun `a span below the view comes in at its bottom, one above or taller than the view at its top`() =
        runComposeUiTest {
            val listState = LazyListState()
            // The span asked for: its start and its end, each an item and the pixels down it
            var span by mutableStateOf<Pair<Pair<Int, Float>, Pair<Int, Float>>?>(null)
            setContent {
                // Amudim 500 high in a view 200 high
                Box(Modifier.height(200.dp)) {
                    LazyColumn(state = listState) {
                        items(10) { Box(Modifier.fillMaxWidth().height(500.dp)) }
                    }
                }
                LaunchedEffect(span) { span?.let { (start, end) -> listState.revealSpan(start, end, MARGIN) } }
            }
            waitForIdle()
            val view = listState.layoutInfo.viewportEndOffset.toFloat()

            fun reveal(
                start: Pair<Int, Float>,
                end: Pair<Int, Float>,
            ) {
                span = start to end
                waitForIdle()
            }

            fun at(
                item: Int,
                y: Float,
            ): Float =
                listState.layoutInfo.visibleItemsInfo
                    .first { it.index == item }
                    .let { it.offset + minOf(y, it.size.toFloat()) }

            fun assertNear(
                expected: Float,
                actual: Float,
                message: String,
            ) = assertTrue(abs(expected - actual) <= 1f, "$message: $actual, not $expected")

            // In view: not scrolled
            reveal(0 to view * 0.2f, 0 to view * 0.6f)
            assertEquals(0, listState.firstVisibleItemScrollOffset, "a span in view is not scrolled to")
            // Partly below: its end at the view's bottom, the list a little on, not a view
            reveal(0 to view * 0.7f, 0 to view * 1.2f)
            assertNear(view - MARGIN, at(0, view * 1.2f), "its end at the view's bottom")
            // Far below: its end at the view's bottom too, its start in view above it
            reveal(2 to view * 0.1f, 2 to view * 0.5f)
            assertNear(view - MARGIN, at(2, view * 0.5f), "the next span's end at the view's bottom")
            assertTrue(at(2, view * 0.1f) >= MARGIN, "and its start in view")
            // Taller than the view: its start at the view's top
            reveal(4 to view * 0.5f, 4 to view * 2f)
            assertNear(MARGIN, at(4, view * 0.5f), "a span taller than the view at its top")
            // Above: at the view's top
            reveal(1 to view * 0.3f, 1 to view * 0.5f)
            assertNear(MARGIN, at(1, view * 0.3f), "a span above the view at its top")
            // Going on into the next item: its end, there, at the view's bottom
            reveal(5 to view * 2.2f, 6 to view * 0.3f)
            assertNear(view - MARGIN, at(6, view * 0.3f), "a span ending in the next item")
            // To its item's end
            reveal(7 to view * 2.3f, 7 to Float.MAX_VALUE)
            assertNear(view - MARGIN, at(7, Float.MAX_VALUE), "a span to its item's end")
        }

    private companion object {
        const val MARGIN = 10f
    }
}
