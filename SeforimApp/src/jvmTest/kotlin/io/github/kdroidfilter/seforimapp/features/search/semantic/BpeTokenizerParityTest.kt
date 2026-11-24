package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reranker's reading of a query and a line ([RerankText]) and its tokenizer ([BpeTokenizer]) in Kotlin against the
 * Python ones the reranker was measured with: the cases are written by pardes-semantic/reranker/make_fixtures.py with
 * the tokenizers library.
 */
class BpeTokenizerParityTest {
    private val dir: Path = Path.of(javaClass.getResource("/semantic/reranker-parity.jsonl.gz")!!.toURI()).parent
    private val tokenizer = BpeTokenizer.fromFile(dir.resolve("reranker-tokenizer.json.gz"))
    private val cases: List<JSONObject> =
        GZIPInputStream(Files.newInputStream(dir.resolve("reranker-parity.jsonl.gz"))).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map(::JSONObject).toList()
        }

    @Test
    fun `a query is read as query_text reads it`() {
        val wrong = cases.filter { RerankText.query(it.getString("query")) != it.getString("queryText") }
        assertTrue(wrong.isEmpty(), "${wrong.size}/${cases.size} differ, first: ${wrong.firstOrNull()?.getString("query")}")
    }

    @Test
    fun `a line is read as line_text reads it`() {
        val wrong = cases.filter { RerankText.line(it.getString("line")) != it.getString("lineText") }
        assertTrue(wrong.isEmpty(), "${wrong.size}/${cases.size} differ, first: ${wrong.firstOrNull()?.getString("line")?.take(120)}")
    }

    @Test
    fun `a pair's ids match the tokenizers library`() {
        assertTrue(cases.size > 50, "too few cases: ${cases.size}")
        var first: String? = null
        var wrong = 0
        for (case in cases) {
            val expected = case.getJSONArray("ids").toLongs()
            val line = case.getString("lineText")
            val actual = tokenizer.encodePair(case.getString("queryText"), line, MAX_TOKENS, QUERY_MAX_TOKENS).toList()
            if (expected != actual) {
                wrong++
                if (first == null) first = "${line.take(80)}\n expected $expected\n actual   $actual"
            }
        }
        assertEquals(0, wrong, "$wrong/${cases.size} differ, first: $first")
    }

    private fun JSONArray.toLongs(): List<Long> = (0 until length()).map { getLong(it) }

    private companion object {
        // The values of pardes-semantic/reranker/build_reranker.py, as in the reranker's pardes-reranker.json
        const val MAX_TOKENS = 256
        const val QUERY_MAX_TOKENS = 64
    }
}
