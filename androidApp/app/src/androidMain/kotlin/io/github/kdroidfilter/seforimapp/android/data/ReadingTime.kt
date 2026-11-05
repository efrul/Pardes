package io.github.kdroidfilter.seforimapp.android.data

import android.os.SystemClock
import android.util.Log
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** When the user last touched the app (MainActivity.onUserInteraction): the reading time stops counting after [IDLE_MS]. */
object LastTouch {
    @Volatile
    var at: Long = SystemClock.elapsedRealtime()
        private set

    fun now() {
        at = SystemClock.elapsedRealtime()
    }
}

// A book left open with no touch (the screen kept on, the phone put down) stops counting this long after the last one
private const val IDLE_MS = 5 * 60_000L

// How often the time read so far is written, so a process killed in the background loses at most this much
private const val TICK_MS = 30_000L

/**
 * How long each book was read, per day, in the user DB (table reading_time, Pardes's own: desktop has no such
 * measure), for the Home's learning split (ui/home/widgets/LearningSplitWidget.kt). The reader [track]s its book
 * while its tab is in front and the app on screen; [revision] bumps when a stretch of reading ends.
 */
class ReadingTimeStore(
    database: UserSettingsDb,
) {
    private val queries = database.readingTimeQueries

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Counts [bookId]'s reading time until cancelled (the tab leaving the front, the app the screen). */
    suspend fun track(bookId: Long) {
        // Coming to the book is a touch: a tab brought back by a key or the session counts from now
        LastTouch.now()
        var from = SystemClock.elapsedRealtime()
        try {
            while (true) {
                delay(TICK_MS)
                from = credit(bookId, from, notify = false)
            }
        } finally {
            withContext(NonCancellable) { credit(bookId, from, notify = true) }
        }
    }

    /** Adds what of the time since [from] is within [IDLE_MS] of the last touch; returns where the next stretch starts. */
    private suspend fun credit(
        bookId: Long,
        from: Long,
        notify: Boolean,
    ): Long {
        val now = SystemClock.elapsedRealtime()
        val activeUntil = minOf(now, LastTouch.at + IDLE_MS)
        val seconds = (activeUntil - from) / 1000
        if (seconds > 0) add(bookId, LocalDate.now().toEpochDay(), seconds)
        if (notify) _revision.update { it + 1 }
        // The idle rest is dropped; a part of a second goes on to the next stretch
        return if (activeUntil < now) now else from + seconds * 1000
    }

    private suspend fun add(
        bookId: Long,
        day: Long,
        seconds: Long,
    ) = withContext(Dispatchers.IO) {
        try {
            queries.transaction {
                queries.insertDay(bookId, day)
                queries.addSeconds(seconds, bookId, day)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Zayit", "Reading time not saved", e)
        }
    }

    /** Seconds read of each book from [fromDay] (an epoch day) on; every day with null. */
    suspend fun totals(fromDay: Long?): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            queries
                .totalsSince(fromDay ?: Long.MIN_VALUE)
                .executeAsList()
                .associate { it.bookId to (it.seconds ?: 0L) }
        }

    suspend fun clearAll(): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteAll()
            _revision.update { it + 1 }
        }
}
