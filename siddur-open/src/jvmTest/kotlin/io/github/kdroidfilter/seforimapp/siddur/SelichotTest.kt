package io.github.kdroidfilter.seforimapp.siddur

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SelichotTest {
    private val ashkenaz = SiddurPrefs(Nusach.ASHKENAZ, inIsrael = true, minyan = true, beitAvel = false)
    private val edot = ashkenaz.copy(nusach = Nusach.EDOT_HAMIZRACH)

    @Test
    fun elulStartsAccordingToNusach() {
        val earlyElul = LocalDate(2026, 8, 16)
        assertNotNull(seasonalSelichot(earlyElul, edot))
        assertNull(seasonalSelichot(earlyElul, ashkenaz))
        assertNull(seasonalSelichot(LocalDate(2026, 9, 5), ashkenaz))
        assertEquals("ליום ראשון", seasonalSelichot(LocalDate(2026, 9, 6), ashkenaz)?.key)
        assertEquals("לערב ראש השנה", seasonalSelichot(LocalDate(2026, 9, 11), ashkenaz)?.key)
    }

    @Test
    fun tenDaysSkipShabbatAndUseTheDatedServices() {
        assertNull(seasonalSelichot(LocalDate(2026, 9, 12), edot))
        assertEquals("לצום גדליה", seasonalSelichot(LocalDate(2026, 9, 14), ashkenaz)?.key)
        assertEquals("ליום שני דעשי\"ת", seasonalSelichot(LocalDate(2026, 9, 15), ashkenaz)?.key)
        assertNull(seasonalSelichot(LocalDate(2026, 9, 19), edot))
        assertEquals("לערב יום כיפור", seasonalSelichot(LocalDate(2026, 9, 20), ashkenaz)?.key)
        assertNull(seasonalSelichot(LocalDate(2026, 9, 21), ashkenaz))
    }

    @Test
    fun publicFastSelectsDedicatedSelichotButTishaBavDoesNot() {
        assertEquals("סליחות/שבעה עשר בתמוז", fastSelichot(LocalDate(2026, 7, 2), ashkenaz)?.key)
        assertEquals("סליחות לי\"ז בתמוז", fastSelichot(LocalDate(2026, 7, 2), edot)?.key)
        assertNull(fastSelichot(LocalDate(2026, 7, 23), ashkenaz))
    }
}
