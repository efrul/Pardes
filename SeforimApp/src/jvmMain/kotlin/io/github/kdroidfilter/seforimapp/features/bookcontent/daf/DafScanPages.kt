package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.pdf.PdfiumCalls
import io.github.kdroidfilter.seforimapp.features.pdf.PdfiumDocument
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.math.roundToInt

// Pardes (request 56 of docs/DESKTOP_STAGE2.md): request 42 asked again on 2026-11-14, "שהמעבר בין טקסט לדף יהיה
// בשבריר שניה", and the recommendations of docs/scan-speed taken on 2026-11-15 ("מה עם ההמלצות של הצגה מהירה").

/** A scan's cut as kept beside its pages ([DafScanPages]): the file it is of, its pages, and their sizes in pixels. */
@Serializable
internal data class ScanCut(
    val scan: String,
    val size: Long,
    val modified: Long,
    val pages: Int,
    val width: Int,
    // The first page's height at [width], the height of a page not cut yet
    val height: Int,
    // Each page's height at [width] once cut, 0 before
    val heights: List<Int>,
    val format: Int = FORMAT,
) {
    /** The height of [page] in pixels at [width]: its own once cut, the first page's before. */
    fun heightOf(page: Int): Int = heights.getOrNull(page)?.takeIf { it > 0 } ?: height

    /** Whether this is the cut of [file] as it is now: the same path, length and time. */
    fun isOf(file: File): Boolean = scan == file.absolutePath && size == file.length() && modified == file.lastModified()

    companion object {
        /** The format of the cut and of its index: another one is cut again. */
        const val FORMAT = 1
    }
}

/**
 * A Talmud tractate's scans cut into an image a page, as docs/scan-speed recommends: PDFium decodes a scanned page's
 * whole picture whenever it draws it (75 to 250 ms on the computers measured), and Nucleus's view of a page waits and
 * draws a preview besides; an image cut once shows in the next frame. A scan is cut the first time it is shown
 * ([open]), in the background, a page at a time ([CUT_WIDTH] pixels wide, one bit a pixel: [BitonalPng]), the pages
 * the reader shows first ([image] asks for them); each in its scan's folder of `pardes-scans`, beside the library (with
 * its index, `cut.json`), and cut again when its file changes. The images shown are decoded off the UI thread and the
 * last ones kept in memory, the page read while the text is shown among them ([prepare]). The PDF is opened only to
 * cut, on PDFium's thread ([PdfiumCalls]), once the binding has loaded PDFium (the scan's view has it load it).
 */
@Inject
@SingleIn(AppScope::class)
class DafScanPages(
    private val appSettings: AppSettings,
    private val databasePathProvider: DatabasePathProvider,
) {
    /** The folder of the cut scans: `pardes-scans` beside the library, unless a test gives another. */
    internal var root: File? = null

    private val folders =
        ScanCutFolder {
            root ?: File(
                runCatching { File(databasePathProvider.get()).absoluteFile.parentFile }.getOrNull()
                    ?: LibraryLocation.currentDir(appSettings),
                DIR_NAME,
            )
        }

    // The cuts read or begun, by the scan's path; the pages asked for first, the last asked first
    private val cuts = HashMap<String, ScanCut>()
    private val wanted = HashMap<String, ArrayDeque<Int>>()
    private val shown =
        object : LinkedHashMap<String, ImageBitmap>(KEPT, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > KEPT
        }

    @StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var cutting: Pair<String, Job>? = null

    private val _revision = MutableStateFlow(0L)

    /** Goes up as each page is cut, for the views that wait for one: [image] answers them again. */
    internal val revision: StateFlow<Long> = _revision.asStateFlow()

    /**
     * The cut of [scan]: as kept beside its pages, or begun now (its pages and the first one's size read by PDFium), and
     * its cutting started in the background, unless it is whole. Null when PDFium cannot read it.
     */
    internal suspend fun open(scan: File): ScanCut? {
        val key = scan.absolutePath
        val kept =
            withContext(Dispatchers.IO) {
                synchronized(this@DafScanPages) { cuts[key] }?.takeIf { it.isOf(scan) }
                    ?: folders.read(scan)
            }
        val cut = kept ?: begin(scan) ?: return null
        synchronized(this) { cuts[key] = cut }
        if (cut.heights.any { it == 0 }) startCutting(scan)
        return cut
    }

    /** The page [page] of [scan] as kept in memory, at once; null when it is not. */
    internal fun shownImage(
        scan: File,
        page: Int,
    ): ImageBitmap? = synchronized(this) { shown[keyOf(scan, page)] }

    /** The page [page] of [scan] as an image, null while it is not cut: it is then cut before the others. */
    internal suspend fun image(
        scan: File,
        page: Int,
    ): ImageBitmap? {
        shownImage(scan, page)?.let { return it }
        val file = folders.page(scan, page)
        val image = withContext(Dispatchers.IO) { file.takeIf { it.isFile }?.let { decode(it) } }
        if (image == null) {
            want(scan, page)
            return null
        }
        synchronized(this) { shown[keyOf(scan, page)] = image }
        return image
    }

    /** The pages [pages] of [scan] decoded into memory when they are cut, as the reader may show them next. */
    internal suspend fun prepare(
        scan: File,
        pages: List<Int>,
    ) {
        for (page in pages.filter { shownImage(scan, it) == null }) {
            val image = withContext(Dispatchers.IO) { folders.page(scan, page).takeIf { it.isFile }?.let { decode(it) } } ?: continue
            synchronized(this) { shown[keyOf(scan, page)] = image }
        }
    }

    /** The room the cut pages take, in bytes. */
    suspend fun bytes(): Long =
        withContext(Dispatchers.IO) {
            folders
                .all()
                .walkBottomUp()
                .filter { it.isFile }
                .sumOf { it.length() }
        }

    /** Removes every cut page; a scan shown again is cut again. */
    suspend fun clear() {
        synchronized(this) {
            cutting?.second?.cancel()
            cutting = null
            cuts.clear()
            wanted.clear()
            shown.clear()
        }
        withContext(Dispatchers.IO) { folders.all().deleteRecursively() }
    }

    private fun want(
        scan: File,
        page: Int,
    ) {
        synchronized(this) {
            val queue = wanted.getOrPut(scan.absolutePath) { ArrayDeque() }
            queue.remove(page)
            queue.addFirst(page)
        }
        startCutting(scan)
    }

    // The scan's pages cut one after the other, those asked for first: one scan at a time, the one shown last
    private fun startCutting(scan: File) {
        val key = scan.absolutePath
        synchronized(this) {
            if (cutting?.first == key && cutting?.second?.isActive == true) return
            cutting?.second?.cancel()
            cutting = key to scope.launch { cutPages(scan) }
        }
    }

    private suspend fun cutPages(scan: File) {
        val doc = withContext(PdfiumCalls.dispatcher) { PdfiumDocument.open(scan) } ?: return
        // Closed and its index written even when the cut stops for another scan
        currentCoroutineContext().job.invokeOnCompletion { finish(scan, doc) }
        var last = -1
        while (true) {
            val page = nextPage(scan, last) ?: break
            val gray = withContext(PdfiumCalls.dispatcher) { doc.render(page, CUT_WIDTH) }
            if (gray == null) {
                setHeight(scan, page, FAILED)
            } else {
                val png = BitonalPng.encode(gray)
                withContext(Dispatchers.IO) { ScanCutFolder.writeAtomically(folders.page(scan, page), png) }
                setHeight(scan, page, gray.height)
                _revision.update { it + 1 }
            }
            last = page
        }
    }

    // A cut ended, done or stopped: its document closed on PDFium's thread, its index written
    private fun finish(
        scan: File,
        doc: PdfiumDocument,
    ) {
        scope.launch(PdfiumCalls.dispatcher) { doc.close() }
        scope.launch(Dispatchers.IO) { synchronized(this@DafScanPages) { cuts[scan.absolutePath] }?.let { folders.write(scan, it) } }
    }

    // The next page to cut: the last asked for, else the first not cut after the one cut last, around to the start
    private fun nextPage(
        scan: File,
        last: Int,
    ): Int? =
        synchronized(this) {
            val cut = cuts[scan.absolutePath] ?: return null
            val queue = wanted[scan.absolutePath]
            while (!queue.isNullOrEmpty()) {
                val page = queue.removeFirst()
                if (cut.heights.getOrNull(page) == 0) return page
            }
            (1..cut.pages).map { (last + it) % cut.pages }.firstOrNull { cut.heights[it] == 0 }
        }

    private suspend fun setHeight(
        scan: File,
        page: Int,
        height: Int,
    ) {
        val cut =
            synchronized(this) {
                val old = cuts[scan.absolutePath] ?: return
                old.copy(heights = old.heights.toMutableList().also { it[page] = height }).also { cuts[scan.absolutePath] = it }
            }
        // The index kept as it grows, not a page lost to a cut stopped
        if (cut.heights.count { it != 0 } % WRITE_EVERY == 0) withContext(Dispatchers.IO) { folders.write(scan, cut) }
    }

    // A scan seen the first time: its pages and the first one's size, read by PDFium; its index written
    private suspend fun begin(scan: File): ScanCut? {
        if (!scan.isFile) return null
        val first =
            withContext(PdfiumCalls.dispatcher) {
                runCatching { PdfiumDocument.open(scan)?.use { doc -> doc.pageCount to doc.size(0) } }.getOrNull()
            } ?: return null
        val (pages, size) = first
        if (pages <= 0 || size == null) return null
        val cut =
            ScanCut(
                scan = scan.absolutePath,
                size = scan.length(),
                modified = scan.lastModified(),
                pages = pages,
                width = CUT_WIDTH,
                height = (CUT_WIDTH * size.second / size.first).roundToInt(),
                heights = List(pages) { 0 },
            )
        withContext(Dispatchers.IO) {
            folders.of(scan).deleteRecursively()
            folders.write(scan, cut)
        }
        return cut
    }

    private fun keyOf(
        scan: File,
        page: Int,
    ) = "${scan.absolutePath}#$page"

    companion object {
        /** The folder of the cut scans, beside the library. */
        const val DIR_NAME = "pardes-scans"

        /** A page's width as cut, in pixels: its smallest print still read at a zoom, docs/scan-speed found. */
        const val CUT_WIDTH = 2000

        private const val FAILED = -1
        private const val WRITE_EVERY = 8
        private const val KEPT = 12
        private const val LOAD_FACTOR = 0.75f

        // A PNG decoded into gray, a byte a pixel (6 MB at 2,000 by 3,000), as Skia draws it
        internal fun decode(file: File): ImageBitmap? =
            runCatching {
                val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
                val bitmap = Bitmap()
                bitmap.allocPixels(ImageInfo(codec.width, codec.height, ColorType.GRAY_8, ColorAlphaType.OPAQUE))
                codec.readPixels(bitmap)
                bitmap.setImmutable()
                bitmap.asComposeImageBitmap()
            }.getOrNull()
    }
}
