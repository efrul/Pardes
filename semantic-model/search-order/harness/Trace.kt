import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine
import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.search.LongQuerySearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties

// Where given lines stand in one search, as the app runs it: their place among all the lexical hits (up to 5000),
// among the first 1000 dense hits and in the fused list (the first 100), with each side's first 10 and the fused 12.
//
// args: seforim.db, lineIds (comma-separated), a UTF-8 file of queries, one a line: "base:<query>" or "all:<query>"
fun main(args: Array<String>): Unit =
    runBlocking {
        val db = Path.of(args[0])
        val indexPath = Path.of("$db.lucene")
        val targets = args[1].split(',').map { it.toLong() }
        val lexical = LongQuerySearchEngine(LuceneSearchEngine(indexPath, null, dictionaryPath = indexPath.resolveSibling("lexical.db")))
        val con = DriverManager.getConnection("jdbc:sqlite:$db", Properties().apply { put("open_mode", "1") })
        val st = con.prepareStatement("SELECT l.bookId, l.lineIndex, l.content, b.title, l.heRef FROM line l JOIN book b ON b.id = l.bookId WHERE l.id = ?")

        fun hit(lineId: Long): LineHit? =
            synchronized(st) {
                st.setLong(1, lineId)
                st.executeQuery().use { rs -> if (rs.next()) LineHit(rs.getLong(1), rs.getString(4), lineId, rs.getInt(2), "", 0f, rs.getString(3)) else null }
            }

        fun ref(lineId: Long): String =
            synchronized(st) {
                st.setLong(1, lineId)
                st.executeQuery().use { rs -> if (rs.next()) (rs.getString(5) ?: rs.getString(4)) else "?" }
            }
        val hybrid = HybridSearchEngine(lexical, indexPath) { lineId, _ -> hit(lineId) }
        val dense = IndependentSemanticSearch.open(indexPath) ?: error("no model or vector index next to $indexPath")
        for (arg in File(args[2]).readLines().filter { it.isNotBlank() }) {
            val (scope, query) = arg.split(':', limit = 2)
            val baseOnly = scope == "base"
            val total = lexical.computeFacets(query, 5, baseBookOnly = baseOnly)?.totalHits ?: 0
            val lex = lexical.openSession(query, 5, baseBookOnly = baseOnly)?.use { it.nextPage(5000, snippets = false)?.hits }.orEmpty()
            val den = dense.search(query, 1000, baseOnly, null)
            val hyb = hybrid.openSession(query, 5, baseBookOnly = baseOnly)?.use { s -> (1..4).flatMap { s.nextPage(25, snippets = false)?.hits.orEmpty() } }.orEmpty()
            println("\n=== [$scope] $query: lexical hits $total")
            for (t in targets) {
                val l = lex.indexOfFirst { it.lineId == t }
                val d = den.indexOfFirst { it.lineId == t }
                val h = hyb.indexOfFirst { it.lineId == t }
                println(
                    "  ${ref(t)}: lexical ${if (l >= 0) "#${l + 1} (score ${"%.1f".format(lex[l].score)})" else "not found"}" +
                        ", dense ${if (d >= 0) "#${d + 1} (${"%.3f".format(den[d].score)})" else "not in the first 1000"}" +
                        ", fused ${if (h >= 0) "#${h + 1}" else "not in the first 100"}",
                )
            }
            println("  lexical first 10: " + lex.take(10).joinToString(" / ") { "${ref(it.lineId)} (${"%.1f".format(it.score)})" })
            println("  dense first 10: " + den.take(10).joinToString(" / ") { "${ref(it.lineId)} (${"%.3f".format(it.score)})" })
            println("  fused first 12: " + hyb.take(12).joinToString(" / ") { ref(it.lineId) })
        }
        hybrid.close()
        dense.close()
    }
