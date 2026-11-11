package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar.Parsha
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.TefilaRules
import io.github.kdroidfilter.seforimapp.siddur.SiddurPart.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

fun dayPartFor(
    date: LocalDate,
    part: SiddurPart,
    prefs: SiddurPrefs,
): SiddurPart {
    val calendar = JewishCalendar(date, prefs.inIsrael)
    val shabbat = date.dayOfWeek == DayOfWeek.SATURDAY
    val festival = calendar.isYomTovAssurBemelacha
    return when (part.familyOf()) {
        SHACHARIT -> if (festival) SHACHARIT_YOM_TOV else if (shabbat) SHACHARIT_SHABBAT else SHACHARIT
        MINCHA -> if (festival) MINCHA_YOM_TOV else if (shabbat) MINCHA_SHABBAT else MINCHA
        MAARIV -> if (festival) MAARIV_YOM_TOV else if (shabbat) MAARIV_SHABBAT else MAARIV
        else -> part
    }
}

internal fun flagsFor(
    date: LocalDate,
    part: SiddurPart,
    prefs: SiddurPrefs,
): Set<String> {
    val day = JewishCalendar(date, prefs.inIsrael)
    val rules = TefilaRules()
    val family = part.familyOf()
    val shabbat = date.dayOfWeek == DayOfWeek.SATURDAY
    val festival = day.isYomTovAssurBemelacha
    val previous = JewishCalendar(date.plus(-1, DateTimeUnit.DAY), prefs.inIsrael)
    val tomorrow = date.plus(1, DateTimeUnit.DAY)
    val next = JewishCalendar(tomorrow, prefs.inIsrael)
    val minchaBeforeFestival = family == MINCHA && next.isYomTovAssurBemelacha
    val occasion = israeliOccasion(day)
    val fast = day.yomTovIndex
    val tachanunMorning = rules.isTachanunRecitedShacharis(day)
    val fourParshiyot = day.specialShabbos in fourSpecialShabbatot
    val omitsTachanun = occasion?.hasThanksgivingPrayer == true ||
        family == MINCHA && israeliOccasion(tomorrow)?.hasThanksgivingPrayer == true
    return buildSet {
        if (prefs.inIsrael) add("israel")
        if (prefs.minyan) add("minyan")
        if (prefs.minyan && (family == SHACHARIT || family == MINCHA || part == NEILA)) add("repetition")
        if (prefs.nusach == Nusach.ASHKENAZ) add("ashkenaz")
        if (shabbat) add("sat")
        if (shabbat && (festival || previous.isYomTovAssurBemelacha || day.isCholHamoed)) add("short_kabbalat_shabbat")
        if (shabbat && !festival && !previous.isYomTovAssurBemelacha && !day.isCholHamoed && !day.isChanukah &&
            !previous.isChanukah && !prefs.beitAvel
        ) {
            add("bameh_madlikin")
        }
        if (festival) add("festival")
        if (!shabbat && !festival) add("weekday")
        add("weekday_${date.dayOfWeek.name.lowercase()}")
        if (prefs.beitAvel) add("beit_avel")
        if (tachanunMorning) add("calendar_tachanun")
        if (family == SHACHARIT && shabbat && prefs.minyan && !festival) {
            val month = upcomingRoshChodesh(date, prefs.inIsrael)?.month
            if (month != null) add("birkat_hachodesh")
            if (prefs.nusach == Nusach.EDOT_HAMIZRACH && upcomingAnnouncedFast(date, prefs.inIsrael) != null) {
                add("fast_announcement")
            }
            if (prefs.nusach != Nusach.EDOT_HAMIZRACH && !fourParshiyot &&
                (month in avHarachamimMonths || month == null && tachanunMorningOnShabbat(day, rules))
            ) {
                add("av_harachamim")
            }
        }
        if (family == MINCHA && shabbat && !fourParshiyot && !festival && next.yomTovIndex != JewishCalendar.TISHA_BEAV &&
            tachanunMorningOnShabbat(day, rules) &&
            rules.isTachanunRecitedShacharis(JewishCalendar(tomorrow, prefs.inIsrael))
        ) {
            add("tzidkatcha")
        }
        if (shabbat && !festival) {
            if ((day.jewishMonth in winterPsalmMonths || day.jewishMonth == HebrewMonth.TISHREI && day.jewishDayOfMonth > 23) &&
                day.specialShabbos != Parsha.HAGADOL
            ) {
                add("winter_psalms")
            }
            if (day.jewishMonth in pirkeiAvotMonths && day.yomTovIndex != JewishCalendar.EREV_ROSH_HASHANA &&
                (day.jewishMonth != HebrewMonth.NISSAN || day.jewishDayOfMonth > if (prefs.inIsrael) 21 else 22)
            ) {
                add("pirkei_avot")
            }
        }
        if (occasion != null) {
            add(
                when (occasion) {
                    IsraeliOccasion.YOM_HAZIKARON -> "yom_hazikaron"
                    IsraeliOccasion.YOM_HAATZMAUT -> "yom_haatzmaut"
                    IsraeliOccasion.YOM_YERUSHALAYIM -> "yom_yerushalayim"
                },
            )
            if (prefs.israeliPrayerCustom.saysHallel(occasion, part)) {
                add("israeli_hallel")
                add("hallel_shalem")
                if (!prefs.israeliPrayerCustom.festive) add("hallel_without_blessings")
            }
        }
        if (family == MINCHA && day.isErevYomKippur) add("erev_yom_kippur")
        if (family == MINCHA && day.isErevRoshHashana) add("erev_rosh_hashana")
        if (day.isRoshChodesh) add("rosh_chodesh")
        if (previous.isRoshChodesh) add("after_rosh_chodesh")
        if (day.isCholHamoed) add("chol_hamoed")
        if (day.isTaanis && fast != JewishCalendar.YOM_KIPPUR) add("taanit")
        if (fast == JewishCalendar.TISHA_BEAV && family == MINCHA) add("nacham")
        if ("taanit" in this && (family == MINCHA ||
                prefs.nusach == Nusach.EDOT_HAMIZRACH &&
                (family == SHACHARIT || family == MAARIV && fast == JewishCalendar.TISHA_BEAV))
        ) {
            add("aneinu")
        }
        if (fast in setOf(JewishCalendar.FAST_OF_GEDALYAH, JewishCalendar.TENTH_OF_TEVES)) add("psalm_83")
        if (fast == JewishCalendar.SEVENTEEN_OF_TAMMUZ) add("psalm_79")
        if (fast == JewishCalendar.FAST_OF_ESTHER) add("psalm_22")
        if (day.jewishMonth == HebrewMonth.TISHREI && day.jewishDayOfMonth == 11) add("psalm_85")
        if (!prefs.beitAvel && !omitsTachanun && when (family) {
                SHACHARIT -> tachanunMorning
                MINCHA -> !minchaBeforeFestival && rules.isTachanunRecitedMincha(day)
                else -> false
            }
        ) {
            add("tachanun")
        }
        if (rules.isYaalehVeyavoRecited(day)) add("yaale_veyavo")
        val purim = day.yomTovIndex == if (prefs.mukafChoma) JewishCalendar.SHUSHAN_PURIM else JewishCalendar.PURIM
        if (purim) add("psalm_22")
        if (day.isChanukah || purim) {
            add("al_hanisim")
        }
        when {
            rules.isHallelShalemRecited(day) -> add("hallel_shalem")
            rules.isHallelRecited(day) -> add("chatzi_hallel")
        }
        if (shabbat || festival || day.isRoshChodesh || day.isCholHamoed) add("musaf")
        if (when {
                rules.isMashivHaruachStartDate(day) -> family == MINCHA
                rules.isMashivHaruachEndDate(day) -> family != MINCHA && !part.night
                else -> rules.isMashivHaruachRecited(day)
            }
        ) {
            add("mashiv_haruach")
        }
        if (rules.isVeseinTalUmatarRecited(day)) add("tal_umatar")
        if ("mashiv_haruach" !in this && (prefs.inIsrael || prefs.nusach != Nusach.ASHKENAZ)) add("morid_hatal")
        if (prefs.minyan && "taanit" in this && family != MAARIV) add("fast_repetition")
        if (!shabbat && !festival && day.yomTovIndex !in setOf(
                JewishCalendar.EREV_YOM_KIPPUR, JewishCalendar.EREV_PESACH, JewishCalendar.CHOL_HAMOED_PESACH,
            )
        ) {
            add("mizmor_letoda")
        }
        if (family == SHACHARIT && !prefs.beitAvel && !shabbat && !festival &&
            if (prefs.nusach == Nusach.EDOT_HAMIZRACH) "tachanun" in this else !day.isRoshChodesh && fast !in noLamenatzeach
        ) {
            add("lamenatzeach")
        }
        if (day.dayOfOmer > 0) {
            add("omer")
            add("omer_${day.dayOfOmer}")
        }
        if (day.dayOfChanukah > 0) add("chanuka_${day.dayOfChanukah}")
        if (day.dayOfChanukah > 0) add("chanuka")
        if (purim) add("purim")
        if (day.jewishDayOfMonth in (if (prefs.nusach == Nusach.EDOT_HAMIZRACH) 7 else 3)..15) add("levana")
        if (previous.isAssurBemelacha && !day.isAssurBemelacha) add("havdala")
        if (previous.yomTovIndex == JewishCalendar.YOM_KIPPUR) add("after_yom_kippur")
        if (date.dayOfWeek == DayOfWeek.MONDAY && previous.yomTovIndex == JewishCalendar.TISHA_BEAV) {
            add("havdala")
            add("deferred_havdala")
        }
        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
            add("motzaei_shabbat")
            if (festival && (part == MAARIV_YOM_TOV || part == LEIL_YOM_TOV)) add("vatodieinu")
            if (fast == JewishCalendar.TISHA_BEAV) add("shabbat_into_tisha_beav")
            if (!festival && fast != JewishCalendar.TISHA_BEAV && (prefs.nusach == Nusach.EDOT_HAMIZRACH || (0..5).none { offset ->
                    JewishCalendar(date.plus(offset, DateTimeUnit.DAY), prefs.inIsrael).isYomTovAssurBemelacha
                })
            ) {
                add("vayehi_noam")
            }
        }
        if (date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY) add("mon_thu")
        if (day.jewishMonth == HebrewMonth.TISHREI && day.jewishDayOfMonth <= 10) add("aseret_yemei_teshuva")
        val lastLedavidDay = if (prefs.nusach == Nusach.ASHKENAZ) (if (prefs.inIsrael) 22 else 23) else 21
        if (day.jewishMonth == HebrewMonth.ELUL ||
            day.jewishMonth == HebrewMonth.TISHREI && day.jewishDayOfMonth <= lastLedavidDay
        ) {
            add("ledavid_ori")
        }
        if (!minchaBeforeFestival &&
            ("aseret_yemei_teshuva" in this && !shabbat && !festival || "taanit" in this && fast != JewishCalendar.TISHA_BEAV)
        ) {
            add("avinu_malkenu")
        }
        if ("taanit" in this && "aseret_yemei_teshuva" !in this) add("fast_avinu_variant")
        when (day.yomTovIndex) {
            JewishCalendar.ROSH_HASHANA -> add("rosh_hashana")
            JewishCalendar.YOM_KIPPUR -> add("yom_kippur")
            JewishCalendar.PESACH, JewishCalendar.CHOL_HAMOED_PESACH ->
                add(if (day.jewishDayOfMonth >= 21) "shvii_shel_pesach" else "pesach")
            JewishCalendar.SHAVUOS -> add("shavuot")
            JewishCalendar.SUCCOS, JewishCalendar.CHOL_HAMOED_SUCCOS, JewishCalendar.HOSHANA_RABBA -> add("sukkot")
            JewishCalendar.SHEMINI_ATZERES -> add(if (prefs.inIsrael) "simchat_torah" else "shmini_atzeret")
            JewishCalendar.SIMCHAS_TORAH -> add("simchat_torah")
        }
        if (festival && fast !in setOf(JewishCalendar.ROSH_HASHANA, JewishCalendar.YOM_KIPPUR) ||
            shabbat && fast == JewishCalendar.CHOL_HAMOED_SUCCOS
        ) {
            add("festival_haftarah")
        }
        if (day.jewishDayOfMonth == 2 && "rosh_hashana" in this) add("rh_second_day")
        if (day.jewishDayOfMonth == 16 && "sukkot" in this && festival) add("sukkot_second_night")
    }
}

private val fourSpecialShabbatot = setOf(Parsha.SHKALIM, Parsha.ZACHOR, Parsha.PARA, Parsha.HACHODESH)
private val avHarachamimMonths = setOf(HebrewMonth.IYAR, HebrewMonth.SIVAN, HebrewMonth.AV)
private val noLamenatzeach = setOf(
    JewishCalendar.CHANUKAH, JewishCalendar.PURIM, JewishCalendar.SHUSHAN_PURIM, JewishCalendar.PURIM_KATAN,
    JewishCalendar.SHUSHAN_PURIM_KATAN, JewishCalendar.EREV_PESACH, JewishCalendar.EREV_YOM_KIPPUR, JewishCalendar.TISHA_BEAV,
)
private val winterPsalmMonths = setOf(
    HebrewMonth.CHESHVAN, HebrewMonth.KISLEV, HebrewMonth.TEVES, HebrewMonth.SHEVAT, HebrewMonth.ADAR, HebrewMonth.ADAR_II,
    HebrewMonth.NISSAN,
)
private val pirkeiAvotMonths = setOf(
    HebrewMonth.NISSAN, HebrewMonth.IYAR, HebrewMonth.SIVAN, HebrewMonth.TAMMUZ, HebrewMonth.AV, HebrewMonth.ELUL,
)

// TefilaRules always omits Tachanun on Saturday. Use the date-specific exclusions for these Shabbat passages.
private fun tachanunMorningOnShabbat(day: JewishCalendar, rules: TefilaRules): Boolean =
    !day.isRoshChodesh && !day.isYomTov && day.jewishMonth != HebrewMonth.NISSAN &&
        !(day.jewishMonth == HebrewMonth.TISHREI && day.jewishDayOfMonth >= 9) &&
        !(day.jewishMonth == HebrewMonth.SIVAN && day.jewishDayOfMonth <= if (rules.isTachanunRecitedWeekAfterShavuos) 6 else 12)
