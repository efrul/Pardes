package io.github.kdroidfilter.seforimapp.legacy.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * SQLDelight's driver over [NativeSqlite], so that the desktop dao (SeforimRepository and its generated queries) runs
 * unchanged on Android 4.4. One connection, used by one thread at a time: the library is opened in exclusive locking
 * mode ([open]), which keeps the WAL index in memory instead of in a mapped -shm file, and so leaves no room for a
 * second connection. A statement is prepared once per query identifier and kept ([STATEMENT_CACHE] of them).
 */
class NativeSqliteDriver private constructor(private var db: Long) : SqlDriver {
    private val lock = ReentrantLock()
    private val listeners = HashMap<String, MutableSet<Query.Listener>>()
    private val statements = object : LinkedHashMap<Int, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>): Boolean {
            if (size <= STATEMENT_CACHE || eldest.value in busy) return false
            NativeSqlite.finalizeStatement(eldest.value)
            return true
        }
    }
    private val transactions = ThreadLocal<Transaction?>()

    /** Runs [block] with the connection to itself, for work SQLDelight has no call for (the search's scan). */
    fun <T> withConnection(block: (Long) -> T): T = lock.withLock { block(checkOpen()) }

    /**
     * Stops the statement running now, from any thread (the search's Cancel). Its caller gets a [SqliteException]
     * whose [SqliteException.interrupted] is true.
     */
    fun interrupt() {
        val handle = db
        if (handle != 0L) NativeSqlite.interrupt(handle)
    }

    private fun checkOpen(): Long = db.takeIf { it != 0L } ?: throw IllegalStateException("database closed")

    /** Cached statements running now: a query run again from inside its own mapper gets a statement of its own. */
    private val busy = HashSet<Long>()

    private fun <T> withStatement(identifier: Int?, sql: String, block: (Long) -> T): T = lock.withLock {
        val handle = checkOpen()
        val cached = identifier?.let { statements[it] }?.takeUnless { it in busy }
        val stmt = cached ?: NativeSqlite.prepare(handle, sql)
        val keep = identifier != null && stmt != 0L && (cached != null || !statements.containsKey(identifier))
        if (keep) busy += stmt
        try {
            block(stmt)
        } finally {
            if (stmt != 0L) {
                if (keep) {
                    busy -= stmt
                    NativeSqlite.reset(stmt)
                    if (cached == null) statements[identifier!!] = stmt
                } else {
                    NativeSqlite.finalizeStatement(stmt)
                }
            }
        }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = withStatement(identifier, sql) { stmt ->
        if (binders != null && stmt != 0L) Binder(stmt).binders()
        mapper(Cursor(stmt))
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = withStatement(identifier, sql) { stmt ->
        if (stmt == 0L) return@withStatement QueryResult.Value(0L)
        if (binders != null) Binder(stmt).binders()
        // A PRAGMA that sets a value answers with a row (journal_mode=WAL gives "wal"): step through them all.
        while (NativeSqlite.step(stmt)) Unit
        QueryResult.Value(NativeSqlite.changes(db).toLong())
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        lock.lock()
        val enclosing = transactions.get()
        val transaction = Transaction(enclosing)
        try {
            if (enclosing == null) NativeSqlite.exec(checkOpen(), "BEGIN IMMEDIATE")
        } catch (e: Throwable) {
            lock.unlock()
            throw e
        }
        transactions.set(transaction)
        return QueryResult.Value(transaction)
    }

    override fun currentTransaction(): Transacter.Transaction? = transactions.get()

    private inner class Transaction(override val enclosingTransaction: Transacter.Transaction?) : Transacter.Transaction() {
        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            try {
                if (enclosingTransaction == null) NativeSqlite.exec(checkOpen(), if (successful) "COMMIT" else "ROLLBACK")
            } finally {
                transactions.set(enclosingTransaction as Transaction?)
                lock.unlock()
            }
            return QueryResult.Unit
        }
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) { queryKeys.forEach { listeners.getOrPut(it) { LinkedHashSet() }.add(listener) } }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) { queryKeys.forEach { listeners[it]?.remove(listener) } }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val toNotify = LinkedHashSet<Query.Listener>()
        synchronized(listeners) { queryKeys.forEach { listeners[it]?.let(toNotify::addAll) } }
        toNotify.forEach { it.queryResultsChanged() }
    }

    override fun close() {
        lock.withLock {
            if (db == 0L) return
            statements.values.forEach(NativeSqlite::finalizeStatement)
            statements.clear()
            NativeSqlite.close(db)
            db = 0L
        }
    }

    /** SQLDelight binds from 0, SQLite from 1. */
    private class Binder(private val stmt: Long) : SqlPreparedStatement {
        override fun bindBytes(index: Int, bytes: ByteArray?) =
            if (bytes == null) NativeSqlite.bindNull(stmt, index + 1) else NativeSqlite.bindBlob(stmt, index + 1, bytes)

        override fun bindLong(index: Int, long: Long?) =
            if (long == null) NativeSqlite.bindNull(stmt, index + 1) else NativeSqlite.bindLong(stmt, index + 1, long)

        override fun bindDouble(index: Int, double: Double?) =
            if (double == null) NativeSqlite.bindNull(stmt, index + 1) else NativeSqlite.bindDouble(stmt, index + 1, double)

        override fun bindString(index: Int, string: String?) =
            if (string == null) NativeSqlite.bindNull(stmt, index + 1) else NativeSqlite.bindText(stmt, index + 1, string)

        override fun bindBoolean(index: Int, boolean: Boolean?) =
            if (boolean == null) NativeSqlite.bindNull(stmt, index + 1) else NativeSqlite.bindLong(stmt, index + 1, if (boolean) 1L else 0L)
    }

    /** Reads the current row of [stmt]; valid only inside the mapper (SqlDriver's contract). */
    private class Cursor(private val stmt: Long) : SqlCursor {
        override fun next(): QueryResult<Boolean> = QueryResult.Value(stmt != 0L && NativeSqlite.step(stmt))

        private fun isNull(index: Int) = NativeSqlite.columnType(stmt, index) == NativeSqlite.TYPE_NULL

        override fun getString(index: Int): String? = NativeSqlite.columnText(stmt, index)

        override fun getLong(index: Int): Long? = if (isNull(index)) null else NativeSqlite.columnLong(stmt, index)

        override fun getBytes(index: Int): ByteArray? = NativeSqlite.columnBlob(stmt, index)

        override fun getDouble(index: Int): Double? = if (isNull(index)) null else NativeSqlite.columnDouble(stmt, index)

        override fun getBoolean(index: Int): Boolean? = getLong(index)?.let { it != 0L }
    }

    companion object {
        private const val STATEMENT_CACHE = 48

        /**
         * Opens [path] read-write (an existing file only) in exclusive locking mode, set before the first read: a
         * WAL database then keeps its index in this process's memory, with no -shm file to map, which the FUSE and FAT
         * storage of Android 4.4 may not allow. The page cache is held to [cacheKib] (the desktop repository asks for
         * 256 MB, more than an old phone's whole share of memory): the caller sets it again after the repository's own
         * PRAGMAs.
         */
        fun open(path: String, cacheKib: Int = DEFAULT_CACHE_KIB): NativeSqliteDriver {
            val handle = NativeSqlite.open(path, NativeSqlite.OPEN_READWRITE)
            try {
                NativeSqlite.exec(handle, "PRAGMA locking_mode=EXCLUSIVE; PRAGMA cache_size=-$cacheKib;")
            } catch (e: Throwable) {
                NativeSqlite.close(handle)
                throw e
            }
            return NativeSqliteDriver(handle)
        }

        const val DEFAULT_CACHE_KIB = 16 * 1024
    }
}
