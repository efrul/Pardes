package io.github.kdroidfilter.seforimapp.features.search.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * The reranker as pardes-semantic/reranker/build_reranker.py describes it in `pardes-reranker.json`, next to its ONNX file: the
 * files, how many of a page's first lines it reads ([lines]) and from how long a query ([minWords]), a pair's tokens at
 * most, and by how much its first line must pass the page's own first to take its place ([margin]).
 */
data class RerankerSpec(
    val id: String,
    val onnx: Path,
    val tokenizer: Path,
    val lines: Int,
    val minWords: Int,
    val maxTokens: Int,
    val queryMaxTokens: Int,
    val margin: Float,
) {
    companion object {
        const val FILE_NAME = "pardes-reranker.json"
        private const val FORMAT = 1

        /** The spec in [dir], or null when [dir] has none; throws when the manifest is unusable. */
        fun read(dir: Path): RerankerSpec? {
            val file = dir.resolve(FILE_NAME)
            if (!Files.isRegularFile(file)) return null
            val json = JSONObject(Files.readString(file, StandardCharsets.UTF_8))
            val format = json.optInt("format", -1)
            require(format == FORMAT) { "$file: format $format, this app reads $FORMAT" }
            val spec =
                RerankerSpec(
                    id = json.getString("id"),
                    onnx = dir.resolve(json.getString("onnx")),
                    tokenizer = dir.resolve(json.getString("tokenizer")),
                    lines = json.getInt("lines"),
                    minWords = json.getInt("minWords"),
                    maxTokens = json.getInt("maxTokens"),
                    queryMaxTokens = json.getInt("queryMaxTokens"),
                    margin = json.getDouble("margin").toFloat(),
                )
            require(Files.isRegularFile(spec.onnx)) { "$file: missing ${spec.onnx.fileName}" }
            require(Files.isRegularFile(spec.tokenizer)) { "$file: missing ${spec.tokenizer.fileName}" }
            require(spec.lines > 1 && spec.queryMaxTokens > 0 && spec.maxTokens > spec.queryMaxTokens) { "$file: unusable numbers" }
            return spec
        }
    }
}

/**
 * A query and a line as the reranker reads them, pardes-semantic/reranker/build_reranker.py's `query_text` and `line_text` in
 * Kotlin: cleaned as the index holds a line ([OpenModelCleaner]), a line without its number in parentheses at its
 * start, both without the Hebrew points and accents, the maqaf a space. `BpeTokenizerParityTest` checks them against
 * the fixtures the Python ones write.
 */
internal object RerankText {
    private val LEADING = Pattern.compile("^\\(([^()\\s]{1,6})\\)\\s*")
    private val NUMERAL = Pattern.compile("^(?:ת{0,2}[קרשת]?)?[יכלמנסעפצ]?(?:טו|טז|[אבגדהוזחט])?$")

    // A quote mark, an apostrophe, a geresh or gershayim inside a line's number
    private val MARKS = setOf('"'.code, '\''.code, 0x05F3, 0x05F4)
    private const val MAQAF = 0x05BE

    // The Hebrew accents and points, U+0591 to U+05C7 (the maqaf among them is a space)
    private val POINTS = 0x0591..0x05C7

    fun query(text: String): String = plain(OpenModelCleaner.clean(text))

    fun line(html: String): String = plain(stripNumber(OpenModelCleaner.clean(html)))

    /** How many words [query] has, as the reranker reads it. */
    fun words(query: String): Int = query(query).split(' ').count { it.isNotEmpty() }

    // "(א)" of every verse, "(קיט)", "(12)": a number alone, not a word ("(הגה)", "(שם)")
    private fun stripNumber(text: String): String {
        val m = LEADING.matcher(text)
        if (!m.find()) return text
        val token = m.group(1).filterNot { it.code in MARKS }
        val number = token.isNotEmpty() && (token.all { it.isDigit() } || NUMERAL.matcher(token).matches())
        return if (number) text.substring(m.end()) else text
    }

    private fun plain(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when {
                c.code == MAQAF -> sb.append(' ')
                c.code !in POINTS -> sb.append(c)
            }
        }
        return sb.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }
}

/**
 * The reranker of a results page's first lines: a small cross-encoder (ONNX, int8) that reads the query and a line
 * together and scores how well the line answers the query, where the embedding model only compares two vectors made
 * apart. On the judged searches it puts a good line first in 291 of 451 pages where the fused order has 278, at the
 * noise's edge (pardes-semantic/eval-pool/results-why.md): a trial. Optional: the search is the fused order without
 * its folder ([DIR_NAME], beside the library).
 */
class Reranker internal constructor(
    val spec: RerankerSpec,
) : AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val tokenizer = BpeTokenizer.fromFile(spec.tokenizer)
    private val session: OrtSession =
        env.createSession(
            spec.onnx.toString(),
            OrtSession.SessionOptions().apply { setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT) },
        )

    /**
     * The score of each of [lines] (their HTML, as the library keeps them) for [query], higher for a better answer; null
     * for a line with nothing to read. A pair a run: the scores of an int8 model lean on what shares its run. Null as
     * a whole once [keepGoing] says the search was dropped.
     */
    fun scores(
        query: String,
        lines: List<String>,
        keepGoing: () -> Boolean = { true },
    ): List<Float?>? {
        val prepared = RerankText.query(query)
        return lines.map { html ->
            if (!keepGoing()) return null
            RerankText.line(html).takeIf { it.isNotEmpty() }?.let { score(prepared, it) }
        }
    }

    private fun score(
        query: String,
        line: String,
    ): Float {
        val ids = tokenizer.encodePair(query, line, spec.maxTokens, spec.queryMaxTokens)
        val shape = longArrayOf(1, ids.size.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { tensorIds ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape).use { tensorMask ->
                session.run(mapOf("input_ids" to tensorIds, "attention_mask" to tensorMask)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    return (result[0].value as Array<FloatArray>)[0][0]
                }
            }
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        /** The reranker's folder, beside the library as the model's and the vector index's are. */
        const val DIR_NAME = "pardes-reranker"

        fun dirBeside(indexDir: Path): Path = indexDir.resolveSibling(DIR_NAME)

        /** Whether the library in [libraryDir] has a reranker beside it (a cheap check, nothing loaded). */
        fun isInstalled(libraryDir: File): Boolean = File(File(libraryDir, DIR_NAME), RerankerSpec.FILE_NAME).isFile

        /** The reranker beside [indexDir], loaded; null when there is none or it can't be read: the fused order then. */
        fun open(indexDir: Path): Reranker? =
            runCatching { RerankerSpec.read(dirBeside(indexDir))?.let { Reranker(it) } }
                .onSuccess { if (it != null) infoln { "[Reranker] ${it.spec.id} loaded." } }
                // An Error too: ONNX Runtime's native library throws UnsatisfiedLinkError where it can't load
                .onFailure { warnln(it) { "[Reranker] Unavailable; the fused order as is." } }
                .getOrNull()

        /**
         * [page] with its first lines in the reranker's order. [scores] are of its first lines, in their order, null for
         * a line it could not read, which keeps its place after those it read. The page's own first line stays first
         * unless another passes it by [margin]: the fused order and the precedence of the earlier level put it there,
         * and the reranker changes the first line of two pages in three, mostly for one as good.
         */
        fun <T> order(
            page: List<T>,
            scores: List<Float?>,
            margin: Float,
        ): List<T> {
            val head = page.take(scores.size)
            val scored = head.indices.filter { scores[it] != null }.sortedByDescending { scores[it] }
            if (scored.isEmpty()) return page
            val best = scored.first()
            val keepsFirst = scores[0]?.let { first -> best != 0 && scores[best]!! - first < margin } ?: false
            val reranked = if (keepsFirst) listOf(0) + scored.filter { it != 0 } else scored
            return reranked.map { head[it] } + head.indices.filter { scores[it] == null }.map { head[it] } + page.drop(head.size)
        }
    }
}

/**
 * The reranker beside a library, loaded when a page first asks for it (a heavy ONNX session, as the embedding model's),
 * and a page's first lines in its order. Without one, or for a query too short for it, a page stays as it is.
 */
internal class PageReranker(
    private val indexDir: Path,
    private val open: (Path) -> Reranker?,
) : AutoCloseable {
    @Volatile private var reranker: Reranker? = null

    @Volatile private var tried = false
    private val mutex = Mutex()

    /** The reranker, loaded at the first call; null when the library has none. */
    suspend fun ready(): Reranker? {
        if (tried) return reranker
        mutex.withLock {
            if (!tried) {
                reranker = withContext(Dispatchers.IO) { runSuspendCatching { open(indexDir) }.getOrNull() }
                tried = true
            }
        }
        return reranker
    }

    /**
     * [page], the line ids of a list in its order, with its first lines in the reranker's order ([Reranker.order]);
     * [textOf] gives a line's text. [page] as it is when the reranker fails, or the search was dropped meanwhile.
     */
    suspend fun reordered(
        query: String,
        page: List<Long>,
        textOf: suspend (Long) -> String?,
    ): List<Long> {
        val reranker = ready() ?: return page
        val spec = reranker.spec
        if (page.size < 2 || RerankText.words(query) < spec.minWords) return page
        val texts = page.take(spec.lines).map { textOf(it).orEmpty() }
        val scores =
            withContext(Dispatchers.Default) {
                runSuspendCatching { reranker.scores(query, texts) { isActive } }
                    .onFailure { warnln(it) { "[Reranker] Failed on a page; the fused order as is." } }
                    .getOrNull()
            } ?: return page
        return Reranker.order(page, scores, spec.margin)
    }

    override fun close() {
        runCatching { reranker?.close() }
    }
}
