package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.features.search.area.SearchArea
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

/** The area beside a search's results, as its pane shows it, and the end bar's button of the pane. */
@OptIn(ExperimentalTestApi::class)
class SearchPanelsSettingsUiTest {
    private val graph by lazy { testAppGraph() }

    // The real graph's setting, the machine's own: the pane asked for in the test, as it was after it
    private var before: Boolean? = null

    @BeforeTest
    fun askForIt() {
        before = graph.appSettings[SearchPanelsSettings.area]
        graph.appSettings[SearchPanelsSettings.area] = true
    }

    @AfterTest
    fun putItBack() {
        before?.let { graph.appSettings[SearchPanelsSettings.area] = it }
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
    fun `the area shows the topic its query names`() =
        runDesktopComposeUiTest(width = 360, height = 640) {
            show {
                SearchArea(
                    "קריאת שמע",
                    entity = null,
                    sources = persistentListOf(),
                    onShowSource = {},
                ) { _, _ -> }
            }
            // Sefaria's topics are read off the UI thread at first
            waitUntil(timeoutMillis = LOADING_MS) { cards() == 1 }
        }

    @Test
    fun `a source of the results shows alone with its text, and a click on it shows it, without a count`() =
        runDesktopComposeUiTest(width = 640, height = 320) {
            val shown = mutableListOf<CommonSource>()
            show {
                SearchArea("zzz", entity = null, sources = persistentListOf(SOURCE), onShowSource = { shown += it }) { _, _ -> }
            }
            waitForIdle()
            onNodeWithText(SOURCE.label).performClick()
            // Its text too: the whole source is a link to it, as a result is
            onNodeWithText(SOURCE.text).performClick()
            assertEquals(listOf(SOURCE, SOURCE), shown)
            onAllNodesWithText("4 ספרים").assertCountEquals(0)
        }

    @Test
    fun `a wide pane shows what the query names beside the sources, the sources on its far side`() =
        runDesktopComposeUiTest(width = 1200, height = 320) {
            show {
                SearchArea(
                    "קריאת שמע",
                    entity = null,
                    sources = persistentListOf(SOURCE),
                    onShowSource = {},
                    modifier = Modifier.fillMaxSize(),
                    wide = true,
                ) { _, _ -> }
            }
            waitUntil(timeoutMillis = LOADING_MS) { cards() == 1 }
            // Right to left: what the query names first, at the right; the sources at the left of it
            val named = onNodeWithText(OPEN_PAGE).getBoundsInRoot()
            val source = onNodeWithText(SOURCE.label).getBoundsInRoot()
            assertTrue(source.right <= named.left, "the source at ${source.right}, what the query names from ${named.left}")
        }

    @Test
    fun `the bar's button hides and shows the area's pane`() =
        runDesktopComposeUiTest(width = 200, height = 200) {
            show { SearchAreaPaneButton() }
            onNodeWithText(PANE).performClick()
            waitForIdle()
            assertFalse(graph.appSettings[SearchPanelsSettings.area])
            onNodeWithText(PANE).performClick()
            waitForIdle()
            assertTrue(graph.appSettings[SearchPanelsSettings.area])
        }

    private companion object {
        const val OPEN_PAGE = "לדף הנושא"
        const val PANE = "על החיפוש"
        const val LOADING_MS = 30_000L
        val SOURCE = CommonSource(2045, 2, "שמות טז, כט", 4, "רְאוּ כִּי יְהֹוָה נָתַן לָכֶם הַשַּׁבָּת", from = listOf(11, 12))
    }
}
