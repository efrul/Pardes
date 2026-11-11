package io.github.kdroidfilter.seforimapp.siddur

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IsraeliOccasionTest {
    @Test
    fun independenceAndMemorialDayMoveTogether() {
        for (date in listOf("2022-05-05", "2023-04-26", "2024-05-14", "2025-05-01")) {
            val independence = LocalDate.parse(date)
            val memorial = independence.minus(1, DateTimeUnit.DAY)
            assertEquals(IsraeliOccasion.YOM_HAATZMAUT, israeliOccasion(independence))
            assertEquals(IsraeliOccasion.YOM_HAZIKARON, israeliOccasion(memorial))
            assertNull(israeliOccasion(memorial.minus(1, DateTimeUnit.DAY)))
        }
        assertEquals(IsraeliOccasion.YOM_HAATZMAUT, israeliOccasion(LocalDate(1997, 5, 12)))
    }

    @Test
    fun jerusalemDayKeepsItsDateOnFridayAndSunday() {
        assertEquals(IsraeliOccasion.YOM_YERUSHALAYIM, israeliOccasion(LocalDate(2026, 5, 15)))
        assertEquals(IsraeliOccasion.YOM_YERUSHALAYIM, israeliOccasion(LocalDate(2022, 5, 29)))
        assertNull(israeliOccasion(LocalDate(2026, 5, 14)))
    }

    @Test
    fun customControlsHallelWithoutChangingTheWeekdayService() {
        val independence = LocalDate(2024, 5, 14)
        val memorial = LocalDate(2024, 5, 13)
        val jerusalem = LocalDate(2026, 5, 15)
        for (custom in IsraeliPrayerCustom.entries) {
            val prefs = SiddurPrefs(Nusach.ASHKENAZ, true, true, false, israeliPrayerCustom = custom)
            for (occasion in listOf(independence, jerusalem)) {
                val flags = flagsFor(occasion, SiddurPart.SHACHARIT, prefs)
                assertEquals(custom != IsraeliPrayerCustom.NO_TACHANUN, "israeli_hallel" in flags)
                assertEquals(custom == IsraeliPrayerCustom.HALLEL_WITHOUT_BLESSINGS, "hallel_without_blessings" in flags)
                assertFalse("tachanun" in flags || "musaf" in flags)
                assertEquals(SiddurPart.SHACHARIT, dayPartFor(occasion, SiddurPart.SHACHARIT, prefs))
            }
            assertTrue("tachanun" in flagsFor(memorial, SiddurPart.SHACHARIT, prefs))
            assertFalse("tachanun" in flagsFor(memorial, SiddurPart.MINCHA, prefs))
            assertFalse("israeli_hallel" in flagsFor(jerusalem, SiddurPart.MAARIV, prefs))
            assertEquals(
                custom == IsraeliPrayerCustom.DAY_AND_NIGHT_HALLEL,
                "israeli_hallel" in flagsFor(independence, SiddurPart.MAARIV, prefs),
            )
        }
    }

    @Test
    fun unblessedHallelKeepsBothPsalmsThatAreSkippedInHalfHallel() {
        val opening = "ברוך אתה ה׳ אלהינו מלך העולם"
        val closing = "יהללוך ה׳ אלהינו"
        val lines = listOf(
            opening,
            "הללויה הללו עבדי ה׳",
            "<small>בראש חודש ובחול המועד מדלגין:</small><br><b>לא</b> לנו ה׳",
            "אהבתי כי ישמע ה׳",
            closing,
            "ברוך אתה ה׳ מלך מהולל בתשבחות",
            "קדיש שלם",
        ).map(::SiddurLine)
        val unblessed = israeliHallel(lines, setOf("hallel_without_blessings"), showAll = false)
        assertEquals(listOf("הללויה הללו עבדי ה", "לא לנו ה", "אהבתי כי ישמע ה"), unblessed.map { plain(it.html) })
        val blessed = israeliHallel(lines, emptySet(), showAll = false)
        assertEquals(lines.take(6).map { plain(it.html).substringAfter("מדלגין:") }, blessed.map { plain(it.html) })
    }
}
