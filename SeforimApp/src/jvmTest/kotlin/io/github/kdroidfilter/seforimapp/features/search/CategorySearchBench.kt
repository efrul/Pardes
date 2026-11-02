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
import org.apache.lucene.search.PointRangeQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.QueryCachingPolicy
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
        printCategoryFieldTimes(indexPath, scopes, categories, rounds)
        if (System.getenv("BENCH_SKIP_ENGINE") == null) printSearchTimes(engine, scopes, rounds)
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

// Words as the index splits them (`text`), searched as they are, for the filters alone to differ
private val RAW_QUERIES = listOf(listOf("אמר", "רבא"), listOf("תפילין"), listOf("קל", "וחומר"), listOf("אמר", "רבי", "יוחנן"))

/**
 * A category by the field every line carries with its categories (`ancestor_category_ids`: its own and every one above
 * it), one value as the base books' `is_base_book`, cached as the engine caches that one, against its books' ids (what
 * the app passes, `book_id` in a set, built again for each search): the words alone, counted (the facets count every
 * line) and their first page.
 */
private fun printCategoryFieldTimes(
    indexPath: Path,
    scopes: List<BenchScope>,
    categories: List<Long>,
    rounds: Int,
) {
    DirectoryReader.open(FSDirectory.open(indexPath)).use { reader ->
        val searcher = IndexSearcher(reader).apply { queryCachingPolicy = EngineFilters }
        val base = IntPoint.newExactQuery("is_base_book", 1)

        fun ids(books: List<Long>) = IntPoint.newSetQuery("book_id", *books.map { it.toInt() }.toIntArray())

        fun category(id: Long) = IntPoint.newExactQuery("ancestor_category_ids", id.toInt())

        fun both(
            a: Query,
            b: Query,
        ): Query =
            BooleanQuery
                .Builder()
                .add(a, BooleanClause.Occur.FILTER)
                .add(b, BooleanClause.Occur.FILTER)
                .build()
        val variants =
            listOf<Pair<String, Query?>>("כל הספרייה" to null, "כל הספרייה, ספרי יסוד" to base) +
                scopes.zip(categories).flatMap { (scope, id) ->
                    listOf(
                        "${scope.title}: book_id in ${scope.all.size}" to ids(scope.all),
                        "${scope.title}: ancestor_category_ids = $id" to category(id),
                        "${scope.title} יסוד: book_id in ${scope.base.size}" to ids(scope.base),
                        "${scope.title} יסוד: ancestor = $id, is_base_book" to both(category(id), base),
                    )
                }
        for (words in RAW_QUERIES) {
            println("--- ${words.joinToString(" ")} (text)")
            for ((label, filter) in variants) {
                val query =
                    BooleanQuery
                        .Builder()
                        .apply {
                            words.forEach { add(TermQuery(Term("text", it)), BooleanClause.Occur.MUST) }
                            add(TermQuery(Term("type", "line")), BooleanClause.Occur.FILTER)
                            filter?.let { add(it, BooleanClause.Occur.FILTER) }
                        }.build()
                var hits = 0
                val countTimes = ArrayList<Double>()
                val pageTimes = ArrayList<Double>()
                repeat(rounds) {
                    val s = System.nanoTime()
                    hits = searcher.count(query)
                    countTimes += (System.nanoTime() - s) / 1_000_000.0
                    val s2 = System.nanoTime()
                    searcher.search(query, 25)
                    pageTimes += (System.nanoTime() - s2) / 1_000_000.0
                }
                println("  %-44s hits=%8d count %s  page %s".format(label, hits, stats(countTimes), stats(pageTimes)))
            }
        }
    }
}

// The engine's caching (SeforimLibrary's StableFiltersCachingPolicy), and the categories' field cached the same way
private object EngineFilters : QueryCachingPolicy {
    override fun onUse(query: Query) = Unit

    override fun shouldCache(query: Query): Boolean =
        when (query) {
            is TermQuery -> query.term.field() == "type"
            is PointRangeQuery -> query.field == "is_base_book" || query.field == "ancestor_category_ids"
            else -> false
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
