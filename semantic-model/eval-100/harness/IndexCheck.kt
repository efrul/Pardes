import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.features.search.semantic.OnnxEmbeddingProvider
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.VectorUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.math.sqrt

// The vector index against an exact search, through the app's own dense search (semantic-model/BENCHMARK.md, section
// 4): each query's first ten of IndependentSemanticSearch.search (k = 150; all books, then base books only) and of an
// exact scan of the same int8 vectors by cosine, as [line id, cosine], the time of the app's search, and the app's
// float query vector (check_index.py compares it with Python's). Compiled as Eval.kt.
// args: seforim.db (pardes-model/ and pardes.vec.lucene/ next to its .lucene), queries.tsv (id, group, query,
// expected), out.jsonl
fun main(args: Array<String>) {
    val indexPath = Path.of("${args[0]}.lucene")
    val spec = checkNotNull(IndependentSemanticSearch.findOpenModel(indexPath)) { "No open model next to $indexPath" }
    val provider = OnnxEmbeddingProvider.open(spec)
    val dense = checkNotNull(IndependentSemanticSearch.open(indexPath)) { "No vector index of ${spec.id}" }
    val queries =
        File(args[1])
            .readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split('\t') }
    val floats = queries.map { provider.embedFloat(it[2]) }
    // The model and the graph's pages warm, as after a first search in the app
    queries.take(20).forEach { dense.search(it[2], 150, false, null) }
    val app =
        queries.map { q ->
            val t0 = System.nanoTime()
            val all = dense.search(q[2], 150, false, null)
            val t1 = System.nanoTime()
            val base = dense.search(q[2], 150, true, null)
            val t2 = System.nanoTime()
            System.err.println("${q[0]} ${(t1 - t0) / 1_000_000}ms ${(t2 - t1) / 1_000_000}ms")
            // Lucene's score of a byte vector by COSINE is (1 + cosine) / 2
            Triple(
                all.take(10).map { it.lineId to 2.0 * it.score - 1 },
                base.take(10).map { it.lineId to 2.0 * it.score - 1 },
                (t1 - t0) to (t2 - t1),
            )
        }
    dense.close()
    val (exactAll, exactBase) =
        exactTop(IndependentSemanticSearch.openVectorIndexDir(indexPath), floats.map(OnnxEmbeddingProvider::quantizeInt8), 10)

    fun hits(lines: List<Pair<Long, Double>>) = JSONArray(lines.map { (line, cosine) -> JSONArray().put(line).put(cosine) })
    File(args[2]).bufferedWriter().use { out ->
        queries.forEachIndexed { i, q ->
            out.write(
                JSONObject()
                    .put("id", q[0])
                    .put("group", q[1])
                    .put("query", q[2])
                    .put("expected", q.getOrElse(3) { "" })
                    .put("model", spec.id)
                    .put("vector", JSONArray(floats[i].map { it.toDouble() }))
                    .put("appAll", hits(app[i].first))
                    .put("appBase", hits(app[i].second))
                    .put("usAll", app[i].third.first / 1000)
                    .put("usBase", app[i].third.second / 1000)
                    .put("exactAll", hits(exactAll[i]))
                    .put("exactBase", hits(exactBase[i]))
                    .toString(),
            )
            out.newLine()
        }
    }
}

// The [n] best of a query: scores and docs, the lowest replaced
internal class Best(
    private val n: Int,
) {
    val scores = DoubleArray(n) { Double.NEGATIVE_INFINITY }
    val docs = IntArray(n) { -1 }
    private var lowest = 0

    fun offer(
        score: Double,
        doc: Int,
    ) {
        if (score <= scores[lowest]) return
        scores[lowest] = score
        docs[lowest] = doc
        for (i in 0 until n) if (scores[i] < scores[lowest]) lowest = i
    }

    fun ranked(): List<Pair<Double, Int>> =
        scores.indices
            .filter { docs[it] >= 0 }
            .map { scores[it] to docs[it] }
            .sortedByDescending { it.first }
}

// The first [n] lines of each query by cosine over every vector of the index (both fields: VectorIndexBuilder puts a
// base book's line in vec_base, any other in vec), and over the base books' only
internal fun exactTop(
    dir: Path,
    queries: List<ByteArray>,
    n: Int,
): Pair<List<List<Pair<Long, Double>>>, List<List<Pair<Long, Double>>>> =
    DirectoryReader.open(FSDirectory.open(dir)).use { reader ->
        val leaf = reader.leaves().single().reader()
        val norms = queries.map { sqrt(VectorUtil.dotProduct(it, it).toDouble()) }
        val threads = Runtime.getRuntime().availableProcessors()
        val pool = Executors.newFixedThreadPool(threads)

        fun scan(field: String): List<List<Pair<Double, Int>>> {
            val values = leaf.getByteVectorValues(field)
            val size = values.size()
            val parts =
                (0 until threads)
                    .map { t ->
                        pool.submit<Array<Best>> {
                            val v = values.copy()
                            val best = Array(queries.size) { Best(n) }
                            for (ord in (t.toLong() * size / threads).toInt() until ((t + 1).toLong() * size / threads).toInt()) {
                                val vector = v.vectorValue(ord)
                                val norm = sqrt(VectorUtil.dotProduct(vector, vector).toDouble())
                                val doc = v.ordToDoc(ord)
                                for (i in queries.indices) best[i].offer(VectorUtil.dotProduct(queries[i], vector) / (norms[i] * norm), doc)
                            }
                            best
                        }
                    }.map { it.get() }
            System.err.println("$field: $size vectors scanned")
            return queries.indices.map { i -> parts.flatMap { it[i].ranked() }.sortedByDescending { it.first }.take(n) }
        }

        val base = scan("vec_base")
        val other = scan("vec")
        pool.shutdown()
        val stored = reader.storedFields()

        fun lines(hits: List<Pair<Double, Int>>) =
            hits.map {
                stored.document(it.second, setOf("line_id")).get("line_id").toLong() to
                    it.first
            }
        queries.indices.map { i -> lines((base[i] + other[i]).sortedByDescending { it.first }.take(n)) } to
            queries.indices.map { i -> lines(base[i]) }
    }
