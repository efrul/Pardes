package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** A tree whose rows have checkboxes (a search's tree of books): Space turns the checkbox of the keys' row. */
@OptIn(ExperimentalTestApi::class)
class TreeKeysCheckUiTest {
    @Test
    fun `Space checks the row the keys are on, and does nothing on a row with no checkbox`() =
        runComposeUiTest {
            val checked = ArrayList<String>()
            val names = listOf("a", "b", "c")
            setContent {
                val keys = rememberTreeKeys(null)
                val listState = rememberLazyListState()
                val rows =
                    names.map { name ->
                        TreeKeyRow(name, TreeNode(level = 0), check = { checked += name }.takeIf { name != "c" }) {}
                    }
                Box(Modifier.testTag("tree").size(200.dp).treeKeys(keys, listState, rows)) {
                    LazyColumn(state = listState) {
                        items(names, key = { it }) { Box(Modifier.fillMaxWidth().height(20.dp)) }
                    }
                }
            }
            onNodeWithTag("tree").requestFocus()
            onNodeWithTag("tree").performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionDown)
                pressKey(Key.Spacebar)
                pressKey(Key.DirectionDown)
                pressKey(Key.Spacebar)
            }
            assertEquals(listOf("b"), checked)
        }

    // The window on Linux gives a printed key as its character alone, with no key press (seen in the app on 2026-11-24:
    // Space reached the tree as an event of no type and no key); elsewhere the character comes after the key's own press
    @OptIn(InternalComposeUiApi::class) // a key event made by hand: the test's keys always come with their press
    @Test
    fun `Space as its character alone checks the row too, and once where it follows its own key press`() =
        runComposeUiTest {
            val checked = ArrayList<String>()
            var keys: TreeKeys? = null
            var list: LazyListState? = null
            setContent {
                val treeKeys = rememberTreeKeys(null).also { keys = it }
                val listState = rememberLazyListState().also { list = it }
                val rows = listOf(TreeKeyRow("a", TreeNode(level = 0), check = { checked += "a" }) {})
                Box(Modifier.testTag("tree").size(200.dp).treeKeys(treeKeys, listState, rows)) {
                    LazyColumn(state = listState) { items(listOf("a"), key = { it }) { Box(Modifier.fillMaxWidth().height(20.dp)) } }
                }
            }
            onNodeWithTag("tree").requestFocus()
            onNodeWithTag("tree").performKeyInput { pressKey(Key.DirectionDown) }
            val typed = KeyEvent(Key.Unknown, KeyEventType.Unknown, codePoint = ' '.code)
            runOnIdle { keys!!.onKey(typed, list!!, null) }
            assertEquals(listOf("a"), checked, "the character alone")
            runOnIdle {
                keys!!.onKey(KeyEvent(Key.Spacebar, KeyEventType.KeyDown), list!!, null)
                keys!!.onKey(typed, list!!, null)
                keys!!.onKey(KeyEvent(Key.Spacebar, KeyEventType.KeyUp), list!!, null)
            }
            assertEquals(listOf("a", "a"), checked, "the key's press, not its character again")
            runOnIdle {
                keys!!.onKey(
                    KeyEvent(Key.Unknown, KeyEventType.Unknown, codePoint = ' '.code, isShiftPressed = true),
                    list!!,
                    null,
                )
            }
            assertEquals(2, checked.size, "with Shift it is not the tree's")
        }
}
