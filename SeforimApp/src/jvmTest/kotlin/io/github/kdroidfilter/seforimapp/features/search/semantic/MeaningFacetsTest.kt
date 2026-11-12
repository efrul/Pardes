package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.core.models.Category
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
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The lines a hybrid search found by meaning alone, counted into its facets ([MeaningFacets], [plusBooks]): the category
 * tabs then cover every line its list shows, not only the lines its words found; and the library's other top categories
 * offered after them ([otherTabCategories]).
 */
class MeaningFacetsTest {
    private val root = createTempDirectory().toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a book's lines count for it, its categories and the total, and an unreadable book not at all`(): Unit =
        runBlocking {
            // Book 10 in category 5 under 1, book 20 in 6 under 2, book 30 in 2; book 99 can't be read
            val paths = mapOf(10L to listOf(1L, 5L), 20L to listOf(2L, 6L), 30L to listOf(2L))
            val words = SearchFacets(totalHits = 1, categoryCounts = mapOf(1L to 1, 5L to 1), bookCounts = mapOf(10L to 1))

            val facets = words.plusBooks(mapOf(10L to 1, 20L to 2, 30L to 1, 99L to 4)) { paths[it] }

            assertEquals(mapOf(10L to 2, 20L to 2, 30L to 1), facets.bookCounts)
            assertEquals(mapOf(1L to 2, 5L to 2, 2L to 3, 6L to 2), facets.categoryCounts)
            assertEquals(5L, facets.totalHits)
            assertSame(words, words.plusBooks(emptyMap()) { paths[it] })
        }

    @Test
    fun `the library's other top categories come in its order, in the base books only those holding some`(): Unit =
        runBlocking {
            val roots =
                listOf(
                    Category(id = 3, title = "חסידות", order = 9),
                    Category(id = 1, title = "תנ״ך", order = 1),
                    Category(id = 2, title = "הלכה", order = 5),
                )
            val holdingBaseBooks = setOf(1L, 2L)

            assertEquals(listOf(1L, 2L, 3L), otherTabCategories(roots, baseBooksOnly = false) { it in holdingBaseBooks }.map { it.id })
            assertEquals(listOf(1L, 2L), otherTabCategories(roots, baseBooksOnly = true) { it in holdingBaseBooks }.map { it.id })
        }

    @Test
    fun `a hybrid session counts the books of the lines only its meaning search found`(): Unit =
        runBlocking {
            // The words find line 1 (book 10); the meaning search finds it too, and lines 2 (book 10), 3 and 4 (book 20)
            hybrid(words = mapOf(1L to 10L), meaning = mapOf(1L to 10L, 2L to 10L, 3L to 20L, 4L to 20L)).use { engine ->
                val session = engine.openSession(QUERY, 5)!!
                // Nothing before the first page, which runs the search
                assertEquals(emptyMap(), (session as MeaningFacets).meaningBookCounts())
                session.nextPage(25, snippets = false)
                assertEquals(mapOf(10L to 1, 20L to 2), session.meaningBookCounts())
                session.close()
            }
        }

    @Test
    fun `a line the words find past the fused candidates is theirs, not the meaning search's own`(): Unit =
        runBlocking {
            // The words find lines 1 to 200 (book 10), more than the fused list takes of them; the meaning search finds
            // line 180, one of theirs past it, and line 500 (book 20)
            hybrid(words = (1L..200L).associateWith { 10L }, meaning = mapOf(180L to 10L, 500L to 20L)).use { engine ->
                val session = engine.openSession(QUERY, 5)!!
                session.nextPage(25, snippets = false)
                assertEquals(mapOf(20L to 1), (session as MeaningFacets).meaningBookCounts())
                session.close()
            }
        }

    @Test
    fun `a hybrid session lists the lines only its meaning search found, in its order`(): Unit =
        runBlocking {
            hybrid(words = mapOf(1L to 10L), meaning = mapOf(1L to 10L, 2L to 10L, 3L to 20L)).use { engine ->
                val session = engine.openSession(QUERY, 5)!!
                session.nextPage(25, snippets = false)
                // All alike in meaning here: none stands out of the others
                val listed = (session as MeaningFacets).meaningLines().sortedBy { it.lineId }
                assertEquals(listOf(MeaningLine(2L, 0.0), MeaningLine(3L, 0.0)), listed)
                session.close()
            }
        }

    @Test
    fun `a line stands out of the dense side's candidates by its distance above their mean, in their standard deviations`() {
        val standings = HybridSearchEngine.standingOf(listOf(DenseHit(1, 0.9f), DenseHit(2, 0.8f), DenseHit(3, 0.7f), DenseHit(1, 0.6f)))

        // 0.9, 0.8, 0.7 and 0.6: their mean is 0.75 and their spread 0.1118; a line repeated keeps its first
        assertEquals(listOf(1L, 2L, 3L), standings.keys.toList())
        assertEquals(1.342, standings.getValue(1), 0.001)
        assertEquals(0.447, standings.getValue(2), 0.001)
        assertEquals(-0.447, standings.getValue(3), 0.001)
        assertEquals(mapOf(5L to 0.0, 6L to 0.0), HybridSearchEngine.standingOf(listOf(DenseHit(5, 0.8f), DenseHit(6, 0.8f))))
        assertEquals(emptyMap(), HybridSearchEngine.standingOf(emptyList()))
    }

    /** A hybrid engine on [words], with a meaning search that finds [meaning] (both line id to book id), all alike. */
    private fun hybrid(
        words: Map<Long, Long>,
        meaning: Map<Long, Long>,
    ): HybridSearchEngine {
        val index = root.toPath().resolve("pardes.vec.lucene")
        NIOFSDirectory(index).use { directory ->
            IndexWriter(directory, IndexWriterConfig()).use { writer ->
                for ((line, book) in meaning) {
                    // As VectorIndexBuilder writes them
                    writer.addDocument(
                        Document().apply {
                            add(StoredField("line_id", line))
                            add(IntPoint("line_id", line.toInt()))
                            add(StoredField("book_id", book))
                            add(IntPoint("book_id", book.toInt()))
                            add(KnnByteVectorField("vec", VECTOR, VectorSimilarityFunction.COSINE))
                        },
                    )
                }
            }
        }
        val model =
            object : EmbeddingProvider {
                override val dimension = VECTOR.size

                override fun embed(text: String): ByteArray = VECTOR

                override fun close() {}
            }
        val bookOf = words + meaning
        return HybridSearchEngine(Words(words), Path.of("unused"), { id, _ -> LineHit(bookOf.getValue(id), "", id, 0, "", 0f, "") }, true) {
            IndependentSemanticSearch(index, model)
        }
    }

    /** Finds [lines] (line id to book id) in line order, among the lines and books asked for, a page at a time. */
    private class Words(
        private val lines: Map<Long, Long>,
    ) : SearchEngine {
        override fun openSession(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchSession {
            val found =
                lines.entries
                    .filter { (line, book) -> (lineIds == null || line in lineIds) && (bookIds == null || book in bookIds) }
                    .sortedBy { it.key }
                    .map { (line, book) -> LineHit(book, "", line, 0, "", 1f, "") }
            return object : SearchSession {
                private var next = 0

                override suspend fun nextPage(
                    limit: Int,
                    snippets: Boolean,
                ): SearchPage? {
                    if (next >= found.size) return null
                    val page = found.subList(next, minOf(found.size, next + limit))
                    next += page.size
                    return SearchPage(page, totalHits = found.size.toLong(), isLastPage = next >= found.size)
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
        ): SearchFacets = SearchFacets(totalHits = lines.size.toLong(), categoryCounts = emptyMap(), bookCounts = emptyMap())

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

    private companion object {
        const val QUERY = "שיר השירים"
        val VECTOR = byteArrayOf(10, 20, 30, 40)
    }
}
