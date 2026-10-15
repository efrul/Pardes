package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind

/**
 * The luach's month, Hebrew or civil; a tap on a day moves every widget to it. Port of desktop 0.6.1
 * features/home/widgets/calendar/CalendarWidget.kt (its civil/Hebrew choice saved as home_widget.calendar.civil).
 */
internal object CalendarWidget : HomeWidget {
    override val id = "calendar"
    override val title = "לוח עברי"
    override val defaultSpan = CellSpan(7, 5)
    override val minSpan = CellSpan(7, 5)
    override val tool = ToolKind.HebrewCalendar

    // Six week rows hold their two lines of text; taller would only part them
    override val maxSpan = CellSpan(14, 6)
    override val settings get() = CalendarSettings

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val civil = state[CalendarSettings.civil]
        PanelCard(modifier) {
            LuachMonth(
                selected = state.shownDate,
                today = state.todayDate,
                inIsrael = state.inIsrael,
                civil = civil,
                onSwap = { state[CalendarSettings.civil] = !civil },
                onSelect = state::selectDate,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
    }
}

internal object CalendarSettings : WidgetSettings(CalendarWidget.id) {
    /** The civil month shown, the Hebrew one's days beside; else the Hebrew month. */
    val civil by boolean(false)
}
