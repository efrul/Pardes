package io.github.kdroidfilter.seforimapp.framework.search.titles

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [TitleSearchCases] over the installed library, through the lookup service as the bar and the book tree call it;
 * skipped when it is absent.
 */
class TitleSearchE2ETest {
    private val dbPath =
        System.getenv("SEFORIMAPP_DATABASE_PATH")
            ?: (System.getProperty("user.home") + "/Library/Application Support/io.github.kdroidfilter.seforimapp/databases/seforim.db")

    private val lookup by lazy { appLookup(dbPath) }

    @Test
    fun `books are found by their names as people type them`() {
        assumeTrue("No database at $dbPath", File(dbPath).exists())
        val missed =
            TitleSearchCases.BOOKS.filter { (query, title) ->
                title !in lookup.suggestBooks(query, limit = 120).take(TitleSearchCases.TOP).map { it.title }
            }
        assertTrue(missed.isEmpty(), "not among the first ${TitleSearchCases.TOP}: $missed")
    }

    @Test
    fun `authors are found by their names and other names as people type them`() {
        assumeTrue("No database at $dbPath", File(dbPath).exists())
        val missed =
            TitleSearchCases.AUTHORS.filter { (query, name) ->
                name !in lookup.suggestAuthors(query, limit = 15).take(TitleSearchCases.TOP_AUTHORS).map { it.name }
            }
        assertTrue(missed.isEmpty(), "not among the first ${TitleSearchCases.TOP_AUTHORS}: $missed")
    }

    @Test
    fun `a book's title typed offers its author`() {
        assumeTrue("No database at $dbPath", File(dbPath).exists())
        val missed =
            TitleSearchCases.AUTHOR_OF_BOOK.filter { (title, author) ->
                val named = lookup.suggestBooks(title, limit = 3).filter { it.namesBook }
                author !in named.flatMap { lookup.authorsOfBook(it.id) }.map { it.name }
            }
        assertTrue(missed.isEmpty(), "author not offered: $missed")
    }
}
