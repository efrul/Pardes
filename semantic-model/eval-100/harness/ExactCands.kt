import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.features.search.semantic.OnnxEmbeddingProvider
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Path

// Cands.kt with an ideal vector index: the dense side is the exact first 150 by cosine over the index's own int8 vectors
// (IndexCheck.kt's exactTop: all books, or the base books' alone when the search keeps to them), not the HNSW graph's.
// What the graph's misses cost a page (BENCHMARK.md, section 7). Compiled with IndexCheck.kt, as Eval.kt.
// args: seforim.db, queries.tsv (id, group, query, expected), out.jsonl, auto|base|all, lexical count
fun main(args: Array<String>): Unit =
    runBlocking {
        val db = Path.of(args[0])
        val indexPath = Path.of("$db.lucene")
        val lexical = LuceneSearchEngine(indexPath, null, dictionaryPath = indexPath.resolveSibling("lexical.db"))
        val spec = checkNotNull(IndependentSemanticSearch.findOpenModel(indexPath)) { "No open model next to $indexPath" }
        val provider = OnnxEmbeddingProvider.open(spec)
        val mode = args[3]
        val lexN = args[4].toInt()
        val queries =
            File(args[1])
                .readLines()
                .drop(1)
                .filter { it.isNotBlank() }
                .map { it.split('\t') }
        val rows =
            queries.map { (id, group, query, expected) ->
                // The lexical side throws TooManyNestedClauses on some long queries: recorded as Cands.kt does
                val errors = JSONObject()
                val baseHits =
                    runCatching { lexical.computeFacets(query, 5, baseBookOnly = true)?.totalHits ?: 0L }
                        .getOrElse {
                            errors.put("facets", it.javaClass.simpleName)
                            0L
                        }
                val baseOnly =
                    when (mode) {
                        "base" -> true
                        "all" -> false
                        else -> baseHits > 0
                    }
                val lex =
                    runCatching {
                        lexical.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(lexN, snippets = false)?.hits }.orEmpty()
                    }.getOrElse {
                        errors.put("lexical", it.javaClass.simpleName)
                        emptyList()
                    }
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
            }
        val (all, base) =
            exactTop(
                IndependentSemanticSearch.openVectorIndexDir(indexPath),
                queries.map { provider.embed(it[2]) },
                150,
            )
        File(args[2]).bufferedWriter().use { out ->
            rows.forEachIndexed { i, o ->
                val dense = if (o.getString("scope") == "base") base[i] else all[i]
                // Lucene's score for COSINE on bytes, as the app's dense hits carry it
                o.put("denseCand", JSONArray(dense.map { it.first }))
                o.put("denseScores", JSONArray(dense.map { (1 + it.second) / 2 }))
                out.write(o.toString())
                out.newLine()
            }
        }
    }
