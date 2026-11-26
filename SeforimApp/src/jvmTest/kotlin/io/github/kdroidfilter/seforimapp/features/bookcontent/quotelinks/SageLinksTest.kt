package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import io.github.kdroidfilter.seforimapp.features.sefaria.Mention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SageLinksTest {
    private fun link(
        id: Int,
        words: String,
    ) = "<a class=\"ref\" href=\"seforim://sage/$id\">$words</a>"

    @Test
    fun `a sage's name in a line is a link to its card`() {
        assertEquals(
            "אמר ${link(10, "רבי עקיבא")}: כך",
            withSageLinks("אמר רבי עקיבא: כך", listOf(Mention(10, "רבי עקיבא"))),
        )
    }

    @Test
    fun `a name is found whatever its nikud or its geresh`() {
        assertEquals(
            "אָמַר ${link(10, "רַבִּי עֲקִיבָא")}",
            withSageLinks("אָמַר רַבִּי עֲקִיבָא", listOf(Mention(10, "רבי עקיבא"))),
        )
        assertEquals(
            "אמר ${link(12, "ר' יהושע")} לו",
            withSageLinks("אמר ר' יהושע לו", listOf(Mention(12, "ר' יהושע"))),
        )
    }

    @Test
    fun `the longer name is the link, not a name inside it`() {
        assertEquals(
            "${link(5, "רבי אליעזר בן הורקנוס")} אומר",
            withSageLinks("רבי אליעזר בן הורקנוס אומר", listOf(Mention(6, "הורקנוס"), Mention(5, "רבי אליעזר בן הורקנוס"))),
        )
    }

    @Test
    fun `a word of the line's own link stays that link's`() {
        val verse = "<a class=\"ref\" href=\"seforim://line/7\">רבי עקיבא</a>"
        assertEquals(
            "$verse אמר ${link(10, "רבי עקיבא")}",
            withSageLinks("$verse אמר רבי עקיבא", listOf(Mention(10, "רבי עקיבא"))),
        )
    }

    @Test
    fun `each mention of the name is a link, one in short with its gershayim in it`() {
        // A word with a prefix letter is not the name as Sefaria wrote it: ור"מ and ורבי מאיר stay as they are
        assertEquals(
            "${link(11, "רבי מאיר")} אומר, ${link(11, "ר\"מ")} היא, ור\"מ סבר ורבי מאיר",
            withSageLinks("רבי מאיר אומר, ר\"מ היא, ור\"מ סבר ורבי מאיר", listOf(Mention(11, "רבי מאיר"), Mention(11, "ר\"מ"))),
        )
    }

    @Test
    fun `the same line when no name is found`() {
        val html = "אמר רבי טרפון"
        assertSame(html, withSageLinks(html, listOf(Mention(11, "רבי מאיר"), Mention(13, ""))))
    }
}
