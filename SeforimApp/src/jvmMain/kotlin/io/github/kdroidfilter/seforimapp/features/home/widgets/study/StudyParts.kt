package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.datetime.toKotlinLocalDate
import java.time.DayOfWeek
import java.time.LocalDate

// Pardes's own: the day's part of a study plan (request 19 of docs/DESKTOP_STAGE2.md), reckoned from the date, never
// stored. A plan studies a book by the entries of its table of contents at one depth, in reading order; a unit is
// known by its headings, so a new library still finds it.

/**
 * An entry of a book's table of contents studied as one unit: its [headings] from the book's top level down, the line
 * it starts on, and the line the next unit (or a heading above it) starts on, null at the book's end.
 */
@Immutable
internal data class StudyUnit(
    val tocId: Long,
    val level: Int,
    val headings: List<String>,
    val lineId: Long?,
    val nextLineId: Long? = null,
)

/** What a depth of a table of contents is called: one unit ([one], "פרק") and several ([many], "פרקים"). */
@Immutable
internal data class UnitName(
    val one: String,
    val many: String,
)

/** A depth of a book's table of contents a plan can study by: its [count] entries, each a [name]. */
@Immutable
internal data class StudyLevel(
    val level: Int,
    val name: UnitName,
    val count: Int,
)

/** [entries] in reading order: each heading before the ones under it, siblings in the order the library made them. */
internal fun readingOrder(entries: List<TocEntry>): List<TocEntry> {
    val children = entries.groupBy { it.parentId }.mapValues { (_, list) -> list.sortedBy { it.id } }
    val ids = entries.mapTo(HashSet()) { it.id }
    val order = ArrayList<TocEntry>(entries.size)

    fun visit(entry: TocEntry) {
        order += entry
        children[entry.id]?.forEach(::visit)
    }
    // A root: no parent, or a parent the book does not have
    entries.filter { it.parentId == null || it.parentId !in ids }.sortedBy { it.id }.forEach(::visit)
    return order
}

/** The depths of [entries] worth studying by: those with more than one entry (not the book's own title above all). */
internal fun studyLevels(entries: List<TocEntry>): List<StudyLevel> =
    entries
        .groupBy { it.level }
        .filterValues { it.size > 1 }
        .toSortedMap()
        .map { (level, list) -> StudyLevel(level, unitNameOf(list.map { it.text }), list.size) }

/**
 * The units of [entries] at depth [level], in reading order, each with its headings: its own and those above it, but
 * for a depth with a single entry (the book's own title), which tells nothing.
 */
internal fun studyUnits(
    entries: List<TocEntry>,
    level: Int,
): List<StudyUnit> {
    val byId = entries.associateBy { it.id }
    val single =
        entries
            .groupingBy { it.level }
            .eachCount()
            .filterValues { it == 1 }
            .keys
    val order = readingOrder(entries)
    return order.withIndex().filter { it.value.level == level }.map { (at, entry) ->
        val chain = generateSequence(entry) { it.parentId?.let(byId::get) }.toList().asReversed()
        // Where it ends: at the next heading not under it
        val next = (at + 1 until order.size).firstOrNull { order[it].level <= level }?.let { order[it].lineId }
        StudyUnit(entry.id, entry.level, chain.filter { it.level !in single }.map { it.text.trim() }, entry.lineId, next)
    }
}

/** Where a plan starting at [headings] starts in [units]: that unit, else the first with its own heading, else the first. */
internal fun startIndexOf(
    units: List<StudyUnit>,
    headings: List<String>,
): Int =
    units.indexOfFirst { it.headings == headings }.takeIf { it >= 0 }
        ?: units.indexOfFirst { it.headings.lastOrNull() == headings.lastOrNull() }.takeIf { it >= 0 }
        ?: 0

private val PLURALS =
    mapOf(
        "פרק" to "פרקים",
        "סימן" to "סימנים",
        "סעיף" to "סעיפים",
        "דף" to "דפים",
        "עמוד" to "עמודים",
        "הלכה" to "הלכות",
        "משנה" to "משניות",
        "פסוק" to "פסוקים",
        "מזמור" to "מזמורים",
        "פרשה" to "פרשיות",
        "פרשת" to "פרשיות",
        "מאמר" to "מאמרים",
        "אות" to "אותיות",
        "שער" to "שערים",
        "חלק" to "חלקים",
        "מצוה" to "מצוות",
        "מצווה" to "מצוות",
        "כלל" to "כללים",
        "שאלה" to "שאלות",
        "תשובה" to "תשובות",
        "סוגיא" to "סוגיות",
        "שיחה" to "שיחות",
        "דרוש" to "דרושים",
        "ענין" to "ענינים",
        "עניין" to "עניינים",
        "מסכת" to "מסכתות",
        "ספר" to "ספרים",
        "קונטרס" to "קונטרסים",
        "אגרת" to "אגרות",
        "מכתב" to "מכתבים",
        "הקדמה" to "הקדמות",
    )

// The Bavli's table of contents goes by amud: "דף ב." and "דף ב:"
private fun String.isAmud(): Boolean = trimEnd().let { it.endsWith('.') || it.endsWith(':') }

/** What headings like [texts] are called: their first word, the most common one; an amud for "דף ב." and "דף ב:". */
internal fun unitNameOf(texts: List<String>): UnitName {
    val word =
        texts
            .mapNotNull { text -> text.trim().substringBefore(' ').takeIf { it.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    if (word == "דף" && texts.count { it.isAmud() } * 2 > texts.size) return UnitName("עמוד", "עמודים")
    if (word == null) return UnitName("יחידה", "יחידות")
    return UnitName(word, PLURALS[word] ?: "יחידות")
}

/**
 * Whether [date] is a day of study of a plan learnt on [weekdays]: one of them, and, when Shabbat is not, not Yom Tov
 * either (in Israel or abroad, as [inIsrael]).
 */
internal fun isStudyDay(
    date: LocalDate,
    weekdays: Set<DayOfWeek>,
    inIsrael: Boolean,
): Boolean {
    if (date.dayOfWeek !in weekdays) return false
    if (DayOfWeek.SATURDAY in weekdays) return true
    return !JewishCalendar(date.toKotlinLocalDate(), inIsrael).isYomTovAssurBemelacha
}

/** The days of study from [from] up to [until], [until] left out. */
internal fun studyDaysBetween(
    from: LocalDate,
    until: LocalDate,
    isStudyDay: (LocalDate) -> Boolean,
): Int {
    var count = 0
    var day = from
    while (day < until) {
        if (isStudyDay(day)) count++
        day = day.plusDays(1)
    }
    return count
}

/** A plan's part of a day. */
internal sealed interface DayPart {
    /**
     * The day's units, by their places in the book's ([indexes], a part across the book's end going on from its
     * start), the plan's [day]th day of study (from 1), and how far into the book they reach ([progress], 0 to 1).
     */
    @Immutable
    data class Part(
        val indexes: List<Int>,
        val day: Int,
        val progress: Float,
    ) : DayPart

    /** Not a day of study: a weekday the plan skips, or Yom Tov while it skips Shabbat. */
    data object Rest : DayPart

    /** Before the plan's first day. */
    data object NotYet : DayPart

    /** Past the book's end, the plan not starting again. */
    data object Finished : DayPart
}

/**
 * The part of a day of a plan of [count] units starting at [startIndex], [perDay] a day, after [daysBefore] days of
 * study: on from where they left off, past the book's end from its start again when [restart].
 */
internal fun dayPart(
    startIndex: Int,
    count: Int,
    perDay: Int,
    restart: Boolean,
    daysBefore: Int,
    studyDay: Boolean,
    beforeStart: Boolean = false,
): DayPart {
    if (count <= 0) return DayPart.Finished
    if (beforeStart) return DayPart.NotYet
    if (!studyDay) return DayPart.Rest
    val first = startIndex.toLong() + daysBefore.toLong() * perDay.coerceAtLeast(1)
    if (!restart && first >= count) return DayPart.Finished
    val positions = (0 until perDay.coerceAtLeast(1)).map { first + it }
    val indexes =
        if (restart) {
            positions.map { (it % count).toInt() }.distinct()
        } else {
            positions.filter { it < count }.map { it.toInt() }
        }
    return DayPart.Part(indexes, daysBefore + 1, (indexes.last() + 1).toFloat() / count)
}

/**
 * A part by its headings: "פרק כג", "פרקים כג–כז", "סימן קכח, סעיפים א–ו", "דף ב.–ג:"; across two headings above
 * them, its first and last units in full ("סימן קכח, סעיף ה – סימן קכט, סעיף ב").
 */
internal fun partLabel(units: List<StudyUnit>): String {
    val first = units.firstOrNull() ?: return ""
    val last = units.last()
    if (units.size == 1) return first.headings.joinToString(", ")
    val above = first.headings.dropLast(1)
    if (units.any { it.headings.dropLast(1) != above }) {
        return "${first.headings.joinToString(", ")} – ${last.headings.joinToString(", ")}"
    }
    val prefix = if (above.isEmpty()) "" else above.joinToString(", ") + ", "
    val from = first.headings.last()
    val to = last.headings.last()
    val word = from.substringBefore(' ')
    val sameWord = from.contains(' ') && to.contains(' ') && to.substringBefore(' ') == word
    if (!sameWord) return "$prefix$from – $to"
    val fromRest = from.substringAfter(' ')
    val toRest = to.substringAfter(' ')
    val many = if (from.isAmud()) null else PLURALS[word]
    return "$prefix${many ?: word} $fromRest–$toRest"
}

/** "פרק ביום", "6 סעיפים ביום". */
internal fun perDayLabel(
    perDay: Int,
    name: UnitName,
): String = if (perDay == 1) "${name.one} ביום" else "$perDay ${name.many} ביום"

private val WEEKDAY_LETTERS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

/** The letter of [day], Sunday "א" to Shabbat "ש". */
internal fun weekdayLetter(day: DayOfWeek): String = WEEKDAY_LETTERS[day.value % 7]

/** The days a plan is learnt on, when not every day: "חוץ משבת ויום טוב", "בימים א, ג, ה, חוץ מיום טוב". */
internal fun weekdaysLabel(weekdays: Set<DayOfWeek>): String? {
    if (weekdays.size == DayOfWeek.entries.size) return null
    if (weekdays == DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY) return "חוץ משבת ויום טוב"
    val days = weekdays.sortedBy { it.value % 7 }.joinToString(", ") { weekdayLetter(it) }
    return if (DayOfWeek.SATURDAY in weekdays) "בימים $days" else "בימים $days, חוץ מיום טוב"
}
