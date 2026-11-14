package io.github.kdroidfilter.seforimapp.features.pdf

import dev.nucleusframework.pdfium.PdfReaderState
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A PDF book's own contents, its bookmarks read by PDFium ([PdfOutlines]), and as the contents pane shows them
 * ([pdfOutlineToc]).
 */
class PdfOutlineTest {
    private val root = createTempDirectory("pdf-outline").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a PDF's bookmarks, nested, with the pages they go to`() {
        val file = File(root, "ספרים/ספר עם סימניות.pdf").apply { parentFile.mkdirs() }
        file.writeBytes(pdfWithBookmarks())
        openedByTheBinding(file)

        val outline = runBlocking { PdfOutlines.read(file) }

        assertEquals(
            listOf(
                PdfOutlineEntry(
                    "פרק ראשון",
                    0,
                    listOf(PdfOutlineEntry("סעיף א", 1), PdfOutlineEntry("סעיף ב", 2)),
                ),
                PdfOutlineEntry("פרק שני", 3, listOf(PdfOutlineEntry("Remote", null))),
                PdfOutlineEntry("Web site", null),
                PdfOutlineEntry("Appendix one", 4),
            ),
            outline,
        )
    }

    @Test
    fun `a PDF without bookmarks, or a file that is none, has no contents`() {
        val plain = File(root, "plain.pdf").apply { writeBytes(pdf(pages = 2, outline = null)) }
        val broken = File(root, "broken.pdf").apply { writeText("not a PDF") }
        openedByTheBinding(plain)

        assertTrue(runBlocking { PdfOutlines.read(plain) }.isEmpty())
        assertTrue(runBlocking { PdfOutlines.read(broken) }.isEmpty())
        assertTrue(runBlocking { PdfOutlines.read(File(root, "missing.pdf")) }.isEmpty())
    }

    @Test
    fun `the bookmarks as contents, counted in the book's order, and the entry read at each page`() {
        val toc =
            pdfOutlineToc(
                bookId = 7,
                outline =
                    listOf(
                        PdfOutlineEntry("א", 0, listOf(PdfOutlineEntry("א1", 1), PdfOutlineEntry("", 3))),
                        PdfOutlineEntry("ב", 3, listOf(PdfOutlineEntry("ב1", null, listOf(PdfOutlineEntry("ב1א", 5))))),
                    ),
            ) { page -> "עמוד ${page?.plus(1)}" }

        assertEquals(listOf(1L, 4L), toc.roots.map { it.id })
        assertEquals(listOf("א1", "עמוד 4"), toc.children[1]?.map { it.text })
        assertEquals(listOf(false, true), toc.children[1]?.map { it.isLastChild })
        assertEquals(listOf(true, true), toc.roots.map { it.hasChildren })
        assertEquals(1, toc.children[4]?.single()?.level)
        assertEquals(setOf(5L, 4L), toc.pathTo(6))
        assertEquals(setOf(5L, 6L), toc.below(4))
        assertNull(toc.pageOf(5))
        // Page 3 is where both "עמוד 4" (id 3) and "ב" (id 4) start: the later one in the book is read there
        assertEquals(listOf(1L, 2L, 2L, 4L, 4L, 6L, 6L), (0..6).map { toc.entryAt(it) })
    }

    // The binding loads PDFium (and readies it) when it first opens a PDF, as a book's reader does before its contents
    // are read; its reader's constructor is internal, as for PdfOpenBench
    private fun openedByTheBinding(file: File) {
        val reader = PdfReaderState::class.java.getConstructor().newInstance()
        runBlocking { reader.open(file.readBytes()) }
        assertEquals(null, reader.error)
        reader.dispose()
    }

    // A PDF of five pages whose bookmarks go to them every way a PDF can say it: a destination (an explicit page, or a
    // named one), a GoTo action, an action to another file or to the web (no page here), and a loop back to the first
    private fun pdfWithBookmarks(): ByteArray =
        pdf(
            pages = 5,
            outline =
                listOf(
                    "<< /Type /Outlines /First 9 0 R /Last 15 0 R /Count 4 >>",
                    "<< /Title ${utf16("פרק ראשון")} /Parent 8 0 R /Next 12 0 R /First 10 0 R /Last 11 0 R /Count 2 /Dest [3 0 R /Fit] >>",
                    "<< /Title ${utf16("סעיף א")} /Parent 9 0 R /Next 11 0 R /Dest [4 0 R /XYZ 0 400 0] >>",
                    "<< /Title ${utf16("סעיף ב")} /Parent 9 0 R /Prev 10 0 R /A << /S /GoTo /D [5 0 R /Fit] >> >>",
                    "<< /Title ${utf16("פרק שני")} /Parent 8 0 R /Prev 9 0 R /Next 14 0 R /First 13 0 R /Last 13 0 R /Count 1 " +
                        "/Dest (chapter2) >>",
                    "<< /Title (Remote) /Parent 12 0 R /A << /S /GoToR /F (other.pdf) /D [1 /Fit] >> >>",
                    "<< /Title (Web site) /Parent 8 0 R /Prev 12 0 R /Next 15 0 R /A << /S /URI /URI (https://example.com) >> >>",
                    "<< /Title (  Appendix\\n   one ) /Parent 8 0 R /Prev 14 0 R /Next 9 0 R /Dest [7 0 R /Fit] >>",
                    "<< /Names [(chapter2) [6 0 R /Fit]] >>",
                ),
        )

    // A PDF of [pages] blank pages (objects 3 on), with the [outline]'s objects from 8 on: its root first, then its
    // entries, and last the leaf of the named destinations' tree
    private fun pdf(
        pages: Int,
        outline: List<String>?,
    ): ByteArray {
        val pageIds = (3 until 3 + pages)
        val catalog =
            if (outline == null) {
                "<< /Type /Catalog /Pages 2 0 R >>"
            } else {
                "<< /Type /Catalog /Pages 2 0 R /Outlines 8 0 R /Names << /Dests ${7 + outline.size} 0 R >> >>"
            }
        val objects =
            listOf(catalog, "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count $pages >>") +
                pageIds.map { "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 400] >>" } +
                List((8 - 3 - pages).coerceAtLeast(0)) { "null" }.takeIf { outline != null }.orEmpty() +
                outline.orEmpty()
        val out = StringBuilder("%PDF-1.7\n")
        val offsets =
            objects.mapIndexed { index, body ->
                out.length.also { out.append("${index + 1} 0 obj\n$body\nendobj\n") }
            }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append("%010d 00000 n \n".format(it)) }
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun utf16(text: String): String = "<FEFF${text.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) }}>"
}
