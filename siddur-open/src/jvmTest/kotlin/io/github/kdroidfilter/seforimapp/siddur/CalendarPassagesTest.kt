package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarPassagesTest {
    private val ashkenaz = SiddurPrefs(Nusach.ASHKENAZ, inIsrael = true, minyan = true, beitAvel = false)

    @Test
    fun selectsTheWholeDailySongAndOnlyApplicableAdditions() {
        val title = SiddurLine("שיר של יום", SiddurLine.Kind.HEADING)
        val body = listOf(
            "<small>בראשון בשבת:</small>", "היום יום ראשון בשבת", "לדוד מזמור",
            "<small>בשני בשבת:</small>", "היום יום שני בשבת", "שיר מזמור לבני קרח",
            "בחנוכה אומרים", "מזמור שיר חנכת הבית לדוד", "הושיענו",
        ).map(::SiddurLine)
        val lines = listOf(title) + body
        val flags = setOf("weekday_monday")
        val part = SiddurPart.SHACHARIT
        val active = annotateCorpus(lines, flags, showAll = false, part = part)
        assertEquals(listOf(title) + body.subList(3, 6) + body.last(), active.map { it.copy(special = false) })
        val all = annotateCorpus(lines, flags + "chanuka", showAll = true, part = part)
        assertEquals(listOf(false, true, true, true, false, false, false, false, false, false), all.map { it.skipped })
    }

    @Test
    fun fillsTwoDayRoshChodeshButDoesNotAnnounceItAgainOnRoshChodesh() = runBlocking {
        val title = "שחרית"
        val announcement = "ברכת החודש"
        val source = SiddurSource {
            SourceBook(
                listOf("שבת", title, "אשרי", announcement, "ראש חדש פלוני יהיה ביום פלוני הַבָּא עָלֵינוּ לטובה"),
                listOf(SourceHeading(0, 1, "שבת"), SourceHeading(1, 2, title), SourceHeading(3, 3, announcement)),
            )
        }
        val shabbat = LocalDate(2025, 12, 13)
        val month = assertNotNull(upcomingRoshChodesh(shabbat, inIsrael = true))
        assertEquals(HebrewMonth.TEVES, month.month)
        assertEquals(LocalDate(2025, 12, 21), month.second)
        val lines = buildDayBook(shabbat, ashkenaz, emptySet(), showAll = false, source).single().lines
        assertTrue(lines.any { it.moladAnnouncement != null })
        val text = lines.single { it.html.startsWith("ראש חדש") }.html
        assertTrue(listOf("טבת", "שבת קודש", "ראשון").all(text::contains))
        assertFalse("פלוני" in text)
        assertNull(upcomingRoshChodesh(LocalDate(2025, 12, 20), inIsrael = true))
        assertNull(upcomingRoshChodesh(LocalDate(2026, 9, 5), inIsrael = true))
        assertFalse("birkat_hachodesh" in flagsFor(shabbat, SiddurPart.SHACHARIT, ashkenaz.copy(minyan = false)))
    }

    @Test
    fun announcesOnlyTheFastNamedInEdotHamizrach() {
        val prefs = ashkenaz.copy(nusach = Nusach.EDOT_HAMIZRACH)
        val shabbat = LocalDate(2027, 7, 17)
        val flags = flagsFor(shabbat, SiddurPart.SHACHARIT, prefs)
        assertTrue("fast_announcement" in flags)
        assertFalse("fast_announcement" in flagsFor(shabbat, SiddurPart.SHACHARIT, ashkenaz))
        val line = SiddurLine(
            """אחינו בית ישראל שמעו. צום <small><small>לי"ז בתמוז</small> הָרְבִיעִי <small>לעשרה בטבת</small> הָעֲשִׂירִי</small> יהיה יום <small>פלוני</small>.""",
        )
        val rendered = listOf(line).fillShabbatAnnouncements(shabbat, prefs, flags).single().html
        assertTrue("הָרְבִיעִי יהיה יום חמישי" in rendered)
        assertFalse("הָעֲשִׂירִי" in rendered)
    }

    @Test
    fun bedtimeRepeatsTheNightVerseAndRestoresViduiAfterMidnight() {
        val vidui = listOf("יעמוד ויאמר וידוי", "אשמנו")
        val verses = listOf("א", "ב", "ג", "ד", "ה", "ו", "שבת").map { "<small>ליל $it</small> פסוק $it" }
        val lines = (vidui + """אחר כך יאמר "אנא בכח" כולו בכל לילה""" + verses + "ברוך שם").map(::SiddurLine)
        val sunday = LocalDate(2026, 11, 8)
        val flags = setOf("calendar_tachanun", "havdala")
        val before = lines.bedtimePassages(sunday, flags, showAll = false, afterMidnight = false)
        assertEquals("פסוק א<br>פסוק א<br>פסוק א", before.single { it.special }.html)
        assertTrue(verses.indices.all { before[it + 1].html == verses[it].substringAfter("</small> ") })
        assertFalse(before.any { it.html == "אשמנו" })
        val after = lines.bedtimePassages(sunday, flags, showAll = false, afterMidnight = true)
        assertTrue(after.any { it.html == "אשמנו" })
        val shabbat = lines.bedtimePassages(LocalDate(2026, 11, 7), emptySet(), showAll = true, afterMidnight = true)
        assertTrue(shabbat.first { it.html == "אשמנו" }.skipped)
        assertEquals("פסוק שבת<br>פסוק שבת<br>פסוק שבת", shabbat.single { it.special }.html)
    }

    @Test
    fun distinguishesHolidayOnShabbatFromHolidayInTheComingWeek() {
        val firstDaySukkot = JewishCalendar(5787, HebrewMonth.TISHREI, 15).gregorianLocalDate
        assertTrue("short_kabbalat_shabbat" in flagsFor(firstDaySukkot, SiddurPart.KABBALAT_SHABBAT, ashkenaz))
        val afterShabbat = LocalDate(2027, 4, 18)
        assertFalse("vayehi_noam" in flagsFor(afterShabbat, SiddurPart.MAARIV, ashkenaz))
        assertTrue(
            "vayehi_noam" in flagsFor(afterShabbat, SiddurPart.MAARIV, ashkenaz.copy(nusach = Nusach.EDOT_HAMIZRACH)),
        )
        assertTrue("vayehi_noam" in flagsFor(LocalDate(2026, 11, 8), SiddurPart.MAARIV, ashkenaz))
        val pesachAfterShabbat = LocalDate(2025, 4, 13)
        assertTrue("vatodieinu" in flagsFor(pesachAfterShabbat, SiddurPart.LEIL_YOM_TOV, ashkenaz))
        assertFalse("havdala" in flagsFor(pesachAfterShabbat, SiddurPart.LEIL_YOM_TOV, ashkenaz))
    }

    @Test
    fun shortKabbalatShabbatInSefardFollowsItsPrintedRubrics() {
        val lines = listOf(
            "קבלת שבת",
            "לכו נרננה",
            "מזמור לדוד הבו ליהוה",
            "שמור וזכור",
            "ביום טוב ובשבת חול המועד מדלגים ל:ימין ושמאל תפרוצי",
            "מקדש מלך",
            "ימין ושמאל תפרוצי",
            """באי בשלום גם ברנה <small><small>(ביו"ט מוסיפים: <b>בשמחה</b>)</small></small> ובצהלה""",
        ).map(::SiddurLine)
        val ordinary = lines.kabbalatShabbat(emptySet(), Nusach.SEFARD, showAll = false)
        assertFalse(ordinary[1].skipped)
        assertFalse("בשמחה" in ordinary.last().html)
        val festival = lines.kabbalatShabbat(setOf("short_kabbalat_shabbat"), Nusach.SEFARD, showAll = false)
        assertTrue(festival[1].skipped)
        assertTrue(festival[5].skipped)
        assertFalse(festival[6].skipped)
        assertTrue("<b>בשמחה</b>" in festival.last().html)
    }

    @Test
    fun hoshienuIsCommonToSundayAndFridayInEdotHamizrach() {
        val hoshienu = "<b>הושיענו</b> ׀ יהוה אלהינו וקבצנו מן הגוים"
        val heading = SiddurLine("שיר של יום", SiddurLine.Kind.HEADING)
        val sunday = "מזמור ראשון"
        val friday = "מזמור שישי"
        val lines = listOf(heading) + listOf(
            "מזמור ליום ראשון היום יום אחד",
            "$sunday <small>וממשיך הושיענו</small>",
            "מזמור ליום ששי היום יום ששי",
            "$friday $hoshienu",
            "בחנוכה אומרים",
            "מזמור שיר חנכת הבית",
        ).map(::SiddurLine)
        for ((weekday, psalm) in listOf("sunday" to sunday, "friday" to friday)) {
            val visible = annotateCorpus(
                lines.splitHoshienu(showAll = false),
                setOf("weekday_$weekday", "chanuka"),
                showAll = false,
                part = SiddurPart.SHACHARIT,
                nusach = Nusach.EDOT_HAMIZRACH,
            ).map(SiddurLine::html)
            assertEquals(listOf(psalm, hoshienu, "בחנוכה אומרים"), visible.drop(2).take(3))
        }
    }

    @Test
    fun fastAmidahControlsHeadingAndBodyTogetherAndKeepsOneClosingBlessing() {
        val normalClosing = "ברוך אתה יהוה, בונה ירושלים"
        val nacham = "בתשעה באב אומרים: נחם יהוה אלהינו את אבלי ציון"
        val nachamClosing = "ברוך אתה יהוה, מנחם ציון בבנין ירושלים"
        val aneinu = "בתענית אומר היחיד עננו, וכן שליח ציבור ששכח"
        val alternative = "נוסח עננו בג׳ צומות (סנסן ליעיר ג:ט)"
        val kohanim = "ברכת כהנים"
        val kohanimRubric = "בתענית ציבור כשמתפללים מנחה קטנה"
        val blessing = "יברכך יהוה וישמרך"
        val lines = listOf(
            nacham, nachamClosing, normalClosing, aneinu, alternative, "כי אתה שומע תפלת כל פה",
            kohanim, kohanimRubric, blessing, "שים שלום טובה וברכה",
        ).map(::SiddurLine)
        val prefs = ashkenaz.copy(nusach = Nusach.EDOT_HAMIZRACH)
        fun mincha(date: LocalDate, minyan: Boolean = true): List<String> = annotateCorpus(
            lines,
            flagsFor(date, SiddurPart.MINCHA, prefs.copy(minyan = minyan)),
            showAll = false,
            part = SiddurPart.MINCHA,
            nusach = prefs.nusach,
        ).map(SiddurLine::html)
        val ordinary = mincha(LocalDate(2026, 11, 8))
        assertEquals(listOf(normalClosing, "כי אתה שומע תפלת כל פה", "שים שלום טובה וברכה"), ordinary)
        val minorFast = LocalDate(2027, 7, 22)
        assertTrue(listOf(normalClosing, aneinu, kohanim, blessing).all(mincha(minorFast)::contains))
        assertFalse(alternative in mincha(minorFast))
        assertFalse(kohanim in mincha(minorFast, minyan = false))
        val tishaBeav = mincha(JewishCalendar(5787, HebrewMonth.AV, 9).gregorianLocalDate)
        assertTrue(listOf(nacham, nachamClosing, aneinu).all(tishaBeav::contains))
        assertFalse(normalClosing in tishaBeav)
    }

    @Test
    fun edotHallelKeepsOnlyTheDatesKaddishAndUsesTheScheduledReading() = runBlocking {
        val shacharit = "שחרית לימי החול"
        val roshChodesh = "סדר ראש חודש"
        val hallel = "הלל לראש חודש ולמועדים"
        val text = listOf(
            shacharit, "בראש חודש אומרים כאן הלל", "אשרי יושבי ביתך",
            roshChodesh, hallel, "הללויה הללו עבדי יהוה", "לא (תהילים קטו) לנו יהוה",
            "<small>ואומר החזן קדיש תתקבל, ובחנוכה אומר רק חצי קדיש:</small>",
            "יתגדל ויתקדש", "תתקבל צלותנא", "יהא שלמא", "עושה שלום",
            "קודם הוצאת ספר תורה אומרים", "קריאה מודפסת לראש חודש",
        )
        val source = SiddurSource { title ->
            if (title == Nusach.EDOT_HAMIZRACH.siddurTitle()) {
                SourceBook(text, listOf(SourceHeading(0, 1, shacharit), SourceHeading(3, 1, roshChodesh), SourceHeading(4, 2, hallel)))
            } else {
                null
            }
        }
        val prefs = ashkenaz.copy(nusach = Nusach.EDOT_HAMIZRACH)
        suspend fun morning(date: LocalDate): List<SiddurLine> =
            buildDayBook(date, prefs, emptySet(), showAll = false, source).first { it.part == SiddurPart.SHACHARIT }.lines

        val chanuka = morning(LocalDate(2026, 12, 7))
        val chanukaText = chanuka.map { plain(it.html) }
        assertTrue("חצי קדיש:" in chanukaText)
        assertFalse("תתקבל צלותנא" in chanukaText)
        assertTrue("לא (תהילים קטו) לנו יהוה" in chanukaText)
        val reading = chanuka.single { it.reading?.kind == ReadingKind.TORAH }.reading
        assertEquals(7, reading?.passages?.first()?.first?.chapter)
        val roshChodeshText = morning(LocalDate(2026, 12, 11)).map { plain(it.html) }
        assertTrue("תתקבל צלותנא" in roshChodeshText)
        assertFalse("חצי קדיש:" in roshChodeshText)
        assertFalse("קריאה מודפסת לראש חודש" in chanukaText + roshChodeshText)
        val halfHallel = morning(LocalDate(2026, 11, 11)).map { plain(it.html) }
        assertFalse(halfHallel.any { it.startsWith("לא (תהילים") })
    }

    @Test
    fun repetitionsUseTheSpecialMarkAndStayOutOfTheSilentAmidah() {
        val lines = listOf(
            "קדושה בחזרת השץ אומרים", "נקדישך ונעריצך", "אתה קדוש ושמך קדוש",
            "מודים אנחנו לך", "מודים דרבנן", "מודים אנחנו לך שאתה הוא יהוה אלהינו ואלהי אבותינו",
            "ועל כלם יתברך", "ברכת כהנים", "יברכך יהוה וישמרך", "שים שלום",
        ).map(::SiddurLine)
        val silent = annotateCorpus(lines, emptySet(), showAll = false).map(SiddurLine::html)
        assertEquals(listOf("אתה קדוש ושמך קדוש", "מודים אנחנו לך", "ועל כלם יתברך", "שים שלום"), silent)
        val publicPrayer = annotateCorpus(lines, setOf("repetition"), showAll = false)
        assertEquals(6, publicPrayer.count { it.special })
        assertTrue(publicPrayer.first { it.html == "ברכת כהנים" }.kind == SiddurLine.Kind.HEADING)
    }
}
