import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine
import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.search.LongQuerySearchEngine
import io.github.kdroidfilter.seforimapp.framework.search.PardesLexicon
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties

// For each query and scope, what the app does (SearchResultViewModel), with the app's engines:
//   facets (lexical count) and the hybrid's first page side by side, then paging the hybrid list to its end.
// Scopes: base   = the regular search (base books, no filter)
//         all    = the regular search with the commentaries ("extended")
//         cat:ID = the bar's category scope (all books under the category, baseBookOnly = false)
//         tab:ID = the category tab above the results (books under the category, baseBookOnly = true)
//         book:ID = one book, picked in the bar or from its results (baseBookOnly = false)
// args: seforim.db queries.txt scopes(comma separated) out.tsv [maxList]
fun main(args: Array<String>): Unit =
    runBlocking {
        co.touchlab.kermit.Logger.setMinSeverity(co.touchlab.kermit.Severity.Info)
        val db = Path.of(args[0])
        val indexPath = Path.of("$db.lucene")
        // As the app: dictionaryFor returns the fixed copy's path at once, built in the background; the warm-up waits for it
        val dictionary = PardesLexicon.dictionaryFor(indexPath.resolveSibling("lexical.db"), indexPath)
        PardesLexicon.awaitBuild(30 * 60_000L)
        System.err.println("dictionary: $dictionary exists=${java.nio.file.Files.isRegularFile(dictionary)}")
        val lucene = LuceneSearchEngine(indexPath, null, dictionaryPath = dictionary)
        val lexical = LongQuerySearchEngine(lucene)
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
        val hybrid: SearchEngine = HybridSearchEngine(lexical, indexPath) { lineId, _ -> hit(lineId) }
        val dense = IndependentSemanticSearch.open(indexPath) ?: error("no model or vector index next to $indexPath")

        fun booksUnder(categoryId: Long): List<Long> =
            con.prepareStatement(
                "WITH RECURSIVE sub(id) AS (SELECT ? UNION ALL SELECT c.id FROM category c JOIN sub ON c.parentId = sub.id) " +
                    "SELECT b.id FROM book b WHERE b.categoryId IN (SELECT id FROM sub) ORDER BY b.id",
            ).use { ps ->
                ps.setLong(1, categoryId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong(1)) } }
            }

        val t = System.nanoTime()
        hybrid.warmUp()
        System.err.println("warm-up ${(System.nanoTime() - t) / 1_000_000} ms")

        val maxList = args.getOrNull(4)?.toInt() ?: 3000
        val queries = File(args[1]).readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val scopes = args[2].split(',')
        File(args[3]).bufferedWriter().use { out ->
            out.write(
                "query\tscope\tbooks\tlexTotal\tfacetsMs\tfirstPageMs\tbothMs\tlexTop150Ms\tdenseMs\tlexCand\tdenseCand\tunion" +
                    "\tpage1Total\tpage1Lex\tpage1DenseOnly\tlistLen\tlistLexical\tlistDenseOnly\tlastTotal\tpagingMs\n",
            )
            for (scope in scopes) {
                val (bookIds, baseOnly) =
                    when {
                        scope == "base" -> null to true
                        scope == "all" -> null to false
                        scope.startsWith("cat:") -> booksUnder(scope.substringAfter(':').toLong()) to false
                        scope.startsWith("tab:") -> booksUnder(scope.substringAfter(':').toLong()) to true
                        scope.startsWith("book:") -> listOf(scope.substringAfter(':').toLong()) to false
                        else -> error(scope)
                    }
                for (query in queries) {
                    repeat(2) { round ->
                        // round 0 warms this scope's filter and query; round 1 is reported (both are printed)
                        val ms = { start: Long -> (System.nanoTime() - start) / 1_000_000 }
                        var t0 = System.nanoTime()
                        val (facets, first) =
                            coroutineScope {
                                val f =
                                    async {
                                        val s = System.nanoTime()
                                        lexical.computeFacets(query, 5, bookIds = bookIds, baseBookOnly = baseOnly) to ms(s)
                                    }
                                val p =
                                    async {
                                        val s = System.nanoTime()
                                        val session = hybrid.openSession(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)!!
                                        val page = session.nextPage(25, snippets = true)
                                        Triple(session, page, ms(s))
                                    }
                                f.await() to p.await()
                            }
                        val both = ms(t0)
                        val (session, page1, firstMs) = first

                        t0 = System.nanoTime()
                        val lexCand =
                            lexical.openSession(query, 5, bookIds = bookIds, baseBookOnly = baseOnly)?.use {
                                it.nextPage(150, snippets = false)?.hits
                            }.orEmpty()
                        val lexMs = ms(t0)
                        t0 = System.nanoTime()
                        val denseCand = dense.search(query, 150, baseOnly, bookIds)
                        val denseMs = ms(t0)
                        val lexIds = lexCand.map { it.lineId }.toSet()
                        val union = (lexIds + denseCand.map { it.lineId }).size

                        val p1 = page1?.hits.orEmpty()
                        // The whole list, page after page (up to maxList), as the app loads it while scrolling
                        t0 = System.nanoTime()
                        val listIds = ArrayList<Long>(p1.map { it.lineId })
                        var lastTotal = page1?.totalHits ?: 0
                        var last = page1?.isLastPage ?: true
                        while (!last && listIds.size < maxList) {
                            val page = session.nextPage(25, snippets = false) ?: break
                            listIds += page.hits.map { it.lineId }
                            lastTotal = page.totalHits
                            last = page.isLastPage
                        }
                        val pagingMs = ms(t0)
                        session.close()
                        val denseOnly = denseCand.map { it.lineId }.toSet() - lexIds
                        val listDenseOnly = listIds.count { it in denseOnly }
                        val row =
                            listOf(
                                query, scope, bookIds?.size ?: -1, facets.first?.totalHits ?: -1, facets.second, firstMs, both, lexMs, denseMs,
                                lexCand.size, denseCand.size, union, page1?.totalHits ?: -1, p1.count { it.lineId in lexIds },
                                p1.count { it.lineId in denseOnly }, if (last) listIds.size else -listIds.size,
                                listIds.size - listDenseOnly, listDenseOnly, lastTotal, pagingMs,
                            ).joinToString("\t")
                        System.err.println("round $round: $row")
                        if (round == 1) {
                            out.write(row + "\n")
                            out.flush()
                        }
                    }
                }
            }
        }
    }
