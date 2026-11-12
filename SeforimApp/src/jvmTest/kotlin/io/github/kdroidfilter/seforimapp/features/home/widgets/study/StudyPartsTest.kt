package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.datetime.toKotlinLocalDate
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A study plan's units in a book's table of contents, and the part of each day, reckoned from the date. */
class StudyPartsTest {
    // A book's own title above two סימנים, with their סעיפים; listed out of order, as a query may give them
    private val toc =
        listOf(
            entry(5, "סעיף א", level = 2, parent = 4),
            entry(1, "משנה ברורה", level = 0, parent = null),
            entry(4, "סימן ב", level = 1, parent = 1),
            entry(3, "סעיף ב", level = 2, parent = 2),
            entry(2, "סימן א", level = 1, parent = 1),
            entry(6, "סעיף א", level = 2, parent = 2),
        )

    private fun entry(
        id: Long,
        text: String,
        level: Int,
        parent: Long?,
    ) = TocEntry(id = id, bookId = 9, parentId = parent, text = text, level = level, lineId = id * 10)

    private val everyDay = DayOfWeek.entries.toSet()
    private val notShabbat = everyDay - DayOfWeek.SATURDAY

    @Test
    fun `a book's units are its headings at one depth, in reading order, under the headings above them`() {
        assertEquals(listOf(1L, 2L, 3L, 6L, 4L, 5L), readingOrder(toc).map { it.id })
        assertEquals(
            listOf(StudyLevel(1, UnitName("סימן", "סימנים"), 2), StudyLevel(2, UnitName("סעיף", "סעיפים"), 3)),
            studyLevels(toc),
        )
        // The book's own title, alone at its depth, is not among the headings
        assertEquals(
            listOf(listOf("סימן א", "סעיף ב"), listOf("סימן א", "סעיף א"), listOf("סימן ב", "סעיף א")),
            studyUnits(toc, level = 2).map { it.headings },
        )
        assertEquals(listOf(listOf("סימן א"), listOf("סימן ב")), studyUnits(toc, level = 1).map { it.headings })
        // Each ends where the next one, or the next heading above it, starts
        assertEquals(listOf(60L, 40L, null), studyUnits(toc, level = 2).map { it.nextLineId })
        assertEquals(listOf(40L, null), studyUnits(toc, level = 1).map { it.nextLineId })
    }

    @Test
    fun `a plan starts at its unit by headings, which a new library still has`() {
        val units = studyUnits(toc, level = 2)
        assertEquals(2, startIndexOf(units, listOf("סימן ב", "סעיף א")))
        // The headings above it changed: the first unit with its own heading
        assertEquals(1, startIndexOf(units, listOf("סימן ג", "סעיף א")))
        assertEquals(0, startIndexOf(units, listOf("פרק ז")))
    }

    @Test
    fun `a depth is named by its headings' first word, the Bavli's by the amud`() {
        assertEquals(UnitName("פרק", "פרקים"), unitNameOf(listOf("פרק א", "פרק ב", "הקדמה")))
        assertEquals(UnitName("עמוד", "עמודים"), unitNameOf(listOf("דף ב.", "דף ב:", "דף ג.")))
        assertEquals(UnitName("ביאור", "יחידות"), unitNameOf(listOf("ביאור א", "ביאור ב")))
    }

    @Test
    fun `without Shabbat a plan skips Yom Tov too, the second day abroad`() {
        // The first day of Pesach 5786, a Thursday, and the second, abroad only
        val pesach = LocalDate.of(2026, 4, 2)
        assertTrue(JewishCalendar(pesach.toKotlinLocalDate(), true).isYomTovAssurBemelacha)
        assertFalse(isStudyDay(pesach, notShabbat, inIsrael = true))
        assertTrue(isStudyDay(pesach, everyDay, inIsrael = true), "a plan learnt on Shabbat is learnt on Yom Tov")
        assertTrue(isStudyDay(pesach.plusDays(1), notShabbat, inIsrael = true))
        assertFalse(isStudyDay(pesach.plusDays(1), notShabbat, inIsrael = false))
        assertTrue(isStudyDay(pesach.plusDays(3), notShabbat, inIsrael = true), "Chol HaMoed is learnt")
        assertFalse(isStudyDay(pesach.plusDays(2), notShabbat, inIsrael = true), "nor Shabbat itself")
        // Sunday to Thursday, the week of Pesach: Sunday, Monday, Tuesday, Wednesday, and not the Thursday
        val week = DayOfWeek.entries.toSet() - DayOfWeek.FRIDAY - DayOfWeek.SATURDAY
        assertEquals(4, studyDaysBetween(LocalDate.of(2026, 3, 29), pesach.plusDays(1)) { isStudyDay(it, week, true) })
    }

    @Test
    fun `the day's part goes on from where the days before left off`() {
        assertEquals(DayPart.Part(listOf(20, 21, 22, 23, 24), day = 4, progress = 25f / 150), dayPart(5, 150, 5, false, 3, true))
        assertEquals(DayPart.Rest, dayPart(5, 150, 5, false, 3, studyDay = false))
        assertEquals(DayPart.NotYet, dayPart(5, 150, 5, false, 0, true, beforeStart = true))
        // At the book's end: the last units alone, then done; or on from its start again
        assertEquals(DayPart.Part(listOf(148, 149), day = 1, progress = 1f), dayPart(148, 150, 5, false, 0, true))
        assertEquals(DayPart.Finished, dayPart(148, 150, 5, false, 1, true))
        assertEquals(DayPart.Part(listOf(148, 149, 0, 1, 2), day = 1, progress = 3f / 150), dayPart(148, 150, 5, true, 0, true))
        assertEquals(DayPart.Part(listOf(3, 4, 5, 6, 7), day = 2, progress = 8f / 150), dayPart(148, 150, 5, true, 1, true))
    }

    @Test
    fun `a part is named by its headings`() {
        fun units(vararg headings: List<String>) = headings.mapIndexed { i, h -> StudyUnit(i.toLong(), h.size, h, null) }
        assertEquals("פרק כג", partLabel(units(listOf("פרק כג"))))
        assertEquals("פרקים כג–כז", partLabel(units(listOf("פרק כג"), listOf("פרק כד"), listOf("פרק כז"))))
        assertEquals("סימן קכח, סעיפים א–ו", partLabel(units(listOf("סימן קכח", "סעיף א"), listOf("סימן קכח", "סעיף ו"))))
        assertEquals(
            "סימן קכח, סעיף ה – סימן קכט, סעיף ב",
            partLabel(units(listOf("סימן קכח", "סעיף ה"), listOf("סימן קכט", "סעיף ב"))),
        )
        assertEquals("דף ב.–ג:", partLabel(units(listOf("דף ב."), listOf("דף ב:"), listOf("דף ג."), listOf("דף ג:"))))
        assertEquals("הקדמה – סימן א", partLabel(units(listOf("הקדמה"), listOf("סימן א"))))
    }

    @Test
    fun `a plan's pace and days in words`() {
        assertEquals("פרק ביום", perDayLabel(1, UnitName("פרק", "פרקים")))
        assertEquals("6 סעיפים ביום", perDayLabel(6, UnitName("סעיף", "סעיפים")))
        assertNull(weekdaysLabel(everyDay))
        assertEquals("חוץ משבת ויום טוב", weekdaysLabel(notShabbat))
        assertEquals("בימים א, ג, ה, חוץ מיום טוב", weekdaysLabel(setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)))
        assertEquals("בימים ו, ש", weekdaysLabel(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)))
    }
}
