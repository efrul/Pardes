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
