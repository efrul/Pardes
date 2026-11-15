package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.features.pdf.GrayPage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

// Pardes (request 56 of docs/DESKTOP_STAGE2.md): a tractate's scanned page kept as an image of one bit a pixel, as
// docs/scan-speed found its ink to keep at 2,000 pixels wide (some 140 KB a page): Skia writes no such PNG.

/** A gray page written as a PNG of one bit a pixel: black below [THRESHOLD], white from it, as a printed page's ink. */
internal object BitonalPng {
    /** The gray (of 255) from which a pixel is paper. */
    const val THRESHOLD = 128

    private val SIGNATURE = byteArrayOf(-119, 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    private const val BITS_IN_BYTE = 8
    private const val HIGH_BIT = 0x80
    private const val IHDR_LENGTH = 13

    /** [page] as a PNG: a grayscale image of one bit a pixel, its rows unfiltered and deflated. */
    fun encode(page: GrayPage): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        chunk(out, "IHDR") {
            writeInt(page.width)
            writeInt(page.height)
            // Bit depth 1, grayscale, deflate, no filter method but the standard one, not interlaced
            write(byteArrayOf(1, 0, 0, 0, 0))
        }
        chunk(out, "IDAT") { write(rows(page)) }
        chunk(out, "IEND") {}
        return out.toByteArray()
    }

    // Each row a filter byte (none) and its pixels, eight to a byte from the high bit, deflated
    private fun rows(page: GrayPage): ByteArray {
        val packed = ByteArrayOutputStream()
        val rowBytes = (page.width + BITS_IN_BYTE - 1) / BITS_IN_BYTE
        val row = ByteArray(rowBytes + 1)
        DeflaterOutputStream(packed, Deflater(Deflater.DEFAULT_COMPRESSION)).use { deflated ->
            for (y in 0 until page.height) {
                row.fill(0)
                for (x in 0 until page.width) {
                    if (page.gray(x, y) >= THRESHOLD) {
                        val i = 1 + x / BITS_IN_BYTE
                        row[i] = (row[i].toInt() or (HIGH_BIT ushr (x % BITS_IN_BYTE))).toByte()
                    }
                }
                deflated.write(row)
            }
        }
        return packed.toByteArray()
    }

    // A chunk: its length, its type, its data, and the CRC of its type and data
    private fun chunk(
        out: ByteArrayOutputStream,
        type: String,
        data: DataOutputStream.() -> Unit,
    ) {
        val body = ByteArrayOutputStream(if (type == "IHDR") IHDR_LENGTH else 0)
        DataOutputStream(body).use(data)
        val bytes = body.toByteArray()
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc =
            CRC32().apply {
                update(typeBytes)
                update(bytes)
            }
        DataOutputStream(out).apply {
            writeInt(bytes.size)
            write(typeBytes)
            write(bytes)
            writeInt(crc.value.toInt())
            flush()
        }
    }
}
