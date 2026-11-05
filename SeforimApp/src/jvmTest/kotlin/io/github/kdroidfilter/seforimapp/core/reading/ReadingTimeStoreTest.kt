package io.github.kdroidfilter.seforimapp.core.reading

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The time read: counted while the book is followed and the user is there, by day, and forgotten with the history. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingTimeStoreTest {
    private val database = UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))

    @Test
    fun `a book read counts until its tab leaves, and stops five minutes after the last input`() =
        runTest {
            var input = 0L
            var day = 20_000L
            val store =
                ReadingTimeStore(
                    database,
                    clock = { testScheduler.currentTime },
                    lastInput = { input },
                    today = { day },
                    io = StandardTestDispatcher(testScheduler),
                )

            // Two minutes read, with an input a minute in
            val first = launch { store.track(7) }
            runCurrent()
            advanceTimeBy(60_000)
            input = testScheduler.currentTime
            advanceTimeBy(60_000)
            first.cancel()
            runCurrent()
            assertEquals(mapOf(7L to 120L), store.totals(null))
            assertEquals(1L, store.revision.value)

            // Left open for an hour with no input: five minutes after the last one (the start) count, the rest not
            day = 20_001L
            val second = launch { store.track(8) }
            runCurrent()
            advanceTimeBy(3_600_000)
            second.cancel()
            runCurrent()
            assertEquals(300L, store.totals(20_001L)[8L])

            // By day, and gone with the history
            assertEquals(mapOf(7L to 120L, 8L to 300L), store.totals(null))
            assertEquals(mapOf(8L to 300L), store.totals(20_001L))
            store.clearAll()
            assertEquals(emptyMap(), store.totals(null))
        }
}
