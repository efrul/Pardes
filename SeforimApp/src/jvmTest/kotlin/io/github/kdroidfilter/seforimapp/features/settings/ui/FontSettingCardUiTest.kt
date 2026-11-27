package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_font_book_label
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A list of fonts with two fonts of the user's of one family's name: their list opened, and the second one chosen by
 * its own code. Jewel's list keyed its rows by their names, and two rows of one name closed the app as it opened.
 */
@OptIn(ExperimentalTestApi::class)
class FontSettingCardUiTest {
    @Test
    fun `two fonts of the same name are listed, and each is chosen by its own code`() =
        runDesktopComposeUiTest(width = 640, height = 480) {
            val chosen = mutableListOf<String>()
            val catalog = FontCatalog.options.first().code
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FontSettingCard(
                            label = Res.string.settings_font_book_label,
                            fontCode = catalog,
                            options = persistentListOf(catalog, "user:David.ttf", "user:David-2.ttf"),
                            optionLabels = persistentListOf("קטלוג", "David", "David"),
                            onFontChange = { chosen += it },
                        )
                    }
                }
            }

            onNodeWithText("קטלוג").performClick()
            waitForIdle()
            onAllNodesWithText("David")[1].performClick()
            waitForIdle()

            assertEquals(listOf("user:David-2.ttf"), chosen)
        }
}
