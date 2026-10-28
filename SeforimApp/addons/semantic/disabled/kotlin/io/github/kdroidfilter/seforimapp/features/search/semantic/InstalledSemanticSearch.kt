package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import java.nio.file.Path

/**
 * Pardes's own semantic search, on Dicta's open model (SEMANTIC_SEARCH.md), in place of Zayit's private package: the
 * bridge to IndependentSemanticSearch and HybridSearchEngine, which depend on no proprietary package.
 */
val installedSemanticSearch: SemanticSearchFeature? =
    object : SemanticSearchFeature {
        override fun hybrid(
            lexical: SearchEngine,
            indexDir: Path,
            resolveLine: suspend (lineId: Long, query: String) -> LineHit?,
        ): SearchEngine = HybridSearchEngine(lexical, indexDir, resolveLine)
    }
