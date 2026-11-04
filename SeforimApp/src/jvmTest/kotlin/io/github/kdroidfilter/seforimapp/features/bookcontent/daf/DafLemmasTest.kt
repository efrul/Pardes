package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The words of a Gemara segment that each dibbur hamatchil on it quotes. */
class DafLemmasTest {
    // Berakhot 2a, segment 5, as printed, and Rashi's nine comments on it
    private val segment =
        (
            "הקטר חלבים ואברים מצותן עד שיעלה עמוד השחר וכל הנאכלים ליום אחד מצותן עד שיעלה עמוד השחר " +
                "א״כ למה אמרו חכמים עד חצות כדי להרחיק אדם מן העבירה:"
        ).split(' ')

    private fun words(s: String) = s.split(' ')

    @Test
    fun rashisNineCommentsOnBerakhot2a() {
        val lemmas =
            listOf(
                "הקטר חלבים ואברים.",
                "מצותן.",
                "חלבים.",
                "אברים.",
                "וכל הנאכלים ליום אחד.",
                "מצותן.",
                "עד שיעלה עמוד השחר.",
                "אם כן למה אמרו חכמים עד חצות.",
                "כדי להרחיק אדם מן העבירה.",
            ).map(::words)
        val found = matchLemmas(segment, lemmas)
        assertEquals(0..2, found[0])
        assertEquals(3..3, found[1], "the first מצותן, after הקטר חלבים ואברים")
        assertEquals(1..1, found[2], "back in the phrase the first comment quoted")
        assertEquals(2..2, found[3], "ואברים, with its ו")
        assertEquals(8..11, found[4])
        assertEquals(12..12, found[5], "the second מצותן, after וכל הנאכלים ליום אחד")
        assertEquals(13..16, found[6], "the second עד שיעלה עמוד השחר, after it")
        assertEquals(17..22, found[7], "א״כ in the print for אם כן in the lemma")
        assertEquals(23..27, found[8])
    }

    @Test
    fun anEllipsisSpansFromTheFirstWordsToTheLast() {
        assertEquals(0..3, matchLemmas(segment, listOf(words("הקטר חלבים וכו' מצותן")))[0])
    }

    @Test
    fun aWordSpelledWithItsYodElsewhereIsTheSameWord() {
        // Bava Batra 29a: Rashbam's "תרתי לא קפדי", the Gemara's "תרתי לא קפיד"
        val gemara = words("אלא אמר רבא שתא קמייתא לא קפיד איניש תרתי לא קפיד תלת קפיד")
        assertEquals(8..10, matchLemmas(gemara, listOf(words("תרתי לא קפדי.")))[0])
    }

    @Test
    fun theDibbursAbbreviationForTheGemarasWordsAndThePrintsMarks() {
        val gemara = words("גמ׳ גודא אמר רב הונא הכי קאמר כותל שבין חצר לחצר")
        assertEquals(5..6, matchLemmas(gemara, listOf(words("ה״ק.")))[0], "ה״ק for הכי קאמר")
        assertEquals(1..1, matchLemmas(gemara, listOf(words("גמ׳ גודא.")))[0], "the print's mark is not the Gemara's")
    }

    @Test
    fun aWordNotInTheSegmentIsNotFound() {
        assertNull(matchLemmas(segment, listOf(words("קורין")))[0])
        assertNull(matchLemmas(segment, listOf(emptyList()))[0])
    }

    @Test
    fun keysTakeLettersOnly() {
        assertEquals("מצותנ", dafKey("מצותן:"))
        assertEquals("אכ", dafKey("א״כ"))
    }
}
