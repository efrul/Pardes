package io.github.kdroidfilter.seforimlibrary.search

import org.apache.lucene.index.FieldInvertState
import org.apache.lucene.search.CollectionStatistics
import org.apache.lucene.search.TermStatistics
import org.apache.lucene.search.similarities.BM25Similarity
import org.apache.lucene.search.similarities.Similarity

/**
 * The harness's knobs on the lexical side, set between searches (Bm25.kt): BM25's k1 and b, the base-book order boost
 * of mapScoreDocs, and the words required near each other. Read by the copy of LuceneSearchEngine.kt that
 * bm25-knob.patch changes; the app's never sees them.
 */
object Bm25Knob {
    @Volatile var current: Similarity = BM25Similarity()

    @Volatile var orderBoost = true

    @Volatile var requireNear = false

    /** The searchers' similarity: [current] at the time a query is weighed. Norms are BM25's, whatever k1 and b. */
    object Delegate : Similarity() {
        override fun computeNorm(state: FieldInvertState): Long = current.computeNorm(state)

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics,
        ): SimScorer = current.scorer(boost, collectionStats, *termStats)
    }
}
