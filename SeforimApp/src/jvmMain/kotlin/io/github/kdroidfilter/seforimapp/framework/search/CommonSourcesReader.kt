package io.github.kdroidfilter.seforimapp.framework.search

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.di.AppScope

/**
 * Pardes: [CommonSourcesQueries] on the library the app opens, with the base books of its catalog
 * (docs/COMMON_SOURCES_PLAN.md).
 */
@Inject
@SingleIn(AppScope::class)
class CommonSourcesReader(
    databasePathProvider: DatabasePathProvider,
    catalogCache: CatalogCache,
) {
    private val queries =
        CommonSourcesQueries(databasePathProvider::get) {
            catalogCache.getAllBooks()?.filter { it.isBaseBook }?.mapTo(HashSet()) { it.id }
        }

    /** The line of common sources of [page] ((lineId, bookId), the engine's order). */
    suspend fun line(
        page: List<Pair<Long, Long>>,
        baseOnly: Boolean,
    ): List<CommonSource> = queries.line(page, baseOnly)
}
