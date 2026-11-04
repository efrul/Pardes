package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Desktop's KleiKodeshDictionaryTest, but for the shipped pack (read from the APK's assets, not here). */
class KleiKodeshDictionaryTest {
    private fun lines(vararg contents: Pair<Int, String>) =
        contents.associate { (index, content) -> index to Line(id = 1000L + index, bookId = 7, lineIndex = index, content = content) }

    private fun DictionaryEntry.short() = Triple(word, gloss, first..last)

    @Test
    fun aPackReadsItsBooksTextsAndLinks() {
        val pack =
            KleiKodeshPack.parse(
                sequenceOf(
                    "B\tMETZUDAT\tמצודת ציון על איוב",
                    "B\tUNKNOWN\tספר אחר",
                    "E\t0\t4\tצמד",
                    "E\t1\t9\tנשמט",
                    "S\tWIKIPEDIA\tא\"א\tאברהם אבינו",
                    "L\t1\tכעס\tקצף",
                    "L\t2\tשפסחו\tפסח",
                    "L\t3\tאחר\tמילה",
                ),
            )
        assertEquals(mapOf("מצודת ציון על איוב" to listOf(IndexedHeadword(4, "צמד"))), pack.books[Dictionary.METZUDAT])
        assertEquals(1, pack.books.size)
        assertEquals(listOf("א\"א" to "אברהם אבינו"), pack.texts[Dictionary.WIKIPEDIA])
        // A synonym both ways; a form to its word only; other kinds left out
        assertEquals(listOf(RelatedWord("קצף", RelationKind.SYNONYM)), pack.related("כעס"))
        assertEquals(listOf(RelatedWord("כעס", RelationKind.SYNONYM)), pack.related("קצף"))
        assertEquals(listOf(RelatedWord("פסח", RelationKind.SPELLING)), pack.related("שֶׁפְּסָחוּ"))
        assertEquals(emptyList(), pack.related("פסח"))
        assertEquals(emptyList(), pack.related("אחר"))
    }

    @Test
    fun anEntryIsKeptOnlyWhenItsLineHoldsItsHeadword() {
        val checked =
            Dictionary.METZUDAT.indexedEntries(
                7,
                "מצודת ציון על איוב",
                listOf(IndexedHeadword(4, "צמד"), IndexedHeadword(6, "הקיפו"), IndexedHeadword(8, "חסר")),
                lines(4 to "<b>צֶמֶד.</b> ענין זוג", 6 to "שורה אחרת לגמרי"),
            )
        assertEquals(listOf(Triple("צֶמֶד", "איוב · ענין זוג", 4..4)), checked.entries.map { it.short() })
        assertEquals(1004L, checked.entries.single().lineId)
        assertEquals("צמד", checked.entries.single().key)
        assertEquals(2, checked.dropped)
    }

    @Test
    fun aHeadingReadsItsLinesUpToTheNextEntry() {
        val checked =
            Dictionary.MIKROPEDIA.indexedEntries(
                7,
                "מיקרופדיה תלמודית",
                listOf(IndexedHeadword(85, "אב המוליד"), IndexedHeadword(90, "אבוד")),
                lines(
                    85 to "<h2>אב  המוליד</h2>",
                    86 to "<b>הגדרה</b><sup>11</sup> - אב הוא המוליד",
                    87 to "עוד",
                    90 to "<h2>אבוד</h2>",
                ),
            )
        assertEquals(
            listOf(Triple("אב המוליד", "הגדרה - אב הוא המוליד", 85..87), Triple("אבוד", "", 90..90)),
            checked.entries.map { it.short() },
        )
    }

    @Test
    fun machberetMenachemAsTheLibraryHasIt() {
        // As in the October 2026 library: the word strong and big after a direction mark, its explanation below
        val checked =
            Dictionary.MENACHEM.indexedEntries(
                1,
                "מחברת מנחם",
                listOf(IndexedHeadword(264, "אב"), IndexedHeadword(268, "אבד")),
                lines(
                    264 to "<strong><big>\u200eאב </big></strong>",
                    265 to "\u200eמתחלק לששה פנים: המראה הראשון, <b>אב</b> ואם",
                    266 to "\u200eהשני. ולא <b>אבה</b>",
                    267 to "<h3>ערך ב</h3>",
                    268 to "<strong><big>\u200eאבד </big></strong>",
                    269 to "\u200eאו מצא <b>אבדה</b>",
                ),
            )
        assertEquals(
            listOf(Triple("אב", "מתחלק לששה פנים: המראה הראשון, אב ואם", 264..266), Triple("אבד", "או מצא אבדה", 268..269)),
            checked.entries.map { it.short() },
        )
    }

    @Test
    fun aLineWithoutMarksKeepsTheIndexHeadword() {
        val checked =
            Dictionary.MENACHEM.indexedEntries(1, "מחברת מנחם", listOf(IndexedHeadword(3, "אבד")), lines(3 to "אבד: יתחלק לשלש מחלקות"))
        assertEquals(listOf(Triple("אבד", "יתחלק לשלש מחלקות", 3..3)), checked.entries.map { it.short() })
    }

    @Test
    fun theLinesReadAreEachEntrysAndAHeadingsFollowing() {
        assertEquals(
            listOf(4..70, 200..260),
            linesToRead(listOf(IndexedHeadword(10, "ב"), IndexedHeadword(4, "א"), IndexedHeadword(200, "ג"))),
        )
    }

    @Test
    fun textEntriesHaveNoBookAndIdsOfTheirOwn() {
        val entries = Dictionary.WIKIPEDIA.textEntries(listOf("א\"א" to "אברהם אבינו", "אדה\"ז" to "אדמו\"ר הזקן"), idBase = -5)
        assertEquals(listOf(-5L, -6L), entries.map { it.lineId })
        assertEquals("אא", entries.first().key)
        assertEquals("אברהם אבינו", entries.first().text)
        assertEquals(-1L, entries.first().bookId)
        assertNull(Dictionary.of("WIKIPEDIA", DictionarySource.INDEX))
    }

    @Test
    fun titlesAsALibraryMaySpellThem() {
        assertTrue("מלבי״ם ביאור המילות על איוב" in titleVariants("מלבי\"ם באור המילות על איוב"))
    }
}
