package io.github.kdroidfilter.seforimapp.features.settings.semantic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.features.settings.semantic.SemanticPackViewModel.Available
import io.github.kdroidfilter.seforimapp.features.settings.semantic.SemanticPackViewModel.State
import io.github.kdroidfilter.seforimapp.features.settings.semantic.SemanticPackViewModel.Work
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The semantic search card: how to install a pack while none is in use, nothing offered again once one is ready, and
 * into view when the settings were opened for it.
 */
@OptIn(ExperimentalTestApi::class)
class SemanticPackCardUiTest {
    private val pack =
        SemanticPackReleases.Available(
            tag = "semantic-2-v3-20261012193827",
            pack = 2,
            library = "v3-20261012193827",
            page = null,
            assets =
                listOf("${SemanticPack.BUNDLE_NAME}.part01", "${SemanticPack.BUNDLE_NAME}.part02", SemanticPack.MANIFEST_NAME)
                    .associateWith { SemanticPackReleases.Asset(it, 1L shl 30, "") },
        )
    private val inUse = InstalledPack.InUse(2, "student@test/pardes-clean-1", 5_725_672, "v3-20261012193827")

    private fun actions(promptAtStart: (Boolean) -> Unit = {}) = SemanticPackActions({}, {}, {}, {}, {}, promptAtStart)

    private fun ComposeUiTest.show(content: @Composable () -> Unit) =
        setContent {
            IntUiTheme(isDark = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
            }
        }

    @Test
    fun `without a pack the card says how to install one and can stop opening at start`() =
        runDesktopComposeUiTest(width = 520, height = 700) {
            var prompt: Boolean? = null
            show { SemanticPackCardContent(State(available = Available.Pack(pack)), actions { prompt = it }) }
            onNodeWithText("עם אינטרנט:", substring = true).assertExists()
            onNodeWithText("בלי אינטרנט:", substring = true).assertExists()
            onNodeWithText("לפתוח את המסך הזה בכל הפעלה", substring = true).performClick()
            assertEquals(false, prompt)
        }

    @Test
    fun `without a connection only the zips are offered`() =
        runDesktopComposeUiTest(width = 520, height = 700) {
            show { SemanticPackCardContent(State(available = Available.Unreachable), actions()) }
            onNodeWithText("בלי אינטרנט:", substring = true).assertExists()
            onAllNodesWithText("עם אינטרנט:", substring = true).assertCountEquals(0)
            onNodeWithText("לדף ההורדות").assertExists()
        }

    @Test
    fun `a pack in use has no instructions and does not open at start`() =
        runDesktopComposeUiTest(width = 520, height = 700) {
            show { SemanticPackCardContent(State(installed = inUse, available = Available.Pack(pack)), actions()) }
            onNodeWithText("זו החבילה האחרונה.").assertExists()
            onAllNodesWithText("עם אינטרנט:", substring = true).assertCountEquals(0)
            onAllNodesWithText("לפתוח את המסך הזה בכל הפעלה", substring = true).assertCountEquals(0)
        }

    @Test
    fun `a pack ready for the restart is not offered again`() =
        runDesktopComposeUiTest(width = 520, height = 700) {
            show { SemanticPackCardContent(State(available = Available.Pack(pack), work = Work.Ready), actions()) }
            onNodeWithText("החבילה מוכנה", substring = true).assertExists()
            onAllNodesWithText("זמינה להורדה", substring = true).assertCountEquals(0)
            onAllNodesWithText("עם אינטרנט:", substring = true).assertCountEquals(0)
        }

    @Test
    fun `the card the settings were opened for comes into view`() =
        runDesktopComposeUiTest(width = 520, height = 500) {
            showBelowTheFold(marked = true)
            onNodeWithText("חיפוש לפי משמעות").assertIsDisplayed()
        }

    @Test
    fun `a card the settings were not opened for stays where it is`() =
        runDesktopComposeUiTest(width = 520, height = 500) {
            showBelowTheFold(marked = false)
            onNodeWithText("חיפוש לפי משמעות").assertIsNotDisplayed()
        }

    private fun ComposeUiTest.showBelowTheFold(marked: Boolean) {
        show {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(1500.dp))
                SemanticPackCardContent(State(available = Available.Pack(pack)), actions(), marked = marked)
            }
        }
        waitForIdle()
    }
}
