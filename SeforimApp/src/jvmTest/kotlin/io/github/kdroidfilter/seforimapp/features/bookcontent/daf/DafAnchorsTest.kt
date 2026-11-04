package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.seforimapp.core.annotations.UserHighlight
import io.github.kdroidfilter.seforimapp.core.annotations.UserNote
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LocatedRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The daf's printed words found in the library's own text, where the user's highlights and notes are kept. */
class DafAnchorsTest {
    private val tractate = 124L
    private val rashi = 1293L
    private val segment = 80288L
    private val comment = 90001L

    // The library's Gemara is vocalized and spells the print's abbreviations out
    private val gemaraText = "אָמַר רַבִּי יוֹחָנָן מִשּׁוּם רַבִּי שִׁמְעוֹן בֶּן יוֹחַי"
    private val rashiText = "משום רבי שמעון - דאמר לעיל"

    private fun line(
        c: String,
        t: String,
        u: List<List<Long>>,
    ) = DafLine(c = c, b = listOf(0f, 0f, 1f, 1f), t = t, u = u)

    private val amud =
        DafAmud(
            amud = "2a",
            page = listOf(600f, 800f),
            block = listOf(0f, 0f, 600f, 800f),
            lines =
                listOf(
                    line("G", "א״ר יוחנן משום", listOf(listOf(segment, 0, 2))),
                    line("G", "ר״ש בן יוחי", listOf(listOf(segment, 0, 2))),
                    line("R", "משום ר״ש. דאמר לעיל", listOf(listOf(comment, 0, 3))),
                ),
        )

    private val lines =
        mapOf(
            segment to LibraryLine(tractate, 41, gemaraText),
            comment to LibraryLine(rashi, 7, rashiText),
        )

    private val anchors = anchorsOf(amud, lines)

    private fun range(
        text: String,
        word: String,
    ): Pair<Int, Int> {
        val start = text.indexOf(word)
        return start to start + word.length
    }

    @Test
    fun anAbbreviationOfThePrintStandsForAllTheWordsItAbbreviates() {
        val amarRabbi = anchors.getValue(DafWord(0, 0))
        assertEquals(DafAnchor(tractate, segment, 41, 0, gemaraText.indexOf(" יוֹחָנָן")), amarRabbi)
        val rabbiShimon = anchors.getValue(DafWord(1, 0))
        assertEquals(gemaraText.indexOf("רַבִּי שִׁמְעוֹן") to gemaraText.indexOf(" בֶּן"), rabbiShimon.start to rabbiShimon.end)
        assertEquals(range(gemaraText, "יוֹחַי"), anchors.getValue(DafWord(1, 2)).let { it.start to it.end })
        // A comment's words in its own line, the abbreviation for two of them
        val shimon = anchors.getValue(DafWord(2, 1))
        assertEquals(rashi, shimon.bookId)
        assertEquals(rashiText.indexOf("רבי") to rashiText.indexOf(" -"), shimon.start to shimon.end)
    }

    @Test
    fun aLineBegunOnTheAmudBeforeIsFoundFromWhereThisAmudTakesItUp() {
        val library = libraryWords("אִם כֵּן מַאי אִירְיָא תַּרְתֵּי בְּנִיפְלֵי לָא אָמְרִינַן")
        val found = alignWords(listOf("תרתי", "בניפלי", "לא", "אמרינן"), library)
        assertEquals(listOf(4..4, 5..5, 6..6, 7..7), found)
    }

    @Test
    fun aPrintedWordTheLibraryHasNotHasNoPlaceButTheWordsAfterItDo() {
        val library = libraryWords("תַּרְתֵּי בְּנִיפְלֵי לָא אָמְרִינַן")
        val found = alignWords(listOf("הדרן", "עלך", "המוכר", "תרתי", "בניפלי", "לא"), library)
        assertEquals(listOf(null, null, null, 0..0, 1..1, 2..2), found)
    }

    @Test
    fun aWordSpelledOtherwiseInTheLibrarysEditionIsStillItsWord() {
        val library = libraryWords("וְאָכַל פִּתּוֹ בַּמֶּלַח")
        assertEquals(listOf(0..0, 1..1, 2..2), alignWords(listOf("ואכל", "פיתו", "במלח"), library))
    }

    @Test
    fun theUsersHighlightsAndNotesAreOnTheWordsTheirCharactersAreOn() {
        val (start, end) = range(gemaraText, "יוֹחָנָן")
        val highlights = mapOf(tractate to listOf(UserHighlight(1, segment, start, end, Color.Yellow)))
        val colored = highlightedWords(anchors, highlights, 0.4f)
        assertEquals(setOf(DafWord(0, 1)), colored.keys)
        assertEquals(Color.Yellow.copy(alpha = 0.4f), colored[DafWord(0, 1)])

        // A note on the end of the segment: the words its characters reach, of the main book only
        val note = UserNote(1, segment, gemaraText.indexOf("בֶּן"), gemaraText.length, "a note")
        assertEquals(setOf(DafWord(1, 1), DafWord(1, 2)), notedWords(anchors, listOf(note), tractate))
        assertEquals(emptySet(), notedWords(anchors, listOf(note), rashi))
    }

    @Test
    fun aSelectionIsLocatedLineByLineInEachLinesOwnBook() {
        val gemara = assertNotNull(locate(amud, DafRange(DafWord(0, 1), DafWord(1, 0)), anchors, tractate) { segment })
        val (start, _) = range(gemaraText, "יוֹחָנָן")
        assertEquals(listOf(LocatedRange(tractate, segment, start, gemaraText.indexOf(" בֶּן"))), gemara.ranges)
        assertEquals("יוחנן משום ר״ש", gemara.note?.quote)
        assertEquals(segment, gemara.note?.lineId)
        assertEquals(segment, gemara.lineId)

        // A comment's words: its own book's highlight, no note (the text's notes are the main book's)
        val comment = assertNotNull(locate(amud, DafRange(DafWord(2, 0), DafWord(2, 3)), anchors, tractate) { segment })
        assertEquals(listOf(rashi), comment.ranges.map { it.bookId })
        assertNull(comment.note)
    }
}
