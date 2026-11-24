package io.github.kdroidfilter.seforimapp.features.search.semantic

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The reranker's order of a page ([Reranker.order]), its manifest ([RerankerSpec]) and a library without one. */
class RerankerTest {
    private val page = listOf(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L)

    @Test
    fun `the first lines take the reranker's order, the rest stay`() {
        val scores = listOf(-3f, 2f, -1f, 4f, 0f, -5f)
        assertEquals(listOf(40L, 20L, 50L, 30L, 10L, 60L, 70L, 80L), Reranker.order(page, scores, margin = 1.5f))
    }

    @Test
    fun `the page's first line stays first unless another passes it by the margin`() {
        val close = listOf(1f, 2.4f, -1f, 2f, 0f, -5f)
        assertEquals(listOf(10L, 20L, 40L, 50L, 30L, 60L, 70L, 80L), Reranker.order(page, close, margin = 1.5f))
        val passed = listOf(1f, 2.5f, -1f, 2f, 0f, -5f)
        assertEquals(listOf(20L, 40L, 10L, 50L, 30L, 60L, 70L, 80L), Reranker.order(page, passed, margin = 1.5f))
    }

    @Test
    fun `a line the reranker could not read keeps its place after those it read`() {
        val scores = listOf(null, 2f, null, 4f, 0f, -5f)
        assertEquals(listOf(40L, 20L, 50L, 60L, 10L, 30L, 70L, 80L), Reranker.order(page, scores, margin = 1.5f))
        assertEquals(page, Reranker.order(page, List(6) { null }, margin = 1.5f))
    }

    @Test
    fun `a page shorter than the reranker reads is ordered whole`() {
        assertEquals(listOf(20L, 10L), Reranker.order(listOf(10L, 20L), listOf(0f, 3f), margin = 1.5f))
    }

    @Test
    fun `a folder without a manifest is no reranker, a manifest without its files is refused`() {
        val dir = Files.createTempDirectory("pardes-reranker")
        try {
            assertNull(RerankerSpec.read(dir))
            Files.writeString(
                dir.resolve(RerankerSpec.FILE_NAME),
                """{"format":1,"id":"reranker-test","onnx":"m.onnx","tokenizer":"tokenizer.json","lines":6,"minWords":3,
                    |"maxTokens":256,"queryMaxTokens":64,"margin":1.5}
                """.trimMargin(),
            )
            assertFailsWith<IllegalArgumentException> { RerankerSpec.read(dir) }
            Files.writeString(dir.resolve("m.onnx"), "")
            Files.writeString(dir.resolve("tokenizer.json"), "{}")
            val spec = RerankerSpec.read(dir)!!
            assertEquals(6, spec.lines)
            assertEquals(1.5f, spec.margin)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a library without a reranker keeps the page as it is`() =
        runBlocking {
            val dir = Files.createTempDirectory("pardes-library")
            try {
                val reranker = PageReranker(dir.resolve("seforim.db.lucene")) { Reranker.open(it) }
                assertNull(reranker.ready())
                assertEquals(page, reranker.reordered("מה דין מוקצה בשבת", page) { "שורה" })
            } finally {
                dir.toFile().deleteRecursively()
            }
        }

    @Test
    fun `the settings' check finds no reranker, or says why one does not load`() {
        val library = Files.createTempDirectory("pardes-library").toFile()
        try {
            assertNull(Reranker.check(library))
            val dir = File(library, Reranker.DIR_NAME).apply { mkdirs() }
            File(dir, RerankerSpec.FILE_NAME).writeText("""{"format":2}""")
            assertTrue(Reranker.check(library)!!.isFailure)
        } finally {
            library.deleteRecursively()
        }
    }

    @Test
    fun `a query's words are counted as the reranker reads it`() {
        assertEquals(3, RerankText.words("  מה   דין־מוקצה "))
        assertEquals(1, RerankText.words("<b>שַׁבָּת</b>"))
    }
}
