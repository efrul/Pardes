package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The learning split: each book in its part by its place in the library, the user's moves, and the shares. */
class LearningSplitTest {
    private val places =
        mapOf(
            1L to BookPlace("בראשית", "תנ״ך"),
            2L to BookPlace("ברכות", "תלמוד"),
            3L to BookPlace("משנה ברורה", "הלכה"),
            4L to BookPlace("מדרש רבה", "מדרש/אגדה"),
            5L to BookPlace("סידור", "סידור"),
            6L to BookPlace("תניא", "חסידות"),
        )

    private fun split(
        measure: LearningMeasure,
        overrides: Map<String, String> = emptyMap(),
    ) = runBlocking {
        splitOf(
            measure = measure,
            opened = mapOf(1L to 5L, 2L to 3L, 3L to 2L, 4L to 1L, 5L to 9L, 6L to 4L, 99L to 1L),
            read = mapOf(1L to 600L, 2L to 3_600L),
            lastLines = mapOf(1L to 11L),
            overrides = overrides,
        ) { places[it] }
    }

    @Test
    fun `each book in its part by its place, the most first, and what no part holds left out`() {
        val split = split(LearningMeasure.OPENS)
        assertEquals(listOf("בראשית", "מדרש רבה"), split.books.getValue(LearningPart.MIKRA).map { it.title })
        assertEquals(listOf("משנה ברורה"), split.books.getValue(LearningPart.MISHNA).map { it.title })
        assertEquals(listOf("תניא", "ברכות"), split.books.getValue(LearningPart.TALMUD).map { it.title })
        // The siddur and a book not in the library aren't counted
        assertEquals(15L, split.total(Amount.OPENS))
        assertEquals(mapOf(LearningPart.MIKRA to 40, LearningPart.MISHNA to 13, LearningPart.TALMUD to 47), split.percents(Amount.OPENS))
        assertEquals(
            11L,
            split.books
                .getValue(LearningPart.MIKRA)
                .first()
                .lineId,
        )
        // Not counted by time
        assertNull(split.percents(Amount.SECONDS))
    }

    @Test
    fun `the time read alone, or both, and a unit moved or left out`() {
        val time = split(LearningMeasure.TIME)
        assertEquals(4_200L, time.total(Amount.SECONDS))
        assertEquals(listOf("ברכות"), time.books.getValue(LearningPart.TALMUD).map { it.title })
        assertEquals(0L, time.total(Amount.OPENS))

        val both = split(LearningMeasure.BOTH, overrides = mapOf("חסידות" to NOT_COUNTED, "הלכה" to LearningPart.TALMUD.name))
        assertEquals(listOf("ברכות", "משנה ברורה"), both.books.getValue(LearningPart.TALMUD).map { it.title })
        assertEquals(emptyList(), both.books.getValue(LearningPart.MISHNA))
    }

    @Test
    fun `whole percents add up to a hundred, and time reads as people say it`() {
        assertEquals(listOf(34, 33, 33), wholePercents(listOf(1, 1, 1)))
        assertEquals(listOf(0, 0, 0), wholePercents(listOf(0, 0, 0)))
        assertEquals(listOf("דקה", "45 דקות", "שעה", "שעתיים", "8 שעות"), listOf(20L, 2_700L, 3_600L, 7_200L, 28_800L).map(::timeLabel))
        assertEquals("מדרש/אגדה", unitOf(listOf("מדרש", "אגדה", "מדרש רבה")))
        assertEquals("תלמוד", unitOf(listOf("תלמוד", "בבלי", "סדר זרעים")))
        assertEquals(mapOf("הלכה" to "TALMUD"), setOf("הלכה=TALMUD", "broken").toOverrides())
    }
}
