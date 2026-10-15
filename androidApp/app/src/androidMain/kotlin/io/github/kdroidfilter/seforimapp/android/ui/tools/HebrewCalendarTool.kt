package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.City
import io.github.kdroidfilter.seforimapp.android.data.HebrewText
import io.github.kdroidfilter.seforimapp.android.data.JewishDay
import io.github.kdroidfilter.seforimapp.android.data.Limud
import io.github.kdroidfilter.seforimapp.android.data.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.android.data.candleLightingTime
import io.github.kdroidfilter.seforimapp.android.data.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.android.data.havdalahTime
import io.github.kdroidfilter.seforimapp.android.data.limudOfDay
import io.github.kdroidfilter.seforimapp.android.data.luachName
import io.github.kdroidfilter.seforimapp.android.data.mikraWeek
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.CalendarSettings
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.CalendarWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.LuachMonth
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberClock
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import java.time.LocalDate
import java.util.Date

/**
 * "לוח עברי": desktop's Calendar widget alone, as its tool window (features/home/widgets/calendar/CalendarWidget.kt,
 * 420 × 460, luach/LuachMonth.kt): the Hebrew month or the civil one (saved as home_widget.calendar.civil), the
 * holiday names in the cells wide enough for them; a tap picks the day every tool shows. The page adds the picked
 * day's card (Android): its dates and occasion, the parsha and the daf, and its times by the community's luach.
 */
@Composable
fun HebrewCalendarTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    ToolPage(ToolKind.HebrewCalendar.title, onBack) { wide ->
        if (inToolPanel()) {
            CalendarWidget.Content(state, Modifier.fillMaxSize())
            return@ToolPage
        }
        val civil = state[CalendarSettings.civil]
        val month: @Composable (Modifier) -> Unit = { mod ->
            PanelCard(mod) {
                LuachMonth(
                    selected = state.shownDate,
                    today = state.todayDate,
                    inIsrael = state.inIsrael,
                    civil = civil,
                    onSwap = { state[CalendarSettings.civil] = !civil },
                    onSelect = state::selectDate,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    onToday = state::backToToday,
                )
            }
        }
        ToolColumn(maxWidth = if (wide) 1100.dp else 620.dp) {
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    month(Modifier.weight(1.3f).height(560.dp))
                    DayDetails(state, Modifier.weight(1f))
                }
            } else {
                month(Modifier.fillMaxWidth().aspectRatio(0.9f).heightIn(max = 560.dp))
                DayDetails(state, Modifier.fillMaxWidth())
            }
        }
    }
}

private data class DayFacts(
    val title: String,
    val civil: String,
    val parsha: String,
    val daf: String?,
    val times: List<Triple<String, Date?, Boolean>>,
)

/**
 * The facts of [date]. Without [full], only its lines: the dates and which times the day has, the rest blank. That much
 * is quick enough to draw at once, and the card then has its size before the times are worked out off the UI thread.
 */
private fun dayFacts(
    date: LocalDate,
    city: City,
    opinion: ZmanimOpinion,
    full: Boolean,
): DayFacts {
    val location = city.location
    val inIsrael = city.inIsrael
    val today = JewishDay.calendar(date, inIsrael)
    val tomorrow = JewishDay.calendar(date.plusDays(1), inIsrael)
    val z = if (full) computeZmanimTimes(date, location, opinion, inIsrael) else null
    return DayFacts(
        title = toolDayLabel(date, inIsrael),
        civil = HebrewText.gregorian(date),
        parsha = if (full) mikraWeek(date, inIsrael).name else " ",
        daf = if (full) runCatching { limudOfDay(date, inIsrael, setOf(Limud.BAVLI)).firstOrNull()?.value }.getOrNull() else " ",
        times =
            buildList {
                add(Triple("נץ החמה", z?.sunrise, false))
                add(Triple("שקיעת החמה", z?.sunset, false))
                add(Triple("צאת הכוכבים", z?.tzais, false))
                if (!today.isAssurBemelacha && tomorrow.isAssurBemelacha) {
                    add(Triple("הדלקת נרות", if (full) candleLightingTime(date, location, opinion, city.name.ifBlank { null }, inIsrael) else null, true))
                }
                if (today.isAssurBemelacha && !tomorrow.isAssurBemelacha) {
                    add(Triple("יציאה", if (full) havdalahTime(date, location, opinion, inIsrael) else null, true))
                }
            },
    )
}

/** The picked day: its dates, the week's parsha, the daf, and its times (candles and havdala on their days). */
@Composable
private fun DayDetails(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val date = state.shownDate
    val city = state.city
    val opinion = state.zmanimOpinion
    val cityLabel = state.cityLabel
    val blank = remember(date, city) { dayFacts(date, city, opinion, full = false) }
    val facts = rememberToolValue("calendar.day", date, city, opinion) { dayFacts(date, city, opinion, full = true) } ?: blank
    val clock = rememberClock(state.location.timeZone)
    ToolCard(facts.title, modifier) {
        Text(facts.civil, color = c.textSecondary, fontSize = 13.sp)
        Column(Modifier.padding(top = 6.dp)) {
            KeyValueRow("פרשת השבוע", facts.parsha)
            facts.daf?.let { KeyValueRow("דף יומי בבלי", it) }
            HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 4.dp))
            facts.times.forEach { (label, time, special) ->
                KeyValueRow(
                    label,
                    time?.let(clock) ?: "--:--",
                    valueColor = if (special) c.accent else c.text,
                    valueWeight = if (special) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Text(
            listOfNotNull(cityLabel, "לפי ${opinion.luachName}").joinToString(" · "),
            color = c.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
