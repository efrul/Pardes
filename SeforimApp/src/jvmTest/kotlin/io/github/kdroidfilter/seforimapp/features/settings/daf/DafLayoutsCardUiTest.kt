package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafDownloads
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafManifest
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReleases
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The daf layouts' card: each tractate with its version in the folder and the release's, one downloaded alone or all
 * that are not the release's at once. Its drawing is written to build/reports/daf/ to look at.
 */
@OptIn(ExperimentalTestApi::class)
class DafLayoutsCardUiTest {
    private fun entry(
        tractate: String,
        he: String,
        version: Int,
    ) = DafManifest.Entry(tractate, he, "$tractate.json.gz", 600_000, "a", "b", amudim = 100, version = version)

    private val berakhot = entry("Berakhot", "ברכות", 3)
    private val bavaBatra = entry("Bava Batra", "בבא בתרא", 2)
    private val keritot = entry("Keritot", "כריתות", 1)
    private val state =
        DafDownloads.State(
            checked = true,
            release = DafReleases.Available("daf-7", 7, null, emptyMap()),
            manifest = DafManifest(format = 1, daf = 7, tractates = listOf(berakhot, bavaBatra, keritot)),
            installed = mapOf("Berakhot" to DafDownloads.Installed.CURRENT, "Bava Batra" to DafDownloads.Installed.OTHER),
            versions = mapOf("Berakhot" to 3, "Bava Batra" to 1),
        )

    @Test
    fun `a tractate has a row with its versions, and is downloaded alone or with the others`() =
        runDesktopComposeUiTest(width = 640, height = 520) {
            val asked = mutableListOf<List<String>>()
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        DafLayoutsCardContent(
                            state = state,
                            onDownload = { entries -> asked += entries.map { it.tractate } },
                            onCancel = {},
                            onRetry = {},
                        )
                    }
                }
            }
            onNodeWithText("מותקנות 1 מתוך 3 המסכתות שבגרסה 7 של הקבצים.").assertIsDisplayed()
            // Those to download are listed, each with its own link; a tractate that is the release's only when asked
            onNodeWithText("בבא בתרא · מותקנת גרסה 1 · זמינה גרסה 2").assertIsDisplayed()
            onNodeWithText("כריתות · לא מותקנת · זמינה גרסה 1").assertIsDisplayed()
            assertEquals(0, onAllNodesWithText("ברכות · גרסה 3").fetchSemanticsNodes().size)
            onNodeWithText("עדכון").performClick()
            onNodeWithText("הורדה").performClick()
            onNodeWithText("הורדת 2 מסכתות", substring = true).performClick()
            waitForIdle()
            assertEquals(listOf(listOf("Bava Batra"), listOf("Keritot"), listOf("Bava Batra", "Keritot")), asked)

            onNodeWithText("כל המסכתות והגרסאות שלהן").performClick()
            onNodeWithText("ברכות · גרסה 3").assertIsDisplayed()
            val out = File("build/reports/daf/layouts-card.png")
            out.parentFile.mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            onNodeWithText("הסתרת רשימת המסכתות").performClick()
            assertEquals(0, onAllNodesWithText("ברכות · גרסה 3").fetchSemanticsNodes().size)
        }

    @Test
    fun `a release of before the versions names its tractates without one`() =
        runDesktopComposeUiTest(width = 640, height = 400) {
            val plain = state.copy(manifest = state.manifest?.copy(tractates = state.manifest.tractates.map { it.copy(version = null) }))
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        DafLayoutsCardContent(state = plain.copy(versions = emptyMap()), onDownload = {}, onCancel = {}, onRetry = {})
                    }
                }
            }
            onNodeWithText("בבא בתרא · מותקן קובץ אחר").assertIsDisplayed()
            onNodeWithText("כריתות · לא מותקנת").assertIsDisplayed()
        }
}
