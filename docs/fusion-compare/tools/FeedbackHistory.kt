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
import java.util.Properties

// The feedback's searches through the app's engines (as search-order/harness/History.kt, with Pardes's fixed dictionary
// as Scope.kt, and the category scope): each in the scope it was done in.
//   book    a book: all of it
//   cat     a category: all its books (the app searches a scope of books without baseBookOnly)
//   all     all books
//   auto    base books, and all books when the lexical facets find none there
// args: seforim.db, searches.tsv (id, query, kind, scopeId, ext: feedback_searches.py), out.jsonl
// Each line: the app's hybrid first 50, and the 150 candidates of each side with their scores.
fun main(args: Array<String>): Unit =
    runBlocking {
        co.touchlab.kermit.Logger.setMinSeverity(co.touchlab.kermit.Severity.Warn)
        val db = Path.of(args[0])
        val indexPath = Path.of("$db.lucene")
        val dictionary = PardesLexicon.dictionaryFor(indexPath.resolveSibling("lexical.db"), indexPath)
        PardesLexicon.awaitBuild(30 * 60_000L)
        System.err.println("dictionary: $dictionary exists=${java.nio.file.Files.isRegularFile(dictionary)}")
        val lexical = LongQuerySearchEngine(LuceneSearchEngine(indexPath, null, dictionaryPath = dictionary))
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

        fun booksUnder(categoryId: Long): List<Long> =
            con.prepareStatement(
                "WITH RECURSIVE sub(id) AS (SELECT ? UNION ALL SELECT c.id FROM category c JOIN sub ON c.parentId = sub.id) " +
                    "SELECT b.id FROM book b WHERE b.categoryId IN (SELECT id FROM sub) ORDER BY b.id",
            ).use { ps ->
                ps.setLong(1, categoryId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong(1)) } }
            }

        val hybrid: SearchEngine = HybridSearchEngine(lexical, indexPath) { lineId, _ -> hit(lineId) }
        val dense = IndependentSemanticSearch.open(indexPath) ?: error("no model or vector index next to $indexPath")
        hybrid.warmUp()

        fun ids(hits: List<LineHit>) = JSONArray(hits.map { JSONArray().put(it.lineId).put(it.score.toDouble()) })

        File(args[2]).bufferedWriter().use { out ->
            File(args[1]).readLines().drop(1).filter { it.isNotBlank() }.forEach { line ->
                val (id, query, kind, scopeIdText, extText) = line.split('\t')
                val scopeId = scopeIdText.toLongOrNull()
                val ext = extText == "1"
                val errors = JSONObject()
                val bookIds: List<Long>? =
                    when (kind) {
                        "book" -> listOf(scopeId!!)
                        "cat" -> booksUnder(scopeId!!)
                        else -> null
                    }
                val baseTotal =
                    if (kind == "auto") {
                        runCatching { lexical.computeFacets(query, 5, baseBookOnly = true)?.totalHits ?: 0L }
                            .getOrElse { errors.put("facets", it.javaClass.simpleName); 0L }
                    } else {
                        null
                    }
                val scope =
                    when (kind) {
                        "book" -> "book"
                        "cat" -> "cat"
                        "all" -> "all"
                        else -> if (baseTotal!! > 0) "base" else "fallback"
                    }
                val baseOnly = scope == "base"
                val t0 = System.nanoTime()
                val hyb =
                    runCatching {
                        hybrid.openSession(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)?.use { s ->
                            (s.nextPage(25, snippets = false)?.hits.orEmpty()) + (s.nextPage(25, snippets = false)?.hits.orEmpty())
                        }.orEmpty()
                    }.getOrElse { errors.put("hybrid", it.javaClass.simpleName); emptyList() }
                val t1 = System.nanoTime()
                val lexCand =
                    runCatching {
                        lexical.openSession(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)?.use { it.nextPage(150, snippets = false)?.hits }.orEmpty()
                    }.getOrElse { errors.put("lexical", it.javaClass.simpleName); emptyList() }
                val lexTotal =
                    runCatching { lexical.computeFacets(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)?.totalHits ?: 0L }
                        .getOrElse { 0L }
                val denseCand =
                    runCatching { dense.search(query, 150, baseOnly, bookIds) }
                        .getOrElse { errors.put("dense", it.javaClass.simpleName); emptyList() }
                out.write(
                    JSONObject()
                        .put("id", id.toInt())
                        .put("query", query)
                        .put("kind", kind)
                        .put("scopeId", scopeId ?: JSONObject.NULL)
                        .put("ext", ext)
                        .put("scope", scope)
                        .put("lexTotal", lexTotal)
                        .put("hybMs", (t1 - t0) / 1_000_000)
                        .put("errors", errors)
                        .put("hybrid", ids(hyb))
                        .put("lexCand", ids(lexCand))
                        .put("denseCand", JSONArray(denseCand.map { JSONArray().put(it.lineId).put(it.score.toDouble()) }))
                        .toString(),
                )
                out.newLine()
                out.flush()
                System.err.println("$id $scope lex=${lexCand.size}/$lexTotal dense=${denseCand.size} hyb=${hyb.size} ${(t1 - t0) / 1_000_000}ms")
            }
        }
        hybrid.close()
        dense.close()
    }
