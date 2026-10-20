package io.github.kdroidfilter.seforimapp.legacy.data

import io.github.kdroidfilter.seforimapp.android.data.CatalogAccess
import io.github.kdroidfilter.seforimapp.android.data.CatalogMenu
import io.github.kdroidfilter.seforimapp.android.data.catalogMenu
import io.github.kdroidfilter.seforimapp.android.data.catalogPresets
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import java.io.File

/** The Home's catalog row as the main app builds it (CatalogAccess over catalog.pb, else the database's tree). */
object Catalog {
    suspend fun menus(opened: Library.Opened): List<CatalogMenu> {
        val access = CatalogAccess.load(File(opened.file.parentFile, "catalog.pb")) { fromDatabase(opened) }
        return catalogPresets.mapNotNull { catalogMenu(access, it) }
    }

    /** The categories, each with its own books in order (the main app's CatalogStore fromDatabase, in two queries). */
    private fun fromDatabase(opened: Library.Opened): PrecomputedCatalog {
        class Row(val id: Long, val parentId: Long?, val title: String, val level: Int)
        val rows = opened.query("SELECT id, parentId, title, level FROM category ORDER BY orderIndex, title") {
            Row(it.getLong(0)!!, it.getLong(1), it.getString(2) ?: "", it.getLong(3)!!.toInt())
        }
        val books = opened.query("SELECT id, title, categoryId, orderIndex FROM book ORDER BY orderIndex, title") {
            CatalogBook(it.getLong(0)!!, it.getString(1)!!, it.getLong(2)!!, it.getLong(3)!!.toFloat())
        }.groupBy { it.categoryId }
        val children = rows.groupBy { it.parentId }
        fun node(r: Row): CatalogCategory = CatalogCategory(r.id, r.title, r.level, r.parentId, books[r.id].orEmpty(), children[r.id].orEmpty().map(::node))
        return PrecomputedCatalog(children[null].orEmpty().map(::node))
    }
}
