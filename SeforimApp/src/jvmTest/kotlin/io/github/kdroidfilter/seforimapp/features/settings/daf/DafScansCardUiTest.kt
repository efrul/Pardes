package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The page of each tractate's daf ב. in the files of the user's folder, set in the settings rather than over the scan,
 * one or several at once (request 65 of the list after the third testers' build).
 */
@OptIn(ExperimentalTestApi::class)
class DafScansCardUiTest {
    private val joined = "18 - עבודה זרה הוריות.pdf"
    private val rows =
        listOf(
            FirstPage("ברכות.pdf|ברכות", "ברכות", "ברכות.pdf", page = 0, set = false),
            FirstPage("$joined|עבודה זרה", "עבודה זרה", joined, page = 2, set = true),
            FirstPage("$joined|הוריות", "הוריות", joined, page = 152, set = false),
        )

    private fun label(row: FirstPage) = "${row.tractate}, בקובץ «${row.file}»: דף ב. בעמוד"

    @Test
    fun `every tractate has its page, guessed or set, and several are set one page at once`() =
        runDesktopComposeUiTest(width = 760, height = 520) {
            val set = mutableListOf<Pair<String, Int?>>()
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FirstPagesContent(rows, onSet = { key, page -> set += key to page })
                    }
                }
            }
            onAllNodesWithText("לפי ניחוש").assertCountEquals(2)
            onNodeWithText("חזרה לניחוש").performClick()
            assertEquals(listOf<Pair<String, Int?>>(rows[1].key to null), set, "the page set, guessed again")
            set.clear()

            onNodeWithText(label(rows[0])).performClick()
            onNodeWithText(label(rows[2])).performClick()
            // The field of the tractates checked, under their rows
            onAllNodes(hasSetTextAction())[rows.size].performTextInput("3")
            onNodeWithText("קביעה").performClick()
            assertEquals(setOf<Pair<String, Int?>>(rows[0].key to 2, rows[2].key to 2), set.toSet(), "the third page, from 0")
        }

    @Test
    fun `a page typed beside a tractate is saved on Enter, not at each key`() =
        runDesktopComposeUiTest(width = 760, height = 520) {
            val set = mutableListOf<Pair<String, Int?>>()
            setContent {
                IntUiTheme(isDark = false) {
                    FirstPagesContent(rows, onSet = { key, page -> set += key to page })
                }
            }
            val field = onAllNodes(hasSetTextAction())[0]
            field.performTextReplacement("15")
            assertEquals(emptyList<Pair<String, Int?>>(), set)
            field.performKeyInput { pressKey(Key.Enter) }
            assertEquals(listOf<Pair<String, Int?>>(rows[0].key to 14), set)
        }

    @Test
    fun `a page is typed from 1`() {
        assertEquals(0, pageTyped(" 1 "))
        assertEquals(211, pageTyped("212"))
        assertNull(pageTyped("0"))
        assertNull(pageTyped("ב"))
    }
}
