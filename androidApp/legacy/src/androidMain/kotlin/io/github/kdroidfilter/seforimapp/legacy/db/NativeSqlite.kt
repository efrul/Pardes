package io.github.kdroidfilter.seforimapp.legacy.db

/**
 * The SQLite this edition carries (libpardes_sqlite.so, src/main/cpp): Android 4.4's own SQLite (3.7.11) cannot read the
 * library's schema, and requery's, which the main app bundles, is built for API 21. Handles are the native pointers;
 * errors come as [SqliteException]. The methods are registered by JNI_OnLoad (pardes_sqlite.c), which keys them by
 * these names and signatures: change both sides together.
 */
internal object NativeSqlite {
    init {
        System.loadLibrary("pardes_sqlite")
    }

    const val OPEN_READONLY = 0x00000001
    const val OPEN_READWRITE = 0x00000002
    const val OPEN_CREATE = 0x00000004

    const val TYPE_INTEGER = 1
    const val TYPE_FLOAT = 2
    const val TYPE_TEXT = 3
    const val TYPE_BLOB = 4
    const val TYPE_NULL = 5

    /** SQLITE_INTERRUPT: a statement stopped by [interrupt]. */
    const val CODE_INTERRUPT = 9

    @JvmStatic external fun open(path: String, flags: Int): Long

    @JvmStatic external fun close(db: Long)

    /** 0 when [sql] holds no statement (only spaces or a comment). */
    @JvmStatic external fun prepare(db: Long, sql: String): Long

    @JvmStatic external fun finalizeStatement(stmt: Long)

    /** Resets the statement and clears its bindings. */
    @JvmStatic external fun reset(stmt: Long)

    @JvmStatic external fun bindNull(stmt: Long, index: Int)

    @JvmStatic external fun bindLong(stmt: Long, index: Int, value: Long)

    @JvmStatic external fun bindDouble(stmt: Long, index: Int, value: Double)

    @JvmStatic external fun bindText(stmt: Long, index: Int, value: String)

    @JvmStatic external fun bindBlob(stmt: Long, index: Int, value: ByteArray)

    /** true with a row to read, false when the statement is done. */
    @JvmStatic external fun step(stmt: Long): Boolean

    @JvmStatic external fun columnCount(stmt: Long): Int

    @JvmStatic external fun columnType(stmt: Long, index: Int): Int

    @JvmStatic external fun columnLong(stmt: Long, index: Int): Long

    @JvmStatic external fun columnDouble(stmt: Long, index: Int): Double

    @JvmStatic external fun columnText(stmt: Long, index: Int): String?

    @JvmStatic external fun columnBlob(stmt: Long, index: Int): ByteArray?

    @JvmStatic external fun changes(db: Long): Int

    /** Stops what [db] is running, from any thread: its step fails with [CODE_INTERRUPT]. */
    @JvmStatic external fun interrupt(db: Long)

    /** Runs [sql], which may hold several statements, and drops their rows. */
    @JvmStatic external fun exec(db: Long, sql: String)

    @JvmStatic external fun version(): String

    /** [text] in the form the search compares (pardes_match): no HTML, points, cantillation or punctuation. */
    @JvmStatic external fun normalize(text: String): String
}

/** An error of the native SQLite, with its (extended) result code. Built by pardes_sqlite.c: keep the constructor. */
class SqliteException(val code: Int, message: String) : RuntimeException(message) {
    val interrupted: Boolean get() = code and 0xff == NativeSqlite.CODE_INTERRUPT
}
