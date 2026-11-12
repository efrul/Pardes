package io.github.kdroidfilter.seforimapp.core.study

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.db.Study_plan
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pardes: a plan of daily study (request 19 of docs/DESKTOP_STAGE2.md). The book [bookTitle] under [categoryPath] (its
 * categories' titles, the root first; [bookId] in the library it was made in), studied by the entries of its table of
 * contents at depth [level], [perDay] of them a day, from the one whose headings are [start] (from the book's top
 * level down), from [startDay] on the [weekdays] chosen; when Shabbat is not one of them, Yom Tov is skipped too. At
 * the book's end it starts again if [restart], else it is done.
 */
@Immutable
data class StudyPlan(
    val id: Long = 0,
    val bookId: Long,
    val bookTitle: String,
    val categoryPath: List<String> = emptyList(),
    val level: Int,
    val start: List<String>,
    val perDay: Int = 1,
    val startDay: LocalDate,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val restart: Boolean = true,
)

/**
 * The user's study plans and the days each one's part was marked done, in the user DB (tables study_plan and
 * study_plan_done), so the backup carries them, for the Home's "הלימוד שלי" (features/home/widgets/study/). [revision]
 * goes up on every change.
 */
class StudyPlanStore(
    database: UserSettingsDb,
) {
    private val queries = database.studyPlansQueries

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Every plan, in the order they were made. */
    suspend fun plans(): List<StudyPlan> =
        withContext(Dispatchers.IO) {
            queries.selectAll().executeAsList().map { it.toPlan() }
        }

    /** Saves [plan], a new one (id 0) last; its id. */
    suspend fun save(plan: StudyPlan): Long =
        withContext(Dispatchers.IO) {
            val id =
                queries.transactionWithResult {
                    if (plan.id == 0L) {
                        queries.insert(
                            bookId = plan.bookId,
                            bookTitle = plan.bookTitle,
                            categoryPath = plan.categoryPath.joined(),
                            level = plan.level.toLong(),
                            startPath = plan.start.joined(),
                            perDay = plan.perDay.toLong(),
                            startDay = plan.startDay.toEpochDay(),
                            weekdays = plan.weekdays.toMask(),
                            restart = if (plan.restart) 1L else 0L,
                            createdAt = System.currentTimeMillis(),
                        )
                        queries.lastInsertId().executeAsOne()
                    } else {
                        queries.update(
                            bookId = plan.bookId,
                            bookTitle = plan.bookTitle,
                            categoryPath = plan.categoryPath.joined(),
                            level = plan.level.toLong(),
                            startPath = plan.start.joined(),
                            perDay = plan.perDay.toLong(),
                            startDay = plan.startDay.toEpochDay(),
                            weekdays = plan.weekdays.toMask(),
                            restart = if (plan.restart) 1L else 0L,
                            id = plan.id,
                        )
                        plan.id
                    }
                }
            _revision.update { it + 1 }
            id
        }

    /** Deletes plan [id] and its marks. */
    suspend fun delete(id: Long): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.deleteDoneOf(id)
                queries.delete(id)
            }
            _revision.update { it + 1 }
        }

    /** The plans whose part of [day] was marked done. */
    suspend fun doneOn(day: LocalDate): Set<Long> =
        withContext(Dispatchers.IO) {
            queries.selectDoneOn(day.toEpochDay()).executeAsList().toSet()
        }

    /** Marks plan [planId]'s part of [day] done, or not. */
    suspend fun setDone(
        planId: Long,
        day: LocalDate,
        done: Boolean,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (done) queries.markDone(planId, day.toEpochDay()) else queries.unmarkDone(planId, day.toEpochDay())
            _revision.update { it + 1 }
        }
}

// Between two headings or two categories: a character no title has
private const val SEPARATOR = '\u001F'

private fun List<String>.joined(): String = joinToString(SEPARATOR.toString())

private fun String.parts(): List<String> = if (isEmpty()) emptyList() else split(SEPARATOR)

/** Sunday the lowest bit, Shabbat the highest. */
internal fun Set<DayOfWeek>.toMask(): Long = fold(0L) { mask, day -> mask or (1L shl (day.value % 7)) }

internal fun weekdaysOf(mask: Long): Set<DayOfWeek> = DayOfWeek.entries.filterTo(LinkedHashSet()) { mask and (1L shl (it.value % 7)) != 0L }

private fun Study_plan.toPlan(): StudyPlan =
    StudyPlan(
        id = id,
        bookId = bookId,
        bookTitle = bookTitle,
        categoryPath = categoryPath.parts(),
        level = level.toInt(),
        start = startPath.parts(),
        perDay = perDay.toInt().coerceAtLeast(1),
        startDay = LocalDate.ofEpochDay(startDay),
        weekdays = weekdaysOf(weekdays),
        restart = restart != 0L,
    )
