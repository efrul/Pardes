package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafMark
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReviewFile
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The send-feedback card: each kind with its count, the queries to leave out, and saving only when something is kept. */
@OptIn(ExperimentalTestApi::class)
class FeedbackSendCardUiTest {
    private val contents =
        FeedbackContents(
            grades =
                listOf(
                    GradeRow("שנים אוחזין", "search::שנים אוחזין", 10, 7, 3, 0, gradedAt = 100),
                    GradeRow("שנים אוחזין", "search::שנים אוחזין", 11, 7, 0, 1, gradedAt = 110),
                    GradeRow("מאימתי", "search::מאימתי", 12, 8, 2, 4, gradedAt = 120),
                ),
            searches = listOf(SearchRow("מאימתי", lastAt = 130)),
            daf =
                listOf(
                    DafReviewFile(
                        tractate = "Berakhot",
                        tractateHe = "ברכות",
                        marks = listOf(DafMark("2a", "g", 1, "שורה", ok = true)),
                    ),
                ),
        )

    @Test
    fun `a query left out leaves the counts, and saving waits for something kept`() =
        runDesktopComposeUiTest(width = 640, height = 900) {
            var saves = 0
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        var state by remember {
                            mutableStateOf(
                                FeedbackSendViewModel.State(contents = contents, sender = "3f2a9c1e-0000-4000-8000-000000000000"),
                            )
                        }

                        fun choose(change: (FeedbackChoice) -> FeedbackChoice) {
                            state = state.copy(choice = change(state.choice))
                        }
                        FeedbackSendCardContent(
                            state = state,
                            actions =
                                FeedbackSendActions(
                                    grades = { on -> choose { it.copy(grades = on) } },
                                    daf = { on -> choose { it.copy(daf = on) } },
                                    query = { query, kept ->
                                        choose { it.copy(leftOut = if (kept) it.leftOut - query else it.leftOut + query) }
                                    },
                                    save = { saves++ },
                                ),
                        )
                    }
                }
            }
            onNodeWithText("ציונים לתוצאות החיפוש: 3 (בחיפושים: 2)").assertIsDisplayed()
            onNodeWithText("היסטוריית החיפושים: 1").assertIsDisplayed()
            onNodeWithText("סימונים של שורות בצורת הדף: 1 (במסכתות: 1)").assertIsDisplayed()
            onNodeWithText("שמירה לקובץ…").assertIsEnabled()

            // The searches are off at first: the queries to look over are those of the grades
            onNodeWithText("עיון בחיפושים שיישלחו (2 מתוך 2)").performClick()
            onNodeWithText("שנים אוחזין").performClick()
            onNodeWithText("ציונים לתוצאות החיפוש: 1 (בחיפושים: 1)").assertIsDisplayed()
            onNodeWithText("עיון בחיפושים שיישלחו (1 מתוך 2)").assertIsDisplayed()

            // The last query and the daf left out: nothing to save
            onNodeWithText("מאימתי").performClick()
            onNodeWithText("סימונים של שורות בצורת הדף: 1 (במסכתות: 1)").performClick()
            onNodeWithText("שמירה לקובץ…").assertIsNotEnabled()
            onNodeWithText("מאימתי").performClick()
            onNodeWithText("שמירה לקובץ…").performClick()
            waitForIdle()
            assertEquals(1, saves)
        }

    @Test
    fun `nothing new since the last package says when it was`() =
        runDesktopComposeUiTest(width = 640, height = 600) {
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FeedbackSendCardContent(
                            state = FeedbackSendViewModel.State(contents = FeedbackContents(), lastSent = 1_762_336_800_000, sender = "s"),
                            actions = FeedbackSendActions(),
                        )
                    }
                }
            }
            onNodeWithText("אין משוב חדש מאז הקובץ הקודם", substring = true).assertIsDisplayed()
            onNodeWithText("הכל מההתחלה, לא רק מה שחדש מאז הקובץ הקודם").assertIsDisplayed()
            onNodeWithText("שמירה לקובץ…").assertIsNotEnabled()
        }
}
