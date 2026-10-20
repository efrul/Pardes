package io.github.kdroidfilter.seforimlibrary.deltaupdater

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import co.touchlab.kermit.Logger
import io.github.kdroidfilter.seforimapp.android.delta.forEachByIntKey
import io.github.kdroidfilter.seforimapp.android.delta.KEY_PAGE_ROWS
import io.github.kdroidfilter.seforimapp.android.delta.openLibraryDatabase
import io.github.kdroidfilter.seforimapp.android.delta.queryWide
import java.nio.file.Path

/**
 * Android variant of SeforimLibrary (7ddf045) `delta-updater/.../LuceneUpdater.kt`, the read side of the Lucene delta:
 *  - `patch.delete_line.id` → [DeleteSink.deleteLineById];
 *  - `patch.upsert_line.*` → [UpsertSink.upsertLine];
 *  - books in `upsert_book` without any `upsert_line` (metadata-only changes) → every line of theirs from the live
 *    database, so the stored book fields of their documents follow.
 * Same API and semantics; the rows are read page by page on their id through android.database (DeltaPaging.kt).
 *
 * [SinkSession] gains [SinkSession.commit]: desktop's session commits on close, even when [applyTo] threw half way
 * through, which would leave the index with part of a delta. Here the orchestrator commits after a full pass, and
 * close() without commit discards the session's changes (the caller's onClose rolls the IndexWriter back).
 */
class LuceneUpdater(
    private val logger: Logger = Logger.withTag("LuceneUpdater"),
    private val checkCancelled: () -> Unit = {},
) {

    fun interface DeleteSink {
        fun deleteLineById(lineId: Long)
    }

    fun interface UpsertSink {
        fun upsertLine(line: PatchLine)
    }

    /**
     * Bundles a [DeleteSink] + [UpsertSink] with an explicit [commit] and a close hook. [onClose] receives whether
     * [commit] ran, so the caller commits or rolls back its IndexWriter accordingly.
     */
    class SinkSession(
        val delete: DeleteSink,
        val upsert: UpsertSink,
        private val onCommit: () -> Unit = {},
        private val onClose: (committed: Boolean) -> Unit = {},
    ) : AutoCloseable {
        private var committed = false

        fun commit() {
            onCommit()
            committed = true
        }

        override fun close() = onClose(committed)
    }

    data class PatchLine(
        val id: Long,
        val bookId: Long,
        val lineIndex: Int,
        val content: String,
        val heRef: String?,
    )

    data class Stats(val deletes: Int, val upserts: Int)

    fun applyTo(
        patchDb: Path,
        deletes: DeleteSink,
        upserts: UpsertSink,
        liveDbPath: Path? = null,
    ): Stats = SQLiteDatabase.openDatabase(patchDb.toString(), null, SQLiteDatabase.OPEN_READONLY).use { patch ->
        val deleted = forEachDelete(patch, deletes)
        val upserted = forEachUpsert(patch, upserts)
        // Book-level metadata changes (title, categoryId, orderIndex, isBaseBook…) without line edits: re-index every
        // line of those books so the documents' stored book fields stay in sync (desktop).
        val metaReindexed = if (liveDbPath != null) reindexBooksWithMetadataOnlyChange(patch, liveDbPath, upserts) else 0
        logger.i { "Lucene delta — deletes=$deleted, upserts=$upserted, meta-reindexed lines=$metaReindexed" }
        Stats(deleted, upserted + metaReindexed)
    }

    private fun reindexBooksWithMetadataOnlyChange(patch: SQLiteDatabase, liveDbPath: Path, sink: UpsertSink): Int {
        if (!hasTable(patch, "upsert_book")) return 0
        val hasLines = hasTable(patch, "upsert_line")
        val booksTouched = patch.rawQuery(
            if (hasLines) {
                "SELECT b.id FROM upsert_book b WHERE NOT EXISTS (SELECT 1 FROM upsert_line ul WHERE ul.bookId = b.id)"
            } else {
                "SELECT id FROM upsert_book"
            },
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        if (booksTouched.isEmpty()) return 0
        var n = 0
        openLibraryDatabase(liveDbPath.toFile()).use { live ->
            for (bookId in booksTouched) {
                // Paged on (lineIndex, rowid), the order of the (bookId, lineIndex) index: a range scan per page.
                var last: Pair<Long, Long>? = null
                while (true) {
                    val after = last?.let { (index, id) -> " AND (lineIndex, id) > ($index, $id)" }.orEmpty()
                    var rows = 0
                    live.queryWide("SELECT id, bookId, lineIndex, content, heRef FROM line WHERE bookId = $bookId$after ORDER BY lineIndex, id LIMIT $KEY_PAGE_ROWS").use { c ->
                        while (c.moveToNext()) {
                            sink.upsertLine(PatchLine(id = c.getLong(0), bookId = c.getLong(1), lineIndex = c.getInt(2), content = c.getString(3), heRef = c.stringOrNull(4)))
                            last = c.getLong(2) to c.getLong(0)
                            rows++
                            if (++n % CANCEL_CHECK_EVERY == 0) checkCancelled()
                        }
                    }
                    if (rows < KEY_PAGE_ROWS) break
                }
            }
        }
        return n
    }

    private fun forEachDelete(patch: SQLiteDatabase, sink: DeleteSink): Int {
        if (!hasTable(patch, "delete_line")) return 0
        var n = 0
        patch.forEachByIntKey("SELECT id FROM delete_line", "id", 0, "id") { c ->
            sink.deleteLineById(c.getLong(0))
            if (++n % CANCEL_CHECK_EVERY == 0) checkCancelled()
        }
        return n
    }

    private fun forEachUpsert(patch: SQLiteDatabase, sink: UpsertSink): Int {
        if (!hasTable(patch, "upsert_line")) return 0
        var n = 0
        patch.forEachByIntKey("SELECT id, bookId, lineIndex, content, heRef FROM upsert_line", "id", 0, "id") { c ->
            sink.upsertLine(PatchLine(id = c.getLong(0), bookId = c.getLong(1), lineIndex = c.getInt(2), content = c.getString(3), heRef = c.stringOrNull(4)))
            if (++n % CANCEL_CHECK_EVERY == 0) checkCancelled()
        }
        return n
    }

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

    private fun Cursor.stringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

    private companion object {
        const val CANCEL_CHECK_EVERY = 1_000
    }
}
