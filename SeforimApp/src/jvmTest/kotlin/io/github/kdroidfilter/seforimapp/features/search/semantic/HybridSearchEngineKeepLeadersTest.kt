package io.github.kdroidfilter.seforimapp.features.search.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [HybridSearchEngine.keepLeaders] (places 2 and 4, as semantic-model/eval-100/fusions.py measures it) and the quote
 * test that decides which lexical hits it keeps ([HybridSearchEngine.holdsQuote]).
 */
class HybridSearchEngineKeepLeadersTest {
    private val places = intArrayOf(1, 3)

    // The lexical side's first hits, in order, each at its place
    private fun <T> keep(
        fused: List<T>,
        leaders: List<T>,
    ) = HybridSearchEngine.keepLeaders(fused, leaders.withIndex().associate { (j, id) -> id to places[j] })

    @Test
    fun `lexical hits ranked low come up to places 2 and 4, the rest in its order`() {
        assertEquals(listOf(1, 8, 2, 9, 3, 4, 5, 6, 7, 10), keep((1..10).toList(), listOf(8, 9)))
    }

    @Test
    fun `a lexical hit the fusion ranks higher than its place stays there`() {
        assertEquals(listOf(1, 2, 3, 6, 4, 5), keep((1..6).toList(), listOf(1, 6)))
        assertEquals((1..6).toList(), keep((1..6).toList(), listOf(1, 3)))
    }

    @Test
    fun `the first lexical hit keeps place 2 when the second already ranks there`() {
        // "בראשית ברא אלהים את השמים ואת הארץ" on the whole library: Genesis 1:1 is the lexical side's first hit and
        // seventh in the fusion, Chagigah 12a its second and second in the fusion
        val fused = listOf("tamid", "chagigah", "yelamdenu", "mekhilta", "tanchuma", "lekach", "genesis")
        assertEquals(
            listOf("tamid", "genesis", "chagigah", "yelamdenu", "mekhilta", "tanchuma", "lekach"),
            keep(fused, listOf("genesis", "chagigah")),
        )
        // The second lexical hit the fusion ranks first stays first, the first lexical hit right after it
        assertEquals(listOf(2, 1, 3), keep(listOf(2, 3, 1), listOf(1, 2)))
    }

    @Test
    fun `only the second lexical hit kept goes to place 4`() {
        val fused = (1..10).toList()
        assertEquals(listOf(1, 2, 3, 9, 4, 5, 6, 7, 8, 10), HybridSearchEngine.keepLeaders(fused, mapOf(9 to 3)))
    }

    @Test
    fun `short lists and no lexical hits`() {
        assertEquals(listOf(1, 2), keep(listOf(1, 2), listOf(2)))
        assertEquals(listOf(5), keep(listOf(5), listOf(5)))
        assertEquals(listOf(3, 1, 2), keep(listOf(3, 1, 2), emptyList()))
    }

    @Test
    fun `nothing is lost or repeated`() {
        val fused = (1..200).toList()
        val out = keep(fused, listOf(150, 199))
        assertEquals(fused.size, out.size)
        assertEquals(fused.toSet(), out.toSet())
        assertEquals(listOf(150, 199), listOf(out[1], out[3]))
    }

    @Test
    fun `a line holds a quote word for word, its niqqud, teamim, markup and punctuation aside`() {
        val genesis = "(א) <big>בְּ</big>רֵאשִׁ֖ית בָּרָ֣א אֱלֹהִ֑ים אֵ֥ת הַשָּׁמַ֖יִם וְאֵ֥ת הָאָֽרֶץ׃"
        assertTrue(HybridSearchEngine.holdsQuote(genesis, "בראשית ברא אלהים את השמים ואת הארץ"))
        assertTrue(HybridSearchEngine.holdsQuote(genesis, "ברא אלהים"))
        // Maqaf, gershayim and final letters
        assertTrue(HybridSearchEngine.holdsQuote("וַיְהִי־עֶרֶב וַיְהִי־בֹקֶר יוֹם אֶחָד׃", "ויהי ערב ויהי בקר"))
        assertTrue(HybridSearchEngine.holdsQuote("אמר רבי יוחנן: ר\"ע אומר", "רבי יוחנן ר״ע"))
        assertTrue(HybridSearchEngine.holdsQuote("<b>ואהבת</b> לרעך כמוך, זה כלל גדול", "ואהבת לרעך כמוך"))
    }

    @Test
    fun `a quote written from memory still holds, its words in another order or with other prefixes`() {
        // Leviticus Rabbah 9:3 for the saying as it is known
        assertTrue(HybridSearchEngine.holdsQuote("עשרים ושש דורות קדמה דרך ארץ את התורה", "דרך ארץ קדמה לתורה"))
        assertTrue(HybridSearchEngine.holdsQuote("את השמים ואת הארץ ברא אלהים בראשית", "בראשית ברא אלהים את השמים ואת הארץ"))
        assertTrue(HybridSearchEngine.holdsQuote("ואהבת לרעך כמוך", "אהבת רעך כמוך"))
        // Two words more than the query's at most between them
        assertTrue(HybridSearchEngine.holdsQuote("אחד א ב שנים שלשה", "אחד שנים שלשה"))
        assertFalse(HybridSearchEngine.holdsQuote("אחד א ב ג ד שנים שלשה", "אחד שנים שלשה"))
    }

    @Test
    fun `words scattered over a line are no quote`() {
        assertFalse(HybridSearchEngine.holdsQuote("ואהבת את ה' אלהיך בכל לבבך ... לרעך ... כמוך", "ואהבת לרעך כמוך"))
        assertFalse(HybridSearchEngine.holdsQuote("ואהבת לרעך", "ואהבת לרעך כמוך"))
    }

    @Test
    fun `prefixes come off longer words only`() {
        assertEquals("תורה", HybridSearchEngine.stem("ולתורה"))
        assertEquals("שבת", HybridSearchEngine.stem("שבת"))
        assertEquals("משה", HybridSearchEngine.stem("משה"))
        assertEquals("ארצ", HybridSearchEngine.stem("הארצ"))
    }

    @Test
    fun `one word is no quote`() {
        assertNull(HybridSearchEngine.quoteKey("שבת"))
        assertNull(HybridSearchEngine.quoteKey(" , "))
        assertEquals("שבת שלומ", HybridSearchEngine.quoteKey("שַׁבָּת שָׁלוֹם!"))
    }
}
