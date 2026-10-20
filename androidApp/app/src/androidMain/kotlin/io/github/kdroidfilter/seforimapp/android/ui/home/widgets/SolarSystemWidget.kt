package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.android.data.hebrewDateOf
import io.github.kdroidfilter.seforimapp.android.data.hebrewFormatter
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.delay
import kotlinx.datetime.toKotlinLocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Desktop 0.6.7 features/home/widgets/solarsystem (SolarSystemWidget, SolarSystemOptions, SolarSystemWindow) and
// earthwidget SolarSystemWidgetView.kt, in 2D: desktop renders the Sun, the Earth and the Moon with Filament. Android
// draws the same "מעגל השנה" flat: the Earth's orbit seen from a little above it, every holiday, fast and Rosh
// Chodesh of the Hebrew year shown where the Earth really is on that day (its ecliptic longitude, as desktop's
// computeHebrewYearEvents and earthLongitudeOn place them), the landmarks named, a tap names the others; the year
// stepper, the options (play speed, the full page's Earth and sky) and the full page that plays the year.

/** The play speeds offered, in simulated days a second (desktop PLAY_SPEEDS; 6 by default). */
internal val PLAY_SPEEDS = listOf(0.25f, 0.5f, 1f, 2f, 6f, 15f, 30f)
private const val PLAY_DAYS_PER_SECOND = 6f

private const val HOME_SOLAR_SYSTEM_TITLE = "מעגל השנה"

/** The solar system widget's settings, under desktop's keys (the full window's card frames have no use here). */
internal object SolarSystemSettings : WidgetSettings(SolarSystemWidget.id) {
    val showEarth by boolean(true)
    val showSky by boolean(true)
    val daysPerSecond by float(PLAY_DAYS_PER_SECOND)

    /** The solar system's Earth turns once per simulated day (true to life), else at a fixed watchable pace. */
    val proportionalSpin by boolean(true)

    /** Advanced: a frame-rate readout in the full window. */
    val showFps by boolean(false)
}

/** The solar system on the selected day, openable in its full page. */
internal object SolarSystemWidget : HomeWidget {
    override val id = "solar_system"
    override val title = HOME_SOLAR_SYSTEM_TITLE
    override val defaultSpan = CellSpan(9, 3)
    override val minSpan = CellSpan(6, 3)
    override val settings get() = SolarSystemSettings
    override val maxSpan = CellSpan(HOME_GRID_COLUMNS, 8)
    override val isSupported get() = !PlatformInfo.isLowEnd
    override val tool = ToolKind.YearCycle

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = SolarSystemCard(state, modifier, onFullscreen = { state.openTool(ToolKind.YearCycle) })

    @Composable
    override fun menuItems(state: HomeWidgetsState) = listOf(WidgetMenuItem(HOME_WIDGETS_OPTIONS) { state.optionsOpen = this })

    @Composable
    override fun Options(state: HomeWidgetsState) = SolarSystemOptionsPage(state)
}

/**
 * The card: its title, the year stepper, its options and its full page above the scene, the day shown below.
 * [detailed] is the full page's scene alone (desktop SolarSystemWindow): every event named, and no chrome of its own,
 * the page having the options and the date picker.
 */
@Composable
internal fun SolarSystemCard(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
    onFullscreen: (() -> Unit)?,
    detailed: Boolean = false,
) {
    val shown = state.shownDate
    val shownYear =
        rememberOffMain(shown, state.userInIsrael, tag = "solar.year") { JewishCalendar(shown.toKotlinLocalDate(), state.userInIsrael).jewishYear.toInt() }
            ?: return WidgetCardLoading(modifier)
    // Another year's holidays can be shown; a new day shown brings its own year back
    var year by remember(shownYear) { mutableStateOf(shownYear) }
    if (detailed) {
        WidgetCard(modifier) { SolarSystemScene(state, shownYear, detailed = true, modifier = Modifier.fillMaxSize()) }
        return
    }
    WidgetCard(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GradientDot(Color(0xFFFFC94A), Color(0xFFFFE6A3), size = 11.dp)
                Spacer(Modifier.width(6.dp))
                Text(HOME_SOLAR_SYSTEM_TITLE, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                // The year stepper: the earlier year on the right, as the reading goes
                ChromeIcon(Icons.Outlined.ChevronRight, "השנה הקודמת") { year-- }
                Text(hebrewFormatter.formatHebrewNumber(year), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                ChromeIcon(Icons.Outlined.ChevronLeft, "השנה הבאה") { year++ }
                ChromeIcon(Icons.Outlined.Settings, HOME_WIDGETS_OPTIONS) { state.optionsOpen = SolarSystemWidget }
                if (onFullscreen != null) ChromeIcon(Icons.Outlined.OpenInFull, HOME_SOLAR_SYSTEM_TITLE, onFullscreen)
            }
            SolarSystemScene(state, year, detailed = false, modifier = Modifier.fillMaxWidth().weight(1f))
            Text(
                hebrewDateOf(shown, state.inIsrael),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun ChromeIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
    }
}

/** Desktop's camera (SolarSystemWidgetView DEFAULT_SOLAR_ELEVATION_DEGREES, CARD_ASPECT): 22° over a card 0.472 high per wide. */
private const val CARD_ELEVATION_DEGREES = 22f
private const val CARD_ASPECT = 0.472f
private const val MAX_ELEVATION_DEGREES = 45f

/**
 * The orbit seen from a little above, turned so the Earth of the moment shown is in front. The camera rises over a
 * taller area as desktop's does (22° on the card, up to 45°): on a phone's full page, taller than wide, a 22° orbit
 * was a thin band across the middle with every name crowded into it, the rest of the page empty.
 */
private class SolarGeometry(
    width: Float,
    height: Float,
    private val earthLongitude: Float,
) {
    val cx = width / 2
    val cy = height / 2
    val rx = minOf(width * 0.40f, height * 1.15f)

    // The orbit's depth under the card's camera: the bodies keep the size they have on the card whatever the camera
    private val cardRy = minOf(rx * 0.37f, height * 0.36f)
    val ry =
        run {
            val elevation =
                (CARD_ELEVATION_DEGREES * (if (width > 0f) height / width else 0f) / CARD_ASPECT)
                    .coerceIn(CARD_ELEVATION_DEGREES, MAX_ELEVATION_DEGREES)
            minOf(cardRy * (sin(elevation * DEG_TO_RAD) / sin(CARD_ELEVATION_DEGREES * DEG_TO_RAD)).toFloat(), height * 0.36f)
        }
    val sunRadius = cardRy * 0.5f
    val earthRadius = cardRy * 0.17f

    fun at(
        longitude: Float,
        scale: Float = 1f,
    ): Offset {
        val t = (longitude - earthLongitude) * DEG_TO_RAD
        return Offset(cx + rx * scale * sin(t).toFloat(), cy + ry * scale * cos(t).toFloat())
    }

    fun isBack(longitude: Float) = cos((longitude - earthLongitude) * DEG_TO_RAD) < 0

    /** 1 at the front of the orbit (by the Earth), 0 at its back. */
    fun depth(longitude: Float) = ((1 + cos((longitude - earthLongitude) * DEG_TO_RAD)) / 2).toFloat()
}

@Composable
private fun SolarSystemScene(
    state: HomeWidgetsState,
    year: Int,
    detailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val now = rememberMinute()
    val millis = state.skyTimeMillis ?: now.time
    val zone = state.location.timeZone
    val inIsrael = state.userInIsrael
    val events = rememberOffMain(year, inIsrael, tag = "solar.events") { computeHebrewYearEvents(year, inIsrael) }.orEmpty()
    val placed = rememberOffMain(events, zone, tag = "solar.placed") { events.map { Triple(it, earthLongitudeOn(it.start, zone), earthLongitudeOn(it.end, zone)) } }.orEmpty()
    val earthLongitude = earthLongitudeAt(millis)
    // The holiday the Earth is on, named on the card too (desktop currentEvent)
    val day = state.shownDate
    val current =
        remember(placed, day) {
            placed.firstOrNull { (e, _, _) -> e.category != SolarEventCategory.RoshChodesh && !day.isBefore(e.start) && !day.isAfter(e.end) }?.first
        }
    val phase = rememberOffMain(millis / 3_600_000L, zone, tag = "solar.phase") { hebrewDayAt(millis, zone).phaseAngleAt(millis) } ?: return
    // Room for a year's labelled events in both weights: the full page names them all on every frame while it plays,
    // and the default 8 layouts would make each of them a new layout every frame
    val measurer = rememberTextMeasurer(cacheSize = LABEL_LAYOUT_CACHE)
    // A tapped event, named for a few seconds (desktop names them on hover)
    var named by remember { mutableStateOf<SolarEvent?>(null) }
    LaunchedEffect(named) {
        if (named != null) {
            delay(3_000)
            named = null
        }
    }
    Canvas(
        modifier.pointerInput(placed, earthLongitude) {
            detectTapGestures { tap ->
                val g = SolarGeometry(size.width.toFloat(), size.height.toFloat(), earthLongitude)
                named = placed.minByOrNull { (_, start, _) -> (g.at(start) - tap).getDistance() }?.takeIf { (_, start, _) -> (g.at(start) - tap).getDistance() < 28.dp.toPx() }?.first
            }
        },
    ) {
        val g = SolarGeometry(size.width, size.height, earthLongitude)
        drawStarsBackdrop()
        drawOrbitHalf(g, back = true)
        placed.filter { g.isBack(it.second) }.forEach { drawEvent(g, it) }
        // The Sun
        val sunR = g.sunRadius
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFC94A), Color.Transparent), Offset(g.cx, g.cy), sunR * 2.4f), sunR * 2.4f, Offset(g.cx, g.cy))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1B8), Color(0xFFFFC233), Color(0xFFE89A12)), Offset(g.cx, g.cy), sunR), sunR, Offset(g.cx, g.cy))
        drawOrbitHalf(g, back = false)
        placed.filterNot { g.isBack(it.second) }.forEach { drawEvent(g, it) }
        // The Earth where it is, lit from the Sun, and the Moon around it at its age
        val earth = g.at(earthLongitude)
        val earthR = g.earthRadius.coerceAtLeast(4.dp.toPx())
        drawLitSphere(earth, earthR, sunAngle = -90f, front = 0f, lit = Color(0xFF4F8FD6), dark = Color(0xFF15233A))
        val moonDistance = earthR * 2.2f
        val p = phase * DEG_TO_RAD
        val moon = Offset(earth.x - moonDistance * sin(p).toFloat(), earth.y - moonDistance * 0.45f * cos(p).toFloat())
        drawCircle(Color(0xFFCFCBC2), earthR * 0.32f, moon)
        drawEventLabels(g, placed, named, current, detailed, earth, moonDistance, measurer)
    }
}

private fun DrawScope.drawStarsBackdrop() {
    // A few fixed stars, as desktop's black sky behind the scene
    val rnd = java.util.Random(7)
    repeat(60) {
        drawCircle(Color.White.copy(alpha = 0.15f + rnd.nextFloat() * 0.35f), 0.6.dp.toPx() + rnd.nextFloat(), Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
    }
}

private fun DrawScope.drawOrbitHalf(
    g: SolarGeometry,
    back: Boolean,
) {
    val path = Path()
    var started = false
    for (i in 0..240) {
        val l = i * 1.5f
        val p = g.at(l)
        if (g.isBack(l) == back) {
            if (!started) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            started = true
        } else {
            started = false
        }
    }
    drawPath(path, Color.White.copy(alpha = if (back) 0.25f else 0.45f), style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawEvent(
    g: SolarGeometry,
    placed: Triple<SolarEvent, Float, Float>,
) {
    val (event, start, endRaw) = placed
    val color = Color(0xFF000000 or event.category.colorRgb.toLong())
    if (event.category == SolarEventCategory.RoshChodesh) {
        val a = g.at(start, 1f - TICK_HALF_LENGTH)
        val b = g.at(start, 1f + TICK_HALF_LENGTH)
        drawLine(color.copy(alpha = 0.8f), a, b, strokeWidth = TICK_WIDTH_DP.dp.toPx())
    } else {
        // Its days as a short arc (half a day's travel either side), never shorter than a dot
        val end = if (endRaw < start) endRaw + 360f else endRaw
        val path = Path()
        var l = start - HALF_DAY_DEGREES
        path.moveTo(g.at(l).x, g.at(l).y)
        while (l < end + HALF_DAY_DEGREES) {
            l += 0.5f
            val p = g.at(l)
            path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(width = ARC_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Half a Rosh Chodesh tick, as a fraction of the orbit's radius: it crosses the orbit from 0.97 to 1.03. */
private const val TICK_HALF_LENGTH = 0.03f
private const val TICK_WIDTH_DP = 1.5f
private const val ARC_WIDTH_DP = 4f

/** The Earth moves ~0.99° a day: a holiday's arc runs half a day's travel past its first and last noon (desktop HALF_DAY_DEGREES). */
private const val HALF_DAY_DEGREES = 0.49f

/**
 * Where the markers are drawn ([drawEvent]), every event's, as the names must leave them: a name on its own tick read
 * as struck through ("תמוז"), one on its dot or arc hid the day it names ("שבעה עשר בתמוז"). Each arc by its points,
 * a degree apart, so a long one (Sukkot, Pesach) bars only its own line and not the box around its curve.
 */
private fun DrawScope.markerAreas(
    g: SolarGeometry,
    placed: List<Triple<SolarEvent, Float, Float>>,
): List<Rect> {
    val margin = 1.dp.toPx()
    val out = ArrayList<Rect>()
    placed.forEach { (event, start, endRaw) ->
        if (event.category == SolarEventCategory.RoshChodesh) {
            val a = g.at(start, 1f - TICK_HALF_LENGTH)
            val b = g.at(start, 1f + TICK_HALF_LENGTH)
            val half = TICK_WIDTH_DP.dp.toPx() / 2 + margin
            out += Rect(minOf(a.x, b.x) - half, minOf(a.y, b.y) - half, maxOf(a.x, b.x) + half, maxOf(a.y, b.y) + half)
        } else {
            val end = if (endRaw < start) endRaw + 360f else endRaw
            val radius = ARC_WIDTH_DP.dp.toPx() / 2 + margin
            var l = start - HALF_DAY_DEGREES
            while (true) {
                out += Rect(g.at(l), radius)
                if (l >= end + HALF_DAY_DEGREES) break
                l = minOf(l + 1f, end + HALF_DAY_DEGREES)
            }
        }
    }
    return out
}

/** Desktop's card width (SolarSystemWidgetView CARD_WIDTH_DP): the labels are sized for it, a larger view scales them. */
private const val CARD_WIDTH_DP = 429f

/**
 * How far past its 4dp clearance a name may sit from its marker, in its own heights. Desktop steps a name out 6 times
 * by most of its height, room it has on its 429dp card; on the Jelly Star's 218 × 135dp page that sent "יום כיפור"
 * and "צום גדליה" into the orbit's middle and "עשרה בטבת" beside "פורים", 60dp from their dots and read as others'.
 * Further than a line from its marker a name is not next to it any more: it is dropped instead (a tap names it).
 */
private const val NEAR_LABEL_HEIGHTS = 1f

/** The tapped name and the Earth's holiday may go a little further: asked for or in focus, better apart than unnamed. */
private const val FOCUS_LABEL_HEIGHTS = 2.5f

/** A step out, in label heights: fine enough that the nearest free spot is found within the short reach. */
private const val LABEL_STEP_HEIGHTS = 0.4f

/** Steps tried at most: past the Earth's clearance on a tablet the reach is long. */
private const val MAX_LABEL_STEPS = 24

/**
 * The size a name with no room at its own tries next: desktop's are sized for its 429dp card, and a phone's page,
 * narrower, has the year's names crowding it.
 */
private const val LABEL_SMALLER = 0.85f

/**
 * The short form a name with no room at either size tries last, as luachs write them: on a narrow page a short name
 * beside its dot reads better than none. Keyed by KosherKotlin's names (computeHebrewYearEvents); "תשעה באב" is not
 * "ט׳ באב", which KosherKotlin already calls ט״ו באב.
 */
private val SHORT_EVENT_NAMES =
    mapOf(
        "ראש השנה" to "ר״ה",
        "יום כיפור" to "יוה״כ",
        "שמיני עצרת" to "שמ״ע",
        "שמחת תורה" to "שמח״ת",
        "עשרה בטבת" to "י׳ בטבת",
        "ל״ג בעומר" to "ל״ג",
        "שבעה עשר בתמוז" to "י״ז בתמוז",
        "תשעה באב" to "ת״ב",
    )

/** Text layouts the scene keeps: a year's events (about 45, the months with them) in both weights and sizes, and short forms. */
private const val LABEL_LAYOUT_CACHE = 128

/** How far [p] is from the nearest point of this rectangle (0 inside it). */
private fun Rect.distanceTo(p: Offset): Float {
    val dx = maxOf(left - p.x, 0f, p.x - right)
    val dy = maxOf(top - p.y, 0f, p.y - bottom)
    return sqrt(dx * dx + dy * dy)
}

/**
 * The events' names, as desktop's SolarEventMarkers places them: the full page names every event (the months too, by
 * their Rosh Chodesh), the card the landmarks, the one tapped and the one the Earth is on. In priority order (the
 * Earth's, the tapped one, Yamim Tovim, then the landmarks, the other days, the months last), each beside the middle of
 * its stretch (a month past the outer end of its tick), stepped out radially (or up, or down, or in toward the Sun)
 * until it clears the Earth, every marker and the names already placed, but never further than a line from its marker
 * ([NEAR_LABEL_HEIGHTS]); one with no room there tries a smaller size, then its short form, then stays unnamed rather
 * than away from its marker or on another. Desktop has no leader lines, so neither does this.
 */
private fun DrawScope.drawEventLabels(
    g: SolarGeometry,
    placed: List<Triple<SolarEvent, Float, Float>>,
    named: SolarEvent?,
    current: SolarEvent?,
    detailed: Boolean,
    earth: Offset,
    earthClearance: Float,
    measurer: TextMeasurer,
) {
    val labeled =
        placed
            .filter { (e, _, _) -> detailed || e.pinned || e == named || e == current }
            .sortedBy { (e, _, _) ->
                // Desktop's order (the Earth's, the tapped one, Yamim Tovim, the rest by date), the rest split so that
                // on a page too small for all of them the landmarks win their spots, and the months give theirs up first
                when {
                    e == current -> 0
                    e == named -> 1
                    e.category == SolarEventCategory.YomTov -> 2
                    e.pinned -> 3
                    e.category != SolarEventCategory.RoshChodesh -> 4
                    else -> 5
                }
            }
    val textScale = sqrt(size.width.toDp().value / CARD_WIDTH_DP).coerceIn(1f, 1.4f)
    val clearance = 4.dp.toPx()
    // Inside the card's border: a name slid against the canvas edge was cut by it ("שבעה עשר בתמוז" lost its end)
    val bounds = Rect(6.dp.toPx(), 4.dp.toPx(), size.width - 6.dp.toPx(), size.height - 4.dp.toPx())
    // Between two names: touching, "אלול" and "ראש השנה" read as one
    val spacingX = 4.dp.toPx()
    val spacingY = 1.dp.toPx()
    val markers = markerAreas(g, placed)
    // The Sun too, beside the Earth: a name stepping inward reaches it on a narrow page
    val taken = mutableListOf(Rect(earth, earthClearance), Rect(Offset(g.cx, g.cy), g.sunRadius))
    labeled.forEach { (event, start, endRaw) ->
        val middle = start + ((endRaw - start + 360f) % 360f) / 2f
        val p = if (event.category == SolarEventCategory.RoshChodesh) g.at(start, 1f + TICK_HALF_LENGTH) else g.at(middle)
        val emphasized = event == named
        val color = Color(0xFF000000 or event.category.colorRgb.toLong())
        val dx = p.x - g.cx
        val dy = p.y - g.cy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        // Desktop's three, then inward: at the sides of a phone's narrow page the room left is inside the orbit
        val directions = listOf(dx / len to dy / len, 0f to -1f, 0f to 1f, -dx / len to -dy / len)
        // A marker under the Earth (its holiday, a Rosh Chodesh beside it) is named just past the Earth, as desktop does
        val pastEarth = if ((p - earth).getDistance() < earthClearance) 2 * earthClearance else 0f
        val nearHeights = if (event == named || event == current) FOCUS_LABEL_HEIGHTS else NEAR_LABEL_HEIGHTS
        val attempts =
            listOfNotNull(
                event.name to 1f,
                event.name to LABEL_SMALLER,
                SHORT_EVENT_NAMES[event.name]?.let { it to LABEL_SMALLER },
            )
        for ((text, scale) in attempts) {
            val style =
                TextStyle(
                    color = color,
                    fontSize = (if (emphasized) 12f else 10f).sp * (textScale * scale),
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                )
            val layout = measurer.measure(text, style)
            val halfW = layout.size.width / 2f
            val halfH = layout.size.height / 2f
            if (2 * halfW > bounds.width) continue

            fun rectAt(
                ux: Float,
                uy: Float,
                gap: Float,
            ): Rect {
                // Slid along its line to stay in: the markers then keep it off its own dot if that brings it over it
                val x = (p.x + ux * (halfW + gap)).coerceIn(bounds.left + halfW, bounds.right - halfW)
                val y = p.y + uy * (halfH + gap)
                return Rect(x - halfW, y - halfH, x + halfW, y + halfH)
            }
            // Nearest first: every direction at one gap before the next gap, the gaps up to the reach and no further
            val reach = clearance + layout.size.height * nearHeights + pastEarth
            val step = (layout.size.height * LABEL_STEP_HEIGHTS).coerceAtLeast(1f)
            val rect =
                generateSequence(clearance) { it + step }
                    .takeWhile { it <= reach }
                    .take(MAX_LABEL_STEPS)
                    .flatMap { gap -> directions.asSequence().map { (ux, uy) -> rectAt(ux, uy, gap) } }
                    .firstOrNull { candidate ->
                        val spaced = Rect(candidate.left - spacingX, candidate.top - spacingY, candidate.right + spacingX, candidate.bottom + spacingY)
                        candidate.top >= bounds.top &&
                            candidate.bottom <= bounds.bottom &&
                            // Slid back inside the card, a name can end up further than its gap: it is measured as drawn
                            candidate.distanceTo(p) <= reach &&
                            markers.none { it.overlaps(candidate) } &&
                            taken.none { it.overlaps(spaced) }
                    } ?: continue
            taken += rect
            // The far side fades like the orbit line
            drawText(layout, topLeft = rect.topLeft, alpha = if (emphasized) 1f else 0.45f + 0.55f * g.depth(middle))
            return@forEach
        }
    }
}

/** The play speed, the full page's Earth and sky (desktop SolarSystemOptionsPage). */
@Composable
internal fun SolarSystemOptionsPage(state: HomeWidgetsState) {
    val c = Zayit.colors
    val settings = SolarSystemSettings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("מהירות ההפעלה", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        val current = state[settings.daysPerSecond]
        // A speed saved that's no longer offered shows the nearest
        val selected = PLAY_SPEEDS.indices.minBy { abs(PLAY_SPEEDS[it] - current) }
        SpeedPicker(selected) { state[settings.daysPerSecond] = PLAY_SPEEDS[it] }
        OptionCheckRow("סיבוב כדור הארץ יחסי למהירות", null, state[settings.proportionalSpin]) { state[settings.proportionalSpin] = it }
        Text(
            "סיבוב אחד בכל יום מדומה, כמו במציאות. בלי זה, כדור הארץ של מעגל השנה מסתובב בקצב קבוע של סיבוב בכל ⁦2⁩ שניות; כדור הארץ והשמיים בפינה תמיד מראים את הרגע המדויק.",
            fontSize = 12.sp,
            color = c.textSecondary,
        )
        Text("בחלון המלא", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(top = 10.dp))
        OptionCheckRow("הצג את כדור הארץ", null, state[settings.showEarth]) { state[settings.showEarth] = it }
        OptionCheckRow("הצג את השמיים", null, state[settings.showSky]) { state[settings.showSky] = it }
    }
}

/** "יום בשנייה", "¼ יום בשנייה", "6 ימים בשנייה"… */
private fun speedLabel(days: Float): String =
    when (days) {
        0.25f -> "¼ יום בשנייה"
        0.5f -> "½ יום בשנייה"
        1f -> "יום בשנייה"
        else -> "${days.toInt()} ימים בשנייה"
    }

@Composable
private fun SpeedPicker(
    selected: Int,
    onPick: (Int) -> Unit,
) {
    val c = Zayit.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape).background(c.panel).border(1.dp, c.panelBorder, shape).clickable(role = Role.DropdownList) { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(speedLabel(PLAY_SPEEDS[selected]), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        }
        DropdownMenu(open, { open = false }, containerColor = c.panel) {
            PLAY_SPEEDS.forEachIndexed { i, speed ->
                DropdownMenuItem(text = { Text(speedLabel(speed), color = c.text, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal) }, onClick = {
                    open = false
                    onPick(i)
                })
            }
        }
    }
}

/**
 * The full page (desktop SolarSystemWindow): the year large, play and pause at the speed of the options, the date
 * picker, and the Earth and the sky of the moment (a row under the scene on a phone, beside it on a tablet).
 */
@Composable
internal fun SolarSystemPage(
    state: HomeWidgetsState,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val speed = state[SolarSystemSettings.daysPerSecond]
    val showEarth = state[SolarSystemSettings.showEarth]
    val showSky = state[SolarSystemSettings.showSky]
    LaunchedEffect(state.playing) {
        if (state.playing) state.runPlayClock { state.peek(SolarSystemSettings.daysPerSecond) }
    }
    // Leaving the page stops the play where it is
    DisposableEffect(state) { onDispose { state.stopPlay() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.accent).clickable(role = Role.Button, onClick = state::togglePlay).semantics { contentDescription = if (state.playing) "השהיה" else "הפעלה" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, tint = Color.White)
            }
            Text(speedLabel(PLAY_SPEEDS.minBy { abs(it - speed) }), color = c.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            LuachDateButton(
                label = hebrewDateOf(state.shownDate, state.inIsrael),
                selected = state.shownDate,
                inIsrael = state.inIsrael,
                onSelect = state::selectDate,
                sheet = !wide,
                today = state.todayDate,
                onToday = state::backToToday,
            )
        }
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SolarSystemCard(state, Modifier.weight(1f).fillMaxHeight(), onFullscreen = null, detailed = true)
                if (showEarth || showSky) {
                    Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showEarth) EarthView(state, Modifier.fillMaxWidth().weight(1f), showDate = false)
                        if (showSky) SkyWidget.Content(state, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SolarSystemCard(state, Modifier.fillMaxWidth().weight(1f), onFullscreen = null, detailed = true)
                    if (showEarth || showSky) {
                        Row(Modifier.fillMaxWidth().aspectRatio(if (showEarth && showSky) 2f else 1.6f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showEarth) EarthView(state, Modifier.weight(1f).fillMaxHeight(), showDate = false)
                            if (showSky) SkyWidget.Content(state, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}
