package io.github.kdroidfilter.seforimapp.features.pdf

import dev.nucleusframework.pdfium.PdfReaderState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A PDF's pages drawn by PDFium into gray pixels of Pardes's own ([PdfiumDocument]), PDFium loaded by the binding. */
class PdfiumDocumentTest {
    private val root = createTempDirectory("pdfium-document").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a page drawn at a width, as tall as its proportions, its ink black and its paper white`() {
        val file = File(root, "דף.pdf").apply { writeBytes(pdfOfPages(2)) }
        loadPdfium()

        val (pages, size, page) =
            runBlocking {
                withContext(PdfiumCalls.dispatcher) {
                    PdfiumDocument.open(file)!!.use { doc -> Triple(doc.pageCount, doc.size(0), doc.render(0, 400)) }
                }
            }

        assertEquals(2, pages)
        assertEquals(200f to 300f, size)
        assertNotNull(page)
        assertEquals(400, page.width)
        assertEquals(600, page.height)
        // The box from 50 to 150 points across and 50 to 100 up, the page 300 high: rows 400 to 500 at two pixels a point
        assertTrue(page.gray(200, 450) < 64, "inside the box")
        assertTrue(page.gray(20, 20) > 200, "the paper")
    }

    @Test
    fun `a page it has not, or a file that is no PDF, draws nothing`() {
        val file = File(root, "page.pdf").apply { writeBytes(pdfOfPages(1)) }
        val broken = File(root, "broken.pdf").apply { writeText("not a PDF") }
        loadPdfium()

        runBlocking {
            withContext(PdfiumCalls.dispatcher) {
                PdfiumDocument.open(file)!!.use { doc ->
                    assertNull(doc.render(1, 400))
                    assertNull(doc.render(0, 0))
                }
                assertNull(PdfiumDocument.open(broken))
            }
        }
    }

    companion object {
        /**
         * PDFium loaded by the binding, as when a book's reader first opens a PDF ([rememberPdfiumLoaded] in the app):
         * its reader's constructor is internal, reached as PdfOpenBench reaches it.
         */
        fun loadPdfium() {
            if (PdfiumCalls.functions() != null) return
            val reader = PdfReaderState::class.java.getConstructor().newInstance()
            runBlocking { reader.open(PdfiumCalls.EMPTY_PDF) }
            reader.dispose()
        }

        /** A PDF of [count] pages of 200 by 300 points, a black box on each from 50 to 150 across and 50 to 100 up. */
        fun pdfOfPages(count: Int): ByteArray {
            val content = "0 g 50 50 100 50 re f"
            val pageIds = (0 until count).map { 3 + it * 2 }
            val objects =
                listOf(
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count $count >>",
                ) +
                    pageIds.flatMap { id ->
                        listOf(
                            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] /Contents ${id + 1} 0 R >>",
                            "<< /Length ${content.length} >>\nstream\n$content\nendstream",
                        )
                    }
            val out = StringBuilder("%PDF-1.4\n")
            val offsets = objects.mapIndexed { i, body -> out.length.also { out.append("${i + 1} 0 obj\n$body\nendobj\n") } }
            val xref = out.length
            out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { out.append("%010d 00000 n \n".format(it)) }
            out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
            return out.toString().toByteArray(Charsets.US_ASCII)
        }
    }
}
