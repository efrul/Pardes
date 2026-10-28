import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine
import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager

// The 100 queries through the app's own engines, outside the app (semantic-model/eval-100/README.md):
// SeforimLibrary's LuceneSearchEngine (lexical), the branch's HybridSearchEngine and IndependentSemanticSearch, with
// the open model and pardes.vec.lucene next to seforim.db.lucene. Compiled with a standalone kotlinc
// (kotlin-compiler-embeddable 2.2.21) against SeforimLibrary/search/src/jvmMain, the semantic package, LoggerStub.kt
// (the app's logger without Sentry) and lucene-core/analysis-common 10.5.2, kermit 2.1.0, jsoup 1.22.1,
// kotlinx-coroutines 1.10.2, sqlite-jdbc, onnxruntime 1.17.1, org.json.
//
// args: seforim.db, queries.tsv (id, group, query, expected), out.jsonl
// Each query as the app runs it: base books first (near 5), all books when the lexical facets find none there;
// the same scope for the lexical engine, the hybrid one and the dense side alone.
fun main(args: Array<String>): Unit =
    runBlocking {
        val db = Path.of(args[0])
        val indexPath = Path.of("$db.lucene")
        val lexical = LuceneSearchEngine(indexPath, null, dictionaryPath = indexPath.resolveSibling("lexical.db"))
        val con = DriverManager.getConnection("jdbc:sqlite:$db")
        val st =
            con.prepareStatement(
                "SELECT l.bookId, l.lineIndex, l.content, b.title, b.isBaseBook, l.heRef FROM line l JOIN book b ON b.id = l.bookId WHERE l.id = ?",
            )

        fun row(lineId: Long): JSONObject? =
            synchronized(st) {
                st.setLong(1, lineId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    JSONObject()
                        .put("lineId", lineId)
                        .put("bookId", rs.getLong(1))
                        .put("lineIndex", rs.getInt(2))
                        .put("text", rs.getString(3))
                        .put("book", rs.getString(4))
                        .put("base", rs.getInt(5) == 1)
                        .put("heRef", rs.getString(6) ?: "")
                }
            }
        val hybrid =
            HybridSearchEngine(lexical, indexPath) { lineId, _ ->
                row(lineId)?.let {
                    LineHit(it.getLong("bookId"), it.getString("book"), lineId, it.getInt("lineIndex"), "", 0f, it.getString("text"))
                }
            }
        val dense = IndependentSemanticSearch.open(indexPath)
        File(args[2]).bufferedWriter().use { out ->
            File(args[1]).readLines().drop(1).filter { it.isNotBlank() }.forEach { line ->
                val (id, group, query, expected) = line.split('\t')
                val baseHits = lexical.computeFacets(query, 5, baseBookOnly = true)?.totalHits ?: 0L
                val baseOnly = baseHits > 0
                val t0 = System.nanoTime()
                val lex = lexical.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(10, snippets = false)?.hits }.orEmpty()
                val t1 = System.nanoTime()
                val hyb = hybrid.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(10, snippets = false)?.hits }.orEmpty()
                val t2 = System.nanoTime()
                val denseAll = dense?.search(query, 150, baseOnly, null).orEmpty()
                val den = denseAll.take(10)
                // The candidates each side gives the fusion (HybridSearchEngine: the first 150 of each), for other fusions offline
                val lexCandidates =
                    lexical
                        .openSession(
                            query,
                            5,
                            baseBookOnly = baseOnly,
                        )?.use { it.nextPage(150, snippets = false)?.hits }
                        .orEmpty()
                // The hybrid kept to the base books even when the lexical side found none there (no fallback)
                val hybBase =
                    if (baseOnly) {
                        hyb
                    } else {
                        hybrid
                            .openSession(
                                query,
                                5,
                                baseBookOnly = true,
                            )?.use { it.nextPage(10, snippets = false)?.hits }
                            .orEmpty()
                    }

                fun list(ids: List<Long>) = JSONArray(ids.mapNotNull { row(it)?.apply { put("text", getString("text").take(400)) } })
                out.write(
                    JSONObject()
                        .put("id", id.toInt())
                        .put("group", group)
                        .put("query", query)
                        .put("expected", expected)
                        .put("scope", if (baseOnly) "base" else "all")
                        .put("lexicalBaseHits", baseHits)
                        .put("lexMs", (t1 - t0) / 1_000_000)
                        .put("hybMs", (t2 - t1) / 1_000_000)
                        .put("lexical", list(lex.map { it.lineId }))
                        .put("hybrid", list(hyb.map { it.lineId }))
                        .put("dense", list(den.map { it.lineId }))
                        .put("hybridBase", list(hybBase.map { it.lineId }))
                        .put("lexCand", JSONArray(lexCandidates.map { it.lineId }))
                        .put("denseCand", JSONArray(denseAll.map { it.lineId }))
                        .put("denseScores", JSONArray(denseAll.map { it.score.toDouble() }))
                        .toString(),
                )
                out.newLine()
                out.flush()
                System.err.println("$id ${if (baseOnly) "base" else "all"} lex=${lex.size} hyb=${hyb.size} ${(t2 - t1) / 1_000_000}ms")
            }
        }
        hybrid.close()
        dense?.close()
    }
