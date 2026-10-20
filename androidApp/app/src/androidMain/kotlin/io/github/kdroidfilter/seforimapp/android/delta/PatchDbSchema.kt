package io.github.kdroidfilter.seforimlibrary.common.patch

import android.database.sqlite.SQLiteDatabase

/**
 * Android variant of SeforimLibrary (7ddf045) `generator/common/.../patch/PatchDbSchema.kt`: the part the client uses,
 * [CURRENT_VERSION] and [readTableInfo], read through android.database instead of JDBC. The DDL that creates a
 * patch.db belongs to the producer and stays on the desktop side. [ColumnInfo.pk] is added for the hasher's paging.
 */
internal object PatchDbSchema {
    const val CURRENT_VERSION: Int = 1

    /** Schema info for a single column ([pk]: 1-based position in the primary key, 0 when not part of it). */
    data class ColumnInfo(val name: String, val type: String, val notNull: Boolean, val pk: Int = 0)

    /** `PRAGMA <schema>.table_info(<table>)`, columns in declaration order. */
    fun readTableInfo(db: SQLiteDatabase, schemaAlias: String, table: String): List<ColumnInfo> =
        db.rawQuery("PRAGMA $schemaAlias.table_info(\"$table\")", null).use { c ->
            val name = c.getColumnIndexOrThrow("name")
            val type = c.getColumnIndexOrThrow("type")
            val notNull = c.getColumnIndexOrThrow("notnull")
            val pk = c.getColumnIndexOrThrow("pk")
            buildList {
                while (c.moveToNext()) {
                    add(ColumnInfo(c.getString(name), c.getString(type).orEmpty().ifBlank { "BLOB" }, c.getInt(notNull) == 1, c.getInt(pk)))
                }
            }
        }
}
