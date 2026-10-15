package io.github.kdroidfilter.seforimlibrary.deltaupdater

import android.database.sqlite.SQLiteDatabase
import co.touchlab.kermit.Logger
import io.github.kdroidfilter.seforimapp.android.delta.queryWide
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Android variant of SeforimLibrary (381fbea) `delta-updater/.../CatalogUpdater.kt`: pulls the fresh `catalog.pb` out
 * of `patch.db.blobs` (android.database instead of JDBC) and swaps it in atomically: written to `catalog.pb.tmp`,
 * synced, then renamed over the target, so a crash leaves the old catalog. The app's CatalogAccess reloads it on its
 * own (its cache is keyed on the file's size and date).
 */
class CatalogUpdater(
    private val logger: Logger = Logger.withTag("CatalogUpdater"),
) {

    /**
     * @return `true` if a catalog blob was found and written, `false` if the patch.db carried no blob for this key.
     */
    fun update(patchDb: Path, target: Path, blobName: String = "catalog.pb"): Boolean {
        val bytes = SQLiteDatabase.openDatabase(patchDb.toString(), null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.queryWide("SELECT content FROM blobs WHERE name = ?", arrayOf(blobName)).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
        } ?: run {
            logger.d { "patch.db has no blob '$blobName' — skipping catalog update" }
            return false
        }
        val tmp = target.resolveSibling("${target.fileName}.tmp")
        FileOutputStream(tmp.toFile()).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        logger.i { "Catalog '$blobName' written to $target (${bytes.size} bytes)" }
        return true
    }
}
