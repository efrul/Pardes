package io.github.kdroidfilter.seforimapp.siddur

/** Thanksgiving customs independent of the worshipper's base nusach and location. */
enum class IsraeliPrayerCustom {
    /** The days are ordinary weekdays: Tachanun as on any day, and nothing of the days said or named. */
    ORDINARY_WEEKDAY,
    NO_TACHANUN,
    HALLEL_WITHOUT_BLESSINGS,
    DAYTIME_HALLEL,
    DAY_AND_NIGHT_HALLEL,
    ;

    val festive: Boolean get() = this == DAYTIME_HALLEL || this == DAY_AND_NIGHT_HALLEL

    /** Whether the custom keeps the days at all. */
    val keepsTheDays: Boolean get() = this != ORDINARY_WEEKDAY

    fun saysHallel(occasion: IsraeliOccasion, part: SiddurPart): Boolean =
        occasion.hasThanksgivingPrayer && when (part.familyOf()) {
            SiddurPart.SHACHARIT -> this != NO_TACHANUN && keepsTheDays
            // The documented evening Hallel in this custom is for Independence Day.
            SiddurPart.MAARIV -> this == DAY_AND_NIGHT_HALLEL && occasion == IsraeliOccasion.YOM_HAATZMAUT
            else -> false
        }
}
