package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.data.hebrewDateOf
import io.github.kdroidfilter.seforimapp.android.data.hebrewFormatter
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.tools.CityPicker
import io.github.kdroidfilter.seforimapp.android.ui.tools.isPhoneWindow
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Desktop 0.6.1 features/home/widgets/earth/EarthWidget.kt and earthwidget EarthWidgetZmanimView.kt, in 2D: desktop
// renders the globe and the Moon with Filament (3D models and materials that have no Android build here). Android
// draws the same scene flat: the globe lit by the Sun at the shared moment (an orthographic view of the world map,
// computed off the UI thread), the Moon's orbit with the Hebrew days of the month (a tap on one picks that day), the
// Moon where desktop puts it, and the Kiddush Levana arc of the user's community; the date picker and the place menu
// on top, the Kiddush Levana legend below, as desktop's.

private const val EARTHWIDGET_KIDDUSH_LEVANA_LEGEND = "קידוש לבנה"

/** The globe at the selected moment; its orbit labels pick the date, its city list the location. */
internal object EarthWidget : HomeWidget {
    override val id = "earth"
    override val title = "כדור הארץ"
    override val defaultSpan = CellSpan(7, 4)
    override val minSpan = CellSpan(5, 4)
    override val maxSpan = CellSpan(12, 8)
    override val isSupported get() = !PlatformInfo.isLowEnd

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = EarthView(state, modifier, showControls = true)

    @Composable
    override fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = EarthView(state, modifier, showControls = false)
}

@Composable
internal fun EarthView(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    compactControls: Boolean = false,
    /** False under a page that has its own date picker (the year cycle's, as desktop's SolarSystemWindow). */
    showDate: Boolean = true,
) {
    val now = rememberMinute()
    val millis = state.skyTimeMillis ?: now.time
    val location = state.location
    val zone = location.timeZone
    val texture = rememberEarthTexture()
    var showLevana by rememberSaveable { mutableStateOf(true) }
    // Off the UI thread like the other cards: the first JewishCalendar also loads the zmanim library, and on the main
    // thread these held the Home's first frames
    val hebrewDay = rememberOffMain(millis / 3_600_000L, zone, tag = "earth.day") { hebrewDayAt(millis, zone) } ?: return WidgetCardLoading(modifier)
    val levana =
        rememberOffMain(millis / 3_600_000L, zone, state.kiddushLevanaEarliest, state.kiddushLevanaLatest, tag = "earth.kiddushLevana") {
            runCatching { computeKiddushLevanaArc(Date(millis), zone, state.kiddushLevanaEarliest, state.kiddushLevanaLatest) }.getOrNull()
        }
    val labels = remember(hebrewDay.daysInMonth) { (1..hebrewDay.daysInMonth).map { it to hebrewFormatter.formatHebrewNumber(it) } }
    val accent = Zayit.colors.accent
    WidgetCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val sizePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
            val globePx = (sizePx * GLOBE_FRACTION).roundToInt().coerceIn(64, 360)
            // A new picture every ten minutes of the moment shown (an hour while playing): the light moves slowly
            val step = if (state.playing) 3_600_000L else 600_000L
            val globe =
                rememberOffMain(texture, location.latitude, location.longitude, millis / step, globePx, tag = "earth.globe") {
                    texture?.let { renderGlobe(it, location.latitude, location.longitude, Date(millis), globePx) }
                }
            val measurer = rememberTextMeasurer()
            val moonDegrees = moonOrbitDegrees(hebrewDay, millis)
            val phase = hebrewDay.phaseAngleAt(millis)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(hebrewDay.firstOfMonth, hebrewDay.dayOfMonth, labels) {
                        // A day's label picks that day of the month shown: one that can be seen. The labels behind the
                        // globe are drawn under it, and were still picked: day 1 sits at the top of the orbit's back
                        // (ORBIT_DAY_LABEL_START_DEGREES), under the globe's upper middle by the user's red dot, so a tap
                        // on the globe there changed the card's date to א׳ of the month without any label in sight.
                        detectTapGestures { tap ->
                            val geometry = OrbitGeometry(size.width.toFloat(), size.height.toFloat(), ORBIT_LABEL_ROOM.toPx())
                            val stride = geometry.labelStride(labels.size, ORBIT_LABEL_PITCH.toPx())
                            val nearest =
                                labels
                                    .filter { (day, _) ->
                                        val degrees = dayDegrees(day, labels.size)
                                        dayLabelShown(day, labels.size, hebrewDay.dayOfMonth, stride) && !geometry.labelUnderGlobe(degrees)
                                    }
                                    .minByOrNull { (day, _) -> (geometry.labelAt(dayDegrees(day, labels.size)) - tap).getDistance() }
                            if (nearest != null && (geometry.labelAt(dayDegrees(nearest.first, labels.size)) - tap).getDistance() < 24.dp.toPx()) {
                                state.selectDate(hebrewDay.firstOfMonth.plusDays((nearest.first - 1).toLong()))
                            }
                        }
                    },
            ) {
                val geometry = OrbitGeometry(size.width, size.height, ORBIT_LABEL_ROOM.toPx())
                // The orbit behind the globe, then the globe, then the orbit before it
                drawOrbit(geometry, back = true, labels, hebrewDay.dayOfMonth, levana.takeIf { showLevana }, accent, measurer)
                if (geometry.isBack(moonDegrees)) drawMoon(geometry, moonDegrees, phase)
                val r = geometry.globeRadius
                if (globe != null) {
                    drawImage(globe, dstOffset = androidx.compose.ui.unit.IntOffset((geometry.cx - r).roundToInt(), (geometry.cy - r).roundToInt()), dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()))
                } else {
                    drawCircle(Color(0xFF0E2A47), r, Offset(geometry.cx, geometry.cy))
                }
                // The user's place, at the centre of the view
                drawCircle(Color(0xFFE53935), 3.dp.toPx(), Offset(geometry.cx, geometry.cy - r * markerLift(location.latitude)))
                drawOrbit(geometry, back = false, labels, hebrewDay.dayOfMonth, levana.takeIf { showLevana }, accent, measurer)
                if (!geometry.isBack(moonDegrees)) drawMoon(geometry, moonDegrees, phase)
            }
            // A view too small for them (the year cycle's panel on a narrow phone) keeps the globe clear
            if (showControls && maxWidth >= MIN_CONTROLS_WIDTH) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showDate) {
                        LuachDateButton(
                            label = hebrewDateOf(state.shownDate, state.inIsrael),
                            selected = state.shownDate,
                            inIsrael = state.inIsrael,
                            onSelect = state::selectDate,
                            sheet = isPhoneWindow(),
                            today = state.todayDate,
                            onToday = state::backToToday,
                            dark = true,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.weight(0.01f))
                    if (!compactControls) CityPicker(state.city, onSelect = state::selectLocation, dark = true)
                }
                // The Kiddush Levana legend, a toggle of its arc
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                        .clickable(role = Role.Checkbox) { showLevana = !showLevana }
                        .heightIn(min = 30.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (showLevana) accent else Color.Gray))
                    Text(EARTHWIDGET_KIDDUSH_LEVANA_LEGEND, color = Color.White.copy(alpha = if (showLevana) 0.9f else 0.6f), fontSize = 11.sp)
                }
            }
        }
    }
}

/** The globe's diameter, of the view's smaller side (desktop earthSizeFraction 0.6). */
private const val GLOBE_FRACTION = 0.56f

/** How high on the globe the user's place shows (the view is tipped to show more of its hemisphere). */
private fun markerLift(latitude: Double): Float = sin(VIEW_TIP * DEG_TO_RAD).toFloat() * if (latitude >= 0) 1f else -1f

/** Degrees the view is tipped from the user's place towards the equator. */
private const val VIEW_TIP = 18.0

private fun dayDegrees(
    day: Int,
    daysInMonth: Int,
) = ORBIT_DAY_LABEL_START_DEGREES + (day - 1) * 360f / daysInMonth

/** The day labels' distance from the globe, in orbits. */
private const val LABEL_SCALE = 1.13f

/** Half the widest day label (כ״ד at 9 sp) and a margin: the orbit leaves it between its outer labels and the card's edges. */
private val ORBIT_LABEL_ROOM = 16.dp

/** The room a day label takes along the orbit: an orbit too short for one a day names every few days. */
private val ORBIT_LABEL_PITCH = 14.dp

/** The narrowest view that shows the date and place pickers and the Kiddush Levana toggle over the globe. */
private val MIN_CONTROLS_WIDTH = 160.dp

/**
 * The Moon's orbit around the globe, seen from a little above its plane: a flat ellipse, its back half behind the globe.
 * [labelRoom] is kept free at either end, so the outer labels stay inside the view (they were cut by the card's edge).
 */
private class OrbitGeometry(
    width: Float,
    height: Float,
    labelRoom: Float,
) {
    val cx = width / 2
    val cy = height / 2 + height * 0.04f
    private val half = minOf(width, height) / 2
    val globeRadius = half * GLOBE_FRACTION
    val rx = minOf((width / 2 - labelRoom).coerceAtLeast(0f) / LABEL_SCALE, width / 2 * 0.86f, half * 1.45f)
    val ry = rx * 0.26f

    fun at(
        degrees: Float,
        scale: Float = 1f,
    ): Offset {
        val a = degrees * DEG_TO_RAD
        return Offset(cx + rx * scale * cos(a).toFloat(), cy - ry * scale * sin(a).toFloat())
    }

    fun labelAt(degrees: Float) = at(degrees, LABEL_SCALE)

    fun isBack(degrees: Float) = sin(degrees * DEG_TO_RAD) > 0

    /** A back label whose centre the globe covers: drawn before the globe, so hidden by it, and no tap target. */
    fun labelUnderGlobe(degrees: Float) = isBack(degrees) && (labelAt(degrees) - Offset(cx, cy)).getDistance() < globeRadius

    /** 1 when [count] labels [pitch] apart fit around the labels' ellipse (Ramanujan's perimeter), else every how many days. */
    fun labelStride(
        count: Int,
        pitch: Float,
    ): Int {
        val a = rx * LABEL_SCALE
        val b = ry * LABEL_SCALE
        val perimeter = PI * (3 * (a + b) - sqrt((3 * a + b) * (a + 3 * b)))
        if (perimeter <= 0.0) return count.coerceAtLeast(1)
        return ceil(count * pitch / perimeter).toInt().coerceAtLeast(1)
    }
}

/**
 * Whether [day]'s label shows with every [stride]th day named: today always, the others not beside it nor beside the
 * first day across the month's end.
 */
private fun dayLabelShown(
    day: Int,
    count: Int,
    today: Int,
    stride: Int,
): Boolean {
    if (stride <= 1 || day == today) return true
    val apart = abs(day - today).let { minOf(it, count - it) }
    return (day - 1) % stride == 0 && count - day + 1 >= stride && apart >= stride
}

private fun DrawScope.drawOrbit(
    g: OrbitGeometry,
    back: Boolean,
    labels: List<Pair<Int, String>>,
    today: Int,
    levana: KiddushLevanaArc?,
    accent: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val steps = 180
    val path = Path()
    var started = false
    for (i in 0..steps) {
        val d = i * 360f / steps
        val p = g.at(d)
        if (g.isBack(d) == back) {
            if (!started) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            started = true
        } else {
            started = false
        }
    }
    drawPath(path, Color(0xFFE8C46A).copy(alpha = if (back) 0.45f else 0.85f), style = Stroke(width = 1.5.dp.toPx()))
    if (levana != null) {
        val arc = Path()
        var from = levana.startDegrees
        val to = if (levana.endDegrees < from) levana.endDegrees + 360f else levana.endDegrees
        var first = true
        while (from <= to) {
            if (g.isBack(from) == back) {
                val p = g.at(from)
                if (first) arc.moveTo(p.x, p.y) else arc.lineTo(p.x, p.y)
                first = false
            } else {
                first = true
            }
            from += 1f
        }
        drawPath(arc, accent.copy(alpha = if (levana.isCurrentlyAllowed) 1f else 0.7f), style = Stroke(width = 4.dp.toPx()))
    }
    val stride = g.labelStride(labels.size, ORBIT_LABEL_PITCH.toPx())
    labels.forEach { (day, text) ->
        val d = dayDegrees(day, labels.size)
        if (g.isBack(d) != back || !dayLabelShown(day, labels.size, today, stride)) return@forEach
        val p = g.labelAt(d)
        val style = TextStyle(color = if (day == today) accent else Color.White.copy(alpha = if (back) 0.55f else 0.9f), fontSize = 9.sp, fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal)
        val layout = measurer.measure(text, style)
        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawMoon(
    g: OrbitGeometry,
    degrees: Float,
    phaseDegrees: Float,
) {
    val p = g.at(degrees)
    val r = g.globeRadius * 0.16f
    // Full at 180° of age, new at 0°; waxing lit on the right, waning on the left (as seen from the north)
    val sunAngle = if (phaseDegrees < 180f) 0f else 180f
    drawLitSphere(p, r, sunAngle, front = -cos(phaseDegrees * DEG_TO_RAD).toFloat(), lit = Color(0xFFE9E6DF), dark = Color(0xFF2C2C30))
}

/**
 * A sphere of radius [r] lit from the screen direction [sunAngle] (degrees, 0 to the right), [front] the cosine of
 * the angle between the light and the viewer (1 lit full face, -1 dark): the terminator as a half ellipse.
 */
internal fun DrawScope.drawLitSphere(
    center: Offset,
    r: Float,
    sunAngle: Float,
    front: Float,
    lit: Color,
    dark: Color,
) {
    drawCircle(dark, r, center)
    val u = front.coerceIn(-1f, 1f)
    val path = Path()
    path.moveTo(center.x, center.y - r)
    path.arcTo(Rect(center.x - r, center.y - r, center.x + r, center.y + r), -90f, 180f, false)
    val e = r * abs(u)
    path.arcTo(Rect(center.x - e, center.y - r, center.x + e, center.y + r), 90f, if (u >= 0) 180f else -180f, false)
    path.close()
    rotate(sunAngle, center) { drawPath(path, lit) }
}

// ---- The globe, rendered off the UI thread --------------------------------------------------------------------------

private class EarthTexture(val width: Int, val height: Int, val pixels: IntArray)

@Volatile
private var earthTexture: EarthTexture? = null

@Composable
private fun rememberEarthTexture(): EarthTexture? {
    val context = LocalContext.current
    return rememberOffMain(Unit, tag = "earth.texture") { loadEarthTexture(context) }
}

private fun loadEarthTexture(context: Context): EarthTexture? {
    earthTexture?.let { return it }
    val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.earthmap, BitmapFactory.Options().apply { inScaled = false }) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return EarthTexture(bitmap.width, bitmap.height, pixels).also {
        bitmap.recycle()
        earthTexture = it
    }
}

/**
 * The world map seen from above [latitude], [longitude] (tipped towards the equator), lit by the Sun at [time]: day,
 * a soft twilight band, and a dim night side.
 */
private fun renderGlobe(
    texture: EarthTexture,
    latitude: Double,
    longitude: Double,
    time: Date,
    size: Int,
): ImageBitmap {
    val (sunDec, sunLon) = subsolarPoint(time)
    val sd = sunDec * DEG_TO_RAD
    val sinSd = sin(sd)
    val cosSd = cos(sd)
    val phi0 = ((latitude - if (latitude >= 0) VIEW_TIP else -VIEW_TIP).coerceIn(-80.0, 80.0)) * DEG_TO_RAD
    val sinPhi0 = sin(phi0)
    val cosPhi0 = cos(phi0)
    val lambda0 = longitude * DEG_TO_RAD
    val out = IntArray(size * size)
    val radius = size / 2.0
    for (py in 0 until size) {
        val y = (radius - py - 0.5) / radius
        for (px in 0 until size) {
            val x = (px + 0.5 - radius) / radius
            val rho = hypot(x, y)
            if (rho > 1.0) continue
            // Inverse orthographic projection
            val c = asin(rho.coerceAtMost(1.0))
            val sinC = sin(c)
            val cosC = cos(c)
            val phi = if (rho < 1e-9) phi0 else asin((cosC * sinPhi0 + y * sinC * cosPhi0 / rho).coerceIn(-1.0, 1.0))
            val lambda = lambda0 + atan2(x * sinC, rho * cosC * cosPhi0 - y * sinC * sinPhi0)
            var lonDeg = Math.toDegrees(lambda)
            lonDeg = ((lonDeg + 540.0) % 360.0) - 180.0
            val latDeg = Math.toDegrees(phi)
            val tx = (((lonDeg + 180.0) / 360.0) * texture.width).toInt().coerceIn(0, texture.width - 1)
            val ty = (((90.0 - latDeg) / 180.0) * texture.height).toInt().coerceIn(0, texture.height - 1)
            val base = texture.pixels[ty * texture.width + tx]
            // The Sun's height over this point: its cosine of zenith angle
            val cosZ = sin(phi) * sinSd + cos(phi) * cosSd * cos(lambda - sunLon * DEG_TO_RAD)
            val day = ((cosZ + 0.1) / 0.2).coerceIn(0.0, 1.0)
            // A rim of air, darker towards the edge
            val limb = 0.75 + 0.25 * sqrt(1.0 - rho * rho)
            val light = (0.12 + 0.88 * day) * limb
            val r = (((base shr 16) and 0xFF) * light).toInt().coerceIn(0, 255)
            val gg = (((base shr 8) and 0xFF) * light).toInt().coerceIn(0, 255)
            // Night keeps a faint blue
            val b = ((base and 0xFF) * light + (1 - day) * 18).toInt().coerceIn(0, 255)
            out[py * size + px] = (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
        }
    }
    return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
