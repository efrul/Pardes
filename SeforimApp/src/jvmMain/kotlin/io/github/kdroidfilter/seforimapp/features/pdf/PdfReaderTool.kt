package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.nucleusframework.pdfium.PdfPage
import dev.nucleusframework.pdfium.rememberPdfReaderState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_experiment_failed
import seforimapp.seforimapp.generated.resources.pdf_experiment_hint
import seforimapp.seforimapp.generated.resources.pdf_experiment_open
import seforimapp.seforimapp.generated.resources.pdf_experiment_status
import seforimapp.seforimapp.generated.resources.pdf_experiment_title
import seforimapp.seforimapp.generated.resources.pdf_experiment_zoom_in
import seforimapp.seforimapp.generated.resources.pdf_experiment_zoom_out
import java.io.File

// Pardes (request 28 of docs/DESKTOP_STAGE2.md): reading a PDF file, an experiment behind a hidden switch, as the user
// decided on 2026-11-12. Nucleus's PDFium binding (dev.nucleusframework:pdfium-jvm) draws the file in a tool window of
// its own, from the Tools menu, which lists it only while the switch is on. Its questions: whether it draws on Pardes's
// Tao window and in the native image, what a large scan costs in memory, and whether its text is copied. The first
// version (a tab, the user's books in the library's tree) waits for the answers.

/**
 * The experiment's hidden switch: on when the environment variable PARDES_EXPERIMENTS, or the system property
 * pardes.experiments, lists "pdf" (comma-separated). Off for everyone else, testers included: no setting shows it.
 */
internal object PdfExperiment {
    val enabled: Boolean by lazy { listsPdf(System.getenv("PARDES_EXPERIMENTS"), System.getProperty("pardes.experiments")) }

    /** Whether one of the lists of experiments [lists] (comma-separated, null when unset) names the PDF reader. */
    fun listsPdf(vararg lists: String?): Boolean =
        lists
            .filterNotNull()
            .flatMap { it.split(',') }
            .any { it.trim().equals("pdf", ignoreCase = true) }
}

/** A PDF file picked from the disk, drawn page under page: the experiment's tool (see [PdfExperiment]). */
internal object PdfReaderTool : HomeWidget {
    override val id = "pdf_reader"
    override val title = Res.string.pdf_experiment_title
    override val defaultSpan = CellSpan(8, 8)
    override val toolWindowSize = DpSize(820.dp, 900.dp)
    override val toolWindowMinSize = DpSize(420.dp, 400.dp)
    override val toolSymbol = "doc.richtext"
    override val isSupported: Boolean get() = PdfExperiment.enabled

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = PdfExperimentView(modifier)
}

/** A file opened: its name, size in megabytes and the time it took, read and opened, in milliseconds. */
private data class OpenedPdf(
    val name: String,
    val megabytes: Long,
    val millis: Long,
)

private const val MEGABYTE = 1024L * 1024L
private const val ZOOM_STEP = 0.25f
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4f

@Composable
private fun PdfExperimentView(modifier: Modifier = Modifier) {
    val reader = rememberPdfReaderState()
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf<OpenedPdf?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    // Off the event-loop thread, as the data screen's pickers
                    val picked =
                        withContext(Dispatchers.IO) { FileKit.openFilePicker(type = FileKitType.File(extensions = setOf("pdf"))) }
                            ?: return@launch
                    val file = File(picked.path)
                    val started = System.nanoTime()
                    failure = null
                    // The binding reads the whole file into memory; a missing native library is an Error, caught too
                    runSuspendCatching { reader.open(withContext(Dispatchers.IO) { file.readBytes() }) }
                        .onSuccess { opened = OpenedPdf(file.name, file.length() / MEGABYTE, (System.nanoTime() - started) / 1_000_000) }
                        .onFailure { failure = it.message ?: it::class.simpleName }
                }
            }) {
                Text(stringResource(Res.string.pdf_experiment_open))
            }
            IconButton(onClick = { zoom = (zoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM) }) {
                Icon(AllIconsKeys.General.ZoomOut, contentDescription = stringResource(Res.string.pdf_experiment_zoom_out))
            }
            IconButton(onClick = { zoom = (zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM) }) {
                Icon(AllIconsKeys.General.ZoomIn, contentDescription = stringResource(Res.string.pdf_experiment_zoom_in))
            }
            opened?.let {
                val runtime = Runtime.getRuntime()
                val used = (runtime.totalMemory() - runtime.freeMemory()) / MEGABYTE
                Text(
                    stringResource(Res.string.pdf_experiment_status, it.name, reader.pageCount, it.megabytes, it.millis, used),
                    color = JewelTheme.globalColors.text.info,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val error = failure ?: reader.error?.message
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val pagesWidth = maxWidth * zoom
            when {
                error != null -> Text(stringResource(Res.string.pdf_experiment_failed, error), Modifier.padding(8.dp))
                opened == null -> Text(stringResource(Res.string.pdf_experiment_hint), Modifier.padding(8.dp))
                // The pages one under the other, as the binding's PdfReader lays them, but with their text selectable,
                // which its PdfReader leaves off, and as wide as the zoom asks, scrolled sideways past the window's width
                else ->
                    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        LazyColumn(Modifier.width(pagesWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(reader.pageCount) { index ->
                                PdfPage(reader, index, Modifier.fillMaxWidth(), selectableText = true)
                            }
                        }
                    }
            }
        }
    }
}
