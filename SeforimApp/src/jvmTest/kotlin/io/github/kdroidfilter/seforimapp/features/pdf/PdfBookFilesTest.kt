package io.github.kdroidfilter.seforimapp.features.pdf

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The user's folders of PDF books as the book tree shows them ([PdfBookFiles]), and the PDF view's zoom steps. */
class PdfBookFilesTest {
    private val root = createTempDirectory("pdf-books").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun file(path: String): File = File(root, path).apply { parentFile.mkdirs() }.apply { writeText(path) }

    @Test
    fun `a folder's PDF files by name, and its subfolders that hold some`() {
        file("ספרים/ב.pdf")
        file("ספרים/א.PDF")
        file("ספרים/הערות.txt")
        file("ספרים/.hidden.pdf")
        file("ספרים/הלכה/שולחן ערוך.pdf")
        file("ספרים/ריקה/קובץ.txt")
        file("ספרים/.git/x.pdf")

        val tree = PdfBookFiles.treeOf(File(root, "ספרים"))

        assertEquals(listOf("א.PDF", "ב.pdf"), tree?.books?.map { it.name })
        assertEquals(listOf("הלכה"), tree?.folders?.map { it.dir.name })
        assertEquals(
            listOf("שולחן ערוך.pdf"),
            tree
                ?.folders
                ?.single()
                ?.books
                ?.map { it.name },
        )
    }

    @Test
    fun `a folder with no PDF file, in it or below it, is none`() {
        file("ריקה/תת/קובץ.txt")

        assertNull(PdfBookFiles.treeOf(File(root, "ריקה")))
    }

    @Test
    fun `a file is in a folder below it, not in one whose name only begins the same`() {
        assertTrue(PdfBookFiles.isIn(File(root, "a/b/c.pdf"), File(root, "a")))
        assertTrue(PdfBookFiles.isIn(File(root, "a/c.pdf"), File(root, "a/")))
        assertFalse(PdfBookFiles.isIn(File(root, "ab/c.pdf"), File(root, "a")))
        assertFalse(PdfBookFiles.isIn(File(root, "a"), File(root, "a")))
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
