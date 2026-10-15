package io.github.kdroidfilter.seforimlibrary.deltaupdater

import android.database.sqlite.SQLiteDatabase
import co.touchlab.kermit.Logger
import io.github.kdroidfilter.seforimapp.android.delta.openLibraryDatabase
import io.github.kdroidfilter.seforimlibrary.common.patch.LogicalContentHasher
import io.github.kdroidfilter.seforimlibrary.common.patch.PatchApplier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** The free space check failed: [required] bytes are needed next to the database, [available] are free. */
class NotEnoughSpaceException(val required: Long, val available: Long, message: String) : IllegalStateException(message)

/**
 * Android variant of SeforimLibrary (381fbea) `delta-updater/.../DeltaApplierClient.kt`: the same gates around
 * [PatchApplier] (schema version, `fromContentHash` before, `toContentHash` inside the transaction, free space, a
 * marker that doubles as a lock), with one difference that a phone needs.
 *
 * Desktop copies the whole seforim.db to `seforim.db.backup` before applying, so it can restore it when Lucene or the
 * catalog fails after the COMMIT: on a phone that is 8 GB more storage. Here the database is never copied:
 *  - the SQLite part is one transaction on the live file. Until COMMIT the app keeps reading the old content, and a
 *    crash or kill rolls it back on the next open (SQLite's journal/WAL), so the old database stays usable until the
 *    new one is verified, with only the journal as extra space;
 *  - what follows the COMMIT (Lucene, catalog, release_info.txt) is idempotent and goes forward instead of back. The
 *    marker records the stage ([Stage.Index] once committed) and the next run resumes from it ([pending]).
 * A run that crashed between COMMIT and the marker update is recognised by its content hash: equal to the manifest's
 * `toContentHash`, so it is treated as committed.
 */
class DeltaApplierClient(
    private val logger: Logger = Logger.withTag("DeltaApplierClient"),
    private val hashProgress: (rowsDone: Long, rowsTotal: Long, afterApply: Boolean) -> Unit = { _, _, _ -> },
    private val checkCancelled: () -> Unit = {},
) {

    data class Result(
        /** null when the delta had already been committed by an earlier run. */
        val applied: PatchApplier.Result?,
        val markerPath: Path,
    )

    /** Where a delta stands: [Sqlite] while its transaction runs, [Index] once committed (Lucene/catalog pending). */
    enum class Stage { Sqlite, Index }

    /** A delta left unfinished by an earlier run (what the marker says). */
    data class Pending(val fromVersion: Int, val toVersion: Int, val stage: Stage, val manifestUrl: String?, val releaseTag: String?)

    /**
     * Applies [patchDb] to [seforimDb]. [entry] and [releaseTag] are written into the marker so an interrupted run can
     * be resumed without the release server. Returns once committed (or found committed); throws when a gate fails,
     * after rolling back, with nothing changed.
     */
    fun apply(
        seforimDb: Path,
        patchDb: Path,
        manifest: DeltaManifest,
        entry: DeltaEntry? = null,
        releaseTag: String? = null,
    ): Result {
        val marker = markerPath(seforimDb)
        val pending = pending(seforimDb)
        if (pending != null && (pending.fromVersion != manifest.fromVersion || pending.toVersion != manifest.toVersion)) {
            throw IllegalStateException(
                "Apply marker already present at $marker for v${pending.fromVersion} → v${pending.toVersion}: " +
                    "finish that delta before applying v${manifest.fromVersion} → v${manifest.toVersion}.",
            )
        }
        if (pending?.stage == Stage.Index) return Result(null, marker)

        openLibraryDatabase(seforimDb.toFile()).use { db ->
            // 1. Schema-version gate (desktop): a delta produced against another schema would mix schemas.
            val localSchemaVersion = readSchemaMetaInt(db, "db_schema_version")
            if (localSchemaVersion != null && localSchemaVersion != manifest.fromSchemaVersion && localSchemaVersion != manifest.toSchemaVersion) {
                throw IllegalStateException(
                    "Local DB schema_meta.db_schema_version=$localSchemaVersion does not match " +
                        "manifest.fromSchemaVersion=${manifest.fromSchemaVersion}. " +
                        "Refusing to apply — request a full bundle download instead.",
                )
            }

            // 2. Current logical content hash: the delta's starting point, or already its end point.
            val currentHash = LogicalContentHasher(progress = { done, total -> hashProgress(done, total, false) }, checkCancelled = checkCancelled).compute(db)
            if (currentHash == manifest.toContentHash) {
                logger.w { "seforim.db already matches v${manifest.toVersion}: an earlier run committed it" }
                writeMarker(marker, manifest, Stage.Index, entry, releaseTag)
                return Result(null, marker)
            }
            if (currentHash != manifest.fromContentHash) {
                // The SQLite part is atomic: a marker over a database at neither end of its delta was left by another
                // database (one replaced since), and offering to resume it on every check blocked updates for good
                if (pending != null) runCatching { Files.deleteIfExists(marker) }
                throw IllegalStateException(
                    "Local DB content_hash ($currentHash) does not match manifest.fromContentHash " +
                        "(${manifest.fromContentHash}). Refusing to apply.",
                )
            }
            if (localSchemaVersion != null && localSchemaVersion != manifest.fromSchemaVersion) {
                throw IllegalStateException(
                    "Local DB schema_meta.db_schema_version=$localSchemaVersion does not match " +
                        "manifest.fromSchemaVersion=${manifest.fromSchemaVersion}. " +
                        "Refusing to apply — request a full bundle download instead.",
                )
            }

            // 3. Disk-space pre-flight, then the marker (the lock: CREATE_NEW unless resuming this same delta).
            assertEnoughFreeSpace(seforimDb, patchDb)
            writeMarker(marker, manifest, Stage.Sqlite, entry, releaseTag, createNew = pending == null)
            logger.i { "Marker at $marker; applying v${manifest.fromVersion} → v${manifest.toVersion} in place" }

            try {
                // FK actions (cascades, SET NULL) as on desktop; the pragma is a no-op inside a transaction.
                db.setForeignKeyConstraintsEnabled(true)
                val hasher = LogicalContentHasher(progress = { done, total -> hashProgress(done, total, true) }, checkCancelled = checkCancelled)
                val applied = PatchApplier(logger, hasher, checkCancelled).apply(db, patchDb, manifest.toContentHash)
                writeMarker(marker, manifest, Stage.Index, entry, releaseTag)
                // The transaction's WAL frames can be gigabytes: give the space back now if no reader holds them.
                runCatching { db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() } }
                return Result(applied, marker)
            } catch (t: Throwable) {
                // Rolled back by PatchApplier: the database is as before, so is no delta in flight.
                runCatching { Files.deleteIfExists(marker) }
                logger.e(t) { "Apply failed — transaction rolled back, marker cleared" }
                throw t
            }
        }
    }

    /**
     * Removes the marker once everything after the COMMIT (Lucene, catalog) is done. Until then the next run resumes
     * the delta from its [Stage.Index].
     */
    fun finalizeApply(seforimDb: Path) {
        runCatching { Files.deleteIfExists(markerPath(seforimDb)) }
        // A backup left by a desktop-style apply (another copy of this library) is of no use here.
        runCatching { Files.deleteIfExists(seforimDb.resolveSibling("${seforimDb.fileName}.backup")) }
    }

    /** The unfinished delta the marker describes, or null. */
    fun pending(seforimDb: Path): Pending? {
        val marker = markerPath(seforimDb)
        if (!Files.exists(marker)) return null
        val values = runCatching { Files.readAllLines(marker) }.getOrDefault(emptyList())
            .flatMap { line -> line.split(' ') }
            .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        val from = values["from"]?.toIntOrNull() ?: return null
        val to = values["to"]?.toIntOrNull() ?: return null
        val stage = if (values["stage"] == "index") Stage.Index else Stage.Sqlite
        return Pending(from, to, stage, values["manifestUrl"], values["tag"])
    }

    /**
     * Desktop restores its backup here. Nothing to restore on Android (no backup, the SQLite part is atomic): returns
     * whether an unfinished delta waits to be resumed.
     */
    fun recoverIfNeeded(seforimDb: Path): Boolean = pending(seforimDb) != null

    private fun markerPath(seforimDb: Path): Path = seforimDb.resolveSibling("${seforimDb.fileName}.applying")

    /** `from=1 to=2 stage=index manifestUrl=… tag=…`: desktop's `from=X to=Y` with what resuming needs. */
    private fun writeMarker(marker: Path, manifest: DeltaManifest, stage: Stage, entry: DeltaEntry?, releaseTag: String?, createNew: Boolean = false) {
        val text = buildString {
            append("from=").append(manifest.fromVersion).append(" to=").append(manifest.toVersion)
            append(" stage=").append(if (stage == Stage.Index) "index" else "sqlite")
            entry?.manifestUrl?.let { append(" manifestUrl=").append(it) }
            releaseTag?.let { append(" tag=").append(it) }
        }
        if (createNew) {
            // Two runs that both passed the existence check cannot both hold the marker.
            Files.write(marker, text.toByteArray(), java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)
        } else {
            val tmp = marker.resolveSibling("${marker.fileName}.tmp")
            Files.write(tmp, text.toByteArray())
            Files.move(tmp, marker, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    /**
     * Reads a single integer-valued key from `seforim.db.schema_meta`. Returns `null` when the key (or the table) is
     * absent — older DBs never wrote one, so absence means "trust the operator" (desktop).
     */
    private fun readSchemaMetaInt(db: SQLiteDatabase, key: String): Int? = runCatching {
        db.rawQuery("SELECT value FROM schema_meta WHERE key = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getString(0)?.toIntOrNull() else null }
    }.getOrNull()

    /**
     * Desktop requires room for a backup copy plus the patch plus 64 MiB. Without the copy, what the apply needs is
     * the rollback journal or WAL of the transaction: the pages it rewrites, bounded here by twice the patch plus the
     * same 64 MiB. A shortfall found later still fails safely (SQLITE_FULL rolls the transaction back).
     */
    internal fun assertEnoughFreeSpace(seforimDb: Path, patchDb: Path) {
        val patchSize = if (Files.exists(patchDb)) Files.size(patchDb) else 0L
        val required = 2 * patchSize + HEADROOM_BYTES
        val available = seforimDb.toAbsolutePath().parent.toFile().usableSpace
        if (available < required) {
            throw NotEnoughSpaceException(
                required,
                available,
                "Not enough free space to apply delta: required ${humanBytes(required)} " +
                    "(journal for a ${humanBytes(patchSize)} patch + ${humanBytes(HEADROOM_BYTES)} headroom), " +
                    "available ${humanBytes(available)} on ${seforimDb.toAbsolutePath().parent}",
            )
        }
    }

    private fun humanBytes(n: Long): String {
        if (n < 1024) return "$n B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var d = n.toDouble() / 1024.0
        var i = 0
        while (d >= 1024.0 && i < units.size - 1) {
            d /= 1024.0
            i++
        }
        return "%.1f %s".format(d, units[i])
    }

    companion object {
        const val HEADROOM_BYTES: Long = 64L * 1024 * 1024
    }
}
