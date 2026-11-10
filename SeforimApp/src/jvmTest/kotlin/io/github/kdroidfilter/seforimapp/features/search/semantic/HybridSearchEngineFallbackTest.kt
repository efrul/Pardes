package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [HybridSearchEngine] when the dense side can't load: the search goes on with its words alone. */
class HybridSearchEngineFallbackTest {
    /** Finds lines 1, 2 and 3, in that order. */
    private class Words : SearchEngine {
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
                ): SearchPage = SearchPage((1L..3L).map { LineHit(1, "", it, 0, "", 1f, "") }, totalHits = 3, isLastPage = true)

                override fun close() {}
            }

        override fun computeFacets(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchFacets = SearchFacets(totalHits = 3, categoryCounts = emptyMap(), bookCounts = emptyMap())

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

    /** Finds lines 1 to [count], in that order, a page at a time. */
    private class ManyWords(
        private val count: Long,
    ) : SearchEngine by Words() {
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
                private var next = 1L

                override suspend fun nextPage(
                    limit: Int,
                    snippets: Boolean,
                ): SearchPage? {
                    if (next > count) return null
                    val ids = next..minOf(count, next + limit - 1)
                    next = ids.last + 1
                    return SearchPage(ids.map { LineHit(1, "", it, 0, "", 1f, "") }, totalHits = count, isLastPage = next > count)
                }

                override fun close() {}
            }
    }

    @Test
    fun `past the fused candidates the lexical results go on, each once, and the total is theirs`(): Unit =
        runBlocking {
            // No model: the fused list is the lexical side's first candidates, the rest come after them
            val engine =
                HybridSearchEngine(ManyWords(400), Path.of("unused"), { id, _ -> LineHit(1, "", id, 0, "", 0f, "") }, true) {
                    throw UnsatisfiedLinkError("no onnxruntime in java.library.path")
                }
            val session = engine.openSession("שבת קודש", 5)!!
            val ids = ArrayList<Long>()
            val totals = HashSet<Long>()
            while (true) {
                val page = session.nextPage(25, snippets = false) ?: break
                ids += page.hits.map { it.lineId }
                totals += page.totalHits
                if (page.isLastPage) break
            }
            assertEquals((1L..400L).toList(), ids)
            assertEquals(setOf(400L), totals)
        }

    @Test
    fun `a view shows the lines of some books of the same list, with no new search`(): Unit =
        runBlocking {
            var searches = 0
            // Lines 1 to 6: the odd ones of book 1, the even ones of book 2
            val words =
                object : SearchEngine by Words() {
                    override fun openSession(
                        query: String,
                        near: Int,
                        bookFilter: Long?,
                        categoryFilter: Long?,
                        bookIds: Collection<Long>?,
                        lineIds: Collection<Long>?,
                        baseBookOnly: Boolean,
                    ): SearchSession {
                        searches++
                        return object : SearchSession {
                            override suspend fun nextPage(
                                limit: Int,
                                snippets: Boolean,
                            ): SearchPage {
                                val hits = (1L..6L).map { LineHit(2 - it % 2, "", it, 0, "", 1f, "") }
                                return SearchPage(hits, totalHits = 6, isLastPage = true)
                            }

                            override fun close() {}
                        }
                    }
                }
            val failing: (Path) -> IndependentSemanticSearch? = { throw UnsatisfiedLinkError("no onnxruntime in java.library.path") }
            val resolve: suspend (Long, String) -> LineHit? = { id, _ -> LineHit(1, "", id, 0, "", 0f, "") }
            val engine = HybridSearchEngine(words, Path.of("unused"), resolve, true, openSemantic = failing)
            val session = engine.openSession("שבת קודש", 5)!!
            val views = session as SessionViews
            // No view of a list that is not made yet
            assertNull(views.view(null))
            assertEquals(6, session.nextPage(4, snippets = false)!!.totalHits)
            val searched = searches

            suspend fun lines(view: SearchSession?): List<Long> {
                val ids = ArrayList<Long>()
                while (true) {
                    val page = view?.nextPage(2, snippets = false) ?: break
                    ids += page.hits.map { it.lineId }
                    assertEquals(page.isLastPage, ids.size.toLong() == page.totalHits)
                    if (page.isLastPage) break
                }
                return ids
            }
            assertEquals(listOf(2L, 4L, 6L), lines(views.view(setOf(2L))))
            assertEquals(listOf(1L, 3L, 5L), lines(views.view(setOf(1L, 9L))))
            // All of it again, from its first, whatever the session itself has shown
            assertEquals((1L..6L).toList(), lines(views.view(null)))
            assertTrue(lines(views.view(setOf(9L))).isEmpty())
            assertEquals(searched, searches)

            // A list that goes on past what it holds is searched instead
            val long = HybridSearchEngine(ManyWords(400), Path.of("unused"), resolve, true, openSemantic = failing)
            val all = long.openSession("שבת קודש", 5)!!
            all.nextPage(25, snippets = false)
            assertNull((all as SessionViews).view(setOf(1L)))
        }

    @Test
    fun `a model that fails to load, with an Error too, leaves the search to its words`(): Unit =
        runBlocking {
            var loads = 0
            val engine =
                HybridSearchEngine(Words(), Path.of("unused"), { id, _ -> LineHit(1, "", id, 0, "", 0f, "") }, true) {
                    loads++
                    // As ONNX Runtime throws where its native library can't load
                    throw UnsatisfiedLinkError("no onnxruntime in java.library.path")
                }
            // Not known yet: by meaning until the model is tried
            assertTrue(engine.highlightsByMeaning("שבת קודש"))
            val hits = engine.openSession("שבת קודש", 5)!!.nextPage(10, snippets = false)!!.hits
            assertEquals(listOf(1L, 2L, 3L), hits.map { it.lineId })
            assertFalse(engine.highlightsByMeaning("שבת קודש"))
            assertFalse(engine.denseReady())
            assertNull(engine.passagesByMeaning("שבת קודש", listOf("משפט ראשון כאן. ומשפט שני כאן.")))
            // Tried once: the next searches don't load again
            engine.openSession("שבת", 5)!!.nextPage(10, snippets = false)
            assertEquals(1, loads)
        }
}
