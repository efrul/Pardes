package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.Limud
import io.github.kdroidfilter.seforimapp.android.data.LimudGroup
import io.github.kdroidfilter.seforimapp.android.data.LimudItem
import io.github.kdroidfilter.seforimapp.android.data.LibraryPlace
import io.github.kdroidfilter.seforimapp.android.data.LuachEvent
import io.github.kdroidfilter.seforimapp.android.data.LuachMolad
import io.github.kdroidfilter.seforimapp.android.data.TefilaLine
import io.github.kdroidfilter.seforimapp.android.data.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.android.data.countdown
import io.github.kdroidfilter.seforimapp.android.data.hebrewDateOf
import io.github.kdroidfilter.seforimapp.android.data.hebrewWeekday
import io.github.kdroidfilter.seforimapp.android.data.limudOfDay
import io.github.kdroidfilter.seforimapp.android.data.moladInfo
import io.github.kdroidfilter.seforimapp.android.data.nextZmanim
import io.github.kdroidfilter.seforimapp.android.data.resolvePlace
import io.github.kdroidfilter.seforimapp.android.data.tefilaOfDay
import io.github.kdroidfilter.seforimapp.android.data.upcomingEvents
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.components.ZCheckRow
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.tools.rememberSiddurInLibrary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Port of desktop 0.6.7 features/home/widgets/luach/LuachWidgets.kt, 0.6.1's with the 0.6.2 fix 411280b9 (TefilaWidget
// turns to tomorrow at tzeit hakochavim), its only change since: the luach widgets (limud, the next zman, the coming
// events, the day's tefila, the molad), text on the calendar's panel, following the Home's day. Their lists show as
// many whole rows as fit. The tools show the same cards on their pages.

// Every line the same height, and the title: the card's height is worked out from their number (heightAt)
private val LIMUD_ROW_HEIGHT = 26.dp
private val LIMUD_ROW_GAP = 2.dp
private val LIMUD_TITLE_HEIGHT = 22.dp
private val LIMUD_PADDING = 8.dp
private val LIMUD_COLUMN_GAP = 12.dp

/** From this width the lines go two by two, the card half as tall. */
private val LIMUD_TWO_COLUMNS_WIDTH = 330.dp

internal fun limudColumnsAt(width: Dp) = if (width >= LIMUD_TWO_COLUMNS_WIDTH) 2 else 1

/** The day's limudim the user picked in its options (see [Limud]); tapping one opens it in a new tab. */
internal object LimudWidget : HomeWidget {
    override val id = "limud"
    override val title = "לימוד יומי"
    override val defaultSpan = CellSpan(4, 4)
    override val minSpan = CellSpan(4, 2)
    override val tool = ToolKind.DailyStudy

    // Its height is the one its lines need (heightAt), no more: only its width is the user's, to two columns
    override val maxSpan = CellSpan(10, 2)

    override val settings get() = LimudSettings

    // The limudim shown, as saved: read by the grid for the card's height as well as by the card
    private var shown by mutableStateOf(LimudSettings.shown.default)

    override fun applySettings(store: HomeWidgetStore) {
        shown = store[LimudSettings.shown]
    }

    /** Tall enough for every limud picked, never one hidden for want of room. */
    override fun heightAt(width: Dp): Dp {
        val columns = limudColumnsAt(width)
        val rows = (shown.size + columns - 1) / columns
        return LIMUD_PADDING * 2 + LIMUD_TITLE_HEIGHT + LIMUD_ROW_HEIGHT * rows + LIMUD_ROW_GAP * (rows - 1).coerceAtLeast(0)
    }

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = LimudPanel(state, state[LimudSettings.shown], rememberOpenInLibrary(state), modifier)

    // Its limudim are many: picked in a page of their own rather than in the menu
    @Composable
    override fun menuItems(state: HomeWidgetsState) = listOf(WidgetMenuItem(HOME_WIDGETS_OPTIONS) { state.optionsOpen = this })

    @Composable
    override fun Options(state: HomeWidgetsState) = LimudOptions(state)
}

internal object LimudSettings : WidgetSettings(LimudWidget.id) {
    val shown by enumSet(Limud.defaults.toSet())
}

/** A box for each limud, by group, with the day's reading beside it; each tick shows or hides it at once. */
@Composable
fun LimudOptions(state: HomeWidgetsState) {
    val c = Zayit.colors
    val shown = state[LimudSettings.shown]
    val readings =
        rememberOffMain(state.shownDate, state.inIsrael, tag = "luach.shownDay") {
            Limud.entries.associateWith { limudOfDay(state.shownDate, state.inIsrael, setOf(it)).firstOrNull()?.value }
        }.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (group in LimudGroup.entries) {
            Text(group.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            for (limud in Limud.entries.filter { it.group == group }) {
                OptionCheckRow(limud.title, readings[limud], limud in shown) { on ->
                    state[LimudSettings.shown] = if (on) shown + limud else shown - limud
                }
            }
        }
    }
}

/** A tick, its label and, on the end side, its detail (desktop CheckboxRow and the reading beside it). */
@Composable
internal fun OptionCheckRow(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = Zayit.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ZCheckRow(label, checked, { if (enabled) onCheckedChange(!checked) }, Modifier.weight(1f))
        if (detail != null) {
            Text(detail, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp))
        }
    }
}

/** The limud lines of [shown], opening a place with [open]: as many as fit the card. */
@Composable
internal fun LimudPanel(
    state: HomeWidgetsState,
    shown: Set<Limud>,
    open: (LibraryPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val day = state.shownDate
    val inIsrael = state.inIsrael
    val items = rememberOffMain(day, inIsrael, shown, tag = "luach.limud") { limudOfDay(day, inIsrael, shown) } ?: return WidgetCardLoading(modifier)
    PanelCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = limudColumnsAt(maxWidth)
            Column(Modifier.fillMaxSize().padding(LIMUD_PADDING)) {
                Row(Modifier.fillMaxWidth().height(LIMUD_TITLE_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                    Text("לימוד יומי", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                    // Its options at hand, as in its menu
                    TitleIconButton(Icons.Outlined.Settings, HOME_WIDGETS_OPTIONS) { state.optionsOpen = LimudWidget }
                }
                // Two columns: the lines two by two, so both columns' lines stay level
                FitColumn(Modifier.fillMaxWidth().weight(1f), spacing = LIMUD_ROW_GAP, spread = true) {
                    items.chunked(columns).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(LIMUD_COLUMN_GAP)) {
                            repeat(columns) { i ->
                                Box(Modifier.weight(1f)) { pair.getOrNull(i)?.let { LimudRow(it, open, chevron = columns == 1) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small icon button in a card's title row, a 36 dp target around its 22 dp icon. */
@Composable
internal fun TitleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Zayit.colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LimudRow(
    item: LimudItem,
    open: (LibraryPlace) -> Unit,
    // Left out on two columns, for the text's room
    chevron: Boolean = true,
) {
    val c = Zayit.colors
    val accent = c.accent
    val place = item.place
    HoverBox(onClick = place?.let { { open(it) } }, modifier = Modifier.fillMaxWidth().height(LIMUD_ROW_HEIGHT)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The kind of limud, as a tag
            Box(
                Modifier.width(54.dp).clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.16f)).padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.kicker, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
            }
            Text(item.value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // Towards the end of the reading direction: "open"
            if (place != null && chevron) Icon(Icons.Outlined.ChevronLeft, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

/** The zmanim coming next, counting down; tapping one points the Earth, the sky and the solar system at it. */
internal object NextZmanWidget : HomeWidget {
    override val id = "next_zman"
    override val title = "הזמן הבא"
    override val defaultSpan = CellSpan(5, 2)
    override val minSpan = CellSpan(4, 2)

    // Wider, its name and its clock would stand apart across the card
    override val maxSpan = CellSpan(6, 6)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val c = Zayit.colors
        val now = rememberMinute()
        val location = state.location
        val opinion = state.zmanimOpinion
        val inIsrael = state.inIsrael
        val zmanim = rememberOffMain(now.time / 60_000L, location, opinion, inIsrael, tag = "luach.nextZmanim") { nextZmanim(now, location, opinion, inIsrael, count = 20) } ?: return WidgetCardLoading(modifier)
        val clock = rememberClock(location.timeZone)
        PanelCard(modifier) {
            val next = zmanim.firstOrNull() ?: return@PanelCard
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
                // The coming zman takes up to half the card, the ones after it the rest
                val heroHeight = (maxHeight * 0.5f).coerceIn(44.dp, 96.dp)
                // A narrow card leaves the name its room
                val clockSize = if (maxWidth < 220.dp) 28.sp else 44.sp
                Column(Modifier.fillMaxSize()) {
                    HoverBox(onClick = { state.targetTime = next.time }, modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Kicker("הזמן הבא · ${countdown(now, next.time)}")
                            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(next.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                FitText(clock(next.time), c.accent, max = clockSize, min = 18.sp)
                            }
                        }
                    }
                    FitColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp)) {
                        zmanim.drop(1).forEach { zman ->
                            HoverBox(onClick = { state.targetTime = zman.time }, modifier = Modifier.fillMaxWidth()) {
                                TimeRow(zman.name, clock(zman.time), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The coming Shabbatot, festivals and fasts with their times; tapping one moves every widget to it. */
internal object UpcomingEventsWidget : HomeWidget {
    override val id = "upcoming_events"
    override val title = "מועדים קרובים"
    override val defaultSpan = CellSpan(6, 4)
    override val minSpan = CellSpan(5, 3)

    // Wider, the names and their times would stand apart across the card
    override val maxSpan = CellSpan(7, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val c = Zayit.colors
        val location = state.location
        val day = state.shownDate
        val opinion = state.zmanimOpinion
        val cityLabel = state.cityLabel
        val inIsrael = state.inIsrael
        val events =
            rememberOffMain(day, location, opinion, cityLabel, inIsrael, tag = "luach.shabbat") {
                upcomingEvents(day, location, opinion, cityLabel, inIsrael, limit = 14)
            } ?: return WidgetCardLoading(modifier)
        val clock = rememberClock(location.timeZone)
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Text("אירועים קרובים", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp).height(22.dp))
                FitColumn(Modifier.fillMaxWidth().weight(1f)) {
                    events.forEach { event ->
                        HoverBox(onClick = { state.selectDate(event.date) }, modifier = Modifier.fillMaxWidth()) { EventRow(event, clock) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: LuachEvent,
    clock: (Date) -> String,
) {
    val c = Zayit.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(event.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${event.date.hebrewWeekday()} · ${event.hebrewDate} · ⁦${event.date.dayOfMonth}.${event.date.monthValue}⁩",
                fontSize = 11.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            event.times.forEach { (label, time) ->
                time?.let { Text("$label ${clock(it)}", fontSize = 11.sp, color = c.textSecondary, maxLines = 1) }
            }
        }
    }
}

/**
 * The day whose tefila is said at [moment] on [civilDay] (desktop 0.6.2 411280b9): from its צאת הכוכבים [tzais],
 * tonight's maariv is tomorrow's. The tools list's line under "תפילת היום" asks it too, so it says what this card shows.
 */
internal fun tefilaDayAt(
    civilDay: LocalDate,
    tzais: Date?,
    moment: Long,
): LocalDate = if (tzais != null && moment >= tzais.time) civilDay.plusDays(1) else civilDay

/** What changes in the day's tefila: the season's words, tachanun, hallel and the day's additions. */
internal object TefilaWidget : HomeWidget {
    override val id = "tefila"
    override val title = "תפילת היום"
    override val defaultSpan = CellSpan(5, 3)
    override val minSpan = CellSpan(5, 3)
    override val tool = ToolKind.DailyPrayer

    // Seven short lines at most: more room would only spread them apart
    override val maxSpan = CellSpan(6, 3)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val c = Zayit.colors
        // From צאת הכוכבים of the moment shown (now, ticking, unless a zman or a play picks one), tonight's maariv is tomorrow's
        val civilDay = state.shownDate
        val location = state.location
        // Off the UI thread like the zmanim card: on it, this held the Home's first frames (an Optional, as there may be no צאת)
        val tzaisIfAny =
            rememberOffMain(civilDay, location, state.zmanimOpinion, state.inIsrael, tag = "luach.tefilaDay") {
                java.util.Optional.ofNullable(computeZmanimTimes(civilDay, location, state.zmanimOpinion, state.inIsrael).tzais)
            } ?: return WidgetCardLoading(modifier)
        val tzais = tzaisIfAny.orElse(null)
        val now = rememberMinute()
        val moment = state.skyTimeMillis ?: now.time
        val day = tefilaDayAt(civilDay, tzais, moment)
        val inIsrael = state.inIsrael
        val (lines, hebrewDate) = rememberOffMain(day, inIsrael, tag = "luach.tefila") { tefilaOfDay(day, inIsrael) to hebrewDateOf(day, inIsrael) } ?: return WidgetCardLoading(modifier)
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("תפילת היום", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(hebrewDate, fontSize = 11.sp, color = c.textSecondary, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    // Desktop: when the smart siddur is installed; here its fallback page, when the library has a siddur
                    if (rememberSiddurInLibrary() != false) {
                        Text(
                            "פתח סידור",
                            fontSize = 13.sp,
                            color = c.link,
                            maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { state.openTool(ToolKind.Siddur) }.heightIn(min = 32.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // The lines share the card's height, but never further apart than reads as one list
                    val gap = ((maxHeight - LINE_HEIGHT * lines.size) / lines.size).coerceIn(5.dp, 10.dp)
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)) {
                        lines.forEach { LabeledRow(it) }
                    }
                }
            }
        }
    }
}

private val LINE_HEIGHT = 17.dp

@Composable
internal fun LabeledRow(
    line: TefilaLine,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(line.label, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, modifier = Modifier.width(68.dp))
        Text(
            line.value,
            fontSize = 13.sp,
            fontWeight = if (line.special) FontWeight.SemiBold else FontWeight.Normal,
            color = if (line.special) c.accent else c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The coming month's molad, and when Kiddush Levana can be said. */
internal object MoladWidget : HomeWidget {
    override val id = "molad"
    override val title = "מולד וקידוש לבנה"

    // A title and three short lines: the size they fill, no more
    override val defaultSpan = CellSpan(5, 2)
    override val minSpan = CellSpan(5, 2)
    override val tool = ToolKind.Molad
    override val maxSpan = CellSpan(5, 2)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val now = rememberMinute()
        val day = state.shownDate
        val inIsrael = state.inIsrael
        val earliest = state.kiddushLevanaEarliest
        val latest = state.kiddushLevanaLatest
        val molad = rememberOffMain(now.time / 60_000L, day, inIsrael, earliest, latest, tag = "luach.molad") { moladInfo(now, day, inIsrael, earliest, latest) }
        if (molad == null) WidgetCardLoading(modifier) else MoladPanel(molad, now, state.location.timeZone, modifier)
    }
}

@Composable
internal fun MoladPanel(
    molad: LuachMolad,
    now: Date,
    zone: TimeZone,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val moment = remember(zone) { SimpleDateFormat("d.M · HH:mm", Locale.US).apply { timeZone = zone } }
    // Kiddush Levana can be said now: its dates in the accent
    val open = now.after(molad.kiddushLevanaStart)
    PanelCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("מולד חודש ${molad.month}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1)
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                LabeledRow(TefilaLine("מולד", "${molad.weekday} · ${molad.time.ltr()} · ${molad.chalakim} ח׳", special = true))
                LabeledRow(TefilaLine("קידוש לבנה", "מ־ ${moment.format(molad.kiddushLevanaStart).ltr()}", special = open))
                LabeledRow(TefilaLine("", "עד ${moment.format(molad.kiddushLevanaEnd).ltr()}", special = open))
            }
        }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text, fontSize = 11.sp, color = Zayit.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** One line of text, as large as fits between [min] and [max]. */
@Composable
private fun FitText(
    text: String,
    color: Color,
    max: TextUnit,
    min: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontWeight = weight),
        maxLines = 1,
        softWrap = false,
        // Ellipsis would hide the overflow from the autosizer: it would keep the largest size and cut the text
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = min, maxFontSize = max, stepSize = 1.sp),
    )
}

@Composable
private fun TimeRow(
    label: String,
    time: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = fontSize, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(time, fontSize = fontSize, color = c.text, maxLines = 1)
    }
}

/** HH:mm in [zone], kept left-to-right in the Hebrew text. */
@Composable
internal fun rememberClock(zone: TimeZone): (Date) -> String {
    val format = remember(zone) { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone } }
    return remember(format) { { format.format(it).ltr() } }
}

/**
 * Opens a [LibraryPlace] in a new tab (desktop rememberOpenInLibrary); a short notice when the library doesn't have the
 * book. The reader opens at its first line with its end marked, and with [shnayimMikra] each verse twice.
 */
@Composable
internal fun rememberOpenInLibrary(state: HomeWidgetsState, shnayimMikra: Boolean = false): (LibraryPlace) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(state, scope, context, shnayimMikra) {
        { place ->
            scope.launch {
                // Read on tap: the books DB is opened when first needed, never by showing the card
                val opened = runCatching { state.app.libraryStore.repository().resolvePlace(place) }.getOrNull()
                if (opened == null) {
                    Toast.makeText(context, "הספר לא נמצא בספרייה", Toast.LENGTH_SHORT).show()
                } else {
                    state.openPassage(opened.book, opened.lineId, opened.endLineId, shnayimMikra)
                }
            }
        }
    }
}
