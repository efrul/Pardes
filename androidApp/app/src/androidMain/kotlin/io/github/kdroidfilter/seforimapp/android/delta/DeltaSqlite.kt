package io.github.kdroidfilter.seforimapp.android.delta

import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import java.io.File

/**
 * android.database plumbing for the delta updater (Android only: the desktop code reads through JDBC, which streams).
 *
 * An Android cursor fills a CursorWindow, and every refill runs the query again from its first row, so one query over
 * the 7.8 M lines of seforim.db would cost O(n²). The delta code therefore reads big tables page by page on a key
 * ([forEachByIntKey], [forEachByRowValue]), with a window large enough for the widest row of the library
 * (book.notesContent reaches 4.25 MB in release 20261012193827; the default window is 2 MB). The app's
 * repository opens the library with the same window (LibraryStore).
 */
internal const val WIDE_WINDOW_BYTES = 16L * 1024 * 1024

/**
 * [SQLiteDatabase.rawQuery] with a [WIDE_WINDOW_BYTES] window. CursorWindow(String, long) is API 28; the delta code runs
 * from Android 11 only (DbDeltaUpdateService.deltaRefusal), so below 28 the cursor keeps its window and nothing else.
 */
internal fun SQLiteDatabase.queryWide(sql: String, args: Array<String>? = null): Cursor =
    rawQuery(sql, args).also { cursor ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cursor as? AbstractWindowedCursor)?.window = CursorWindow("zayit-delta", WIDE_WINDOW_BYTES)
    }

/**
 * The library database, opened on its own connection (the app's repository keeps its own). Read-write even to read:
 * a WAL database needs its -shm file, which a read-only connection cannot create (as LibraryInstaller.openForCheck).
 */
internal fun openLibraryDatabase(file: File): SQLiteDatabase =
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
