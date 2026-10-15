package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.computeShabbatTimes
import io.github.kdroidfilter.seforimapp.android.data.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Port of desktop 0.6.1 features/home/widgets/zmanim/ZmanimWidget.kt: the day's cards (moments half a slot, dual
// cards one, mincha one and a half and only from 443 dp), five columns or four when a card would be under 99 dp, rows
// shared out evenly, titles shortened when they don't fit, and a tap on a time points every widget at it. Android
// adds one rule: under 300 dp (the Jelly Star) a row holds two slots, so the dual cards stay readable.

private const val ZMANIM_LAYOUT_SCALE = 1.5f
internal val ZMANIM_CARD_HEIGHT = 90.dp * ZMANIM_LAYOUT_SCALE
private val ZMANIM_VERTICAL_SPACING = 12.dp * ZMANIM_LAYOUT_SCALE
internal val ZMANIM_HORIZONTAL_SPACING = 12.dp
private val MIN_ZMANIM_CARD_WIDTH = 99.dp
private val MIN_TITLE_WIDTH_WITH_DOT = 72.dp
private val MIN_WIDTH_FOR_EXTRA_CARDS = 443.dp

/** Android: below this width a row holds two slots (desktop's math would leave a moment card some 20 dp wide). */
private val NARROW_ZMANIM_WIDTH = 300.dp
private const val NARROW_ROW_SLOTS = 2f

private data class DayMomentCardData(
    val title: String,
    val titleAbbrev: String? = null,
    val time: String,
    val timeValue: Date?,
    val accentStart: Color,
    val accentEnd: Color,
)

/** One card of the grid: a moment (one time) or a card of two or three labelled times. */
private sealed class ZmanimGridItem {
    data class Moment(val data: DayMomentCardData) : ZmanimGridItem()

    data class Dual(
        val title: String,
        val titleAbbrev: String?,
        val slots: List<Slot>,
        val shabbat: Boolean = false,
    ) : ZmanimGridItem()
}

private data class Slot(val label: String, val labelAbbrev: String?, val time: String, val timeValue: Date?)

/** The zmanim cards of the selected day; tapping one points the Earth, the sky and the solar system at that zman. */
internal object ZmanimWidget : HomeWidget {
    override val id = "zmanim"
    override val title = "זמני היום"
    override val defaultSpan = CellSpan(13, 4)
    override val minSpan = CellSpan(8, 4)
    override val tool = ToolKind.Zmanim

    // Its cards are a fixed height: the grid gives it the rows they need at each width (minRows), never more
    override val maxSpan = CellSpan(HOME_GRID_COLUMNS, 4)

    override fun heightAt(width: Dp): Dp = zmanimGridHeight(width)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val location = state.location
        val timeZone = location.timeZone
        val selectedDate = state.shownDate
        val opinion = state.zmanimOpinion
        val inIsrael = state.inIsrael
        val cityLabel = state.cityLabel
        val zmanimTimes = rememberOffMain(selectedDate, location, opinion, inIsrael, tag = "zmanim.times") { computeZmanimTimes(selectedDate, location, opinion, inIsrael) }
        val shabbatTimes = rememberOffMain(selectedDate, location, opinion, cityLabel, inIsrael, tag = "zmanim.shabbat") { computeShabbatTimes(selectedDate, location, opinion, cityLabel, inIsrael) }
        if (zmanimTimes == null || shabbatTimes == null) return WidgetCardLoading(modifier)
        val timeFormatter = remember(timeZone) { SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = timeZone } }

        fun formatTime(date: Date?): String = date?.let { "⁦${timeFormatter.format(it)}⁩" } ?: ""

        fun slot(label: String, time: Date?, abbrev: String? = null) = Slot(label, abbrev, formatTime(time), time)

        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val showExtraCards = maxWidth >= MIN_WIDTH_FOR_EXTRA_CARDS
            val momentCards =
                listOf(
                    DayMomentCardData("עמוד השחר", "ע״ה", formatTime(zmanimTimes.alosHashachar), zmanimTimes.alosHashachar, Color(0xFF8AB4F8), Color(0xFFC3DAFE)),
                    DayMomentCardData("נץ החמה", "נצ״ח", formatTime(zmanimTimes.sunrise), zmanimTimes.sunrise, Color(0xFFFFCA7A), Color(0xFFFFE0A3)),
                    DayMomentCardData("שקיעת החמה", "שק״ה", formatTime(zmanimTimes.sunset), zmanimTimes.sunset, Color(0xFF9CB9FF), Color(0xFFB6D4FF)),
                )
            val items =
                buildList {
                    momentCards.forEachIndexed { index, card ->
                        add(ZmanimGridItem.Moment(card))
                        if (index == 1) {
                            add(ZmanimGridItem.Dual("סוף זמן ק\"ש", "ס״ז ק\"ש", listOf(slot("מג״א", zmanimTimes.sofZmanShmaMga), slot("גר״א", zmanimTimes.sofZmanShmaGra))))
                            add(ZmanimGridItem.Dual("סוף זמן תפילה", "ס״ז תפ׳", listOf(slot("מג״א", zmanimTimes.sofZmanTfilaMga), slot("גר״א", zmanimTimes.sofZmanTfilaGra))))
                            // Desktop reuses the Tefila card: the day in the start slot, the night in the end one
                            add(ZmanimGridItem.Dual("חצות", null, listOf(slot("היום", zmanimTimes.chatzosHayom), slot("הלילה", zmanimTimes.chatzosLayla))))
                            if (showExtraCards) {
                                add(
                                    ZmanimGridItem.Dual(
                                        "מנחה",
                                        null,
                                        listOf(slot("גדולה", zmanimTimes.minchaGedola), slot("קטנה", zmanimTimes.minchaKetana), slot("פלג", zmanimTimes.plagHamincha)),
                                    ),
                                )
                            }
                        }
                    }
                    add(ZmanimGridItem.Dual("צאת הכוכבים", "צה״כ", listOf(slot("גאונים", zmanimTimes.tzais, "גאונ׳"), slot("ר״ת", zmanimTimes.tzaisRabbeinuTam))))
                    add(ZmanimGridItem.Dual(shabbatTimes.parashaName, null, listOf(slot("כניסה", shabbatTimes.entryTime), slot("יציאה", shabbatTimes.exitTime)), shabbat = true))
                }
            ZmanimCardsGrid(
                items = items,
                width = maxWidth,
                selectedTimeMillis = state.targetTime?.time,
                onSelect = { state.targetTime = Date(it.time) },
                compactMode = !showExtraCards,
                modifier = Modifier.fillMaxWidth().height(zmanimGridHeight(maxWidth)),
            )
        }
    }
}

private val ZmanimGridItem.span: Float
    get() =
        when (this) {
            is ZmanimGridItem.Moment -> 0.5f
            is ZmanimGridItem.Dual -> if (slots.size == 3) 1.5f else 1f
        }

/**
 * Row width in slots: the first row takes items until it reaches [columns], so it is always full,
 * and every row gets that width (5.5 when the Mincha card overflows 5 columns). Leftover space ends the last row.
 */
private fun <T> List<T>.rowCapacity(
    columns: Int,
    span: (T) -> Float,
): Float {
    var filled = 0f
    for (item in this) {
        filled += span(item)
        if (filled >= columns) return filled
    }
    return columns.toFloat()
}

/** Greedily fills rows of [capacity] slots, each item taking its [span]. */
private fun <T> List<T>.toZmanimRows(
    capacity: Float,
    span: (T) -> Float,
): List<List<T>> =
    fold(mutableListOf<MutableList<T>>()) { rows, item ->
        val last = rows.lastOrNull()
        if (last != null && last.sumOf { span(it).toDouble() } + span(item) <= capacity) {
            last += item
        } else {
            rows += mutableListOf(item)
        }
        rows
    }

/** [rowCount] rows of about the same total span, in order. */
internal fun <T> List<T>.balancedRows(
    rowCount: Int,
    span: (T) -> Float,
): List<List<T>> {
    val target = sumOf { span(it).toDouble() } / rowCount
    val rows = mutableListOf(mutableListOf<T>())
    var filled = 0.0
    for (item in this) {
        // A quarter slot of slack keeps a row from closing just short of its share
        if (filled >= target - 0.25 && rows.size < rowCount) {
            rows += mutableListOf<T>()
            filled = 0.0
        }
        rows.last() += item
        filled += span(item)
    }
    return rows
}

/** Five columns while a card keeps [MIN_ZMANIM_CARD_WIDTH], else four. */
private fun zmanimColumns(
    width: Dp,
    itemCount: Int,
): Int {
    val baseColumns = 5.coerceAtMost(itemCount).coerceAtLeast(1)
    if (baseColumns != 5) return baseColumns
    val cardWidth = (width - ZMANIM_HORIZONTAL_SPACING * (baseColumns - 1)) / baseColumns
    return if (cardWidth < MIN_ZMANIM_CARD_WIDTH) 4 else baseColumns
}

/** The slots a row holds at [width]: desktop's, or two on the narrowest phones. */
private fun <T> List<T>.capacityAt(
    width: Dp,
    span: (T) -> Float,
): Float = if (width < NARROW_ZMANIM_WIDTH) NARROW_ROW_SLOTS else rowCapacity(zmanimColumns(width, size), span)

/**
 * The spans of the cards [ZmanimWidget] lays out at [width], in order: first light, sunrise, Shema, Tefila, Chatzot,
 * Mincha (only where it fits), sunset, stars, Shabbat. Only the spans count for the height.
 */
internal fun zmanimCardSpans(width: Dp): List<Float> =
    listOfNotNull(0.5f, 0.5f, 1f, 1f, 1f, 1.5f.takeIf { width >= MIN_WIDTH_FOR_EXTRA_CARDS }, 0.5f, 1f, 1f)

/** The height of the zmanim cards at [width], known before composing them so the widgets beside can match it. */
internal fun zmanimGridHeight(width: Dp): Dp {
    val spans = zmanimCardSpans(width)
    val rows = spans.toZmanimRows(spans.capacityAt(width) { it }) { it }.size.coerceAtLeast(1)
    return ZMANIM_CARD_HEIGHT * rows + ZMANIM_VERTICAL_SPACING * (rows - 1)
}

@Composable
private fun ZmanimCardsGrid(
    items: List<ZmanimGridItem>,
    width: Dp,
    selectedTimeMillis: Long?,
    onSelect: (Date) -> Unit,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
) {
    val capacity = items.capacityAt(width) { it.span }
    // As many rows as the greedy fill needs (the height counts on it), but shared out evenly, so none ends on a hole
    val rows = items.balancedRows(items.toZmanimRows(capacity) { it.span }.size) { it.span }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ZMANIM_VERTICAL_SPACING)) {
        rows.forEach { rowItems ->
            // Width of one slot plus its gap: an item of span n spans n pitches minus one gap, so the row fills
            // the width whatever its item count (two half cards and their gap make exactly one card)
            val pitch = (width + ZMANIM_HORIZONTAL_SPACING) / rowItems.sumOf { it.span.toDouble() }.toFloat()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZMANIM_HORIZONTAL_SPACING)) {
                rowItems.forEach { item ->
                    val itemModifier = Modifier.width(pitch * item.span - ZMANIM_HORIZONTAL_SPACING)
                    when (item) {
                        is ZmanimGridItem.Moment ->
                            DayMomentCard(
                                data = item.data,
                                isSelected = selectedTimeMillis != null && item.data.timeValue?.time == selectedTimeMillis,
                                compactMode = compactMode,
                                modifier = itemModifier,
                                onClick = item.data.timeValue?.let { time -> { onSelect(time) } },
                            )
                        is ZmanimGridItem.Dual ->
                            DualTimeCardContent(item, selectedTimeMillis, onSelect, itemModifier, compactMode)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMomentCard(
    data: DayMomentCardData,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(18.dp)
    val labelColor = c.text.copy(alpha = 0.78f)
    val glassBackground = c.panel.copy(alpha = if (c.isDark) 0.25f else 0.35f)
    Box(
        modifier =
            modifier
                .height(ZMANIM_CARD_HEIGHT)
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .background(glassBackground, shape)
                .border(1.5.dp, if (isSelected) c.accent else c.panelBorder, shape),
    ) {
        if (isSelected) Box(Modifier.fillMaxSize().border(2.5.dp, c.accent, shape))
        Column(
            // Half-width card: a tighter inset keeps abbreviated titles whole
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            CardTitleRow(data.title, data.titleAbbrev, data.accentStart, data.accentEnd, labelColor, compactMode)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                TimeValue(time = data.time, color = c.text, compactMode = compactMode, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Card title with its accent dot; the dot is dropped on cards too narrow to hold both. */
@Composable
private fun CardTitleRow(
    text: String,
    abbreviation: String?,
    dotStart: Color,
    dotEnd: Color,
    color: Color,
    compactMode: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val showDot = maxWidth >= MIN_TITLE_WIDTH_WITH_DOT
        if (showDot) GradientDot(dotStart, dotEnd, size = 11.dp, modifier = Modifier.align(Alignment.CenterStart))
        AdaptiveSingleLineText(
            text = text,
            abbreviation = abbreviation,
            color = color,
            fontSize = if (compactMode) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            // Symmetric inset keeps the title centered without running under the dot
            modifier = Modifier.padding(horizontal = if (showDot) 15.dp else 0.dp),
        )
    }
}

/** The full [text] when it fits on one line, else its [abbreviation] (desktop AdaptiveSingleLineText). */
@Composable
private fun AdaptiveSingleLineText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    abbreviation: String? = null,
) {
    val resolvedAbbrev = abbreviation?.takeIf { it.isNotBlank() }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val maxWidthPx = constraints.maxWidth
        val textMeasurer = rememberTextMeasurer()
        val shouldAbbreviate =
            remember(text, resolvedAbbrev, maxWidthPx, fontSize) {
                if (resolvedAbbrev == null || maxWidthPx == Constraints.Infinity || maxWidthPx <= 0) {
                    false
                } else {
                    val layoutResult =
                        textMeasurer.measure(
                            text = AnnotatedString(text),
                            style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, textAlign = TextAlign.Center),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            constraints = Constraints(maxWidth = maxWidthPx),
                        )
                    layoutResult.didOverflowWidth || layoutResult.hasVisualOverflow
                }
            }
        Text(
            text = if (shouldAbbreviate) resolvedAbbrev ?: text else text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A card of two (or, for mincha, three) labelled times, each its own tap target; the Shabbat one in its premium dress. */
@Composable
private fun DualTimeCardContent(
    item: ZmanimGridItem.Dual,
    selectedTimeMillis: Long?,
    onSelect: (Date) -> Unit,
    modifier: Modifier,
    compactMode: Boolean,
) {
    val c = Zayit.colors
    val accent = c.accent
    val shape = RoundedCornerShape(18.dp)
    val panelBackground = c.panel
    val borderColor = if (item.shabbat) accent else c.panelBorder
    val labelColor = c.text.copy(alpha = 0.78f)
    val accentStart = accent.blendTowards(Color.White, 0.35f)
    val accentEnd = accent.blendTowards(Color.White, 0.55f)
    val dividerColor = borderColor.copy(alpha = 0.7f)
    val isSelected = item.slots.any { selectedTimeMillis != null && it.timeValue?.time == selectedTimeMillis }
    val background: Brush =
        if (item.shabbat) {
            val dark = c.isDark
            Brush.verticalGradient(
                listOf(
                    if (dark) panelBackground.blendTowards(accent.copy(alpha = 0.8f), 0.65f) else panelBackground.blendTowards(Color.White, 0.92f),
                    if (dark) panelBackground.blendTowards(accent, 0.55f) else panelBackground.blendTowards(Color.White, 0.86f),
                    if (dark) panelBackground.blendTowards(accent.copy(alpha = 0.6f), 0.45f) else panelBackground.blendTowards(Color.White, 0.8f),
                ),
            )
        } else {
            Brush.verticalGradient(List(2) { panelBackground.copy(alpha = if (c.isDark) 0.25f else 0.35f) })
        }
    Box(
        modifier =
            modifier
                .height(ZMANIM_CARD_HEIGHT)
                .clip(shape)
                .background(background, shape)
                .border(1.5.dp, if (isSelected) accent else borderColor, shape),
    ) {
        if (item.shabbat) {
            val sheen =
                if (c.isDark) {
                    listOf(Color.White.copy(alpha = 0.14f), Color.Transparent, Color.White.copy(alpha = 0.08f))
                } else {
                    listOf(Color.White.copy(alpha = 0.3f), Color.Transparent, Color.White.copy(alpha = 0.12f))
                }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(sheen)))
        }
        if (isSelected) Box(Modifier.fillMaxSize().border(2.5.dp, accent, shape))
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.Start) {
            CardTitleRow(item.title, item.titleAbbrev, accentStart, accentEnd, labelColor, compactMode)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val labelFontSize = if (compactMode) 10.sp else 12.sp
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    item.slots.forEachIndexed { i, s ->
                        if (i > 0) SlotDivider(dividerColor)
                        TimeSlot(s.label, s.labelAbbrev, s.time, labelColor, labelFontSize, compactMode)
                    }
                }
            }
        }
        // Each time its own tap target, the card's height
        Row(Modifier.fillMaxSize()) {
            item.slots.forEach { s ->
                val time = s.timeValue
                Box(Modifier.weight(1f).fillMaxHeight().then(if (time != null) Modifier.clickable(role = Role.Button) { onSelect(time) } else Modifier))
            }
        }
    }
}

@Composable
private fun RowScope.TimeSlot(
    label: String,
    labelAbbrev: String?,
    time: String,
    labelColor: Color,
    labelFontSize: TextUnit,
    compactMode: Boolean,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AdaptiveSingleLineText(label, labelColor, labelFontSize, FontWeight.Normal, Modifier.fillMaxWidth(), labelAbbrev)
        TimeValue(time = time, color = Zayit.colors.text, compactMode = compactMode, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RowScope.SlotDivider(color: Color) {
    Box(Modifier.fillMaxHeight(0.5f).align(Alignment.CenterVertically).width(1.dp).background(color))
}

/** The hours over the minutes, as desktop's cards set a time. */
@Composable
private fun TimeValue(
    time: String,
    color: Color,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
) {
    val primaryFontSize = if (compactMode) 18.sp else 22.sp
    val secondaryFontSize = if (compactMode) 17.sp else 21.sp
    val parts = remember(time) { splitTimeParts(time) }
    if (parts == null) {
        Text(time, color = color, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, fontSize = primaryFontSize, modifier = modifier)
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(parts.first, color = color, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, fontSize = primaryFontSize, lineHeight = primaryFontSize, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(parts.second, color = color.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, fontSize = secondaryFontSize, lineHeight = secondaryFontSize, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private fun splitTimeParts(time: String): Pair<String, String>? {
    val sanitized = time.replace("⁦", "").replace("⁩", "").trim()
    if (sanitized.isEmpty()) return null
    val parts = sanitized.split(":")
    if (parts.size != 2) return null
    val hours = parts[0].trim()
    val minutes = parts[1].trim()
    if (hours.isEmpty() || minutes.isEmpty()) return null
    return hours to minutes
}

@Composable
internal fun GradientDot(
    colorStart: Color,
    colorEnd: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    Box(
        modifier
            .size(size)
            .background(brush = Brush.radialGradient(listOf(colorStart, colorEnd)), shape = CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
    )
}

internal fun Color.blendTowards(
    target: Color,
    ratio: Float,
): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * r,
        green = green + (target.green - green) * r,
        blue = blue + (target.blue - blue) * r,
        alpha = alpha + (target.alpha - alpha) * r,
    )
}
