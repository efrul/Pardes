package io.github.kdroidfilter.seforimapp.android.appwidget

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.data.City
import io.github.kdroidfilter.seforimapp.android.data.JewishDay
import io.github.kdroidfilter.seforimapp.android.data.Limud
import io.github.kdroidfilter.seforimapp.android.data.LimudItem
import io.github.kdroidfilter.seforimapp.android.data.LimudResources
import io.github.kdroidfilter.seforimapp.android.data.LuachEvent
import io.github.kdroidfilter.seforimapp.android.data.NextZman
import io.github.kdroidfilter.seforimapp.android.data.ShabbatTimes
import io.github.kdroidfilter.seforimapp.android.data.TempleCountdownData
import io.github.kdroidfilter.seforimapp.android.data.ZmanRow
import io.github.kdroidfilter.seforimapp.android.data.ZmanimDay
import io.github.kdroidfilter.seforimapp.android.data.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.android.data.computeShabbatTimes
import io.github.kdroidfilter.seforimapp.android.data.computeTempleCountdown
import io.github.kdroidfilter.seforimapp.android.data.hebrewFormatter
import io.github.kdroidfilter.seforimapp.android.data.limudOfDay
import io.github.kdroidfilter.seforimapp.android.data.millisUntilNextJerusalemSunset
import io.github.kdroidfilter.seforimapp.android.data.nextZmanim
import io.github.kdroidfilter.seforimapp.android.data.upcomingEvents
import io.github.kdroidfilter.seforimapp.android.ui.theme.AccentColor
import io.github.kdroidfilter.seforimapp.android.ui.theme.ThemeMode
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitColors
import io.github.kdroidfilter.seforimapp.android.ui.theme.zayitColors
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

// Desktop's key for the Limud widget's choice (home_widget.<widget id>.<setting>, WidgetSettings): read here as a plain
// preference, since touching ui/home's LimudSettings first from a receiver hits its init cycle with LimudWidget.
private const val LIMUD_SHOWN_KEY = "home_widget.limud.shown"

/**
 * What the widgets show at one place on one day: the date, the zmanim, the Shabbat, the coming events, the limudim.
 * Worked out once per (day, place, luach) and kept by [WidgetCache] for every redraw that day (a resize, a widget
 * added, the next zman passing), each part the first time a placed widget asks for it. Not thread-safe: only
 * [ZayitWidgets]'s thread touches it.
 */
internal class WidgetDay(val city: City, val opinion: ZmanimOpinion, val today: LocalDate) {
    val inIsrael = city.inIsrael

    /** Blank for an unset region (Jerusalem's zmanim then, unnamed, as desktop). */
    val cityLabel: String? = city.name.ifBlank { null }
    val location = city.location
    private val zone = ZoneId.of(city.tz)
    private val midnight: Long = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val nextMidnight: Long = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val jc: JewishCalendar = JewishDay.calendar(today, inIsrael)

    /** "כ״א תשרי": the year is the one being read (desktop upcomingEvents's form). */
    val hebrewDateShort = "${hebrewFormatter.formatHebrewNumber(jc.jewishDayOfMonth)} ${hebrewFormatter.formatMonth(jc)}"

    val zmanRows by lazy { ZmanimDay.rows(city, today, opinion) }

    /**
     * nextZmanim's list for any moment of [today] before it drops the ones passed: the zmanim of yesterday, today and
     * tomorrow after today's midnight, in order ([WidgetData.upcomingZmanim] keeps the ones after now).
     */
    val zmanimFromMidnight by lazy { nextZmanim(Date(midnight), location, opinion, inIsrael, count = Int.MAX_VALUE) }
    val shabbat by lazy { computeShabbatTimes(today, location, opinion, cityLabel, inIsrael) }
    val events by lazy { upcomingEvents(today, location, opinion, cityLabel, inIsrael, limit = 8) }

    private var limudimShown: Set<Limud>? = null
    private var limudimOfDay: List<Pair<Limud, LimudItem>> = emptyList()

    /** The day's limudim among [shown], each with its own [Limud] (two kinds share a tag: רמב״ם, תהילים). */
    fun limudim(app: ZayitApplication, shown: Set<Limud>): List<Pair<Limud, LimudItem>> {
        if (limudimShown != shown) {
            // Before any schedule is read: limud.pb's schedules are read once per process, empty without the app's resources
            LimudResources.install(app)
            limudimOfDay = Limud.entries.filter { it in shown }.mapNotNull { limud -> limudOfDay(today, inIsrael, setOf(limud)).firstOrNull()?.let { limud to it } }
            limudimShown = shown
        }
        return limudimOfDay
    }
}

/**
 * What the widgets computed, kept between redraws so that one asking only for a size or a widget redraws without
 * working out the zmanim again: the [WidgetDay] of the user's place, and the Temple count until Jerusalem's next sunset.
 * Only [ZayitWidgets]'s thread touches it.
 */
internal object WidgetCache {
    private var day: WidgetDay? = null
    private var temple: TempleCountdownData? = null
    private var templeTurnsAt = Long.MIN_VALUE

    /** The day at the user's place at [now] (the civil day there), and the luach the user follows. */
    fun day(app: ZayitApplication, now: Long): WidgetDay {
        val city = app.prefs.userCityFlow.value
        val opinion = app.prefs.zmanimOpinion.value
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.of(city.tz)).toLocalDate()
        return day?.takeIf { it.city == city && it.opinion == opinion && it.today == today } ?: WidgetDay(city, opinion, today).also { day = it }
    }

    /** The count since the destruction at [now], and when it next turns (Jerusalem's sunset, a second after). */
    fun temple(now: Long): Pair<TempleCountdownData, Long> {
        val kept = temple
        if (kept != null && now < templeTurnsAt) return kept to templeTurnsAt
        val count = computeTempleCountdown()
        templeTurnsAt = now + millisUntilNextJerusalemSunset(now)
        temple = count
        return count to templeTurnsAt
    }

    /** The clock, the time zone or the locale changed: worked out again on the next redraw. */
    fun clear() {
        day = null
        temple = null
        templeTurnsAt = Long.MIN_VALUE
    }
}

/**
 * One redraw: the cached [day], the moment it is drawn at ([nowMillis]) and what may change between two redraws (the
 * user's font size, the colours, the limudim picked on the Home).
 */
internal class WidgetData(val app: ZayitApplication, nowMillis: Long, private val day: WidgetDay) {
    val packageName: String = app.packageName
    val now = Date(nowMillis)
    val inIsrael: Boolean get() = day.inIsrael

    /** Blank for an unset region (Jerusalem's zmanim then, unnamed, as desktop). */
    val cityLabel: String? get() = day.cityLabel
    val today: LocalDate get() = day.today
    val nextMidnight: Long get() = day.nextMidnight
    val jc: JewishCalendar get() = day.jc

    /** "כ״א תשרי": the year is the one being read (desktop upcomingEvents's form). */
    val hebrewDateShort: String get() = day.hebrewDateShort

    /** The text the launcher draws in sp follows the user's font size: rows are counted with it. */
    val fontScale: Float = app.resources.configuration.fontScale.coerceIn(0.85f, 2f)
    val density: Float = app.resources.displayMetrics.density

    /** Measures the widgets' text, to pick the wording that fits (WidgetRenderers): one per redraw, used in turn. */
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = day.location.timeZone }

    /** "HH:mm" in the place's time zone, kept left-to-right in the Hebrew line (rememberClock). */
    fun clock(date: Date): String = "⁦${timeFormat.format(date)}⁩"

    val zmanRows: List<ZmanRow> get() = day.zmanRows

    /** The next 12 zmanim (nextZmanim's count for the widget), from the day's list. */
    val upcomingZmanim: List<NextZman> by lazy { day.zmanimFromMidnight.asSequence().filter { it.time.after(now) }.take(12).toList() }
    val shabbat: ShabbatTimes get() = day.shabbat
    val events: List<LuachEvent> get() = day.events

    private val templeCount by lazy { WidgetCache.temple(now.time) }
    val temple: TempleCountdownData get() = templeCount.first

    /** Jerusalem's next sunset (a second after), where [temple] turns. */
    val templeTurnsAt: Long get() = templeCount.second

    /** The day's limudim the user ticked on the Home, each with its own [Limud] (two kinds share a tag: רמב״ם, תהילים). */
    val limudim: List<Pair<Limud, LimudItem>> by lazy { day.limudim(app, shownLimudim()) }

    /** WidgetSettings' enumSet codec: the names, comma separated; the defaults while the user never picked. */
    private fun shownLimudim(): Set<Limud> {
        val raw = app.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE).all[LIMUD_SHOWN_KEY]?.toString() ?: return Limud.defaults.toSet()
        return raw.split(',').filter { it.isNotEmpty() }.mapNotNullTo(mutableSetOf()) { name -> Limud.entries.firstOrNull { it.name == name } }
    }

    /**
     * The app's colours (ZayitTheme): its theme mode and accent. With the system's theme both are given and the
     * launcher picks by its night mode, so the widgets follow the phone's dark mode with the app not running.
     */
    val palette: WidgetPalette by lazy {
        val accent = app.prefs.accent.value
        fun colors(dark: Boolean) = zayitColors(dark, accent, if (accent == AccentColor.System) systemAccent(dark) else null)
        when (app.prefs.themeMode.value) {
            ThemeMode.Light -> colors(false).let { WidgetPalette(it, it) }
            ThemeMode.Dark -> colors(true).let { WidgetPalette(it, it) }
            ThemeMode.System -> WidgetPalette(colors(false), colors(true))
        }
    }

    // ZayitTheme's "מערכת" accent: Material You's primary (Android 12+, as the widgets themselves)
    private fun systemAccent(dark: Boolean): Color? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null else runCatching { (if (dark) dynamicDarkColorScheme(app) else dynamicLightColorScheme(app)).primary }.getOrNull()
}

/** The widget's colours by day and by night. */
internal class WidgetPalette(val light: ZayitColors, val dark: ZayitColors)

@RequiresApi(Build.VERSION_CODES.S)
internal fun RemoteViews.textColor(id: Int, p: WidgetPalette, pick: (ZayitColors) -> Color) =
    setColorInt(id, "setTextColor", pick(p.light).toArgb(), pick(p.dark).toArgb())

/** Colours a white shape (the panel, its border, a pill): ImageView.setColorFilter keeps the shape's rounded outline. */
@RequiresApi(Build.VERSION_CODES.S)
internal fun RemoteViews.tint(id: Int, p: WidgetPalette, pick: (ZayitColors) -> Color) =
    setColorInt(id, "setColorFilter", pick(p.light).toArgb(), pick(p.dark).toArgb())
