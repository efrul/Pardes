package io.github.kdroidfilter.seforimapp.features.pdf

import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_BYTE
import kotlin.math.roundToInt

// Pardes (request 56 of docs/DESKTOP_STAGE2.md): a PDF's pages drawn by PDFium into pixels of Pardes's own, as a
// tractate's scan is cut into an image a page (docs/scan-speed).

private const val BYTE = 0xFF

/** A page drawn in gray: [width] × [height] pixels, a byte each (0 black, 255 white), [stride] bytes a row. */
internal class GrayPage(
    val width: Int,
    val height: Int,
    val stride: Int,
    val pixels: ByteArray,
) {
    /** The gray of the pixel at [x], [y], from 0 (black) to 255. */
    fun gray(
        x: Int,
        y: Int,
    ): Int = pixels[y * stride + x].toInt() and BYTE
}

/**
 * A PDF opened by PDFium for Pardes ([PdfiumCalls]), which reads from its file only what it draws: its pages drawn in
 * gray at a width of pixels ([render]). Every call, [close] too, on [PdfiumCalls.dispatcher].
 */
internal class PdfiumDocument private constructor(
    private val f: PdfiumCalls.Functions,
    private val doc: MemorySegment,
) : AutoCloseable {
    /** Its pages. */
    val pageCount: Int = f.pageCount.invokeExact(doc) as Int

    /** The size of the page [index] in points, without drawing it; null when it has none. */
    fun size(index: Int): Pair<Float, Float>? {
        if (index !in 0 until pageCount) return null
        val page = f.loadPage.invokeExact(doc, index) as MemorySegment
        if (page.address() == 0L) return null
        try {
            val width = f.pageWidth.invokeExact(page) as Float
            val height = f.pageHeight.invokeExact(page) as Float
            return (width to height).takeIf { width > 0f && height > 0f }
        } finally {
            f.closePage.invokeExact(page)
        }
    }

    /** The page [index] drawn [width] pixels wide (and as high as its proportions), in gray; null when it cannot be. */
    fun render(
        index: Int,
        width: Int,
    ): GrayPage? {
        if (index !in 0 until pageCount || width <= 0) return null
        val page = f.loadPage.invokeExact(doc, index) as MemorySegment
        if (page.address() == 0L) return null
        try {
            val pageWidth = f.pageWidth.invokeExact(page) as Float
            val pageHeight = f.pageHeight.invokeExact(page) as Float
            if (pageWidth <= 0f || pageHeight <= 0f) return null
            val height = (width * pageHeight / pageWidth).roundToInt().coerceIn(1, width * MOST_TALL)
            // A row's bytes a multiple of four, as bitmaps' rows are
            val stride = (width + ALIGN - 1) / ALIGN * ALIGN
            Arena.ofConfined().use { arena ->
                val pixels = arena.allocate(stride.toLong() * height).fill(WHITE)
                val bitmap = f.bitmap.invokeExact(width, height, FORMAT_GRAY, pixels, stride) as MemorySegment
                if (bitmap.address() == 0L) return null
                try {
                    f.render.invokeExact(bitmap, page, 0, 0, width, height, 0, 0)
                } finally {
                    f.destroyBitmap.invokeExact(bitmap)
                }
                return GrayPage(width, height, stride, pixels.toArray(JAVA_BYTE))
            }
        } finally {
            f.closePage.invokeExact(page)
        }
    }

    override fun close() {
        f.close.invokeExact(doc)
    }

    companion object {
        // FPDFBitmap_Gray of fpdfview.h: a byte a pixel
        private const val FORMAT_GRAY = 1
        private const val ALIGN = 4
        private const val WHITE: Byte = -1

        // A page at most this many times as tall as it is wide: a malformed size draws no endless bitmap
        private const val MOST_TALL = 4

        /** [file] opened by PDFium, once the binding has loaded it ([rememberPdfiumLoaded]); null when it cannot be. */
        fun open(file: File): PdfiumDocument? {
            val f = PdfiumCalls.functions() ?: return null
            // Done once by the binding before it opens a PDF; skipped by PDFium then
            f.init.invokeExact()
            Arena.ofConfined().use { arena ->
                val doc = f.load.invokeExact(arena.allocateFrom(file.absolutePath), MemorySegment.NULL) as MemorySegment
                return if (doc.address() == 0L) null else PdfiumDocument(f, doc)
            }
        }
    }
}
