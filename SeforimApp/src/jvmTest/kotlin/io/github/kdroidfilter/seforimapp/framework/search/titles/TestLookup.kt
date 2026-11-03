package io.github.kdroidfilter.seforimapp.framework.search.titles

import io.github.kdroidfilter.seforimapp.framework.search.AcronymFrequencyCache
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimlibrary.dao.CatalogLoader
import java.nio.file.Paths

/** The lookup service of the library at [dbPath] as the app builds it: its lookup index, with the title index. */
fun appLookup(dbPath: String): LuceneLookupSearchService {
    val titles = BookTitleIndexHolder { CatalogLoader.loadCatalog(dbPath)?.let { BookTitleIndexLoader.load(it, dbPath) } }
    return LuceneLookupSearchService(
        Paths.get("$dbPath.lookup.lucene"),
        acronymCache = AcronymFrequencyCache { dbPath },
        titleIndex = titles::get,
    )
}
