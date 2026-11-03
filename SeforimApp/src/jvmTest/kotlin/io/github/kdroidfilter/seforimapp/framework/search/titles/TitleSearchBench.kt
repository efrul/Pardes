package io.github.kdroidfilter.seforimapp.framework.search.titles

import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.framework.search.AcronymFrequencyCache
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimlibrary.dao.CatalogLoader
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Paths
import java.sql.DriverManager
import kotlin.test.Test

/**
 * The [TitleSearchCases] run as the app runs them, the lookup index's suggestions against [BookTitleIndex]'s, with the
 * time a query costs. Writes its report to `TITLE_BENCH_OUT`; runs only with `SEFORIMAPP_DATABASE_PATH` and that set.
 */
class TitleSearchBench {
    @Test
    fun bench() {
        val dbPath = System.getenv("SEFORIMAPP_DATABASE_PATH")
        val out = System.getenv("TITLE_BENCH_OUT")
        assumeTrue(dbPath != null && out != null && File(dbPath).exists())
        val report = StringBuilder()
        val lookup = LuceneLookupSearchService(Paths.get("$dbPath.lookup.lucene"), acronymCache = AcronymFrequencyCache { dbPath!! })
        val catalog = CatalogLoader.loadCatalog(dbPath!!) ?: error("no catalog.pb")
        var t = System.nanoTime()
        val index = BookTitleIndexLoader.load(catalog, dbPath)
        report.appendLine("index: ${index.bookCount} books, built in ${(System.nanoTime() - t) / 1_000_000} ms")
        val aliases = HashMap<Long, MutableList<String>>()
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { db ->
            db.createStatement().executeQuery("SELECT authorId, alias FROM author_alias").use { rs ->
                while (rs.next()) aliases.getOrPut(rs.getLong(1)) { ArrayList() } += rs.getString(2)
            }
        }

        var oldOk = 0
        var newOk = 0
        report.appendLine("\n## books (found among the first ${TitleSearchCases.TOP})")
        for ((q, expected) in TitleSearchCases.BOOKS) {
            val old = lookup.suggestBooks(q, 120).map { it.title }
            val new = index.searchBooks(q, 120).map { it.book.title }
            val o = old.indexOf(expected)
            val n = new.indexOf(expected)
            if (o in 0 until TitleSearchCases.TOP) oldOk++
            if (n in 0 until TitleSearchCases.TOP) newOk++
            report.appendLine("$q | $expected | old ${rank(o)} ${old.take(3)} | new ${rank(n)} ${new.take(3)}")
        }
        report.appendLine("books: old $oldOk / ${TitleSearchCases.BOOKS.size}, new $newOk")

        oldOk = 0
        newOk = 0
        report.appendLine("\n## authors (among the first ${TitleSearchCases.TOP_AUTHORS})")
        for ((q, expected) in TitleSearchCases.AUTHORS) {
            val old =
                lookup
                    .suggestAuthors(q, 15)
                    .filter { hit ->
                        val names = listOf(hit.name, AuthorNames.display(hit.name)) + aliases[hit.id].orEmpty()
                        AuthorNames.typedAs(q, names, lookup::typedAsWordsOf)
                    }.map { it.name }
            val new = index.searchAuthors(q, 15).map { it.author.name }
            val o = old.indexOf(expected)
            val n = new.indexOf(expected)
            if (o in 0 until TitleSearchCases.TOP_AUTHORS) oldOk++
            if (n in 0 until TitleSearchCases.TOP_AUTHORS) newOk++
            report.appendLine("$q | $expected | old ${rank(o)} ${old.take(3)} | new ${rank(n)} ${new.take(3)}")
        }
        report.appendLine("authors: old $oldOk / ${TitleSearchCases.AUTHORS.size}, new $newOk")

        report.appendLine("\n## held out")
        val lookupNew = appLookup(dbPath)
        var heldOld = 0
        var heldNew = 0
        for ((q, expected) in TitleSearchCases.HELD_OUT_BOOKS) {
            val old = lookup.suggestBooks(q, 120).map { it.title }.indexOf(expected)
            val new = lookupNew.suggestBooks(q, 120).map { it.title }
            val n = new.indexOf(expected)
            if (old in 0 until TitleSearchCases.TOP) heldOld++
            if (n in 0 until TitleSearchCases.TOP) heldNew++
            report.appendLine("$q | $expected | old ${rank(old)} | new ${rank(n)} ${new.take(3)}")
        }
        report.appendLine("held-out books: old $heldOld / ${TitleSearchCases.HELD_OUT_BOOKS.size}, new $heldNew")
        heldOld = 0
        heldNew = 0
        for ((q, expected) in TitleSearchCases.HELD_OUT_AUTHORS) {
            val old =
                lookup
                    .suggestAuthors(q, 15)
                    .filter { hit ->
                        val names = listOf(hit.name, AuthorNames.display(hit.name)) + aliases[hit.id].orEmpty()
                        AuthorNames.typedAs(q, names, lookup::typedAsWordsOf)
                    }.map { it.name }
                    .indexOf(expected)
            val new = lookupNew.suggestAuthors(q, 15).map { it.name }
            val n = new.indexOf(expected)
            if (old in 0 until TitleSearchCases.TOP_AUTHORS) heldOld++
            if (n in 0 until TitleSearchCases.TOP_AUTHORS) heldNew++
            report.appendLine("$q | $expected | old ${rank(old)} | new ${rank(n)} ${new.take(3)}")
        }
        report.appendLine("held-out authors: old $heldOld / ${TitleSearchCases.HELD_OUT_AUTHORS.size}, new $heldNew")

        report.appendLine("\n## the authors of the book typed")
        for ((q, expected) in TitleSearchCases.AUTHOR_OF_BOOK) {
            val top = index.searchBooks(q, 3).filter { it.tier >= 2 }
            val authors = top.flatMap { index.authorsOf(it.book.id) }.map { it.name }.distinct()
            report.appendLine("$q | $expected | ${if (expected in authors) "ok" else "MISSING"} $authors")
        }

        // Time per query, typed letter by letter as the bar sees it
        val typed =
            (TitleSearchCases.BOOKS.map { it.first } + TitleSearchCases.AUTHORS.map { it.first }).flatMap { q ->
                (2..q.length).map { q.take(it) }
            }
        repeat(2) {
            typed.forEach {
                index.searchBooks(it, 120)
                index.searchAuthors(it, 15)
            }
        }
        t = System.nanoTime()
        var worst = 0L
        var worstQuery = ""
        for (q in typed) {
            val s = System.nanoTime()
            index.searchBooks(q, 120)
            index.searchAuthors(q, 15)
            val d = System.nanoTime() - s
            if (d > worst) {
                worst = d
                worstQuery = q
            }
        }
        val avg = (System.nanoTime() - t) / typed.size / 1000
        report.appendLine("\nnew, per keystroke (${typed.size}): $avg us on average, worst ${worst / 1000} us ($worstQuery)")
        t = System.nanoTime()
        for (q in typed) lookup.suggestBooks(q, 120)
        report.appendLine("old books, per keystroke: ${(System.nanoTime() - t) / typed.size / 1000} us on average")
        File(out!!).writeText(report.toString())
    }

    private fun rank(i: Int): String = if (i < 0) "—" else "#${i + 1}"
}
