package io.github.kdroidfilter.seforimapp.features.search.semantic

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
import kotlin.test.assertTrue

/**
 * [IndependentSemanticSearch] over a vector index with a trap: a line repeated 40 times, the closest to the query. Its
 * vectors are each other's only neighbors in the graph, and a walk that comes down on one of them finds 33 lines.
 */
class DenseTrapTest {
    private val root = createTempDirectory().toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    /** Every query is (127, 0, 0): a line of vector (127, y, z) is the closer the smaller y and z are. */
    private object Query : EmbeddingProvider {
        override val dimension = 3

        override fun embed(text: String): ByteArray = byteArrayOf(127, 0, 0)

        override fun close() {}
    }

    // Lines 1 to 40, of book 7: one line repeated, the closest to the query
    private val repeated = (1L..REPEATED).map { Triple(it, 7, byteArrayOf(127, 1, 0)) }

    // Lines 1001 to 3000, of books 1 to 4 by turns: each its own vector, the lower its number the closer
    private val others =
        (0 until OTHERS).map { i ->
            Triple(1001L + i, i % 4 + 1, byteArrayOf(127, (3 + i / 50).toByte(), (i % 50 - 25).toByte()))
        }

    private fun index(): Path =
        root.toPath().resolve("vectors").also { dir ->
            FSDirectory.open(dir).use { directory ->
                IndexWriter(directory, IndexWriterConfig()).use { writer ->
                    for ((id, book, vector) in repeated + others) {
                        writer.addDocument(
                            Document().apply {
                                add(StoredField("line_id", id))
                                add(IntPoint("line_id", id.toInt()))
                                add(StoredField("book_id", book.toLong()))
                                add(IntPoint("book_id", book))
                                add(KnnByteVectorField("vec", vector, VectorSimilarityFunction.COSINE))
                            },
                        )
                    }
                    writer.forceMerge(1)
                }
            }
        }

    // The lines closest to the query, every vector scored: of [books] when given
    private fun closest(
        k: Int,
        books: Set<Int>? = null,
    ): List<Long> =
        (repeated + others)
            .filter { books == null || it.second in books }
            .sortedBy { (_, _, v) -> (v[1] * v[1] + v[2] * v[2]).toDouble() / (127 * 127 + v[1] * v[1] + v[2] * v[2]) }
            .take(k)
            .map { it.first }

    @Test
    fun `a walk that comes down on a repeated line comes back short, and says so`() {
        IndependentSemanticSearch(index(), Query).use { search ->
            var short = false
            val found = search.search("q", k = K, onShort = { short = true })
            assertTrue(short, "told of the short walk")
            assertTrue(found.size < K && found.all { it.lineId <= REPEATED }, "the repeated line alone: ${found.size} lines")
            // With nobody to tell and no scope, the short list is all there is
            assertEquals(found.size, search.search("q", k = K).size)
        }
    }

    @Test
    fun `started again where the words' hits are, the walk finds the closest lines`() {
        IndependentSemanticSearch(index(), Query).use { search ->
            val seeds = listOf(1003L, 1027L, 1051L, 1076L, 1101L, 1152L, 1203L, 1254L)
            val found = search.search("q", k = K, seeds = seeds).map { it.lineId }
            assertEquals(K, found.size)
            val exact = closest(K).toSet()
            // Not all of them: of the repeated line's 40 vectors the graph reaches 33
            val same = found.count { it in exact }
            assertTrue(same >= K * 3 / 4, "three in four of the closest at least: $same")
            assertTrue(found.any { it > REPEATED } && found.any { it <= REPEATED }, "the repeated line and the others")
        }
    }

    @Test
    fun `in a large scope with no seeds, every vector of the scope is scored`() {
        IndependentSemanticSearch(index(), Query).use { search ->
            // Books 7, 1 and 2: more than one in eight of the vectors, so the graph is walked first
            val found = search.search("q", k = K, bookIds = listOf(7L, 1L, 2L)).map { it.lineId }
            assertEquals(closest(K, setOf(7, 1, 2)).toSet(), found.toSet())
        }
    }

    private companion object {
        const val REPEATED = 40L
        const val OTHERS = 2000
        const val K = 100
    }
}
