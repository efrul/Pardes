package io.github.kdroidfilter.seforimapp.siddur

/** Thanksgiving customs independent of the worshipper's base nusach and location. */
enum class IsraeliPrayerCustom {
    NO_TACHANUN,
    HALLEL_WITHOUT_BLESSINGS,
    DAYTIME_HALLEL,
    DAY_AND_NIGHT_HALLEL,
    ;

    val festive: Boolean get() = this == DAYTIME_HALLEL || this == DAY_AND_NIGHT_HALLEL

    fun saysHallel(occasion: IsraeliOccasion, part: SiddurPart): Boolean =
        occasion.hasThanksgivingPrayer && when (part.familyOf()) {
            SiddurPart.SHACHARIT -> this != NO_TACHANUN
            // The documented evening Hallel in this custom is for Independence Day.
            SiddurPart.MAARIV -> this == DAY_AND_NIGHT_HALLEL && occasion == IsraeliOccasion.YOM_HAATZMAUT
            else -> false
        }
}
