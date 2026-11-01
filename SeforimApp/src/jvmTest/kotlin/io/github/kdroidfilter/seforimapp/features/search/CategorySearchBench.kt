package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.dao.CatalogLoader
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.runBlocking
import org.apache.lucene.document.IntPoint
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import java.nio.file.Paths

private val BENCH_QUERIES =
    listOf(
        "אמר רבי יוחנן",
        "קל וחומר",
        "תפילין",
        "שבת",
        "ואהבת לרעך כמוך",
        "מצות עשה שהזמן גרמא",
    )

/**
 * Where a search scoped to a category spends its time, against a search of the whole library: run as the app does
 * (facets, then a session and its first page), lexical search alone. `SEFORIMAPP_DATABASE_PATH` names seforim.db,
 * `BENCH_CATEGORIES` the category ids (default: תלמוד, תנ״ך).
 */
fun main(args: Array<String>) {
    val dbPath = System.getenv("SEFORIMAPP_DATABASE_PATH") ?: error("SEFORIMAPP_DATABASE_PATH")
    val rounds = args.firstOrNull()?.toInt() ?: 5
    val categories = System.getenv("BENCH_CATEGORIES")?.split(',')?.map { it.trim().toLong() } ?: listOf(12L, 1L)
    val repository = SeforimRepository(dbPath, PersistentSqliteDriver("jdbc:sqlite:$dbPath"))
    val indexPath = Paths.get("$dbPath.lucene")
    val engine =
        LuceneSearchEngine(indexPath, RepositorySnippetSourceProvider(repository), dictionaryPath = indexPath.resolveSibling("lexical.db"))

    runBlocking {
        val scopes = loadScopes(dbPath, repository, categories)
        val w = System.nanoTime()
        engine.warmUp()
        println("warmUp ${ms(w)} ms")
        printFilterCosts(indexPath, scopes, rounds)
        printSearchTimes(engine, scopes, rounds)
    }
    engine.close()
}

// A category searched: its title, all its books and its base books
private data class BenchScope(
    val title: String,
    val all: List<Long>,
    val base: List<Long>,
)

// The books of each category, as the app loaded them (every book whole, from the database) and from the catalog
private suspend fun loadScopes(
    dbPath: String,
    repository: SeforimRepository,
    categories: List<Long>,
): List<BenchScope> {
    val catalog = CatalogLoader.loadCatalog(dbPath) ?: error("no catalog.pb")
    return categories.map { id ->
        val s = System.nanoTime()
        val books = repository.getBooksUnderCategoryTree(id)
        val dbMs = ms(s)
        val s2 = System.nanoTime()
        val node = findCategory(catalog.rootCategories, id) ?: error("no category $id")
        val all = ArrayList<Long>()
        val base = ArrayList<Long>()
        collectBooks(node, all, base)
        val catalogMicros = (System.nanoTime() - s2) / 1_000
        println(
            "category $id ${node.title}: ${all.size} books (${base.size} base); " +
                "getBooksUnderCategoryTree ${books.size} books in $dbMs ms, from the catalog in $catalogMicros µs",
        )
        BenchScope(node.title, all, base)
    }
}

// The filters alone: what Lucene builds to restrict a search, with no query cache (the app's engine caches the
// base-books filter only)
private fun printFilterCosts(
    indexPath: Path,
    scopes: List<BenchScope>,
    rounds: Int,
) {
    DirectoryReader.open(FSDirectory.open(indexPath)).use { reader ->
        val searcher = IndexSearcher(reader).apply { queryCache = null }
        val lines = TermQuery(Term("type", "line"))

        fun filtered(filter: Query): Query =
            BooleanQuery
                .Builder()
                .add(lines, BooleanClause.Occur.FILTER)
                .add(filter, BooleanClause.Occur.FILTER)
                .build()

        fun bookIds(ids: List<Long>): Query = IntPoint.newSetQuery("book_id", *ids.map { it.toInt() }.toIntArray())
        val filters =
            listOf("is_base_book = 1" to IntPoint.newExactQuery("is_base_book", 1)) +
                scopes.flatMap { scope ->
                    listOf(
                        "${scope.title}: book_id in ${scope.all.size}" to bookIds(scope.all),
                        "${scope.title}: book_id in ${scope.base.size}" to bookIds(scope.base),
                    )
                }
        for ((label, filter) in filters) {
            val times =
                (0 until rounds).map {
                    val s = System.nanoTime()
                    val count = searcher.count(filtered(filter))
                    (System.nanoTime() - s) / 1_000_000.0 to count
                }
            println("filter %-40s %8d lines, %s".format(label, times.first().second, stats(times.map { it.first })))
        }
    }
}

// Facets, then a session and its first page, for each query: the whole library, then each category whole and in its
// base books alone
private suspend fun printSearchTimes(
    engine: LuceneSearchEngine,
    scopes: List<BenchScope>,
    rounds: Int,
) {
    data class Variant(
        val label: String,
        val bookIds: List<Long>?,
        val baseOnly: Boolean,
    )
    val variants =
        listOf(Variant("כל הספרייה, ספרי יסוד", null, true), Variant("כל הספרייה, כל הספרים", null, false)) +
            scopes.flatMap { scope ->
                listOf(
                    Variant("${scope.title} (${scope.all.size} ספרים)", scope.all, false),
                    Variant("${scope.title}, ספרי יסוד (${scope.base.size})", scope.base, true),
                )
            }
    for (q in BENCH_QUERIES) {
        println("--- $q")
        for (v in variants) {
            val facetTimes = ArrayList<Double>()
            val pageTimes = ArrayList<Double>()
            var hits = 0L
            repeat(rounds) {
                val s = System.nanoTime()
                hits = engine.computeFacets(q, 5, bookIds = v.bookIds, baseBookOnly = v.baseOnly)?.totalHits ?: -1
                facetTimes += (System.nanoTime() - s) / 1_000_000.0
                val s2 = System.nanoTime()
                engine.openSession(q, 5, bookIds = v.bookIds, baseBookOnly = v.baseOnly)?.use { it.nextPage(25) }
                pageTimes += (System.nanoTime() - s2) / 1_000_000.0
            }
            println("  %-36s hits=%8d facets %s  page %s".format(v.label, hits, stats(facetTimes), stats(pageTimes)))
        }
    }
}

// Median and min, the first round (cold) left out when there are several
private fun stats(times: List<Double>): String {
    val warm = if (times.size > 1) times.drop(1) else times
    val sorted = warm.sorted()
    return "med=%7.1f ms min=%7.1f ms (first %7.1f)".format(sorted[sorted.size / 2], sorted.first(), times.first())
}

private fun findCategory(
    nodes: List<CatalogCategory>,
    id: Long,
): CatalogCategory? {
    for (node in nodes) {
        if (node.id == id) return node
        findCategory(node.subcategories, id)?.let { return it }
    }
    return null
}

private fun collectBooks(
    node: CatalogCategory,
    all: MutableList<Long>,
    base: MutableList<Long>,
) {
    node.books.forEach { book ->
        all += book.id
        if (book.isBaseBook) base += book.id
    }
    node.subcategories.forEach { collectBooks(it, all, base) }
}

private fun ms(start: Long) = (System.nanoTime() - start) / 1_000_000
