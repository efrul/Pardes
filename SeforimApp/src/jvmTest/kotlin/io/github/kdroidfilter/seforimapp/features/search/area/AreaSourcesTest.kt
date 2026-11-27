package io.github.kdroidfilter.seforimapp.features.search.area

import io.github.kdroidfilter.seforimapp.framework.search.CommonSource
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sources of the area beside the results: a source as a result, to show it in the preview as a result is. */
class AreaSourcesTest {
    @Test
    fun `a source becomes a result of its own line, to show in the preview`() {
        val source = CommonSource(2045, 2, "שמות טז, כט", 4, "רְאוּ", bookTitle = "שמות", lineIndex = 612)
        assertEquals(SearchResult(2, "שמות", 2045, 612, "רְאוּ", 0.0), source.asResult())
    }
}
