package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishDate
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.time.Clock

/**
 * A place of [worldPlaces] under its country and city names (the stored region values). [name] is blank for an unset
 * region, which computes for Jerusalem like desktop's HomeUserLocation.
 */
data class City(val name: String, val country: String, val lat: Double, val lon: Double, val elevation: Double, val tz: String) {
    val inIsrael: Boolean get() = country == ISRAEL_COUNTRY_NAME
    val location: EarthWidgetLocation get() = EarthWidgetLocation(lat, lon, elevation, TimeZone.getTimeZone(tz))

    /** Today where the city is: another time zone may already be on another day. */
    fun today(): LocalDate = LocalDate.now(ZoneId.of(tz))
}

/** Every city of desktop's [worldPlaces], countries and cities in its order. */
val Cities: List<City> = worldPlaces.flatMap { (country, cities) -> cities.map { (name, p) -> City(name, country, p.lat, p.lng, p.elevation, p.zoneId) } }

/** Desktop HomeUserLocationViewModel's DEFAULT_PLACE: Jerusalem. */
val DEFAULT_PLACE = Place(31.7683, 35.2137, 800.0, "Asia/Jerusalem")

/**
 * Desktop HomeUserLocationViewModel.resolveState: the place of the region settings, Jerusalem when the region is unset
 * or unknown; the label is the stored city and Israel is decided by the stored country alone.
 */
fun userCity(country: String?, city: String?): City {
    val place = if (!country.isNullOrBlank() && !city.isNullOrBlank()) worldPlaces[country]?.get(city) else null
    val p = place ?: DEFAULT_PLACE
    return City(city?.takeIf { it.isNotBlank() }.orEmpty(), country.orEmpty(), p.lat, p.lng, p.elevation, p.zoneId)
}

// ---- Temple countdown (desktop features/home/widgets/temple/TempleCountdownWidget.kt) ---------------------------------

/** Years, months and days since the Temple's destruction. */
internal data class TempleCountdownData(
    val years: Int,
    val months: Int,
    val days: Int,
)

private const val DESTRUCTION_YEAR = 3830
private const val DESTRUCTION_DAY = 9
private val DESTRUCTION_MONTH = HebrewMonth.AV

// Fallback if sunset is null (polar regions) or computation fails — keeps the loop alive.
private const val FALLBACK_REFRESH_MS = 60L * 60 * 1000

// Small buffer past sunset so JewishDate definitely resolves the new Hebrew day.
private const val POST_SUNSET_BUFFER_MS = 1_000L

private val JERUSALEM =
    GeoLocation(
        "Jerusalem",
        31.7683,
        35.2137,
        800.0,
        kotlinx.datetime.TimeZone.of("Asia/Jerusalem"),
    )

/** Verbatim desktop computeTempleCountdown: the Hebrew day rolls over at Jerusalem sunset. */
internal fun computeTempleCountdown(): TempleCountdownData {
    // After sunset in Jerusalem the Hebrew day rolls over — advance the Gregorian date by one day
    // so JewishDate resolves to the new Hebrew day instead of yesterday's.
    val calendar = ComplexZmanimCalendar(JERUSALEM)
    var date = calendar.localDateTime.date
    val sunset = calendar.sunset
    if (sunset != null && sunset <= Clock.System.now()) {
        date = date.plus(1, DateTimeUnit.DAY)
    }
    val today = JewishDate(date)

    // Most recent Av-9 anniversary that is strictly before today. Using `>=` (rather than `>`)
    // means the anniversary day itself is reported as "almost a full year" instead of "X years
    // and 0 days", keeping the display free of 0-day artifacts.
    var years = (today.jewishYear - DESTRUCTION_YEAR).toInt()
    var anniversary = JewishDate(today.jewishYear, DESTRUCTION_MONTH, DESTRUCTION_DAY)
    if (anniversary >= today) {
        years -= 1
        anniversary = JewishDate(today.jewishYear - 1, DESTRUCTION_MONTH, DESTRUCTION_DAY)
    }

    // Walk forward by full Hebrew months. Same `>=` rationale: stop just before reaching today.
    var months = 0
    var cursor = anniversary
    while (true) {
        val next = cursor.copy().forward(DateTimeUnit.MONTH, 1)
        if (next >= today) break
        cursor = next
        months += 1
    }

    val days = cursor.gregorianLocalDate.daysUntil(today.gregorianLocalDate)
    return TempleCountdownData(years, months, days)
}

/** The displayed value only changes at Jerusalem sunset, so wake up exactly then. */
internal fun millisUntilNextJerusalemSunset(nowMillis: Long): Long {
    val cal = ComplexZmanimCalendar(JERUSALEM)
    var sunset = cal.sunset
    if (sunset == null || sunset.toEpochMilliseconds() <= nowMillis) {
        cal.localDateTime = LocalDateTime(cal.localDateTime.date.plus(1, DateTimeUnit.DAY), cal.localDateTime.time)
        sunset = cal.sunset
    }
    val target = sunset?.toEpochMilliseconds() ?: return FALLBACK_REFRESH_MS
    return (target - nowMillis + POST_SUNSET_BUFFER_MS).coerceAtLeast(POST_SUNSET_BUFFER_MS)
}
