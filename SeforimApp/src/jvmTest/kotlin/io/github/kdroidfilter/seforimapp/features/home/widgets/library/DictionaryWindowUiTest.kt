package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeUserLocation
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import io.github.kdroidfilter.seforimapp.testAppSettings
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The dictionary's own window: its field focused by itself and given the word asked for; its card on the Home left alone. */
@OptIn(ExperimentalTestApi::class)
class DictionaryWindowUiTest {
    private val graph by lazy { testAppGraph() }

    private fun ComposeUiTest.show(ownWindow: Boolean) {
        val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, HomeWidgetsLayout(testAppSettings()))
        setContent {
            IntUiTheme(isDark = false) {
                CompositionLocalProvider(
                    LocalAppGraph provides graph,
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDictionaryWindow provides ownWindow,
                ) {
                    DictionaryWidget.Content(state, Modifier.size(460.dp, 560.dp))
                }
            }
        }
    }

    @Test
    fun `in its own window the field takes the focus`() =
        runDesktopComposeUiTest(width = 460, height = 560) {
            show(ownWindow = true)
            waitForIdle()
            onNode(hasSetTextAction()).assertIsFocused()
        }

    @Test
    fun `on the Home the card takes no focus by itself`() =
        runDesktopComposeUiTest(width = 460, height = 560) {
            show(ownWindow = false)
            waitForIdle()
            onNode(hasSetTextAction()).assertIsNotFocused()
        }

    @Test
    fun `the card on the Home leaves the word asked for from outside to the window`() =
        runDesktopComposeUiTest(width = 460, height = 560) {
            graph.dictionaryLookup.open("אגמא")
            show(ownWindow = false)
            waitForIdle()
            assertEquals("אגמא", graph.dictionaryLookup.word)
        }

    @Test
    fun `its own window takes the word asked for from outside`() =
        runDesktopComposeUiTest(width = 460, height = 560) {
            graph.dictionaryLookup.open("אגמא")
            show(ownWindow = true)
            waitForIdle()
            onNode(hasSetTextAction()).assertTextContains("אגמא")
            assertNull(graph.dictionaryLookup.word)
        }
}
