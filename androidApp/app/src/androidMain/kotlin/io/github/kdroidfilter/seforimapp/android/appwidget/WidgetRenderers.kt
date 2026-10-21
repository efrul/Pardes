// The launcher widgets exist from Android 12 (ZayitWidgets.enqueue, @bool/appwidget_enabled): their drawing uses API 31
@file:RequiresApi(Build.VERSION_CODES.S)

package io.github.kdroidfilter.seforimapp.android.appwidget

import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.compositeOver
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.data.JewishDay
import io.github.kdroidfilter.seforimapp.android.data.Limud
import io.github.kdroidfilter.seforimapp.android.data.LimudItem
import io.github.kdroidfilter.seforimapp.android.data.LuachEvent
import io.github.kdroidfilter.seforimapp.android.data.hebrewFormatter
import io.github.kdroidfilter.seforimapp.android.data.hebrewWeekday
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import java.time.DayOfWeek
import java.util.Date
import kotlin.math.roundToInt

/*
 * The launcher widgets drawn as RemoteViews at one size (in dp). Each mirrors a widget of the Home, itself a port of
 * desktop 0.6.7's (features/home/widgets): the same wording, numbers and colours, laid out for the size the launcher
 * gives, from one row of a small launcher to a tablet's. Rows are nested RemoteViews of equal weight; each column gets
 * as many as fit, padded with blank ones, so two columns stay level and spare room spreads evenly.
 *
 * A launcher row can be much shorter than a widget's layout: about 30 dp on the Jelly Star (16 dp turned sideways),
 * where the cards' lines were cut ("עלות ה…", half a time). Each widget works out the height its layout needs at the
 * user's font size; below it, it is drawn as one line instead ([lineViews], [templeLine]), its text as tall as the
 * row allows and its wording the fullest that fits the width ([fitLine]).
 */

internal fun WidgetKind.render(d: WidgetData, size: SizeF): RemoteViews =
    when (this) {
        WidgetKind.ZMANIM -> zmanimViews(d, size)
        WidgetKind.NEXT_ZMAN -> nextZmanViews(d, size)
        WidgetKind.LUACH -> luachViews(d, size)
        WidgetKind.LIMUD -> limudViews(d, size)
        WidgetKind.TEMPLE -> templeViews(d, size)
        WidgetKind.SEARCH -> searchViews(d, size)
    }

// The layouts' padding above and below, and a title row's height (both in dp, the title's in sp)
private const val PAD = 8f
private const val HEADER = 24f

// A line's height per sp of text (at font size 1): without font padding, Hebrew falls back to Noto Sans Hebrew,
// about 1.36 em; with it, about 1.45 em; digits alone (a clock without padding) are Roboto's 1.17 em
private const val LINE_EM = 1.36f
private const val PADDED_EM = 1.45f
private const val DIGITS_EM = 1.2f

/** No smaller text (sp): a landscape row of 16 dp still holds a line of it. */
private const val MIN_SP = 9f

/** A fuller wording of a one-row widget shrinks down to this (sp, the cards' kicker size) before a shorter one is tried. */
private const val FULLER_MIN_SP = 11f

// The one-line card (appwidget_line): its padding at the sides and the room kept above and below, the gap between its
// label and its value (dp); its text shrinks in steps of this much
private const val LINE_SIDE = 10f
private const val LINE_EDGE = 1.5f
private const val LINE_GAP = 8f
private const val SHRINK_STEP = 0.05f

/** Measured widths are taken this much wider: a bold part, the launcher's own rounding. */
private const val MEASURE_SLACK = 1.05f

/** Desktop's LIMUD_TWO_COLUMNS_WIDTH (LuachWidgets): from this width the limudim go two by two. */
private const val LIMUD_TWO_COLUMNS = 330f

/** Body text by the widget's width: the Jelly Star's two cells, a phone's, a phone's four, a tablet's. */
private enum class Scale(val body: Float) { XS(12f), S(13f), M(14f), L(15f) }

private fun scaleAt(width: Float) =
    when {
        width < 150f -> Scale.XS
        width < 300f -> Scale.S
        width < 450f -> Scale.M
        else -> Scale.L
    }

/** A line of [sp] text with its leading, as the user's font size draws it. */
private fun WidgetData.rowDp(sp: Float) = sp * 1.5f * fontScale

private fun RemoteViews.textSize(id: Int, sp: Float) = setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp)

private fun RemoteViews.visible(id: Int, shown: Boolean) = setViewVisibility(id, if (shown) View.VISIBLE else View.GONE)

private fun bold(text: String): CharSequence =
    SpannableString(text).apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

/** The largest text size up to [max] (sp) whose line ([em] per sp) fits [heightDp] at the user's font size. */
private fun WidgetData.spFor(heightDp: Float, max: Float, em: Float = LINE_EM) = (heightDp / (em * fontScale)).coerceIn(MIN_SP, max)

private val MEDIUM: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

/** How wide [text] is drawn at [sp], in dp (the user's font size included), a little over. */
private fun WidgetData.widthDp(text: CharSequence, sp: Float, bold: Boolean = false): Float {
    if (text.isEmpty()) return 0f
    paint.textSize = sp * fontScale * density
    paint.setTypeface(if (bold) Typeface.DEFAULT_BOLD else MEDIUM)
    return paint.measureText(text, 0, text.length) / density * MEASURE_SLACK
}

/** The first of [texts] that fits [room] (dp) at [sp], else the last (cut at its end). */
private fun WidgetData.fitting(room: Float, sp: Float, texts: List<String>): String =
    texts.firstOrNull { widthDp(it, sp) <= room } ?: texts.last()

// ---- One launcher row ---------------------------------------------------------------------------------------------

/** A wording of a one-row widget: [label] from the start of the line, [value] (a time) at its end. */
private class LineText(val label: CharSequence, val value: CharSequence = "")

private class FittedLine(val text: LineText, val labelSp: Float, val valueSp: Float)

/**
 * The first of [wordings] (fullest first) that fits [room] (dp) at the sizes [height] allows, up to [labelMax] and
 * [valueMax] (sp). A wording shrinks to [FULLER_MIN_SP] before the next is tried; the last to [MIN_SP], then its label
 * is cut (the layout ellipsizes it).
 */
private fun WidgetData.fitLine(wordings: List<LineText>, room: Float, height: Float, labelMax: Float, valueMax: Float): FittedLine {
    val labelTop = spFor(height, labelMax)
    val valueTop = spFor(height, valueMax)
    val steps = ((1f - SHRINK_STEP) / SHRINK_STEP).roundToInt()
    wordings.forEachIndexed { index, text ->
        val floor = if (index == wordings.lastIndex) MIN_SP else FULLER_MIN_SP
        val gap = if (text.value.isEmpty()) 0f else LINE_GAP
        for (step in 0..steps) {
            val k = 1f - step * SHRINK_STEP
            val labelSp = (labelTop * k).coerceAtLeast(MIN_SP)
            val valueSp = (valueTop * k).coerceAtLeast(MIN_SP)
            // Its full size is always tried (a short row's may already be under the floor)
            if (step > 0 && labelSp < floor) break
            val width = widthDp(text.label, labelSp) + gap + widthDp(text.value, valueSp, bold = true)
            if (width <= room) return FittedLine(text, labelSp, valueSp)
        }
    }
    return FittedLine(wordings.last(), MIN_SP, MIN_SP)
}

/** A widget one short launcher row high: the line [fitLine] picks on the panel, its value in the accent; a tap opens [launch]. */
private fun lineViews(
    d: WidgetData,
    size: SizeF,
    wordings: List<LineText>,
    labelMax: Float,
    valueMax: Float,
    launch: WidgetLaunch,
): RemoteViews {
    val p = d.palette
    val v = panel(d, R.layout.appwidget_line)
    val fit = d.fitLine(wordings, size.width - LINE_SIDE * 2, size.height - LINE_EDGE * 2, labelMax, valueMax)
    v.setTextViewText(R.id.aw_line_label, fit.text.label)
    v.textSize(R.id.aw_line_label, fit.labelSp)
    v.textColor(R.id.aw_line_label, p) { it.text }
    v.setTextViewText(R.id.aw_line_value, fit.text.value)
    v.textSize(R.id.aw_line_value, fit.valueSp)
    v.textColor(R.id.aw_line_value, p) { it.accent }
    v.visible(R.id.aw_line_value, fit.text.value.isNotEmpty())
    v.setOnClickPendingIntent(android.R.id.background, launch.pendingIntent(d.app))
    return v
}

/** A layout on the Islands panel (PanelCard, opaque here so it reads on any wallpaper) with its border. */
private fun panel(d: WidgetData, layout: Int): RemoteViews =
    RemoteViews(d.packageName, layout).apply {
        tint(R.id.aw_fill, d.palette) { it.panel }
        tint(R.id.aw_border, d.palette) { it.panelBorder }
    }

private fun blankRow(d: WidgetData) = RemoteViews(d.packageName, R.layout.appwidget_row_zman)

/** [items] down aw_col_a (the right one, RTL), then aw_col_b; [perColumn] rows in each. */
private fun <T> RemoteViews.fillColumns(
    d: WidgetData,
    items: List<T>,
    columns: Int,
    perColumn: Int,
    row: (T) -> RemoteViews,
) {
    val groups = items.chunked(((items.size + columns - 1) / columns).coerceAtLeast(1))
    listOf(R.id.aw_col_a, R.id.aw_col_b).forEachIndexed { index, column ->
        removeAllViews(column)
        visible(column, index < columns)
        if (index < columns) {
            val group = groups.getOrElse(index) { emptyList() }
            group.forEach { addView(column, row(it)) }
            repeat(perColumn - group.size) { addView(column, blankRow(d)) }
        }
    }
    visible(R.id.aw_col_gap, columns > 1)
}

// ---- זמני היום (ZmanimWidget's day, the Zmanim tool's list: ZmanimDay.rows) ------------------------------------------

private class ZmanLine(
    val name: String,
    val time: Date?,
    val next: Boolean = false,
    val past: Boolean = false,
    val emphasized: Boolean = false,
)

// What a small widget keeps when the day doesn't fit, after the next zman and the day's own times (candles, havdala,
// a fast): sunrise, shema, sunset and nightfall first, past ones last
private val ZMAN_TIERS =
    mapOf(
        "הנץ החמה" to 1,
        "סוף זמן ק״ש גר״א" to 1,
        "שקיעה" to 1,
        "צאת הכוכבים" to 1,
        "עלות השחר" to 2,
        "סוף זמן תפילה גר״א" to 2,
        "חצות היום" to 2,
        "סוף זמן ק״ש מג״א" to 3,
        "סוף זמן תפילה מג״א" to 3,
        "מנחה גדולה" to 3,
        "פלג המנחה" to 3,
    )

private fun ZmanLine.priority() = (if (next || emphasized) 0 else ZMAN_TIERS[name] ?: 4) + if (past && !next) 2 else 0

/** Desktop ZmanimWidget's short titles (home_widget_*_title_abbrev), for the narrowest widget. */
private fun abbreviated(name: String) =
    name.replace("סוף זמן ק״ש", "ס״ז ק״ש").replace("סוף זמן תפילה", "ס״ז תפ׳").replace("צאת הכוכבים", "צה״כ")

private fun zmanimViews(d: WidgetData, size: SizeF): RemoteViews {
    val w = size.width
    val h = size.height
    val scale = scaleAt(w)
    val p = d.palette
    val now = d.now
    val rows = d.zmanRows
    val nextIndex = rows.indexOfFirst { it.time?.after(now) == true }
    // Too short for the title over a zman (a Jelly Star row): the next one alone, on one line
    if (h < PAD * 2 + HEADER * d.fontScale + d.rowDp(scale.body)) {
        val next = rows.getOrNull(nextIndex)
        val wordings =
            if (next != null) {
                val time = next.time?.let(d::clock) ?: "--:--"
                listOf(next.title, abbreviated(next.title)).distinct().map { LineText(it, time) }
            } else {
                // The day's last one passed: the widget's name, and where
                listOfNotNull(d.cityLabel?.let { "זמני היום · $it" }, "זמני היום").map { LineText(it) }
            }
        return lineViews(d, size, wordings, labelMax = 14f, valueMax = 17f, launch = WidgetLaunch.Tool(ToolKind.Zmanim))
    }
    val v = panel(d, R.layout.appwidget_zmanim)
    v.textSize(R.id.aw_title, if (scale == Scale.XS) 13f else 14f)
    v.textColor(R.id.aw_title, p) { it.text }
    v.setTextViewText(R.id.aw_subtitle, listOfNotNull(d.cityLabel, d.hebrewDateShort.takeIf { w >= 250f }).joinToString(" · "))
    v.textColor(R.id.aw_subtitle, p) { it.textSecondary }

    val day =
        rows.mapIndexed { i, row ->
            ZmanLine(row.title, row.time, next = i == nextIndex, past = row.time?.before(now) == true, emphasized = row.emphasized)
        }
    val columns = if (w >= 300f) 2 else 1
    val perColumn = ((h - PAD * 2 - HEADER * d.fontScale) / d.rowDp(scale.body)).toInt().coerceAtLeast(1)
    val capacity = perColumn * columns
    val lines =
        if (day.size <= capacity) {
            day + shabbatLines(d, day, capacity - day.size)
        } else {
            // The most needed ones (a coming one before a past one as needed), shown in the day's order
            day.indices.sortedWith(compareBy<Int>({ day[it].priority() }, { day[it].past }, { it })).take(capacity).sorted().map { day[it] }
        }
    v.fillColumns(d, lines, columns, perColumn) { zmanRow(d, it, scale) }
    v.setOnClickPendingIntent(android.R.id.background, WidgetLaunch.Tool(ToolKind.Zmanim).pendingIntent(d.app))
    return v
}

/** Room to spare: the Home's Shabbat card (ZmanimWidget) as rows, unless the day already has its time or it passed. */
private fun shabbatLines(d: WidgetData, day: List<ZmanLine>, room: Int): List<ZmanLine> {
    if (room <= 0) return emptyList()
    val shown = day.mapNotNullTo(HashSet()) { it.time?.time }
    val shabbat = d.shabbat
    val title = shabbat.parashaName
    return listOf("כניסת $title" to shabbat.entryTime, "יציאת $title" to shabbat.exitTime)
        .mapNotNull { (name, time) -> time?.takeIf { it.after(d.now) && it.time !in shown }?.let { ZmanLine(name, it, emphasized = true) } }
        .take(room)
}

/** ZmanimTool's ZmanList row: past ones dimmed, the day's own times in the accent; the next one on the accent's pill. */
private fun zmanRow(d: WidgetData, line: ZmanLine, scale: Scale): RemoteViews {
    val p = d.palette
    val r = RemoteViews(d.packageName, R.layout.appwidget_row_zman)
    val name = if (scale == Scale.XS) abbreviated(line.name) else line.name
    r.setTextViewText(R.id.aw_row_name, if (line.next || line.emphasized) bold(name) else name)
    r.setTextViewText(R.id.aw_row_time, line.time?.let(d::clock) ?: "--:--")
    r.textSize(R.id.aw_row_name, scale.body)
    r.textSize(R.id.aw_row_time, scale.body)
    r.textColor(R.id.aw_row_name, p) {
        when {
            line.next -> it.accent
            line.past -> it.textSecondary
            else -> it.text
        }
    }
    r.textColor(R.id.aw_row_time, p) {
        when {
            line.next || line.emphasized -> it.accent
            line.past -> it.textSecondary
            else -> it.text
        }
    }
    if (line.next) {
        r.visible(R.id.aw_row_pill, true)
        r.tint(R.id.aw_row_pill, p) { it.accentTint.compositeOver(it.panel) }
    }
    return r
}

// ---- הזמן הבא (LuachWidgets NextZmanWidget) --------------------------------------------------------------------------

/** The card's smallest time (sp): below it the kicker and the time don't both fit, and the card is one line. */
private const val NEXT_ZMAN_MIN_TIME = 18f

private fun nextZmanViews(d: WidgetData, size: SizeF): RemoteViews {
    val w = size.width
    val h = size.height
    val p = d.palette
    val zmanim = d.upcomingZmanim
    val next = zmanim.firstOrNull()
    val launch = WidgetLaunch.Tool(ToolKind.Zmanim)
    // A narrow card leaves the name its room (NextZmanWidget's clockSize); a short one (one launcher row: 3 dp above
    // and below) as tall as it leaves under the kicker
    val vertical = if (h < 64f) 3f else 8f
    val timeSp =
        minOf(
            when {
                w < 150f -> 20f
                w < 220f -> 24f
                w < 300f -> 30f
                else -> 36f
            },
            (h - vertical * 2 - 11f * PADDED_EM * d.fontScale) / (DIGITS_EM * d.fontScale),
        )
    // Too short for the kicker over a readable time (a Jelly Star row, ~30 dp): the zman and its time on one line
    if (timeSp < NEXT_ZMAN_MIN_TIME) {
        val name = next?.name ?: "הזמן הבא"
        val time = next?.let { d.clock(it.time) } ?: "--:--"
        val wordings = listOf(name, abbreviated(name)).distinct().map { LineText(it, time) }
        return lineViews(d, size, wordings, labelMax = 15f, valueMax = 20f, launch = launch)
    }
    val v = panel(d, R.layout.appwidget_next_zman)
    // The Home's kicker counts down to it; drawn once, a count would be wrong a minute later: the day instead
    v.setTextViewText(R.id.aw_kicker, if (w >= 140f) "הזמן הבא · ${d.hebrewDateShort}" else "הזמן הבא")
    v.textColor(R.id.aw_kicker, p) { it.textSecondary }
    v.setTextViewText(R.id.aw_name, next?.name ?: "—")
    v.textSize(R.id.aw_name, if (w < 150f) 13f else 15f)
    v.textColor(R.id.aw_name, p) { it.text }
    v.setTextViewText(R.id.aw_time, next?.let { d.clock(it.time) } ?: "--:--")
    v.textSize(R.id.aw_time, timeSp)
    v.textColor(R.id.aw_time, p) { it.accent }

    // One launcher row: less padding, for the kicker and the time to fit the Jelly Star's short cells. Set at every
    // size (the layout's 8 dp otherwise): the launcher reapplies a new size's views onto the same view tree, so a
    // padding set for one row would stay after a resize
    val side = (12 * d.density).roundToInt()
    val edge = (vertical * d.density).roundToInt()
    v.setViewPadding(R.id.aw_content, side, edge, side, edge)

    // The ones after it, as many as fit under it
    val used = PAD * 2 + 16f * d.fontScale + timeSp * 1.25f * d.fontScale + 4f
    val more = if (h >= 100f) ((h - used) / d.rowDp(12f)).toInt().coerceIn(0, (zmanim.size - 1).coerceAtLeast(0)) else 0
    v.removeAllViews(R.id.aw_list)
    v.visible(R.id.aw_list, more > 0)
    zmanim.drop(1).take(more).forEach { zman ->
        val r = RemoteViews(d.packageName, R.layout.appwidget_row_zman)
        r.setTextViewText(R.id.aw_row_name, zman.name)
        r.setTextViewText(R.id.aw_row_time, d.clock(zman.time))
        r.textColor(R.id.aw_row_name, p) { it.textSecondary }
        r.textColor(R.id.aw_row_time, p) { it.text }
        v.addView(R.id.aw_list, r)
    }
    v.setOnClickPendingIntent(android.R.id.background, launch.pendingIntent(d.app))
    return v
}

// ---- לוח עברי (CalendarWidget's day, ZmanimWidget's Shabbat card, LuachWidgets UpcomingEventsWidget) ---------------

/** A short card's padding above and below (dp): the day's number takes the rest. */
private const val LUACH_SHORT_EDGE = 4f

private fun luachViews(d: WidgetData, size: SizeF): RemoteViews {
    val w = size.width
    val h = size.height
    val p = d.palette
    val jc = d.jc
    val today = d.today
    val dayName = if (today.dayOfWeek == DayOfWeek.SATURDAY) "שבת" else "יום ${today.hebrewWeekday()}"
    val omer = if (jc.dayOfOmer != -1) hebrewFormatter.formatOmer(jc) else ""
    val tag = listOf(JewishDay.dayTag(jc), omer).filter { it.isNotBlank() }.joinToString(" · ")
    // Too short for the month over the weekday (a Jelly Star row, ~30 dp): one line
    if (h < LUACH_SHORT_EDGE * 2 + (15f + 12f) * PADDED_EM * d.fontScale) return luachLine(d, size, dayName, tag)
    val wide = w >= 300f && h >= 140f
    val v = panel(d, if (wide) R.layout.appwidget_luach_wide else R.layout.appwidget_luach)
    // One row high: the date alone, the festival on the weekday's line
    val short = h < 90f
    // Tighter when short, the day's number as tall as that leaves. Set at every size (the layout's otherwise): the
    // launcher reapplies a new size's views onto the same view tree
    val edge = if (short) LUACH_SHORT_EDGE else PAD
    if (!wide) {
        val side = (12 * d.density).roundToInt()
        val vertical = (edge * d.density).roundToInt()
        v.setViewPadding(R.id.aw_content, side, vertical, side, vertical)
    }
    val day = hebrewFormatter.formatHebrewNumber(jc.jewishDayOfMonth)
    val daySp = minOf(30f, (h - edge * 2) / (LINE_EM * d.fontScale))
    v.setTextViewText(R.id.aw_day, day)
    v.textSize(R.id.aw_day, daySp)
    v.textColor(R.id.aw_day, p) { it.accent }
    // Beside the number, the fullest month and weekday that fit (the Jelly Star's three cells cut the civil year)
    val pane = if (wide) (w - 24f - 12f) / 2 else w - 24f
    val column = pane - 10f - d.widthDp(day, daySp, bold = true)
    val month = hebrewFormatter.formatMonth(jc)
    v.setTextViewText(R.id.aw_month, d.fitting(column, 15f, listOf("$month ${hebrewFormatter.formatHebrewNumber(jc.jewishYear)}", month)))
    v.textColor(R.id.aw_month, p) { it.text }
    val weekdays =
        listOf(
            "$dayName · ⁦${today.dayOfMonth}.${today.monthValue}.${today.year}⁩",
            "$dayName · ⁦${today.dayOfMonth}.${today.monthValue}⁩",
            dayName,
        )
    // Short: the festival on its line, kept over the civil date
    val weekdayLines = if (short && tag.isNotEmpty()) weekdays.map { "$it · $tag" } + tag else weekdays
    v.setTextViewText(R.id.aw_weekday, d.fitting(column, 12f, weekdayLines))
    v.textColor(R.id.aw_weekday, p) { it.textSecondary }
    v.setTextViewText(R.id.aw_day_tag, tag)
    v.visible(R.id.aw_day_tag, !short && tag.isNotEmpty())
    v.textColor(R.id.aw_day_tag, p) { it.accent }
    v.visible(R.id.aw_shabbat, !short)
    if (!short) {
        val shabbat = d.shabbat
        val entry = shabbat.entryTime?.let(d::clock) ?: "--:--"
        val exit = shabbat.exitTime?.let(d::clock) ?: "--:--"
        v.setTextViewText(R.id.aw_shabbat_name, shabbat.parashaName)
        v.textColor(R.id.aw_shabbat_name, p) { it.accent }
        v.setTextViewText(R.id.aw_shabbat_times, "כניסה $entry · יציאה $exit")
        v.textColor(R.id.aw_shabbat_times, p) { it.text }
        v.tint(R.id.aw_shabbat_pill, p) { it.accentTint.compositeOver(it.panel) }
    }

    // The coming events: beside the date on a wide widget, under the Shabbat on a tall one
    val room = if (wide) h - PAD * 2 - HEADER * d.fontScale else h - PAD * 2 - 130f * d.fontScale
    val capacity = if (wide || h >= 180f) (room / (34f * d.fontScale)).toInt().coerceAtLeast(0) else 0
    val events = if (capacity > 0) d.events.take(capacity) else emptyList()
    v.visible(R.id.aw_events_pane, events.isNotEmpty())
    v.textColor(R.id.aw_events_title, p) { it.text }
    v.removeAllViews(R.id.aw_events)
    if (events.isNotEmpty()) {
        events.forEach { v.addView(R.id.aw_events, eventRow(d, it)) }
        repeat(capacity - events.size) { v.addView(R.id.aw_events, blankRow(d)) }
    }
    v.setOnClickPendingIntent(android.R.id.background, WidgetLaunch.Tool(ToolKind.HebrewCalendar).pendingIntent(d.app))
    return v
}

/**
 * One launcher row: the Hebrew date, the day's festival, the coming Shabbat and its entry (its exit on the day itself:
 * chosen by the day, which the midnight redraw follows), as much of it as fits.
 */
private fun luachLine(d: WidgetData, size: SizeF, dayName: String, tag: String): RemoteViews {
    val shabbat = d.shabbat
    val saturday = d.today.dayOfWeek == DayOfWeek.SATURDAY
    val time = (if (saturday) shabbat.exitTime else shabbat.entryTime)?.let(d::clock)
    val timed = time?.let { "${if (saturday) "יציאה" else "כניסה"} $it" }
    val date = listOf(d.hebrewDateShort, tag).filter { it.isNotEmpty() }.joinToString(" · ")
    val withShabbat = "$date · ${shabbat.parashaName}"
    val wordings =
        buildList {
            if (time != null && timed != null) {
                add(LineText("$dayName · $withShabbat", timed))
                add(LineText(withShabbat, timed))
                add(LineText(withShabbat, time))
            } else {
                add(LineText(withShabbat))
            }
            if (tag.isNotEmpty()) add(LineText(date))
            add(LineText(d.hebrewDateShort))
        }
    return lineViews(d, size, wordings, labelMax = 14f, valueMax = 14f, launch = WidgetLaunch.Tool(ToolKind.HebrewCalendar))
}

/** UpcomingEventsWidget's EventRow. */
private fun eventRow(d: WidgetData, event: LuachEvent): RemoteViews {
    val p = d.palette
    val r = RemoteViews(d.packageName, R.layout.appwidget_row_event)
    r.setTextViewText(R.id.aw_event_name, event.name)
    r.textColor(R.id.aw_event_name, p) { it.text }
    r.setTextViewText(R.id.aw_event_date, "${event.date.hebrewWeekday()} · ${event.hebrewDate} · ⁦${event.date.dayOfMonth}.${event.date.monthValue}⁩")
    r.textColor(R.id.aw_event_date, p) { it.textSecondary }
    val times = event.times.mapNotNull { (label, time) -> time?.let { "$label ${d.clock(it)}" } }
    r.setTextViewText(R.id.aw_event_times, times.joinToString("\n"))
    r.visible(R.id.aw_event_times, times.isNotEmpty())
    r.textColor(R.id.aw_event_times, p) { it.textSecondary }
    return r
}

// ---- לימוד יומי (LuachWidgets LimudPanel) ---------------------------------------------------------------------------

// A limud's row (dp): on the card under its title, and on a short launcher row (the limudim alone, the row's whole
// height: a landscape row of ~16 dp has none to spare for padding)
private const val LIMUD_ROW = 28f
private const val LIMUD_BARE_ROW = 22f
private const val LIMUD_BARE_EDGE = 0f

/** The card's limud text (sp), and the tag's under it. */
private const val LIMUD_SP = 13f

/** The tag's height (dp) beside text of [sp]: its own text, padded, with the padding [limudRow] gives it. */
private fun WidgetData.limudTagDp(sp: Float) = (sp - 2f).coerceAtLeast(MIN_SP) * PADDED_EM * fontScale + if (sp < LIMUD_SP) 2f else 4f

private fun limudViews(d: WidgetData, size: SizeF): RemoteViews {
    val w = size.width
    val h = size.height
    val p = d.palette
    val launch = WidgetLaunch.Tool(ToolKind.DailyStudy)
    // No limud ticked (the Home's options can untick them all): the title, which says what the widget is, rather than
    // a blank panel; on a row too short for it, the title as one line
    if (d.limudim.isEmpty() && h < PAD * 2 + HEADER * d.fontScale) {
        val wordings = listOf(LineText("לימוד יומי · ${d.hebrewDateShort}"), LineText("לימוד יומי"))
        return lineViews(d, size, wordings, labelMax = 14f, valueMax = 14f, launch = launch)
    }
    val v = panel(d, R.layout.appwidget_limud)
    // Too short for the title over a limud (a Jelly Star row, ~30 dp): the limudim alone, as many as fit. Set at every
    // size: the launcher reapplies a new size's views onto the same view tree
    val bare = d.limudim.isNotEmpty() && h < PAD * 2 + (HEADER + LIMUD_ROW) * d.fontScale
    v.visible(R.id.aw_header, !bare)
    v.textColor(R.id.aw_title, p) { it.text }
    v.setTextViewText(R.id.aw_subtitle, d.hebrewDateShort)
    v.visible(R.id.aw_subtitle, w >= 150f)
    v.textColor(R.id.aw_subtitle, p) { it.textSecondary }
    val edge = if (bare) LIMUD_BARE_EDGE else PAD
    val side = (PAD * d.density).roundToInt()
    val vertical = (edge * d.density).roundToInt()
    v.setViewPadding(R.id.aw_content, side, vertical, side, vertical)
    v.setViewLayoutMargin(R.id.aw_columns, RemoteViews.MARGIN_TOP, if (bare) 0f else 2f, TypedValue.COMPLEX_UNIT_DIP)
    val columns = if (w >= LIMUD_TWO_COLUMNS) 2 else 1
    val room = if (bare) h - edge * 2 else h - PAD * 2 - HEADER * d.fontScale
    val perColumn = (room / ((if (bare) LIMUD_BARE_ROW else LIMUD_ROW) * d.fontScale)).toInt().coerceAtLeast(1)
    // As tall as a row allows, the card's size at most
    val rowHeight = room / perColumn
    val sp = d.spFor(rowHeight - 4f, LIMUD_SP, PADDED_EM)
    // A row shorter than the tag at the smallest text (a landscape Jelly Star row, a large font): the reading alone
    val tag = rowHeight >= d.limudTagDp(sp)
    v.fillColumns(d, d.limudim.take(perColumn * columns), columns, perColumn) { (limud, item) ->
        limudRow(d, limud, item, chevron = columns == 1, sp = sp, tag = tag)
    }
    v.setOnClickPendingIntent(android.R.id.background, launch.pendingIntent(d.app))
    return v
}

/**
 * LimudRow: the kind as a tag (when [tag]), the day's reading at [sp]; a line the library has opens its passage in a
 * new tab.
 */
private fun limudRow(d: WidgetData, limud: Limud, item: LimudItem, chevron: Boolean, sp: Float, tag: Boolean): RemoteViews {
    val p = d.palette
    val r = RemoteViews(d.packageName, R.layout.appwidget_row_limud)
    // Without its tag, the reading from the row's start
    r.visible(R.id.aw_kicker_box, tag)
    if (!tag) r.setViewLayoutMargin(R.id.aw_value, RemoteViews.MARGIN_START, 0f, TypedValue.COMPLEX_UNIT_DIP)
    r.setTextViewText(R.id.aw_kicker, item.kicker)
    r.textSize(R.id.aw_kicker, (sp - 2f).coerceAtLeast(MIN_SP))
    r.textColor(R.id.aw_kicker, p) { it.accent }
    r.tint(R.id.aw_kicker_bg, p) { it.accent.copy(alpha = 0.16f).compositeOver(it.panel) }
    r.setTextViewText(R.id.aw_value, item.value)
    r.textSize(R.id.aw_value, sp)
    r.textColor(R.id.aw_value, p) { it.text }
    if (sp < LIMUD_SP) {
        // A short row: the tag hugs its text, the chevron as small as the text (a new row each time, nothing to reset)
        val hug = d.density.roundToInt()
        r.setViewPadding(R.id.aw_kicker, 0, hug, 0, hug)
        r.setViewLayoutWidth(R.id.aw_chevron, sp + 3f, TypedValue.COMPLEX_UNIT_DIP)
        r.setViewLayoutHeight(R.id.aw_chevron, sp + 3f, TypedValue.COMPLEX_UNIT_DIP)
    }
    val opens = item.place != null
    // Left out on two columns, for the text's room
    r.visible(R.id.aw_chevron, opens && chevron)
    r.tint(R.id.aw_chevron, p) { it.textSecondary }
    if (opens) r.setOnClickPendingIntent(R.id.aw_row, WidgetLaunch.LimudPassage(limud, d.today, d.inIsrael).pendingIntent(d.app))
    return r
}

// ---- חורבן הבית (TempleCountdownWidget, its still picture) ----------------------------------------------------------

// The units by tier (the Jelly Star's three cells, a narrow card, a roomy one): the number (sp), its glass's padding
// across and above and below, the gap between units (dp)
private val TEMPLE_NUMBER_SP = floatArrayOf(16f, 18f, 22f)
private val TEMPLE_ACROSS = intArrayOf(5, 8, 16)
private val TEMPLE_EDGE = intArrayOf(4, 6, 8)
private val TEMPLE_GAP = floatArrayOf(2f, 3f, 6f)

/** The dot and its gap before the one-row line (dp). */
private const val TEMPLE_DOT = 15f

/** The height (dp) the units take at [tier]: a number in its glass over its name, in the card's 10 dp padding. */
private fun templeUnitsDp(d: WidgetData, tier: Int) =
    (TEMPLE_NUMBER_SP[tier] + 12f) * PADDED_EM * d.fontScale + TEMPLE_EDGE[tier] * 2 + 2f + 20f

/** What the title (13 sp) and the verse (11 sp, 8 dp under the units) add. */
private fun templeTextsDp(d: WidgetData) = (13f + 11f) * PADDED_EM * d.fontScale + 8f

private fun templeViews(d: WidgetData, size: SizeF): RemoteViews {
    val w = size.width
    val h = size.height
    // The card's compact units below 240 dp (a narrow one can't hold three roomy units), tighter still on the Jelly
    // Star's three cells ("1956" stays on one line); smaller again when the height asks for it, and one line on the
    // picture when even the smallest don't fit (a Jelly Star row, ~30 dp)
    val widest =
        when {
            w < 180f -> 0
            w < 240f -> 1
            else -> 2
        }
    val tier = (widest downTo 0).firstOrNull { templeUnitsDp(d, it) <= h } ?: return templeLine(d, size)
    val v = RemoteViews(d.packageName, R.layout.appwidget_temple)
    v.tint(R.id.aw_border, d.palette) { it.panelBorder }
    val countdown = d.temple
    v.setTextViewText(R.id.aw_years, countdown.years.toString())
    v.setTextViewText(R.id.aw_months, countdown.months.toString())
    v.setTextViewText(R.id.aw_days, countdown.days.toString())
    // The title and the verse when they fit too, else the numbers alone
    val texts = h >= templeUnitsDp(d, tier) + templeTextsDp(d)
    v.visible(R.id.aw_temple_title_row, texts)
    v.visible(R.id.aw_temple_subtitle, texts)
    val horizontal = (TEMPLE_ACROSS[tier] * d.density).roundToInt()
    val vertical = (TEMPLE_EDGE[tier] * d.density).roundToInt()
    listOf(R.id.aw_years, R.id.aw_months, R.id.aw_days).forEach { id ->
        v.textSize(id, TEMPLE_NUMBER_SP[tier])
        v.setViewPadding(id, horizontal, vertical, horizontal, vertical)
    }
    listOf(R.id.aw_unit_years, R.id.aw_unit_months, R.id.aw_unit_days).forEach { id ->
        for (side in intArrayOf(RemoteViews.MARGIN_START, RemoteViews.MARGIN_END)) {
            v.setViewLayoutMargin(id, side, TEMPLE_GAP[tier], TypedValue.COMPLEX_UNIT_DIP)
        }
    }
    v.setOnClickPendingIntent(android.R.id.background, WidgetLaunch.Home.pendingIntent(d.app))
    return v
}

/**
 * One launcher row: the years since, on the picture, with the months and days and "לחורבן הבית" when they fit
 * ("1956 שנים, 2 חודשים ו־3 ימים לחורבן הבית"), the number in bold.
 */
private fun templeLine(d: WidgetData, size: SizeF): RemoteViews {
    val countdown = d.temple
    val v = RemoteViews(d.packageName, R.layout.appwidget_temple_line)
    v.tint(R.id.aw_border, d.palette) { it.panelBorder }
    val years = "${countdown.years} שנים"
    val parts =
        listOfNotNull(
            years,
            when (countdown.months) {
                0 -> null
                1 -> "חודש אחד"
                else -> "${countdown.months} חודשים"
            },
            when (countdown.days) {
                0 -> null
                1 -> "יום אחד"
                else -> "${countdown.days} ימים"
            },
        )
    // "ו־" before a number, "ו" before a word
    val lastPart = parts.last().let { if (it.first().isDigit()) "ו־$it" else "ו$it" }
    val full = if (parts.size == 1) years else parts.dropLast(1).joinToString(", ") + " " + lastPart
    val wordings = listOf("$full לחורבן הבית", full, "$years לחורבן הבית", "$years לחורבן", years).distinct().map { LineText(it) }
    val dot = size.width >= 120f
    val room = size.width - LINE_SIDE * 2 - if (dot) TEMPLE_DOT else 0f
    val fit = d.fitLine(wordings, room, size.height - LINE_EDGE * 2, labelMax = 14f, valueMax = 14f)
    val label = fit.text.label
    val number = countdown.years.toString().length
    v.setTextViewText(
        R.id.aw_line_label,
        SpannableString(label).apply { setSpan(StyleSpan(Typeface.BOLD), 0, minOf(number, length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) },
    )
    v.textSize(R.id.aw_line_label, fit.labelSp)
    v.visible(R.id.aw_temple_dot, dot)
    v.setOnClickPendingIntent(android.R.id.background, WidgetLaunch.Home.pendingIntent(d.app))
    return v
}

// ---- חיפוש בפרדס ---------------------------------------------------------------------------------------------------

// The layout's logo and search icon (dp), and the smallest each is drawn at (the logo is left out under it)
private const val SEARCH_LOGO = 28f
private const val SEARCH_LOGO_MIN = 16f
private const val SEARCH_ICON = 22f
private const val SEARCH_ICON_MIN = 12f

private fun searchViews(d: WidgetData, size: SizeF): RemoteViews {
    val w = size.width
    val h = size.height
    val p = d.palette
    val v = panel(d, R.layout.appwidget_search)
    // Just the bar, its hint and icons as tall as the row allows (a Jelly Star row is ~30 dp, 16 turned sideways). Set
    // at every size: the launcher reapplies a new size's views onto the same view tree
    v.textColor(R.id.aw_hint, p) { it.textSecondary }
    v.textSize(R.id.aw_hint, d.spFor(h - 6f, if (w < 160f) 13f else 15f, PADDED_EM))
    val logo = minOf(SEARCH_LOGO, h - 6f)
    v.visible(R.id.aw_logo, w >= 120f && logo >= SEARCH_LOGO_MIN)
    v.setViewLayoutWidth(R.id.aw_logo, logo.coerceAtLeast(SEARCH_LOGO_MIN), TypedValue.COMPLEX_UNIT_DIP)
    v.setViewLayoutHeight(R.id.aw_logo, logo.coerceAtLeast(SEARCH_LOGO_MIN), TypedValue.COMPLEX_UNIT_DIP)
    val icon = minOf(SEARCH_ICON, h - 6f).coerceAtLeast(SEARCH_ICON_MIN)
    v.setViewLayoutWidth(R.id.aw_search_icon, icon, TypedValue.COMPLEX_UNIT_DIP)
    v.setViewLayoutHeight(R.id.aw_search_icon, icon, TypedValue.COMPLEX_UNIT_DIP)
    v.tint(R.id.aw_search_icon, p) { it.textSecondary }
    // The Home, where the search bar is (with its suggestions and references)
    v.setOnClickPendingIntent(android.R.id.background, WidgetLaunch.Home.pendingIntent(d.app))
    return v
}
