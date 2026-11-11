package io.github.kdroidfilter.seforimapp.siddur

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class DayBookTest {
    @Test
    fun minchaOnFestivalEvesOmitsTachanun() {
        val eves = listOf(LocalDate(2026, 9, 11), LocalDate(2026, 9, 20), LocalDate(2026, 9, 25), LocalDate(2027, 4, 21))
        for (nusach in Nusach.entries) {
            val prefs = SiddurPrefs(nusach, inIsrael = true, minyan = true, beitAvel = false)
            for (date in eves) {
                assertFalse("tachanun" in flagsFor(date, SiddurPart.MINCHA, prefs), "$nusach $date")
            }
            assertTrue("erev_yom_kippur" in flagsFor(LocalDate(2026, 9, 20), SiddurPart.MINCHA, prefs))
        }
    }

    @Test
    fun nightUsesTheNextHebrewDay() {
        val friday = LocalDate(2026, 11, 6)
        val saturday = LocalDate(2026, 11, 7)
        val sunset = Instant.parse("2026-11-06T15:00:00Z")
        val (date, part) = siddurOfTheHour(sunset, friday, null, null, sunset)
        val prefs = SiddurPrefs(Nusach.ASHKENAZ, inIsrael = true, minyan = true, beitAvel = false)

        assertEquals(saturday, date)
        assertEquals(SiddurPart.MAARIV_SHABBAT, dayPartFor(date, part, prefs))
        assertEquals(friday, civilDayOf(date, part))
    }

    @Test
    fun showAllRetainsTheSourceOrderAndMarksAnInactiveBlock() {
        val lines = listOf(
            "רצה יהוה אלהינו בעמך ישראל",
            "<small>בראש חדש ובחול המועד אומרים זה:</small>",
            "אלהינו ואלהי אבותינו יעלה ויבא",
            "ותחזינה עינינו בשובך לציון",
        ).map(::SiddurLine)
        assertEquals(listOf(lines.first(), lines.last()), annotateCorpus(lines, emptySet(), showAll = false))
        val all = annotateCorpus(lines, emptySet(), showAll = true)
        assertEquals(lines.map(SiddurLine::html), all.map(SiddurLine::html))
        assertEquals(listOf(false, true, true, false), all.map(SiddurLine::skipped))
    }

    @Test
    fun dayBookHidesEntireInactiveBlockWhenItsOpeningLineIsConditional() = runBlocking {
        val before = "מודים אנחנו לך"
        val opening = "<small>מודים דרבנן</small>"
        val response = "מודים אנחנו לך שאתה הוא יהוה אלהינו ואלהי אבותינו"
        val after = "ועל כלם יתברך ויתרומם שמך מלכנו תמיד לעולם ועד"
        val source = SiddurSource {
            SourceBook(
                listOf("ימי חול", "תפילת מנחה", before, opening, response, after),
                listOf(SourceHeading(0, 1, "ימי חול"), SourceHeading(1, 2, "תפילת מנחה")),
            )
        }
        val date = LocalDate(2026, 11, 6)
        val prefs = SiddurPrefs(Nusach.ASHKENAZ, inIsrael = true, minyan = false, beitAvel = false)

        val filtered = buildDayBook(date, prefs, emptySet(), showAll = false, source).single().lines
        assertEquals(listOf("תפילת מנחה", before, after), filtered.map(SiddurLine::html))

        val all = buildDayBook(date, prefs, emptySet(), showAll = true, source).single().lines
        assertEquals(listOf("תפילת מנחה", before, opening, response, after), all.map(SiddurLine::html))
        assertEquals(listOf(false, false, true, true, false), all.map(SiddurLine::skipped))
    }

    @Test
    fun edotHamizrachMaarivFiltersRoshChodeshAndSummerPassagesInWinter() = runBlocking {
        val title = "ערבית לימי החול"
        val roshChodesh = "<small>בליל ראש חודש יש הנוהגים לומר</small>"
        val psalm = "ברכי (תהילים קד) נפשי את יהוה"
        val opening = "לשם יחוד קדשא בריך הוא"
        val summer = "<small>בקיץ:</small> מוריד הטל."
        val winter = "<small>בחורף:</small> משיב הרוח ומוריד הגשם."
        val source = SiddurSource {
            SourceBook(listOf(title, roshChodesh, psalm, opening, "$summer $winter"), listOf(SourceHeading(0, 1, title)))
        }
        val date = LocalDate(2026, 11, 7)
        val prefs = SiddurPrefs(Nusach.EDOT_HAMIZRACH, inIsrael = true, minyan = true, beitAvel = false)

        val filtered = buildDayBook(date, prefs, emptySet(), showAll = false, source).single().lines
        assertEquals(listOf(title, opening, "${MARK_START}משיב הרוח ומוריד הגשם.$MARK_END"), filtered.map(SiddurLine::html))

        val all = buildDayBook(date, prefs, emptySet(), showAll = true, source).single().lines
        assertEquals(listOf(title, roshChodesh, psalm, opening, summer, winter), all.map(SiddurLine::html))
        assertEquals(listOf(false, true, true, false, true, false), all.map(SiddurLine::skipped))
    }

    @Test
    fun alHanisimInBirkatHamazonUsesItsOwnClosingText() {
        val lines = listOf(
            "נודה לך יהוה אלהינו",
            "בחנוכה ופורים אומרים",
            "על הנסים ועל הפרקן",
            "על הכל יהוה אלהינו אנחנו מודים לך",
        ).map(::SiddurLine)

        assertEquals(listOf(lines.first(), lines.last()), annotateCorpus(lines, emptySet(), showAll = false))
        val all = annotateCorpus(lines, emptySet(), showAll = true)
        assertEquals(listOf(false, true, true, false), all.map(SiddurLine::skipped))
    }

    @Test
    fun hidingAnInlineTeshuvaAdditionDoesNotHighlightTheRegularBlessing() {
        val regular = "ברוך אתה יהוה האל הקדוש."
        val addition = "המלך הקדוש."
        val line = SiddurLine("$regular<small><small>בעשרת ימי תשובה אומרים:</small> $addition</small>")

        val ordinary = annotateCorpus(listOf(line), emptySet(), showAll = false).single()
        assertEquals(SiddurLine(regular), ordinary)
        val showAll = annotateCorpus(listOf(line), emptySet(), showAll = true).single()
        assertFalse(showAll.special)
        assertEquals(line.html.replace(regular, "$regular$SKIP_START") + SKIP_END, showAll.html)

        val teshuva = annotateCorpus(listOf(line), setOf("aseret_yemei_teshuva"), showAll = false).single()
        assertTrue(teshuva.special)
        assertEquals("$regular$MARK_START$addition$MARK_END", teshuva.html)
    }

    @Test
    fun saturdayKeepsTheThirdMealAndGroupsTheDifferentNightBlessings() = runBlocking {
        val retzeh = "רצה והחליצנו יהוה אלהינו"
        val meeinRetzeh = "<small><small>בשבת</small> ורצה והחליצנו ביום השבת הזה.</small>"
        val headingsAndLines = listOf(
            "סדר סעודה שניה" to listOf("ושמרו בני ישראל את השבת"),
            "מנחה של שבת" to listOf("אשרי יושבי ביתך"),
            "סעודה שלישית" to listOf("מזמור לדוד"),
            "ערבית לימי החול" to listOf("ברכו", "תפילת ערבית לימי החול", "ברכו את יהוה המברך"),
            "ברכת המזון" to listOf("ברוך אתה יהוה הזן את הכל", "<small>בשבת אומרים</small>", retzeh),
            "ברכת מעין שלוש" to listOf("<small>מזונות</small> ועל המחיה", "<small>יין</small> ועל הגפן", meeinRetzeh),
        )
        val source = SiddurSource {
            val lines = mutableListOf<String>()
            val headings = mutableListOf<SourceHeading>()
            for ((title, body) in headingsAndLines) {
                headings += SourceHeading(lines.size, 1, title)
                lines += title
                if (title == "ערבית לימי החול") headings += SourceHeading(lines.size, 2, "ברכו")
                lines += body
            }
            SourceBook(lines, headings)
        }
        val date = LocalDate(2026, 11, 7)
        val prefs = SiddurPrefs(Nusach.EDOT_HAMIZRACH, inIsrael = true, minyan = true, beitAvel = false)
        val sections = buildDayBook(date, prefs, emptySet(), showAll = false, source)
        assertEquals(
            listOf(
                SiddurPart.SEUDAT_SHABBAT, SiddurPart.MINCHA_SHABBAT, SiddurPart.MAARIV,
                SiddurPart.BIRKAT_HAMAZON, SiddurPart.BIRKAT_HAMAZON, SiddurPart.MEEIN_SHALOSH, SiddurPart.MEEIN_SHALOSH,
            ),
            sections.map(BookSection::part),
        )
        assertEquals(listOf(false, false, true, false, true, false, true), sections.map(BookSection::night))
        assertTrue(sections.first().lines.any { it.kind == SiddurLine.Kind.HEADING && it.html == "סעודה שלישית" })
        assertEquals(listOf("ערבית לימי החול", "ברכו את יהוה המברך"), sections[2].lines.map(SiddurLine::html))
        assertTrue(sections[3].lines.any { it.html == retzeh })
        assertFalse(sections[4].lines.any { it.html == retzeh })
        assertTrue(sections.takeLast(2).all { it.lines.isEmpty() })

        val selected = buildDayBook(date, prefs, setOf("MEEIN_SHALOSH:mezonot"), showAll = false, source).takeLast(2)
        assertTrue(selected.first().lines.any { it.html == meeinRetzeh })
        assertFalse(selected.last().lines.any { it.html == meeinRetzeh })
        assertTrue(selected.all { section -> section.lines.any { "ועל המחיה" in it.html } })
        assertFalse(selected.any { section -> section.lines.any { "ועל הגפן" in it.html } })

        val weekday = buildDayBook(LocalDate(2026, 11, 8), prefs, emptySet(), showAll = false, source)
        assertEquals(1, weekday.count { it.part == SiddurPart.BIRKAT_HAMAZON })
        assertEquals(1, weekday.count { it.part == SiddurPart.MEEIN_SHALOSH })
    }

    @Test
    fun meeinShaloshUsesTheEndingForThePlaceOfResidence() {
        val variants = listOf(
            "<em>על יין:</em> ועל פרי הגפן:<br><small><em>על יין של ארץ ישראל:</em> ועל פרי גפנה:</small>",
            "<small>יין</small> ועל פרי הגפן <small><small>של ארץ ישראל</small> ועל פרי גפנה</small>",
            "<small><small>(על היין:)</small></small> פְּרִי הַגֶּפֶן: <small>( <small>בא\"י</small> ועל פרי גפנה)</small>:",
        ).map(::SiddurLine)
        val inIsrael = selectFood(variants, setOf("wine", "israel")).map { plain(it.html) }
        val abroad = selectFood(variants, setOf("wine")).map { plain(it.html) }

        assertEquals(3, inIsrael.count { "פרי גפנה" in it && "פרי הגפן" !in it })
        assertEquals(3, abroad.count { "פרי הגפן" in it && "פרי גפנה" !in it })
    }

    @Test
    fun weekdayAndShabbatUseDifferentSectionsFromTheSameSource() = runBlocking {
        val source = SiddurSource {
            SourceBook(
                listOf("סידור אשכנז", "ימי חול", "תפילת שחרית", "חול", "שבת", "מעריב", "לילה"),
                listOf(
                    SourceHeading(0, 1, "סידור אשכנז"),
                    SourceHeading(1, 2, "ימי חול"),
                    SourceHeading(2, 3, "תפילת שחרית"),
                    SourceHeading(4, 2, "שבת"),
                    SourceHeading(5, 3, "מעריב"),
                ),
            )
        }
        val prefs = SiddurPrefs(Nusach.ASHKENAZ, inIsrael = true, minyan = true, beitAvel = false)
        val sections = buildDayBook(LocalDate(2026, 11, 6), prefs, emptySet(), false, source)
        assertEquals(listOf(SiddurPart.SHACHARIT, SiddurPart.MAARIV_SHABBAT), sections.map(BookSection::part))
        assertFalse(sections.first().night)
        assertTrue(sections.last().night)
    }
}
