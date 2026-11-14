package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.nucleusframework.pdfium.PdfReaderState
import dev.nucleusframework.pdfium.rememberPdfReaderState
import io.github.kdroidfilter.seforimapp.features.pdf.PdfPageList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.File
import kotlin.system.exitProcess

// Pardes (request 42 of docs/DESKTOP_STAGE2.md, docs/scan-speed): the time from the switch to a tractate's scan until
// its page is drawn, as the reader waits for it, in the ways the scan could be shown.

/** The scene: the cloud's screen, the view's room as the reader's at 1440 px. */
private const val SCENE_WIDTH = 1440
private const val SCENE_HEIGHT = 900

/** The page's width at the zoom's 1, as the scan's ([DafScanReader]). */
private val PAGE_WIDTH = 1100.dp

/** PdfPageList's widest drawing, in pixels. */
private const val MOST_DRAWN_PX = 1600

/** How long a switch is watched, and the text shown before it. */
private const val WATCH_MS = 2500L
private const val TEXT_MS = 700L

/** The amudim switched to, a round each: pages not drawn before in the round's own reader. */
private val PAGES = listOf(10, 13, 16, 19, 22, 25)

/** The ways the scan is shown. */
private enum class Way(
    val label: String,
) {
    // Today's DafScanReader: the reader made, the file read and opened, at the switch; PdfPageList's pages
    TODAY("today: the file opened at the switch"),
    TODAY_SPREADS("today, two pages side by side"),

    // The reader kept above the switch, its amud asked for (prefetch) while the text is shown: all the binding allows
    KEPT("the reader kept, the amud drawn ahead"),

    // An image an amud, cut beforehand: read and decoded at the switch, or decoded already while the text is shown
    CUT_READ("cut: the amud's image read at the switch"),
    CUT_READY("cut: the amud's image decoded ahead"),
}

private class Frame(
    val ms: Double,
    val ink: Int,
    val hash: Long,
)

/**
 * Times each way of showing the scan [args]`[0]` (a PDF) on the pages [PAGES], its pages cut beforehand into
 * [args]`[1]`/<page>.png at the page's width (docs/scan-speed/tools/CutPages.java), at the density [args]`[2]` (1 by
 * default): from the switch to the first frame with a quarter of the page's ink ([INK_SHARE]: a preview may count), and
 * to the frame that stays (the page as drawn at last). Each round's frames are rendered by Skia on the CPU, one every
 * [FRAME_MS] at most, and read back.
 *
 *   bash .claude/skills/pardes-desktop-run/scripts/gradle.sh -I docs/scope-search-speed/tools/bench.init.gradle \
 *     --no-configuration-cache :SeforimApp:runBench \
 *     -PbenchMain=io.github.kdroidfilter.seforimapp.features.bookcontent.daf.ScanSwitchBenchKt \
 *     -PbenchArgs="<scan.pdf> <cut dir> [density]"
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val file = File(args[0])
    val cut = File(args[1])
    val density = args.getOrNull(2)?.toFloat() ?: 1f
    println("${file.name}, ${"%.1f".format(file.length() / 1e6)} MB, density $density")
    var way by mutableStateOf(Way.TODAY)
    var page by mutableIntStateOf(PAGES.first())
    var scan by mutableStateOf(false)
    var round by mutableIntStateOf(0)
    val scene =
        ImageComposeScene(SCENE_WIDTH, SCENE_HEIGHT, Density(density), LayoutDirection.Rtl) {
            IntUiTheme(isDark = false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    when (way) {
                        Way.TODAY, Way.TODAY_SPREADS ->
                            if (scan) TodaysScan(file, page, way == Way.TODAY_SPREADS, round)
                        Way.KEPT -> KeptScan(file, page, scan)
                        Way.CUT_READ, Way.CUT_READY -> CutScan(cut, page, scan, ahead = way == Way.CUT_READY)
                    }
                    if (!scan) Text("הטקסט של המסכת")
                }
            }
        }
    // A round not counted: PDFium's and Skia's libraries loaded, the code warmed
    watch(scene, {
        way = Way.TODAY
        page = 4
        scan = false
    }) { scan = true }
    for (w in Way.entries) {
        val firsts = mutableListOf<Double>()
        val finals = mutableListOf<Double>()
        PAGES.forEachIndexed { i, p ->
            val frames =
                watch(scene, {
                    way = w
                    page = p
                    round = i
                    scan = false
                }) { scan = true }
            val last = frames.last()
            val first = frames.firstOrNull { it.ink >= last.ink * INK_SHARE }?.ms ?: Double.NaN
            val final = frames.indexOfLast { it.hash != last.hash }.let { frames.getOrNull(it + 1)?.ms ?: Double.NaN }
            firsts += first
            finals += final
        }
        println(
            "%-42s first ink %5.0f ms, final %5.0f ms   (finals: %s)"
                .format(w.label, firsts.median(), finals.median(), finals.joinToString(" ") { "%.0f".format(it) }),
        )
    }
    scene.close()
    exitProcess(0)
}

/** A frame has the page's ink once it has this much of the last frame's (a preview has). */
private const val INK_SHARE = 0.25

/** Shows the text ([before]) for [TEXT_MS], switches ([switch]) and renders frames for [WATCH_MS], each one read. */
@OptIn(ExperimentalComposeUiApi::class)
private fun watch(
    scene: ImageComposeScene,
    before: () -> Unit,
    switch: () -> Unit,
): List<Frame> {
    before()
    val shown = System.nanoTime()
    while (System.nanoTime() - shown < TEXT_MS * 1_000_000) frame(scene).close()
    switch()
    val start = System.nanoTime()
    val frames = mutableListOf<Frame>()
    while (System.nanoTime() - start < WATCH_MS * 1_000_000) {
        val image = frame(scene)
        val ms = (System.nanoTime() - start) / 1e6
        val bitmap = Bitmap.makeFromImage(image)
        frames += read(bitmap, ms)
        bitmap.close()
        image.close()
    }
    return frames
}

/** A frame of [scene], no sooner than [FRAME_MS] after the one before, as a screen's (its snapshot to close). */
@OptIn(ExperimentalComposeUiApi::class)
private fun frame(scene: ImageComposeScene): org.jetbrains.skia.Image {
    val wait = lastFrame + FRAME_MS * 1_000_000 - System.nanoTime()
    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
    lastFrame = System.nanoTime()
    return scene.render(lastFrame)
}

private var lastFrame = 0L

/** A frame every 8 ms at most: twice a 60 Hz screen's rate, so that a page drawn is seen within a frame. */
private const val FRAME_MS = 8

/** The ink and a hash of the view's middle, where the first page is. */
private fun read(
    bitmap: Bitmap,
    ms: Double,
): Frame {
    val pixels = bitmap.readPixels() ?: return Frame(ms, 0, 0)
    val row = bitmap.rowBytes
    var ink = 0
    var hash = 1L
    for (y in bitmap.height / 10 until bitmap.height - 10 step 3) {
        for (x in bitmap.width / 6 until bitmap.width * 5 / 6 step 3) {
            val luma = pixels[y * row + x * 4 + 1].toInt() and 0xFF
            if (luma < DARK) ink++
            hash = hash * 31 + luma
        }
    }
    return Frame(ms, ink, hash)
}

private const val DARK = 128

/** Today's scan ([DafScanReader]'s ScanPages and ScanList): a reader of its own, the file read and opened, its list. */
@Composable
private fun TodaysScan(
    file: File,
    page: Int,
    spreads: Boolean,
    round: Int,
) {
    val reader = rememberPdfReaderState()
    var opened by remember(round) { mutableStateOf(false) }
    LaunchedEffect(round) {
        reader.open(withContext(Dispatchers.IO) { file.readBytes() })
        opened = true
    }
    if (opened) PageList(reader, remember(round) { LazyListState(if (spreads) (page + 1) / 2 else page) }, spreads)
}

/** The scan's reader kept while the text is shown, opened once, the amud asked for ahead; its list at the switch. */
@Composable
private fun KeptScan(
    file: File,
    page: Int,
    scan: Boolean,
) {
    val reader = rememberPdfReaderState()
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        reader.open(withContext(Dispatchers.IO) { file.readBytes() })
        opened = true
    }
    if (!opened) return
    // At the width PdfPage draws it: the page's, no wider than PdfPageList's most
    val drawn = with(LocalDensity.current) { PAGE_WIDTH.roundToPx() }.coerceAtMost(MOST_DRAWN_PX)
    LaunchedEffect(page) { reader.prefetch(page, drawn) }
    if (scan) PageList(reader, remember(page) { LazyListState(page) }, spreads = false)
}

@Composable
private fun PageList(
    reader: PdfReaderState,
    listState: LazyListState,
    spreads: Boolean,
) {
    PdfPageList(
        reader = reader,
        listState = listState,
        pageWidth = if (spreads) (PAGE_WIDTH - 8.dp) / 2 else PAGE_WIDTH,
        spreads = spreads,
        selectableText = false,
        title = { "ברכות ${amudOfScanPage(it)}" },
    )
}

/**
 * The scan cut into an image an amud ([cut]/<page>.png): each page's image read and decoded off the UI thread as its
 * row comes into view, or, [ahead], the amud's decoded while the text is shown; the same list of titled pages.
 */
@Composable
private fun CutScan(
    cut: File,
    page: Int,
    scan: Boolean,
    ahead: Boolean,
) {
    val ready = remember { mutableMapOf<Int, ImageBitmap>() }
    if (ahead) {
        LaunchedEffect(page) { decodeCut(cut, page)?.let { ready[page] = it } }
    }
    if (!scan) return
    LazyColumn(
        state = remember(page) { LazyListState(page) },
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        items(PAGES.last() + 10) { p -> CutPage(cut, p, ready[p], PAGE_WIDTH) }
    }
}

@Composable
private fun CutPage(
    cut: File,
    page: Int,
    ready: ImageBitmap?,
    width: Dp,
) {
    val bitmap by produceState(ready, page) { if (value == null) value = decodeCut(cut, page) }
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().height(36.dp)) { Text("ברכות ${amudOfScanPage(page)}") }
        Box(Modifier.fillMaxWidth().aspectRatio(PAGE_ASPECT).background(Color.White)) {
            bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/** The test pages' width to height (2950 x 4490 at 300 DPI). */
private const val PAGE_ASPECT = 2950f / 4490f

/** The amud cut into [cut]/<page>.png, decoded by Skia into its gray, off the UI thread. */
private suspend fun decodeCut(
    cut: File,
    page: Int,
): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val bytes = File(cut, "$page.png").takeIf { it.isFile }?.readBytes() ?: return@withContext null
        Codec.makeFromData(Data.makeFromBytes(bytes)).use { codec ->
            val bitmap = Bitmap()
            bitmap.allocPixels(codec.imageInfo)
            codec.readPixels(bitmap)
            bitmap.setImmutable()
            bitmap.asComposeImageBitmap()
        }
    }

private fun List<Double>.median(): Double = sorted()[size / 2]
