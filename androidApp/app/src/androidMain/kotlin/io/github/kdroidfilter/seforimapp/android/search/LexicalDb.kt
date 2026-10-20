package io.github.kdroidfilter.seforimlibrary.search

import android.database.sqlite.SQLiteDatabase
import io.github.kdroidfilter.seforimapp.android.data.SplitDatabase.LibraryReader

/**
 * lexical.db for MagicDictionaryIndex, read-only, with the platform SQLite (its lookups use WITH: SQLite 3.8.3, which
 * Android has from 5.0). The Android 4.4 edition has its own openLexicalDb, on the SQLite it carries.
 */
internal fun openLexicalDb(path: String): LibraryReader {
    val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    return LibraryReader({ sql, args -> db.rawQuery(sql, args) }, db::close)
}
