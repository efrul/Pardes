package io.github.kdroidfilter.seforimapp.features.search.area

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchAreasTest {
    @Test
    fun `each tab keeps its pane open or closed`() {
        val areas = SearchAreas()
        assertFalse(areas.isShown("a"))
        areas.show("a", true)
        assertTrue(areas.isShown("a"))
        assertFalse(areas.isShown("b"))
        areas.show("a", false)
        assertFalse(areas.isShown("a"))
    }
}
