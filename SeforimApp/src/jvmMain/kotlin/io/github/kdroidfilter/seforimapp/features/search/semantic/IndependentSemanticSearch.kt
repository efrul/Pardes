package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPack
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import org.apache.lucene.document.IntPoint
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.StoredFields
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnByteVectorQuery
import org.apache.lucene.search.Query
import org.apache.lucene.store.FSDirectory
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class DenseHit(
    val lineId: Long,
    val score: Float,
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

        if (baseOnly) {
            val q =
                if (filter != null) {
                    KnnByteVectorQuery("vec_base", queryVec, k, filter)
                } else {
                    KnnByteVectorQuery("vec_base", queryVec, k)
                }
            val td = searcher.search(q, k)
            for (sd in td.scoreDocs) {
                docScoreMap[sd.doc] = sd.score
            }
        } else {
            val qVec =
                if (filter != null) {
                    KnnByteVectorQuery("vec", queryVec, k, filter)
                } else {
                    KnnByteVectorQuery("vec", queryVec, k)
                }
            val qBase =
                if (filter != null) {
                    KnnByteVectorQuery("vec_base", queryVec, k, filter)
                } else {
                    KnnByteVectorQuery("vec_base", queryVec, k)
                }

            val tdVec = searcher.search(qVec, k)
            val tdBase = searcher.search(qBase, k)
            for (sd in tdVec.scoreDocs) {
                docScoreMap[sd.doc] = sd.score
            }
            for (sd in tdBase.scoreDocs) {
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
            val doc = storedFields.document(entry.key, LINE_ID_ONLY)
            val lineId = doc.get("line_id")?.toLongOrNull() ?: continue
            if (seenLineIds.add(lineId)) {
                hits.add(DenseHit(lineId, entry.value))
            }
        }

        return hits
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
        /** The open model's folder name next to the databases (or `PARDES_MODEL_DIR` / `-Dpardes.model.dir`). */
        const val OPEN_MODEL_DIR_NAME = "pardes-model"

        private val LINE_ID_ONLY = setOf("line_id")

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

        private fun otherLibrary(
            info: VectorIndexBuilder.Info,
            indexDir: Path,
        ): Boolean {
            val library =
                indexDir
                    .resolveSibling("release_info.txt")
                    .takeIf(Files::isRegularFile)
                    ?.let { runCatching { Files.readString(it).trim() }.getOrNull() }
                    ?.ifEmpty { null }
            return info.dbRelease != null && library != null && !SemanticPack.sameLibrary(info.dbRelease, library)
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
                    // Vectors of another library release (release_info.txt, which the library's delta updates keep):
                    // their line ids may name other lines
                    otherLibrary(info, indexDir) -> {
                        warnln { "[IndependentSemanticSearch] $vectorDir was embedded on the library ${info.dbRelease}: not used" }
                        null
                    }
                    else -> {
                        infoln { "[IndependentSemanticSearch] Open model ${spec.id}, ${info.lines} vectors" }
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
