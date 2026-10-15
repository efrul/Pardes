package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.Dictionary
import io.github.kdroidfilter.seforimapp.android.data.JewishDay
import io.github.kdroidfilter.seforimapp.android.data.Limud
import io.github.kdroidfilter.seforimapp.android.data.TefilaLine
import io.github.kdroidfilter.seforimapp.android.data.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.android.data.limudOfDay
import io.github.kdroidfilter.seforimapp.android.data.mikraWeek
import io.github.kdroidfilter.seforimapp.android.data.moladInfo
import io.github.kdroidfilter.seforimapp.android.data.tefilaOfDay
import io.github.kdroidfilter.seforimapp.android.data.upcomingEvents
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberMinute
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.tefilaDayAt
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

// The app's Tools menu (desktop core/presentation/components/ToolsMenuButton.kt): the siddur, then the tools in the
// widgets' order. On a tablet the small tools open as a panel at their desktop window's size, the others as a page.

private val ToolKind.icon: ImageVector
    get() =
        when (this) {
            ToolKind.Siddur -> Icons.AutoMirrored.Outlined.MenuBook
            ToolKind.HebrewCalendar -> Icons.Outlined.CalendarMonth
            ToolKind.Dictionary -> Icons.Outlined.Translate
            ToolKind.DailyStudy -> Icons.Outlined.School
            ToolKind.Molad -> Icons.Outlined.NightsStay
            ToolKind.ShnayimMikra -> Icons.Outlined.AutoStories
            ToolKind.DailyPrayer -> Icons.Outlined.WbTwilight
            ToolKind.Measures -> Icons.Outlined.Straighten
            ToolKind.YearCycle -> Icons.Outlined.Public
            ToolKind.Zmanim -> Icons.Outlined.Schedule
        }

/**
 * One line under each tool name: what the tool shows today, from the same data as the tool (computed off the UI
 * thread). Kept for the next opening, so the page comes back with its lines on its first frame. "תפילת היום"'s follows
 * its page's day and moment ([rememberTefilaSubtitle]).
 */
@Composable
private fun rememberToolSubtitles(state: HomeWidgetsState): Map<ToolKind, String> {
    val now = rememberMinute()
    val day = state.todayDate
    val location = state.location
    val opinion = state.zmanimOpinion
    val inIsrael = state.inIsrael
    val earliest = state.kiddushLevanaEarliest
    val latest = state.kiddushLevanaLatest
    val cityLabel = state.cityLabel
    // The place, its label and its region all follow the city
    return rememberToolValue("tools.subtitles", day, state.city, opinion, earliest, latest, now.time / 3_600_000L) {
        fun safe(block: () -> String): String = runCatching(block).getOrDefault("")
        val clock = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = location.timeZone }

        fun time(d: Date?) = d?.let { "⁦${clock.format(it)}⁩" } ?: "--:--"
        val jc = JewishDay.calendar(day, inIsrael)
        mapOf(
            ToolKind.Siddur to safe { (listOf(JewishDay.occasion(jc)) + JewishDay.highlights(jc).take(3)).joinToString(" · ") },
            ToolKind.HebrewCalendar to safe { toolDayLabel(day, inIsrael) },
            ToolKind.Dictionary to Dictionary.entries.joinToString(" · ") { it.label },
            ToolKind.DailyStudy to safe { limudOfDay(day, inIsrael, Limud.defaults.toSet()).joinToString(" · ") { "${it.kicker} ${it.value}" } },
            ToolKind.Molad to safe { moladInfo(now, day, inIsrael, earliest, latest).let { "מולד חודש ${it.month} · ${it.weekday} · ⁦${it.time}⁩" } },
            ToolKind.ShnayimMikra to safe { "שנים מקרא · ${mikraWeek(day, inIsrael).name}" },
            ToolKind.Measures to "אצבע · טפח · אמה · מיל · פרסה",
            ToolKind.YearCycle to
                safe {
                    upcomingEvents(day.plusDays(1), location, opinion, cityLabel, inIsrael, limit = 1, horizonDays = 120)
                        .firstOrNull()
                        ?.let { "הבא: ${it.name} · בעוד ${ChronoUnit.DAYS.between(day, it.date)} ימים" }
                        .orEmpty()
                },
            ToolKind.Zmanim to
                safe {
                    val z = computeZmanimTimes(day, location, opinion, inIsrael)
                    listOfNotNull(cityLabel, "נץ ${time(z.sunrise)}", "שקיעה ${time(z.sunset)}").joinToString(" · ")
                },
        )
    }.orEmpty() + (ToolKind.DailyPrayer to rememberTefilaSubtitle(state, now))
}

/** "תפילת היום"'s line for [civilDay] until its [tzais] ([day]), and from it ([night], the next day's). */
private class TefilaSubtitle(
    val civilDay: LocalDate,
    val tzais: Date?,
    val day: String,
    val night: String,
)

/**
 * "תפילת היום"'s line, the day its page shows (TefilaWidget, with desktop 0.6.2 411280b9): the same day, place, luach
 * and moment, and from their צאת הכוכבים the next day's tefila ([tefilaDayAt]). Both lines of the evening are worked out
 * with that צאת, so the line turns at the minute [now] passes it, as the page does, not at the next hour of the others.
 */
@Composable
private fun rememberTefilaSubtitle(
    state: HomeWidgetsState,
    now: Date,
): String {
    val civilDay = state.shownDate
    val location = state.location
    val opinion = state.zmanimOpinion
    val inIsrael = state.inIsrael
    // The place's location and region follow the city
    val lines =
        rememberToolValue("tools.tefila", civilDay, state.city, opinion) {
            fun line(day: LocalDate) = runCatching { tefilaOfDay(day, inIsrael).take(3).joinToString(" · ") { it.alone() } }.getOrDefault("")
            val tzais = computeZmanimTimes(civilDay, location, opinion, inIsrael).tzais
            TefilaSubtitle(civilDay, tzais, line(civilDay), line(civilDay.plusDays(1)))
        } ?: return ""
    // Until a new day's lines are ready the last ones stay, read with their own day's צאת: never one day's line by another's
    val day = tefilaDayAt(lines.civilDay, lines.tzais, state.skyTimeMillis ?: now.time)
    return if (day == lines.civilDay) lines.day else lines.night
}

/**
 * A tefila line's value as it reads without its label: "אומרים" alone, the third of the line, said nothing. Desktop's
 * siddur names it so in its highlights ("תחנון", "אין תחנון"), as the siddur's own line here does.
 */
private fun TefilaLine.alone(): String =
    when (value) {
        "אומרים" -> label
        "אין אומרים" -> "אין $label"
        else -> value
    }

/** The tools menu: the desktop title-bar list, as a page of large touch rows (a grid on tablets). */
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpen: (ToolKind) -> Unit,
) {
    val c = Zayit.colors
    val state = rememberToolsState()
    val subtitles = rememberToolSubtitles(state)
    var panel by rememberSaveable { mutableStateOf<ToolKind?>(null) }
    // Desktop lists the siddur only when one is installed; here when the library has one (shown until known: most do)
    val hasSiddur = rememberSiddurInLibrary()
    val tools = ToolKind.entries.filter { it != ToolKind.Siddur || hasSiddur != false }
    ToolPage("כלים", onBack) { wide ->
        val open = { tool: ToolKind -> if (opensAsPanel(tool, wide)) panel = tool else onOpen(tool) }
        if (wide) {
            ToolColumn(maxWidth = 1100.dp) {
                tools.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { tool -> ToolTile(tool, subtitles[tool].orEmpty(), { open(tool) }, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            ToolColumn {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.panel)
                        .border(1.dp, c.panelBorder, RoundedCornerShape(14.dp)),
                ) {
                    tools.forEachIndexed { i, tool ->
                        if (i > 0) HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 64.dp))
                        ToolRow(tool, subtitles[tool].orEmpty()) { open(tool) }
                    }
                }
            }
        }
    }
    ToolPanelHost(panel) { panel = null }
}

@Composable
private fun ToolIcon(
    tool: ToolKind,
    size: Int = 40,
) {
    val c = Zayit.colors
    Box(Modifier.size(size.dp).clip(CircleShape).background(c.accentTint), contentAlignment = Alignment.Center) {
        Icon(tool.icon, null, tint = c.accent, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun ToolRow(
    tool: ToolKind,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = Zayit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToolIcon(tool)
        Column(Modifier.weight(1f)) {
            ToolTitle(tool.title, 16.sp)
            ToolSubtitle(subtitle, lines = 1)
        }
        Icon(Icons.Outlined.ChevronLeft, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToolTile(
    tool: ToolKind,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    Column(
        modifier
            .heightIn(min = 132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.panel)
            .border(1.dp, c.panelBorder, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolIcon(tool, 44)
        ToolTitle(tool.title, 17.sp)
        ToolSubtitle(subtitle, lines = 2)
    }
}

/** How far a tool's name shrinks to stay on one line before it takes two instead (a sp at a time). */
private const val TITLE_MIN_SCALE = 0.85f

/**
 * A tool's name, whole: a little smaller if that keeps it on one line, else on two lines at its size. Cut to one line,
 * the Jelly Star's 110dp read "מולד וקידוש ל…" and "שנים מקרא וא…".
 */
@Composable
private fun ToolTitle(
    title: String,
    fontSize: TextUnit,
) {
    val style = LocalTextStyle.current.merge(TextStyle(color = Zayit.colors.text, fontSize = fontSize, fontWeight = FontWeight.Bold))
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        // The largest size down to TITLE_MIN_SCALE that holds it on one line; null when none does
        val oneLine: TextUnit? =
            remember(title, width, style, measurer) {
                generateSequence(fontSize.value) { it - 1f }
                    .takeWhile { it >= fontSize.value * TITLE_MIN_SCALE }
                    .map { it.sp }
                    .firstOrNull { size -> measurer.measure(title, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= width }
            }
        Text(
            title,
            style = if (oneLine != null) style.copy(fontSize = oneLine) else style,
            maxLines = if (oneLine != null) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Between a subtitle's values, as every subtitle joins them: where one too long for its lines is cut. */
private const val SUBTITLE_SEPARATOR = " · "

/**
 * The separator as shown: a no-break space ties the dot to the value before it, so the space after it is the only
 * place the line may break. With plain spaces a line could break before the dot, and a row's second line opened with
 * it ("· מיל · פרסה", "· שקיעה 18:09" at 240dp) while another's first line ended with it.
 */
private const val SHOWN_SEPARATOR = "\u00A0· "

/** After the last value kept, for the ones dropped: held to that value the same way, never alone on a line. */
private const val DROPPED_MARK = "\u00A0…"

private val SubtitleSize = 13.sp

/** Under this many characters' width a row's subtitle takes two lines: one held a value and a half at 240dp. */
private const val NARROW_SUBTITLE_EMS = 14

/**
 * A tool's subtitle in a space of [lines] lines held from the first frame: on the first opening the subtitles are
 * worked out after it, and lines added then moved every title and grew every row ("ריצוד" when the tools open). They
 * fade in instead. A narrow row (the Jelly Star's) gives it two. One longer than its lines keeps its first values
 * whole and drops the rest ([fitValues]); the line's own ellipsis cut a value in the middle ("ותן טל ומטר לברכה ·…").
 */
@Composable
private fun ToolSubtitle(
    subtitle: String,
    lines: Int,
) {
    val shown by animateFloatAsState(if (subtitle.isEmpty()) 0f else 1f, tween(150), label = "toolSubtitle")
    val style = LocalTextStyle.current.merge(TextStyle(color = Zayit.colors.textSecondary, fontSize = SubtitleSize))
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rows = if (maxWidth < with(LocalDensity.current) { (SubtitleSize * NARROW_SUBTITLE_EMS).toDp() }) maxOf(lines, 2) else lines
        val width = constraints.maxWidth
        val text = remember(subtitle, rows, width, style, measurer) { fitValues(subtitle, rows, width, style, measurer) }
        Text(
            // Not empty: a blank line keeps the style's line height
            text.ifEmpty { " " },
            style = style,
            minLines = rows,
            maxLines = rows,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(shown),
        )
    }
}

/**
 * [subtitle] as many of its values as fit in [lines] lines of [width] px, the dropped ones marked by "…", joined by
 * [SHOWN_SEPARATOR] (measured so, as it wraps differently). One whose first value alone is too long is left whole, for
 * the line's own ellipsis.
 */
private fun fitValues(
    subtitle: String,
    lines: Int,
    width: Int,
    style: TextStyle,
    measurer: TextMeasurer,
): String {
    fun fits(text: String) = !measurer.measure(text, style, maxLines = lines, constraints = Constraints(maxWidth = width)).hasVisualOverflow
    // A value of a few words kept on one line when one holds it: at 240dp lines broke inside them ("משיב / הרוח")
    fun tied(value: String) =
        if (' ' in value && measurer.measure(value, style, maxLines = 1, softWrap = false).size.width <= width) value.replace(' ', '\u00A0') else value
    val values = subtitle.split(SUBTITLE_SEPARATOR).map(::tied)
    val whole = values.joinToString(SHOWN_SEPARATOR)
    if (subtitle.isBlank() || width <= 0 || fits(whole)) return whole
    for (count in values.size - 1 downTo 1) {
        val text = values.take(count).joinToString(SHOWN_SEPARATOR) + DROPPED_MARK
        if (fits(text)) return text
    }
    return whole
}

/** One tool as a full page (the desktop opens each as its own window; on a tablet the small ones are panels). */
@Composable
fun ToolScreen(
    tool: ToolKind,
    onBack: () -> Unit,
) {
    ToolContent(tool, onBack)
}
