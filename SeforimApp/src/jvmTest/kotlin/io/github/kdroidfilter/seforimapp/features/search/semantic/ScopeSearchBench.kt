package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimapp.features.search.domain.BuildSearchTreeUseCase
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths
import java.sql.DriverManager

// The first words of a chapter among them: three of them fall in a trap of the vectors' graph (IndependentSemanticSearch.search)
private val SCOPE_QUERIES =
    listOf(
        "חזקת הבתים שלש שנים",
        "המוכר את הספינה",
        "המפקיד",
        "ארבעה אבות נזיקין",
        "קל וחומר",
        "אין אדם מעיז פניו בפני בעל חובו",
        "תפילין",
    )

// A search's scope as the app passes it: the books searched (null: the whole library), in the base books alone or not
private class Scope(
    val label: String,
    val bookIds: List<Long>?,
    val baseOnly: Boolean,
)

/**
 * Where a search in a scope spends its time with Pardes's semantic search, stage by stage: the query's vector, the
 * dense side in the scope, the words' candidates, and the search as the app runs it (facets, then a session and its
 * first page). `SEFORIMAPP_DATABASE_PATH` names seforim.db, with its full-text index and the semantic pack beside it;
 * the first argument is the rounds (the first, cold, apart).
 */
fun main(args: Array<String>) {
    val dbPath = System.getenv("SEFORIMAPP_DATABASE_PATH") ?: error("SEFORIMAPP_DATABASE_PATH")
    val rounds = args.firstOrNull()?.toInt() ?: 4
    val indexPath = Paths.get("$dbPath.lucene")
    val repository = SeforimRepository(dbPath, PersistentSqliteDriver("jdbc:sqlite:$dbPath"))
    val lexical =
        LuceneSearchEngine(indexPath, RepositorySnippetSourceProvider(repository), dictionaryPath = indexPath.resolveSibling("lexical.db"))
    val spec = IndependentSemanticSearch.findOpenModel(indexPath) ?: error("no pardes-model beside the index")
    val embedder = OnnxEmbeddingProvider.open(spec)
    val dense = IndependentSemanticSearch.open(indexPath) ?: error("no vector index beside the index")
    val engine =
        HybridSearchEngine(lexical, indexPath) { lineId, query ->
            repository.getLine(lineId)?.let { line ->
                LineHit(
                    bookId = line.bookId,
                    bookTitle = repository.getBook(line.bookId)?.title.orEmpty(),
                    lineId = lineId,
                    lineIndex = line.lineIndex,
                    snippet = lexical.buildSnippet(line.content, query, 5),
                    score = 0f,
                    rawText = line.content,
                )
            }
        }
    val scopes = scopes(dbPath)
    runBlocking {
        val w = System.nanoTime()
        engine.warmUp()
        println("warmUp ${millis(w)} ms")
        for (query in System.getenv("BENCH_QUERY")?.let { listOf(it) } ?: SCOPE_QUERIES) {
            println("--- $query")
            for (scope in scopes) {
                val vector = ArrayList<Double>()
                val denseSide = ArrayList<Double>()
                val words = ArrayList<Double>()
                val facets = ArrayList<Double>()
                val page = ArrayList<Double>()
                val tree = ArrayList<Double>()
                var hits = 0L
                var found = 0
                var books = 0
                // The result tree, in a new tab (its books and categories read) and in the same one
                engine.computeFacets(query, 5, bookIds = scope.bookIds, baseBookOnly = scope.baseOnly)?.let { counted ->
                    books = counted.bookCounts.size
                    val inTab = BuildSearchTreeUseCase(repository)
                    repeat(2) { tree += timed { inTab.invoke(counted.categoryCounts, counted.bookCounts) } }
                }
                repeat(rounds) {
                    vector += timed { embedder.embed(query) }
                    denseSide += timed { found = dense.search(query, 150, scope.baseOnly, scope.bookIds).size }
                    words +=
                        timed {
                            lexical
                                .openSession(query, near = 5, bookIds = scope.bookIds, baseBookOnly = scope.baseOnly)
                                ?.use { it.nextPage(150, snippets = false) }
                        }
                    facets +=
                        timed {
                            hits = engine.computeFacets(query, 5, bookIds = scope.bookIds, baseBookOnly = scope.baseOnly)?.totalHits ?: -1
                        }
                    page +=
                        timed {
                            engine.openSession(query, 5, bookIds = scope.bookIds, baseBookOnly = scope.baseOnly)?.use { it.nextPage(25) }
                        }
                }
                val first = tree.getOrElse(0) { 0.0 }
                val again = tree.getOrElse(1) { 0.0 }
                val times = "vector ${stats(vector)} | dense ${stats(denseSide)} | words ${stats(words)} | facets ${stats(facets)}"
                val tab = "page ${stats(page)} | tree of $books books %.0f then %.0f".format(first, again)
                println("  %-34s words=%8d dense=%3d | %s | %s".format(scope.label, hits, found, times, tab))
            }
        }
    }
    engine.close()
    dense.close()
    embedder.close()
}

// The scopes people search in: the whole library, a large category, a tractate's commentators, one book
private fun scopes(dbPath: String): List<Scope> =
    DriverManager.getConnection("jdbc:sqlite:file:$dbPath?mode=ro").use { db ->
        fun ids(sql: String): List<Long> =
            db.createStatement().use { st -> st.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } } }

        fun under(category: Long) =
            "SELECT b.id FROM book b WHERE b.categoryId IN (SELECT descendantId FROM category_closure WHERE ancestorId = $category)"
        val bavaBatra = ids("SELECT id FROM book WHERE title = 'בבא בתרא' AND categoryId IN (${(14..19).joinToString()})").first()
        val commentaries = "SELECT DISTINCT targetBookId FROM link WHERE sourceBookId = $bavaBatra AND connectionTypeId = 1"
        val rishonim =
            "$commentaries AND targetBookId IN (SELECT b.id FROM book b JOIN category_closure cc ON cc.descendantId = b.categoryId " +
                "JOIN category c ON c.id = cc.ancestorId WHERE c.title = 'ראשונים')"
        listOf(
            Scope("כל הספרייה, ספרי יסוד", null, true),
            Scope("כל הספרייה", null, false),
            Scope("תלמוד", ids(under(12)), false),
            Scope("הלכה", ids(under(44)), false),
            Scope("מפרשי בבא בתרא", ids(commentaries), false),
            Scope("ראשונים על בבא בתרא", ids(rishonim), false),
            Scope("בבא בתרא", listOf(bavaBatra), false),
        ).map { Scope("${it.label} (${it.bookIds?.size ?: "הכל"})", it.bookIds, it.baseOnly) }
    }

private inline fun timed(block: () -> Unit): Double {
    val start = System.nanoTime()
    block()
    return (System.nanoTime() - start) / 1e6
}

private fun millis(start: Long) = (System.nanoTime() - start) / 1_000_000

// Median, the first round (cold) apart when there are several
private fun stats(times: List<Double>): String {
    val warm = (if (times.size > 1) times.drop(1) else times).sorted()
    return "%6.0f (first %6.0f)".format(warm[warm.size / 2], times.first())
}
