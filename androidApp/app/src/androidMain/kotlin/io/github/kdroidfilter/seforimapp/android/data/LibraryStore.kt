package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.kdroidfilter.seforimapp.android.delta.WIDE_WINDOW_BYTES
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.db.SeforimDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Locates the installed library and opens the shared repository on top of it.
 *
 * The library is the desktop databases folder, same names: seforim.db, the Lucene indexes seforim.db.lucene/
 * (full text) and seforim.db.lookup.lucene/ (books and TOC), lexical.db (magic dictionary), catalog.pb and
 * release_info.txt. It lives in app-specific storage: `<external files>/library/` when external storage is
 * available (so a large download does not count against internal storage), otherwise `<files>/library/`.
 * `tools/push-desktop-library.sh` copies a desktop install straight into the external folder.
 */
class LibraryStore(private val context: Context) {
    private val externalDir: File? get() = context.getExternalFilesDir(null)?.let { File(it, "library") }
    private val internalDir: File get() = File(context.filesDir, "library")

    @Volatile
    private var openedToAdb = false

    /** Where the library is, or will be installed. An existing install wins, so a late-mounted or missing external volume never hides it. */
    val libraryDir: File
        get() = (listOfNotNull(externalDir, internalDir).firstOrNull { File(it, DB_NAME).isFile } ?: externalDir ?: internalDir).apply {
            mkdirs()
            // tools/push-desktop-library.sh writes here as adb's `shell` user, which is in the ext_data_rw group. A folder
            // made in the external files dir inherits that group (setgid), and so does everything the app later creates
            // in it (its own download, delta-cache), so the script can replace all of it. Only a folder without that is
            // opened to everyone: chmod drops the setgid bit, and the app cannot set it again for a group it is not in.
            if (!openedToAdb && this == externalDir) {
                openedToAdb = true
                runCatching {
                    val mode = Os.stat(path).st_mode
                    val groupWritable = mode and OsConstants.S_ISGID != 0 && mode and OsConstants.S_IRWXG == OsConstants.S_IRWXG
                    if (!groupWritable) Os.chmod(path, "777".toInt(8))
                }
            }
        }

    /** Both places the library may be in (an install clears both). */
    val candidateDirs: List<File> get() = listOfNotNull(externalDir, internalDir)

    val databaseFile: File get() = File(libraryDir, DB_NAME)

    /** Full-text index (desktop LuceneSearchEngine). */
    val luceneDir: File get() = File(libraryDir, TEXT_INDEX_NAME)

    /** Book title / TOC lookup index (desktop LuceneLookupSearchService). */
    val lookupDir: File get() = File(libraryDir, LOOKUP_INDEX_NAME)

    /** Magic dictionary used to expand search terms. */
    val lexicalFile: File get() = File(libraryDir, LEXICAL_NAME)

    val catalogFile: File get() = File(libraryDir, CATALOG_NAME)

    val releaseInfoFile: File get() = File(libraryDir, RELEASE_INFO_NAME)

    /** Both Lucene indexes are in place (a commit point exists). Indexes are only renamed into place once complete. */
    fun hasSearchIndex(): Boolean = isLuceneIndex(luceneDir) && isLuceneIndex(lookupDir)

    /** Bytes taken by the library's own files (database and indexes). */
    fun installedBytes(): Long = LIBRARY_ITEMS.sumOf { name ->
        val f = File(libraryDir, name)
        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
    }

    /**
     * Removes what earlier libraries left in the library folder: the full-text index that builds from before the Lucene
     * search made on the device (search_index.db and its temporary and journal files, close to 10 GB on a full library),
     * and what tools/push-desktop-library.sh renamed .zayit-old-* because adb could not empty it (folders the app made,
     * such as its own download). Blocking; call it off the main thread.
     */
    fun deleteObsoleteFiles() {
        for (dir in candidateDirs) {
            dir.listFiles { file -> file.isFile && file.name.startsWith(OBSOLETE_FTS_INDEX) }?.forEach { it.delete() }
            dir.listFiles { file -> file.name.startsWith(PUSH_LEFTOVER_PREFIX) }?.forEach { it.deleteRecursively() }
        }
    }

    /** Files dropped by `adb push` or a file manager into the external files root are adopted too. */
    private val legacyCandidates: List<File>
        get() = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, DB_NAME) },
            File(context.filesDir, DB_NAME),
        )

    fun isInstalled(): Boolean {
        if (databaseFile.isFile && databaseFile.length() > 0) return true
        val legacy = legacyCandidates.firstOrNull { it.isFile && it.length() > 0 } ?: return false
        return legacy.renameTo(databaseFile) || run {
            legacy.copyTo(databaseFile, overwrite = true)
            legacy.delete()
            true
        }
    }

    @Volatile
    private var repository: SeforimRepository? = null

    @Volatile
    private var driver: SqlDriver? = null

    fun repository(): SeforimRepository {
        repository?.let { return it }
        synchronized(this) {
            repository?.let { return it }
            val driver = PragmaSafeDriver(
                AndroidSqliteDriver(
                    schema = SeforimDb.Schema,
                    context = context,
                    name = databaseFile.absolutePath,
                    // A row wider than Android's 2 MB cursor window closed the app (SQLiteBlobTooBigException): a
                    // book's notes reach 4.25 MB (מיקרופדיה תלמודית opened the encyclopedias with 2.9 MB). Desktop
                    // reads through JDBC, which has no such window.
                    windowSizeBytes = WIDE_WINDOW_BYTES,
                ),
            )
            this.driver = driver
            return SeforimRepository(databaseFile.absolutePath, driver).also { repository = it }
        }
    }

    /**
     * Every book's id, title, category and place, by category in the order of its list (getBooksByCategory's): the
     * catalog of a library without catalog.pb in one query. That one reads whole rows, notes included (4.25 MB for one
     * book, 37 MB in all), and four more tables per book: 40,000 queries for the 7,900 books.
     */
    suspend fun catalogBooks(): Map<Long, List<CatalogBook>> = withContext(Dispatchers.IO) {
        repository()
        val driver = driver ?: return@withContext emptyMap()
        driver.executeQuery(
            null,
            "SELECT id, title, categoryId, orderIndex FROM book ORDER BY orderIndex, title",
            { cursor ->
                val books = ArrayList<CatalogBook>()
                while (cursor.next().value) {
                    books += CatalogBook(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getLong(2)!!, cursor.getLong(3)!!.toFloat())
                }
                QueryResult.Value(books)
            },
            0,
        ).value.groupBy { it.categoryId }
    }

    /** Closes the open library so its file can be replaced (reinstall); the next [repository] call reopens it. */
    fun close() {
        synchronized(this) {
            runCatching { repository?.close() }
            repository = null
            driver = null
        }
    }

    companion object {
        const val DB_NAME = "seforim.db"

        /** Desktop names (AppCoreBindings): "<db>.lucene" and "<db>.lookup.lucene" next to the database. */
        const val TEXT_INDEX_NAME = "$DB_NAME.lucene"
        const val LOOKUP_INDEX_NAME = "$DB_NAME.lookup.lucene"
        const val LEXICAL_NAME = "lexical.db"
        const val CATALOG_NAME = "catalog.pb"
        const val RELEASE_INFO_NAME = "release_info.txt"

        /** Name, and prefix of the side files, of the SQLite FTS index that earlier builds created on the device. */
        private const val OBSOLETE_FTS_INDEX = "search_index.db"

        /** Prefix of what tools/push-desktop-library.sh set aside because adb could not remove it. */
        private const val PUSH_LEFTOVER_PREFIX = ".zayit-old-"

        /** Suffix of an item still being installed or pushed; it only gets its real name once complete. */
        const val PARTIAL_SUFFIX = ".part"

        /** What a library consists of, in the order an install moves it into place: the database last, so it only counts as installed when complete. */
        val LIBRARY_ITEMS = listOf(LOOKUP_INDEX_NAME, TEXT_INDEX_NAME, LEXICAL_NAME, CATALOG_NAME, RELEASE_INFO_NAME, DB_NAME)

        fun isLuceneIndex(dir: File): Boolean = dir.list()?.any { it.startsWith("segments_") } == true
    }
}
