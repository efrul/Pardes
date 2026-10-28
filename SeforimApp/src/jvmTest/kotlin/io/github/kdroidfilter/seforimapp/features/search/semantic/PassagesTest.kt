package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [Passages] and [IndependentSemanticSearch.bestPassages], the passage a search by meaning highlights. */
class PassagesTest {
    private val root = createTempDirectory().toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun passages(text: String) = Passages.split(text).map(text::substring)

    @Test
    fun `a line is cut into its sentences, each a verbatim part of it`() {
        val text = "(א) אמר רבי יוחנן: כל המתפלל צריך שיתפלל בכוונה. ואם לא כיון, יחזור ויתפלל."
        assertEquals(listOf("(א) אמר רבי יוחנן:", "כל המתפלל צריך שיתפלל בכוונה.", "ואם לא כיון, יחזור ויתפלל."), passages(text))
        assertEquals(
            listOf("בְּרֵאשִׁית בָּרָא אֱלֹהִים אֵת הַשָּׁמַיִם וְאֵת הָאָרֶץ׃"),
            passages("בְּרֵאשִׁית בָּרָא אֱלֹהִים אֵת הַשָּׁמַיִם וְאֵת הָאָרֶץ׃"),
        )
    }

    @Test
    fun `short pieces join the next one, or the last one at the end`() {
        assertEquals(listOf("שאלה: מה? תשובה: כך וכך היא ההלכה."), passages("שאלה: מה? תשובה: כך וכך היא ההלכה."))
        assertEquals(
            listOf("ואלו דברים שאין להם שיעור הפאה והבכורים.", "וגמילות חסדים ותלמוד תורה: כנגד כולם."),
            passages("ואלו דברים שאין להם שיעור הפאה והבכורים. וגמילות חסדים ותלמוד תורה: כנגד כולם."),
        )
    }

    @Test
    fun `a long sentence is cut at its commas, then into even pieces of words`() {
        val clause = (1..20).joinToString(" ") { "מילה$it" }
        val withCommas = "$clause, $clause, $clause."
        assertEquals(3, passages(withCommas).size)
        val cuts =
            listOf(
                75 to listOf(25, 25, 25),
                34 to listOf(17, 17),
                64 to listOf(21, 21, 22),
                // Fewer than 4 words left over stay with the rest
                33 to listOf(33),
                63 to listOf(31, 32),
                30 to listOf(30),
            )
        for ((words, sizes) in cuts) {
            val bare = (1..words).joinToString(" ") { "מילה$it" }
            val cut = passages(bare)
            assertEquals(sizes, cut.map { it.split(' ').size })
            assertEquals(bare, cut.joinToString(" "))
        }
    }

    @Test
    fun `the semicolon of an escaped entity ends no sentence`() {
        // A results snippet's text is HTML-escaped (SnippetSources.clean)
        assertEquals(
            listOf("אמר להם משה רבנו&nbsp;עליו השלום דברים אלו.", "ויענו כל העם יחד ויאמרו נעשה &amp; נשמע."),
            passages("אמר להם משה רבנו&nbsp;עליו השלום דברים אלו. ויענו כל העם יחד ויאמרו נעשה &amp; נשמע."),
        )
        assertEquals(2, passages("אמר להם משה רבנו דברים אלו; ויענו כל העם יחד ויאמרו נעשה").size)
    }

    @Test
    fun `the passages cover the line in order, no space around them`() {
        val text = "  ויאמר: לא כן.   ויען ויאמר; כן הוא באמת, ואין לשנות דבר!  "
        val ranges = Passages.split(text)
        ranges.zipWithNext { a, b -> assertTrue(a.last < b.first) }
        ranges.forEach { assertEquals(text.substring(it), text.substring(it).trim()) }
    }

    @Test
    fun `the passage closest in meaning is the one picked, a single passage none`() {
        val provider = KeywordProvider("שבת")
        val search = IndependentSemanticSearch(emptyIndex(), provider)
        val texts =
            listOf(
                "דיני תפילה ותחנונים בכל יום. ובשבת ויום טוב אומרים מזמורים אחרים לגמרי.",
                "אין כאן אלא משפט אחד ויחיד בלבד",
            )
        assertEquals(listOf("ובשבת ויום טוב אומרים מזמורים אחרים לגמרי.", null), search.bestPassages("שבת", texts))
        // The query's vector is kept: the reader asks again for each line it shows
        search.bestPassages("שבת", texts)
        assertEquals(1, provider.queries)
        search.close()
    }

    @Test
    fun `a passage is embedded once, for every query and every line holding it`() {
        val provider = KeywordProvider("שבת")
        val search = IndependentSemanticSearch(emptyIndex(), provider)
        val line = "דיני תפילה ותחנונים בכל יום. ובשבת ויום טוב אומרים מזמורים אחרים לגמרי."
        search.bestPassages("שבת", listOf(line, line))
        assertEquals(2, provider.passages)
        assertEquals(listOf("ובשבת ויום טוב אומרים מזמורים אחרים לגמרי."), search.bestPassages("שבת קודש", listOf(line)))
        assertEquals(2, provider.passages)
        search.close()
    }

    @Test
    fun `of a very long line, the first passages and those holding the query's words`() {
        val search = IndependentSemanticSearch(emptyIndex(), KeywordProvider("שבת"))
        val sentences = (1..40).map { "זה המשפט ה$it בשורה ארוכה מאוד." }.toMutableList()
        sentences[37] = "וכאן לבסוף מדובר על שבת קודש."
        val line = sentences.joinToString(" ")
        val words = Regex("שבת").findAll(line).map { it.range }.toList()
        assertEquals(listOf(sentences[37]), search.bestPassages("שבת", listOf(line)) { words })
        // Without the query's words, the first ones alone: all as far from it, the first of them
        assertEquals(listOf(sentences[0]), search.bestPassages("שבת", listOf(line)))
        search.close()
    }

    @Test
    fun `a model that can't embed passages gives none`() {
        val search = IndependentSemanticSearch(emptyIndex(), KeywordProvider("שבת", canEmbedPassages = false))
        assertNull(search.bestPassages("שבת", listOf("משפט ראשון כאן במקום. ומשפט שני אחריו כאן.")))
        search.close()
    }

    private fun emptyIndex(): java.nio.file.Path =
        root.toPath().resolve("index").also { dir ->
            FSDirectory.open(dir).use { IndexWriter(it, IndexWriterConfig()).use(IndexWriter::commit) }
        }

    /** Two dimensions: whether a text holds [keyword]. */
    private class KeywordProvider(
        private val keyword: String,
        private val canEmbedPassages: Boolean = true,
    ) : EmbeddingProvider {
        var queries = 0

        /** Passages embedded so far. */
        var passages = 0
        override val dimension = 2

        override fun embed(text: String): ByteArray = ByteArray(dimension)

        override fun embedQuery(text: String): FloatArray {
            queries++
            return vector(text)
        }

        override fun embedPassages(texts: List<String>): List<FloatArray>? {
            if (!canEmbedPassages) return null
            passages += texts.size
            return texts.map(::vector)
        }

        private fun vector(text: String) = if (keyword in text) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f)

        override fun close() {}
    }
}
