package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.datetime.toKotlinLocalDate
import java.time.DayOfWeek
import java.time.LocalDate

// Pardes's own: the day's part of a study plan (request 19 of docs/DESKTOP_STAGE2.md), reckoned from the date, never
// stored. A plan studies a book by the entries of its table of contents at one depth, in reading order; a unit is
// known by its headings, so a new library still finds it.

/**
 * An entry of a book's table of contents studied as one unit: its [headings] from the book's top level down, the line
 * it starts on, and the line the next unit (or a heading above it) starts on, null at the book's end; in a category's
 * plan, its book ([bookId], its title first among the headings).
 */
@Immutable
internal data class StudyUnit(
    val tocId: Long,
    val level: Int,
    val headings: List<String>,
    val lineId: Long?,
    val nextLineId: Long? = null,
    val bookId: Long = 0,
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

// Pardes (request 64 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md: "צריך תמיכה גם לימוד
// קטגוריה, קצב לפי כמה סוגי הרמות חלוקה והספק שלא תלוי רק ביום אחד אלא בכמה ימים/ שבוע וכדומה"): a whole category
// studied book after book, a unit by its name in each book (a daf of two amudim, a book), and a part for several days.

/** A book of a plan with its table of contents: its id, its title and its [entries]. */
@Immutable
internal data class PlanBook(
    val id: Long,
    val title: String,
    val entries: List<TocEntry>,
)

/** [amuds] (the Bavli's contents, "דף ב." and "דף ב:") two by two, as a daf is learnt: "דף ב", from its first amud. */
internal fun dafUnits(amuds: List<StudyUnit>): List<StudyUnit> =
    amuds
        .fold(mutableListOf<MutableList<StudyUnit>>()) { dafs, amud ->
            val daf = amud.headings.lastOrNull()?.dafOf()
            val last = dafs.lastOrNull()
            if (last != null &&
                last
                    .first()
                    .headings
                    .lastOrNull()
                    ?.dafOf() == daf
            ) {
                last += amud
            } else {
                dafs += mutableListOf(amud)
            }
            dafs
        }.map { pair ->
            val first = pair.first()
            first.copy(headings = first.headings.dropLast(1) + first.headings.last().dafOf(), nextLineId = pair.last().nextLineId)
        }

// "דף ב." and "דף ב:" are "דף ב"
private fun String.dafOf(): String = trimEnd().trimEnd('.', ':').trimEnd()

/**
 * The units of a book's [entries] by [unitName]: the depth [level] (none), its depth so named, or its amuds two by two
 * ([StudyPlan.DAF_UNIT]); the depth [level] when it has none so named.
 */
internal fun bookUnits(
    entries: List<TocEntry>,
    level: Int,
    unitName: String,
): List<StudyUnit> {
    val levels = studyLevels(entries)
    return when {
        unitName.isEmpty() -> studyUnits(entries, level)
        unitName == DAF -> levels.firstOrNull { it.name.one == AMUD }?.let { dafUnits(studyUnits(entries, it.level)) }.orEmpty()
        else -> levels.firstOrNull { it.name.one == unitName }?.let { studyUnits(entries, it.level) } ?: studyUnits(entries, level)
    }
}

/**
 * A category's units, its [books] one after the other: each book a unit ([StudyPlan.BOOK_UNIT]), or its units by
 * [unitName] ([bookUnits]), its title first among their headings; a book with none so named, one unit.
 */
internal fun categoryUnits(
    books: List<PlanBook>,
    unitName: String,
): List<StudyUnit> =
    books.flatMap { book ->
        val whole = listOf(StudyUnit(tocId = 0, level = 0, headings = listOf(book.title), lineId = null, bookId = book.id))
        if (unitName == BOOK) return@flatMap whole
        val levels = studyLevels(book.entries)
        val named = unitName == DAF && levels.any { it.name.one == AMUD } || levels.any { it.name.one == unitName }
        if (!named) return@flatMap whole
        bookUnits(book.entries, level = -1, unitName).map { it.copy(headings = listOf(book.title) + it.headings, bookId = book.id) }
    }

/**
 * The units a category's [books] can be learnt by: the names of their depths, the most common first, a daf where they
 * go by amud, and a book.
 */
internal fun categoryUnitNames(books: List<PlanBook>): List<UnitName> {
    val names = books.flatMap { book -> studyLevels(book.entries).map { it.name } }
    val byCount =
        names
            .groupingBy { it.one }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { (one, _) -> names.firstOrNull { it.one == one } }
    val daf = UnitName(DAF, "דפים").takeIf { byCount.any { it.one == AMUD } }
    return byCount + listOfNotNull(daf) + UnitName(BOOK, "ספרים")
}

/** A book's units' names to learn by, its depths' and a daf where it goes by amud. */
internal fun bookUnitNames(levels: List<StudyLevel>): List<UnitName> =
    levels.map { it.name } + listOfNotNull(UnitName(DAF, "דפים").takeIf { levels.any { it.name.one == AMUD } })

/** The days a part can be for, as the editor offers them: a day, two, three, a week, two weeks. */
internal val PERIOD_DAYS = listOf(1, 2, 3, 7, 14)

/** "ביום", "ביומיים", "בשלושה ימים", "בשבוע", "בשבועיים". */
internal fun periodLabel(days: Int): String =
    when (days) {
        1 -> "ביום"
        2 -> "ביומיים"
        3 -> "בשלושה ימים"
        7 -> "בשבוע"
        14 -> "בשבועיים"
        else -> "ב־$days ימים"
    }

/** "פרק ביום", "6 סעיפים ביום", "10 פרקים בשבוע". */
internal fun perPeriodLabel(
    perDay: Int,
    name: UnitName,
    periodDays: Int,
): String = if (perDay == 1) "${name.one} ${periodLabel(periodDays)}" else "$perDay ${name.many} ${periodLabel(periodDays)}"

/**
 * The part of [day] belongs to, from 0, in a plan from [startDay] of a part for [periodDays] days: a week or two from
 * Sunday, as a week is learnt, else from [startDay].
 */
internal fun periodIndex(
    startDay: LocalDate,
    day: LocalDate,
    periodDays: Int,
): Int {
    val from = periodAnchor(startDay, periodDays)
    return ((day.toEpochDay() - from.toEpochDay()).coerceAtLeast(0) / periodDays.coerceAtLeast(1)).toInt()
}

/** The first day of the part [day] belongs to ([periodIndex]): where it is marked done. */
internal fun periodStart(
    startDay: LocalDate,
    day: LocalDate,
    periodDays: Int,
): LocalDate =
    if (periodDays <= 1) {
        day
    } else {
        periodAnchor(startDay, periodDays).plusDays(periodIndex(startDay, day, periodDays).toLong() * periodDays)
    }

// Where a plan's parts are counted from: the Sunday of its first week for a week or two, else its first day
private fun periodAnchor(
    startDay: LocalDate,
    periodDays: Int,
): LocalDate = if (periodDays % 7 == 0) startDay.minusDays((startDay.dayOfWeek.value % 7).toLong()) else startDay

private const val DAF = StudyPlan.DAF_UNIT
private const val BOOK = StudyPlan.BOOK_UNIT
private const val AMUD = "עמוד"
