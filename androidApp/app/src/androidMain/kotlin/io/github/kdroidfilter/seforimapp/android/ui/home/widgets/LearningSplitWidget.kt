package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.VisitKind
import io.github.kdroidfilter.seforimapp.android.ui.components.ZChip
import io.github.kdroidfilter.seforimapp.android.ui.settings.ZDropdown
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.time.LocalDate

// Pardes's own widget (desktop 0.6.1 has none such): "לעולם ישלש אדם שנותיו, שליש במקרא, שליש במשנה, שליש בתלמוד"
// (Kiddushin 30a). How the learning splits between the three, by the books opened (the history) or by the time read
// (data/ReadingTime.kt). A book falls in the part of its place in the library, its root category (a commentary sits
// under the text it explains); the options move a root category to another part, or leave it out.

/** The three parts of learning. */
internal enum class LearningPart(
    val label: String,
) {
    MIKRA("מקרא"),
    MISHNA("משנה"),
    TALMUD("תלמוד"),
}

/** What is counted: each opening of a book in a tab (the history), or the time a book was on screen. */
internal enum class LearningMeasure(
    val label: String,
) {
    OPENS("פתיחות"),
    TIME("זמן קריאה"),
}

/** The days the time read is counted over; the openings have no days (the history keeps a book's last visit only). */
internal enum class LearningPeriod(
    val label: String,
    val since: String,
    val days: Long?,
) {
    WEEK("שבוע", "בשבוע האחרון", 7),
    MONTH("חודש", "בחודש האחרון", 30),
    YEAR("שנה", "בשנה האחרונה", 365),
    ALL("הכל", "מתחילת המדידה", null),
}

internal object LearningSplitSettings : WidgetSettings(LearningSplitWidget.id) {
    val measure by enum(LearningMeasure.OPENS)
    val period by enum(LearningPeriod.MONTH)

    /** "unit=PART" for each unit the user moved ([LearningPart.name], or [NOT_COUNTED]); the others keep their default. */
    val parts by stringSet(emptySet())
}

/** A book of the split: [amount] openings or seconds; it reopens at [lineId], where it was last read. */
@Immutable
internal data class SplitBook(
    val bookId: Long,
    val title: String,
    val amount: Long,
    val lineId: Long?,
)

/** The books counted, by part, the most first. */
@Immutable
internal data class LearningSplit(
    val books: Map<LearningPart, List<SplitBook>>,
) {
    val amounts: Map<LearningPart, Long> = LearningPart.entries.associateWith { part -> books[part].orEmpty().sumOf { it.amount } }
    val total: Long = amounts.values.sum()
}

// The history rows read: far more than anyone's history holds
private const val HISTORY_LIMIT = 100_000

// A unit's saved choice for "not counted"
private const val NOT_COUNTED = "NONE"

// Roots whose children fall in different parts: their units are "root/child"
private val SPLIT_ROOTS = setOf("מדרש")

/**
 * The library's units and the part each falls in by default; any other root (the siddur, dictionaries, reference
 * books, the unsorted ones) isn't counted. Halacha is משנה, the Oral Torah's rulings (the Rambam's משנה תורה); the
 * matters "called פרדס" (Kabbala, Chassidut, Jewish thought, Mussar) are תלמוד, as the Rambam counts them (Talmud
 * Torah 1:12), and so are the responsa, which derive one thing from another.
 */
private val DEFAULT_PARTS: Map<String, LearningPart> =
    mapOf(
        "תנ״ך" to LearningPart.MIKRA,
        "מדרש/אגדה" to LearningPart.MIKRA,
        "משנה" to LearningPart.MISHNA,
        "תוספתא" to LearningPart.MISHNA,
        "מדרש/הלכה" to LearningPart.MISHNA,
        "הלכה" to LearningPart.MISHNA,
        "תלמוד" to LearningPart.TALMUD,
        "שו״ת" to LearningPart.TALMUD,
        "קבלה" to LearningPart.TALMUD,
        "חסידות" to LearningPart.TALMUD,
        "מחשבת ישראל" to LearningPart.TALMUD,
        "ספרי מוסר" to LearningPart.TALMUD,
    )

/** The saved "unit=PART" entries as a map. */
private fun Set<String>.toOverrides(): Map<String, String> = mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

/** The unit of a category path, root first: the root, or root/child under a [SPLIT_ROOTS] root. */
private fun unitOf(path: List<String>): String? {
    val root = path.firstOrNull() ?: return null
    return if (root in SPLIT_ROOTS && path.size > 1) "$root/${path[1]}" else root
}

private fun partOf(
    unit: String,
    overrides: Map<String, String>,
): LearningPart? =
    when (val chosen = overrides[unit]) {
        null -> DEFAULT_PARTS[unit]
        NOT_COUNTED -> null
        else -> LearningPart.entries.find { it.name == chosen } ?: DEFAULT_PARTS[unit]
    }

/** The category titles from the root down to [categoryId]. */
private suspend fun categoryPath(
    repository: SeforimRepository,
    categoryId: Long,
): List<String> {
    val path = ArrayDeque<String>()
    var category = repository.getCategory(categoryId)
    while (category != null) {
        path.addFirst(category.title)
        category = category.parentId?.let { repository.getCategory(it) }
    }
    return path
}

/** The books counted by [measure] (the time over [period]) by part, with the user's [overrides]; each category's unit read once. */
internal suspend fun learningSplit(
    app: ZayitApplication,
    measure: LearningMeasure,
    period: LearningPeriod,
    overrides: Map<String, String>,
): LearningSplit {
    val repository = app.libraryStore.repository()
    // One row per chapter opened, newest first: the first is where the book was last read
    val visits = app.userData.history.query("", HISTORY_LIMIT).filter { it.kind == VisitKind.BOOK && it.bookId != null }.groupBy { it.bookId!! }
    val amounts =
        when (measure) {
            LearningMeasure.OPENS -> visits.mapValues { (_, rows) -> rows.sumOf { it.visitCount } }
            LearningMeasure.TIME -> app.userData.readingTime.totals(period.days?.let { LocalDate.now().toEpochDay() - it + 1 })
        }
    val units = HashMap<Long, String?>()
    val byPart = LearningPart.entries.associateWith { mutableListOf<SplitBook>() }
    for ((bookId, amount) in amounts) {
        if (amount <= 0) continue
        val book = repository.getBookCore(bookId) ?: continue
        val unit = units.getOrPut(book.categoryId) { unitOf(categoryPath(repository, book.categoryId)) } ?: continue
        val part = partOf(unit, overrides) ?: continue
        byPart.getValue(part) += SplitBook(bookId, book.title, amount, visits[bookId]?.first()?.lineId)
    }
    return LearningSplit(byPart.mapValues { (_, books) -> books.sortedByDescending { it.amount } })
}

/** Whole percents of [values] that add up to 100 (the largest remainders rounded up). */
private fun percents(values: List<Long>): List<Int> {
    val total = values.sum()
    if (total == 0L) return values.map { 0 }
    val exact = values.map { it * 100.0 / total }
    val floors = exact.map { it.toInt() }.toMutableList()
    val left = 100 - floors.sum()
    exact.indices
        .sortedByDescending { exact[it] - floors[it] }
        .take(left)
        .forEach { floors[it]++ }
    return floors
}

/** "5 ד׳", "2 ש׳ 10 ד׳": a time read, to the minute (a part of one counts as one). */
private fun timeLabel(seconds: Long): String {
    val minutes = maxOf(1L, (seconds + 59) / 60)
    val hours = minutes / 60
    return when {
        hours == 0L -> "$minutes ד׳"
        minutes % 60 == 0L -> "$hours ש׳"
        else -> "$hours ש׳ ${minutes % 60} ד׳"
    }
}

private fun amountLabel(
    measure: LearningMeasure,
    amount: Long,
): String = if (measure == LearningMeasure.TIME) timeLabel(amount) else amount.toString()

/** "32 פתיחות", "2 ש׳ 10 ד׳": an amount that says what it counts. */
private fun totalLabel(
    measure: LearningMeasure,
    amount: Long,
): String =
    when {
        measure == LearningMeasure.TIME -> timeLabel(amount)
        amount == 1L -> "פתיחה אחת"
        else -> "$amount פתיחות"
    }

/** Each part's shade of the accent: the full colour, then lighter; every part is also named beside its share. */
private fun LearningPart.color(accent: Color): Color =
    accent.copy(
        alpha =
            when (this) {
                LearningPart.MIKRA -> 1f
                LearningPart.MISHNA -> 0.62f
                LearningPart.TALMUD -> 0.34f
            },
    )

// Narrower, the parts go one under the other (the Jelly Star, a phone's half)
private val SIDE_BY_SIDE_WIDTH = 300.dp

/** How the learning splits between מקרא, משנה and תלמוד, against the third each should have; a book opens where it was last read. */
internal object LearningSplitWidget : HomeWidget {
    override val id = "learning_split"
    override val title = "חלוקת הלימוד"
    override val defaultSpan = CellSpan(8, 4)
    override val minSpan = CellSpan(6, 3)
    override val maxSpan = CellSpan(12, 8)
    override val settings get() = LearningSplitSettings

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val app = state.app
        val historyRevision by app.userData.history.revision.collectAsState()
        val timeRevision by app.userData.readingTime.revision.collectAsState()
        val measure = state[LearningSplitSettings.measure]
        val period = state[LearningSplitSettings.period]
        val overrides = state[LearningSplitSettings.parts].toOverrides()
        val split by produceState<LearningSplit?>(null, historyRevision, timeRevision, measure, period, overrides) {
            // No library yet (being installed or replaced): nothing to split
            value = runCatching { learningSplit(app, measure, period, overrides) }.getOrNull() ?: LearningSplit(emptyMap())
        }
        val shown = split ?: return WidgetCardLoading(modifier)
        val open = rememberOpenBookAt(state)
        val c = Zayit.colors
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle(shown, measure, period), fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TitleIconButton(Icons.Outlined.Settings, HOME_WIDGETS_OPTIONS) { state.optionsOpen = LearningSplitWidget }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().weight(1f).padding(end = 8.dp)) {
                    if (shown.total == 0L) {
                        EmptySplit(measure)
                    } else {
                        SplitBody(shown, measure, open = { open(it.bookId, it.lineId) })
                    }
                }
            }
        }
    }

    @Composable
    override fun menuItems(state: HomeWidgetsState) = listOf(WidgetMenuItem(HOME_WIDGETS_OPTIONS) { state.optionsOpen = this })

    @Composable
    override fun Options(state: HomeWidgetsState) = LearningSplitOptions(state)
}

/** "84 פתיחות", "6 ש׳ 20 ד׳ בחודש האחרון". */
private fun subtitle(
    split: LearningSplit,
    measure: LearningMeasure,
    period: LearningPeriod,
): String =
    when (measure) {
        LearningMeasure.OPENS -> totalLabel(measure, split.total)
        LearningMeasure.TIME -> if (split.total == 0L) "זמן קריאה ${period.since}" else "${timeLabel(split.total)} ${period.since}"
    }

@Composable
private fun EmptySplit(measure: LearningMeasure) {
    val text =
        when (measure) {
            LearningMeasure.OPENS -> "פתחו ספרים, והחלוקה תופיע כאן"
            LearningMeasure.TIME -> "זמן הקריאה נמדד בזמן שספר פתוח על המסך. קראו, והחלוקה תופיע כאן"
        }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = Zayit.colors.textSecondary, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/** The bar, then the parts side by side with their books, or one under the other when narrow. */
@Composable
private fun SplitBody(
    split: LearningSplit,
    measure: LearningMeasure,
    open: (SplitBook) -> Unit,
) {
    val accent = Zayit.colors.accent
    val percent = percents(LearningPart.entries.map { split.amounts.getValue(it) }).let { p -> LearningPart.entries.zip(p).toMap() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= SIDE_BY_SIDE_WIDTH
        Column(Modifier.fillMaxSize()) {
            SplitBar(split, percent, accent)
            Spacer(Modifier.height(14.dp))
            if (sideBySide) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LearningPart.entries.forEach { part ->
                        PartColumn(part, percent.getValue(part), split, measure, accent, open, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else {
                FitColumn(Modifier.fillMaxWidth().weight(1f), spacing = 6.dp) {
                    LearningPart.entries.forEach { part -> PartRow(part, percent.getValue(part), split, measure, accent, open) }
                }
            }
        }
    }
}

/**
 * The parts side by side, the first at the start, each as wide as its share, 2 dp apart; ticks under it at the thirds,
 * where an even split would part them.
 */
@Composable
private fun SplitBar(
    split: LearningSplit,
    percent: Map<LearningPart, Int>,
    accent: Color,
) {
    val tick = Zayit.colors.textSecondary
    val description = LearningPart.entries.joinToString(", ") { "${it.label} ${percent.getValue(it)}%" }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            LearningPart.entries.forEach { part ->
                val amount = split.amounts.getValue(part)
                // A sliver still shows: never under 2% of the bar
                if (amount > 0) Box(Modifier.weight(maxOf(amount.toFloat(), split.total * 0.02f)).fillMaxHeight().background(part.color(accent)))
            }
        }
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            for (third in 1..2) {
                val x = size.width * third / 3
                drawLine(tick, Offset(x, 1.dp.toPx()), Offset(x, size.height), 1.5.dp.toPx())
            }
        }
    }
}

/** A part's name and share, its amount, then its books, the most first: as many as fit. */
@Composable
private fun PartColumn(
    part: LearningPart,
    percent: Int,
    split: LearningSplit,
    measure: LearningMeasure,
    accent: Color,
    open: (SplitBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val books = split.books[part].orEmpty()
    Column(modifier) {
        PartName(part, accent)
        Text("$percent%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
        Text(totalLabel(measure, split.amounts.getValue(part)).takeIf { books.isNotEmpty() } ?: "—", fontSize = 11.sp, color = c.textSecondary, maxLines = 1)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = c.divider)
        FitColumn(Modifier.fillMaxWidth().weight(1f)) {
            books.forEach { book ->
                HoverBox(onClick = { open(book) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(book.title, fontSize = 12.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(4.dp))
                        Text(amountLabel(measure, book.amount), fontSize = 11.sp, color = c.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A part on one line when narrow: its name, its share and amount, and its book read most (tapped, it opens). */
@Composable
private fun PartRow(
    part: LearningPart,
    percent: Int,
    split: LearningSplit,
    measure: LearningMeasure,
    accent: Color,
    open: (SplitBook) -> Unit,
) {
    val c = Zayit.colors
    val top = split.books[part].orEmpty().firstOrNull()
    HoverBox(onClick = top?.let { { open(it) } }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PartName(part, accent)
                Text(top?.title ?: "—", fontSize = 11.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$percent%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
                if (top != null) Text(totalLabel(measure, split.amounts.getValue(part)), fontSize = 11.sp, color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

/** The part's dot, in its colour, and its name, in the text's. */
@Composable
private fun PartName(
    part: LearningPart,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(part.color(accent)))
        Spacer(Modifier.width(6.dp))
        Text(part.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Zayit.colors.text, maxLines = 1)
    }
}

/** What is counted and over what time, then each unit of the library with the part it is counted in; saved at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearningSplitOptions(state: HomeWidgetsState) {
    val c = Zayit.colors
    val app = state.app
    val units by produceState(emptyList<String>()) {
        value =
            runCatching {
                val repository = app.libraryStore.repository()
                repository.getRootCategories().flatMap { root ->
                    if (root.title in SPLIT_ROOTS) {
                        repository.getCategoryChildren(root.id).map { "${root.title}/${it.title}" }
                    } else {
                        listOf(root.title)
                    }
                }
            }.getOrDefault(DEFAULT_PARTS.keys.toList())
    }
    val measure = state[LearningSplitSettings.measure]
    val period = state[LearningSplitSettings.period]
    val saved = state[LearningSplitSettings.parts]
    val overrides = saved.toOverrides()
    val choices: List<LearningPart?> = LearningPart.entries + null
    val labels = choices.map { it?.label ?: "לא נספר" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "לעולם ישלש אדם שנותיו: שליש במקרא, שליש במשנה, שליש בתלמוד (קידושין ל.). הסימנים מתחת לפס הם השלישים.",
            fontSize = 12.sp,
            color = c.textSecondary,
        )
        OptionsHeading("מה נספר")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LearningMeasure.entries.forEach { m -> ZChip(m.label, measure == m, { state[LearningSplitSettings.measure] = m }) }
        }
        Text(
            when (measure) {
                LearningMeasure.OPENS -> "כל ספר שנפתח בלשונית נספר פעם אחת, לפי ההיסטוריה."
                LearningMeasure.TIME -> "הזמן שספר פתוח על המסך. אחרי 5 דקות בלי מגע המדידה נעצרת עד המגע הבא."
            },
            fontSize = 12.sp,
            color = c.textSecondary,
        )
        if (measure == LearningMeasure.TIME) {
            OptionsHeading("תקופה")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LearningPeriod.entries.forEach { p -> ZChip(p.label, period == p, { state[LearningSplitSettings.period] = p }) }
            }
        }
        OptionsHeading("שיוך לחלקים")
        Text("כל ספר נספר בחלק של מקומו בספרייה: מפרש יושב תחת הטקסט שהוא מפרש.", fontSize = 12.sp, color = c.textSecondary)
        for (unit in units) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(unit.replace("/", " · "), fontSize = 14.sp, color = c.text, modifier = Modifier.weight(1f))
                ZDropdown(
                    options = labels,
                    selectedIndex = choices.indexOf(partOf(unit, overrides)),
                    onSelect = { i ->
                        val chosen = choices[i]
                        val others = saved.filterNot { it.startsWith("$unit=") }.toSet()
                        state[LearningSplitSettings.parts] = if (chosen == DEFAULT_PARTS[unit]) others else others + "$unit=${chosen?.name ?: NOT_COUNTED}"
                    },
                    modifier = Modifier.width(130.dp),
                )
            }
        }
    }
}

@Composable
private fun OptionsHeading(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Zayit.colors.text, modifier = Modifier.padding(top = 6.dp))
}
