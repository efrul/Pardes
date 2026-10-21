package io.github.kdroidfilter.seforimlibrary.common.patch

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import co.touchlab.kermit.Logger
import io.github.kdroidfilter.seforimapp.android.delta.forEachByIntKey
import io.github.kdroidfilter.seforimapp.android.delta.forEachByRowValue
import io.github.kdroidfilter.seforimapp.android.delta.queryWide
import java.security.MessageDigest

/**
 * Android variant of SeforimLibrary (7ddf045) `generator/common/.../patch/LogicalContentHasher.kt`: the same logical
 * sha256 of a seforim.db (each tracked table in primary-key order, every cell type-tagged), so it matches the
 * `fromContentHash` / `toContentHash` the producer wrote into the manifest. The byte stream is identical to the JDBC
 * one (sqlite-jdbc getObject: INTEGER → its decimal digits, REAL → Double.toString, TEXT → UTF-8, BLOB → raw bytes).
 *
 * Only the reading differs: the rows come page by page in the same order (DeltaPaging.kt) instead of one query per
 * table, which an Android cursor would replay from the start at every window refill. [progress] gets the rows hashed
 * against an estimate (each table's max rowid); [checkCancelled] is called between pages and may throw.
 */
class LogicalContentHasher(
    private val tables: List<String> = DEFAULT_TABLES,
    private val logger: Logger = Logger.withTag("LogicalContentHasher"),
    private val progress: (rowsDone: Long, rowsTotal: Long) -> Unit = { _, _ -> },
    private val checkCancelled: () -> Unit = {},
) {

    fun compute(db: SQLiteDatabase): String {
        val md = MessageDigest.getInstance("SHA-256")
        val total = tables.sumOf { estimateRows(db, it) }.coerceAtLeast(1L)
        var done = 0L
        progress(0L, total)
        for (table in tables) {
            checkCancelled()
            md.update(" table:$table ".toByteArray())
            val info = PatchDbSchema.readTableInfo(db, "main", table)
            if (info.isEmpty()) continue // table not present
            val cols = info.map { it.name }.sorted() // canonical order = alphabetical
            md.update(cols.joinToString(",", prefix = "cols:").toByteArray())
            md.update(byteArrayOf(0x00))

            val colsSql = cols.joinToString(",") { "\"$it\"" }
            val pkOrder = if ("id" in cols) "id" else colsSql
            val select = "SELECT $colsSql FROM \"$table\""
            val row: (Cursor) -> Unit = { c ->
                for (i in cols.indices) encodeCell(md, c, i)
                md.update(0xFF.toByte())
                if (++done % PROGRESS_EVERY == 0L) {
                    progress(done, total)
                    checkCancelled()
                }
            }
            // A sole INTEGER primary key is the rowid: unique and never null, so paging on it keeps the ORDER BY.
            val rowidKey = info.singleOrNull { it.pk > 0 }?.takeIf { it.type.equals("INTEGER", ignoreCase = true) }?.name
            when {
                "id" in cols && rowidKey == "id" -> db.forEachByIntKey(select, "id", cols.indexOf("id"), pkOrder, block = row)
                "id" !in cols && rowidKey == cols.first() -> db.forEachByIntKey(select, "\"${cols.first()}\"", 0, pkOrder, block = row)
                "id" !in cols && info.any { it.pk > 0 } && info.all { it.notNull && it.type.equals("INTEGER", ignoreCase = true) } ->
                    db.forEachByRowValue(select, cols, block = row)
                // Small tables with text keys (author_alias, book_acronym, schema_meta…): one query.
                else -> db.queryWide("$select ORDER BY $pkOrder").use { c -> while (c.moveToNext()) row(c) }
            }
        }
        progress(total, total)
        val digest = md.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun encodeCell(md: MessageDigest, c: Cursor, i: Int) {
        when (c.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> md.update(byteArrayOf(0))
            Cursor.FIELD_TYPE_BLOB -> {
                md.update(byteArrayOf(1))
                md.update(c.getBlob(i))
            }
            Cursor.FIELD_TYPE_INTEGER -> {
                md.update(byteArrayOf(2))
                md.update(c.getLong(i).toString().toByteArray())
            }
            Cursor.FIELD_TYPE_FLOAT -> {
                md.update(byteArrayOf(2))
                md.update(c.getDouble(i).toString().toByteArray())
            }
            else -> {
                md.update(byteArrayOf(3))
                md.update(c.getString(i).toByteArray())
            }
        }
        md.update(byteArrayOf(0x1F)) // unit separator between cells
    }

    private fun estimateRows(db: SQLiteDatabase, table: String): Long =
        runCatching { db.rawQuery("SELECT max(rowid) FROM \"$table\"", null).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L } }
            .getOrDefault(0L)

    companion object {
        private const val PROGRESS_EVERY = 4_096L

        /** Tables tracked by the logical-hash. Order matters (foreign-key topology). Same list as desktop. */
        val DEFAULT_TABLES: List<String> = listOf(
            "source",
            "author",
            "topic",
            "pub_place",
            "pub_date",
            "connection_type",
            "category",
            "category_closure",
            "tocText",
            "license",
            "edition",
            "book",
            "book_topic",
            "book_author",
            "author_alias",
            "author_bio",
            "book_pub_place",
            "book_pub_date",
            "book_edition",
            "tocEntry",
            "line",
            "line_toc",
            "link",
            "book_has_links",
            "book_acronym",
            "alt_toc_structure",
            "alt_toc_entry",
            "line_alt_toc",
            "default_commentator",
            "default_targum",
            "schema_meta",
        )
    }
}
