package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import io.github.kdroidfilter.seforimlibrary.search.TextHighlights
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The passage preview's highlights stay with their line ([rememberPreviewHighlights]): when the next result's preview
 * draws its lines where the last one's were, none of them shows the ranges of the line drawn there before.
 */
@OptIn(ExperimentalTestApi::class)
class PreviewHighlightsUiTest {
    /** By meaning, a found line's first word; the query's words are each line's last word. */
    private class Engine(
        private val byMeaning: Boolean,
    ) : SearchEngine {
        override fun openSession(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchSession? = null

        override fun computeFacets(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchFacets? = null

        override fun searchBooksByTitlePrefix(
            query: String,
            limit: Int,
        ): List<Long> = emptyList()

        override fun buildSnippet(
            rawText: String,
            query: String,
            near: Int,
        ): String = rawText

        override fun highlightsByMeaning(query: String): Boolean = byMeaning

        override fun highlightRanges(
            text: String,
            query: String,
        ): List<IntRange> = listOf(text.lastIndexOf(' ') + 1 until text.length)

        override suspend fun highlights(
            texts: List<String>,
            query: String,
        ): List<TextHighlights> =
            texts.map { text ->
                val ranges = if (byMeaning) listOf(0 until text.indexOf(' ')) else highlightRanges(text, query)
                TextHighlights(ranges, byMeaning = byMeaning)
            }

        override fun close() {}
    }

    /** A preview's lines, as (text, found), in order. */
    private var lines by mutableStateOf(emptyList<Pair<String, Boolean>>())

    /** What each place of the preview highlights, as last drawn (from the UI thread). */
    private val drawn = ConcurrentHashMap<Int, List<IntRange>>()

    private fun ComposeUiTest.show(engine: SearchEngine) {
        setContent {
            lines.forEachIndexed { i, (text, found) ->
                val ranges = rememberPreviewHighlights(engine, text, "בראשית ברא", found, engine.highlightsByMeaning("בראשית ברא"))
                SideEffect { drawn[i] = ranges }
            }
        }
    }

    @Test
    fun `by meaning, the line drawn where the found line was shows none of its passage`(): Unit =
        runDesktopComposeUiTest(width = 300, height = 200) {
            lines = listOf("בראשית ברא אלהים" to true, "והארץ היתה תהו ובהו" to false)
            show(Engine(byMeaning = true))
            waitUntil(timeoutMillis = 5_000) { drawn[0] == listOf(0 until 6) }
            assertEquals(emptyList(), drawn[1])
            // The next result: the found line moves down, another line takes its place
            lines = listOf("והארץ היתה תהו ובהו" to false, "בראשית ברא אלהים" to true)
            waitUntil(timeoutMillis = 5_000) { drawn[1] == listOf(0 until 6) }
            assertEquals(emptyList(), drawn[0])
        }

    @Test
    fun `by words, a line drawn where another was highlights its own words`(): Unit =
        runDesktopComposeUiTest(width = 300, height = 200) {
            lines = listOf("ויאמר אלהים יהי אור" to false, "בראשית ברא אלהים" to true)
            show(Engine(byMeaning = false))
            waitForIdle()
            assertEquals(listOf(16 until 19), drawn[0])
            lines = listOf("וירא אלהים את האור כי טוב" to false, "בראשית ברא אלהים" to true)
            waitForIdle()
            assertEquals(listOf(22 until 25), drawn[0])
        }

    @Test
    fun `a line that is no longer the found one loses its passage`(): Unit =
        runDesktopComposeUiTest(width = 300, height = 200) {
            lines = listOf("בראשית ברא אלהים" to true)
            show(Engine(byMeaning = true))
            waitUntil(timeoutMillis = 5_000) { drawn[0] == listOf(0 until 6) }
            lines = listOf("בראשית ברא אלהים" to false)
            waitForIdle()
            assertEquals(emptyList(), drawn[0])
        }
}
