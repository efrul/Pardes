// Android variant of Pardes desktop's SeforimApp/src/jvmTest/.../features/search/semantic/HfTokenizerParityTest.kt: the
// same cases and checks, on a device, so on Android's own regex (ICU) and normalizer, where the queries are tokenized.
// The fixtures are desktop's (app/build.gradle.kts copies them into the test's assets): read as assets, as the test APK
// has no file path for its resources (desktop finds them with Path.of, API 34 here).
package io.github.kdroidfilter.seforimapp.features.search.semantic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The open model's cleaner and tokenizer in Kotlin against the Python ones (the corpus is embedded in Python, the
 * queries in Kotlin): the cases are written by semantic-model/make_parity_fixtures.py with the tokenizers library.
 */
@RunWith(AndroidJUnit4::class)
class HfTokenizerParityTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    // HfTokenizer reads a file, as the app reads the pack's: the gzipped asset copied out as it is
    private val tokenizer: HfTokenizer =
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "parity-tokenizer.json.gz").let { file ->
            assets.open("semantic/tokenizer.json.gz").use { input -> file.outputStream().use { input.copyTo(it) } }
            HfTokenizer.fromFile(file.toPath()).also { file.delete() }
        }

    private val cases: List<JSONObject> =
        GZIPInputStream(assets.open("semantic/parity.jsonl.gz")).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map(::JSONObject).toList()
        }

    @Test
    fun cleanerMatchesCleanPy() {
        val wrong = cases.filter { OpenModelCleaner.clean(it.getString("text")) != it.getString("clean") }
        assertTrue(wrong.isEmpty(), "${wrong.size}/${cases.size} differ, first: ${wrong.firstOrNull()?.getString("text")}")
    }

    @Test
    fun queryIdsMatchTheTokenizersLibrary() = assertIds("query", QUERY_PREFIX, QUERY_MAX_TOKENS)

    @Test
    fun documentIdsMatchTheTokenizersLibrary() = assertIds("doc", "", DOCUMENT_MAX_TOKENS)

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
