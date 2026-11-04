package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.core.feedback.FeedbackGrade
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Feedback mode's buttons under a result: each grade, and the one given taken back by a second click. */
@OptIn(ExperimentalTestApi::class)
class SearchFeedbackButtonsUiTest {
    @Test
    fun `each button gives its grade, and the grade given is taken back by a second click`() =
        runDesktopComposeUiTest(width = 400, height = 80) {
            val given = mutableListOf<FeedbackGrade?>()
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        var grade by remember { mutableStateOf<FeedbackGrade?>(null) }
                        // As SearchResultViewModel.grade: the same grade again takes it back
                        SearchFeedbackButtons(grade, onGrade = { picked ->
                            grade = if (grade == picked) null else picked
                            given += grade
                        })
                    }
                }
            }
            onNodeWithText("בדיוק זה").assertIsDisplayed()
            onNodeWithText("רלוונטי").assertIsDisplayed()
            onNodeWithText("לא רלוונטי").performClick()
            onNodeWithText("בדיוק זה").performClick()
            onNodeWithText("בדיוק זה").performClick()
            waitForIdle()
            assertEquals(listOf(FeedbackGrade.NOT_RELEVANT, FeedbackGrade.EXACT, null), given)
        }
}
