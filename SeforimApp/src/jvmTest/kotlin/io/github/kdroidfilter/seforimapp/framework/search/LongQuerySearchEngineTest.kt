package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.runBlocking
import org.apache.lucene.search.IndexSearcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LongQuerySearchEngineTest {
    // The query of semantic-model/eval-v1 that LuceneSearchEngine could not run (v1-0002)
    private val long = "ההבטחה להוציא את ישראל מעוני מצרים ולהביא אותם לארץ שבעת העמים"
    private val quote = "\"$long\""

    /**
     * Throws as Lucene does for [failing] queries (in the base books only when [onlyInBase]); answers any other with
     * one hit, its line id the call's number.
     */
    private class FakeEngine(
        private val failing: Set<String>,
        private val onlyInBase: Boolean = false,
    ) : SearchEngine {
        val sessions = mutableListOf<String>()
        val facets = mutableListOf<String>()

        override fun openSession(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchSession {
            sessions += query
            val id = sessions.size.toLong()
            return object : SearchSession {
                override suspend fun nextPage(
                    limit: Int,
                    snippets: Boolean,
                ): SearchPage {
                    if (query in failing && (baseBookOnly || !onlyInBase)) throw IndexSearcher.TooManyNestedClauses()
                    return SearchPage(listOf(LineHit(1, "", id, 0, "", 1f, "")), totalHits = 1, isLastPage = true)
                }

                override fun close() {}
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
        ): SearchFacets {
            facets += query
            return SearchFacets(totalHits = if (query in failing) 1 else 2, categoryCounts = emptyMap(), bookCounts = emptyMap())
        }

        override fun searchBooksByTitlePrefix(
            query: String,
            limit: Int,
        ): List<Long> = emptyList()

        override fun buildSnippet(
            rawText: String,
            query: String,
            near: Int,
        ): String = rawText

        override fun close() {}
    }

    @Test
    fun `a query too large for Lucene is searched as a quote`() =
        runBlocking {
            val fake = FakeEngine(failing = setOf(long))
            val engine = LongQuerySearchEngine(fake)
            val page = engine.openSession(long, 5, baseBookOnly = true)!!.nextPage(10, snippets = false)
            assertEquals(listOf(2L), page!!.hits.map { it.lineId })
            assertEquals(listOf(long, quote), fake.sessions)
            // Known now: the next search with the same filters goes to the quote at once
            engine.openSession(long, 5, baseBookOnly = true)!!.nextPage(10)
            assertEquals(listOf(long, quote, quote), fake.sessions)
        }

    @Test
    fun `the facets of a query too large count its quote`() {
        val fake = FakeEngine(failing = setOf(long))
        val facets = LongQuerySearchEngine(fake).computeFacets(long, 5, baseBookOnly = true)
        assertEquals(2, facets!!.totalHits)
        assertEquals(listOf(quote), fake.facets)
        // Probed once, with a one-hit page
        assertEquals(listOf(long), fake.sessions)
    }

    @Test
    fun `a query too large in the base books only is quoted there only`() {
        val fake = FakeEngine(failing = setOf(long), onlyInBase = true)
        val engine = LongQuerySearchEngine(fake)
        engine.computeFacets(long, 5, baseBookOnly = true)
        engine.computeFacets(long, 5, baseBookOnly = false)
        assertEquals(listOf(quote, long), fake.facets)
    }

    @Test
    fun `a query Lucene runs is passed on, a short one never probed`() =
        runBlocking {
            val fake = FakeEngine(failing = emptySet())
            val engine = LongQuerySearchEngine(fake)
            engine.computeFacets("כמה זמן מחכים בין בשר לחלב", 5)
            assertEquals(emptyList<String>(), fake.sessions)
            engine.computeFacets(long, 5)
            assertEquals(listOf(long), fake.facets.drop(1))
            engine.openSession(long, 5)!!.nextPage(10)
            assertEquals(listOf(long, long), fake.sessions)
        }

    @Test
    fun `a failure after a page was served is not hidden`(): Unit =
        runBlocking {
            var calls = 0
            val inner =
                object : SearchEngine by FakeEngine(emptySet()) {
                    override fun openSession(
                        query: String,
                        near: Int,
                        bookFilter: Long?,
                        categoryFilter: Long?,
                        bookIds: Collection<Long>?,
                        lineIds: Collection<Long>?,
                        baseBookOnly: Boolean,
                    ): SearchSession =
                        object : SearchSession {
                            override suspend fun nextPage(
                                limit: Int,
                                snippets: Boolean,
                            ): SearchPage? {
                                if (++calls > 1) throw IndexSearcher.TooManyNestedClauses()
                                return SearchPage(emptyList(), totalHits = 0, isLastPage = false)
                            }

                            override fun close() {}
                        }
                }
            val session = LongQuerySearchEngine(inner).openSession("short", 5)!!
            session.nextPage(10)
            assertFailsWith<IndexSearcher.TooManyNestedClauses> { session.nextPage(10) }
        }

    @Test
    fun `the quote keeps acronyms and quoted parts`() {
        assertEquals("\"בכל מתרפאין חוץ מע\"ז ג\"ע וש\"ד\"", LongQuerySearchEngine.asQuote("בכל מתרפאין חוץ מע\"ז ג\"ע וש\"ד"))
        assertEquals("\"שור המועד\" \"להעיד אדם או שור\"", LongQuerySearchEngine.asQuote("\"שור המועד\" להעיד אדם או שור"))
        assertEquals("", LongQuerySearchEngine.asQuote("  "))
    }

    @Test
    fun `an empty query stays without a session`() {
        val engine =
            LongQuerySearchEngine(
                object : SearchEngine by FakeEngine(emptySet()) {
                    override fun openSession(
                        query: String,
                        near: Int,
                        bookFilter: Long?,
                        categoryFilter: Long?,
                        bookIds: Collection<Long>?,
                        lineIds: Collection<Long>?,
                        baseBookOnly: Boolean,
                    ): SearchSession? = null
                },
            )
        assertNull(engine.openSession("", 5))
    }
}
