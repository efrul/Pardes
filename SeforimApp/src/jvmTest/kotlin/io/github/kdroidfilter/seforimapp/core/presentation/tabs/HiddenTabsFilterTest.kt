package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
