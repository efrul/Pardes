package io.github.kdroidfilter.seforimapp.features.sefaria

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SefariaDataTest {
    private fun rows(vararg rows: List<String>) = rows.asSequence().map { it.joinToString("\t") }

    private val topics =
        SefariaTopics.parse(
            rows(
                listOf("T", "1", "תפילה", "תפלה|סידור", "עבודה שבלב"),
                listOf("T", "2", "קריאת שמע", "שמע", ""),
                listOf("T", "3", "ברכות", "ברכה", ""),
                listOf("T", "4", "מועדי השנה", "", ""),
                listOf("T", "5", "שבת", "השבת", "היום השביעי"),
                listOf("O", "4", "1"),
                listOf("O", "1", "0"),
                listOf("R", "2", "under", "1"),
                listOf("R", "3", "under", "1"),
                listOf("R", "2", "isa", "1"),
                listOf("R", "5", "related", "2"),
                listOf("R", "2", "unknown", "5"),
                listOf("A", "2", "ברכות ב., א", "10", "1"),
                listOf("A", "2", "ברכות ב., ב", "10", "0"),
                listOf("A", "1", "ברכות ב., א", "5", "1"),
                listOf("A", "1", "ברכות יא:, א", "3", "1"),
                listOf("A", "3", "ברכות ב., א", "1", "1"),
            ),
        )

    private val sages =
        SefariaSages.parse(
            rows(
                listOf("P", "10", "רבי עקיבא", "עקיבא|ר' עקיבא", "תנא", "T3", "https://he.wikipedia.org/wiki/רבי_עקיבא"),
                listOf("P", "11", "רבי מאיר", "", "", "T4", ""),
                listOf("P", "12", "ר' יהושע", "רבי יהושע בן חנניה", "", "T2", ""),
                listOf("P", "13", "רבי טרפון", "", "", "T3", ""),
                listOf("P", "40", "חכמי יבנה", "", "", "", ""),
                listOf("W", "20", "רבן גמליאל", "11|13"),
                listOf("R", "12", "taught", "10"),
                listOf("R", "10", "taught", "11"),
                listOf("R", "10", "member", "40"),
                listOf("R", "12", "child", "13"),
                listOf("M", "ברכות ב., א", "10", "רבי עקיבא"),
                listOf("M", "ברכות ב., א", "10", "רַבִּי עֲקִיבָא"),
                listOf("M", "ברכות ב., א", "11", "רבי מאיר"),
                listOf("M", "ברכות ג., ב", "10", ""),
            ),
        )

    @Test
    fun `a line's topics are the ones with the most sources first`() {
        assertEquals(listOf("תפילה", "קריאת שמע", "ברכות"), topics.of("ברכות ב., א").map { it.name })
        // A line of a source's range is about its topic, though no source of its own
        assertEquals(listOf("קריאת שמע"), topics.of("ברכות ב., ב").map { it.name })
        assertEquals(listOf("ברכות ב., א"), topics.sourcesOf(2))
        assertEquals(2, topics.sourceCount(1))
        assertTrue(topics.of("ברכות ד., א").isEmpty())
    }

    @Test
    fun `the tree's roots come in their order, each topic's children under it`() {
        assertEquals(listOf(1, 4), topics.roots)
        assertEquals(listOf("קריאת שמע", "ברכות"), topics.children(1).map { it.name })
        assertTrue(topics.children(4).isEmpty())
    }

    @Test
    fun `a topic's neighbours are read both ways, a relation unknown left out`() {
        val shema = topics.neighbours(2)
        assertEquals(listOf("תפילה"), shema.shownUnder.map { it.name })
        assertEquals(listOf("תפילה"), shema.broader.map { it.name })
        assertEquals(listOf("שבת"), shema.related.map { it.name })
        assertEquals(listOf("קריאת שמע"), topics.neighbours(1).narrower.map { it.name })
        assertEquals(listOf("קריאת שמע"), topics.neighbours(5).related.map { it.name })
    }

    @Test
    fun `a topic is found by any of its names as typed`() {
        assertEquals(1, topics.named("סִידּוּר")?.id)
        assertEquals(5, topics.named(" השבת ")?.id)
        assertNull(topics.named(""))
        // The index and the search show the topics that have sources
        assertEquals(listOf("קריאת שמע"), topics.startingWith('ק').map { it.name })
        assertTrue(topics.startingWith('ש').isEmpty())
        assertEquals(listOf("קריאת שמע"), topics.search("שמע", 10).map { it.name })
        assertEquals(listOf("תפילה"), topics.search("תפ", 10).map { it.name })
    }

    @Test
    fun `a topic is found as the bar finds books, in another spelling, with a typo or in another order`() {
        assertEquals(listOf("קריאת שמע"), topics.search("קראת שמע", 10).map { it.name })
        assertEquals(listOf("קריאת שמע"), topics.search("שמע קריאת", 10).map { it.name })
        assertEquals(listOf("תפילה"), topics.search("תפילא", 10).map { it.name })
        // The topics with sources alone: שבת is a name of no source
        assertTrue(topics.search("שבת", 10).isEmpty())
    }

    @Test
    fun `the search's card names a topic in another spelling or order, never by a typo`() {
        assertEquals(2, topics.named("קראת שמע")?.id)
        assertEquals(2, topics.named("שמע קריאת")?.id)
        assertNull(topics.named("קריעת שמע"))
        assertNull(topics.named("תפילא"))
        // The name whole: a word more or less names nothing
        assertNull(topics.named("קריאת"))
    }

    @Test
    fun `a sage's mentions, places and relations are read`() {
        assertEquals(listOf(10, 10, 11), sages.at("ברכות ב., א").map { it.sageId })
        // A line once, however many times it mentions the sage
        assertEquals(listOf("ברכות ב., א", "ברכות ג., ב"), sages.placesOf(10))
        assertEquals(listOf("רבי עקיבא"), sages.students(12).map { it.name })
        assertEquals(listOf("ר' יהושע"), sages.teachers(10).map { it.name })
        assertEquals(listOf("רבי מאיר"), sages.students(10).map { it.name })
        assertEquals(listOf("רבי טרפון"), sages.family(12).map { it.name })
        assertEquals(listOf("ר' יהושע"), sages.family(13).map { it.name })
        assertEquals(listOf("חכמי יבנה"), sages.groups(10).map { it.name })
        assertEquals(listOf(11, 13), sages.ambiguous[20]?.options)
        assertEquals("T3", sages.sages[10]?.generation)
    }

    @Test
    fun `a sage is found by any of its names, the most mentioned first`() {
        assertEquals(listOf(12), sages.search("בן חנניה", 10).map { it.id })
        assertEquals(listOf(10), sages.search("עקיבא", 10).map { it.id })
        assertEquals(listOf(10, 11), sages.search("", 2).map { it.id })
        // Another spelling, a typo, and a leading רבי not needed
        assertEquals(listOf(13), sages.search("טרפן", 10).map { it.id })
        assertEquals(listOf(10), sages.search("עקיבה", 10).map { it.id })
        assertEquals(listOf(12), sages.search("רבי יהושע", 10).map { it.id })
    }

    @Test
    fun `a line in a sugya and in its Mishnah is the sugya's`() {
        val mishnahFirst =
            SefariaSugyot.parse(
                rows(
                    listOf("S", "M", "steinsaltz", "ברכות ב., א|ברכות ב., ב"),
                    listOf("S", "S", "steinsaltz", "ברכות ב., ב|ברכות ב., ג"),
                ),
            )
        assertEquals('M', mishnahFirst.of("ברכות ב., א")?.kind)
        assertEquals('S', mishnahFirst.of("ברכות ב., ב")?.kind)
        assertEquals(listOf("ברכות ב., ב", "ברכות ב., ג"), mishnahFirst.of("ברכות ב., ג")?.lines)
        assertNull(mishnahFirst.of("ברכות ד., א"))
        val sugyaFirst =
            SefariaSugyot.parse(
                rows(
                    listOf("S", "S", "steinsaltz", "ברכות ב., ב|ברכות ב., ג"),
                    listOf("S", "M", "steinsaltz", "ברכות ב., א|ברכות ב., ב"),
                ),
            )
        assertEquals('S', sugyaFirst.of("ברכות ב., ב")?.kind)
    }

    @Test
    fun `each headword of Krupnik's dictionary, and each other one, has the entry's text`() {
        val entries =
            KrupnikDictionary.parse(
                rows(
                    listOf("K", "אביא", "", "עי׳ אַסְיָא"),
                    listOf("K", "בְּסוֹרָא", "בְּסוֹרְתָא", "בשורה טובה"),
                    listOf("X", "אחר", "", "לא"),
                ),
            )
        assertEquals(
            listOf("אביא" to "עי׳ אַסְיָא", "בְּסוֹרָא" to "בשורה טובה", "בְּסוֹרְתָא" to "בשורה טובה"),
            entries,
        )
    }

    @Test
    fun `the shipped data is read`() {
        val shipped = SefariaTopics.shipped
        assertTrue(shipped.topics.size > 4_000, "${shipped.topics.size} topics")
        assertEquals(14, shipped.roots.size)
        assertTrue(shipped.of("ברכות ב., א").map { it.name }.containsAll(listOf("קריאת שמע", "תפילה")))
        assertTrue(shipped.sourceCount(shipped.named("שבת")!!.id) > 100)
        val sages = SefariaSages.shipped
        assertTrue(sages.at("ברכות ב:, יג").any { it.text == "רבי יהודה" })
        // Sefaria's ר without its geresh is the library's ר'
        assertTrue(sages.sages.values.none { it.name.startsWith("ר ") })
        assertEquals("רבי עקיבא", sages.search("רבי עקיבא", 10).first().name)
        assertEquals("תפילה", shipped.search("תפלה", 10).first().name)
        assertEquals('M', SefariaSugyot.shipped.of("ברכות ב., א")?.kind)
        assertEquals('S', SefariaSugyot.shipped.of("ברכות ב:, ה")?.kind)
        assertTrue(KrupnikDictionary.shipped.size > 18_000)
    }
}
