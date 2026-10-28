import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimlibrary.search.Bm25Knob
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.runBlocking
import org.apache.lucene.search.similarities.BM25Similarity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Path

// The lexical side again under other settings, for the queries of an Eval.kt jsonl (eval-100/harness/Eval.kt), with
// the copy of LuceneSearchEngine.kt that bm25-knob.patch changes. A setting is "k1,b,boost[,near[,keep]]":
// - k1, b: BM25's (the app's 1.2, 0.75);
// - boost 1/0: the base-book order boost of mapScoreDocs on or off;
// - near 1: the words required near each other (the near-5 phrase as MUST instead of SHOULD);
// - keep 1: Eval.kt's scope for each query; otherwise the scope again as Eval.kt picks it (base books, all books when
//   the lexical facets find none there), with the dense candidates again when it changes.
//
// args: seforim.db, eval.jsonl, settings ("k1,b,boost[,near[,keep]];..."), out.jsonl
// Each line: the setting, the scope, the lexical first page of 10 and the first 150 (lineIds).
fun main(args: Array<String>): Unit =
    runBlocking {
        val indexPath = Path.of("${args[0]}.lucene")
        val lexical = LuceneSearchEngine(indexPath, null, dictionaryPath = indexPath.resolveSibling("lexical.db"))
        val dense by lazy { IndependentSemanticSearch.open(indexPath) ?: error("no model or vector index next to $indexPath") }
        val queries = File(args[1]).readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
        val settings = args[2].split(';').map { it.split(',') }
        File(args[3]).bufferedWriter().use { out ->
            for (s in settings) {
                val (k1, b) = s[0].toFloat() to s[1].toFloat()
                val keepScope = s.getOrNull(4) == "1"
                Bm25Knob.current = BM25Similarity(k1, b)
                Bm25Knob.orderBoost = s[2] == "1"
                Bm25Knob.requireNear = s.getOrNull(3) == "1"
                val t0 = System.nanoTime()
                for (q in queries) {
                    val query = q.getString("query")
                    val baseOnly =
                        if (keepScope) {
                            q.getString("scope") == "base"
                        } else {
                            (lexical.computeFacets(query, 5, baseBookOnly = true)?.totalHits ?: 0L) > 0
                        }
                    val scope = if (baseOnly) "base" else "all"
                    val page = lexical.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(10, snippets = false)?.hits }.orEmpty()
                    val cand = lexical.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(150, snippets = false)?.hits }.orEmpty()
                    val row =
                        JSONObject()
                            .put("id", q.getInt("id"))
                            .put("k1", k1.toDouble())
                            .put("b", b.toDouble())
                            .put("boost", Bm25Knob.orderBoost)
                            .put("near", Bm25Knob.requireNear)
                            .put("keepScope", keepScope)
                            .put("scope", scope)
                            .put("lexical", JSONArray(page.map { it.lineId }))
                            .put("lexCand", JSONArray(cand.map { it.lineId }))
                    if (scope != q.getString("scope")) {
                        row.put("denseCand", JSONArray(dense.search(query, 150, baseOnly, null).map { it.lineId }))
                    }
                    out.write(row.toString())
                    out.newLine()
                }
                out.flush()
                System.err.println("k1=$k1 b=$b boost=${Bm25Knob.orderBoost} near=${Bm25Knob.requireNear} keep=$keepScope: ${(System.nanoTime() - t0) / 1_000_000_000}s")
            }
        }
        lexical.close()
    }
