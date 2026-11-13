package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafMark
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReviewFile
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The feedback page: what waits, each kind with its count and the queries to leave out behind a row that opens, saving
 * only when something is kept, and the switches of the automatic sending and of the grade buttons.
 */
@OptIn(ExperimentalTestApi::class)
class FeedbackSettingsUiTest {
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
                        FeedbackSettingsContent(
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
            // What waits is said at once; each kind, once its row is opened
            onNodeWithText("מחכים לשליחה: ציונים 3, חיפושים 1, סימונים בצורת הדף 1.").assertIsDisplayed()
            onAllNodesWithText("ציונים לתוצאות החיפוש: 3 (בחיפושים: 2)").assertCountEquals(0)
            onNodeWithText("מה נשלח").performClick()
            onNodeWithText("ציונים לתוצאות החיפוש: 3 (בחיפושים: 2)").assertIsDisplayed()
            onNodeWithText("היסטוריית החיפושים: 1").assertIsDisplayed()
            onNodeWithText("סימונים של שורות בצורת הדף: 1 (במסכתות: 1)").assertIsDisplayed()
            onNodeWithText("שמירה לקובץ…").assertIsEnabled()

            // The queries to look over: the grades' and the searches', each once
            onNodeWithText("עיון בחיפושים שיישלחו (2 מתוך 2)").performClick()
            onNodeWithText("שנים אוחזין").performClick()
            onNodeWithText("ציונים לתוצאות החיפוש: 1 (בחיפושים: 1)").assertIsDisplayed()
            onNodeWithText("עיון בחיפושים שיישלחו (1 מתוך 2)").assertIsDisplayed()
            onNodeWithText("מחכים לשליחה: ציונים 1, חיפושים 1, סימונים בצורת הדף 1.").assertIsDisplayed()

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
    fun `with the service, sending now, the automatic sending, and deleting what was sent once the user says yes`() =
        runDesktopComposeUiTest(width = 640, height = 1000) {
            var sends = 0
            var deletes = 0
            val auto = mutableListOf<Boolean>()
            val buttons = mutableListOf<Boolean>()
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FeedbackSettingsContent(
                            state =
                                FeedbackSendViewModel.State(
                                    contents = contents,
                                    sender = "s",
                                    online = true,
                                    lastSent = 1_762_336_800_000,
                                ),
                            actions =
                                FeedbackSendActions(
                                    send = { sends++ },
                                    auto = { auto += it },
                                    buttons = { buttons += it },
                                    deleteSent = { deletes++ },
                                ),
                        )
                    }
                }
            }
            onNodeWithText("שליחה עכשיו").performClick()
            onNodeWithText("הפעם הקודמת:", substring = true).assertIsDisplayed()
            switchOf("שליחה אוטומטית, בכל הפעלה ובכל חיבור לרשת").performClick()
            switchOf("משוב על תוצאות החיפוש").performClick()
            onNodeWithText("פרטיות ומחיקה").performClick()
            onNodeWithText("מחיקת מה ששלחתי מהשרת").performClick()
            onNodeWithText("אי אפשר לבטל את זה", substring = true).assertIsDisplayed()
            onNodeWithText("מחיקה").performClick()
            waitForIdle()
            assertEquals(listOf(1, 1), listOf(sends, deletes))
            assertEquals(listOf(true), auto)
            assertEquals(listOf(true), buttons)
        }

    @Test
    fun `nothing new since the last package says when it was`() =
        runDesktopComposeUiTest(width = 640, height = 600) {
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FeedbackSettingsContent(
                            state = FeedbackSendViewModel.State(contents = FeedbackContents(), lastSent = 1_762_336_800_000, sender = "s"),
                            actions = FeedbackSendActions(),
                        )
                    }
                }
            }
            onNodeWithText("אין משוב חדש מאז הפעם הקודמת", substring = true).assertIsDisplayed()
            onNodeWithText("מה נשלח").performClick()
            onNodeWithText("הכל מההתחלה, לא רק מה שחדש מאז הפעם הקודמת").assertIsDisplayed()
            onNodeWithText("שמירה לקובץ…").assertIsNotEnabled()
        }

    // The checkbox of the row titled [title] (SettingCard: its title stands beside it, and is not part of it)
    private fun ComposeUiTest.switchOf(title: String) = onNode(isToggleable() and hasAnySibling(hasText(title)))
}
