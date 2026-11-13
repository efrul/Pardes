import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine
import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.search.LongQuerySearchEngine
import io.github.kdroidfilter.seforimapp.framework.search.PardesLexicon
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager

// Request 31 (docs/DESKTOP_STAGE2_PLAN.md): the lines of a search's fused list that the words don't find (the meaning's
// own), each with its place and score on the dense side and its place in the fused list.
//
// args: seforim.db, queries.tsv (id, group, query, expected), scope (app | all | base), out.jsonl
// "app": base books first (near 5), all books when the lexical facets find none there, as Eval.kt.
fun main(args: Array<String>): Unit =
    runBlocking {
        val db = Path.of(args[0])
        val scope = args[2]
        val indexPath = Path.of("$db.lucene")
        // The words' side as the app builds it: Pardes's dictionary, long queries cut
        val dictionary = PardesLexicon.dictionaryFor(indexPath.resolveSibling("lexical.db"), indexPath)
        PardesLexicon.awaitBuild(30 * 60_000L)
        val lexical: SearchEngine = LongQuerySearchEngine(LuceneSearchEngine(indexPath, null, dictionaryPath = dictionary))
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
        val dense = IndependentSemanticSearch.open(indexPath) ?: error("no dense index beside $indexPath")
        File(args[3]).bufferedWriter().use { out ->
            File(args[1]).readLines().drop(1).filter { it.isNotBlank() }.forEach { line ->
                val (id, _, query) = line.split('\t')
                val baseHits = lexical.computeFacets(query, 5, baseBookOnly = true)?.totalHits ?: 0L
                val baseOnly =
                    when (scope) {
                        "all" -> false
                        "base" -> true
                        else -> baseHits > 0
                    }
                val lexPage = lexical.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(150, snippets = false) }
                val lexHits = lexPage?.hits.orEmpty()
                // The dense side as HybridSearchEngine.fuse asks it: again from the words' first hits when its walk came back short
                var short = false
                var den = dense.search(query, 150, baseOnly, null, onShort = { short = true })
                if (short) den = dense.search(query, 150, baseOnly, null, lexHits.take(64).map { it.lineId })
                val denseRank = HashMap<Long, Int>()
                den.forEachIndexed { i, hit -> denseRank.putIfAbsent(hit.lineId, i + 1) }
                val denseScore = den.associate { it.lineId to it.score.toDouble() }

                val fused = hybrid.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(400, snippets = false)?.hits }.orEmpty()
                val fusedIds = fused.map { it.lineId }
                val byWords =
                    if (fusedIds.isEmpty()) {
                        emptySet()
                    } else {
                        lexical
                            .openSession(query, 5, lineIds = fusedIds, baseBookOnly = baseOnly)
                            ?.use { it.nextPage(fusedIds.size, snippets = false)?.hits }
                            .orEmpty()
                            .mapTo(HashSet()) { it.lineId }
                    }
                val meant = JSONArray()
                fusedIds.forEachIndexed { i, lineId ->
                    if (lineId in byWords) return@forEachIndexed
                    val r = row(lineId) ?: return@forEachIndexed
                    meant.put(
                        r
                            .put("text", r.getString("text").take(600))
                            .put("fusedPlace", i + 1)
                            .put("denseRank", denseRank[lineId] ?: 0)
                            .put("denseScore", denseScore[lineId] ?: 0.0),
                    )
                }
                out.write(
                    JSONObject()
                        .put("id", id.toInt())
                        .put("query", query)
                        .put("scope", if (baseOnly) "base" else "all")
                        .put("lexicalTotal", lexPage?.totalHits ?: 0L)
                        .put("lexicalBaseHits", baseHits)
                        .put("denseHits", den.size)
                        .put("denseTop", den.firstOrNull()?.score?.toDouble() ?: 0.0)
                        .put("denseLast", den.lastOrNull()?.score?.toDouble() ?: 0.0)
                        .put("denseScores", JSONArray(den.map { it.score.toDouble() }))
                        .put("denseIds", JSONArray(den.map { it.lineId }))
                        .put("fused", JSONArray(fusedIds))
                        .put("byWords", JSONArray(byWords.toList()))
                        .put("meant", meant)
                        .toString(),
                )
                out.newLine()
                out.flush()
                System.err.println("$id ${if (baseOnly) "base" else "all"} words=${lexPage?.totalHits ?: 0} fused=${fusedIds.size} meant=${meant.length()}")
            }
        }
        hybrid.close()
        dense.close()
    }
