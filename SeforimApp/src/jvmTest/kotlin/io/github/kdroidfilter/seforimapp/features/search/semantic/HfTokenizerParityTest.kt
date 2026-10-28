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
 * The open model's cleaner and tokenizer in Kotlin against the Python ones (the corpus is embedded in Python, the
 * queries in Kotlin): the cases are written by semantic-model/make_parity_fixtures.py with the tokenizers library.
 */
class HfTokenizerParityTest {
    private val dir: Path = Path.of(javaClass.getResource("/semantic/parity.jsonl.gz")!!.toURI()).parent
    private val tokenizer = HfTokenizer.fromFile(dir.resolve("tokenizer.json.gz"))
    private val cases: List<JSONObject> =
        GZIPInputStream(Files.newInputStream(dir.resolve("parity.jsonl.gz"))).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map(::JSONObject).toList()
        }

    @Test
    fun `cleaner matches clean py`() {
        val wrong = cases.filter { OpenModelCleaner.clean(it.getString("text")) != it.getString("clean") }
        assertTrue(wrong.isEmpty(), "${wrong.size}/${cases.size} differ, first: ${wrong.firstOrNull()?.getString("text")}")
    }

    @Test
    fun `query ids match the tokenizers library`() = assertIds("query", QUERY_PREFIX, QUERY_MAX_TOKENS)

    @Test
    fun `document ids match the tokenizers library`() = assertIds("doc", "", DOCUMENT_MAX_TOKENS)

    private fun assertIds(
        key: String,
        prefix: String,
        maxTokens: Int,
    ) {
        assertTrue(cases.size > 50, "too few cases: ${cases.size}")
        var first: String? = null
        var wrong = 0
        for (case in cases) {
            val expected = case.getJSONArray(key).toLongs()
            val actual = tokenizer.encode(prefix + case.getString("clean"), maxTokens).toList()
            if (expected != actual) {
                wrong++
                if (first == null) first = "${case.getString("clean").take(80)}\n expected $expected\n actual   $actual"
            }
        }
        assertEquals(0, wrong, "$wrong/${cases.size} differ, first: $first")
    }

    private fun JSONArray.toLongs(): List<Long> = (0 until length()).map { getLong(it) }

    private companion object {
        // The values of semantic-model/pardes_semantic/model.py, as in the model's pardes-model.json
        const val QUERY_PREFIX = "query: "
        const val QUERY_MAX_TOKENS = 128
        const val DOCUMENT_MAX_TOKENS = 512
    }
}
