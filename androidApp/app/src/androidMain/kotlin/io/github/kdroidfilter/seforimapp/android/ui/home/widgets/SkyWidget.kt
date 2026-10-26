package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

// Desktop 0.6.10 features/home/widgets/sky/SkyWidget.kt and earthwidget SkyWidgetView.kt, in 2D: desktop shades the air
// with a scattering shader (Skia SkSL) over a Filament scene. Android keeps desktop's camera and what it shows (the
// same aim: the Sun while any of its light is left, the Moon in full night if it is up, else the south; the stars of
// the HYG catalogue turning with the night; the day's path of the Sun with its rise, highest point and set), on a sky
// whose gradient follows the Sun's height.

private const val SKY_VFOV_DEG = 72f
private const val DEFAULT_PITCH_DEG = 12f
private const val MOON_FOCUS_FOV_DEG = 24f
private const val BODY_MAGNIFICATION = 18f
private const val SUN_ANGULAR_RADIUS_DEG = 0.2666f * BODY_MAGNIFICATION
private const val MOON_ANGULAR_RADIUS_DEG = 0.2590f * BODY_MAGNIFICATION
private const val CARD_FAINTEST_MAGNITUDE = 3.5f
private const val PASS_STEP_MS = 5 * 60_000L
private const val PASS_REACH_MS = 26 * 3_600_000L
private const val HOUR_MS = 3_600_000L
private val SunPathColor = Color(0xFFFFD27A)

private const val EARTHWIDGET_SKY_SUNRISE = "זריחה"
private const val EARTHWIDGET_SKY_SUNSET = "שקיעה"
private const val EARTHWIDGET_SKY_CHATZOS = "חצות"

/** The sky seen from the selected location at the selected moment. */
internal object SkyWidget : HomeWidget {
    override val id = "sky"
    override val title = "השמיים"
    override val defaultSpan = CellSpan(5, 3)
    override val minSpan = CellSpan(5, 3)
    override val maxSpan = CellSpan(HOME_GRID_COLUMNS, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val now = rememberMinute()
        WidgetCard(modifier) {
            SkyView(state.location.latitude, state.location.longitude, state.location.timeZone, state.skyTimeMillis ?: now.time, Modifier.fillMaxSize())
        }
    }
}

/** Where the view looks, and how wide. */
private data class SkyAim(
    val yawDeg: Float,
    val pitchDeg: Float,
    val fovDeg: Float,
)

/** The viewer's camera: its basis and its perspective projection (desktop SkyCamera). */
private class SkyCamera(
    yawDeg: Float,
    pitchDeg: Float,
    private val w: Float,
    private val h: Float,
    fovDeg: Float,
) {
    val forward = skyDirection(yawDeg.toDouble(), pitchDeg.toDouble())
    val right: Direction
    val up: Direction
    private val tanHalfV = tan(fovDeg / 2f * DEG_TO_RAD).toFloat()
    private val aspect = w / h.coerceAtLeast(1f)

    init {
        val yaw = (yawDeg * DEG_TO_RAD).toFloat()
        right = Direction(cos(yaw), 0f, sin(yaw))
        up = cross(right, forward)
    }

    /** Screen px of direction [d], or null behind the viewer. */
    fun project(d: Direction): Offset? {
        val z = dot(d, forward)
        if (z <= 0.01f) return null
        val x = dot(d, right) / z / (aspect * tanHalfV)
        val y = dot(d, up) / z / tanHalfV
        return Offset((x + 1f) / 2f * w, (1f - y) / 2f * h)
    }

    /** Pixels an angle of [deg] spans at the centre of the view. */
    fun pixels(deg: Float): Float = tan(deg * DEG_TO_RAD).toFloat() / tanHalfV * h / 2f
}

private fun dot(
    a: Direction,
    b: Direction,
) = a.x * b.x + a.y * b.y + a.z * b.z

private fun cross(
    a: Direction,
    b: Direction,
) = Direction(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

/** Stars of the HYG catalogue (to magnitude 4), equatorial unit vectors, magnitudes, colours; read once. */
private class StarCatalog(
    val xyz: FloatArray,
    val magnitude: FloatArray,
    val color: IntArray,
)

@Volatile
private var starCatalog: StarCatalog? = null

private fun loadStars(context: Context): StarCatalog? {
    starCatalog?.let { return it }
    val csv = runCatching { context.resources.openRawResource(R.raw.sky_stars).bufferedReader().use { it.readText() } }.getOrNull() ?: return null
    val rows = csv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(',') }.filter { it.size >= 4 }.toList()
    val xyz = FloatArray(rows.size * 3)
    val magnitude = FloatArray(rows.size)
    val color = IntArray(rows.size)
    rows.forEachIndexed { i, (ra, dec, mag, bv) ->
        val a = ra.toDouble() * 15.0 * DEG_TO_RAD
        val d = dec.toDouble() * DEG_TO_RAD
        xyz[i * 3] = (cos(d) * cos(a)).toFloat()
        xyz[i * 3 + 1] = (cos(d) * sin(a)).toFloat()
        xyz[i * 3 + 2] = sin(d).toFloat()
        magnitude[i] = mag.toDouble().toFloat()
        color[i] = bvToRgb(bv.toDoubleOrNull() ?: 0.6)
    }
    return StarCatalog(xyz, magnitude, color).also { starCatalog = it }
}

/** The Sun's day over the viewer: its path, the hours on it, and where it rises, peaks and sets. */
private class SkyPass(
    val path: List<Pair<Long, Direction>>,
    val hours: List<Direction>,
    val rise: Pair<Long, Direction>?,
    val peak: Pair<Long, Direction>,
    val set: Pair<Long, Direction>?,
)

/** Desktop skyPass: the Sun's pass that contains [instant], or the next one, between its rise and its set. */
private fun skyPass(
    instant: Long,
    horizon: Double,
    at: (Long) -> Pair<Double, Double>,
): SkyPass? {
    fun el(t: Long) = at(t).second

    fun dir(t: Long) = at(t).let { (az, e) -> skyDirection(az, e) }
    var t = instant
    var reach = 0L
    while (el(t) < horizon) {
        t += PASS_STEP_MS
        reach += PASS_STEP_MS
        if (reach > PASS_REACH_MS) return null
    }

    fun end(step: Long): Long? {
        var u = t
        var walked = 0L
        while (el(u + step) >= horizon) {
            u += step
            walked += PASS_STEP_MS
            if (walked > PASS_REACH_MS) return null
        }
        val above = el(u)
        val below = el(u + step)
        return u + (step * ((above - horizon) / (above - below))).toLong()
    }
    val riseAt = end(-PASS_STEP_MS)
    val setAt = end(PASS_STEP_MS)
    val from = riseAt ?: (t - PASS_REACH_MS / 2)
    val to = setAt ?: (t + PASS_REACH_MS / 2)
    val times = (from until to step PASS_STEP_MS) + to
    val path = times.map { it to dir(it) }
    val peak = path.maxBy { it.second.y }
    val hours = ((from / HOUR_MS + 1) * HOUR_MS until to step HOUR_MS).map(::dir)
    return SkyPass(path, hours, riseAt?.let { it to dir(it) }, peak, setAt?.let { it to dir(it) })
}

private class SkyModel(
    val aim: SkyAim,
    val sun: Direction,
    val sunElevation: Double,
    val moon: Direction,
    val moonFront: Float,
    val stars: Array<DoubleArray>,
    val pass: SkyPass?,
)

private fun skyModel(
    latitude: Double,
    longitude: Double,
    millis: Long,
): SkyModel {
    val at = Date(millis)
    val sunPos = computeSolarPositionNoaaUtc(at, latitude, longitude)
    val jd = computeJulianDayUtc(at)
    val moonPos = computeMoonHorizontalPosition(jd, latitude, longitude)
    // The Sun while any of its light is left in the sky (down to 18° under); in full night the Moon if it is up,
    // close in so its phase reads; else the south (the north under the equator)
    val aim =
        when {
            sunPos.elevationDegrees > -18.0 -> {
                val el = sunPos.elevationDegrees.toFloat()
                SkyAim(sunPos.azimuthDegreesFromNorth.toFloat(), (el - 18f).coerceIn(DEFAULT_PITCH_DEG, 60f), SKY_VFOV_DEG)
            }
            moonPos.elevationDeg > 0.0 -> {
                val el = moonPos.elevationDeg.toFloat()
                SkyAim(moonPos.azimuthFromNorthDeg.toFloat(), el.coerceAtLeast(MOON_FOCUS_FOV_DEG * 0.3f), MOON_FOCUS_FOV_DEG)
            }
            else -> SkyAim(if (latitude >= 0) 180f else 0f, DEFAULT_PITCH_DEG, SKY_VFOV_DEG)
        }
    val sun = skyDirection(sunPos.azimuthDegreesFromNorth, sunPos.elevationDegrees)
    val moon = skyDirection(moonPos.azimuthFromNorthDeg, moonPos.elevationDeg)
    // The Moon's lit face towards the viewer: full when opposite the Sun
    val moonFront = -dot(sun, moon)
    val stars = equatorialToSky(localSiderealTimeRad(jd, longitude), latitude)
    // The Sun's pass now, or by night the coming one
    val pass =
        skyPass(millis, -0.833) { t ->
            computeSolarPositionNoaaUtc(Date(t), latitude, longitude).let { it.azimuthDegreesFromNorth to it.elevationDegrees }
        }
    return SkyModel(aim, sun, sunPos.elevationDegrees, moon, moonFront, stars, pass)
}

/** The sky over [latitude]/[longitude] at [millis], as seen from the ground. */
@Composable
internal fun SkyView(
    latitude: Double,
    longitude: Double,
    timeZone: TimeZone,
    millis: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stars = rememberOffMain(Unit, tag = "sky.stars") { loadStars(context) }
    val model = rememberOffMain(latitude, longitude, millis / 60_000L, tag = "sky.model") { skyModel(latitude, longitude, millis) }
    val measurer = rememberTextMeasurer()
    val clock = remember(timeZone) { SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = timeZone } }
    Canvas(modifier) {
        val m = model ?: return@Canvas
        val camera = SkyCamera(m.aim.yawDeg, m.aim.pitchDeg, size.width, size.height, m.aim.fovDeg)
        drawSkyGradient(camera, m.sunElevation)
        if (stars != null) drawStars(stars, m, camera)
        m.pass?.let { drawPass(it, millis, camera, measurer, clock) }
        // The Moon, lit from the Sun's side
        camera.project(m.moon)?.let { at ->
            if (m.moon.y > -0.02f) {
                val toSun = camera.project(m.sun)?.let { atan2(it.y - at.y, it.x - at.x) * 57.29578f } ?: if (dot(m.sun, camera.right) > 0) 0f else 180f
                drawLitSphere(at, camera.pixels(MOON_ANGULAR_RADIUS_DEG), toSun, m.moonFront, Color(0xFFEDEAE2), Color(0xFF1B1D24).copy(alpha = 0.85f))
            }
        }
        camera.project(m.sun)?.let { at ->
            if (m.sun.y > -0.02f) {
                val r = camera.pixels(SUN_ANGULAR_RADIUS_DEG)
                drawCircle(Brush.radialGradient(listOf(Color(0x66FFE7A8), Color.Transparent), at, r * 3f), r * 3f, at)
                drawCircle(Color(0xFFFFF4D6), r, at)
            }
        }
        drawGround(camera)
    }
}

/** The sky's colours, from the zenith to the horizon, as the Sun's height turns day into dusk and night. */
private fun skyColors(elevation: Double): Pair<Color, Color> {
    val stops =
        listOf(
            -18.0 to (Color(0xFF02040A) to Color(0xFF0A1020)),
            -12.0 to (Color(0xFF060B1C) to Color(0xFF1B2448)),
            -6.0 to (Color(0xFF13234A) to Color(0xFF6A4E6E)),
            0.0 to (Color(0xFF2A4A82) to Color(0xFFE38B5A)),
            6.0 to (Color(0xFF3567A8) to Color(0xFFF1BE86)),
            20.0 to (Color(0xFF2D6DB8) to Color(0xFFA9CFF0)),
        )
    if (elevation <= stops.first().first) return stops.first().second
    if (elevation >= stops.last().first) return stops.last().second
    val i = stops.indexOfLast { it.first <= elevation }
    val (e0, c0) = stops[i]
    val (e1, c1) = stops[i + 1]
    val f = ((elevation - e0) / (e1 - e0)).toFloat()
    return lerp(c0.first, c1.first, f) to lerp(c0.second, c1.second, f)
}

private fun DrawScope.drawSkyGradient(
    camera: SkyCamera,
    sunElevation: Double,
) {
    val (zenith, horizon) = skyColors(sunElevation)
    val horizonY = camera.project(Direction(camera.forward.x, 0f, camera.forward.z).normalized())?.y ?: size.height
    drawRect(Brush.verticalGradient(listOf(zenith, horizon), startY = 0f, endY = horizonY.coerceAtLeast(1f)))
}

private fun Direction.normalized(): Direction {
    val n = kotlin.math.sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
    return Direction(x / n, y / n, z / n)
}

/** The land under the horizon: dark, its edge a little lighter. */
private fun DrawScope.drawGround(camera: SkyCamera) {
    val flat = Direction(camera.forward.x, 0f, camera.forward.z).normalized()
    val y = camera.project(flat)?.y ?: return
    if (y >= size.height) return
    drawRect(Brush.verticalGradient(listOf(Color(0xFF15181C), Color(0xFF07080A)), startY = y, endY = size.height), topLeft = Offset(0f, y))
    drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

/** Stars as points of light, the faintest showing only in a dark sky (desktop drawStars, without the air's dimming). */
private fun DrawScope.drawStars(
    catalog: StarCatalog,
    m: SkyModel,
    camera: SkyCamera,
) {
    // Faintest magnitude that shows: by night to the card's limit, none by day
    val darkness = ((-m.sunElevation - 2.0) / 12.0).coerceIn(0.0, 1.0).toFloat()
    if (darkness <= 0f) return
    val limit = -1.5f + (CARD_FAINTEST_MAGNITUDE + 1.5f) * darkness
    val unit = 1.dp.toPx()
    val (c0, c1, c2) = m.stars
    for (i in catalog.magnitude.indices) {
        if (catalog.magnitude[i] > limit + 0.5f) continue
        val x = catalog.xyz[i * 3]
        val y = catalog.xyz[i * 3 + 1]
        val z = catalog.xyz[i * 3 + 2]
        val d = Direction((c0[0] * x + c1[0] * y + c2[0] * z).toFloat(), (c0[1] * x + c1[1] * y + c2[1] * z).toFloat(), (c0[2] * x + c1[2] * y + c2[2] * z).toFloat())
        if (d.y < -0.01f) continue
        val at = camera.project(d) ?: continue
        if (at.x < -4f || at.y < -4f || at.x > size.width + 4f || at.y > size.height + 4f) continue
        val excess = limit - catalog.magnitude[i]
        if (excess < 0f) continue
        val bright = excess * 0.4f
        val radius = unit * (0.6f + 0.45f * bright).coerceAtMost(3.2f)
        val alpha = ((0.35f + 0.25f * bright) * darkness).coerceIn(0.15f, 1f)
        drawCircle(Color(catalog.color[i] or (0xFF shl 24)).copy(alpha = alpha), radius, at)
    }
}

/** The pass as a line: solid where the Sun has been, dashed ahead, a dot on each hour; its rise, chatzos and set named. */
private fun DrawScope.drawPass(
    pass: SkyPass,
    now: Long,
    camera: SkyCamera,
    measurer: TextMeasurer,
    clock: SimpleDateFormat,
) {
    val done = Path()
    val ahead = Path()
    var last: Offset? = null
    var lastDone = false
    for ((t, d) in pass.path) {
        val p = camera.project(d)
        val isDone = t <= now
        if (p != null && last != null) {
            val target = if (isDone) done else ahead
            if (target.isEmpty || isDone != lastDone) target.moveTo(last.x, last.y)
            target.lineTo(p.x, p.y)
        }
        last = p
        lastDone = isDone
    }
    drawPath(done, SunPathColor.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    drawPath(ahead, SunPathColor.copy(alpha = 0.6f), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
    pass.hours.forEach { d -> camera.project(d)?.let { drawCircle(SunPathColor.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = it) } }
    val style = TextStyle(color = SunPathColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f))
    listOfNotNull(
        pass.rise?.let { EARTHWIDGET_SKY_SUNRISE to it },
        EARTHWIDGET_SKY_CHATZOS to pass.peak,
        pass.set?.let { EARTHWIDGET_SKY_SUNSET to it },
    ).forEach { (name, point) ->
        val at = camera.project(point.second) ?: return@forEach
        if (at.x < 0f || at.x > size.width) return@forEach
        val layout = measurer.measure("$name ⁦${clock.format(Date(point.first))}⁩", style)
        val x = (at.x - layout.size.width / 2f).coerceIn(2f, (size.width - layout.size.width - 2f).coerceAtLeast(2f))
        val y = (at.y - layout.size.height - 6.dp.toPx()).coerceIn(2f, (size.height - layout.size.height - 2f).coerceAtLeast(2f))
        drawText(layout, topLeft = Offset(x, y))
    }
}
