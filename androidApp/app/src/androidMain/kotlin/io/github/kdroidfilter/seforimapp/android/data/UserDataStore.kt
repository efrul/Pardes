package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.db.User_notes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A note anchored to [startOffset, endOffset) of a line's plain text WITH diacritics (desktop NoteStore.UserNote), so
 * it stays valid whatever diacritics are shown. [quote] is the anchored passage.
 */
data class UserNote(
    val id: Long,
    val bookId: Long,
    val lineId: Long,
    val note: String,
    val quote: String,
    val updatedAt: Long,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val createdAt: Long = 0L,
)

/** A highlight of [start, end) of a line's plain text WITH diacritics, in [colorArgb] (desktop HighlightStore.UserHighlight). */
data class UserHighlight(val id: Long, val bookId: Long, val lineId: Long, val start: Int, val end: Int, val colorArgb: Long)

/**
 * A write to the user database launched from the UI. One the database refuses (a full disk: SQLiteFullException) loses
 * that edit and is logged, instead of closing the app from a coroutine nothing handles. True when it went through.
 */
suspend fun guardedWrite(what: String, block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("Zayit", "$what failed", e)
    false
}

/**
 * The user database, `user_settings.db`, with desktop 0.6.1's schema (SeforimApp/src/commonMain/sqldelight, vendored in
 * src/androidMain/sqldelight-user): notes and highlights here, the visit history in [history] and the favorites in
 * [favorites] (desktop AppCoreBindings: one shared connection for every user store). Constructing it opens the
 * database: do it off the main thread (MainActivity does).
 *
 * Notes and highlights follow desktop core/annotations NoteStore and HighlightStore: per-book caches ([notesByBook],
 * [highlightsByBook]) read by the reader on its render path, refreshed after each write. The diacritics mode of each
 * root category is desktop core/settings/CategoryDisplaySettingsStore's (stored 0 none, 1 all, 2 nikud only).
 */
class UserDataStore(context: Context) {
    init {
        adoptImportedDatabase(context)
    }

    private val driver = AndroidSqliteDriver(UserSettingsDb.Schema, context, DB_NAME)
    private val db = UserSettingsDb(driver)

    init {
        // As desktop: new tables reach existing databases (an older Android one, or a desktop one imported) through
        // Schema.create, where every statement is CREATE … IF NOT EXISTS. The open helper only runs it on a new file.
        runCatching { UserSettingsDb.Schema.create(driver) }.onFailure { Log.w(TAG, "Schema update failed", it) }
    }

    /** Visit history (desktop HistoryStore): opened books and executed searches. */
    val history = HistoryStore(db)

    /** Favorites and their folders (desktop FavoritesStore). */
    val favorites = FavoritesStore(db)

    /** Shnayim mikra ticks (desktop ShnayimMikraStore). */
    val shnayimMikra = ShnayimMikraStore(db)

    /** How long each book was read, per day (Pardes's own, for the Home's learning split). */
    val readingTime = ReadingTimeStore(db)

    /** The latest notes, newest first (the Home notes widget; desktop NoteStore.recent). */
    suspend fun recentNotes(limit: Int): List<UserNote> = withContext(Dispatchers.IO) {
        db.userNotesQueries.selectRecent(limit.toLong()).executeAsList().map { it.toNote() }
    }

    /** Every note of every book, the most recently written first (desktop NoteStore.all: a negative LIMIT is none). */
    suspend fun allNotes(): List<UserNote> = recentNotes(-1)

    private val _revision = MutableStateFlow(0)

    /** Bumped on every change to notes or highlights so screens can reload. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _notesByBook = MutableStateFlow<Map<Long, List<UserNote>>>(emptyMap())

    /** Notes of the books loaded with [loadNotes], by book. */
    val notesByBook: StateFlow<Map<Long, List<UserNote>>> = _notesByBook.asStateFlow()

    private val _highlightsByBook = MutableStateFlow<Map<Long, List<UserHighlight>>>(emptyMap())

    /** Highlights of the books loaded with [loadHighlights], by book. */
    val highlightsByBook: StateFlow<Map<Long, List<UserHighlight>>> = _highlightsByBook.asStateFlow()

    suspend fun notesForBook(bookId: Long): List<UserNote> = withContext(Dispatchers.IO) {
        db.userNotesQueries.selectAllForBook(bookId).executeAsList().map { it.toNote() }
    }

    /** Reads [bookId]'s notes into [notesByBook] (again: an import may have changed them meanwhile). */
    suspend fun loadNotes(bookId: Long) = withContext(Dispatchers.IO) { refreshNotes(bookId) }

    /**
     * Adds a note anchored to [startOffset, endOffset) and returns its id, or null for an empty range or a blank body
     * (desktop NoteStore.addNote).
     */
    suspend fun addNote(bookId: Long, lineId: Long, startOffset: Int, endOffset: Int, note: String, quote: String): Long? =
        withContext(Dispatchers.IO) {
            if (startOffset >= endOffset || note.isBlank()) return@withContext null
            val now = System.currentTimeMillis()
            val id = db.userNotesQueries.transactionWithResult {
                db.userNotesQueries.insert(bookId, lineId, startOffset.toLong(), endOffset.toLong(), note, quote, now, now)
                db.userNotesQueries.lastInsertRowId().executeAsOne()
            }
            refreshNotes(bookId)
            id
        }

    /** Updates a note's text; a blank text deletes it instead (desktop NoteStore.updateNote). */
    suspend fun updateNote(bookId: Long, noteId: Long, note: String) = withContext(Dispatchers.IO) {
        if (note.isBlank()) db.userNotesQueries.deleteById(noteId) else db.userNotesQueries.updateNote(note, System.currentTimeMillis(), noteId)
        refreshNotes(bookId)
    }

    suspend fun removeNote(bookId: Long, noteId: Long) = withContext(Dispatchers.IO) {
        db.userNotesQueries.deleteById(noteId)
        refreshNotes(bookId)
    }

    suspend fun highlightsForBook(bookId: Long): List<UserHighlight> = withContext(Dispatchers.IO) {
        db.userHighlightsQueries.selectAllForBook(bookId).executeAsList().map {
            UserHighlight(it.id, it.bookId, it.lineId, it.startOffset.toInt(), it.endOffset.toInt(), it.colorArgb)
        }
    }

    /** Reads [bookId]'s highlights into [highlightsByBook]. */
    suspend fun loadHighlights(bookId: Long) = withContext(Dispatchers.IO) { refreshHighlights(bookId) }

    /** Adds a highlight, replacing those overlapping it on the line (desktop HighlightStore.addHighlight). */
    suspend fun addHighlight(bookId: Long, lineId: Long, startOffset: Int, endOffset: Int, colorArgb: Long) = withContext(Dispatchers.IO) {
        if (startOffset >= endOffset) return@withContext
        db.userHighlightsQueries.transaction {
            db.userHighlightsQueries.deleteOverlapping(bookId, lineId, endOffset.toLong(), startOffset.toLong())
            db.userHighlightsQueries.insert(bookId, lineId, startOffset.toLong(), endOffset.toLong(), colorArgb, System.currentTimeMillis())
        }
        refreshHighlights(bookId)
    }

    /** Removes the highlights overlapping [startOffset, endOffset) of the line: the palette's "clear". */
    suspend fun removeOverlappingHighlights(bookId: Long, lineId: Long, startOffset: Int, endOffset: Int) = withContext(Dispatchers.IO) {
        db.userHighlightsQueries.deleteOverlapping(bookId, lineId, endOffset.toLong(), startOffset.toLong())
        refreshHighlights(bookId)
    }

    /** The stored diacritics mode of a root category (0 none, 1 all, 2 nikud only), or null when never set. */
    suspend fun categoryDiacritics(categoryId: Long): Long? = withContext(Dispatchers.IO) {
        db.categoryDisplaySettingsQueries.selectShowDiacritics(categoryId).executeAsOneOrNull()
    }

    suspend fun setCategoryDiacritics(categoryId: Long, stored: Long) = withContext(Dispatchers.IO) {
        db.categoryDisplaySettingsQueries.upsertShowDiacritics(categoryId, stored)
        _diacriticsChanges.tryEmit(categoryId)
    }

    private val _diacriticsChanges = MutableSharedFlow<Long>(extraBufferCapacity = 4)

    /** Root categories whose diacritics mode changed (another tab of the same corpus follows). */
    val diacriticsChanges: SharedFlow<Long> = _diacriticsChanges.asSharedFlow()

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        // The generated queries delete per book, so clear the tables through the driver.
        db.userNotesQueries.transaction {
            driver.execute(null, "DELETE FROM user_notes", 0)
            driver.execute(null, "DELETE FROM user_highlights", 0)
        }
        _notesByBook.value = emptyMap()
        _highlightsByBook.value = emptyMap()
        _revision.value++
    }

    private fun refreshNotes(bookId: Long) {
        val list = db.userNotesQueries.selectAllForBook(bookId).executeAsList().map { it.toNote() }
        _notesByBook.update { it + (bookId to list) }
        _revision.value++
    }

    private fun refreshHighlights(bookId: Long) {
        val list = db.userHighlightsQueries.selectAllForBook(bookId).executeAsList().map {
            UserHighlight(it.id, it.bookId, it.lineId, it.startOffset.toInt(), it.endOffset.toInt(), it.colorArgb)
        }
        _highlightsByBook.update { it + (bookId to list) }
        _revision.value++
    }

    private fun User_notes.toNote() = UserNote(id, bookId, lineId, note, quote, updatedAt, startOffset.toInt(), endOffset.toInt(), createdAt)

    companion object {
        private const val TAG = "UserDataStore"
        private const val DB_NAME = "user_settings.db"
        private const val IMPORT_STAMP = "imported_user_db"
        private val SIDECARS = listOf("", "-wal", "-shm", "-journal")

        /**
         * `tools/push-desktop-library.sh --with-notes` drops the desktop user_settings.db in
         * `<external files>/import/`. It replaces this app's database before the driver opens it (its history and
         * favorites come along, and tables Android does not use stay unchanged), then the import file is deleted. The
         * stamp stops a file that could not be deleted (shell-owned folder) from overwriting newer data later.
         */
        private fun adoptImportedDatabase(context: Context) {
            val import = context.getExternalFilesDir(null)?.let { File(it, "import/$DB_NAME") }?.takeIf { it.isFile && it.length() > 0 } ?: return
            val prefs = context.getSharedPreferences("user_data_import", Context.MODE_PRIVATE)
            val stamp = "${import.length()}:${import.lastModified()}"
            if (prefs.getString(IMPORT_STAMP, null) != stamp) {
                // Checked on an app-owned copy: SQLite may need to write next to a file pushed by adb.
                val copy = File(context.cacheDir, "import_$DB_NAME")
                runCatching {
                    import.copyTo(copy, overwrite = true)
                    val valid = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                        db.rawQuery(
                            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('user_notes', 'user_highlights', 'visit_history', 'favorite')",
                            null,
                        ).use { it.moveToFirst() && it.getInt(0) > 0 }
                    }
                    require(valid) { "no user tables" }
                    val target = context.getDatabasePath(DB_NAME)
                    target.parentFile?.mkdirs()
                    SIDECARS.forEach { File(target.path + it).delete() }
                    copy.copyTo(target, overwrite = true)
                    Log.i(TAG, "Imported the desktop user database from ${import.path}")
                }.onFailure { Log.w(TAG, "Ignored ${import.path}", it) }
                SIDECARS.forEach { File(copy.path + it).delete() }
            }
            SIDECARS.forEach { File(import.path + it).delete() }
            // Only a file that is still there keeps its stamp: the same desktop file pushed again later (adb keeps
            // its size and mtime) must be imported again.
            prefs.edit().apply { if (import.exists()) putString(IMPORT_STAMP, stamp) else remove(IMPORT_STAMP) }.apply()
        }
    }
}
