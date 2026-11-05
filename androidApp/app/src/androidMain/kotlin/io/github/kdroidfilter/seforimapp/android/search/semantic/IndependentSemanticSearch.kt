// Android variant of Pardes desktop's SeforimApp/src/jvmMain/.../features/search/semantic/IndependentSemanticSearch.kt,
// same package and class. A diff against it stays small; each change is marked "Android:":
//  - the vector index is opened with NIOFSDirectory: FSDirectory.open picks MMapDirectory on 64-bit, which needs
//    java.lang.foreign (absent on Android). The HNSW search reads its vectors through positional reads instead;
//  - the open model is only looked for next to the index: PARDES_MODEL_DIR / -Dpardes.model.dir do not exist on a phone;
//  - no legacy model: desktop runs it only when SEFORIM_MODEL_DIR names it, for a comparison, and it is not Pardes's;
//  - android.util.Log instead of desktop's logger.
package io.github.kdroidfilter.seforimapp.features.search.semantic

import android.util.Log
import org.apache.lucene.document.IntPoint
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.StoredFields
import org.apache.lucene.search.ConjunctionUtils
import org.apache.lucene.search.DocIdSetIterator
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnByteVectorQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.search.Weight
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NIOFSDirectory
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.PriorityQueue

data class DenseHit(
    val lineId: Long,
    val score: Float,
    // The line's book, as the index stores it (VectorIndexBuilder); -1 when it doesn't
    val bookId: Long = -1L,
)

class IndependentSemanticSearch(
    private val indexDir: Path,
    private val embeddingProvider: EmbeddingProvider,
    /** The weight of these dense hits in [HybridSearchEngine]'s RRF (the model's, [OpenModelSpec.hybridDenseWeight]). */
    val hybridWeight: Double = 1.0,
) : Closeable {
    // Android: NIOFSDirectory, never MMapDirectory
    private val directory: FSDirectory = NIOFSDirectory(indexDir)
    private val reader: DirectoryReader = DirectoryReader.open(directory)
    private val searcher: IndexSearcher = IndexSearcher(reader)

    fun search(
        query: String,
        k: Int = 150,
        baseOnly: Boolean = false,
        bookIds: Collection<Long>? = null,
    ): List<DenseHit> {
        val queryVec = embeddingProvider.embed(query)
        val filter: Query? =
            if (!bookIds.isNullOrEmpty()) {
                val intIds = bookIds.map { it.toInt() }
                IntPoint.newSetQuery("book_id", intIds)
            } else {
                null
            }

        val docScoreMap = HashMap<Int, Float>()
        val fields = if (baseOnly) listOf(FIELD_VEC_BASE) else listOf(FIELD_VEC, FIELD_VEC_BASE)
        // Made once for both fields: the filter's weight
        val filterWeight = filter?.let { searcher.createWeight(searcher.rewrite(it), ScoreMode.COMPLETE_NO_SCORES, 1f) }
        for (field in fields) {
            for (sd in searchField(field, queryVec, k, filter, filterWeight)) {
                docScoreMap.merge(sd.doc, sd.score, ::maxOf)
            }
        }

        val sorted = docScoreMap.entries.sortedByDescending { it.value }.take(k)
        val hits = ArrayList<DenseHit>(sorted.size)
        val seenLineIds = HashSet<Long>()

        // One StoredFields per search: Lucene's is not thread-safe, and searches run at once (two windows, a search
        // and a smart find in a book)
        val storedFields: StoredFields = reader.storedFields()
        for (entry in sorted) {
            val doc = storedFields.document(entry.key, LINE_AND_BOOK)
            val lineId = doc.get("line_id")?.toLongOrNull() ?: continue
            if (seenLineIds.add(lineId)) {
                hits.add(DenseHit(lineId, entry.value, doc.get("book_id")?.toLongOrNull() ?: -1L))
            }
        }

        return hits
    }

    /**
     * The [k] vectors of [field] closest to [target], among the docs of [filter] when there is one (its weight
     * [filterWeight]). A filter that keeps few of the field's vectors (a book, an author, a small category: at most
     * [EXACT_LIMIT], and at most one in [EXACT_SHARE] of them) is searched exactly, every vector of it scored. There
     * Lucene's filtered graph search walks far for each vector it keeps, costs more and finds less (the מהר״ל's 18
     * books, 54,883 of 5,483,563 vectors: 22 to 128 ms and 87 to 100% of the closest 150, against 17 to 29 ms and all of
     * them), and falls back to that same exact search whenever its walk finds fewer than [k], as in a cluster of
     * identical lines (a heading repeated in hundreds of books). A filter keeping a large share goes to Lucene: the
     * תלמוד's base books, 81,269 of the 242,109 base vectors, take it 6 to 16 ms, and 25 to 56 ms exactly.
     */
    private fun searchField(
        field: String,
        target: ByteArray,
        k: Int,
        filter: Query?,
        filterWeight: Weight?,
    ): Array<ScoreDoc> {
        if (filter == null || filterWeight == null) return searcher.search(KnnByteVectorQuery(field, target, k), k).scoreDocs
        var vectors = 0L
        for (leaf in reader.leaves()) vectors += leaf.reader().getByteVectorValues(field)?.size() ?: 0
        val limit = minOf(EXACT_LIMIT.toLong(), vectors / EXACT_SHARE).toInt()
        if (filteredVectors(field, filterWeight, limit) > limit) {
            return searcher.search(KnnByteVectorQuery(field, target, k, filter), k).scoreDocs
        }
        return exactSearch(field, target, k, filterWeight)
    }

    /** How many of [field]'s vectors [filterWeight] keeps, counted up to [limit] + 1 (past it, the count stops). */
    private fun filteredVectors(
        field: String,
        filterWeight: Weight,
        limit: Int,
    ): Int {
        var count = 0
        for (leaf in reader.leaves()) {
            val vectors = leaf.reader().getByteVectorValues(field)?.iterator() ?: continue
            val docs = filterWeight.scorer(leaf)?.iterator() ?: continue
            val both = ConjunctionUtils.intersectIterators(listOf(docs, vectors))
            while (both.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                if (++count > limit) return count
            }
        }
        return count
    }

    /** The [k] vectors of [field] closest to [target] among the docs of [filterWeight], each one scored. */
    private fun exactSearch(
        field: String,
        target: ByteArray,
        k: Int,
        filterWeight: Weight,
    ): Array<ScoreDoc> {
        // The k best so far, the worst first
        val best = PriorityQueue<ScoreDoc>(k + 1, compareBy<ScoreDoc> { it.score }.thenByDescending { it.doc })
        for (leaf in reader.leaves()) {
            val scorer = leaf.reader().getByteVectorValues(field)?.scorer(target) ?: continue
            val docs = filterWeight.scorer(leaf)?.iterator() ?: continue
            val live = leaf.reader().liveDocs
            val both = ConjunctionUtils.intersectIterators(listOf(docs, scorer.iterator()))
            var doc = both.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                if (live == null || live[doc]) {
                    val score = scorer.score()
                    if (best.size < k) {
                        best.add(ScoreDoc(leaf.docBase + doc, score))
                    } else if (score > best.peek().score) {
                        best.poll()
                        best.add(ScoreDoc(leaf.docBase + doc, score))
                    }
                }
                doc = both.nextDoc()
            }
        }
        return best.sortedByDescending { it.score }.toTypedArray()
    }

    // The vectors of the last queries: a search's found lines are weighed against the same query many times (each page
    // of results, each line the reader shows)
    private val queryVectors =
        object : LinkedHashMap<String, FloatArray>(QUERY_CACHE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > QUERY_CACHE
        }

    private fun queryVector(query: String): FloatArray? =
        synchronized(queryVectors) { queryVectors[query] }
            ?: embeddingProvider.embedQuery(query)?.also { vector -> synchronized(queryVectors) { queryVectors[query] = vector } }

    // The vectors of found lines' passages, whatever the query: a results page's lines come back in its preview, in the
    // reader and in the next searches
    private val passageCache =
        object : LinkedHashMap<String, FloatArray>(PASSAGE_CACHE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > PASSAGE_CACHE
        }

    // [passages]' vectors, each embedded once and in one batch, the known ones from the cache
    private fun passageVectors(passages: List<String>): List<FloatArray>? {
        val vectors = HashMap<String, FloatArray>()
        synchronized(passageCache) { for (passage in passages) passageCache[passage]?.let { vectors[passage] = it } }
        val missing = passages.filterNot(vectors::containsKey).distinct()
        if (missing.isNotEmpty()) {
            val fresh = embeddingProvider.embedPassages(missing) ?: return null
            synchronized(passageCache) {
                missing.forEachIndexed { i, passage ->
                    vectors[passage] = fresh[i]
                    passageCache[passage] = fresh[i]
                }
            }
        }
        return passages.map(vectors::getValue)
    }

    /**
     * For each of [texts] (found lines, plain), the passage closest in meaning to [query], a verbatim part of it
     * ([Passages]); null for a text that is a single passage. Null when the model can't embed passages. Of a text of more
     * than [MAX_PASSAGES] passages, the first ones are weighed (what its vector holds: the corpus's embedding reads a
     * line's first tokens) and, as many more, those holding one of [matches] (the query's words in a text).
     */
    fun bestPassages(
        query: String,
        texts: List<String>,
        matches: (String) -> List<IntRange> = { emptyList() },
    ): List<String?>? {
        val ranges = texts.map { text -> candidates(Passages.split(text)) { matches(text) } }
        val passages = texts.indices.flatMap { i -> if (ranges[i].size > 1) ranges[i].map(texts[i]::substring) else emptyList() }
        if (passages.isEmpty()) return texts.map { null }
        val query = queryVector(query) ?: return null
        val vectors = passageVectors(passages) ?: return null
        var next = 0
        return texts.indices.map { i ->
            if (ranges[i].size <= 1) {
                null
            } else {
                val scores = ranges[i].map { dot(query, vectors[next++]) }
                texts[i].substring(ranges[i][scores.indices.maxBy(scores::get)])
            }
        }
    }

    override fun close() {
        reader.close()
        directory.close()
        embeddingProvider.close()
    }

    companion object {
        private const val TAG = "PardesSemantic"

        /** The open model's folder name next to the databases. */
        const val OPEN_MODEL_DIR_NAME = "pardes-model"

        private val LINE_AND_BOOK = setOf("line_id", "book_id")

        private const val FIELD_VEC = "vec"
        private const val FIELD_VEC_BASE = "vec_base"

        /**
         * A filter keeping at most this many of a field's vectors is searched exactly ([searchField]): some 45 ms on one
         * core without SIMD (0.45 ms per thousand), about what Lucene's filtered graph search costs on such a filter.
         */
        internal const val EXACT_LIMIT = 100_000

        /** ...and keeping at most one in this many of them: above it, Lucene's graph search finds them quickly. */
        internal const val EXACT_SHARE = 8

        /** Queries whose vectors [bestPassages] keeps. */
        private const val QUERY_CACHE = 16

        /** Passages whose vectors [bestPassages] keeps: some 20 pages of results, under 4 MB. */
        private const val PASSAGE_CACHE = 2048

        /** Of a longer text, [bestPassages] weighs this many first passages, and at most as many of the others. */
        private const val MAX_PASSAGES = 32

        // Of [passages], those [bestPassages] weighs: all, or the first MAX_PASSAGES and as many of the others holding
        // one of [matches] (asked for long texts only)
        private fun candidates(
            passages: List<IntRange>,
            matches: () -> List<IntRange>,
        ): List<IntRange> {
            if (passages.size <= MAX_PASSAGES) return passages
            val words = matches()
            val holding = passages.drop(MAX_PASSAGES).filter { p -> words.any { it.first <= p.last && it.last >= p.first } }
            return passages.take(MAX_PASSAGES) + holding.take(MAX_PASSAGES)
        }

        private fun dot(
            a: FloatArray,
            b: FloatArray,
        ): Float {
            var sum = 0f
            for (i in a.indices) sum += a[i] * b[i]
            return sum
        }

        // Android: next to the index only (no PARDES_MODEL_DIR or -Dpardes.model.dir on a phone)
        fun findOpenModel(indexDir: Path): OpenModelSpec? {
            val dir = indexDir.resolveSibling(OPEN_MODEL_DIR_NAME)
            return try {
                OpenModelSpec.read(dir)
            } catch (e: Exception) {
                Log.w(TAG, "Unusable open model in $dir: ${e.message}", e)
                null
            }
        }

        /** The vector index of the open model, next to the full-text index [indexDir]. */
        fun openVectorIndexDir(indexDir: Path): Path = indexDir.resolveSibling(VectorIndexBuilder.DIR_NAME)

        // Android: the open model only (no legacy one)
        fun isAvailable(indexDir: Path): Boolean = findOpenModel(indexDir) != null && Files.isDirectory(openVectorIndexDir(indexDir))

        /** The open model with its own vector index when both are there and agree. */
        fun open(indexDir: Path): IndependentSemanticSearch? = openOpenModel(indexDir)

        private fun openOpenModel(indexDir: Path): IndependentSemanticSearch? {
            val spec = findOpenModel(indexDir) ?: return null
            val vectorDir = openVectorIndexDir(indexDir)
            return try {
                val info = VectorIndexBuilder.readInfo(vectorDir)
                when {
                    info == null -> {
                        Log.w(TAG, "Open model ${spec.id} found but no vector index at $vectorDir")
                        null
                    }
                    // Vectors of another model or cleaner: the query would be compared with unrelated vectors
                    info.model != spec.id || info.dimension != spec.dimension -> {
                        Log.w(
                            TAG,
                            "$vectorDir was embedded with ${info.model} (${info.dimension} dims), the model is ${spec.id} " +
                                "(${spec.dimension}): not used",
                        )
                        null
                    }
                    // Vectors of any library release: SeforimLibrary keeps a line's id from release to release, so
                    // the vectors of another release miss only the lines changed in between (SemanticPackReleases.newest)
                    else -> {
                        Log.i(TAG, "Open model ${spec.id}, ${info.lines} vectors of the library ${info.dbRelease}")
                        IndependentSemanticSearch(vectorDir, OnnxEmbeddingProvider.open(spec), spec.hybridDenseWeight)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open the open model: ${e.message}", e)
                null
            }
        }
    }
}
