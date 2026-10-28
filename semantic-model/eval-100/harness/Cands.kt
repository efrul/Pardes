import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Path

// The candidates each side hands the fusion, under a scope the caller picks, with more lexical ones than the app takes
// (semantic-model/eval-real/results-level-order.md). Compiled as Eval.kt, without HybridSearchEngine.kt.
// args: seforim.db, queries.tsv (id, group, query, expected), out.jsonl, auto|base|all, lexical count [nodense]
// auto: as the app runs a search, base books first and all books when the lexical facets find none there.
// nodense: the lexical side only (the dense one does not change with BM25's parameters; the BM25 runs set them with
// -Dbm25.k1 and -Dbm25.b on a copy of LuceneSearchEngine whose searcher factory reads them).
fun main(args: Array<String>): Unit =
    runBlocking {
        val db = Path.of(args[0])
        val indexPath = Path.of("$db.lucene")
        val lexical = LuceneSearchEngine(indexPath, null, dictionaryPath = indexPath.resolveSibling("lexical.db"))
        val dense = if (args.getOrNull(5) == "nodense") null else IndependentSemanticSearch.open(indexPath)
        val mode = args[3]
        val lexN = args[4].toInt()
        File(args[2]).bufferedWriter().use { out ->
            File(args[1]).readLines().drop(1).filter { it.isNotBlank() }.forEach { line ->
                val (id, group, query, expected) = line.split('\t')
                val errors = JSONObject()
                val baseHits =
                    runCatching { lexical.computeFacets(query, 5, baseBookOnly = true)?.totalHits ?: 0L }
                        .getOrElse { errors.put("facets", it.javaClass.simpleName); 0L }
                val baseOnly = when (mode) {
                    "base" -> true
                    "all" -> false
                    else -> baseHits > 0
                }
                val lex =
                    runCatching { lexical.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(lexN, snippets = false)?.hits }.orEmpty() }
                        .getOrElse { errors.put("lexical", it.javaClass.simpleName); emptyList() }
                val den = dense?.search(query, 150, baseOnly, null).orEmpty()
                out.write(
                    JSONObject()
                        .put("id", id.toInt())
                        .put("group", group)
                        .put("query", query)
                        .put("expected", expected)
                        .put("scope", if (baseOnly) "base" else "all")
                        .put("errors", errors)
                        .put("lexicalBaseHits", baseHits)
                        .put("lexCand", JSONArray(lex.map { it.lineId }))
                        .put("lexScores", JSONArray(lex.map { it.score.toDouble() }))
                        .put("denseCand", JSONArray(den.map { it.lineId }))
                        .put("denseScores", JSONArray(den.map { it.score.toDouble() }))
                        .toString(),
                )
                out.newLine()
                out.flush()
                System.err.println("$id ${if (baseOnly) "base" else "all"} lex=${lex.size} dense=${den.size}")
            }
        }
        dense?.close()
    }
