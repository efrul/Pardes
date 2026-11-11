package io.github.kdroidfilter.seforimapp.siddur

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorahReadingsTest {
    private val ashkenaz = SiddurPrefs(Nusach.ASHKENAZ, inIsrael = true, minyan = true, beitAvel = false)

    @Test
    fun weekdayAndShabbatAfternoonHaveThreeAliyot() {
        val monday = readingsFor(LocalDate(2026, 11, 2), SiddurPart.SHACHARIT, ashkenaz).single()
        assertEquals(listOf("1", "2", "3"), monday.passages.map(ReadingPassage::aliyah))
        assertEquals(VerseAddress(23, 1), monday.passages.first().first)
        val mincha = readingsFor(LocalDate(2026, 11, 7), SiddurPart.MINCHA_SHABBAT, ashkenaz).single()
        assertEquals(listOf("1", "2", "3"), mincha.passages.map(ReadingPassage::aliyah))
        assertEquals(VerseAddress(25, 19), mincha.passages.first().first)
    }

    @Test
    fun shabbatZachorUsesSpecialMaftirAndHaftarahByNusach() {
        val date = LocalDate(2027, 3, 20)
        val readings = readingsFor(date, SiddurPart.SHACHARIT_SHABBAT, ashkenaz)
        val maftir = readings.single { it.kind == ReadingKind.TORAH }.passages.last()
        assertEquals("M", maftir.aliyah)
        assertEquals("דברים", maftir.bookTitle)
        assertEquals(VerseAddress(25, 17), maftir.first)
        assertEquals(VerseAddress(15, 2), readings.single { it.kind == ReadingKind.HAFTARAH }.passages.first().first)
        val edot = readingsFor(date, SiddurPart.SHACHARIT_SHABBAT, ashkenaz.copy(nusach = Nusach.EDOT_HAMIZRACH))
        assertEquals(VerseAddress(15, 1), edot.single { it.kind == ReadingKind.HAFTARAH }.passages.first().first)
    }

    @Test
    fun chanukahAndRoshChodeshUseOneCombinedReading() {
        val readings = readingsFor(LocalDate(2026, 12, 11), SiddurPart.SHACHARIT, ashkenaz)
        assertEquals(4, readings.single().passages.size)
        assertEquals(VerseAddress(28, 1), readings.single().passages.first().first)
        assertEquals(VerseAddress(7, 48), readings.single().passages.last().first)
    }

    @Test
    fun eighthDayOfPesachOnlyUsesFestivalReadingOutsideIsrael() {
        val date = LocalDate(2027, 4, 29)
        val israel = readingsFor(date, SiddurPart.SHACHARIT, ashkenaz)
        val diaspora = readingsFor(date, SiddurPart.SHACHARIT_YOM_TOV, ashkenaz.copy(inIsrael = false))
        assertEquals(3, israel.single().passages.size)
        assertTrue(diaspora.any { it.kind == ReadingKind.HAFTARAH })
    }

    @Test
    fun walledPurimUsesTheCorrectNightAndMegillahWorksWithoutMinyan() {
        val walled = ashkenaz.copy(mukafChoma = true, minyan = false)
        assertTrue(readingsFor(LocalDate(2027, 3, 23), SiddurPart.MAARIV, walled).isEmpty())
        val megillah = readingsFor(LocalDate(2027, 3, 24), SiddurPart.MAARIV, walled).single()
        assertEquals(ReadingKind.MEGILLAH, megillah.kind)
        assertEquals("אסתר", megillah.passages.first().bookTitle)
    }

    @Test
    fun minorFastMinchaHasHaftarahForAshkenazAndSefard() {
        val date = LocalDate(2026, 7, 2)
        val ashkenazReadings = readingsFor(date, SiddurPart.MINCHA, ashkenaz)
        assertEquals(listOf(ReadingKind.TORAH, ReadingKind.HAFTARAH), ashkenazReadings.map(SiddurReading::kind))
        val edot = readingsFor(date, SiddurPart.MINCHA, ashkenaz.copy(nusach = Nusach.EDOT_HAMIZRACH))
        assertEquals(ReadingKind.TORAH, edot.single().kind)
        assertEquals(VerseAddress(32, 11), edot.single().passages.first().first)
    }
}
