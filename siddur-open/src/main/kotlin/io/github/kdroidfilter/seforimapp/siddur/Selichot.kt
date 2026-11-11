package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal data class SelichotSource(val title: String, val key: String)

private val days = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שביעי")

fun hasSeasonalSelichot(date: LocalDate, prefs: SiddurPrefs): Boolean = seasonalSelichot(date, prefs) != null

/** The dated service before Shacharit, independent of the weekday Tachanun rules. */
internal fun seasonalSelichot(date: LocalDate, prefs: SiddurPrefs): SelichotSource? {
    val calendar = JewishCalendar(date, prefs.inIsrael)
    if (calendar.isAssurBemelacha) return null
    val month = calendar.jewishMonth
    val day = calendar.jewishDayOfMonth
    if (month != HebrewMonth.ELUL && !(month == HebrewMonth.TISHREI && day in 3..9)) return null
    if (prefs.nusach == Nusach.EDOT_HAMIZRACH) {
        if (calendar.isRoshChodesh) return null
        return SelichotSource("סליחות נוסח עדות המזרח", "סליחות נוסח עדות המזרח")
    }
    val title = if (prefs.nusach == Nusach.ASHKENAZ) "סליחות נוסח אשכנז ליטא" else "סליחות נוסח פולין"
    val key = when {
        calendar.isErevRoshHashana -> "לערב ראש השנה"
        calendar.isErevYomKippur -> "לערב יום כיפור"
        calendar.yomTovIndex == JewishCalendar.FAST_OF_GEDALYAH -> "לצום גדליה"
        month == HebrewMonth.ELUL -> {
            val roshHashana = JewishCalendar(calendar.jewishYear + 1, HebrewMonth.TISHREI, 1).gregorianLocalDate
            // The nearest Sunday at least four days before Rosh Hashana, per Rema Orach Chayim 581:1.
            val latest = roshHashana.minus(4, DateTimeUnit.DAY)
            val start = latest.minus((latest.dayOfWeek.ordinal + 1) % 7, DateTimeUnit.DAY)
            val ordinal = selichotOrdinal(start, date)
            days.getOrNull(ordinal - 1)?.let { "ליום $it" } ?: return null
        }
        else -> {
            val tishrei3 = JewishCalendar(calendar.jewishYear, HebrewMonth.TISHREI, 3).gregorianLocalDate
            val ordinal = selichotOrdinal(tishrei3, date)
            days.getOrNull(ordinal - 1)?.let { "ליום $it דעשי\"ת" } ?: return null
        }
    }
    return SelichotSource(title, key)
}

private fun selichotOrdinal(start: LocalDate, date: LocalDate): Int {
    val count = (date.toEpochDays() - start.toEpochDays()).toInt()
    return (0..count).count { start.plus(it, DateTimeUnit.DAY).dayOfWeek != DayOfWeek.SATURDAY }
}

/** The fast-day additions placed in Shacharit, using the corpus units mapped by Amud. */
internal fun fastSelichot(date: LocalDate, prefs: SiddurPrefs): SelichotSource? {
    val day = JewishCalendar(date, prefs.inIsrael)
    val nusach = prefs.nusach
    val key = when (day.yomTovIndex) {
        JewishCalendar.FAST_OF_GEDALYAH -> when (nusach) {
            Nusach.EDOT_HAMIZRACH -> "סליחות לצום גדליה"
            // For Ashkenaz and Sefard the dated seasonal service supplies the full Selichot for this fast.
            else -> return null
        }
        JewishCalendar.TENTH_OF_TEVES -> when (nusach) {
            Nusach.ASHKENAZ -> "סליחות/עשרה בטבת"
            else -> "סליחות לעשרה בטבת"
        }
        JewishCalendar.FAST_OF_ESTHER -> when (nusach) {
            Nusach.ASHKENAZ -> "סליחות/תענית אסתר"
            else -> "סליחות לתענית אסתר"
        }
        JewishCalendar.SEVENTEEN_OF_TAMMUZ -> when (nusach) {
            Nusach.ASHKENAZ -> "סליחות/שבעה עשר בתמוז"
            Nusach.SEFARD -> "סליחות לשבעה עשר בתמוז"
            Nusach.EDOT_HAMIZRACH -> "סליחות לי\"ז בתמוז"
        }
        else -> return null
    }
    return SelichotSource(nusach.siddurTitle(), key)
}
