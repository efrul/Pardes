package io.github.kdroidfilter.seforimapp.core

import io.github.kdroidfilter.seforimapp.core.selection.CommentaryLineRef
import io.github.kdroidfilter.seforimapp.core.selection.DefaultSelectionContext
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LocatedRange
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A selection made in a commentary, a targum or the daf view's Rashi and Tosafot is copied with its own source, not the
 * book open in the tab.
 */
class CommentarySourceTest {
    // The commentaries pane's column on Bava Batra 2a: two of the Rashba's, one of Tosafot's
    private val column =
        listOf(
            CommentaryLineRef(1741, 11, "<b>השותפין שרצו וכו'.</b> תמיה לי, אמאי לא תני סתמא"),
            CommentaryLineRef(1741, 12, "<b>הכל כמנהג המדינה.</b> י\"א כמנהג בניני העיר"),
            CommentaryLineRef(900, 21, "<b>בגויל.</b> פירש בקונטרס אבנים שאינן משופות"),
        )

    private val rashba = Book(id = 1741, categoryId = 340, sourceId = 1, title = "רשב״א על בבא בתרא", heRef = "רשב״א על בבא בתרא")
    private val rashi = Book(id = 300, categoryId = 20, sourceId = 1, title = "רש״י על בראשית", heRef = "רש״י על בראשית")
    private val rashiLines =
        listOf(
            Line(31, 300, 7, "", heRef = "רש״י על בראשית כה, א, א"),
            Line(32, 300, 9, "", heRef = "רש״י על בראשית כה, ב, א"),
        )
    private val repository =
        mockk<SeforimRepository> {
            coEvery { getBookCore(1741) } returns rashba
            coEvery { getBookCore(300) } returns rashi
            coEvery { getLine(11) } returns Line(11, 1741, 3, "", heRef = "רשב״א על בבא בתרא ב., א")
            coEvery { getLine(12) } returns Line(12, 1741, 4, "", heRef = "רשב״א על בבא בתרא ב., ב")
            coEvery { getLine(31) } returns rashiLines[0]
            coEvery { getLine(32) } returns rashiLines[1]
        }
    private val catalog =
        mockk<CatalogCache> {
            every { getRootForBook(rashba) } returns Category(id = 1, title = "תלמוד", level = 0)
            every { getRootForBook(rashi) } returns Category(id = 2, title = "תנ״ך", level = 0)
        }

    @Test
    fun `a selection is found among the commentaries of the column`() {
        assertEquals(column[0] to column[1], commentaryRangeOf("אמאי לא תני סתמא\nהכל כמנהג המדינה", column))
        assertEquals(column[2] to column[2], commentaryRangeOf("אבנים שאינן משופות", column))
        assertNull(commentaryRangeOf("ואלה תולדות יצחק", column))
        assertNull(commentaryRangeOf("תמיה לי", emptyList()))
    }

    @Test
    fun `it is copied with the commentary's book and daf`() =
        runBlocking {
            assertEquals(
                "תמיה לי, אמאי לא תני\n\n(רשב״א על בבא בתרא ב.)",
                commentaryCopyWithSource("תמיה לי, אמאי לא תני", column, repository, catalog),
            )
            assertNull(commentaryCopyWithSource("ואלה תולדות יצחק", column, repository, catalog))
        }

    @Test
    fun `the daf view's selection is copied with the lines it locates, first to last in their own book`() =
        runBlocking {
            // Rashi's two lines, the later one first, then a word of the Gemara: Rashi's are cited, in their order
            val ranges = listOf(LocatedRange(300, 32, 0, 4), LocatedRange(300, 31, 2, 9), LocatedRange(5, 99, 0, 3))
            assertEquals(
                "שנאים מעשיה\n\n(רש״י על בראשית כה, א, א – רש״י על בראשית כה, ב, א)",
                menuOwnSourceOf("שנאים מעשיה", ranges, column)?.invoke(repository, catalog),
            )
            assertEquals(
                "תמיה לי\n\n(רשב״א על בבא בתרא ב.)",
                menuOwnSourceOf("תמיה לי", emptyList(), column)?.invoke(repository, catalog),
            )
            assertNull(menuOwnSourceOf("ואלה תולדות יצחק", emptyList(), column))
        }

    @Test
    fun `the shortcut takes a selection of the window's tab whose lines are in another book, or in the column only`() =
        runBlocking {
            val bava = Book(id = 1, categoryId = 340, sourceId = 1, title = "בבא בתרא")
            val gemara = Line(5, 1, 2, "השותפין שרצו לעשות מחיצה בחצר", heRef = "בבא בתרא ב., א")
            val context =
                DefaultSelectionContext().apply {
                    setActiveBook("tab", bava, "תלמוד")
                    setVisibleLines("tab", listOf(gemara))
                    setActiveCommentaryColumn(column)
                }
            // The daf's Rashi: its lines are known, in another book than the tab's
            context.setSelectedText("tab", "שנאים מעשיה", rashiLines[0] to rashiLines[1])
            assertEquals(
                "שנאים מעשיה\n\n(רש״י על בראשית כה, א, א – רש״י על בראשית כה, ב, א)",
                ownSourceOf(context, "tab")?.invoke(repository, catalog),
            )
            assertNull(ownSourceOf(context, "other tab"))
            // The daf's Gemara, its lines in the tab's book: Zayit's shortcut cites it
            context.setSelectedText("tab", "השותפין שרצו", gemara to gemara)
            assertNull(ownSourceOf(context, "tab"))
            // A commentary of the column, not among the book's lines
            context.setSelectedText("tab", "תמיה לי, אמאי לא תני")
            assertEquals("תמיה לי, אמאי לא תני\n\n(רשב״א על בבא בתרא ב.)", ownSourceOf(context, "tab")?.invoke(repository, catalog))
            // The book's own text
            context.setSelectedText("tab", "לעשות מחיצה בחצר")
            assertNull(ownSourceOf(context, "tab"))
        }

    @Test
    fun `a commentary on the Talmud is cited by its daf, as the Gemara is`() {
        val rashiBava = Book(id = 1293, categoryId = 249, sourceId = 1, title = "רש\"י על בבא בתרא", heRef = "רש\"י על בבא בתרא")
        // The daf, the Gemara's segment, the comment
        val first = Line(1, 1293, 3, "", heRef = "רש\"י על בבא בתרא ב., א, א")
        val last = Line(2, 1293, 7, "", heRef = "רש\"י על בבא בתרא ב., ב, א")
        val nextAmud = Line(3, 1293, 90, "", heRef = "רש\"י על בבא בתרא ב:, א, א")
        assertEquals("רש\"י על בבא בתרא ב.", formatHebrewSourceReference(rashiBava, "תלמוד", first, last))
        assertEquals("רש\"י על בבא בתרא ב. – רש\"י על בבא בתרא ב:", formatHebrewSourceReference(rashiBava, "תלמוד", first, nextAmud))
    }
}
