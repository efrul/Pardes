package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.Zman
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import io.github.kdroidfilter.kosherkotlin.util.NOAACalculator
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinTimeZone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/*
 * The desktop 0.6.10 zmanim, ported verbatim on the same library (KosherKotlin 4.0.1), with the default zmanim in place
 * of the luach desktop 0.6.10 read for everyone but עדות המזרח, as in Pardes desktop:
 * - earthwidget EarthWidgetZmanimView.kt: EarthWidgetLocation, ZmanimTimes, ZmanimOpinion, computeZmanimTimes,
 *   zmanimCalendar, ohrHaChaimSunset and the conversions;
 * - earthwidget KiddushLevanaData.kt: the kiddush levana opinions;
 * - home/widgets/zmanim/ZmanimWidget.kt: computeShabbatTimes, candleLightingTime, havdalahTime, isJerusalemLocation;
 * - home/widgets/HomeWidgetsState.kt: which luach and opinions a community follows.
 * Do not "fix" the methods here: each one is what the luach prints, and the times must match desktop to the minute.
 */

data class EarthWidgetLocation(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val timeZone: TimeZone,
)

data class ZmanimTimes(
    val alosHashachar: Date?,
    val sunrise: Date?,
    val sofZmanShmaGra: Date?,
    val sofZmanShmaMga: Date?,
    val sofZmanTfilaGra: Date?,
    val sofZmanTfilaMga: Date?,
    val chatzosHayom: Date?,
    val minchaGedola: Date?,
    val minchaKetana: Date?,
    val plagHamincha: Date?,
    val sunset: Date?,
    val tzais: Date?,
    val tzaisRabbeinuTam: Date?,
    val chatzosLayla: Date?,
)

/** The luach the zmanim are read from: its sun, and which opinions it prints. */
enum class ZmanimOpinion {
    /** The library's standard GRA and MGA zmanim. */
    DEFAULT,

    /** אור החיים, the luach Rabbi Ovadia Yosef used: zmaniyos minutes off an ordinary sun. */
    OHR_HACHAIM,
}

/** Opinion for the earliest time to recite Kiddush Levana (Tchilas Zman). */
enum class KiddushLevanaEarliestOpinion {
    /** 3 days after the molad - strict opinion */
    DAYS_3,

    /** 7 days after the molad - Mechaber (Shulchan Aruch) opinion */
    DAYS_7,
}

/** Opinion for the latest time to recite Kiddush Levana (Sof Zman). */
enum class KiddushLevanaLatestOpinion {
    /** Approximately 14 days, 18 hours, 22 minutes after molad - Maharil opinion */
    BETWEEN_MOLDOS,

    /** 15 days after the molad - Shulchan Aruch opinion */
    DAYS_15,
}

// HomeWidgetsState: עדות המזרח read the אור החיים, everyone else the default zmanim (an unset community included)
private fun isSephardi(community: String?) = Community.fromStored(community) == Community.SEPHARADE

fun zmanimOpinionFor(community: String?): ZmanimOpinion = if (isSephardi(community)) ZmanimOpinion.OHR_HACHAIM else ZmanimOpinion.DEFAULT

fun kiddushLevanaEarliestFor(community: String?): KiddushLevanaEarliestOpinion =
    if (isSephardi(community)) KiddushLevanaEarliestOpinion.DAYS_7 else KiddushLevanaEarliestOpinion.DAYS_3

fun kiddushLevanaLatestFor(community: String?): KiddushLevanaLatestOpinion =
    if (isSephardi(community)) KiddushLevanaLatestOpinion.DAYS_15 else KiddushLevanaLatestOpinion.BETWEEN_MOLDOS

/** Android-only footnote of the zmanim pages: the luach the times come from, none for the default zmanim. */
val ZmanimOpinion.luachName: String?
    get() = when (this) {
        ZmanimOpinion.DEFAULT -> null
        ZmanimOpinion.OHR_HACHAIM -> "לוח אור החיים"
    }

fun computeZmanimTimes(
    date: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion = ZmanimOpinion.DEFAULT,
    inIsrael: Boolean = false,
): ZmanimTimes {
    val calendar = zmanimCalendar(date, location, opinion, inIsrael)
    // GRA hours, chatzos and mincha ketana are shared; both print sunrise במישור
    return when (opinion) {
        ZmanimOpinion.DEFAULT ->
            ZmanimTimes(
                alosHashachar = calendar.alosHashachar.toDate(),
                sunrise = calendar.seaLevelSunrise?.toDate(),
                sofZmanShmaGra = calendar.sofZmanShmaGRA.toDate(),
                sofZmanShmaMga = calendar.sofZmanShmaMGA.toDate(),
                sofZmanTfilaGra = calendar.sofZmanTfilaGRA.toDate(),
                sofZmanTfilaMga = calendar.sofZmanTfilaMGA.toDate(),
                chatzosHayom = calendar.chatzos.toDate(),
                minchaGedola = calendar.minchaGedola.toDate(),
                minchaKetana = calendar.minchaKetana.toDate(),
                plagHamincha = calendar.plagHamincha.toDate(),
                sunset = calendar.seaLevelSunset?.toDate(),
                tzais = calendar.tzais.toDate(),
                tzaisRabbeinuTam = calendar.tzais72.toDate(),
                chatzosLayla = calendar.solarMidnight.toDate(),
            )

        ZmanimOpinion.OHR_HACHAIM ->
            ZmanimTimes(
                alosHashachar = calendar.alos72Zmanis.toDate(),
                sunrise = calendar.seaLevelSunrise?.toDate(),
                sofZmanShmaGra = calendar.sofZmanShmaGRA.toDate(),
                sofZmanShmaMga = calendar.sofZmanShmaMGA72MinutesZmanis.toDate(),
                sofZmanTfilaGra = calendar.sofZmanTfilaGRA.toDate(),
                sofZmanTfilaMga = calendar.sofZmanTfilaMGA72MinutesZmanis.toDate(),
                chatzosHayom = calendar.chatzos.toDate(),
                minchaGedola = calendar.minchaGedolaOhrHaChaim.toDate(),
                minchaKetana = calendar.minchaKetana.toDate(),
                plagHamincha = calendar.plagHaminchaYalkutYosef.toDate(),
                sunset = calendar.ohrHaChaimSunset?.toDate(),
                tzais = calendar.tzais13Point5MinutesZmanis.toDate(),
                tzaisRabbeinuTam = calendar.tzais72Zmanis.toDate(),
                chatzosLayla = calendar.solarMidnight.toDate(),
            )
    }
}

/**
 * A [ComplexZmanimCalendar] for [date] at [location], running on the sun of [opinion]'s luach.
 * The אור החיים counts its day from the city's height in Israel, from sea level abroad.
 */
fun zmanimCalendar(
    date: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion,
    inIsrael: Boolean,
): ComplexZmanimCalendar {
    val timeZone = location.timeZone.toKotlin()
    return ComplexZmanimCalendar(
        location = GeoLocation("earthwidget", location.latitude, location.longitude, location.elevationMeters, timeZone),
        date = date.toKotlinLocalDate(),
        useElevation = opinion == ZmanimOpinion.OHR_HACHAIM && inIsrael,
    ).apply {
        astronomicalCalculator =
            when (opinion) {
                ZmanimOpinion.DEFAULT, ZmanimOpinion.OHR_HACHAIM -> NOAACalculator()
            }
    }
}

/** The sunset the אור החיים zmaniyos are counted to: from height when elevation is on (mirrors the protected one). */
val ComplexZmanimCalendar.ohrHaChaimSunset get() = if (isUseElevation) sunset else seaLevelSunset

fun Zman.DateBased.toDate(): Date? = momentOfOccurrence?.toDate()

fun kotlin.time.Instant.toDate(): Date = Date(toEpochMilliseconds())

fun TimeZone.toKotlin(): kotlinx.datetime.TimeZone = toZoneId().toKotlinTimeZone()

// ---- Shabbat card, candle lighting and havdala (ZmanimWidget.kt) ---------------------------------------------------

/** The Shabbat card: "שבת <parasha>" and its entry and exit. */
data class ShabbatTimes(
    val parashaName: String,
    val entryTime: Date?,
    val exitTime: Date?,
)

fun computeShabbatTimes(
    date: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion = ZmanimOpinion.DEFAULT,
    cityLabel: String? = null,
    inIsrael: Boolean = false,
): ShabbatTimes {
    val shabbatDate = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    val fridayDate = shabbatDate.minusDays(1)

    val parashaName =
        run {
            val jewishCalendar = JewishCalendar(shabbatDate.toKotlinLocalDate(), inIsrael)
            val formatter =
                HebrewDateFormatter().apply {
                    isHebrewFormat = true
                    isUseGershGershayim = false
                }
            formatter.formatParsha(jewishCalendar)?.takeIf { it.isNotBlank() }
                ?: formatter.formatSpecialParsha(jewishCalendar).orEmpty()
        }
    val parashaTitle = if (parashaName.isBlank()) "שבת" else "שבת $parashaName"

    val entryTime = candleLightingTime(fridayDate, location, opinion, cityLabel, inIsrael)
    val exitTime = havdalahTime(shabbatDate, location, opinion, inIsrael, cityLabel)

    return ShabbatTimes(
        parashaName = parashaTitle,
        entryTime = entryTime,
        exitTime = exitTime,
    )
}

/** Candle lighting on [eve], the day before a Shabbat or Yom Tov, as [opinion]'s luach prints it. */
fun candleLightingTime(
    eve: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion,
    cityLabel: String?,
    inIsrael: Boolean,
): Date? {
    val calendar = zmanimCalendar(eve, location, opinion, inIsrael)
    return when (opinion) {
        // אור החיים: candles 20 minutes before sunset everywhere
        ZmanimOpinion.OHR_HACHAIM -> calendar.ohrHaChaimSunset?.minus(20.minutes)?.toDate()

        // Default: 18 minutes before sunset, 40 in Jerusalem
        ZmanimOpinion.DEFAULT -> {
            val minutes = if (isJerusalemLocation(location, cityLabel)) 40 else 18
            calendar.seaLevelSunset?.minus(minutes.minutes)?.toDate()
        }
    }
}

/** The end of the Shabbat or Yom Tov of [day], as [opinion]'s luach prints it. */
fun havdalahTime(
    day: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion,
    inIsrael: Boolean,
    cityLabel: String? = null,
): Date? {
    val calendar = zmanimCalendar(day, location, opinion, inIsrael)
    return when (opinion) {
        // אור החיים: 30 minutes after sunset in Israel
        // ponytail: abroad the RO calendar defaults to Amudei Horaah (7.165°); we keep its fixed-minutes choice, 40
        ZmanimOpinion.OHR_HACHAIM -> {
            calendar.ateretTorahSunsetOffset = if (inIsrael) 30.0 else 40.0
            calendar.tzaisAteretTorah.toDate()
        }

        // Default: 8.5°, or 40 minutes after sunset in Jerusalem
        ZmanimOpinion.DEFAULT ->
            if (isJerusalemLocation(location, cityLabel)) {
                calendar.seaLevelSunset?.plus(40.minutes)?.toDate()
            } else {
                calendar.tzais.toDate()
            }
    }
}

private fun isJerusalemLocation(
    location: EarthWidgetLocation,
    cityLabel: String?,
): Boolean {
    val trimmedLabel = cityLabel?.trim().orEmpty()
    if (trimmedLabel == "ירושלים" || trimmedLabel.equals("Jerusalem", ignoreCase = true)) {
        return true
    }

    val jerusalem = worldPlaces["ישראל"]?.get("ירושלים") ?: return false
    val latDiff = abs(location.latitude - jerusalem.lat)
    val lonDiff = abs(location.longitude - jerusalem.lng)
    return latDiff < 0.1 && lonDiff < 0.1
}
