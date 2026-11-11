package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

enum class IsraeliOccasion {
    YOM_HAZIKARON,
    YOM_HAATZMAUT,
    YOM_YERUSHALAYIM,
    ;

    val hasThanksgivingPrayer: Boolean get() = this != YOM_HAZIKARON
}

fun israeliOccasion(date: LocalDate): IsraeliOccasion? =
    israeliOccasion(JewishCalendar(date))

fun israeliOccasion(day: JewishCalendar): IsraeliOccasion? {
    if (day.jewishMonth != HebrewMonth.IYAR) return null
    val year = day.jewishYear
    val dayOfMonth = day.jewishDayOfMonth
    // The statutory date and prayers stay on 28 Iyar even when public events move.
    if (dayOfMonth == 28 && year >= 5728) return IsraeliOccasion.YOM_YERUSHALAYIM
    if (dayOfMonth !in 2..6 || year < 5709) return null

    val fifth = day.gregorianLocalDate.minus(dayOfMonth - 5, DateTimeUnit.DAY)
    // Independence Day Act, section 1(b), including its 1954 and 2004 amendments.
    val independenceDay =
        when (fifth.dayOfWeek) {
            DayOfWeek.SATURDAY -> if (year >= 5714) 3 else 6
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.MONDAY -> if (year >= 5764) 6 else 5
            else -> 5
        }
    return when {
        dayOfMonth == independenceDay -> IsraeliOccasion.YOM_HAATZMAUT
        dayOfMonth == independenceDay - 1 && year >= 5711 -> IsraeliOccasion.YOM_HAZIKARON
        else -> null
    }
}
