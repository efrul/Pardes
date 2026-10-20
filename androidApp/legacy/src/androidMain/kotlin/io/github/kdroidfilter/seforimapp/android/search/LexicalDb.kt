package io.github.kdroidfilter.seforimlibrary.search

import io.github.kdroidfilter.seforimapp.android.data.NativeCursor
import io.github.kdroidfilter.seforimapp.android.data.SplitDatabase.LibraryReader
import io.github.kdroidfilter.seforimapp.legacy.db.NativeSqlite

/**
 * This edition's openLexicalDb (the main app's is in its search/LexicalDb.kt): lexical.db for MagicDictionaryIndex,
 * read-only, on this edition's SQLite 3.49. Android 4.4's own (3.7.11) has no WITH, which the dictionary's lookups use.
 * One handle per call: MagicDictionaryIndex keeps a pool of them, each used by one thread at a time (the native SQLite
 * is built with SQLITE_THREADSAFE=2).
 */
internal fun openLexicalDb(path: String): LibraryReader {
    val db = NativeSqlite.open(path, NativeSqlite.OPEN_READONLY)
    return LibraryReader({ sql, args -> NativeCursor.rows(db, sql, args) }, { NativeSqlite.close(db) })
}
