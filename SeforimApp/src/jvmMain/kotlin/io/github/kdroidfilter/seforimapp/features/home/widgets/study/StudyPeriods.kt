package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import java.time.LocalDate

// Pardes (request 64 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md): a plan's part for
// several days, a week or two from Sunday, as a week is learnt.

/** The days a part can be for, as the editor offers them: a day, two, three, a week, two weeks. */
internal val PERIOD_DAYS = listOf(1, 2, 3, 7, 14)

/** "ביום", "ביומיים", "בשלושה ימים", "בשבוע", "בשבועיים". */
internal fun periodLabel(days: Int): String =
    when (days) {
        1 -> "ביום"
        2 -> "ביומיים"
        3 -> "בשלושה ימים"
        7 -> "בשבוע"
        14 -> "בשבועיים"
        else -> "ב־$days ימים"
    }

/** "פרק ביום", "6 סעיפים ביום", "10 פרקים בשבוע". */
internal fun perPeriodLabel(
    perDay: Int,
    name: UnitName,
    periodDays: Int,
): String = if (perDay == 1) "${name.one} ${periodLabel(periodDays)}" else "$perDay ${name.many} ${periodLabel(periodDays)}"

/**
 * The part of [day] belongs to, from 0, in a plan from [startDay] of a part for [periodDays] days: a week or two from
 * Sunday, as a week is learnt, else from [startDay].
 */
internal fun periodIndex(
    startDay: LocalDate,
    day: LocalDate,
    periodDays: Int,
): Int {
    val from = periodAnchor(startDay, periodDays)
    return ((day.toEpochDay() - from.toEpochDay()).coerceAtLeast(0) / periodDays.coerceAtLeast(1)).toInt()
}

/** The first day of the part [day] belongs to ([periodIndex]): where it is marked done. */
internal fun periodStart(
    startDay: LocalDate,
    day: LocalDate,
    periodDays: Int,
): LocalDate =
    if (periodDays <= 1) {
        day
    } else {
        periodAnchor(startDay, periodDays).plusDays(periodIndex(startDay, day, periodDays).toLong() * periodDays)
    }

// Where a plan's parts are counted from: the Sunday of its first week for a week or two, else its first day
private fun periodAnchor(
    startDay: LocalDate,
    periodDays: Int,
): LocalDate = if (periodDays % 7 == 0) startDay.minusDays((startDay.dayOfWeek.value % 7).toLong()) else startDay
