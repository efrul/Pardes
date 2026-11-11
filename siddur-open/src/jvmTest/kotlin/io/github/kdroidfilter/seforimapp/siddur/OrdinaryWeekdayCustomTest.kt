package io.github.kdroidfilter.seforimapp.siddur

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The custom that keeps the State of Israel's days in Iyar as ordinary weekdays (added in Pardes's copy). */
class OrdinaryWeekdayCustomTest {
    private val independence = LocalDate(2024, 5, 14)
    private val memorial = LocalDate(2024, 5, 13)
    private val jerusalem = LocalDate(2026, 5, 15)
    private val ordinary =
        SiddurPrefs(Nusach.ASHKENAZ, true, true, false, israeliPrayerCustom = IsraeliPrayerCustom.ORDINARY_WEEKDAY)
    private val noTachanun = ordinary.copy(israeliPrayerCustom = IsraeliPrayerCustom.NO_TACHANUN)

    @Test
    fun `the days are weekdays like any other, with Tachanun and no Hallel`() {
        // Ordinary weekdays of Iyar beside each: the Wednesday after, and the Friday before
        val weekdays = listOf(LocalDate(2024, 5, 15), LocalDate(2026, 5, 8))
        for ((day, weekday) in listOf(independence, jerusalem).zip(weekdays)) {
            for (part in listOf(SiddurPart.SHACHARIT, SiddurPart.MINCHA, SiddurPart.MAARIV)) {
                val flags = flagsFor(day, part, ordinary)
                assertFalse("israeli_hallel" in flags || "hallel_shalem" in flags, "$day $part")
                assertFalse(flags.any { it.startsWith("yom_") }, "$day $part")
                assertEquals("tachanun" in flagsFor(weekday, part, ordinary), "tachanun" in flags, "$day $part")
            }
            assertTrue("tachanun" in flagsFor(day, SiddurPart.SHACHARIT, ordinary), "$day")
            assertTrue(readingsFor(day, SiddurPart.SHACHARIT, ordinary).none { it.kind == ReadingKind.HAFTARAH }, "$day")
        }
    }

    @Test
    fun `Mincha of the day before keeps its Tachanun, and the day before has no name`() {
        assertTrue("tachanun" in flagsFor(memorial, SiddurPart.MINCHA, ordinary))
        assertFalse("yom_hazikaron" in flagsFor(memorial, SiddurPart.SHACHARIT, ordinary))
        // The customs that keep the days are as they were
        assertFalse("tachanun" in flagsFor(memorial, SiddurPart.MINCHA, noTachanun))
        assertTrue("yom_hazikaron" in flagsFor(memorial, SiddurPart.SHACHARIT, noTachanun))
        assertFalse("tachanun" in flagsFor(independence, SiddurPart.SHACHARIT, noTachanun))
    }

    @Test
    fun `no custom of the days says Hallel under it, and the engine's own default is unchanged`() {
        for (occasion in IsraeliOccasion.entries) {
            for (part in SiddurPart.entries) {
                assertFalse(IsraeliPrayerCustom.ORDINARY_WEEKDAY.saysHallel(occasion, part), "$occasion $part")
            }
        }
        assertFalse(IsraeliPrayerCustom.ORDINARY_WEEKDAY.festive)
        assertEquals(IsraeliPrayerCustom.NO_TACHANUN, SiddurPrefs(Nusach.ASHKENAZ, true, true, false).israeliPrayerCustom)
    }
}
