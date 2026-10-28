import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine
import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.search.LongQuerySearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties

// The searches of a history (history.py's history.tsv) through the app's engines, outside the app, each in the scope
// the app gave it (SearchResultViewModel):
// - a book the user chose: that book, all of it (bookIds, baseBookOnly = false)
// - "all books" on (searchExtended): all books
// - otherwise base books, and all books when the lexical facets find none there ("fallback")
// The lexical side is wrapped as the app wraps it (withSemanticSearch): a query too large for Lucene is searched as a quote.
//
// args: seforim.db, history.tsv (id, query, book, ext), out.jsonl
// Each line: the hybrid's first 50 (two pages of 25), the lexical engine's first page of 25 (what Zayit shows), the
// 150 candidates of each side the fusion takes, and after a fallback the hybrid's first page in the base books; each
// hit as [lineId, score].
fun main(args: Array<String>): Unit =
    runBlocking {
        val db = Path.of(args[0])
        val indexPath = Path.of("$db.lucene")
        val lexical = LongQuerySearchEngine(LuceneSearchEngine(indexPath, null, dictionaryPath = indexPath.resolveSibling("lexical.db")))
        val con = DriverManager.getConnection("jdbc:sqlite:$db", Properties().apply { put("open_mode", "1") })
        val st = con.prepareStatement("SELECT l.bookId, l.lineIndex, l.content, b.title FROM line l JOIN book b ON b.id = l.bookId WHERE l.id = ?")

        fun hit(lineId: Long): LineHit? =
            synchronized(st) {
                st.setLong(1, lineId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    LineHit(rs.getLong(1), rs.getString(4), lineId, rs.getInt(2), "", 0f, rs.getString(3))
                }
            }
        val hybrid = HybridSearchEngine(lexical, indexPath) { lineId, _ -> hit(lineId) }
        val dense = IndependentSemanticSearch.open(indexPath) ?: error("no model or vector index next to $indexPath")

        fun ids(hits: List<LineHit>) = JSONArray(hits.map { JSONArray().put(it.lineId).put(it.score.toDouble()) })

        File(args[2]).bufferedWriter().use { out ->
            File(args[1]).readLines().drop(1).filter { it.isNotBlank() }.forEach { line ->
                val (id, query, bookText, ext) = line.split('\t')
                val book = bookText.toLongOrNull()
                val bookIds = book?.let { listOf(it) }
                val baseTotal = if (book == null) lexical.computeFacets(query, 5, baseBookOnly = true)?.totalHits ?: 0L else null
                val scope =
                    when {
                        book != null -> "book"
                        ext == "1" -> "all"
                        baseTotal!! > 0 -> "base"
                        else -> "fallback"
                    }
                val baseOnly = scope == "base"
                val scopeTotal = lexical.computeFacets(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)?.totalHits ?: 0L

                val t0 = System.nanoTime()
                val hyb =
                    hybrid.openSession(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)?.use { s ->
                        (s.nextPage(25, snippets = false)?.hits.orEmpty()) + (s.nextPage(25, snippets = false)?.hits.orEmpty())
                    }.orEmpty()
                val t1 = System.nanoTime()
                val lexCand =
                    runCatching {
                        lexical.openSession(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)?.use { it.nextPage(150, snippets = false)?.hits }.orEmpty()
                    }.getOrDefault(emptyList())
                // Zayit's page: the first 25, boosted and sorted within the page
                val lexPage =
                    runCatching {
                        lexical.openSession(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)?.use { it.nextPage(25, snippets = false)?.hits }.orEmpty()
                    }.getOrDefault(emptyList())
                val denseCand = dense.search(query, 150, baseOnly, bookIds)
                val hybBase =
                    if (scope == "fallback") {
                        hybrid.openSession(query, 5, baseBookOnly = true)?.use { it.nextPage(25, snippets = false)?.hits }.orEmpty()
                    } else {
                        emptyList()
                    }
                out.write(
                    JSONObject()
                        .put("id", id.toInt())
                        .put("query", query)
                        .put("book", book ?: JSONObject.NULL)
                        .put("ext", ext == "1")
                        .put("scope", scope)
                        .put("lexBaseTotal", baseTotal ?: JSONObject.NULL)
                        .put("lexScopeTotal", scopeTotal)
                        .put("hybMs", (t1 - t0) / 1_000_000)
                        .put("hybrid", ids(hyb))
                        .put("lexPage", ids(lexPage))
                        .put("lexCand", ids(lexCand))
                        .put("denseCand", JSONArray(denseCand.map { JSONArray().put(it.lineId).put(it.score.toDouble()) }))
                        .put("hybridBase", ids(hybBase))
                        .toString(),
                )
                out.newLine()
                out.flush()
                System.err.println("$id $scope lex=$scopeTotal hyb=${hyb.size} ${(t1 - t0) / 1_000_000}ms")
            }
        }
        hybrid.close()
        dense.close()
    }
