package io.github.kdroidfilter.seforimapp.features.search.semantic

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Android only: the semantic pack installed beside the device's library (pack 2, `semantic-2-v3-20261012193827`) answers
 * the reference queries as desktop does (`IndependentSemanticSearch.search(query, k = 10)` on the same pack, measured on
 * desktop on 2026-10-28), and how long its steps take here, with the memory they take (logged as PardesSemanticTest).
 * Skipped when the device has no such pack. A model run on another CPU may reorder near ties, so each reference line is
 * asked for among the first ten, not at its place.
 */
@RunWith(AndroidJUnit4::class)
class SemanticReferenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun referenceQueriesFindDesktopsLines() {
        val library = LibraryStore(context).libraryDir
        val modelDir = File(library, IndependentSemanticSearch.OPEN_MODEL_DIR_NAME)
        val vectorDir = File(library, VectorIndexBuilder.DIR_NAME)
        assumeTrue("no semantic pack in $library", modelDir.isDirectory && vectorDir.isDirectory)
        val info = VectorIndexBuilder.readInfo(vectorDir.toPath())
        assumeTrue("not pack 2's model: ${info?.model}", info?.model == MODEL)
        memory("before")

        val spec = checkNotNull(OpenModelSpec.read(modelDir.toPath()))
        val (tokenizer, tokenizerMs) = timed { HfTokenizer.fromFile(spec.tokenizer) }
        log("tokenizer.json read in $tokenizerMs ms (${tokenizer.padId} pads)")
        val (provider, modelMs) = timed { OnnxEmbeddingProvider.open(spec) }
        log("model loaded in $modelMs ms (tokenizer read again inside)")
        memory("model loaded")
        val (search, indexMs) = timed { IndependentSemanticSearch(vectorDir.toPath(), provider, spec.hybridDenseWeight) }
        log("vector index opened in $indexMs ms (${info?.lines} lines)")
        memory("index opened")

        val misses = ArrayList<String>()
        for ((query, expected) in REFERENCE) {
            val (_, firstEmbedMs) = timed { provider.embed(query) }
            val embedMs = (1..EMBED_RUNS).map { timed { provider.embed(query) }.second }
            val (hits, searchMs) = timed { search.search(query, k = 10) }
            val (wide, wideMs) = timed { search.search(query, k = 150) }
            val (again, againMs) = timed { search.search(query, k = 10) }
            val top = hits.map { it.lineId }
            log(
                "\"$query\": embedding ${firstEmbedMs} ms first, ${embedMs.average().toInt()} ms then ($embedMs); " +
                    "search k=10 $searchMs ms (again $againMs ms), k=150 $wideMs ms (${wide.size} hits); " +
                    "top 10 ${top.joinToString()}; desktop's top five ${expected.joinToString()}",
            )
            if (again.map { it.lineId } != top) misses += "\"$query\" answered differently twice"
            expected.filterNot { it in top }.takeIf { it.isNotEmpty() }?.let { misses += "\"$query\": $it not in $top" }
        }
        memory("after the searches")
        search.close()
        assertTrue(misses.isEmpty(), misses.joinToString("\n"))
    }

    private fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = SystemClock.elapsedRealtimeNanos()
        val value = block()
        return value to (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
    }

    private fun memory(stage: String) {
        val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val rss = File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS") }?.substringAfter(':')?.trim()
        val heap = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1024 }
        log(
            "memory $stage: PSS ${info.totalPss} kB (native ${info.nativePss}, java ${info.dalvikPss}, other ${info.otherPss}), " +
                "RSS $rss, Java heap used $heap kB",
        )
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        println("$TAG: $message")
    }

    private companion object {
        const val TAG = "PardesSemanticTest"
        const val MODEL = "student@695cbe2/pardes-clean-1"
        const val EMBED_RUNS = 5

        /** Desktop's top five of IndependentSemanticSearch.search(query, k = 10) on pack 2 (JVM, x86_64). */
        val REFERENCE =
            listOf(
                "ואהבת לרעך כמוך" to listOf(5849089L, 2763778L, 1599474L, 6533217L, 1578222L),
                "מצוות כיבוד אב ואם" to listOf(1970888L, 6604044L, 3940798L, 3752715L, 3724069L),
                "הדלקת נרות שבת" to listOf(1596047L, 1587810L, 1678240L, 5969023L, 3774773L),
            )
    }
}
