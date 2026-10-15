package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.luachName
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.MoladWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.TefilaWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberClock
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import java.util.Date

/**
 * "תפילת היום": desktop's Tefila widget alone, as its tool window (features/home/widgets/luach/LuachWidgets.kt
 * TefilaWidget, 380 × 320): what changes in the day's tefila (LuachData.tefilaOfDay), the Hebrew date and
 * "פתח סידור". A page on a phone, the day above it; under it Android adds the day's prayer times (the community's luach)
 * and the Molad widget.
 */
@Composable
fun DailyPrayerTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    ToolPage(ToolKind.DailyPrayer.title, onBack) { wide ->
        if (inToolPanel()) {
            TefilaWidget.Content(state, Modifier.fillMaxSize())
            return@ToolPage
        }
        ToolColumn(maxWidth = if (wide) 1000.dp else 560.dp) {
            ToolDayBar(state)
            AdaptiveColumns(
                wide,
                first = { TefilaWidget.Content(state, Modifier.fillMaxWidth().height(TEFILA_HEIGHT)) },
                second = {
                    PrayerTimesCard(state)
                    MoladWidget.Content(state, Modifier.fillMaxWidth().height(MOLAD_HEIGHT))
                },
            )
        }
    }
}

// Desktop's tool windows, less their title bar and padding
private val TEFILA_HEIGHT = 264.dp
private val MOLAD_HEIGHT = 144.dp

/**
 * The day's prayer times, by the community's luach (desktop's zmanim cards' names). Its rows are there from the first
 * frame, "--:--" until the times are worked out, so the Molad card under it keeps its place.
 */
@Composable
private fun PrayerTimesCard(state: HomeWidgetsState) {
    val c = Zayit.colors
    val opinion = state.zmanimOpinion
    val z = rememberToolZmanim(state.shownDate, state.city, opinion)
    val clock = rememberClock(state.location.timeZone)
    val rows: List<Pair<String, Date?>> =
        listOf(
            "סוף זמן ק\"ש · מג״א" to z?.sofZmanShmaMga,
            "סוף זמן ק\"ש · גר״א" to z?.sofZmanShmaGra,
            "סוף זמן תפילה · מג״א" to z?.sofZmanTfilaMga,
            "סוף זמן תפילה · גר״א" to z?.sofZmanTfilaGra,
            "מנחה גדולה" to z?.minchaGedola,
            "פלג המנחה" to z?.plagHamincha,
            "שקיעת החמה" to z?.sunset,
            "צאת הכוכבים" to z?.tzais,
        )
    ToolCard("זמני התפילה", subtitle = state.cityLabel) {
        rows.forEachIndexed { i, (label, time) ->
            if (i > 0) HorizontalDivider(color = c.divider)
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(time?.let(clock) ?: "--:--", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("לפי ${opinion.luachName}", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
