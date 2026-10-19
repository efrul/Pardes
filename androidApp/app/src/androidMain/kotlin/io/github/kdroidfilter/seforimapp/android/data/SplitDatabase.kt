package io.github.kdroidfilter.seforimapp.android.data

import android.database.Cursor
import android.database.CursorWrapper
import android.os.Build
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.kdroidfilter.seforimapp.android.delta.WIDE_WINDOW_BYTES
import io.requery.android.database.CursorWindow
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCursor
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import android.database.sqlite.SQLiteDatabase as FrameworkDatabase
import io.requery.android.database.sqlite.SQLiteDatabase as BundledDatabase

/**
 * A library database kept in chunks, for a FAT32 memory card, which takes no file of 4 GB or more while seforim.db is
 * 8 GB. The chunks are those of SQLite's multiplex VFS (src/test_multiplex.c): a plain split of the file into pieces of
 * [CHUNK_BYTES], the first keeping the database's name and the next ones named `seforim.db001`, `seforim.db002`… They
 * are written by the install and the move ([ChunkedOutputStream]) and read through the multiplex VFS, which the app's
 * own build of SQLite carries (requery sqlite-android with the extension libpardes_multiplex.so, src/androidMain/cpp):
 * Android's SQLite cannot take another VFS. A database in one file never goes through it.
 *
 * Incremental updates do not run on a split database yet: their code opens the database with Android's SQLite.
 *
 * Below Android 9 every library is read through the bundled SQLite, split or not ([usesBundledSqlite]): there Android's
 * cursor window cannot be widened (CursorWindow(String, long) is API 28, and SQLDelight applies windowSizeBytes only from
 * API 28), so a row over its 2 MB (a book's notes reach 4.25 MB) could not be read.
 */
object SplitDatabase {
    /** SQLITE_MULTIPLEX_CHUNK_SIZE: 2 GiB less 64 KiB. The multiplex VFS also finds it from the first chunk's size. */
    const val CHUNK_BYTES = 2_147_418_112L

    private const val EXTENSION = "libpardes_multiplex.so"
    private const val ENTRY_POINT = "sqlite3_multiplex_init"
    private val CHUNK_NUMBER = Regex("""\d{3}""")

    /** Chunk [index] of the database [base]: the name itself, then base001, base002… (multiplexFilename). */
    fun chunkName(base: String, index: Int): String = if (index == 0) base else base + String.format(Locale.ROOT, "%03d", index)

    /** [db]'s overflow chunks that exist, in order (none for a database in one file). */
    fun overflowChunks(db: File): List<File> {
        val dir = db.parentFile ?: return emptyList()
        return generateSequence(1) { it + 1 }.map { File(dir, chunkName(db.name, it)) }.takeWhile { it.isFile }.toList()
    }

    /** [db] and its overflow chunks. */
    fun files(db: File): List<File> = listOf(db) + overflowChunks(db)

    fun isSplit(db: File): Boolean = File(db.path + "001").isFile

    /**
     * Whether the library database [db] is opened with the bundled SQLite rather than Android's: in chunks, or below
     * Android 9 (see the class comment). Every opener of the library asks this (the repository in LibraryStore and
     * [openReader]): two SQLite builds in one process must not open the same file, as each keeps its own POSIX locks and
     * one closing the file drops the other's. The delta code, which opens it with Android's SQLite, does not run where
     * this is true (DbDeltaUpdateService.deltaRefusal).
     */
    fun usesBundledSqlite(db: File): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.P || isSplit(db)

    /** The database's size: all its chunks. */
    fun size(db: File): Long = files(db).sumOf { it.length() }

    /** Every overflow chunk of [base] in [dir], a gap or a stale one included. */
    fun allOverflowChunks(dir: File, base: String): List<File> =
        dir.listFiles { file -> file.isFile && file.name.startsWith(base) && CHUNK_NUMBER.matches(file.name.removePrefix(base)) }.orEmpty().toList()

    fun deleteOverflow(dir: File, base: String) {
        allOverflowChunks(dir, base).forEach { it.delete() }
    }

    /** Renames [from]'s overflow chunks to [to]'s (the database itself is left to the caller, as it goes last). */
    fun renameOverflow(from: File, to: File) {
        val dir = to.parentFile ?: return
        deleteOverflow(dir, to.name)
        overflowChunks(from).forEachIndexed { i, chunk ->
            if (!chunk.renameTo(File(dir, chunkName(to.name, i + 1)))) throw IOException("Cannot rename ${chunk.name}")
        }
    }

    /** The concatenation of [db]'s chunks. */
    fun openInput(db: File): InputStream = java.io.SequenceInputStream(java.util.Collections.enumeration(files(db).map { java.io.FileInputStream(it) }))

    // ------------------------------------------------------------------ the bundled SQLite

    @Volatile
    private var registered = false

    /**
     * Loads the multiplex extension into the bundled SQLite once per process. It registers the multiplex VFS as that
     * SQLite's default, so every database the bundled SQLite opens afterwards can be in chunks.
     */
    @Synchronized
    fun register() {
        if (registered) return
        // The extension stays loaded (SQLITE_OK_LOAD_PERMANENTLY); loading it here first keeps it in memory regardless.
        System.loadLibrary("pardes_multiplex")
        val config = SQLiteDatabaseConfiguration(SQLiteDatabaseConfiguration.MEMORY_DB_PATH, BundledDatabase.CREATE_IF_NECESSARY)
        config.customExtensions.add(SQLiteCustomExtension(EXTENSION, ENTRY_POINT))
        BundledDatabase.openDatabase(config, null, null).close()
        registered = true
    }

    /** For SQLDelight's AndroidSqliteDriver where [usesBundledSqlite]: the bundled SQLite, with wide cursor windows. */
    fun openHelperFactory(): SupportSQLiteOpenHelper.Factory {
        register()
        return WideWindowFactory(RequerySQLiteOpenHelperFactory())
    }

    /**
     * Read-only queries on the library database [db], with Android's SQLite or, where [usesBundledSqlite], the bundled
     * one. For the few readers outside the repository: the installer's check and the acronym cache.
     */
    fun openReader(db: File, writable: Boolean = false): LibraryReader =
        if (usesBundledSqlite(db)) {
            register()
            val flags = if (writable) BundledDatabase.OPEN_READWRITE else BundledDatabase.OPEN_READONLY
            val sql = BundledDatabase.openDatabase(db.path, null, flags)
            LibraryReader({ q, args -> sql.rawQuery(q, args) }, sql::close)
        } else {
            val flags = (if (writable) FrameworkDatabase.OPEN_READWRITE else FrameworkDatabase.OPEN_READONLY) or FrameworkDatabase.NO_LOCALIZED_COLLATORS
            val sql = FrameworkDatabase.openDatabase(db.path, null, flags)
            LibraryReader({ q, args -> sql.rawQuery(q, args) }, sql::close)
        }

    /** A connection's rawQuery and close, whichever SQLite opened it. */
    class LibraryReader(private val query: (String, Array<String>) -> Cursor, private val onClose: () -> Unit) : AutoCloseable {
        fun rawQuery(sql: String, args: Array<String> = emptyArray()): Cursor = query(sql, args)

        override fun close() = onClose()
    }

    /** A cursor of the bundled SQLite gets the same wide window as the repository's (WIDE_WINDOW_BYTES: rows of 4 MB). */
    private fun widen(cursor: Cursor): Cursor {
        var c: Cursor? = cursor
        while (c is CursorWrapper) c = c.wrappedCursor
        (c as? SQLiteCursor)?.setWindow(CursorWindow(null, WIDE_WINDOW_BYTES.toInt()))
        return cursor
    }

    private class WideWindowFactory(private val inner: SupportSQLiteOpenHelper.Factory) : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val helper = inner.create(configuration)
            return object : SupportSQLiteOpenHelper by helper {
                override val writableDatabase: SupportSQLiteDatabase get() = WideWindowDatabase(helper.writableDatabase)
                override val readableDatabase: SupportSQLiteDatabase get() = WideWindowDatabase(helper.readableDatabase)
            }
        }
    }

    private class WideWindowDatabase(private val db: SupportSQLiteDatabase) : SupportSQLiteDatabase by db {
        override fun query(query: String): Cursor = widen(db.query(query))

        override fun query(query: String, bindArgs: Array<out Any?>): Cursor = widen(db.query(query, bindArgs))

        override fun query(query: SupportSQLiteQuery): Cursor = widen(db.query(query))

        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor = widen(db.query(query, cancellationSignal))
    }
}

/**
 * Writes one database as SQLite multiplex chunks of [SplitDatabase.CHUNK_BYTES] next to [base] ([base] itself, then
 * base001…), flushing each to disk as it is closed. A database smaller than one chunk stays a single file.
 */
class ChunkedOutputStream(private val base: File) : OutputStream() {
    private var index = 0
    private var inChunk = 0L
    private var closed = false
    private var out: FileOutputStream = FileOutputStream(base)

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var left = len
        while (left > 0) {
            if (inChunk == SplitDatabase.CHUNK_BYTES) next()
            val n = minOf(left.toLong(), SplitDatabase.CHUNK_BYTES - inChunk).toInt()
            out.write(b, offset, n)
            inChunk += n
            offset += n
            left -= n
        }
    }

    private fun next() {
        finish(out)
        index++
        inChunk = 0
        out = FileOutputStream(File(base.parentFile, SplitDatabase.chunkName(base.name, index)))
    }

    private fun finish(stream: FileOutputStream) {
        stream.flush()
        stream.fd.sync()
        stream.close()
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        closed = true
        finish(out)
    }
}
