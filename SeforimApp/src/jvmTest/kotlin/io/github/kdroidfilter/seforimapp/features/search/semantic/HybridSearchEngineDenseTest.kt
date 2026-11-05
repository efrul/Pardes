package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.runBlocking
import org.apache.lucene.document.Document
import org.apache.lucene.document.IntPoint
import org.apache.lucene.document.KnnByteVectorField
import org.apache.lucene.document.StoredField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [IndependentSemanticSearch] over a vector index of its own, and [HybridSearchEngine]'s count with it. */
class HybridSearchEngineDenseTest {
    private val root = createTempDirectory().toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    /** Every query is (127, 0): a line of vector (127, y) is the closer the smaller y is. */
    private object Query : EmbeddingProvider {
        override val dimension = 2

        override fun embed(text: String): ByteArray = byteArrayOf(127, 0)

        override fun close() {}
    }

    private class Line(
        val id: Long,
        val book: Int,
        val y: Int,
        val base: Boolean = false,
    )

    /** A vector index of [lines], as VectorIndexBuilder writes one: a base book's line in `vec_base`, any other in `vec`. */
    private fun index(lines: List<Line>): Path =
        root.toPath().resolve("vectors-${lines.size}").also { dir ->
            FSDirectory.open(dir).use { directory ->
                IndexWriter(directory, IndexWriterConfig()).use { writer ->
                    for (line in lines) {
                        writer.addDocument(
                            Document().apply {
                                add(StoredField("line_id", line.id))
                                add(IntPoint("line_id", line.id.toInt()))
                                add(StoredField("book_id", line.book.toLong()))
                                add(IntPoint("book_id", line.book))
                                val field = if (line.base) "vec_base" else "vec"
                                add(KnnByteVectorField(field, byteArrayOf(127, line.y.toByte()), VectorSimilarityFunction.COSINE))
                            },
                        )
                    }
                    writer.forceMerge(1)
                }
            }
        }

    @Test
    fun `in a few books, the closest lines of those books, though a heading repeated 40 times is in them too`() {
        // Lines 1 to 60 in books 1, 2 and 3 by turns (y even); a far heading 40 times in book 2; base lines in book 4 (y odd);
        // and far lines in book 9, so that books 1 and 2 hold few of the vectors (searched exactly)
        val lines =
            (1L..60L).map { Line(it, (it % 3).toInt() + 1, 2 * it.toInt()) } +
                (1001L..1040L).map { Line(it, 2, 127) } +
                (2001L..2010L).map { Line(it, 4, 2 * (it - 2000).toInt() - 1, base = true) } +
                (5001L..6000L).map { Line(it, 9, 100 + (it % 27).toInt()) }
        IndependentSemanticSearch(index(lines), Query).use { search ->
            assertEquals(listOf(1L, 4L, 7L, 10L, 13L), search.search("q", k = 5, bookIds = listOf(2L)).map { it.lineId })
            // Both fields, the closest first whichever field holds it
            assertEquals(listOf(2001L, 1L, 2002L, 2003L, 3L), search.search("q", k = 5, bookIds = listOf(1L, 2L, 4L)).map { it.lineId })
            val base = search.search("q", k = 3, baseOnly = true, bookIds = listOf(2L, 4L))
            assertEquals(listOf(2001L, 2002L, 2003L), base.map { it.lineId })
            // Fewer lines in the books than asked for: all of them
            assertEquals(20 + 40, search.search("q", k = 150, bookIds = listOf(2L)).size)
        }
    }

    /** Finds lines 1 to [count], in that order, a page at a time. */
    private class Numbered(
        private val count: Long,
    ) : SearchEngine {
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

        override fun computeFacets(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchFacets = SearchFacets(count, emptyMap(), emptyMap())

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

    /** Every line of [session], page after page, and the totals its pages gave. */
    private suspend fun pageThrough(session: SearchSession): Pair<List<Long>, Set<Long>> {
        val ids = ArrayList<Long>()
        val totals = HashSet<Long>()
        while (true) {
            val page = session.nextPage(25, snippets = false) ?: break
            ids += page.hits.map { it.lineId }
            totals += page.totalHits
            if (page.isLastPage) break
        }
        return ids to totals
    }

    @Test
    fun `with lines by meaning the list is the fused one, as Zayit's, and without any the words' whole list`(): Unit =
        runBlocking {
            // The words find lines 1 to 400; by meaning, in book 1, 390 to 410 (11 the words find past their first 150) and
            // 1000 to 1010
            val dense = (390L..410L).map { Line(it, 1, (it - 389).toInt()) } + (1000L..1010L).map { Line(it, 1, (it - 970).toInt()) }
            val dir = index(dense)
            val engine =
                HybridSearchEngine(
                    Numbered(400),
                    dir,
                    { id, _ -> LineHit(1, "", id, 0, "", 0f, "") },
                    semanticConfigured = true,
                    library = null,
                    openSemantic = { IndependentSemanticSearch(dir, Query, hybridWeight = 3.0) },
                )
            // The words' first 150 and the 32 by meaning, each once; the total is the list's length
            val (fused, fusedTotals) = pageThrough(engine.openSession("שבת", 5)!!)
            assertEquals(150 + 32, fused.size)
            assertEquals(fused.size, fused.toSet().size)
            assertEquals((1L..150L).toSet() + (390L..410L) + (1000L..1010L), fused.toSet())
            assertEquals(setOf(182L), fusedTotals)
            // In book 2, without a line by meaning: all 400 lines the words find, each once
            val (words, wordsTotals) = pageThrough(engine.openSession("שבת", 5, bookIds = listOf(2L))!!)
            assertEquals((1L..400L).toList(), words)
            assertEquals(setOf(400L), wordsTotals)
        }
}
