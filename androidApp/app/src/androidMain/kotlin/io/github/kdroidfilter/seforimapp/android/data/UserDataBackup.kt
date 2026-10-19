package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The user's data as one archive, in desktop 0.6.1's format (`framework/backup/UserDataBackup.kt`), so a backup moves
 * between the phone and the computer in both directions: a zip of `manifest.json` (format 1, creation time and the
 * portable preferences under desktop's keys, every value a string) and `user_settings.db` (the user database:
 * notes, highlights and the desktop tables that come along). A bare `.db` exported before the archive format is
 * accepted too. Restoring is staged ([PendingUserDataRestore]) and applied at the next launch.
 */
object UserDataBackup {
    private const val FORMAT_VERSION = 1
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DATABASE_ENTRY = "user_settings.db"
    internal const val USER_DB = "user_settings.db"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val json = Json { ignoreUnknownKeys = true }

    /** Desktop exportToFile's name, under this app's name: `pardes_backup_<yyyy-MM-dd_HH-mm-ss>.zip` (import takes any name). */
    const val BACKUP_EXTENSION = "zip"

    /**
     * A consistent copy of the user database and the portable preferences, as an archive. Blocking. Desktop runs
     * VACUUM INTO on its live connection; here a second connection does it beside UserDataStore's (WAL allows it).
     */
    fun capture(context: Context): ByteArray {
        val dbFile = context.getDatabasePath(USER_DB)
        check(dbFile.isFile) { "No user database yet" }
        val copy = File(context.cacheDir, "user_settings_backup.db").apply { delete() }
        val database = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                runCatching { db.execSQL("VACUUM INTO '${copy.absolutePath.replace("'", "''")}'") }.onFailure {
                    // VACUUM INTO needs SQLite 3.27 (Android 8-10 have 3.18 to 3.22); otherwise fold the log into the
                    // file and copy it, holding the write lock meanwhile: a commit on UserDataStore's connection (or the
                    // checkpoint after one) would change the file under the copy.
                    db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor -> cursor.moveToFirst() }
                    db.beginTransactionNonExclusive()
                    try {
                        dbFile.copyTo(copy, overwrite = true)
                    } finally {
                        db.endTransaction()
                    }
                }
            }
            copy.readBytes()
        } finally {
            copy.delete()
        }
        return toArchive(database, AppPrefs.exportPortablePreferences(context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)))
    }

    /** Desktop stageRestore: validates [backup] and stages it for the next launch; throws when it is not a backup. */
    fun stageRestore(context: Context, backup: ByteArray) {
        val archive = upgradeLegacyBackup(context, backup)
        val (database, manifest) = readArchive(archive)
        require(manifest.format <= FORMAT_VERSION) { "Backup format ${manifest.format} is newer than this app" }
        require(database.startsWith(SQLITE_HEADER)) { "The backup's database is not a SQLite file" }
        PendingUserDataRestore.stage(context, archive)
    }

    /** A bare user database (the exports made before the archive format) restores alone, keeping this device's preferences. */
    private fun upgradeLegacyBackup(context: Context, backup: ByteArray): ByteArray =
        if (backup.startsWith(SQLITE_HEADER)) {
            toArchive(backup, AppPrefs.exportPortablePreferences(context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)))
        } else {
            backup
        }

    private fun toArchive(database: ByteArray, preferences: Map<String, String>): ByteArray {
        val manifest = BackupManifest(FORMAT_VERSION, System.currentTimeMillis(), preferences)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            zip.write(database)
        }
        return out.toByteArray()
    }

    /** The database and the manifest of [archive]; throws when either is missing. */
    internal fun readArchive(archive: ByteArray): Pair<ByteArray, BackupManifest> {
        var database: ByteArray? = null
        var manifest: BackupManifest? = null
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when (entry.name) {
                    DATABASE_ENTRY -> database = zip.readBytes()
                    MANIFEST_ENTRY -> manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                }
            }
        }
        return requireNotNull(database) { "The backup has no database" } to requireNotNull(manifest) { "The backup has no manifest" }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}

@Serializable
internal data class BackupManifest(
    val format: Int,
    val createdAtEpochMs: Long,
    val preferences: Map<String, String>,
)

/**
 * Desktop `framework/backup/PendingUserDataRestore.kt`: a backup waiting to replace the user's data. The running app
 * keeps the user database open, so a restore is staged in the databases folder and [runOnce] applies it at the next
 * launch, before the database is opened and the preferences are read. The data it replaced is kept as
 * `user_settings.db.before-restore` until the next restore.
 */
object PendingUserDataRestore {
    private const val TAG = "PendingUserDataRestore"
    private const val PENDING_NAME = "pending-user-restore.zip"
    private const val PREVIOUS_SUFFIX = ".before-restore"
    private val SIDECARS = listOf("-wal", "-shm", "-journal")

    private fun pendingFile(context: Context): File = context.getDatabasePath(PENDING_NAME)

    internal fun stage(context: Context, archive: ByteArray) {
        val target = pendingFile(context)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(archive)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("Cannot stage the backup")
        }
    }

    fun isPending(context: Context): Boolean = runCatching { pendingFile(context).exists() }.getOrDefault(false)

    /** Applies a staged restore, if any. Called once at launch, before AppPrefs reads anything and before the user database opens. */
    fun runOnce(context: Context) {
        val pending = runCatching { pendingFile(context) }.getOrNull()?.takeIf { it.exists() } ?: return
        val (database, manifest) = runCatching { UserDataBackup.readArchive(pending.readBytes()) }.getOrElse {
            // Validated when staged, so only a damaged file lands here: retrying would not help.
            Log.w(TAG, "Dropping an unreadable backup", it)
            pending.delete()
            return
        }
        runCatching {
            val dbFile = context.getDatabasePath(UserDataBackup.USER_DB)
            val previous = File(dbFile.path + PREVIOUS_SUFFIX)
            // Once per staged restore: a retry after a failed attempt would copy the half-restored database.
            if (dbFile.exists() && previous.lastModified() < pending.lastModified()) {
                // Android keeps the database in WAL mode: fold the log into the file before copying it.
                runCatching {
                    SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    }
                }
                dbFile.copyTo(previous, overwrite = true)
            }
            // A log left next to the new file would be replayed into it.
            SIDECARS.forEach { File(dbFile.path + it).delete() }
            dbFile.parentFile?.mkdirs()
            val tmp = File(dbFile.path + ".restore")
            tmp.writeBytes(database)
            if (!tmp.renameTo(dbFile)) error("Cannot replace ${dbFile.name}")
            AppPrefs.importPortablePreferences(context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE), manifest.preferences)
            pending.delete()
            Log.i(TAG, "Restored the backup of ${manifest.createdAtEpochMs}")
        }.onFailure {
            // Kept for the next launch (a full disk passes).
            Log.w(TAG, "Could not restore the backup, will retry", it)
        }
    }
}
