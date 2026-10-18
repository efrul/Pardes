package io.github.kdroidfilter.seforimlibrary.common.patch

import android.database.sqlite.SQLiteDatabase
import co.touchlab.kermit.Logger
import java.nio.file.Path

/**
 * Android variant of SeforimLibrary (381fbea) `generator/common/.../patch/PatchApplier.kt`: applies a `patch.db` onto
 * the live `seforim.db` in one transaction, with the same steps, SQL and checks as desktop:
 *
 *  1. Snapshot pre-apply FK violation count.
 *  2. `ATTACH 'patch.db' AS patch`.
 *  3. `PRAGMA defer_foreign_keys = ON` (tocEntry.lineId ↔ line.tocEntryId form a cycle).
 *  4. Migrations (DDL), then upserts in FK order (`ON CONFLICT … DO UPDATE` / `DO NOTHING`), then deletes in reverse.
 *  5. The FK violation count must not grow; the logical content hash must equal [apply]'s expectedToContentHash.
 *  6. COMMIT — only now do the app's own connections see the new content (WAL readers keep the old snapshot until then).
 *
 * android.database instead of JDBC: the transaction is [SQLiteDatabase.beginTransactionNonExclusive], which pins one
 * connection, and ATTACH goes through a compiled statement inside it (SQLiteDatabase.execSQL would try to switch the
 * journal mode for an ATTACH, which fails inside a transaction). SQLite ≥ 3.21 (Android 11 has 3.28) allows ATTACH in a
 * transaction. [checkCancelled] is called between statements and may throw: the transaction is then rolled back.
 */
class PatchApplier(
    private val logger: Logger = Logger.withTag("PatchApplier"),
    private val hasher: LogicalContentHasher = LogicalContentHasher(),
    private val checkCancelled: () -> Unit = {},
) {

    data class Result(
        val migrationsApplied: Int,
        val upsertCounts: Map<String, Int>,
        val deleteCounts: Map<String, Int>,
    )

    fun apply(
        db: SQLiteDatabase,
        patchDb: Path,
        expectedToContentHash: String? = null,
    ): Result {
        val result: Result
        db.beginTransactionNonExclusive()
        try {
            val preFkCount = countFkViolations(db)
            attach(db, patchDb)
            assertPatchSchemaCompatible(db)
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            val migrations = runMigrations(db)
            val upserts = runUpserts(db)
            val deletes = runDeletes(db)
            checkCancelled()
            val postFkCount = countFkViolations(db)
            check(postFkCount <= preFkCount) {
                "Patch introduced ${postFkCount - preFkCount} new FK violations (pre=$preFkCount, post=$postFkCount)"
            }
            if (preFkCount > 0) {
                logger.w { "DB carries $preFkCount pre-existing FK violations — tolerated, not introduced by this patch." }
            }

            if (expectedToContentHash != null) {
                val actual = hasher.compute(db)
                if (actual != expectedToContentHash) {
                    throw IllegalStateException(
                        "Logical content hash after apply ($actual) does not match expected ($expectedToContentHash)",
                    )
                }
            }
            checkCancelled()
            db.setTransactionSuccessful()
            result = Result(migrations, upserts, deletes)
        } catch (t: Throwable) {
            logger.e(t) { "Patch apply failed; rolled back transaction" }
            throw t
        } finally {
            // Commits when marked successful, rolls back otherwise.
            db.endTransaction()
            runCatching { detach(db) }
        }
        logger.i { "Patch applied — migrations=${result.migrationsApplied}, upserts=${result.upsertCounts}, deletes=${result.deleteCounts}" }
        return result
    }

    private fun attach(db: SQLiteDatabase, patchDb: Path) {
        db.compileStatement("ATTACH DATABASE ? AS patch").use { st ->
            st.bindString(1, patchDb.toAbsolutePath().toString())
            st.execute()
        }
    }

    private fun detach(db: SQLiteDatabase) {
        db.compileStatement("DETACH DATABASE patch").use { it.execute() }
    }

    private fun runMigrations(db: SQLiteDatabase): Int {
        val statements = db.rawQuery("SELECT sql FROM patch.migrations ORDER BY version ASC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        statements.forEach { sql ->
            checkCancelled()
            db.execSQL(sql)
        }
        return statements.size
    }

    private fun runUpserts(db: SQLiteDatabase): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (table in PATCH_TABLES_IN_FK_ORDER) {
            checkCancelled()
            if (!patchHasTable(db, "upsert_${table.name}")) continue
            val cols = PatchDbSchema.readTableInfo(db, "patch", "upsert_${table.name}").map { it.name }
            if (cols.isEmpty()) continue
            val colsCsv = cols.joinToString(",") { "\"$it\"" }
            val pkCsv = table.primaryKey.joinToString(",") { "\"$it\"" }
            val nonPkCols = cols.filter { it !in table.primaryKey }
            val conflictClause = if (!table.updatable || nonPkCols.isEmpty()) {
                "ON CONFLICT($pkCsv) DO NOTHING"
            } else {
                "ON CONFLICT($pkCsv) DO UPDATE SET " +
                    nonPkCols.joinToString(",") { "\"$it\" = excluded.\"$it\"" }
            }
            val sql = """
                INSERT INTO "${table.name}" ($colsCsv)
                SELECT $colsCsv FROM patch."upsert_${table.name}"
                WHERE true
                $conflictClause
            """.trimIndent()
            val n = db.compileStatement(sql).use { it.executeUpdateDelete() }
            counts[table.name] = n
            if (n > 0) logger.d { "Upserted $n row(s) into ${table.name}" }
        }
        return counts
    }

    private fun runDeletes(db: SQLiteDatabase): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (table in PATCH_TABLES_IN_FK_ORDER.asReversed()) {
            checkCancelled()
            if (!patchHasTable(db, "delete_${table.name}")) continue
            if (table.primaryKey.isEmpty()) continue
            val pkCsv = table.primaryKey.joinToString(",") { "\"$it\"" }
            val sql = if (table.primaryKey.size == 1) {
                val k = "\"${table.primaryKey[0]}\""
                "DELETE FROM \"${table.name}\" WHERE $k IN (SELECT $k FROM patch.\"delete_${table.name}\")"
            } else {
                // SQLite supports tuple IN: WHERE (a,b) IN (SELECT a,b FROM …).
                "DELETE FROM \"${table.name}\" WHERE ($pkCsv) IN (SELECT $pkCsv FROM patch.\"delete_${table.name}\")"
            }
            val n = db.compileStatement(sql).use { it.executeUpdateDelete() }
            counts[table.name] = n
            if (n > 0) logger.d { "Deleted $n row(s) from ${table.name}" }
        }
        return counts
    }

    /** `SELECT COUNT(*) FROM pragma_foreign_key_check`, or the PRAGMA's rows counted if the table function is missing. */
    private fun countFkViolations(db: SQLiteDatabase): Long {
        checkCancelled()
        return runCatching {
            db.rawQuery("SELECT COUNT(*) FROM pragma_foreign_key_check", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }.getOrElse {
            db.rawQuery("PRAGMA foreign_key_check", null).use { c -> c.count.toLong() }
        }
    }

    /**
     * Reads `patch.patch_meta.schema_version` and refuses to apply a patch whose schema is newer than
     * [PatchDbSchema.CURRENT_VERSION] (desktop: an older client must not silently mis-apply a future-schema patch).
     */
    private fun assertPatchSchemaCompatible(db: SQLiteDatabase) {
        val patchSchemaVersion = readPatchMetaInt(db, "schema_version")
            ?: throw IllegalStateException(
                "patch.db is missing patch_meta.schema_version — refusing to apply " +
                    "(likely a corrupt or hand-built patch).",
            )
        if (patchSchemaVersion > PatchDbSchema.CURRENT_VERSION) {
            throw IllegalStateException(
                "patch.db carries schema_version=$patchSchemaVersion but this build " +
                    "of the applier only understands up to ${PatchDbSchema.CURRENT_VERSION}. " +
                    "Upgrade the client before applying this patch.",
            )
        }
    }

    private fun readPatchMetaInt(db: SQLiteDatabase, key: String): Int? =
        db.rawQuery("SELECT value FROM patch.patch_meta WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0)?.toIntOrNull() else null
        }

    private fun patchHasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM patch.sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }
}
