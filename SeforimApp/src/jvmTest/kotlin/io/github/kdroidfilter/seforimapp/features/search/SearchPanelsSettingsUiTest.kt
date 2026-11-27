package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.features.search.area.SearchArea
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaSettings
import io.github.kdroidfilter.seforimapp.features.sefaria.topics.SearchTopicCardButton
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.search.CommonSource
import io.github.kdroidfilter.seforimapp.testAppGraph
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The area beside a search's results, its topic shown or hidden from the buttons of the search's end bar. */
@OptIn(ExperimentalTestApi::class)
class SearchPanelsSettingsUiTest {
    private val graph by lazy { testAppGraph() }

    // The real graph's settings, the machine's own: both cards shown for the test, as they were after it
    private var before: Pair<Boolean, Boolean>? = null

    @BeforeTest
    fun showThem() {
        before = graph.appSettings[SearchPanelsSettings.entity] to graph.appSettings[SefariaSettings.searchTopicCard]
        graph.appSettings[SearchPanelsSettings.entity] = true
        graph.appSettings[SefariaSettings.searchTopicCard] = true
    }

    @AfterTest
    fun putThemBack() {
        before?.let { (entity, topic) ->
            graph.appSettings[SearchPanelsSettings.entity] = entity
            graph.appSettings[SefariaSettings.searchTopicCard] = topic
        }
    }

    private fun ComposeUiTest.show(content: @Composable () -> Unit) =
        setContent {
            IntUiTheme(isDark = false) {
                CompositionLocalProvider(LocalAppGraph provides graph, LocalLayoutDirection provides LayoutDirection.Rtl) {
                    content()
                }
            }
        }

    private fun ComposeUiTest.cards() = onAllNodesWithText(OPEN_PAGE).fetchSemanticsNodes().size

    @Test
    fun `the area's topic shows while its setting is on`() =
        runDesktopComposeUiTest(width = 640, height = 520) {
            show { SearchArea("קריאת שמע", entity = null, sources = persistentListOf(), onOpenSource = {}) { _, _ -> } }
            // Sefaria's topics are read off the UI thread at first
            waitUntil(timeoutMillis = LOADING_MS) { cards() == 1 }
            graph.appSettings[SefariaSettings.searchTopicCard] = false
            waitForIdle()
            onAllNodesWithText(OPEN_PAGE).assertCountEquals(0)
            graph.appSettings[SefariaSettings.searchTopicCard] = true
            waitUntil(timeoutMillis = LOADING_MS) { cards() == 1 }
        }

    @Test
    fun `a source of the results shows alone, with its text, and opens`() =
        runDesktopComposeUiTest(width = 640, height = 320) {
            val source = CommonSource(2045, 2, "שמות טז, כט", 4, "רְאוּ כִּי יְהֹוָה נָתַן לָכֶם הַשַּׁבָּת")
            var opened: CommonSource? = null
            show { SearchArea("zzz", entity = null, sources = persistentListOf(source), onOpenSource = { opened = it }) { _, _ -> } }
            waitForIdle()
            onNodeWithText("4 ספרים").assertExists()
            onNodeWithText(source.text).assertExists()
            onNodeWithText(source.label).performClick()
            assertEquals(source, opened)
        }

    @Test
    fun `the bar's buttons hide and show the cards`() =
        runDesktopComposeUiTest(width = 200, height = 400) {
            show {
                Column {
                    SearchEntityPanelButton()
                    SearchTopicCardButton()
                }
            }
            onNodeWithText("ספר/מחבר").performClick()
            waitForIdle()
            assertFalse(graph.appSettings[SearchPanelsSettings.entity])
            onNodeWithText("ספר/מחבר").performClick()
            waitForIdle()
            assertTrue(graph.appSettings[SearchPanelsSettings.entity])
            onNodeWithText("נושא").performClick()
            waitForIdle()
            assertFalse(graph.appSettings[SefariaSettings.searchTopicCard])
        }

    private companion object {
        const val OPEN_PAGE = "לדף הנושא"
        const val LOADING_MS = 30_000L
    }
}
