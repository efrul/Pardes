import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine
import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SnippetProvider
import io.github.kdroidfilter.seforimlibrary.search.SnippetSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.sql.DriverManager

// The two runtime checks of the semantic fixes (README.md), built as Eval.kt is: the bold of the hybrid results' lexical
// lines, and dense searches at once against one at a time.
//
// args: seforim.db, snippets|concurrency
fun main(args: Array<String>): Unit =
    runBlocking {
        val db = Path.of(args[0])
        val indexPath = Path.of("$db.lucene")
        val con = DriverManager.getConnection("jdbc:sqlite:$db")
        val lines = con.prepareStatement("SELECT lineIndex, content FROM line WHERE bookId = ? AND lineIndex BETWEEN ? AND ?")
        val one =
            con.prepareStatement(
                "SELECT l.bookId, l.lineIndex, l.content, b.title FROM line l JOIN book b ON b.id = l.bookId WHERE l.id = ?",
            )
        val provider =
            SnippetProvider { infos ->
                SnippetSources.build(infos) { bookId, from, to ->
                    synchronized(lines) {
                        lines.setLong(1, bookId)
                        lines.setInt(2, from)
                        lines.setInt(3, to)
                        lines.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getInt(1), rs.getString(2)) } }
                    }
                }
            }
        val lexical = LuceneSearchEngine(indexPath, provider, dictionaryPath = indexPath.resolveSibling("lexical.db"))
        when (args[1]) {
            "snippets" -> {
                val hybrid =
                    HybridSearchEngine(lexical, indexPath) { lineId, _ ->
                        synchronized(one) {
                            one.setLong(1, lineId)
                            one.executeQuery().use { rs ->
                                if (rs.next()) {
                                    LineHit(
                                        rs.getLong(1),
                                        rs.getString(4),
                                        lineId,
                                        rs.getInt(2),
                                        "",
                                        0f,
                                        rs.getString(3),
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                    }
                for (q in listOf("ואהבת לרעך כמוך", "שור שנגח את הפרה", "כמה נרות מדליקים בחנוכה", "תפילת העמידה")) {
                    val lexIds =
                        lexical
                            .openSession(q, 5)
                            ?.use { it.nextPage(150, snippets = false)?.hits }
                            .orEmpty()
                            .map { it.lineId }
                            .toSet()
                    val hits = hybrid.openSession(q, 5)?.use { it.nextPage(20, snippets = true)?.hits }.orEmpty()
                    val lexHits = hits.filter { it.lineId in lexIds }
                    println(
                        "$q: ${hits.size} hits, ${lexHits.size} found by words, bold in ${lexHits.count {
                            "<b>" in it.snippet
                        }} of them, in ${hits.count { "<b>" in it.snippet }} in all",
                    )
                }
            }
            "concurrency" -> {
                val dense = IndependentSemanticSearch.open(indexPath)!!
                val queries =
                    listOf(
                        "כמה זמן מחכים בין בשר לחלב",
                        "שור שנגח את הפרה",
                        "מי שמצא אבידה",
                        "תפילת העמידה",
                        "הדלקת נרות חנוכה",
                        "ספירת העומר",
                        "כיבוד אב ואם",
                        "איסור לשון הרע",
                    )
                val expected = queries.associateWith { q -> dense.search(q, 150, false, null).map { it.lineId } }
                var wrong = 0
                var failed = 0
                repeat(5) { round ->
                    val got =
                        queries
                            .map { q ->
                                async(Dispatchers.Default) {
                                    q to
                                        runCatching { dense.search(q, 150, false, null).map { it.lineId } }
                                }
                            }.awaitAll()
                    for ((q, r) in got) {
                        r.onFailure {
                            failed++
                            if (failed <= 3) println("round $round, $q: ${it.javaClass.simpleName}: ${it.message}")
                        }
                        r.onSuccess {
                            if (it !=
                                expected[q]
                            ) {
                                wrong++
                                if (wrong <=
                                    3
                                ) {
                                    println("round $round, $q: ${it.zip(expected[q]!!).count { (a, b) -> a != b }} line ids differ")
                                }
                            }
                        }
                    }
                }
                println("concurrent searches: ${5 * queries.size}, wrong results $wrong, exceptions $failed")
            }
        }
    }
