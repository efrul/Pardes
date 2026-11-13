package io.github.kdroidfilter.seforimapp.features.pdf

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where a PDF book the user adds is kept ([PdfBookFiles]): beside the library, in place, or in a folder they chose. */
class PdfBookFilesTest {
    private val root = createTempDirectory("pdf-books").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun pdf(
        dir: String,
        name: String,
        bytes: String,
    ): File = File(root, "$dir/$name").apply { parentFile.mkdirs() }.apply { writeText(bytes) }

    @Test
    fun `the folder is beside the library, the one chosen, or none in place`() {
        val database = File(root, "library/seforim.db")
        val appDir = File(root, "app")
        val chosen = File(root, "chosen")

        assertEquals(File(root, "library/my-books"), PdfBookFiles.folderFor(PdfKeep.BesideLibrary, database, chosen, appDir))
        assertEquals(File(appDir, "my-books"), PdfBookFiles.folderFor(PdfKeep.BesideLibrary, null, chosen, appDir))
        assertEquals(chosen, PdfBookFiles.folderFor(PdfKeep.ChosenFolder, database, chosen, appDir))
        assertNull(PdfBookFiles.folderFor(PdfKeep.ChosenFolder, database, null, appDir))
        assertNull(PdfBookFiles.folderFor(PdfKeep.InPlace, database, chosen, appDir))
    }

    @Test
    fun `a book is copied under its own name, and under a number when another file has it`() {
        val folder = File(root, "kept")
        val first = pdf("a", "ספר.pdf", "one")
        val second = pdf("b", "ספר.pdf", "two")

        val keptFirst = PdfBookFiles.keep(first, folder)
        val keptSecond = PdfBookFiles.keep(second, folder)

        assertEquals(File(folder, "ספר.pdf").absoluteFile, keptFirst)
        assertEquals(File(folder, "ספר-2.pdf").absoluteFile, keptSecond)
        assertEquals("two", keptSecond.readText())
        assertTrue(first.exists(), "the source stays")
        assertFalse(File(folder, "ספר.pdf.part").exists(), "no copy in progress is left")
    }

    @Test
    fun `the same file added again is the copy already kept`() {
        val folder = File(root, "kept")
        val source = pdf("a", "book.pdf", "same")

        val once = PdfBookFiles.keep(source, folder)
        val twice = PdfBookFiles.keep(source, folder)

        assertEquals(once, twice)
        assertEquals(listOf("book.pdf"), folder.list()?.toList())
    }

    @Test
    fun `a book kept in place, or already in the folder, is not copied`() {
        val source = pdf("kept", "book.pdf", "here")

        assertEquals(source.absoluteFile, PdfBookFiles.keep(source, null))
        assertEquals(source.absoluteFile, PdfBookFiles.keep(source, File(root, "kept")))
        assertEquals(listOf("book.pdf"), File(root, "kept").list()?.toList())
    }

    @Test
    fun `a book's title is its file's name`() {
        assertEquals("שולחן ערוך", PdfBookFiles.titleOf(File("/books/שולחן ערוך.pdf")))
    }

    @Test
    fun `the zoom's steps are the daf's levels`() {
        assertEquals(1.1f, zoomStep(1f, closer = false))
        assertEquals(0.9f, zoomStep(1f, closer = true))
        assertEquals(1.25f, zoomStep(1.13f, closer = false))
        assertEquals(1.1f, zoomStep(1.13f, closer = true))
        assertEquals(4f, zoomStep(4f, closer = false))
        assertEquals(0.3f, zoomStep(0.3f, closer = true))
    }
}
