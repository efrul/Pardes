package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.pdf.GrayPage
import io.github.kdroidfilter.seforimapp.features.pdf.PdfiumDocumentTest
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A tractate's scan cut into an image a page ([DafScanPages]), and a page kept as one bit a pixel ([BitonalPng]). */
class DafScanPagesTest {
    private val root = createTempDirectory("scan-pages").toFile()
    private val scans = File(root, "scans").apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun pages(): DafScanPages {
        PdfiumDocumentTest.loadPdfium()
        val settings = mockk<AppSettings>(relaxed = true)
        return DafScanPages(settings, DatabasePathProvider(settings)).apply { root = File(this@DafScanPagesTest.root, "pardes-scans") }
    }

    @Test
    fun `a page as a PNG of one bit a pixel, its ink black and its paper white`() {
        // Three rows of ten: white, a dark run in the middle, white
        val gray = ByteArray(3 * 12) { 255.toByte() }
        for (x in 3..6) gray[12 + x] = 20
        val png = BitonalPng.encode(GrayPage(width = 10, height = 3, stride = 12, pixels = gray))

        assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), png.copyOf(8))
        // IHDR: 10 by 3, one bit, grayscale
        assertEquals(1, png[24].toInt())
        assertEquals(0, png[25].toInt())
        val codec = Codec.makeFromData(Data.makeFromBytes(png))
        assertEquals(10 to 3, codec.width to codec.height)
    }

    @Test
    fun `a scan is cut an image a page in the background, its pages shown and asked for first`() {
        val scan = File(scans, "ברכות.pdf").apply { writeBytes(PdfiumDocumentTest.pdfOfPages(3)) }
        val pages = pages()

        val cut = runBlocking { pages.open(scan) }
        assertNotNull(cut)
        assertEquals(3, cut.pages)
        assertEquals(DafScanPages.CUT_WIDTH, cut.width)
        assertEquals(3000, cut.height, "200 by 300 points, 2,000 pixels wide")

        val image = runBlocking { withTimeout(TIMEOUT) { waitFor { pages.image(scan, 2) } } }
        assertEquals(DafScanPages.CUT_WIDTH, image.width)
        assertEquals(3000, image.height)
        runBlocking { withTimeout(TIMEOUT) { waitFor { (0..2).all { pages.image(scan, it) != null }.takeIf { it } } } }

        // Opened again, by another store as at the next start: read from its index, nothing cut again
        val again = runBlocking { pages().open(scan) }
        assertEquals(listOf(3000, 3000, 3000), again?.heights)
        val folder = File(root, "pardes-scans").listFiles()!!.single()
        assertTrue(File(folder, "cut.json").isFile)
        assertEquals(setOf("0.png", "1.png", "2.png", "cut.json"), folder.list()!!.toSet())
    }

    @Test
    fun `a scan changed is cut again, a file that is no PDF has no cut, and the pages are removed`() {
        val scan = File(scans, "שבת.pdf").apply { writeBytes(PdfiumDocumentTest.pdfOfPages(1)) }
        val pages = pages()
        runBlocking {
            pages.open(scan)
            withTimeout(TIMEOUT) { waitFor { pages.image(scan, 0) } }
        }
        scan.writeBytes(PdfiumDocumentTest.pdfOfPages(2))
        scan.setLastModified(scan.lastModified() + 2000)

        assertEquals(2, runBlocking { pages().open(scan) }?.pages)
        assertNull(runBlocking { pages.open(File(scans, "missing.pdf")) })
        assertNull(runBlocking { pages.open(File(scans, "broken.pdf").apply { writeText("not a PDF") }) })
        assertTrue(runBlocking { pages.bytes() } > 0)

        runBlocking { pages.clear() }
        assertEquals(0L, runBlocking { pages.bytes() })
        assertFalse(File(root, "pardes-scans").exists())
    }

    @Test
    fun `a cut kept whole serves without cutting, and nothing is begun for a scan not cut`() {
        val scan = File(scans, "ברכות.pdf").apply { writeBytes(PdfiumDocumentTest.pdfOfPages(2)) }
        assertNull(runBlocking { pages().whole(scan) }, "not cut: nothing begun")
        assertFalse(File(root, "pardes-scans").exists())

        val pages = pages()
        runBlocking {
            pages.open(scan)
            withTimeout(TIMEOUT) { waitFor { (0..1).all { pages.image(scan, it) != null }.takeIf { it } } }
        }
        val folder = File(root, "pardes-scans").listFiles()!!.single()
        runBlocking { withTimeout(TIMEOUT) { waitFor { pages().whole(scan) } } }
        assertEquals(listOf(3000, 3000), runBlocking { pages().whole(scan) }?.heights)
        assertTrue(File(folder, "1.png").isFile)
    }

    // Asks again as pages are cut, until there is an answer
    private suspend fun <T : Any> waitFor(answer: suspend () -> T?): T {
        while (true) {
            answer()?.let { return it }
            kotlinx.coroutines.delay(STEP)
        }
    }

    private companion object {
        const val TIMEOUT = 30_000L
        const val STEP = 50L
    }
}
