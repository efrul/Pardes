package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.features.pdf.PdfOutlineEntry
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceParser
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A tractate's daf ב. in a file of the user's: set by the user, or guessed by its bookmarks or the tractates before it. */
class ScanSourceTest {
    private val root = createTempDirectory("scan-source").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    // A table of contents from daf ב. to [lastDaf] amud b
    private fun toc(
        bookId: Long,
        lastDaf: Int,
    ): List<TocEntry> =
        (2..lastDaf).flatMap { daf ->
            listOf(".", ":").map { mark ->
                TocEntry(id = daf * 2L, bookId = bookId, text = "דף ${ReferenceParser.hebrewNumeral(daf)}$mark", level = 0)
            }
        }

    @Test
    fun `a tractate's amudim are counted by its table of contents`() {
        assertEquals(126, amudCount(toc(1, 64)), "2a to 64b")
        assertNull(amudCount(listOf(TocEntry(id = 1, bookId = 1, text = "הקדמה", level = 0))))
    }

    @Test
    fun `in a file of several, a tractate begins after the amudim of those named before it`() {
        val amuds = mapOf("עבודה זרה" to 151, "הוריות" to 27)

        assertEquals(0, firstByName("עבודה זרה", listOf("עבודה זרה", "הוריות"), amuds::get))
        assertEquals(151, firstByName("הוריות", listOf("עבודה זרה", "הוריות"), amuds::get))
        assertNull(firstByName("הוריות", listOf("שבועות", "הוריות"), amuds::get), "a tractate before it not known")
        assertNull(firstByName("מכות", listOf("עבודה זרה", "הוריות"), amuds::get))
        val set = mapOf("עבודה זרה" to 2)
        assertEquals(153, firstByName("הוריות", listOf("עבודה זרה", "הוריות"), amuds::get, set::get), "from the page set before it")
    }

    @Test
    fun `the bookmarks give a tractate's daf ב, under the tractate's own or anywhere in a file of it alone`() {
        val joined =
            listOf(
                PdfOutlineEntry("שער", 0),
                PdfOutlineEntry("מסכת עבודה זרה", 2, listOf(PdfOutlineEntry("ב.", 4), PdfOutlineEntry("ב:", 5))),
                PdfOutlineEntry("מסכת הוריות", 160),
            )
        assertEquals(4, firstByBookmarks("עבודה זרה", joined, alone = false))
        assertEquals(160, firstByBookmarks("הוריות", joined, alone = false), "the tractate's own bookmark, with none under it")
        assertNull(firstByBookmarks("שבועות", joined, alone = false))

        val alone = listOf(PdfOutlineEntry("שער", 0), PdfOutlineEntry("הקדמה", 1), PdfOutlineEntry("דף ב עמוד א", 3))
        assertEquals(3, firstByBookmarks("ברכות", alone, alone = true))
        assertNull(firstByBookmarks("ברכות", alone, alone = false))
    }

    @Test
    fun `the page at the top named by its daf and amud gives the tractate's first page`() {
        assertEquals(4, firstFromTop(10, "5a"), "5a is the tractate's sixth page")
        assertEquals(10, firstFromTop(10, "2a"))
        assertNull(firstFromTop(3, "5a"), "before the file's start")
        assertEquals(5, dafTyped("ה"))
        assertEquals(5, dafTyped("ה'"))
        assertEquals(12, dafTyped("יב"))
        assertEquals(40, dafTyped(" 40 "))
        assertNull(dafTyped("א"), "a tractate begins at daf ב")
        assertNull(dafTyped("abc"))
    }

    @Test
    fun `a tractate's pages are its count, at most the file's after its first`() {
        val file = File(root, "x.pdf")

        assertEquals(20, ScanSource(file, first = 10, count = 20).pagesIn(100))
        assertEquals(15, ScanSource(file, first = 10, count = 20).pagesIn(25))
        assertEquals(30, ScanSource(file).pagesIn(30))
        assertEquals(0, ScanSource(file, first = 40).pagesIn(30))
    }

    @Test
    fun `a joined file of the folder places each tractate, and the user's page comes first`() =
        runBlocking {
            val repository = mockk<SeforimRepository>()
            coEvery { repository.getBooksByCategory(any()) } returns emptyList()
            coEvery { repository.getBooksByCategory(CatalogPresets.Ids.Categories.BAVLI_NEZIKIN) } returns
                listOf(book(1, "עבודה זרה"), book(2, "הוריות"))
            coEvery { repository.getTocEntriesForBook(1) } returns toc(1, 76)
            coEvery { repository.getTocEntriesForBook(2) } returns toc(2, 14)
            val scans = DafScans(testAppSettings(), repository).apply { otzariaRoots = listOf(File(root, "nothing")) }
            val joined = File(root, "mine/18 - עבודה זרה הוריות עדיות מורחב - מחובר.pdf").apply { parentFile.mkdirs() }
            joined.writeText("%PDF")
            scans.setFolder(File(root, "mine"))

            val horayot = scans.of("הוריות")!!
            assertEquals(joined, horayot.file)
            assertEquals(150, horayot.first, "after עבודה זרה's 150 amudim")
            assertEquals(26, horayot.count)
            assertEquals(false, horayot.firstSet)
            assertEquals(0, scans.of("עבודה זרה")?.first)

            scans.setFirstPage(firstPageKey(joined, "הוריות"), 154)
            assertEquals(154, scans.of("הוריות")?.first)
            assertEquals(true, scans.of("הוריות")?.firstSet)
            assertEquals(
                "18 - עבודה זרה הוריות עדיות מורחב - מחובר.pdf" to "הוריות",
                firstPageOfKey(
                    scans.firstPages.value.keys
                        .single(),
                ),
            )
            scans.setFirstPage(firstPageKey(joined, "הוריות"), null)
            assertEquals(150, scans.of("הוריות")?.first)
            scans.setFirstPage(firstPageKey(joined, "עבודה זרה"), 2)
            assertEquals(152, scans.of("הוריות")?.first, "after the page set for עבודה זרה, the tractate before it")
        }

    private fun book(
        id: Long,
        title: String,
    ) = Book(id = id, categoryId = CatalogPresets.Ids.Categories.BAVLI_NEZIKIN, sourceId = 1, title = title)
}
