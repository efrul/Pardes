package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The scan's place kept as long as its tab (request 60 of the list after the third testers' build). */
@OptIn(ExperimentalTestApi::class)
class ScanPlaceTest {
    @Test
    fun `the list is kept for the rows it was made of, and made anew for others`() {
        val place = ScanPlace(topLineId = 1L)
        val first = place.listFor("ברכות" to false) { LazyListState(3) }
        assertSame(first, place.listFor("ברכות" to false) { LazyListState(0) })
        val spreads = place.listFor("ברכות" to true) { LazyListState(1) }
        assertNotSame(first, spreads, "two pages a row: other rows")
        assertEquals(1, spreads.firstVisibleItemIndex)
    }

    @Test
    fun `the tab's line is followed once, and not when it is the scan's place`() {
        val place = ScanPlace(topLineId = 7L, followed = 7L to 100L)
        assertNull(place.follow(7L, 100L), "the line the scan opened at")
        assertEquals(42L, place.follow(42L, 200L))
        assertNull(place.follow(42L, 200L), "asked before: the tab shown again")
        assertNull(place.follow(7L, 300L), "the scan's own place")
        assertNull(place.follow(null, 400L))
    }

    @Test
    fun `the scan shown again is where the table of contents took it`() =
        runDesktopComposeUiTest(width = 300, height = 400) {
            val place = ScanPlace(topLineId = 1L, followed = 1L to 0L)
            var shown by mutableStateOf(true)
            // A page the table of contents asks, gone to once, as FollowTheTab goes
            var asked by mutableStateOf<Int?>(null)
            lateinit var listState: LazyListState
            setContent {
                if (shown) {
                    val state = remember { place.listFor("ברכות" to false) { LazyListState(0) } }
                    listState = state
                    LaunchedEffect(asked) {
                        asked?.let { state.bringToTop(it) }
                        asked = null
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = state) {
                        items(20) { page -> Box(Modifier.fillMaxWidth().height(300.dp)) { BasicText("עמוד $page") } }
                    }
                }
            }
            // The table of contents chooses the sixth page's line
            assertEquals(42L, place.follow(42L, 1L))
            asked = 5
            waitForIdle()
            assertEquals(5, listState.firstVisibleItemIndex)
            // Another tab, and back
            shown = false
            waitForIdle()
            shown = true
            waitForIdle()
            assertEquals(5, listState.firstVisibleItemIndex, "the same list, where it was left")
            onNodeWithText("עמוד 5").assertIsDisplayed()
            assertNull(place.follow(42L, 1L), "not taken there again")
        }
}
