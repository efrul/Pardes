package io.github.kdroidfilter.seforimapp.android.search

import android.util.Log
import io.github.kdroidfilter.seforimapp.android.data.CatalogAccess
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.data.SplitDatabase
import io.github.kdroidfilter.seforimapp.framework.search.titles.BookTitleIndex
import io.github.kdroidfilter.seforimapp.framework.search.titles.BookTitleIndexHolder
import io.github.kdroidfilter.seforimapp.framework.search.titles.BookTitleIndexLoader
import kotlinx.coroutines.runBlocking

/**
 * Pardes desktop's title index (framework/search/titles, compiled as-is) on the installed library, as desktop's
 * AppCoreBindings builds it: the catalog as [CatalogAccess.readCatalog] reads it (catalog.pb, or the database's tree
 * without it), and the authors, their other names and the books' acronyms read through [SplitDatabase], read only, as
 * [io.github.kdroidfilter.seforimapp.framework.search.AcronymFrequencyCache] reads the library.
 */
internal object TitleIndex {
    /** Built once, off the main thread, ahead of the first letter typed; null without a library. */
    fun holder(store: LibraryStore): BookTitleIndexHolder =
        BookTitleIndexHolder(onError = { Log.w(TAG, "title index", it) }) { load(store) }.also { it.warmUp() }

    private fun load(store: LibraryStore): BookTitleIndex? {
        if (!store.databaseFile.isFile) return null
        // On the holder's own thread, or a suggestion's (never the main one)
        val catalog = runBlocking { CatalogAccess.readCatalog(store) }
        if (catalog.rootCategories.isEmpty()) return null
        return SplitDatabase.openReader(store.databaseFile).use { db ->
            BookTitleIndexLoader.build(catalog) { sql, each ->
                db.rawQuery(sql).use { c ->
                    val row =
                        object : BookTitleIndexLoader.Row {
                            override fun long(column: Int) = c.getLong(column)

                            override fun string(column: Int): String? = c.getString(column)
                        }
                    while (c.moveToNext()) each(row)
                }
            }
        }
    }

    private const val TAG = "Zayit"
}
