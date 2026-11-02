package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The layout files of a folder found by their tractate's names, an import, and the names of amudim. */
class DafLayoutsTest {
    private val fixture = File(assertNotNull(javaClass.classLoader.getResource("daf/berakhot-35b.json")).toURI())
    private val dir = Files.createTempDirectory("pardes-daf").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun findsATractateByEitherNameWhateverItsFilesName() {
        fixture.copyTo(File(dir, "anything.json"))
        val layouts = DafLayouts(dir)
        assertTrue(layouts.has("Berakhot"))
        assertTrue(layouts.has("ברכות"))
        assertTrue(layouts.has(" בְּרָכוֹת "), "nikud and spaces do not count")
        assertFalse(layouts.has("שבת"))
        assertEquals("35b", assertNotNull(layouts.tractate("ברכות")).amudim.single().amud)
    }

    @Test
    fun seesAFilePutInAfterItLooked() {
        val layouts = DafLayouts(dir)
        assertFalse(layouts.has("ברכות"))
        fixture.copyTo(File(dir, "b.json"))
        // A folder's time can be to the second: set it, as a file put in a second later would
        dir.setLastModified(dir.lastModified() + 2_000)
        assertTrue(layouts.has("ברכות"))
    }

    @Test
    fun importsUnderItsEnglishNameInPlaceOfAFormerFile() {
        fixture.copyTo(File(dir, "old.json"))
        val layouts = DafLayouts(dir)
        assertEquals("ברכות", assertNotNull(layouts.import(fixture)).tractateHe)
        assertTrue(File(dir, "Berakhot.json").isFile)
        assertFalse(File(dir, "old.json").exists(), "the former file would be found first")
        assertTrue(layouts.has("ברכות"))

        val notLayout = File(dir, "x.txt").apply { writeText("{\"a\":1}") }
        assertNull(layouts.import(notLayout))
    }

    @Test
    fun namesAmudim() {
        assertEquals("35b", amudOf("דף לה:"))
        assertEquals("35b", amudOf("ברכות לה:, א"))
        assertEquals("2a", amudOf("ב."))
        assertEquals("15a", amudOf("דף טו."))
        assertNull(amudOf("פרק ראשון"))
        assertNull(amudOf("לה"))
        assertEquals("לה:", amudTitle("35b"))
        assertEquals("טו.", amudTitle("15a"))
        assertEquals(listOf("2a", "2b", "10a", "64a"), listOf("64a", "10a", "2b", "2a").sortedBy(::amudIndex))
    }

    @Test
    fun theFirstLineOfEachAmudFromTheTableOfContents() {
        val toc =
            listOf(
                TocEntry(id = 1, bookId = 1, text = "ברכות", level = 0, lineId = 1),
                TocEntry(id = 2, bookId = 1, text = "דף ב.", level = 1, lineId = 2),
                TocEntry(id = 3, bookId = 1, text = "דף ב:", level = 1, lineId = 9),
                TocEntry(id = 4, bookId = 1, text = "דף ב:", level = 1, lineId = 12),
                TocEntry(id = 5, bookId = 1, text = "דף ג.", level = 1, lineId = null),
            )
        assertEquals(mapOf("2a" to 2L, "2b" to 9L), firstLines(toc))
    }
}
