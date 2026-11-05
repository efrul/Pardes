package io.github.kdroidfilter.seforimapp.core.reading

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A clock that only goes on, in milliseconds: the time read is measured on it. */
internal fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

/** When the user last moved the mouse, scrolled, clicked or typed in a window ([noteInput]): reading counts until [IDLE_MS] after. */
object LastInput {
    @Volatile
    var at: Long = monotonicMillis()
        private set

    fun now() {
        at = monotonicMillis()
    }
}

/** Marks the user present on every pointer event the window gets, before anything handles it (it consumes nothing). */
fun Modifier.noteInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                LastInput.now()
            }
        }
    }

// A book left open with nobody at the computer stops counting this long after the last input, as on Android
internal const val IDLE_MS = 5 * 60_000L

// How often the time read so far is written, so that a crash loses at most this much
internal const val TICK_MS = 30_000L

/**
 * How long each book was read, per day, in the user DB (table reading_time), for the Home's learning split
 * (LearningSplitWidget), as androidApp's ReadingTimeStore. A book's tab [track]s it while the tab is in front and its
 * window focused; [revision] goes up when a stretch of reading ends.
 */
class ReadingTimeStore(
    database: UserSettingsDb,
    private val clock: () -> Long = ::monotonicMillis,
    private val lastInput: () -> Long = { LastInput.at },
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val queries = database.readingTimeQueries

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Counts [bookId]'s reading time until cancelled (its tab leaving the front, its window the focus). */
    suspend fun track(bookId: Long) {
        // Coming to the book is an input: a tab brought back by a key or the session counts from now
        val start = clock()
        var from = start
        try {
            while (true) {
                delay(TICK_MS)
                val (seconds, next) = measure(from, start)
                if (seconds > 0) withContext(io) { add(bookId, seconds) }
                from = next
            }
        } finally {
            // The stretch's end, written at once (a single row): a cancelled coroutine waits for no dispatcher
            val (seconds, _) = measure(from, start)
            if (seconds > 0) add(bookId, seconds)
            _revision.update { it + 1 }
        }
    }

    /**
     * The seconds since [from] within [IDLE_MS] of the last input (or of [since], the book's coming), and where the
     * next stretch starts: the idle rest dropped, a part of a second carried on.
     */
    private fun measure(
        from: Long,
        since: Long,
    ): Pair<Long, Long> {
        val now = clock()
        val activeUntil = minOf(now, maxOf(lastInput(), since) + IDLE_MS)
        val seconds = maxOf(0L, (activeUntil - from) / 1000)
        return seconds to if (activeUntil < now) now else from + seconds * 1000
    }

    private fun add(
        bookId: Long,
        seconds: Long,
    ) {
        runCatching { queries.addSeconds(bookId, today(), seconds) }
            .onFailure { warnln(it) { "[ReadingTimeStore] Reading time not saved: ${it.message}" } }
    }

    /** Seconds read of each book from [fromDay] (an epoch day) on; every day with null. */
    suspend fun totals(fromDay: Long?): Map<Long, Long> =
        withContext(io) {
            queries
                .totalsSince(fromDay ?: Long.MIN_VALUE)
                .executeAsList()
                .associate { it.bookId to (it.seconds ?: 0L) }
        }

    /** Forgets every book's time, with the history (HistoryScreen's clearing). */
    suspend fun clearAll(): Unit =
        withContext(io) {
            queries.deleteAll()
            _revision.update { it + 1 }
        }
}
