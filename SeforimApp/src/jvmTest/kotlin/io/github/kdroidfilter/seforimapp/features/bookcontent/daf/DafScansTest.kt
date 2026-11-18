package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.testAppSettings
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The tractates' scans: in the folder the user chose, and Otzaria's, found in the library its data folder points at. */
class DafScansTest {
    private val root = createTempDirectory("daf-scans").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    // Otzaria's data folder, its pointer file (UTF-8 with a byte order mark, as Otzaria writes it) naming [library]
    private fun dataFolder(library: File): File =
        File(root, "data").apply {
            mkdirs()
            File(this, "library_path.txt").writeText(BYTE_ORDER_MARK + library.path + "\r\n")
        }

    private fun scan(path: String): File = File(root, path).apply { parentFile.mkdirs() }.apply { writeText("%PDF") }

    @Test
    fun `a tractate's scan is found in its order's folder of the library Otzaria points at`() {
        val berakhot = scan("library/אוצריא/תלמוד בבלי/סדר זרעים/ברכות.pdf")
        val shabbat = scan("library/אוצריא/תלמוד בבלי/סדר מועד/שבת.pdf")
        scan("library/אוצריא/תלמוד בבלי/סדר זרעים/ברכות.txt")

        val bavli = OtzariaScans.bavliFolders(listOf(dataFolder(File(root, "library"))))

        assertEquals(listOf(File(root, "library/אוצריא/תלמוד בבלי")), bavli)
        assertEquals(berakhot, OtzariaScans.find("ברכות", bavli))
        assertEquals(shabbat, OtzariaScans.find("שבת", bavli))
        assertNull(OtzariaScans.find("עירובין", bavli), "a tractate without a scan")
    }

    @Test
    fun `the app's own scans are beside its books database, in one folder`() {
        val berakhot = scan("db/תלמוד בבלי/ברכות.pdf")
        val data =
            dataFolder(File(root, "library")).apply {
                File(this, "library_database_path.txt").writeText(BYTE_ORDER_MARK + File(root, "db").path)
            }

        val bavli = OtzariaScans.bavliFolders(listOf(data))

        assertEquals(listOf(File(root, "db/תלמוד בבלי")), bavli)
        assertEquals(berakhot, OtzariaScans.find("ברכות", bavli))
    }

    @Test
    fun `the default library is books in the data folder`() {
        val berakhot = scan("data/books/תלמוד בבלי/ברכות.pdf")

        assertEquals(berakhot, OtzariaScans.find("ברכות", OtzariaScans.bavliFolders(listOf(File(root, "data")))))
    }

    @Test
    fun `the pointer may name the library's own אוצריא folder, and a scan may sit right in the Bavli's`() {
        val berakhot = scan("library/אוצריא/תלמוד בבלי/ברכות.pdf")

        val bavli = OtzariaScans.bavliFolders(listOf(dataFolder(File(root, "library/אוצריא"))))

        assertEquals(berakhot, OtzariaScans.find("ברכות", bavli))
    }

    @Test
    fun `a scan's page i is the amud i from 2a, and back`() {
        assertEquals(0, scanPageOf("2a"))
        assertEquals(1, scanPageOf("2b"))
        assertEquals(66, scanPageOf("35a"))
        assertEquals(124, scanPageOf("64a"), "Berakhot's last amud, the 125th page of its scan")
        assertNull(scanPageOf("1b"))
        assertNull(scanPageOf("x"))
        assertEquals("2a", amudOfScanPage(0))
        assertEquals("35b", amudOfScanPage(67))
        assertEquals("64a", amudOfScanPage(124))
    }

    @Test
    fun `without Otzaria there is no scan`() {
        assertEquals(emptyList(), OtzariaScans.bavliFolders(listOf(File(root, "nothing"))))
        assertNull(OtzariaScans.find("ברכות", emptyList()))
    }

    @Test
    fun `a folder chosen is looked in first, a scan right in it or folders down, then Otzaria's`() =
        runBlocking {
            val scans = DafScans(testAppSettings(), mockk(relaxed = true)).apply { otzariaRoots = listOf(File(root, "nothing")) }
            val mine = scan("mine/סדר נזיקין/בבא בתרא.pdf")
            scan("mine/ברכות-ישן.txt")

            assertNull(scans.of("בבא בתרא"), "no folder chosen, and no Otzaria here")
            scans.setFolder(File(root, "mine"))

            assertEquals(File(root, "mine").absolutePath, scans.folder.value)
            assertEquals(mine, scans.of("בבא בתרא")?.file)
            assertNull(scans.of("ברכות"))
            scans.setFolder(null)
            assertNull(scans.folder.value)
            assertNull(scans.of("בבא בתרא"))
        }

    @Test
    fun `a scan is found three folders down at most, the nearest first`() {
        val near = scan("mine/a/שבת.pdf")
        scan("mine/b/c/שבת.pdf")
        val deep = scan("deep/1/2/3/עירובין.pdf")
        scan("deep/1/2/3/4/פסחים.pdf")

        assertEquals(near, OtzariaScans.findIn("שבת", File(root, "mine"))?.file)
        assertEquals(deep, OtzariaScans.findIn("עירובין", File(root, "deep"))?.file)
        assertNull(OtzariaScans.findIn("פסחים", File(root, "deep")), "four folders down")
    }

    @Test
    fun `a file named otherwise is found by its tractate's words, a number before them and words after them`() {
        val joined = scan("mine/18 - עבודה זרה הוריות עדיות מורחב - מחובר.pdf")
        val berakhot = scan("mine/03 ברכות.pdf")
        val metzia = scan("mine/בבא מציעא וילנא.pdf")
        val eruvin = scan("mine/ערובין.pdf")
        scan("mine/מכות הערות.txt")
        val dir = File(root, "mine")

        assertEquals(joined, OtzariaScans.findIn("עבודה זרה", dir, TRACTATES)?.file)
        assertEquals(ScanFit.JOINED, OtzariaScans.findIn("הוריות", dir, TRACTATES)?.fit)
        assertEquals(ScanFit.EXACT, OtzariaScans.findIn("ברכות", dir, TRACTATES)?.fit)
        assertEquals(berakhot, OtzariaScans.findIn("ברכות", dir, TRACTATES)?.file)
        assertEquals(metzia, OtzariaScans.findIn("בבא מציעא", dir, TRACTATES)?.file)
        assertEquals(ScanFit.ALONE, OtzariaScans.findIn("בבא מציעא", dir, TRACTATES)?.fit)
        assertNull(OtzariaScans.findIn("בבא קמא", dir, TRACTATES), "בבא קמא is not in בבא מציעא")
        assertNull(OtzariaScans.findIn("מכות", dir, TRACTATES), "a file that is no PDF")
        assertEquals(eruvin, OtzariaScans.findIn("עירובין", dir, TRACTATES)?.file, "without yod and vav")
        assertEquals(true, OtzariaScans.findIn("עירובין", dir, TRACTATES)?.personal)
    }

    @Test
    fun `the file named the tractate comes first, then one of the tractate alone, then one of several`() {
        scan("mine/ברכות שבת.pdf")
        val alone = scan("mine/ברכות מהדורת וילנא.pdf")
        val exact = scan("mine/a/ברכות.pdf")
        val dir = File(root, "mine")

        assertEquals(exact, OtzariaScans.findIn("ברכות", dir, TRACTATES)?.file)
        exact.delete()
        assertEquals(alone, OtzariaScans.findIn("ברכות", dir, TRACTATES)?.file)
        alone.delete()
        assertEquals(ScanFit.JOINED, OtzariaScans.findIn("ברכות", dir, TRACTATES)?.fit)
    }

    @Test
    fun `no tractate is found in a file named another, with or without yod and vav`() {
        for (tractate in TRACTATES) {
            for (other in TRACTATES - tractate) {
                assertNull(ScanNames.fit("$other.pdf", tractate, TRACTATES), "$tractate in $other.pdf")
            }
            assertEquals(ScanFit.EXACT, ScanNames.fit("$tractate.pdf", tractate, TRACTATES))
        }
    }

    private companion object {
        val BYTE_ORDER_MARK = Char(0xFEFF).toString()

        // The Talmud Bavli's tractates, as the library names them
        val TRACTATES =
            (
                "ברכות,שבת,עירובין,פסחים,שקלים,יומא,סוכה,ביצה,ראש השנה,תענית,מגילה,מועד קטן,חגיגה,יבמות,כתובות,נדרים," +
                    "נזיר,סוטה,גיטין,קידושין,בבא קמא,בבא מציעא,בבא בתרא,סנהדרין,מכות,שבועות,עבודה זרה,הוריות,זבחים," +
                    "מנחות,חולין,בכורות,ערכין,תמורה,כריתות,מעילה,תמיד,נדה"
            ).split(',')
    }
}
