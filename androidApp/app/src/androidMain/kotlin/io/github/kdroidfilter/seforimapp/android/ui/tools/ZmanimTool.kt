package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.ZmanRow
import io.github.kdroidfilter.seforimapp.android.data.ZmanimDay
import io.github.kdroidfilter.seforimapp.android.data.luachName
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.NextZmanWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.ZmanimWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberClock
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberMinute
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/**
 * "זמני היום": desktop's Zmanim widget alone, as its tool window (features/home/widgets/zmanim/ZmanimWidget.kt,
 * 700 × 360): the zmanim cards of the day shown, a tap on one pointing the other tools at its moment. The page adds the
 * day and the place to look at (never written to the settings), the coming zmanim (desktop's next-zman widget, today)
 * and every zman of the day in a list, all by the community's luach and computed off the UI thread.
 */
@Composable
fun ZmanimTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    ToolPage(ToolKind.Zmanim.title, onBack) { wide ->
        ToolColumn(maxWidth = if (wide) 1100.dp else 760.dp) {
            ToolDayBar(state, showCity = true)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                ZmanimWidget.Content(state, Modifier.fillMaxWidth().height(ZmanimWidget.heightAt(maxWidth)))
            }
            AdaptiveColumns(
                wide,
                first = { ZmanList(state) },
                second = {
                    if (state.shownDate == state.todayDate) NextZmanWidget.Content(state, Modifier.fillMaxWidth().height(NEXT_ZMAN_HEIGHT))
                },
            )
            Text("לפי ${state.zmanimOpinion.luachName}", color = Zayit.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

private val NEXT_ZMAN_HEIGHT = 220.dp

// ZmanimDay.rows on an ordinary day; the days with a fast, candles or havdala add a row or two
private const val DAY_ZMANIM = 14

/**
 * Every zman of the day in time order; a tap points the tools at it, as a zman card does. While they are worked out
 * the card holds as many blank rows as a day has zmanim, not a small box the list then grows out of.
 */
@Composable
private fun ZmanList(state: HomeWidgetsState) {
    val c = Zayit.colors
    val city = state.city
    val date = state.shownDate
    val opinion = state.zmanimOpinion
    val rows: List<ZmanRow?> = rememberToolValue("zmanim.day", city, date, opinion) { ZmanimDay.rows(city, date, opinion) } ?: List(DAY_ZMANIM) { null }
    val clock = rememberClock(state.location.timeZone)
    val now = rememberMinute().time
    val today = date == state.todayDate
    val selected = state.targetTime?.time
    ToolCard("זמני היום", subtitle = state.cityLabel) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = c.divider)
            val time = row?.time
            val past = today && time != null && time.time < now
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (time != null && time.time == selected) c.selectedRow else Color.Transparent)
                    .clickable(enabled = time != null, role = Role.Button) { state.targetTime = time }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val emphasized = row?.emphasized == true
                Column(Modifier.weight(1f)) {
                    Text(row?.title ?: " ", color = if (past) c.textSecondary else c.text, fontSize = 15.sp, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
                    row?.subtitle?.let { Text(it, color = c.textSecondary, fontSize = 12.sp) }
                }
                Text(
                    time?.let(clock) ?: "--:--",
                    color =
                        when {
                            emphasized -> c.accent
                            past -> c.textSecondary
                            else -> c.text
                        },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
