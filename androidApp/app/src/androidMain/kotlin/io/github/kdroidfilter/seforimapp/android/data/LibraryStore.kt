package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
 * release_info.txt. It lives in app-specific storage, in one of the [locations]: on the phone, `<external files>/library/`
 * when external storage is available (so a large download does not count against internal storage), otherwise
 * `<files>/library/`; or on a memory card, the app's folder there (`/storage/<card>/Android/data/<app>/files/library/`).
 * `tools/push-desktop-library.sh` copies a desktop install straight into either.
 *
 * The chosen location is remembered (a separate preferences file, not part of a backup), so that a library on a card that
 * has been taken out is reported as such ([missingCard]) instead of looking like no library at all.
 */
class LibraryStore(private val context: Context) {
    private val internalDir: File get() = File(context.filesDir, LibraryLocation.LIBRARY_FOLDER)
    private val prefs = context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var openedToAdb = false

    @Volatile
    private var cachedLocations: List<LibraryLocation>? = null

    /** The phone, then every memory card mounted now. Read once; [refreshLocations] reads them again (a card in or out). */
    val locations: List<LibraryLocation>
        get() = cachedLocations ?: LibraryLocation.read(context, internalDir).also { cachedLocations = it }

    fun refreshLocations() {
        cachedLocations = null
    }

    /** The location chosen for the library (by the user, or where it was found): [LibraryLocation.PHONE] or a card's UUID. */
    val preferredKey: String get() = prefs.getString(KEY_LOCATION, null) ?: LibraryLocation.PHONE

    /** Chooses where a new library goes: the next install (or a move) writes there. */
    fun setPreferred(location: LibraryLocation) {
        if (location.key == preferredKey && (!location.removable || prefs.getString(KEY_CARD_LABEL, null) == location.label)) return
        prefs.edit().putString(KEY_LOCATION, location.key).putString(KEY_CARD_LABEL, location.label.takeIf { location.removable }).commit()
    }

    /** Where the library is now (it has seforim.db), or null. The internal folder of older builds counts as the phone. */
    fun currentLocation(): LibraryLocation? {
        val dir = libraryDir
        if (!hasDatabase(dir)) return null
        return locations.firstOrNull { it.dir == dir } ?: locations.first().copy(dir = dir)
    }

    /**
     * Records where the library is (called at startup): a library found elsewhere than chosen, copied onto a card from
     * a computer say, becomes the chosen location, so that its card is asked for once it is taken out. A chosen card
     * that is only out now keeps the choice over a library left on the phone: that one is used this time ([resolveDir]),
     * not chosen. A library on another card (one that replaced the chosen card) is still chosen.
     */
    fun rememberCurrentLocation() {
        val current = currentLocation() ?: return
        val key = preferredKey
        if (key != LibraryLocation.PHONE && !current.removable && locations.none { it.key == key }) return
        setPreferred(current)
    }

    /** The location a new install writes to: the chosen one when it is mounted, else the phone. */
    fun targetLocation(): LibraryLocation = locations.firstOrNull { it.key == preferredKey } ?: locations.first()

    /**
     * The card the library was kept on, when that card is not in the device now and no library is found anywhere else:
     * its name, for the screen that asks for it back. null otherwise.
     */
    fun missingCard(): String? {
        val key = preferredKey
        if (key == LibraryLocation.PHONE) return null
        refreshLocations()
        if (locations.any { it.key == key }) return null
        if (candidateDirs.any(::hasDatabase)) return null
        return prefs.getString(KEY_CARD_LABEL, null) ?: LibraryLocation.CARD_LABEL
    }

    /** Where the library is, or will be installed. An existing install wins, so a late-mounted or missing volume never hides it. */
    val libraryDir: File
        get() = resolveDir().apply {
            mkdirs()
            // tools/push-desktop-library.sh writes here as adb's `shell` user, which is in the ext_data_rw group. A folder
            // made in the external files dir inherits that group (setgid), and so does everything the app later creates
            // in it (its own download, delta-cache), so the script can replace all of it. Only a folder without that is
            // opened to everyone: chmod drops the setgid bit, and the app cannot set it again for a group it is not in.
            // On a card the filesystem decides permissions, so nothing is changed there.
            if (!openedToAdb && this == locations.first().dir && this != internalDir) {
                openedToAdb = true
                runCatching {
                    val mode = Os.stat(path).st_mode
                    val groupWritable = mode and OsConstants.S_ISGID != 0 && mode and OsConstants.S_IRWXG == OsConstants.S_IRWXG
                    if (!groupWritable) Os.chmod(path, "777".toInt(8))
                }
            }
        }

    /**
     * The chosen location first, then the others, then the internal folder of builds that had no external storage: the
     * first that holds a database is the library; with none, the location a new install writes to.
     */
    private fun resolveDir(): File {
        val all = locations
        val preferred = all.firstOrNull { it.key == preferredKey }
        val ordered = listOfNotNull(preferred) + all.filter { it !== preferred }
        ordered.firstOrNull { hasDatabase(it.dir) }?.let { return it.dir }
        if (internalDir !in ordered.map { it.dir } && hasDatabase(internalDir)) return internalDir
        return (preferred ?: all.first()).dir
    }

    /** Every place the library may be in (an install clears them all). */
    val candidateDirs: List<File> get() = (locations.map { it.dir } + internalDir).distinct()

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
    fun installedBytes(): Long = libraryBytes(libraryDir)

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

    /** How many times [close] closed an open repository in this process: whoever holds one can tell it was closed since. */
    @Volatile
    var closeCount = 0
        private set

    /** The database this process opened the library from ([repository]), or null; [close] leaves it. */
    @Volatile
    var openedFrom: File? = null
        private set

    fun repository(): SeforimRepository {
        repository?.let { return it }
        synchronized(this) {
            repository?.let { return it }
            val bundled = SplitDatabase.usesBundledSqlite(databaseFile)
            val driver = PragmaSafeDriver(
                AndroidSqliteDriver(
                    schema = SeforimDb.Schema,
                    context = context,
                    name = databaseFile.absolutePath,
                    // A database in chunks (a FAT32 card) is read through the multiplex VFS of the app's own SQLite,
                    // and so is every library below Android 9, whose own SQLite keeps a 2 MB window (SplitDatabase).
                    factory = if (bundled) SplitDatabase.openHelperFactory() else FrameworkSQLiteOpenHelperFactory(),
                    // A row wider than Android's 2 MB cursor window closed the app (SQLiteBlobTooBigException): a
                    // book's notes reach 4.25 MB (מיקרופדיה תלמודית opened the encyclopedias with 2.9 MB). Desktop
                    // reads through JDBC, which has no such window. SQLDelight applies it from API 28, to Android's
                    // cursors; the bundled SQLite's get theirs from openHelperFactory.
                    windowSizeBytes = WIDE_WINDOW_BYTES,
                ),
            )
            this.driver = driver
            openedFrom = databaseFile
            return SeforimRepository(databaseFile.absolutePath, driver).also { repository = it }
        }
    }

    /**
     * The library's own connection (the repository's, opened if need be), for reads the repository has no query for: the
     * semantic search's book levels and lines (HybridSearchEngine, BookLevels.kt).
     */
    fun driver(): SqlDriver {
        repository()
        return checkNotNull(driver) { "The library is closed" }
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
            if (repository != null) closeCount++
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

        /** Android: the version a delta applied from files reached (DbDeltaUpdateService), beside release_info.txt. */
        const val LOCAL_VERSION_NAME = "local_update_version.txt"

        /** Name, and prefix of the side files, of the SQLite FTS index that earlier builds created on the device. */
        private const val OBSOLETE_FTS_INDEX = "search_index.db"

        /** Prefix of what tools/push-desktop-library.sh set aside because adb could not remove it. */
        private const val PUSH_LEFTOVER_PREFIX = ".zayit-old-"

        /** Suffix of an item still being installed or pushed; it only gets its real name once complete. */
        const val PARTIAL_SUFFIX = ".part"

        /** What a library consists of, in the order an install moves it into place: the database last, so it only counts as installed when complete. */
        val LIBRARY_ITEMS = listOf(LOOKUP_INDEX_NAME, TEXT_INDEX_NAME, LEXICAL_NAME, CATALOG_NAME, RELEASE_INFO_NAME, LOCAL_VERSION_NAME, DB_NAME)

        fun isLuceneIndex(dir: File): Boolean = dir.list()?.any { it.startsWith("segments_") } == true

        /** Bytes of the library in [dir]: its items, with the overflow chunks of a split database (SplitDatabase). */
        fun libraryBytes(dir: File): Long = LIBRARY_ITEMS.sumOf { name ->
            val f = File(dir, name)
            if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
        } + SplitDatabase.overflowChunks(File(dir, DB_NAME)).sumOf { it.length() }

        /** [dir] holds a library database (complete: an install or a move only gives it its name once done). */
        fun hasDatabase(dir: File): Boolean = File(dir, DB_NAME).isFile

        /** Where the library is kept (not the user's preferences: a backup does not carry it). */
        private const val LOCATION_PREFS = "library_location"
        private const val KEY_LOCATION = "location"

        /** The card's name when the library is on a card, for the screen that asks for it back. */
        private const val KEY_CARD_LABEL = "card_label"
    }
}
