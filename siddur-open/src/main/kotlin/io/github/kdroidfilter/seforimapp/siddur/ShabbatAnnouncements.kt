package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal data class UpcomingRoshChodesh(
    val first: LocalDate,
    val calendar: JewishCalendar,
) {
    val month: HebrewMonth get() = calendar.jewishMonth
    val second: LocalDate? get() = calendar.gregorianLocalDate.takeUnless { it == first }
}

internal fun upcomingRoshChodesh(date: LocalDate, inIsrael: Boolean): UpcomingRoshChodesh? {
    if (date.dayOfWeek != DayOfWeek.SATURDAY) return null
    for (offset in 1..7) {
        val first = date.plus(offset, DateTimeUnit.DAY)
        val day = JewishCalendar(first, inIsrael)
        if (!day.isRoshChodesh) continue
        // When Rosh Chodesh starts on Shabbat, both days were announced the previous Shabbat.
        if (offset == 1 && day.jewishDayOfMonth == 1 && JewishCalendar(date, inIsrael).isRoshChodesh) continue
        val monthStart = if (day.jewishDayOfMonth == 1) day else JewishCalendar(first.plus(1, DateTimeUnit.DAY), inIsrael)
        if (monthStart.jewishMonth == HebrewMonth.TISHREI) return null
        return UpcomingRoshChodesh(first, monthStart)
    }
    return null
}

internal fun upcomingAnnouncedFast(date: LocalDate, inIsrael: Boolean): JewishCalendar? {
    if (date.dayOfWeek != DayOfWeek.SATURDAY) return null
    return (1..6).firstNotNullOfOrNull { offset ->
        val day = JewishCalendar(date.plus(offset, DateTimeUnit.DAY), inIsrael)
        day.takeIf { it.yomTovIndex == JewishCalendar.SEVENTEEN_OF_TAMMUZ || it.yomTovIndex == JewishCalendar.TENTH_OF_TEVES }
    }
}

internal fun List<SiddurLine>.fillShabbatAnnouncements(
    date: LocalDate,
    prefs: SiddurPrefs,
    flags: Set<String>,
): List<SiddurLine> {
    val month = if ("birkat_hachodesh" in flags) upcomingRoshChodesh(date, prefs.inIsrael) else null
    val fast = if ("fast_announcement" in flags) upcomingAnnouncedFast(date, prefs.inIsrael) else null
    if (month == null && fast == null) return this
    return flatMap { line ->
        if (line.skipped || line.kind != SiddurLine.Kind.TEXT) return@flatMap listOf(line)
        val label = plain(line.html)
        when {
            month != null && (label.startsWith("ראש חדש פלוני") || "יהא לנו ראש חדש פלוני" in label) -> {
                val molad = month.calendar.molad
                listOf(
                    SiddurLine(
                        "molad_announcement",
                        SiddurLine.Kind.NOTE,
                        special = true,
                        moladAnnouncement = MoladAnnouncement(
                            dayName(molad.gregorianLocalDate),
                            molad.moladHours,
                            molad.moladMinutes,
                            molad.moladChalakim,
                        ),
                    ),
                    line.copy(html = fillMonth(line.html, month, prefs.nusach), special = true),
                )
            }
            fast != null && label.startsWith("אחינו בית ישראל שמעו") -> {
                val name = if (fast.yomTovIndex == JewishCalendar.SEVENTEEN_OF_TAMMUZ) "הָרְבִיעִי" else "הָעֲשִׂירִי"
                val html = line.html.replaceFirst(fastChoices, name)
                    .replaceFirst("<small>פלוני</small>", dayName(fast.gregorianLocalDate))
                listOf(line.copy(html = html, special = true))
            }
            else -> listOf(line)
        }
    }
}

private fun fillMonth(html: String, month: UpcomingRoshChodesh, nusach: Nusach): String {
    val first = dayName(month.first)
    val second = month.second?.let(::dayName)
    val monthName = when (month.month) {
        HebrewMonth.NISSAN -> "ניסן"
        HebrewMonth.IYAR -> "אייר"
        HebrewMonth.SIVAN -> "סיוון"
        HebrewMonth.TAMMUZ -> "תמוז"
        HebrewMonth.AV -> "אב"
        HebrewMonth.ELUL -> "אלול"
        HebrewMonth.TISHREI -> "תשרי"
        HebrewMonth.CHESHVAN -> "מרחשוון"
        HebrewMonth.KISLEV -> "כסלו"
        HebrewMonth.TEVES -> "טבת"
        HebrewMonth.SHEVAT -> "שבט"
        HebrewMonth.ADAR -> if (month.calendar.isJewishLeapYear) "אדר א׳" else "אדר"
        HebrewMonth.ADAR_II -> "אדר ב׳"
    }
    if (nusach == Nusach.EDOT_HAMIZRACH) {
        val days = if (second == null) first else "$first וּבְיוֹם $second"
        return html.replaceFirst("<small>פלוני</small>", monthName)
            .replaceFirst(sefardiMonthDays, days)
    }
    val days = if (second == null) first else "$first וּלְמָחֳרָתוֹ בְּיוֹם $second"
    val result = html.replaceFirst("פלוני", monthName).replaceFirst(ashkenaziMonthDays, days)
    return if (second == null) result else result.replaceFirst("הַבָּא", "הַבָּאִים")
}

private val fastChoices = Regex("""<small><small>לי"ז בתמוז</small>[^<]+<small>לעשרה בטבת</small>[^<]+</small>""")
private val sefardiMonthDays = Regex("""<small>פלוני ופלוני</small>\s*<small>\([^<]*\)</small>""")
private val ashkenaziMonthDays = Regex("""פלוני(?:\s*\([^)]*פלוני\))?""")

private fun dayName(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.SUNDAY -> "ראשון"
    DayOfWeek.MONDAY -> "שני"
    DayOfWeek.TUESDAY -> "שלישי"
    DayOfWeek.WEDNESDAY -> "רביעי"
    DayOfWeek.THURSDAY -> "חמישי"
    DayOfWeek.FRIDAY -> "שישי"
    DayOfWeek.SATURDAY -> "שבת קודש"
}
