package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import org.apache.lucene.document.IntPoint
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.StoredFields
import org.apache.lucene.search.AcceptDocs
import org.apache.lucene.search.ConjunctionUtils
import org.apache.lucene.search.DocIdSetIterator
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnByteVectorQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.search.SeededKnnVectorQuery
import org.apache.lucene.search.Weight
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.IOSupplier
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
    private val directory: FSDirectory = FSDirectory.open(indexDir)
    private val reader: DirectoryReader = DirectoryReader.open(directory)
    private val searcher: IndexSearcher = IndexSearcher(reader)

    /**
     * The [k] lines closest in meaning to [query], in [bookIds] when given, in the base books alone or not.
     *
     * The vectors' graph has traps: a line repeated in a book 33 times or more (a heading: `בה"ג הלכ' בבא בתרא`, 141
     * times) is 33 identical vectors that are each other's only neighbors, and a walk that comes down on one of them
     * comes back with those 33 and no more (3 of 8 first words of a chapter did: `המוכר את הספינה`, `המפקיד`, `ארבעה אבות
     * נזיקין`). [seeds], the lines the words find first, are where the walk then starts again: it found 150 lines in 4
     * to 32 ms, 88 to 141 of the 150 closest in the תלמוד. A caller that has no seeds yet gives [onShort], is told
     * that the walk came back short, and searches again with them.
     */
    fun search(
        query: String,
        k: Int = 150,
        baseOnly: Boolean = false,
        bookIds: Collection<Long>? = null,
        seeds: Collection<Long> = emptyList(),
        onShort: (() -> Unit)? = null,
    ): List<DenseHit> {
        val started = System.nanoTime()
        val queryVec = searchVector(query)
        val embedded = System.nanoTime()
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
        val fieldTimes = ArrayList<String>(fields.size)
        val seedLines = seeds.takeIf { it.isNotEmpty() }?.let { ids -> IntPoint.newSetQuery(FIELD_LINE_ID, ids.map { it.toInt() }) }
        val walk = Walk(queryVec, k, filter, filterWeight, seedLines, onShort)
        for (field in fields) {
            val fieldStarted = System.nanoTime()
            for (sd in searchField(field, walk)) {
                docScoreMap.merge(sd.doc, sd.score, ::maxOf)
            }
            fieldTimes += "$field ${millisSince(fieldStarted)} ms"
        }
        // Where a slow search spent its time: the model, or one of the fields (a large scope is searched on Lucene's
        // filtered graph, a small one exactly)
        debugln {
            "[IndependentSemanticSearch] ${bookIds?.size ?: "all"} books: vector ${(embedded - started) / NANOS_PER_MILLI} ms, " +
                fieldTimes.joinToString()
        }

        val sorted = docScoreMap.entries.sortedByDescending { it.value }.take(k)
        val hits = ArrayList<DenseHit>(sorted.size)
        val seenLineIds = HashSet<Long>()

        // One StoredFields per search: Lucene's is not thread-safe, and searches run at once (two windows, a search
        // and a smart find in a book)
        val storedFields: StoredFields = reader.storedFields()
        for (entry in sorted) {
            val doc = storedFields.document(entry.key, LINE_AND_BOOK)
            val lineId = doc.get(FIELD_LINE_ID)?.toLongOrNull() ?: continue
            if (seenLineIds.add(lineId)) {
                hits.add(DenseHit(lineId, entry.value, doc.get("book_id")?.toLongOrNull() ?: -1L))
            }
        }

        return hits
    }

    // What one search asks of each field: the [k] vectors closest to [target], among the docs of [filter] when there is
    // one (its weight [filterWeight]), the walk started again at [seeds] or [onShort] told when it comes back short
    private class Walk(
        val target: ByteArray,
        val k: Int,
        val filter: Query?,
        val filterWeight: Weight?,
        val seeds: Query?,
        val onShort: (() -> Unit)?,
    )

    /**
     * The vectors of [field] that [walk] asks for. A filter that keeps few of the field's vectors (a book, an author, a
     * small category: at most [EXACT_LIMIT], and at most one in [EXACT_SHARE] of them) is searched exactly, every vector
     * of it scored. There Lucene's filtered graph search walks far for each vector it keeps, costs more and finds less
     * (the מהר״ל's 18 books, 54,883 of 5,483,563 vectors: 22 to 128 ms and 87 to 100% of the closest 150, against 17 to
     * 29 ms and all of them). A filter keeping a large share is walked on the graph: the תלמוד's base books, 81,269 of
     * the 242,109 base vectors, take it 6 to 16 ms, and 25 to 56 ms exactly.
     *
     * A walk that comes back short fell in a trap ([search]): it starts again at the seeds. Without seeds a caller that
     * gave `onShort` is told, and searches again with them; any other gets every vector of the filter scored, as Lucene's
     * own query does (the תלמוד's 2,137,211 lines: 255 to 629 ms), or with no filter the short list.
     */
    private fun searchField(
        field: String,
        walk: Walk,
    ): Array<ScoreDoc> {
        val filterWeight = walk.filterWeight
        if (walk.filter != null && filterWeight != null) {
            var vectors = 0L
            for (leaf in reader.leaves()) vectors += leaf.reader().getByteVectorValues(field)?.size() ?: 0
            val limit = minOf(EXACT_LIMIT.toLong(), vectors / EXACT_SHARE).toInt()
            if (filteredVectors(field, filterWeight, limit) <= limit) return exactSearch(field, walk.target, walk.k, filterWeight)
        }
        val walked = graphWalk(field, walk)
        if (walked.size >= walk.k) return walked
        walk.seeds?.let { seeds ->
            // Lucene's own query from here: a seeded walk that is short too scores every vector of the filter
            val closest = SeededKnnVectorQuery.fromByteQuery(KnnByteVectorQuery(field, walk.target, walk.k, walk.filter), seeds)
            // With the repeated line the first walk found, where it is among the closest
            val both = searcher.search(closest, walk.k).scoreDocs.asList() + walked
            return both
                .distinctBy { it.doc }
                .sortedByDescending { it.score }
                .take(walk.k)
                .toTypedArray()
        }
        walk.onShort?.let { told ->
            told()
            return walked
        }
        return if (filterWeight != null) exactSearch(field, walk.target, walk.k, filterWeight) else walked
    }

    /** The graph's walk for [walk] in [field], as Lucene's query makes it, without its exact search when it is short. */
    private fun graphWalk(
        field: String,
        walk: Walk,
    ): Array<ScoreDoc> {
        val found = ArrayList<ScoreDoc>(walk.k)
        for (leaf in reader.leaves()) {
            val leafReader = leaf.reader()
            if (leafReader.getByteVectorValues(field) == null) continue
            val live = leafReader.liveDocs
            val accepted =
                walk.filterWeight?.let { weight ->
                    val docs = IOSupplier { weight.scorer(leaf)?.iterator() ?: DocIdSetIterator.empty() }
                    AcceptDocs.fromIteratorSupplier(docs, live, leafReader.maxDoc())
                } ?: AcceptDocs.fromLiveDocs(live, leafReader.maxDoc())
            for (doc in leafReader.searchNearestVectors(field, walk.target, walk.k, accepted, Int.MAX_VALUE).scoreDocs) {
                found += ScoreDoc(leaf.docBase + doc.doc, doc.score)
            }
        }
        return found.sortedByDescending { it.score }.take(walk.k).toTypedArray()
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

    // The last queries as the index's vectors (int8): a query is searched again as it is, in each scope picked and each
    // time its tab is brought back
    private val searchVectors =
        object : LinkedHashMap<String, ByteArray>(QUERY_CACHE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > QUERY_CACHE
        }

    private fun searchVector(query: String): ByteArray =
        synchronized(searchVectors) { searchVectors[query] }
            ?: embeddingProvider.embed(query).also { vector -> synchronized(searchVectors) { searchVectors[query] = vector } }

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
        /** The open model's folder name next to the databases (or `PARDES_MODEL_DIR` / `-Dpardes.model.dir`). */
        const val OPEN_MODEL_DIR_NAME = "pardes-model"

        private const val FIELD_LINE_ID = "line_id"

        private val LINE_AND_BOOK = setOf(FIELD_LINE_ID, "book_id")

        private const val FIELD_VEC = "vec"
        private const val FIELD_VEC_BASE = "vec_base"

        /**
         * A filter keeping at most this many of a field's vectors is searched exactly ([searchField]): some 45 ms on one
         * core without SIMD (0.45 ms per thousand), about what Lucene's filtered graph search costs on such a filter.
         */
        internal const val EXACT_LIMIT = 100_000

        /** ...and keeping at most one in this many of them: above it, Lucene's graph search finds them quickly. */
        internal const val EXACT_SHARE = 8

        /** Queries whose vectors [search] and [bestPassages] keep. */
        private const val QUERY_CACHE = 16

        private const val NANOS_PER_MILLI = 1_000_000

        private fun millisSince(start: Long) = (System.nanoTime() - start) / NANOS_PER_MILLI

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

        fun findOpenModel(indexDir: Path): OpenModelSpec? {
            val candidates =
                listOfNotNull(
                    (System.getenv("PARDES_MODEL_DIR") ?: System.getProperty("pardes.model.dir"))?.let { Paths.get(it) },
                    indexDir.resolveSibling(OPEN_MODEL_DIR_NAME),
                )
            for (dir in candidates) {
                val spec =
                    try {
                        OpenModelSpec.read(dir)
                    } catch (e: Exception) {
                        warnln(e) { "[IndependentSemanticSearch] Unusable open model in $dir: ${e.message}" }
                        null
                    }
                if (spec != null) return spec
            }
            return null
        }

        /** The vector index of the open model, next to the full-text index [indexDir]. */
        fun openVectorIndexDir(indexDir: Path): Path = indexDir.resolveSibling(VectorIndexBuilder.DIR_NAME)

        /**
         * The legacy model, for comparison only: used when `SEFORIM_MODEL_DIR` (or `-Dseforim.model.dir`) names a
         * folder with it, never looked for anywhere else. The app's semantic search is the open model's
         * ([findOpenModel]); the legacy model is not Pardes's to ship.
         */
        fun findLegacyModelDir(): Path? {
            val dir = (System.getenv("SEFORIM_MODEL_DIR") ?: System.getProperty("seforim.model.dir"))?.let { Paths.get(it) }
            return dir?.takeIf {
                Files.exists(it.resolve("seforim-embed-v5-int8.onnx")) && Files.exists(it.resolve("tokenizer.json"))
            }
        }

        fun isAvailable(indexDir: Path): Boolean {
            if (findOpenModel(indexDir) != null && Files.isDirectory(openVectorIndexDir(indexDir))) return true
            findLegacyModelDir() ?: return false
            // Also check that index directory exists
            return Files.exists(indexDir)
        }

        /**
         * The open model with its own vector index when both are there and agree, else the legacy model with the
         * vectors of [indexDir].
         */
        fun open(indexDir: Path): IndependentSemanticSearch? = openOpenModel(indexDir) ?: openLegacy(indexDir)

        private fun openOpenModel(indexDir: Path): IndependentSemanticSearch? {
            val spec = findOpenModel(indexDir) ?: return null
            val vectorDir = openVectorIndexDir(indexDir)
            return try {
                val info = VectorIndexBuilder.readInfo(vectorDir)
                when {
                    info == null -> {
                        warnln { "[IndependentSemanticSearch] Open model ${spec.id} found but no vector index at $vectorDir" }
                        null
                    }
                    // Vectors of another model or cleaner: the query would be compared with unrelated vectors
                    info.model != spec.id || info.dimension != spec.dimension -> {
                        warnln {
                            "[IndependentSemanticSearch] $vectorDir was embedded with ${info.model} " +
                                "(${info.dimension} dims), the model is ${spec.id} (${spec.dimension}): not used"
                        }
                        null
                    }
                    // Vectors of any library release: SeforimLibrary keeps a line's id from release to release, so
                    // the vectors of another release miss only the lines changed in between (SemanticPackReleases.newest)
                    else -> {
                        infoln {
                            "[IndependentSemanticSearch] Open model ${spec.id}, ${info.lines} vectors of the library ${info.dbRelease}"
                        }
                        IndependentSemanticSearch(vectorDir, OnnxEmbeddingProvider.open(spec), spec.hybridDenseWeight)
                    }
                }
            } catch (e: Exception) {
                warnln(e) { "[IndependentSemanticSearch] Failed to open the open model: ${e.message}" }
                null
            }
        }

        private fun openLegacy(indexDir: Path): IndependentSemanticSearch? {
            val modelDir = findLegacyModelDir()
            if (modelDir == null) {
                warnln {
                    "[IndependentSemanticSearch] No open model ($OPEN_MODEL_DIR_NAME/ and ${VectorIndexBuilder.DIR_NAME}/ next to " +
                        "the index) and no legacy one (SEFORIM_MODEL_DIR): lexical search only"
                }
                return null
            }

            return try {
                val onnxPath = modelDir.resolve("seforim-embed-v5-int8.onnx")
                val tokPath = modelDir.resolve("tokenizer.json")
                infoln { "[IndependentSemanticSearch] Initializing ONNX model from $onnxPath" }
                val provider = OnnxEmbeddingProvider.legacy(onnxPath, tokPath)
                IndependentSemanticSearch(indexDir, provider)
            } catch (e: Exception) {
                warnln(e) { "[IndependentSemanticSearch] Failed to initialize ONNX embedding model: ${e.message}" }
                null
            }
        }
    }
}
