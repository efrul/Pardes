package io.github.kdroidfilter.seforimapp.android.delta

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * Keyset paging over android.database (see DeltaSqlite.kt for why the delta code never runs one query over a whole big
 * table): the rows come out in exactly the order of `ORDER BY [orderBy]`, as one JDBC query would give them.
 */

/** Page size for tables keyed by a rowid (`id`, `lineId`…): each page is a range seek. */
internal const val KEY_PAGE_ROWS = 2_000

/** Page size for the composite-key tables (a few integers per row; no index matches their canonical order). */
internal const val ROW_VALUE_PAGE_ROWS = 50_000

/**
 * Rows of `[select] [WHERE [where] AND] key > last ORDER BY [orderBy] LIMIT n`, page after page. [key] must be unique
 * and not null (a rowid alias), so ordering by [orderBy] (which starts with it) equals ordering by [key]; [keyIndex] is
 * its position in the selected columns. Literal keys are inlined (they come from the database itself).
 */
internal inline fun SQLiteDatabase.forEachByIntKey(
    select: String,
    key: String,
    keyIndex: Int,
    orderBy: String,
    where: String? = null,
    pageRows: Int = KEY_PAGE_ROWS,
    block: (Cursor) -> Unit,
) {
    var last: Long? = null
    while (true) {
        val condition = listOfNotNull(where, last?.let { "$key > $it" }).joinToString(" AND ")
        val sql = buildString {
            append(select)
            if (condition.isNotEmpty()) append(" WHERE ").append(condition)
            append(" ORDER BY ").append(orderBy).append(" LIMIT ").append(pageRows)
        }
        var rows = 0
        queryWide(sql).use { c ->
            while (c.moveToNext()) {
                block(c)
                last = c.getLong(keyIndex)
                rows++
            }
        }
        if (rows < pageRows) return
    }
}

/**
 * Rows of `[select] WHERE (c1, …, cn) > (last row) ORDER BY c1, …, cn LIMIT n`, for a table whose [columns] (all
 * selected, in this order) are integers that are never null and unique together (the table has a primary key among
 * them). Throws if a cell turns out not to be an integer: SQLite would order it differently than the row value.
 */
internal inline fun SQLiteDatabase.forEachByRowValue(
    select: String,
    columns: List<String>,
    pageRows: Int = ROW_VALUE_PAGE_ROWS,
    block: (Cursor) -> Unit,
) {
    val tuple = columns.joinToString(",", "(", ")") { "\"$it\"" }
    val orderBy = columns.joinToString(",") { "\"$it\"" }
    var last: LongArray? = null
    while (true) {
        val sql = buildString {
            append(select)
            last?.let { values -> append(" WHERE ").append(tuple).append(" > ").append(values.joinToString(",", "(", ")")) }
            append(" ORDER BY ").append(orderBy).append(" LIMIT ").append(pageRows)
        }
        var rows = 0
        queryWide(sql).use { c ->
            while (c.moveToNext()) {
                block(c)
                last = LongArray(columns.size) { i ->
                    check(c.getType(i) == Cursor.FIELD_TYPE_INTEGER) { "Non-integer key in column ${columns[i]}" }
                    c.getLong(i)
                }
                rows++
            }
        }
        if (rows < pageRows) return
    }
}
