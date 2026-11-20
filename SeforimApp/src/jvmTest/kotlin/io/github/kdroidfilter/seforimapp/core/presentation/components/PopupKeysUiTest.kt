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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.kdroidfilter.seforim.tabs.TabType
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The title bar's popups and the home's dropdowns answer the keys (request 69 of the list after the third testers' build). */
@OptIn(ExperimentalTestApi::class)
class PopupKeysUiTest {
    private val books = listOf("בראשית", "שמות", "ויקרא")

    @Test
    fun `up and down mark a row from the field, Enter opens it, Escape closes the popup`() =
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
            assertEquals(listOf("שמות"), opened, "the second row: down past the last stays on it")
            field.performKeyInput { pressKey(Key.Escape) }
            assertTrue(closed)
        }

    @Test
    fun `a dropdown's rows are marked by the keys until the pointer moves, not as a list scrolls under it`() {
        val marking = DropdownMarking()
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
