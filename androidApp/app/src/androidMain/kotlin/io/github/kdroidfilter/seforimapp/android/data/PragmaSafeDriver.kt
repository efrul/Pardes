package io.github.kdroidfilter.seforimapp.android.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * Wraps the Android SQLDelight driver so the shared [io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository]
 * can run unchanged on Android.
 *
 * The repository issues `PRAGMA ...` statements through [SqlDriver.execute]. On Android that maps to
 * `SQLiteStatement.executeUpdateDelete`, which rejects statements that return rows (journal_mode,
 * mmap_size...). PRAGMAs are therefore routed through [SqlDriver.executeQuery], and the desktop-sized
 * cache values are reduced to fit a phone.
 */
class PragmaSafeDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        val trimmed = sql.trim()
        if (!trimmed.startsWith("PRAGMA", ignoreCase = true)) {
            return delegate.execute(identifier, sql, parameters, binders)
        }
        val adjusted = adjustPragma(trimmed) ?: return QueryResult.Value(0L)
        delegate.executeQuery(
            identifier = null,
            sql = adjusted,
            mapper = { cursor: SqlCursor ->
                while (cursor.next().value) {
                    // Drain the result rows of the PRAGMA.
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        return QueryResult.Value(0L)
    }

    private fun adjustPragma(sql: String): String? {
        val lower = sql.lowercase()
        return when {
            lower.startsWith("pragma cache_size") -> "PRAGMA cache_size=-16000"
            lower.startsWith("pragma mmap_size") -> "PRAGMA mmap_size=134217728"
            // WAL needs the -shm/-wal files next to the database, which is fine in app storage.
            else -> sql
        }
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = delegate.addListener(*queryKeys, listener = listener)

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = delegate.removeListener(*queryKeys, listener = listener)
}
