package io.github.kdroidfilter.seforimapp.core.study

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The study plans and their marks, kept in the user DB. */
class StudyPlanStoreTest {
    private val store = StudyPlanStore(UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)))

    private val tehillim =
        StudyPlan(
            bookId = 28,
            bookTitle = "תהילים",
            categoryPath = listOf("תנ״ך", "כתובים"),
            level = 1,
            start = listOf("פרק כג"),
            perDay = 5,
            startDay = LocalDate.of(2026, 11, 12),
            weekdays = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY,
            restart = true,
        )

    @Test
    fun `a plan is kept with every field, a new one last, and changed in place`() =
        runBlocking {
            val first = store.save(tehillim)
            val second = store.save(tehillim.copy(bookId = 3243, bookTitle = "משנה ברורה", start = listOf("סימן קכח"), restart = false))
            assertEquals(
                listOf(
                    tehillim.copy(id = first),
                    tehillim.copy(id = second, bookId = 3243, bookTitle = "משנה ברורה", start = listOf("סימן קכח"), restart = false),
                ),
                store.plans(),
            )

            store.save(tehillim.copy(id = first, perDay = 3, weekdays = setOf(DayOfWeek.SUNDAY)))
            assertEquals(tehillim.copy(id = first, perDay = 3, weekdays = setOf(DayOfWeek.SUNDAY)), store.plans().first())
        }

    @Test
    fun `a day's part is marked done and unmarked, and a plan deleted takes its marks along`() =
        runBlocking {
            val id = store.save(tehillim)
            val other = store.save(tehillim.copy(bookTitle = "משלי"))
            val today = LocalDate.of(2026, 11, 12)
            store.setDone(id, today, true)
            store.setDone(other, today.plusDays(1), true)
            assertEquals(setOf(id), store.doneOn(today))
            store.setDone(id, today, false)
            assertEquals(emptySet(), store.doneOn(today))

            store.delete(other)
            assertEquals(listOf(id), store.plans().map { it.id })
            assertEquals(emptySet(), store.doneOn(today.plusDays(1)))
        }

    @Test
    fun `the weekdays go into one number and back`() {
        val days = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
        assertEquals(0b1001001L, days.toMask())
        assertEquals(days, weekdaysOf(days.toMask()))
        assertEquals(DayOfWeek.entries.toSet(), weekdaysOf(DayOfWeek.entries.toSet().toMask()))
    }
}
