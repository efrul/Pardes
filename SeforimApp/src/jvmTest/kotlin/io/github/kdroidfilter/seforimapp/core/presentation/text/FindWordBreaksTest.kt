package io.github.kdroidfilter.seforimapp.core.presentation.text

import kotlin.test.Test
import kotlin.test.assertEquals

// Pardes (request 55 of docs/DESKTOP_STAGE3_PLAN.md): a maqaf or a paseq between two words of the text is the query's
// space, as Isaiah writes כֹּה־אָמַר and כֹּה&thinsp;׀ אָמַר beside כֹּה אָמַר.
class FindWordBreaksTest {
    @Test
    fun `a maqaf between two words is found by a space`() {
        val text = "(כד) לָכֵן כֹּה־אָמַר אֲדֹנָי יְהוִה"
        val found = findAllMatchesOriginal(text, "כה אמר")
        assertEquals(1, found.size)
        // Highlighted on its own letters, from the kaf to the resh
        assertEquals(text.indexOf("כֹּה"), found[0].first)
        assertEquals(text.indexOf("ר אֲדֹנָי"), found[0].last)
    }

    @Test
    fun `a paseq with its thin space is found by a space`() {
        val text = "(יד) כֹּ֣ה\u2009׀ אָמַ֣ר יְהוָ֗ה יְגִ֨יעַ מִצְרַ֥יִם"
        val found = findAllMatchesOriginal(text, "כה אמר")
        assertEquals(1, found.size)
        assertEquals(text.indexOf('כ'), found[0].first)
    }

    @Test
    fun `a query with a maqaf finds the words with a space or a maqaf`() {
        assertEquals(2, findAllMatchesOriginal("כֹּה אָמַר וְכֹה־אָמַר", "כה־אמר").size)
    }

    @Test
    fun `a run of spaces in the query or the text is one`() {
        assertEquals(1, findAllMatchesOriginal("כה  אמר", "כה אמר").size)
        assertEquals(1, findAllMatchesOriginal("כה אמר", "כה   אמר").size)
    }

    @Test
    fun `a line keeps its count of matches`() {
        val text = "כִּי כֹה אָמַר יְהוָה וְעוֹד כֹּה־אָמַר"
        assertEquals(2, findAllMatchesOriginal(text, "כה אמר").size)
        assertEquals(2, findAllMatchesNormalized(text, normalizeQueryForHebrew("כה אמר")).size)
    }

    @Test
    fun `words joined without a break stay one word`() {
        assertEquals(0, findAllMatchesOriginal("כהאמר", "כה אמר").size)
    }

    @Test
    fun `the query's paseq is a space`() {
        assertEquals("כה אמר", normalizeQueryForHebrew("כה ׀ אמר"))
    }
}
