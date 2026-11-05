package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The question at start: every kind checked at first, the grade buttons under the grades, and agreeing needs a kind. */
@OptIn(ExperimentalTestApi::class)
class FeedbackConsentUiTest {
    private data class Answer(
        val agree: Boolean,
        val choice: FeedbackChoice,
        val buttons: Boolean,
    )

    @Test
    fun `the answer carries the kinds checked and the buttons, and agreeing needs a kind`() =
        runDesktopComposeUiTest(width = 560, height = 600) {
            val answers = mutableListOf<Answer>()
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FeedbackConsentContent(
                            initial = FeedbackChoice(),
                            onAnswer = { agree, choice, buttons -> answers += Answer(agree, choice, buttons) },
                        )
                    }
                }
            }
            // All three kinds and the buttons, checked at first
            onNodeWithText("אישור").assertIsEnabled().performClick()
            assertEquals(Answer(true, FeedbackChoice(grades = true, searches = true, daf = true), true), answers.last())

            // Without the grades, no buttons
            onNodeWithText("הציונים שנתתם לתוצאות החיפוש").performClick()
            onNodeWithText("להציג מעכשיו מתחת לכל תוצאת חיפוש את כפתורי המשוב").assertIsNotEnabled()
            onNodeWithText("אישור").performClick()
            assertEquals(Answer(true, FeedbackChoice(grades = false, searches = true, daf = true), false), answers.last())

            // Nothing checked: no agreeing; no is no, buttons or not
            onNodeWithText("היסטוריית החיפושים").performClick()
            onNodeWithText("הסימונים של שורות בצורת הדף").performClick()
            onNodeWithText("אישור").assertIsNotEnabled()
            onNodeWithText("לא, תודה").performClick()
            assertEquals(Answer(false, FeedbackChoice(grades = false, searches = false, daf = false), false), answers.last())
        }

    private fun ComposeUiTest.question(
        offerButtons: Boolean,
        answers: MutableList<Answer>,
    ) = setContent {
        IntUiTheme(isDark = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FeedbackConsentContent(
                    initial = FeedbackChoice(),
                    onAnswer = { agree, choice, buttons -> answers += Answer(agree, choice, buttons) },
                    offerButtons = offerButtons,
                )
            }
        }
    }

    @Test
    fun `the buttons are not offered to whom has them already`() =
        runDesktopComposeUiTest(width = 560, height = 600) {
            val answers = mutableListOf<Answer>()
            question(offerButtons = false, answers)
            onAllNodesWithText("להציג מעכשיו מתחת לכל תוצאת חיפוש את כפתורי המשוב").assertCountEquals(0)
            onNodeWithText("אישור").performClick()
            assertEquals(false, answers.single().buttons)
        }

    @Test
    fun `the buttons can be left off`() =
        runDesktopComposeUiTest(width = 560, height = 600) {
            val answers = mutableListOf<Answer>()
            question(offerButtons = true, answers)
            onNodeWithText("להציג מעכשיו מתחת לכל תוצאת חיפוש את כפתורי המשוב").performClick()
            onNodeWithText("אישור").performClick()
            assertEquals(Answer(true, FeedbackChoice(), false), answers.single())
        }
}
