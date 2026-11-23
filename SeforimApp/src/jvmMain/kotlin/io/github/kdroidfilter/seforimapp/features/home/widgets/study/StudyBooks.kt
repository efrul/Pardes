package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

// Pardes (request 64 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md: "צריך תמיכה גם לימוד
// קטגוריה, קצב לפי כמה סוגי הרמות חלוקה והספק שלא תלוי רק ביום אחד אלא בכמה ימים/ שבוע וכדומה"): a whole category
// studied book after book, a unit by its name in each book (a daf of two amudim, a book); a part for several days is
// StudyPeriods.kt's.

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

private const val DAF = StudyPlan.DAF_UNIT
private const val BOOK = StudyPlan.BOOK_UNIT
private const val AMUD = "עמוד"
