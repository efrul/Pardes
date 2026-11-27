package io.github.kdroidfilter.seforimapp.features.search.area

import io.github.kdroidfilter.seforimapp.framework.search.CommonSource
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The sources of the area beside the results: the list narrowed to a source's results, and a source as a result. */
class AreaSourcesTest {
    private fun result(line: Long) =
        SearchResult(bookId = line / 10, bookTitle = "ספר", lineId = line, lineIndex = 0, snippet = "", rank = 0.0)

    private val results = persistentListOf(result(11), result(21), result(31), result(41))

    @Test
    fun `the list narrows to the results that link to the source, in their order`() {
        val source = CommonSource(500, 50, "שמות טז, כט", 3, from = listOf(41, 11, 31))
        assertEquals(listOf(11L, 31L, 41L), linkedResults(results, source).map { it.lineId })
    }

    @Test
    fun `with no source, the list stays whole`() {
        assertSame(results, linkedResults(results, null))
    }

    @Test
    fun `a source becomes a result of its own line, to show in the preview`() {
        val source = CommonSource(2045, 2, "שמות טז, כט", 4, "רְאוּ", bookTitle = "שמות", lineIndex = 612)
        assertEquals(SearchResult(2, "שמות", 2045, 612, "רְאוּ", 0.0), source.asResult())
    }
}
