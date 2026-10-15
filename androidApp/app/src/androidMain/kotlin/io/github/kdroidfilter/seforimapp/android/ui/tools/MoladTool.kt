package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.android.data.hebrewFormatter
import io.github.kdroidfilter.seforimapp.android.data.hebrewWeekday
import io.github.kdroidfilter.seforimapp.android.data.toDate
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.MoladWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.ltr
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberMinute
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * "מולד וקידוש לבנה": desktop's Molad widget alone, as its tool window (features/home/widgets/luach/LuachWidgets.kt
 * MoladWidget, 280 × 200): the coming month's molad and the Kiddush Levana window of the user's community, in the
 * accent once it can be said. A page on a phone, the day above it; under it Android adds the moon of the day shown and
 * the year's moladot, from the same calendar (KosherKotlin's molad).
 */
@Composable
fun MoladTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    ToolPage(ToolKind.Molad.title, onBack) { wide ->
        if (inToolPanel()) {
            MoladWidget.Content(state, Modifier.fillMaxSize())
            return@ToolPage
        }
        ToolColumn(maxWidth = if (wide) 1000.dp else 560.dp) {
            ToolDayBar(state)
            AdaptiveColumns(
                wide,
                first = {
                    MoladWidget.Content(state, Modifier.fillMaxWidth().height(144.dp))
                    MoonCard(state)
                },
                second = { MoladYear(state) },
            )
        }
    }
}

private const val SYNODIC_DAYS = 29.530588

/** The month's molad, as an instant (the one of the Hebrew month [date] falls in). */
private fun moladOfMonth(
    date: java.time.LocalDate,
    inIsrael: Boolean,
): Date {
    val month = JewishCalendar(date.toKotlinLocalDate(), inIsrael).apply { setJewishDate(jewishYear, jewishMonth, 1) }
    return month.moladAsInstant.toDate()
}

/**
 * The moon at the moment shown: its age since the molad, lit as it is. The card is drawn before the molad is worked
 * out, blank, so the cards under it never move down when it is.
 */
@Composable
private fun MoonCard(state: HomeWidgetsState) {
    val c = Zayit.colors
    val now = rememberMinute()
    val moment = state.skyTimeMillis ?: now.time
    val day = state.shownDate
    val inIsrael = state.inIsrael
    val lastMolad =
        rememberToolValue("molad.last", day, inIsrael, moment / 3_600_000L) {
            val molad = moladOfMonth(day, inIsrael)
            if (molad.time <= moment) molad else moladOfMonth(day.minusDays(25), inIsrael)
        }
    val age = lastMolad?.let { (moment - it.time) / 86_400_000.0 }
    val phase = age?.let { ((it / SYNODIC_DAYS) % 1.0 + 1.0) % 1.0 }
    ToolCard("הלבנה") {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Canvas(Modifier.size(88.dp)) { if (phase != null) drawMoon(phase, if (c.isDark) Color(0xFF15161A) else Color(0xFF30323A), Color(0xFFF4EBC8)) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(age?.let { "${"%.1f".format(it)} ימים מהמולד" } ?: " ", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(phase?.let { "מואר: ${((1 - cos(2 * PI * it)) / 2 * 100).toInt()}%" } ?: " ", color = c.textSecondary, fontSize = 14.sp)
            }
        }
    }
}

/** The moon disc lit after [phase] (0 the molad, 0.5 full), the lit side on the right while it waxes. */
private fun DrawScope.drawMoon(
    phase: Double,
    dark: Color,
    light: Color,
) {
    val r = size.minDimension / 2f * 0.92f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(dark, r, Offset(cx, cy))
    val waxing = phase <= 0.5
    val q = if (waxing) phase else 1 - phase
    val k = cos(2 * PI * q).toFloat()
    val path = Path()
    val steps = 48

    fun x(dx: Float) = if (waxing) cx + dx else cx - dx
    for (i in 0..steps) {
        val t = -PI / 2 + PI * i / steps
        val px = x(r * cos(t).toFloat())
        val py = cy + r * sin(t).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    for (i in steps downTo 0) {
        val t = -PI / 2 + PI * i / steps
        path.lineTo(x(r * k * cos(t).toFloat()), cy + r * sin(t).toFloat())
    }
    path.close()
    drawPath(path, light)
    drawCircle(light.copy(alpha = 0.35f), r, Offset(cx, cy), style = Stroke(width = 1.5f))
}

private data class YearMolad(
    val month: String,
    val molad: String,
    val roshChodesh: String,
    val at: Date,
)

/**
 * The moladot of the twelve months from the one shown, as the Molad widget writes one ("weekday · HH:mm · N ח׳"). Its
 * thirteen rows are there, blank, while they are worked out: the card has its size from the first frame.
 */
@Composable
private fun MoladYear(state: HomeWidgetsState) {
    val c = Zayit.colors
    val now = rememberMinute()
    val day = state.shownDate
    val inIsrael = state.inIsrael
    val list =
        rememberToolValue("molad.year", day, inIsrael) {
            var month = JewishCalendar(day.toKotlinLocalDate(), inIsrael).apply { setJewishDate(jewishYear, jewishMonth, 1) }
            buildList<YearMolad> {
                repeat(YEAR_MONTHS) {
                    val molad = month.molad
                    val time = "${molad.moladHours.toString().padStart(2, '0')}:${molad.moladMinutes.toString().padStart(2, '0')}"
                    val first = month.gregorianLocalDate.toJavaLocalDate()
                    // After a full month (its 30th day) Rosh Chodesh is two days, that 30th and the 1st, as the luach counts it
                    val eve = first.minusDays(1)
                    val twoDays = JewishCalendar(eve.toKotlinLocalDate(), inIsrael).jewishDayOfMonth == 30
                    add(
                        YearMolad(
                            month = hebrewFormatter.formatMonth(month),
                            molad = "${molad.gregorianLocalDate.toJavaLocalDate().hebrewWeekday()} · ${time.ltr()} · ${molad.moladChalakim} ח׳",
                            roshChodesh = if (twoDays) "${roshChodeshDay(eve)} ו${roshChodeshDay(first)}" else roshChodeshDay(first),
                            at = month.moladAsInstant.toDate(),
                        ),
                    )
                    month = JewishCalendar(month.gregorianLocalDate.plus(month.daysInJewishMonth, DateTimeUnit.DAY), inIsrael)
                }
            }
        }
    val rows: List<YearMolad?> = list ?: List(YEAR_MONTHS) { null }
    ToolCard("מולדות השנה") {
        rows.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = c.divider)
            val past = m != null && m.at.before(now)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m?.month ?: " ", color = if (past) c.textSecondary else c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(m?.let { "ראש חודש: ${it.roshChodesh}" } ?: " ", color = c.textSecondary, fontSize = 12.sp)
                }
                Text(m?.molad ?: " ", color = if (past) c.textSecondary else c.accent, fontSize = 14.sp)
            }
        }
    }
}

// The month shown and the twelve after it, a leap year's thirteen months at most
private const val YEAR_MONTHS = 13

/** "שני ⁦12.10⁩": a day of Rosh Chodesh, its date kept in one piece. */
private fun roshChodeshDay(date: java.time.LocalDate) = "${date.hebrewWeekday()} ⁦${date.dayOfMonth}.${date.monthValue}⁩"
