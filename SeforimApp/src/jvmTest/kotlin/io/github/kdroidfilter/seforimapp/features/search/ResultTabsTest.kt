package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultTabsTest {
    @Test
    fun `the tabs picked are where the text is searched again`() {
        // The results' tabs, then the library's other top categories: one may be in both
        val tabs = listOf(30L, 10L, 20L, 10L, 30L, 40L)
        assertNull(scopeOfTabs(tabs, emptySet()))
        assertNull(scopeOfTabs(tabs, setOf(99L)))
        assertEquals(SearchScope.Category(10), scopeOfTabs(tabs, setOf(10L)))
        // A tab's subcategories are picked with it: they are no tabs
        assertEquals(SearchScope.Category(10), scopeOfTabs(tabs, setOf(10L, 11L, 12L)))
        assertEquals(SearchScope.Multi(emptyList(), listOf(30L, 10L)), scopeOfTabs(tabs, setOf(10L, 30L)))
    }
}
