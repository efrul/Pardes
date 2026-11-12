package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** The study plans' card: each plan's part of the day, its pace and day, the tick once learnt, and a click to learn it. */
@OptIn(ExperimentalTestApi::class)
class StudyCardUiTest {
    private val start = LocalDate.of(2026, 11, 12)

    private fun shown(
        id: Long,
        title: String,
        headings: List<List<String>>,
        name: UnitName,
        part: DayPart,
        perDay: Int = 1,
        weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    ) = ShownPlan(
        plan =
            StudyPlan(
                id,
                bookId = id,
                bookTitle = title,
                level = 1,
                start = headings.first(),
                perDay = perDay,
                startDay = start,
                weekdays = weekdays,
            ),
        book = Book(id = id, categoryId = 1, sourceId = 1, title = title),
        name = name,
        units = headings.mapIndexed { i, h -> StudyUnit(i.toLong(), 1, h, i.toLong()) },
        part = part,
    )

    private val plans =
        listOf(
            shown(
                1,
                "תהילים",
                (23..27).map { listOf("פרק ${"כג כד כה כו כז".split(' ')[it - 23]}") },
                UnitName("פרק", "פרקים"),
                DayPart.Part((0..4).toList(), day = 1, progress = 27f / 150),
                perDay = 5,
                weekdays = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY,
            ),
            shown(
                2,
                "משנה ברורה",
                listOf(listOf("סימן קסח")),
                UnitName("סימן", "סימנים"),
                DayPart.Part(listOf(0), day = 41, progress = 0.24f),
            ),
            shown(3, "ספר החינוך", listOf(listOf("מצוה א")), UnitName("מצוה", "מצוות"), DayPart.Rest),
        )

    @Test
    fun `the card shows the day's part of each plan, and a tick or a click acts on it`() =
        runDesktopComposeUiTest(width = 420, height = 360) {
            val opened = mutableListOf<Long>()
            val ticked = mutableListOf<Pair<Long, Boolean>>()
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalAppGraph provides testAppGraph(), LocalLayoutDirection provides LayoutDirection.Rtl) {
                        StudyCard(
                            plans = plans,
                            done = setOf(2L),
                            today = true,
                            onDone = { plan, checked -> ticked += plan.id to checked },
                            onOpen = { opened += it.plan.id },
                            onEdit = {},
                            onNew = {},
                            modifier = Modifier.size(420.dp, 360.dp),
                        )
                    }
                }
            }
            onNodeWithText("הלימוד שלי").assertIsDisplayed()
            onNodeWithText("פרקים כג–כז").assertIsDisplayed()
            onNodeWithText("5 פרקים ביום, חוץ משבת ויום טוב · יום 1").assertIsDisplayed()
            onNodeWithText("סימן ביום · סיימת להיום").assertIsDisplayed()
            onNodeWithText("אין לימוד היום").assertIsDisplayed()
            onNodeWithText("פרקים כג–כז").performClick()
            waitForIdle()
            assertEquals(listOf(1L), opened)
            onNodeWithTag("study-new-plan").assertIsDisplayed()
            System.getenv("STUDY_CARD_SHOT")?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it)) }
            assertEquals(emptyList(), ticked)
        }
}
