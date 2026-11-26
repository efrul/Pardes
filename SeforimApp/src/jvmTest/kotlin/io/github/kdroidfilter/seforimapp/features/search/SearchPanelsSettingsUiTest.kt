package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaSettings
import io.github.kdroidfilter.seforimapp.features.sefaria.topics.SearchTopicCard
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The cards beside a search's results: hidden for good from their own "אל תציג עוד", brought back in Settings › כללי. */
@OptIn(ExperimentalTestApi::class)
class SearchPanelsSettingsUiTest {
    private val graph by lazy { testAppGraph() }

    @AfterTest
    fun showThemAgain() {
        graph.appSettings[SefariaSettings.searchTopicCard] = true
        graph.appSettings[SearchPanelsSettings.entity] = true
    }

    private fun ComposeUiTest.show(content: @Composable () -> Unit) =
        setContent {
            IntUiTheme(isDark = false) {
                CompositionLocalProvider(LocalAppGraph provides graph, LocalLayoutDirection provides LayoutDirection.Rtl) {
                    content()
                }
            }
        }

    private fun ComposeUiTest.hideLinks() = onAllNodesWithText(HIDE).fetchSemanticsNodes().size

    @Test
    fun `the topic card hides for good from its link, and comes back from its setting`() =
        runDesktopComposeUiTest(width = 640, height = 520) {
            show { SearchTopicCard("קריאת שמע") }
            // Sefaria's topics are read off the UI thread at first
            waitUntil(timeoutMillis = LOADING_MS) { hideLinks() == 1 }
            onNodeWithText(HIDE).performClick()
            waitForIdle()
            assertFalse(graph.appSettings[SefariaSettings.searchTopicCard])
            onAllNodesWithText("קריאת שמע").assertCountEquals(0)
            graph.appSettings[SefariaSettings.searchTopicCard] = true
            waitUntil(timeoutMillis = LOADING_MS) { hideLinks() == 1 }
        }

    @Test
    fun `Settings have a box for each card, as its setting is`() =
        runDesktopComposeUiTest(width = 640, height = 520) {
            graph.appSettings[SearchPanelsSettings.entity] = false
            show { Column { SearchPanelsCards() } }
            waitForIdle()
            val boxes = onAllNodes(isToggleable()).fetchSemanticsNodes()
            assertEquals(2, boxes.size)
            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertTrue(graph.appSettings[SearchPanelsSettings.entity])
        }

    private companion object {
        const val HIDE = "אל תציג עוד"
        const val LOADING_MS = 30_000L
    }
}
