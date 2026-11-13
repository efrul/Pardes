package io.github.kdroidfilter.seforimapp.features.pdf

import dev.nucleusframework.pdfium.PdfReaderState
import dev.nucleusframework.pdfium.RenderQuality
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.OtzariaScans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * How fast PDFs open on this computer, as Pardes opens a tractate's scan or a PDF book (request 50 of
 * docs/DESKTOP_STAGE2.md). It runs only when PARDES_PDF_BENCH says what to open: `otzaria`, Otzaria's scans of the
 * Talmud found here, or files and folders of PDFs separated by the system's path separator. For each file (8 at most,
 * PARDES_PDF_BENCH_MAX, from the smallest to the largest): its size; reading it into memory; PDFium opening it; its
 * pages; its first page drawn at 1100, 1600, 2200 and 3300 pixels wide (Pardes draws a page 1600 pixels wide at most,
 * and scales it up); the next five pages at 1600 (as Pardes draws them ahead); the Java heap in use then. Written as a
 * table to build/pdf-bench/report.md, and printed. The binding's reader ([PdfReaderState]) opens and draws them, as in the
 * app; its constructor and its drawing of a page are internal to it, and reached here by their names:
 *
 *     PARDES_PDF_BENCH=otzaria ./gradlew :SeforimApp:jvmTest --tests '*PdfOpenBench*'
 */
class PdfOpenBench {
    @Test
    fun `how fast PDFs open on this computer`() {
        val spec = System.getenv(BENCH)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val most = System.getenv(BENCH_MOST)?.toIntOrNull() ?: DEFAULT_MOST
        val files = spread(filesOf(spec), most)
        check(files.isNotEmpty()) { "No PDF file for $BENCH=$spec" }
        val (warmUp, rows) =
            runBlocking {
                // The first open and draw of the process load PDFium's native library and warm the JVM up: told apart,
                // as the app pays them once
                val bytes = withContext(Dispatchers.IO) { files.first().readBytes() }
                val reader = newReader()
                val warmUp = timed { reader.open(bytes) } + timed { draw(reader, 0, WARM_UP_PX) }
                reader.dispose()
                warmUp to files.map { measure(it) }
            }
        val report = report(spec, warmUp, rows)
        File("build/pdf-bench").apply { mkdirs() }.resolve("report.md").writeText(report)
        println(report)
    }

    private class Row(
        val name: String,
        val megabytes: Double,
        val readMs: Double,
        val openMs: Double,
        val pages: Int,
        val firstMs: List<Double>,
        val nextMs: Double,
        val heapMegabytes: Long,
    )

    private suspend fun measure(file: File): Row {
        var bytes = ByteArray(0)
        val read = timed { bytes = withContext(Dispatchers.IO) { file.readBytes() } }
        val reader = newReader()
        try {
            val open = timed { reader.open(bytes) }
            reader.error?.let { error -> error("${file.name}: $error") }
            val first = WIDTHS.map { width -> timed { draw(reader, 0, width) } }
            val next = (1 until minOf(reader.pageCount, AHEAD + 1)).map { page -> timed { draw(reader, page, DRAWN_PX) } }
            val runtime = Runtime.getRuntime()
            return Row(
                name = file.name,
                megabytes = file.length() / MB,
                readMs = read,
                openMs = open,
                pages = reader.pageCount,
                firstMs = first,
                nextMs = next.average().takeIf { !it.isNaN() } ?: 0.0,
                heapMegabytes = ((runtime.totalMemory() - runtime.freeMemory()) / MB).toLong(),
            )
        } finally {
            reader.dispose()
        }
    }

    // The page [page] drawn [width] pixels wide, as tall as its proportions make it, by the reader's own drawing
    private suspend fun draw(
        reader: PdfReaderState,
        page: Int,
        width: Int,
    ) {
        val size = checkNotNull(reader.pageSize(page)) { "No size for page $page" }
        val height = (width * size.heightPoints / size.widthPoints).roundToInt()
        suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
            RENDER_PAGE.invoke(reader, page, width, height, RenderQuality.FULL, continuation)
        }
    }

    private fun report(
        spec: String,
        warmUpMs: Double,
        rows: List<Row>,
    ): String =
        buildString {
            val runtime = Runtime.getRuntime()
            appendLine("# PDF open speed")
            appendLine()
            appendLine("- $BENCH: `$spec`")
            appendLine("- ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, Java ${System.getProperty("java.version")}")
            appendLine("- ${runtime.availableProcessors()} processors, Java heap at most ${runtime.maxMemory() / MB.toLong()} MB")
            appendLine("- First open and draw of the process (PDFium's library loaded, the JVM warmed up): ${ms(warmUpMs)} ms")
            appendLine()
            appendLine(
                "| File | MB | Read ms | Open ms | Pages | To first page at 1600 ms | First page ms at " +
                    WIDTHS.joinToString(" / ") + " | Next pages at 1600, ms each | Heap MB |",
            )
            appendLine("|---|---|---|---|---|---|---|---|---|")
            for (row in rows) {
                val toFirst = row.readMs + row.openMs + row.firstMs[WIDTHS.indexOf(DRAWN_PX)]
                appendLine(
                    "| ${row.name} | ${"%.1f".format(Locale.ROOT, row.megabytes)} | ${ms(row.readMs)} | ${ms(row.openMs)} | " +
                        "${row.pages} | ${ms(toFirst)} | ${row.firstMs.joinToString(" / ") { ms(it) }} | ${ms(row.nextMs)} | " +
                        "${row.heapMegabytes} |",
                )
            }
        }

    private companion object {
        const val BENCH = "PARDES_PDF_BENCH"
        const val BENCH_MOST = "PARDES_PDF_BENCH_MAX"
        const val DEFAULT_MOST = 8
        const val AHEAD = 5
        const val DRAWN_PX = 1600
        const val WARM_UP_PX = 800
        val WIDTHS = listOf(1100, DRAWN_PX, 2200, 3300)
        const val MB = 1024.0 * 1024.0

        // A reader with its default caches, as rememberPdfReaderState makes one; its constructor is internal to the binding
        fun newReader(): PdfReaderState = PdfReaderState::class.java.getConstructor().newInstance()

        // The reader's drawing of a page, internal to the binding (its name on the JVM carries the module's)
        val RENDER_PAGE =
            PdfReaderState::class.java.getMethod(
                "renderPage\$pdfium",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                RenderQuality::class.java,
                Continuation::class.java,
            )

        // The PDFs [spec] names: Otzaria's scans found here, or the files given and those in the folders given
        fun filesOf(spec: String): List<File> {
            val roots =
                if (spec.equals(
                        "otzaria",
                        ignoreCase = true,
                    )
                ) {
                    OtzariaScans.bavliFolders()
                } else {
                    spec.split(File.pathSeparator).map(::File)
                }
            return roots
                .flatMap { root -> if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile }.toList() }
                .filter { it.extension.equals("pdf", ignoreCase = true) }
                .distinct()
        }

        // [most] of [files] from the smallest to the largest, evenly apart
        fun spread(
            files: List<File>,
            most: Int,
        ): List<File> {
            val bySize = files.sortedBy { it.length() }
            if (bySize.size <= most) return bySize
            return (0 until most).map { i -> bySize[i * (bySize.size - 1) / (most - 1).coerceAtLeast(1)] }.distinct()
        }

        suspend fun timed(block: suspend () -> Unit): Double {
            val start = System.nanoTime()
            block()
            return (System.nanoTime() - start) / NANOS_PER_MS
        }

        fun ms(value: Double): String = value.roundToInt().toString()

        const val NANOS_PER_MS = 1_000_000.0
    }
}
