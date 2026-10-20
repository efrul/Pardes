package io.github.kdroidfilter.seforimapp.android.data

import android.database.Cursor
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.db.NativeSqlite
import java.io.File

/**
 * This edition's stand-in for the main app's SplitDatabase (same name and package): its [openReader], which the main
 * app's AcronymFrequencyCache reads seforim.db through, and [LibraryReader], which MagicDictionaryIndex reads lexical.db
 * through (search/LexicalDb.kt). Both on this edition's SQLite 3.49, which reads a database split in chunks as one.
 * seforim.db is read through the library's own connection: NativeSqliteDriver opens it in exclusive locking mode, so
 * a second connection could not read it.
 */
object SplitDatabase {
    /** As the main app's: a connection's rawQuery and close. */
    class LibraryReader(private val query: (String, Array<String>) -> Cursor, private val onClose: () -> Unit) : AutoCloseable {
        fun rawQuery(sql: String, args: Array<String> = emptyArray()): Cursor = query(sql, args)

        override fun close() = onClose()
    }

    /** The app's library (PardesApp sets it): seforim.db is read through its connection. */
    @Volatile
    var library: Library? = null

    fun openReader(db: File, writable: Boolean = false): LibraryReader {
        val library = library
        // Only the library's own seforim.db: another one (a library folder an install checks) opens on its own.
        if (library != null && db.name == Library.DB_NAME && library.current?.file?.absolutePath == db.absolutePath) {
            // The open library at each query: one opened again after this reader was made is the one read
            return LibraryReader({ sql, args -> library.open().driver.withConnection { NativeCursor.rows(it, sql, args) } }, {})
        }
        val handle = NativeSqlite.open(db.path, if (writable) NativeSqlite.OPEN_READWRITE else NativeSqlite.OPEN_READONLY)
        return LibraryReader({ sql, args -> NativeCursor.rows(handle, sql, args) }, { NativeSqlite.close(handle) })
    }
}
