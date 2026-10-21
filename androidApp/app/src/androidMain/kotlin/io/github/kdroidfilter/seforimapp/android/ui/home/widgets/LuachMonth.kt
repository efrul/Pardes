package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.android.data.hebrewFormatter
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate

// Port of desktop 0.6.7 luach/LuachMonth.kt and LuachPopup.kt: a month of the luach, Hebrew or civil, the Calendar
// widget's and the day picker of the tools, the siddur and the Earth widget. The picker is a bottom sheet on a phone,
// a popup under its anchor on a tablet.

private const val LUACH_PREVIOUS_MONTH = "החודש הקודם"
private const val LUACH_NEXT_MONTH = "החודש הבא"
private const val LUACH_SWAP = "החלפה בין לוח עברי ללוח לועזי"
private const val LUACH_TODAY = "חזרה להיום"

/**
 * A month of the luach, Hebrew or civil ([civil], swapped by [onSwap]), with [selected] marked: its arrows page
 * through the months, a tap on a day picks it.
 */
@Composable
fun LuachMonth(
    selected: LocalDate,
    today: LocalDate,
    inIsrael: Boolean,
    civil: Boolean,
    onSwap: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** Shown as a header button that brings the pick back to today. */
    onToday: (() -> Unit)? = null,
) {
    val c = Zayit.colors
    // Any day of the month on show; follows the date picked elsewhere (a zman card, the Earth orbit)
    var shown by remember(selected) { mutableStateOf(selected) }
    val month = remember(shown, civil, inIsrael) { if (civil) civilMonth(shown, inIsrael) else calendarMonth(shown, inIsrael) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonthHeader(
            month = month,
            onPrevious = { shown = month.first.minusDays(1) },
            onNext = { shown = month.last.plusDays(1) },
            onSwap = onSwap,
            onToday = onToday,
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Holiday names only where a cell can hold a word
            val showTags = maxWidth / 7 >= 56.dp
            // Too narrow for both dates side by side: the Gregorian one goes under the Hebrew one
            val stacked = maxWidth / 7 < 56.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    WEEKDAY_INITIALS.forEach { name ->
                        Text(name, fontSize = 11.sp, color = c.textSecondary, modifier = Modifier.weight(1f).padding(bottom = 4.dp), textAlign = TextAlign.Center)
                    }
                }
                month.weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                isToday = day?.date == today,
                                isSelected = day?.date == selected,
                                showTag = showTags,
                                stacked = stacked,
                                civil = civil,
                                onClick = { day?.let { onSelect(it.date) } },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val WEEKDAY_INITIALS = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")

internal data class CalendarDay(
    val date: LocalDate,
    val hebrewDay: String,
    val gregorianDay: Int,
    /** The festival or Rosh Chodesh of the day, blank on an ordinary one. */
    val tag: String,
    val isShabbat: Boolean,
)

internal data class CalendarMonth(
    val title: String,
    /** Its first and last days in the other calendar. */
    val subtitle: String,
    val first: LocalDate,
    val last: LocalDate,
    /** Sunday-first weeks; null pads the days of the neighbouring months. */
    val weeks: List<List<CalendarDay?>>,
)

/** The Hebrew month that [date] falls in. */
internal fun calendarMonth(
    date: LocalDate,
    inIsrael: Boolean,
): CalendarMonth {
    val anchor = JewishCalendar(date.toKotlinLocalDate(), inIsrael).apply { setJewishDate(jewishYear, jewishMonth, 1) }
    val first = anchor.gregorianLocalDate
    val length = anchor.daysInJewishMonth
    val cells =
        buildList {
            repeat(first.dayOfWeek.isoDayNumber % 7) { add(null) } // Sunday is column 0
            for (offset in 0 until length) {
                val gregorian = first.plus(offset, DateTimeUnit.DAY)
                val day = JewishCalendar(gregorian, inIsrael)
                add(
                    CalendarDay(
                        date = gregorian.toJavaLocalDate(),
                        hebrewDay = hebrewFormatter.formatHebrewNumber(day.jewishDayOfMonth),
                        gregorianDay = gregorian.day,
                        tag = day.tag(),
                        isShabbat = gregorian.dayOfWeek == kotlinx.datetime.DayOfWeek.SATURDAY,
                    ),
                )
            }
            while (size % 7 != 0) add(null)
        }
    val last = first.plus(length - 1, DateTimeUnit.DAY)
    return CalendarMonth(
        title = "${hebrewFormatter.formatMonth(anchor)} ${hebrewFormatter.formatHebrewNumber(anchor.jewishYear)}",
        subtitle = "⁦${first.day}.${first.month.number} – ${last.day}.${last.month.number}.${last.year}⁩",
        first = first.toJavaLocalDate(),
        last = last.toJavaLocalDate(),
        weeks = cells.chunked(7),
    )
}

private val CIVIL_MONTHS = listOf("ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני", "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר")

/** The civil month that [date] falls in, laid out as [calendarMonth] lays out a Hebrew one. */
internal fun civilMonth(
    date: LocalDate,
    inIsrael: Boolean,
): CalendarMonth {
    val first = date.withDayOfMonth(1)
    val last = date.withDayOfMonth(date.lengthOfMonth())
    val cells =
        buildList {
            repeat(first.dayOfWeek.value % 7) { add(null) } // Sunday is column 0
            for (offset in 0L until date.lengthOfMonth()) {
                val gregorian = first.plusDays(offset)
                val day = JewishCalendar(gregorian.toKotlinLocalDate(), inIsrael)
                add(
                    CalendarDay(
                        date = gregorian,
                        hebrewDay = hebrewFormatter.formatHebrewNumber(day.jewishDayOfMonth),
                        gregorianDay = gregorian.dayOfMonth,
                        tag = day.tag(),
                        isShabbat = gregorian.dayOfWeek == java.time.DayOfWeek.SATURDAY,
                    ),
                )
            }
            while (size % 7 != 0) add(null)
        }
    return CalendarMonth(
        title = "${CIVIL_MONTHS[first.monthValue - 1]} ${first.year}",
        subtitle = "${hebrewDayMonth(first, inIsrael)} – ${hebrewDayMonth(last, inIsrael)}",
        first = first,
        last = last,
        weeks = cells.chunked(7),
    )
}

private fun hebrewDayMonth(
    date: LocalDate,
    inIsrael: Boolean,
): String {
    val day = JewishCalendar(date.toKotlinLocalDate(), inIsrael)
    return "${hebrewFormatter.formatHebrewNumber(day.jewishDayOfMonth)} ${hebrewFormatter.formatMonth(day)}"
}

/** The day's yom tov, else its Rosh Chodesh (desktop LuachMonth.tag). */
internal fun JewishCalendar.tag(): String {
    hebrewFormatter.formatYomTov(this).takeIf { it.isNotBlank() }?.let { return it }
    if (isRoshChodesh) return hebrewFormatter.formatRoshChodesh(this).ifBlank { "ראש חודש" }
    return ""
}

@Composable
private fun MonthHeader(
    month: CalendarMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSwap: () -> Unit,
    onToday: (() -> Unit)?,
) {
    val c = Zayit.colors
    // The earlier month sits at the start of the reading direction: on the right
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Outlined.ChevronRight, LUACH_PREVIOUS_MONTH, onPrevious)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(month.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(month.subtitle, fontSize = 11.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onToday?.let { StepButton(Icons.Outlined.MyLocation, LUACH_TODAY, it) }
        StepButton(Icons.Outlined.SwapHoriz, LUACH_SWAP, onSwap)
        StepButton(Icons.Outlined.ChevronLeft, LUACH_NEXT_MONTH, onNext)
    }
}

/** A 40 dp target around desktop's 28 dp step button. */
@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Zayit.colors.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DayCell(
    day: CalendarDay?,
    isToday: Boolean,
    isSelected: Boolean,
    showTag: Boolean,
    stacked: Boolean,
    civil: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val accent = c.accent
    val shape = RoundedCornerShape(8.dp)
    val textColor = c.text
    val background =
        when {
            day == null -> Color.Transparent
            isToday -> accent.copy(alpha = 0.22f)
            day.isShabbat -> textColor.copy(alpha = 0.05f)
            else -> Color.Transparent
        }
    Box(
        modifier
            .padding(1.dp)
            .clip(shape)
            .background(background)
            .then(if (isSelected) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .then(if (day != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        if (day == null) return@Box
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = if (stacked) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            // The day in the month's calendar, then in the other one
            val main = @Composable { rowModifier: Modifier ->
                Text(
                    text = if (civil) day.gregorianDay.toString() else day.hebrewDay,
                    fontSize = 14.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) accent else textColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = rowModifier,
                )
            }
            val other = @Composable {
                Text(if (civil) day.hebrewDay else day.gregorianDay.toString(), fontSize = 9.sp, color = c.textSecondary, maxLines = 1, softWrap = false)
            }
            if (stacked) {
                main(Modifier)
                other()
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    main(Modifier.weight(1f))
                    other()
                }
            }
            if (showTag && day.tag.isNotBlank()) {
                Text(day.tag, fontSize = 9.sp, lineHeight = 11.sp, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (!showTag && day.tag.isNotBlank()) {
                // Phone portrait: no room for the name, a dot tells the day is one
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 1.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            }
        }
    }
}

/**
 * [LuachMonth] to pick a day, Hebrew first: picking a day, or today through [onToday], closes it. A bottom sheet on a
 * phone ([sheet]), a popup under its anchor elsewhere (desktop LuachPopup).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuachPicker(
    selected: LocalDate,
    inIsrael: Boolean,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    sheet: Boolean,
    today: LocalDate = LocalDate.now(),
    onToday: (() -> Unit)? = null,
) {
    val c = Zayit.colors
    var civil by remember { mutableStateOf(false) }
    val month: @Composable (Modifier) -> Unit = { mod ->
        LuachMonth(
            selected = selected,
            today = today,
            inIsrael = inIsrael,
            civil = civil,
            onSwap = { civil = !civil },
            onSelect = {
                onSelect(it)
                onDismiss()
            },
            onToday = onToday?.let { back -> { back(); onDismiss() } },
            modifier = mod,
        )
    }
    if (sheet) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panel) {
            month(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 12.dp).heightIn(max = 460.dp).aspectRatio(0.95f))
        }
    } else {
        Popup(popupPositionProvider = BelowAnchor, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
            val shape = RoundedCornerShape(10.dp)
            month(
                Modifier
                    .size(420.dp, 380.dp)
                    .clip(shape)
                    .background(c.panel)
                    .border(1.dp, c.panelBorder, shape)
                    .padding(12.dp),
            )
        }
    }
}

/** Under the anchor, from its start edge; above it when the window has no room below. Kept inside the window. */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Rtl) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below else anchorBounds.top - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** An outlined button showing [label], the selected day: a tap opens the luach to pick another (desktop LuachDateButton). */
@Composable
fun LuachDateButton(
    label: String,
    selected: LocalDate,
    inIsrael: Boolean,
    onSelect: (LocalDate) -> Unit,
    sheet: Boolean,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    onToday: (() -> Unit)? = null,
    dark: Boolean = false,
) {
    val c = Zayit.colors
    var picking by remember { mutableStateOf(false) }
    val fg = if (dark) Color.White else c.text
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (dark) Color.Black.copy(alpha = 0.55f) else c.panel)
                .border(1.dp, if (dark) Color.White.copy(alpha = 0.35f) else c.panelBorder, RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { picking = !picking }
                .heightIn(min = 36.dp)
                .widthIn(max = 260.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = fg, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = fg, modifier = Modifier.size(16.dp))
        }
        if (picking && isTabInFront()) LuachPicker(selected, inIsrael, onSelect, { picking = false }, sheet, today, onToday)
    }
}
