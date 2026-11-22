package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.kdroidfilter.seforim.tabs.TabType
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The title bar's popups and the home's dropdowns answer the keys (request 69 of the list after the third testers'
 * build), and every popup marks one row, the keys' or the pointer's (request 74 of the list after the public release).
 */
@OptIn(ExperimentalTestApi::class)
class PopupKeysUiTest {
    private val books = listOf("בראשית", "שמות", "ויקרא")

    @Test
    fun `up and down mark a row from the field, round its ends, Enter opens it, Escape closes the popup`() =
        runDesktopComposeUiTest(width = 360, height = 300) {
            val opened = mutableListOf<String>()
            var closed = false
            setContent {
                IntUiTheme(isDark = false) {
                    val keys = rememberPopupKeys()
                    // A header first, as the popups have theirs: the rows are the list's items 1 to 3
                    keys.rows = books.mapIndexed { i, book -> PopupKeyRow(i + 1) { opened += book } }
                    Column(Modifier.popupKeys(keys) { closed = true }) {
                        BasicTextField("", {})
                        LazyColumn(state = keys.listState) {
                            item { PopupSectionHeader("ספרים") }
                            itemsIndexed(books) { i, book ->
                                PopupRow(book, TabType.BOOK, onClick = { opened += book }, onClose = null, selected = keys.marks(i))
                            }
                        }
                    }
                }
            }
            val field = onAllNodes(hasSetTextAction())[0]
            field.performClick()
            field.performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionUp)
                pressKey(Key.Enter)
            }
            assertEquals(listOf("ויקרא"), opened, "down past the last row comes to the first, and up from it to the last")
            field.performKeyInput { pressKey(Key.Escape) }
            assertTrue(closed)
        }

    @Test
    fun `the pointer moved over a row marks it alone, and the keys go on from it`() =
        runDesktopComposeUiTest(width = 360, height = 300) {
            val opened = mutableListOf<String>()
            setContent {
                IntUiTheme(isDark = false) {
                    val keys = rememberPopupKeys()
                    keys.rows = books.mapIndexed { i, book -> PopupKeyRow(i + 1) { opened += book } }
                    Column(Modifier.popupKeys(keys) {}) {
                        BasicTextField("", {})
                        LazyColumn(Modifier.markOnPointer(keys), state = keys.listState) {
                            item { PopupSectionHeader("ספרים") }
                            itemsIndexed(books) { i, book ->
                                PopupRow(book, TabType.BOOK, onClick = { opened += book }, onClose = null, selected = keys.marks(i))
                            }
                        }
                    }
                }
            }
            val field = onAllNodes(hasSetTextAction())[0]
            field.performClick()
            // In over שמות, its first place there only noted, then a move: it is the row marked, not the first
            onNodeWithText("שמות").performMouseInput {
                moveTo(center)
                moveTo(center + Offset(4f, 0f))
                moveTo(center + Offset(8f, 0f))
            }
            field.performKeyInput { pressKey(Key.Enter) }
            assertEquals(listOf("שמות"), opened)
            field.performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.Enter)
            }
            assertEquals(listOf("שמות", "ויקרא"), opened, "the arrows go on from the row the pointer marked")
        }

    @Test
    fun `a popup's rows are marked by the keys until the pointer moves, not as a list scrolls under it`() {
        val marking = PopupMarking()
        // Its first place only noted, as IntelliJ's popups: a popup opened under a still pointer
        marking.pointerAt(Offset(8f, 20f))
        assertFalse(marking.pointerMoved)
        marking.pointerAt(Offset(10f, 20f))
        assertTrue(marking.pointerMoved)
        marking.keysMoved()
        assertTrue(marking.byKeys)
        assertFalse(marking.pointerMoved)
        // The same place again: a list scrolled under a still pointer
        marking.pointerAt(Offset(10f, 20f))
        assertTrue(marking.byKeys)
        marking.pointerAt(Offset(12f, 20f))
        assertFalse(marking.byKeys)
        assertTrue(marking.pointerMoved)
    }
}
