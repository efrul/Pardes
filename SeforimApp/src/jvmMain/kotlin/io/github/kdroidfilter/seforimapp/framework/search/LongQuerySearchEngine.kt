package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchQueryParser
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.runBlocking
import org.apache.lucene.search.IndexSearcher
import java.util.concurrent.ConcurrentHashMap

/**
 * [lexical], with a query too large for Lucene searched as an exact quote instead of failing.
 *
 * SeforimLibrary's LuceneSearchEngine adds, for each word, its dictionary forms (up to 32) and ranking clauses over
 * them. Past about ten words they can pass Lucene's 1,024 clauses: the first page then throws
 * [IndexSearcher.TooManyNestedClauses], and the search shows nothing (8 of the 34 queries of 11 words or more in
 * semantic-model/eval-v1). Its facets, built from the required clauses only, still count.
 *
 * Such a query asks for all its words within five of each other, so it is in practice a pasted quote. Searched as
 * one exact phrase (in double quotes, without the dictionary forms), it finds that quote in a single clause. A
 * query Lucene can run is passed on unchanged.
 *
 * SeforimLibrary 4f07145 (Zayit 0.6.11, its #135) gives a long query's words fewer forms each and raises the limit to
 * 4,096 clauses; upstream then found no failure among 555 queries of 6 to 20 words. This fallback stays for a query
 * that still overflows.
 */
class LongQuerySearchEngine(
    private val lexical: SearchEngine,
) : SearchEngine by lexical {
    // The searches found too large (by a page that failed, or probed before the facets), so that the facets and the
    // pages of one search agree. By query and filters: a filter adds a clause, enough to cross the limit (a query of
    // eval-v1 fails in the base books and runs in all of them). Bounded: cleared when full
    private val tooLarge = ConcurrentHashMap<Search, Boolean>()

    private data class Search(
        val query: String,
        val near: Int,
        val bookFilter: Long?,
        val categoryFilter: Long?,
        val bookIds: Collection<Long>?,
        val lineIds: Collection<Long>?,
        val baseBookOnly: Boolean,
    )

    override fun openSession(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchSession? {
        fun open(q: String) = lexical.openSession(q, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
        val search = Search(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
        if (tooLarge[search] == true) return open(asQuote(query))
        val session = open(query) ?: return null
        return QuoteFallbackSession(session) {
            remember(search)
            open(asQuote(query))
        }
    }

    override fun computeFacets(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchFacets? {
        val search = Search(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
        val q = if (isTooLarge(search)) asQuote(query) else query
        return lexical.computeFacets(q, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
    }

    /**
     * Whether [search] fails. The facets run before (or beside) the first page, so a long query is probed with a
     * one-hit page under the same filters; a short one never is.
     */
    private fun isTooLarge(search: Search): Boolean {
        tooLarge[search]?.let { return it }
        if (search.query.split(WHITESPACE).count { it.isNotBlank() } < PROBE_WORDS) return false
        val fails =
            runBlocking {
                try {
                    with(search) {
                        lexical.openSession(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
                    }?.use { it.nextPage(1, snippets = false) }
                    false
                } catch (_: IndexSearcher.TooManyClauses) {
                    true
                }
            }
        remember(search, fails)
        return fails
    }

    private fun remember(
        search: Search,
        fails: Boolean = true,
    ) {
        if (tooLarge.size >= MAX_REMEMBERED) tooLarge.clear()
        tooLarge[search] = fails
    }

    /** [session], replaced by [reopen]'s session (the quote) when its first page is too large for Lucene. */
    private class QuoteFallbackSession(
        private var session: SearchSession,
        private val reopen: () -> SearchSession?,
    ) : SearchSession {
        private var served = false

        override suspend fun nextPage(
            limit: Int,
            snippets: Boolean,
        ): SearchPage? {
            val page =
                try {
                    session.nextPage(limit, snippets)
                } catch (e: IndexSearcher.TooManyClauses) {
                    // Lucene builds the whole query on the first page: a later one failing would be another error
                    if (served) throw e
                    session.close()
                    val quote = reopen() ?: return null
                    session = quote
                    quote.nextPage(limit, snippets)
                }
            served = true
            return page
        }

        override suspend fun refinedSnippets(hits: List<LineHit>): Map<Long, String> = session.refinedSnippets(hits)

        override fun close() = session.close()
    }

    companion object {
        /** Below this many words a query is never probed: the failures start at 11. */
        private const val PROBE_WORDS = 8
        private const val MAX_REMEMBERED = 256
        private val WHITESPACE = "\\s+".toRegex()

        /**
         * [query] as exact phrases: its quoted parts as they are, its free text as one more. Acronym quotes (רש"י)
         * stay inside, as they sit between letters.
         */
        internal fun asQuote(query: String): String {
            val parsed = SearchQueryParser.parse(query)
            return (parsed.exactPhrases + parsed.freeText)
                .filter { it.isNotBlank() }
                .joinToString(" ") { "\"$it\"" }
        }
    }
}
