package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A drag past the end of a right-to-left row, into the margin, closed the app when a text lay over the line (the find
 * bar's texts over the book's first lines): Compose crossed the selection by its layout's slots, against its offsets,
 * and drew it reversed ("Start(3638) or End(3594) is out of range"). Fixed by the build's SelectionDrawPatch (buildSrc).
 */
@OptIn(ExperimentalTestApi::class)
class CrossedSelectionUiTest {
    @Test
    fun `a drag past a right-to-left row's end beside a text laid over the line is drawn`() =
        runComposeUiTest {
            val state = SelectionState()
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SelectionContainer(state, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            // A line of the book, its margin at its end (the left), as in BookContentView's list
                            BasicText(PARAGRAPH, Modifier.fillMaxWidth().padding(end = 24.dp).testTag("line"))
                            // Below the line's top, over its first row, as the find bar's counter
                            BasicText("3/17", Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                        }
                    }
                }
            }

            onNodeWithTag("line").performMouseInput {
                // From the middle of the third row, below the counter, past the row's end into the margin
                moveTo(Offset(width / 2f, 45f))
                press()
                moveTo(Offset(-10f, 45f))
            }
            waitForIdle()
            onNodeWithTag("line").performMouseInput { release() }

            assertTrue(runOnIdle { state.selectedTexts.joinToString() }.isNotEmpty(), "the row's end selected")
        }

    private companion object {
        val PARAGRAPH = List(30) { "אמר רבי יוחנן משום רבי שמעון בן יוחאי" }.joinToString(" ")
    }
}
