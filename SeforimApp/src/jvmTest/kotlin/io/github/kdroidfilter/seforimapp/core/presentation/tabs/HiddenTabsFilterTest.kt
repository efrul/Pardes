package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What is typed over the hidden tabs' list narrows it (request 83 of stage 3). */
class HiddenTabsFilterTest {
    private val titles =
        listOf(
            "שולחן ערוך, אורח חיים - סימן רנג",
            "משנה ברורה - סימן רנג",
            "ברכות - דף ב.",
            "שו\"ע יורה דעה - סימן פז",
            "בְּרֵאשִׁית - פרק א",
            "Mishnah Berurah",
        )

    // The short names of the first two, as their narrow tabs show them
    private val short = mapOf(0 to "או\"ח", 1 to "משנ\"ב")

    private fun found(query: String) =
        matchingTabs(titles.indices.toList(), titles.mapIndexed { i, title -> listOfNotNull(title, short[i]) }, query)

    @Test
    fun `nothing typed leaves every tab`() {
        assertEquals(titles.indices.toList(), found(""))
        assertEquals(titles.indices.toList(), found("  "))
    }

    @Test
    fun `a part of the title finds its tabs, in their order`() {
        assertEquals(listOf(0, 1), found("רנג"))
        assertEquals(listOf(0), found("אורח"))
        assertEquals(listOf(2), found("דף ב"))
    }

    @Test
    fun `nikud and quote marks do not matter, in what is typed or in the title`() {
        assertEquals(listOf(3), found("שוע"))
        assertEquals(listOf(3), found("שו״ע"))
        assertEquals(listOf(4), found("בראשית"))
    }

    @Test
    fun `the short name a narrow tab shows finds it too`() {
        assertEquals(listOf(0), found("או\"ח"))
        assertEquals(listOf(1), found("משנב"))
    }

    @Test
    fun `letter case does not matter`() {
        assertEquals(listOf(5), found("mishnah"))
    }

    @Test
    fun `a title that holds nothing typed leaves no tab`() {
        assertEquals(emptyList(), found("זוהר"))
    }

    @Test
    fun `the keys' row comes into view by the least scroll`() {
        // Ten rows 30 px high in a view of 120 px: rows 0 to 3 in view at first
        assertNull(revealScroll(row = 2, rows = 10, rowHeight = 30f, scrolled = 0, viewport = 120))
        assertEquals(30, revealScroll(row = 4, rows = 10, rowHeight = 30f, scrolled = 0, viewport = 120))
        assertEquals(180, revealScroll(row = 9, rows = 10, rowHeight = 30f, scrolled = 0, viewport = 120))
        assertEquals(60, revealScroll(row = 2, rows = 10, rowHeight = 30f, scrolled = 150, viewport = 120))
        // The + row, under the column, is always in view
        assertNull(revealScroll(row = 10, rows = 10, rowHeight = 30f, scrolled = 0, viewport = 120))
    }

    @Test
    fun `a page is the rows in view but four, one at least`() {
        assertEquals(12, pageRows(viewport = 480, rowHeight = 30f))
        assertEquals(1, pageRows(viewport = 120, rowHeight = 30f))
        assertEquals(1, pageRows(viewport = 0, rowHeight = 0f))
    }
}
