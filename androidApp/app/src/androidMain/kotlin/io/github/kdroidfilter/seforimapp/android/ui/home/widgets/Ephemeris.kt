package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.android.data.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.android.data.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.android.data.toDate
import io.github.kdroidfilter.seforimapp.android.data.toKotlin
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toJavaLocalDate
import java.time.Instant
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/*
 * The astronomy of the Earth, sky and solar-system widgets, verbatim from desktop 0.6.1 earthwidget where it is plain
 * math: SolarPositionCalculator.kt (NOAA sun, Julian day), EarthEphemeris.kt (the Moon, the Sun's ecliptic
 * longitude), SkyScene.kt (sky directions, the star field's turn), EarthWidgetZmanimView.kt (the Hebrew day, the
 * moon's age, the Kiddush Levana window on the orbit) and SolarSystemWidgetView.kt (the year's events). Desktop draws
 * them with Filament (3D); Android draws them in 2D (EarthWidget, SkyWidget, SolarSystemWidget).
 */

internal const val DEG_TO_RAD = PI / 180.0
private const val J2000_EPOCH_JD = 2451545.0
private const val DAYS_PER_JULIAN_CENTURY = 36525.0
private const val MOON_MEAN_LONGITUDE_J2000 = 218.3164477
private const val MOON_MEAN_LONGITUDE_RATE = 481267.88123421
private const val MOON_MEAN_ANOMALY_J2000 = 134.9633964
private const val MOON_MEAN_ANOMALY_RATE = 477198.8675055
private const val MOON_MEAN_ELONGATION_J2000 = 297.8501921
private const val MOON_MEAN_ELONGATION_RATE = 445267.1114034
private const val SUN_MEAN_ANOMALY_J2000 = 357.5291092
private const val SUN_MEAN_ANOMALY_RATE = 35999.0502909
private const val MOON_ARG_LATITUDE_J2000 = 93.2720950
private const val MOON_ARG_LATITUDE_RATE = 483202.0175233
private const val MEAN_OBLIQUITY_COEFF0 = 21.448
private const val MEAN_OBLIQUITY_COEFF1 = 46.815
private const val MEAN_OBLIQUITY_COEFF2 = 0.00059
private const val MEAN_OBLIQUITY_COEFF3 = 0.001813
private const val SUN_MEAN_LONGITUDE_J2000 = 280.4665
private const val SUN_MEAN_LONGITUDE_RATE = 36000.7698

/** Where the first day's label sits on the moon's orbit (desktop ORBIT_DAY_LABEL_START_DEGREES). */
internal const val ORBIT_DAY_LABEL_START_DEGREES = 90f

/** A mean lunation (desktop LUNAR_CYCLE_MILLIS). */
internal const val LUNAR_CYCLE_MILLIS = 29.0 * 86_400_000.0 + 12.0 * 3_600_000.0 + 793.0 * 10_000.0 / 3.0

internal fun normalizeAngleDeg(degrees: Double): Double {
    val result = degrees % 360.0
    return if (result < 0) result + 360.0 else result
}

internal fun normalizeAngle360(value: Float): Float {
    val mod = value % 360f
    return if (mod < 0f) mod + 360f else mod
}

private fun normalizeRad(angle: Double): Double {
    val twoPi = 2 * PI
    val mod = angle % twoPi
    return if (mod < 0) mod + twoPi else mod
}

// ---- The Sun (SolarPositionCalculator.kt) ----------------------------------------------------------------------------

internal data class SolarPosition(
    val azimuthDegreesFromNorth: Double,
    val elevationDegrees: Double,
)

internal fun computeJulianDayUtc(date: Date): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
    var year = cal.get(Calendar.YEAR)
    var month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    val millisecond = cal.get(Calendar.MILLISECOND)
    if (month <= 2) {
        year -= 1
        month += 12
    }
    val a = year / 100
    val b = 2 - a + a / 4
    val dayFraction = (hour + minute / 60.0 + second / 3600.0 + millisecond / 3_600_000.0) / 24.0
    return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + dayFraction + b - 1524.5
}

private fun utcMinutesOfDay(date: Date): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
    return cal.get(Calendar.HOUR_OF_DAY) * 60.0 + cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60.0 + cal.get(Calendar.MILLISECOND) / 60_000.0
}

private fun normalizeMinutes1440(minutes: Double): Double {
    val mod = minutes % 1440.0
    return if (mod < 0.0) mod + 1440.0 else mod
}

/** The Sun's declination and the equation of time at [referenceTime] (the first half of desktop's NOAA position). */
private fun sunDeclinationAndEquationOfTime(referenceTime: Date): Pair<Double, Double> {
    val julianDay = computeJulianDayUtc(referenceTime)
    val julianCenturies = (julianDay - 2451545.0) / 36525.0
    val meanLongitude = normalizeAngleDeg(280.46646 + julianCenturies * (36000.76983 + julianCenturies * 0.0003032))
    val meanAnomaly = normalizeAngleDeg(357.52911 + julianCenturies * (35999.05029 - 0.0001537 * julianCenturies))
    val earthOrbitEccentricity = 0.016708634 - julianCenturies * (0.000042037 + 0.0000001267 * julianCenturies)
    val meanAnomalyRad = Math.toRadians(meanAnomaly)
    val sinM = sin(meanAnomalyRad)
    val sin2M = sin(2.0 * meanAnomalyRad)
    val sin3M = sin(3.0 * meanAnomalyRad)
    val equationOfCenter =
        sinM * (1.914602 - julianCenturies * (0.004817 + 0.000014 * julianCenturies)) +
            sin2M * (0.019993 - 0.000101 * julianCenturies) +
            sin3M * 0.000289
    val trueLongitude = meanLongitude + equationOfCenter
    val omega = 125.04 - 1934.136 * julianCenturies
    val apparentLongitude = trueLongitude - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
    val meanObliquity =
        23.0 + (26.0 + ((21.448 - julianCenturies * (46.815 + julianCenturies * (0.00059 - julianCenturies * 0.001813))) / 60.0)) / 60.0
    val obliquityCorrection = meanObliquity + 0.00256 * cos(Math.toRadians(omega))
    val obliquityRad = Math.toRadians(obliquityCorrection)
    val apparentLongitudeRad = Math.toRadians(apparentLongitude)
    val sinDeclination = (sin(obliquityRad) * sin(apparentLongitudeRad)).coerceIn(-1.0, 1.0)
    val declinationRad = asin(sinDeclination)
    val y = tan(obliquityRad / 2.0)
    val y2 = y * y
    val meanLongitudeRad = Math.toRadians(meanLongitude)
    val equationOfTimeMinutes =
        4.0 *
            Math.toDegrees(
                y2 * sin(2.0 * meanLongitudeRad) -
                    2.0 * earthOrbitEccentricity * sinM +
                    4.0 * earthOrbitEccentricity * y2 * sinM * cos(2.0 * meanLongitudeRad) -
                    0.5 * y2 * y2 * sin(4.0 * meanLongitudeRad) -
                    1.25 * earthOrbitEccentricity * earthOrbitEccentricity * sin(2.0 * meanAnomalyRad),
            )
    return declinationRad to equationOfTimeMinutes
}

internal fun computeSolarPositionNoaaUtc(
    referenceTime: Date,
    latitude: Double,
    longitude: Double,
): SolarPosition {
    val (declinationRad, equationOfTimeMinutes) = sunDeclinationAndEquationOfTime(referenceTime)
    val sinDeclination = sin(declinationRad)
    val cosDeclination = cos(declinationRad)
    val utcMinutes = utcMinutesOfDay(referenceTime)
    val trueSolarTimeMinutes = normalizeMinutes1440(utcMinutes + equationOfTimeMinutes + 4.0 * longitude)
    var hourAngleDegrees = trueSolarTimeMinutes / 4.0 - 180.0
    if (hourAngleDegrees < -180.0) hourAngleDegrees += 360.0
    val hourAngleRad = Math.toRadians(hourAngleDegrees)
    val latRad = Math.toRadians(latitude)
    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val cosZenith = (sinLat * sinDeclination + cosLat * cosDeclination * cos(hourAngleRad)).coerceIn(-1.0, 1.0)
    val zenithRad = acos(cosZenith)
    val elevationDegrees = 90.0 - Math.toDegrees(zenithRad)
    val azimuthRad = atan2(sin(hourAngleRad), cos(hourAngleRad) * sinLat - tan(declinationRad) * cosLat)
    return SolarPosition(normalizeAngleDeg(Math.toDegrees(azimuthRad) + 180.0), elevationDegrees)
}

/** Where the Sun is overhead at [time]: its declination and longitude, in degrees (for the globe's day and night). */
internal fun subsolarPoint(time: Date): Pair<Double, Double> {
    val (declinationRad, equationOfTimeMinutes) = sunDeclinationAndEquationOfTime(time)
    // Local solar noon where the true solar time is 720 minutes
    val longitude = -(utcMinutesOfDay(time) + equationOfTimeMinutes - 720.0) / 4.0
    return Math.toDegrees(declinationRad) to ((longitude + 540.0) % 360.0 - 180.0)
}

// ---- The Moon (EarthEphemeris.kt) ------------------------------------------------------------------------------------

private fun meanObliquityRad(julianDay: Double): Double {
    val t = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY
    val seconds = MEAN_OBLIQUITY_COEFF0 - t * (MEAN_OBLIQUITY_COEFF1 + t * (MEAN_OBLIQUITY_COEFF2 - MEAN_OBLIQUITY_COEFF3 * t))
    val degrees = 23.0 + (26.0 + seconds / 60.0) / 60.0
    return degrees * DEG_TO_RAD
}

/** The Moon's ecliptic longitude and latitude, degrees (Meeus, simplified). */
private fun computeMoonEclipticPosition(julianDay: Double): Pair<Double, Double> {
    val t = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY
    val lp = normalizeAngleDeg(MOON_MEAN_LONGITUDE_J2000 + MOON_MEAN_LONGITUDE_RATE * t)
    val mp = normalizeAngleDeg(MOON_MEAN_ANOMALY_J2000 + MOON_MEAN_ANOMALY_RATE * t)
    val d = normalizeAngleDeg(MOON_MEAN_ELONGATION_J2000 + MOON_MEAN_ELONGATION_RATE * t)
    val ms = normalizeAngleDeg(SUN_MEAN_ANOMALY_J2000 + SUN_MEAN_ANOMALY_RATE * t)
    val f = normalizeAngleDeg(MOON_ARG_LATITUDE_J2000 + MOON_ARG_LATITUDE_RATE * t)
    val mpRad = mp * DEG_TO_RAD
    val dRad = d * DEG_TO_RAD
    val msRad = ms * DEG_TO_RAD
    val fRad = f * DEG_TO_RAD
    val dL = 6.289 * sin(mpRad) + 1.274 * sin(2.0 * dRad - mpRad) + 0.658 * sin(2.0 * dRad) + 0.214 * sin(2.0 * mpRad) - 0.186 * sin(msRad) - 0.114 * sin(2.0 * fRad)
    val dB = 5.128 * sin(fRad) + 0.281 * sin(mpRad + fRad) + 0.278 * sin(mpRad - fRad)
    return normalizeAngleDeg(lp + dL) to dB
}

private fun greenwichMeanSiderealTimeRad(julianDay: Double): Double {
    val t = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY
    val gmstDeg = 280.46061837 + 360.98564736629 * (julianDay - J2000_EPOCH_JD) + t * t * (0.000387933 - t / 38710000.0)
    return normalizeRad(gmstDeg * DEG_TO_RAD)
}

internal fun localSiderealTimeRad(
    julianDay: Double,
    longitudeDeg: Double,
): Double = normalizeRad(greenwichMeanSiderealTimeRad(julianDay) + longitudeDeg * DEG_TO_RAD)

internal data class HorizontalPosition(
    val azimuthFromNorthDeg: Double,
    val elevationDeg: Double,
)

/** The Moon's azimuth and elevation for the observer. */
internal fun computeMoonHorizontalPosition(
    julianDay: Double,
    latitudeDeg: Double,
    longitudeDeg: Double,
): HorizontalPosition {
    val (lon, lat) = computeMoonEclipticPosition(julianDay)
    val lonRad = lon * DEG_TO_RAD
    val latRad = lat * DEG_TO_RAD
    val obliquityRad = meanObliquityRad(julianDay)
    val decRad = asin((sin(latRad) * cos(obliquityRad) + cos(latRad) * sin(obliquityRad) * sin(lonRad)).coerceIn(-1.0, 1.0))
    var raRad = atan2(sin(lonRad) * cos(obliquityRad) - tan(latRad) * sin(obliquityRad), cos(lonRad))
    if (raRad < 0) raRad += 2 * PI
    val observerLat = latitudeDeg * DEG_TO_RAD
    val hourAngleRad = normalizeRad(localSiderealTimeRad(julianDay, longitudeDeg) - raRad)
    val sinLat = sin(observerLat)
    val cosLat = cos(observerLat)
    val elevationRad = asin((sin(decRad) * sinLat + cos(decRad) * cosLat * cos(hourAngleRad)).coerceIn(-1.0, 1.0))
    val azimuthRad = atan2(-sin(hourAngleRad), tan(decRad) * cosLat - sinLat * cos(hourAngleRad))
    return HorizontalPosition(normalizeAngleDeg(Math.toDegrees(azimuthRad)), Math.toDegrees(elevationRad))
}

/** The Sun's ecliptic longitude, degrees (simplified). */
internal fun computeSunEclipticLongitude(julianDay: Double): Float {
    val t = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY
    val l0 = normalizeAngleDeg(SUN_MEAN_LONGITUDE_J2000 + SUN_MEAN_LONGITUDE_RATE * t)
    val m = normalizeAngleDeg(SUN_MEAN_ANOMALY_J2000 + SUN_MEAN_ANOMALY_RATE * t)
    val mRad = m * DEG_TO_RAD
    val c = 1.9146 * sin(mRad) + 0.02 * sin(2.0 * mRad)
    return normalizeAngleDeg(l0 + c).toFloat()
}

// ---- The sky's frame (SkyScene.kt): +x east, +y up, -z north --------------------------------------------------------

internal data class Direction(val x: Float, val y: Float, val z: Float)

/** Unit direction for an azimuth (from north, clockwise) and an elevation, in degrees. */
internal fun skyDirection(
    azimuthDeg: Double,
    elevationDeg: Double,
): Direction {
    val az = azimuthDeg * DEG_TO_RAD
    val el = elevationDeg * DEG_TO_RAD
    return Direction((sin(az) * cos(el)).toFloat(), sin(el).toFloat(), (-cos(az) * cos(el)).toFloat())
}

/**
 * The columns of the rotation taking equatorial coordinates (x toward RA 0h, z to the celestial pole) to the sky at
 * local sidereal time [lstRad] and latitude [latitudeDeg] (desktop equatorialToSky, as a matrix).
 */
internal fun equatorialToSky(
    lstRad: Double,
    latitudeDeg: Double,
): Array<DoubleArray> {
    val phi = latitudeDeg * DEG_TO_RAD
    // Where the equator crosses the meridian, due east, and the pole
    val meridian = doubleArrayOf(0.0, cos(phi), sin(phi))
    val east = doubleArrayOf(1.0, 0.0, 0.0)
    val pole = doubleArrayOf(0.0, sin(phi), -cos(phi))
    val c = cos(lstRad)
    val s = sin(lstRad)
    val c0 = DoubleArray(3) { c * meridian[it] - s * east[it] }
    val c1 = DoubleArray(3) { s * meridian[it] + c * east[it] }
    return arrayOf(c0, c1, pole)
}

// ---- The Hebrew day and the moon's age (EarthWidgetZmanimView.kt) -----------------------------------------------------

internal fun jewishCalendarAt(
    time: Date,
    timeZone: TimeZone,
): JewishCalendar = JewishCalendar(kotlin.time.Instant.fromEpochMilliseconds(time.time), timeZone.toKotlin())

/** Moves the Jewish calendar to the previous Hebrew month. */
internal fun goToPreviousHebrewMonth(jewishCalendar: JewishCalendar) {
    jewishCalendar.setJewishDate(jewishCalendar.jewishYear, jewishCalendar.jewishMonth, 1)
    jewishCalendar.back() // lands on the last day of the previous month
    jewishCalendar.setJewishDate(jewishCalendar.jewishYear, jewishCalendar.jewishMonth, 1)
}

/**
 * The Hebrew calendar of one local day ([startMillis] until [endMillis]): its day of the month and the month's
 * length, and the moladot an instant of it is aged from.
 */
internal class HebrewDay(
    val startMillis: Long,
    val endMillis: Long,
    val dayOfMonth: Int,
    val daysInMonth: Int,
    val firstOfMonth: LocalDate,
    private val molad: Long,
    private val previousMolad: Long,
) {
    /** As desktop computeHalakhicPhaseAngle: the age since the last molad, as an angle. */
    fun phaseAngleAt(millis: Long): Float {
        val from = if (molad > millis) previousMolad else molad
        return (((millis - from).toDouble() / LUNAR_CYCLE_MILLIS) * 360.0).toFloat() % 360f
    }

    /** The last molad at or before [millis]. */
    fun moladBefore(millis: Long): Long = if (molad > millis) previousMolad else molad
}

internal fun hebrewDayAt(
    millis: Long,
    timeZone: TimeZone,
): HebrewDay {
    val zone = timeZone.toZoneId()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val calendar = jewishCalendarAt(Date(millis), timeZone)
    val dayOfMonth = calendar.jewishDayOfMonth
    val daysInMonth = calendar.daysInJewishMonth
    val molad = calendar.moladAsInstant.toDate().time
    val firstOfMonth = date.minusDays((dayOfMonth - 1).toLong())
    goToPreviousHebrewMonth(calendar)
    val previousMolad = calendar.moladAsInstant.toDate().time
    return HebrewDay(start, end, dayOfMonth, daysInMonth, firstOfMonth, molad, previousMolad)
}

/** Where the moon is on its orbit at [millis]: gliding from its day's label to the next one, a turn a month. */
internal fun moonOrbitDegrees(
    hebrewDay: HebrewDay,
    millis: Long,
): Float {
    val daysInMonth = hebrewDay.daysInMonth
    val dayOfMonth = hebrewDay.dayOfMonth
    return if (daysInMonth > 0 && dayOfMonth in 1..daysInMonth) {
        val stepDegrees = 360f / daysInMonth.toFloat()
        val dayFraction = (millis - hebrewDay.startMillis).toFloat() / (hebrewDay.endMillis - hebrewDay.startMillis)
        normalizeAngle360(ORBIT_DAY_LABEL_START_DEGREES + (dayOfMonth - 1 + dayFraction) * stepDegrees)
    } else {
        normalizeAngle360(hebrewDay.phaseAngleAt(millis) + ORBIT_DAY_LABEL_START_DEGREES)
    }
}

internal fun orbitDegreesForDaysSinceMolad(
    daysSinceMolad: Double,
    daysInMonth: Int,
): Float {
    if (daysInMonth <= 0) return normalizeAngle360(ORBIT_DAY_LABEL_START_DEGREES)
    val stepDegrees = 360f / daysInMonth.toFloat()
    // Align "day N after molad" with the same day label (labels are 1-based).
    return normalizeAngle360(ORBIT_DAY_LABEL_START_DEGREES + ((daysSinceMolad - 1.0) * stepDegrees).toFloat())
}

/** The Kiddush Levana window on the moon's orbit (desktop computeKiddushLevanaData). */
internal data class KiddushLevanaArc(
    val startDegrees: Float,
    val endDegrees: Float,
    val isCurrentlyAllowed: Boolean,
)

internal fun computeKiddushLevanaArc(
    referenceTime: Date,
    timeZone: TimeZone,
    earliestOpinion: KiddushLevanaEarliestOpinion,
    latestOpinion: KiddushLevanaLatestOpinion,
): KiddushLevanaArc {
    val jewishCalendar = jewishCalendarAt(referenceTime, timeZone)
    val earliestTime =
        when (earliestOpinion) {
            KiddushLevanaEarliestOpinion.DAYS_3 -> jewishCalendar.tchilasZmanKidushLevana3Days
            KiddushLevanaEarliestOpinion.DAYS_7 -> jewishCalendar.tchilasZmanKidushLevana7Days
        }.toDate()
    val latestTime =
        when (latestOpinion) {
            KiddushLevanaLatestOpinion.BETWEEN_MOLDOS -> jewishCalendar.sofZmanKidushLevanaBetweenMoldos
            KiddushLevanaLatestOpinion.DAYS_15 -> jewishCalendar.sofZmanKidushLevana15Days
        }.toDate()
    var molad = jewishCalendar.moladAsInstant.toDate()
    if (molad.time > referenceTime.time) {
        goToPreviousHebrewMonth(jewishCalendar)
        molad = jewishCalendar.moladAsInstant.toDate()
    }
    val daysInMonth = jewishCalendar.daysInJewishMonth

    fun dateToOrbitDegrees(date: Date): Float = orbitDegreesForDaysSinceMolad((date.time - molad.time) / 86_400_000.0, daysInMonth)
    return KiddushLevanaArc(
        startDegrees = dateToOrbitDegrees(earliestTime),
        endDegrees = dateToOrbitDegrees(latestTime),
        isCurrentlyAllowed = referenceTime.time >= earliestTime.time && referenceTime.time <= latestTime.time,
    )
}

// ---- The year's events (SolarSystemWidgetView.kt) ----------------------------------------------------------------------

internal enum class SolarEventCategory(
    val colorRgb: Int,
    val major: Boolean,
) {
    YomTov(0xFFD166, major = true),
    Rabbinic(0x7FDBFF, major = true),
    Fast(0xFF8A80, major = true),
    RoshChodesh(0xB8B8B8, major = false),
}

/** A holiday / fast / Rosh Chodesh of a Hebrew year, spanning [start]..[end] (inclusive). */
internal data class SolarEvent(
    val name: String,
    val category: SolarEventCategory,
    val start: LocalDate,
    val end: LocalDate,
    /** Always named on the orbit (the landmarks of the year); the others only when tapped. */
    val pinned: Boolean = false,
)

private val PinnedFamilies =
    setOf(
        JewishCalendar.ROSH_HASHANA,
        JewishCalendar.SUCCOS,
        JewishCalendar.CHANUKAH,
        JewishCalendar.PURIM,
        JewishCalendar.PESACH,
        JewishCalendar.SHAVUOS,
        JewishCalendar.TISHA_BEAV,
    )

/** Groups KosherKotlin's per-day indices into one event per holiday (Sukkot with its Chol HaMoed, etc.). */
private fun holidayFamily(yomTovIndex: Int): Pair<Int, SolarEventCategory>? =
    when (yomTovIndex) {
        JewishCalendar.ROSH_HASHANA -> JewishCalendar.ROSH_HASHANA to SolarEventCategory.YomTov
        JewishCalendar.YOM_KIPPUR -> JewishCalendar.YOM_KIPPUR to SolarEventCategory.YomTov
        JewishCalendar.SUCCOS, JewishCalendar.CHOL_HAMOED_SUCCOS, JewishCalendar.HOSHANA_RABBA -> JewishCalendar.SUCCOS to SolarEventCategory.YomTov
        JewishCalendar.SHEMINI_ATZERES, JewishCalendar.SIMCHAS_TORAH -> JewishCalendar.SHEMINI_ATZERES to SolarEventCategory.YomTov
        JewishCalendar.PESACH, JewishCalendar.CHOL_HAMOED_PESACH -> JewishCalendar.PESACH to SolarEventCategory.YomTov
        JewishCalendar.SHAVUOS -> JewishCalendar.SHAVUOS to SolarEventCategory.YomTov
        JewishCalendar.CHANUKAH -> JewishCalendar.CHANUKAH to SolarEventCategory.Rabbinic
        JewishCalendar.TU_BESHVAT -> JewishCalendar.TU_BESHVAT to SolarEventCategory.Rabbinic
        JewishCalendar.PURIM, JewishCalendar.SHUSHAN_PURIM -> JewishCalendar.PURIM to SolarEventCategory.Rabbinic
        JewishCalendar.LAG_BAOMER -> JewishCalendar.LAG_BAOMER to SolarEventCategory.Rabbinic
        JewishCalendar.TU_BEAV -> JewishCalendar.TU_BEAV to SolarEventCategory.Rabbinic
        JewishCalendar.FAST_OF_GEDALYAH, JewishCalendar.TENTH_OF_TEVES, JewishCalendar.FAST_OF_ESTHER,
        JewishCalendar.SEVENTEEN_OF_TAMMUZ, JewishCalendar.TISHA_BEAV,
        -> yomTovIndex to SolarEventCategory.Fast
        else -> null
    }

/** Every holiday, fast and Rosh Chodesh of Hebrew [hebrewYear] (1 Tishrei → 29 Elul), in date order. */
internal fun computeHebrewYearEvents(
    hebrewYear: Int,
    inIsrael: Boolean,
): List<SolarEvent> {
    val formatter = HebrewDateFormatter().apply { isHebrewFormat = true }
    val calendar = JewishCalendar(hebrewYear, HebrewMonth.TISHREI, 1).apply { this.inIsrael = inIsrael }
    val events = ArrayList<SolarEvent>()

    // Holidays and Rosh Chodesh are tracked apart: Rosh Chodesh Tevet falls inside Chanukah.
    class Run(
        var key: Int?,
        var event: SolarEvent? = null,
    )
    val holiday = Run(null)
    val roshChodesh = Run(null)

    fun Run.step(
        family: Pair<Int, SolarEventCategory>?,
        date: LocalDate,
        name: () -> String,
    ) {
        val current = event
        if (current != null && family?.first == key && current.end == date.minusDays(1)) {
            event = current.copy(end = date)
        } else {
            current?.let { events += it }
            key = family?.first
            event = family?.let { SolarEvent(name(), it.second, date, date, pinned = it.first in PinnedFamilies) }
        }
    }

    repeat(calendar.daysInJewishYear) {
        val date = calendar.gregorianLocalDate.toJavaLocalDate()
        val family = holidayFamily(calendar.yomTovIndex)
        holiday.step(family, date) {
            // "א׳ חנוכה" → "חנוכה": one label for the eight days
            formatter.formatYomTov(calendar).let { if (family?.first == JewishCalendar.CHANUKAH) it.substringAfter(' ') else it }
        }
        roshChodesh.step(if (calendar.isRoshChodesh) 0 to SolarEventCategory.RoshChodesh else null, date) { "" }
        // Rosh Chodesh is named after the month it opens: its last day
        if (calendar.isRoshChodesh) roshChodesh.event = roshChodesh.event?.copy(name = formatter.formatMonth(calendar))
        calendar.forward(DateTimeUnit.DAY, 1)
    }
    listOf(holiday, roshChodesh).forEach { run -> run.event?.let { events += it } }
    return events.sortedBy { it.start }
}

/** Local time used to place a date on the orbit. */
private const val EVENT_HOUR = 12

/** Heliocentric longitude of the Earth on [date] (desktop earthLongitudeOn): the Sun's, plus 180°. */
internal fun earthLongitudeOn(
    date: LocalDate,
    timeZone: TimeZone,
): Float =
    normalizeAngle360(
        computeSunEclipticLongitude(computeJulianDayUtc(Date.from(date.atTime(EVENT_HOUR, 0).atZone(timeZone.toZoneId()).toInstant()))) + 180f,
    )

/** Heliocentric longitude of the Earth at [millis]. */
internal fun earthLongitudeAt(millis: Long): Float = normalizeAngle360(computeSunEclipticLongitude(computeJulianDayUtc(Date(millis))) + 180f)

/** "B-V" colour index → star tint (desktop SkyAtmosphere.bvToRgb). */
internal fun bvToRgb(bv: Double): Int {
    val b = bv.coerceIn(-0.4, 2.0)
    val t = 4600.0 * (1.0 / (0.92 * b + 1.7) + 1.0 / (0.92 * b + 0.62))
    val k = t / 100.0
    val r = if (k <= 66) 255.0 else 329.7 * Math.pow(k - 60, -0.1332)
    val g = if (k <= 66) 99.47 * Math.log(k) - 161.12 else 288.12 * Math.pow(k - 60, -0.0755)
    val bl = if (k >= 66) 255.0 else if (k <= 19) 0.0 else 138.52 * Math.log(k - 10) - 305.04

    fun c(v: Double) = v.coerceIn(0.0, 255.0).toInt()

    // Paler than the blackbody: stars look near white to the eye, only tinted
    fun pale(v: Int) = (v + (255 - v) * 0.45).toInt()
    return (pale(c(r)) shl 16) or (pale(c(g)) shl 8) or pale(c(bl))
}
