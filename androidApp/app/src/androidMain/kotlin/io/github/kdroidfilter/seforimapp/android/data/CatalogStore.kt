package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog

/** The catalog of the installed library, read once (desktop CatalogCache): off the main thread. */
suspend fun CatalogAccess.Companion.load(store: LibraryStore): CatalogAccess = load(store.catalogFile) { fromDatabase(store) }

/** The catalog tree of the database, for a library without catalog.pb: categories, then each one's books in order. */
private suspend fun fromDatabase(store: LibraryStore): PrecomputedCatalog {
    val repo = store.repository()
    val books = store.catalogBooks()
    suspend fun category(id: Long, title: String, level: Int, parentId: Long?): CatalogCategory {
        val children = repo.getCategoryChildren(id).map { category(it.id, it.title, it.level, it.parentId) }
        return CatalogCategory(id, title, level, parentId, books[id].orEmpty(), children)
    }
    return PrecomputedCatalog(repo.getRootCategories().map { category(it.id, it.title, it.level, it.parentId) })
}
