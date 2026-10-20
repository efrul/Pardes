package io.github.kdroidfilter.seforimapp.android.data

import android.database.Cursor
import android.database.MatrixCursor
import io.github.kdroidfilter.seforimapp.legacy.db.NativeSqlite

/**
 * A statement's rows on this edition's SQLite as a Cursor, all read at once, with the columns' real names (the
 * dictionary's lookups read them by name): the acronym and dictionary queries return a few hundred rows at most.
 */
internal object NativeCursor {
    fun rows(db: Long, sql: String, args: Array<String>): Cursor {
        val stmt = NativeSqlite.prepare(db, sql)
        if (stmt == 0L) return MatrixCursor(emptyArray())
        try {
            args.forEachIndexed { i, a -> NativeSqlite.bindText(stmt, i + 1, a) }
            val n = NativeSqlite.columnCount(stmt)
            val cursor = MatrixCursor(Array(n) { NativeSqlite.columnName(stmt, it) })
            while (NativeSqlite.step(stmt)) {
                cursor.addRow(
                    Array<Any?>(n) { c ->
                        when (NativeSqlite.columnType(stmt, c)) {
                            NativeSqlite.TYPE_INTEGER -> NativeSqlite.columnLong(stmt, c)
                            NativeSqlite.TYPE_FLOAT -> NativeSqlite.columnDouble(stmt, c)
                            NativeSqlite.TYPE_NULL -> null
                            NativeSqlite.TYPE_BLOB -> NativeSqlite.columnBlob(stmt, c)
                            else -> NativeSqlite.columnText(stmt, c)
                        }
                    },
                )
            }
            return cursor
        } finally {
            NativeSqlite.finalizeStatement(stmt)
        }
    }
}
