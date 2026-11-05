package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** The learning split's card: the shares of each part, its books, a book opened where it was read, and its options. */
@OptIn(ExperimentalTestApi::class)
class LearningSplitCardUiTest {
    private val split =
        LearningSplit(
            mapOf(
                LearningPart.MIKRA to listOf(SplitBook(1, "בראשית", 6, 1_800, 11), SplitBook(4, "מדרש רבה", 2, 600, null)),
                LearningPart.MISHNA to listOf(SplitBook(3, "משנה ברורה", 4, 3_600, null)),
                LearningPart.TALMUD to listOf(SplitBook(2, "ברכות", 8, 7_200, 22)),
            ),
        )

    @Test
    fun `the card shows each part's share and books, and opens a book where it was read`() =
        runDesktopComposeUiTest(width = 520, height = 300) {
            val opened = mutableListOf<SplitBook>()
            var options = 0
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalAppGraph provides testAppGraph(), LocalLayoutDirection provides LayoutDirection.Rtl) {
                        LearningSplitCard(
                            split = split,
                            measure = LearningMeasure.BOTH,
                            period = LearningPeriod.MONTH,
                            onOptions = { options++ },
                            onOpen = { opened += it },
                            modifier = Modifier.size(520.dp, 300.dp),
                        )
                    }
                }
            }
            onNodeWithText("חלוקת הלימוד").assertIsDisplayed()
            onNodeWithText("4 שעות בחודש האחרון · 20 פתיחות").assertIsDisplayed()
            onNodeWithText("ברכות").assertIsDisplayed().performClick()
            waitForIdle()
            assertEquals(listOf(22L), opened.map { it.lineId })
            System.getenv("LEARNING_SPLIT_SHOT")?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it)) }
        }
}
