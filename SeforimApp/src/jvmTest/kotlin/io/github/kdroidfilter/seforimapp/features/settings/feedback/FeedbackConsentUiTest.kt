package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The question at start: the kinds as the user checks them go with the answer, and agreeing to nothing is not offered. */
@OptIn(ExperimentalTestApi::class)
class FeedbackConsentUiTest {
    @Test
    fun `the answer carries the kinds checked, and agreeing needs one`() =
        runDesktopComposeUiTest(width = 540, height = 520) {
            val answers = mutableListOf<Pair<Boolean, FeedbackChoice>>()
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FeedbackConsentContent(initial = FeedbackChoice(), onAnswer = { agree, choice -> answers += agree to choice })
                    }
                }
            }
            // The searches, not checked at first, checked; the grades and the daf unchecked
            onNodeWithText("היסטוריית החיפושים").performClick()
            onNodeWithText("הציונים שנתתם לתוצאות החיפוש").performClick()
            onNodeWithText("הסימונים של שורות בצורת הדף").performClick()
            onNodeWithText("אישור").assertIsEnabled().performClick()
            assertEquals(listOf(true to FeedbackChoice(grades = false, searches = true, daf = false)), answers)

            onNodeWithText("היסטוריית החיפושים").performClick()
            onNodeWithText("אישור").assertIsNotEnabled()
            onNodeWithText("לא, תודה").performClick()
            assertEquals(false to FeedbackChoice(grades = false, searches = false, daf = false), answers.last())
        }
}
