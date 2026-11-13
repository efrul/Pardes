package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.nucleusframework.application.LocalNucleusApplicationScope
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.jewel.JewelDecoratedDialog
import dev.nucleusframework.window.jewel.JewelDialogTitleBar
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_keep_add
import seforimapp.seforimapp.generated.resources.pdf_keep_beside_library
import seforimapp.seforimapp.generated.resources.pdf_keep_cancel
import seforimapp.seforimapp.generated.resources.pdf_keep_choose_folder
import seforimapp.seforimapp.generated.resources.pdf_keep_chosen_folder
import seforimapp.seforimapp.generated.resources.pdf_keep_description
import seforimapp.seforimapp.generated.resources.pdf_keep_dialog_description
import seforimapp.seforimapp.generated.resources.pdf_keep_in_place
import seforimapp.seforimapp.generated.resources.pdf_keep_in_place_hint
import seforimapp.seforimapp.generated.resources.pdf_keep_no_folder
import seforimapp.seforimapp.generated.resources.pdf_keep_question
import seforimapp.seforimapp.generated.resources.pdf_keep_title
import java.io.File

// Pardes (request 28 of docs/DESKTOP_STAGE2.md): where a PDF book the user adds is kept, asked at the first one and
// changed in Settings › ניהול נתונים (the user's decision of 2026-11-13).

/** The answer the user gave, null until they were asked. */
fun AppSettings.pdfKeep(): PdfKeepAnswer? {
    val keep = getPdfKeep()?.let { name -> PdfKeep.entries.firstOrNull { it.name == name } } ?: return null
    return PdfKeepAnswer(keep, getPdfKeepFolder()?.let(::File))
}

fun AppSettings.setPdfKeep(answer: PdfKeepAnswer) = setPdfKeep(answer.keep.name, answer.folder?.path)

/** The folder a book added is copied to as [answer] says, null when it stays in place. */
fun AppSettings.pdfFolderFor(answer: PdfKeepAnswer): File? =
    PdfBookFiles.folderFor(answer.keep, getDatabasePath()?.let(::File), answer.folder, File(FileKit.filesDir.path))

/**
 * The three answers, [answer] picked, each with what it means: beside the library (its folder shown), in place, or in a
 * folder the user chooses here. A change goes to [onAnswer].
 */
@Composable
internal fun PdfKeepChoice(
    answer: PdfKeepAnswer,
    onAnswer: (PdfKeepAnswer) -> Unit,
) {
    val appSettings = LocalAppGraph.current.appSettings
    val scope = rememberCoroutineScope()
    val info = JewelTheme.globalColors.text.info
    val besideLibrary = remember(appSettings) { appSettings.pdfFolderFor(PdfKeepAnswer(PdfKeep.BesideLibrary)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RadioButtonRow(
                text = stringResource(Res.string.pdf_keep_beside_library),
                selected = answer.keep == PdfKeep.BesideLibrary,
                onClick = { onAnswer(answer.copy(keep = PdfKeep.BesideLibrary)) },
            )
            besideLibrary?.let { Text(isolatedPath(it.path), fontSize = 12.sp, color = info, modifier = Modifier.padding(start = 28.dp)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RadioButtonRow(
                text = stringResource(Res.string.pdf_keep_in_place),
                selected = answer.keep == PdfKeep.InPlace,
                onClick = { onAnswer(answer.copy(keep = PdfKeep.InPlace)) },
            )
            Text(
                stringResource(Res.string.pdf_keep_in_place_hint),
                fontSize = 12.sp,
                color = info,
                modifier = Modifier.padding(start = 28.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RadioButtonRow(
                text = stringResource(Res.string.pdf_keep_chosen_folder),
                selected = answer.keep == PdfKeep.ChosenFolder,
                onClick = { onAnswer(answer.copy(keep = PdfKeep.ChosenFolder)) },
            )
            Row(
                modifier = Modifier.padding(start = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = {
                    scope.launch {
                        // Off the GTK event-loop thread, as the data screen's pickers (see pickDatabaseParts)
                        val directory = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() } ?: return@launch
                        onAnswer(PdfKeepAnswer(PdfKeep.ChosenFolder, File(directory.path)))
                    }
                }) {
                    Text(stringResource(Res.string.pdf_keep_choose_folder))
                }
                val folder = answer.folder
                Text(
                    if (folder != null) isolatedPath(folder.path) else stringResource(Res.string.pdf_keep_no_folder),
                    fontSize = 12.sp,
                    color = info,
                )
            }
        }
    }
}

/** Whether [answer] can be kept: a folder of one's own needs a folder. */
internal fun PdfKeepAnswer.isComplete(): Boolean = keep != PdfKeep.ChosenFolder || folder != null

/**
 * The question at the first PDF book added, in a window of its own as the app's other questions: where books added are
 * kept. [onAnswer] gets the answer, kept by the caller, and [onCancel] a window closed without one (asked again then).
 */
@Composable
internal fun PdfKeepDialog(
    onAnswer: (PdfKeepAnswer) -> Unit,
    onCancel: () -> Unit,
) {
    val title = stringResource(Res.string.pdf_keep_title)
    var answer by remember { mutableStateOf(PdfKeepAnswer(PdfKeep.BesideLibrary)) }
    with(LocalNucleusApplicationScope.current) {
        IntUiTheme(theme = ThemeUtils.buildThemeDefinition(), styling = ThemeUtils.buildComponentStyling()) {
            JewelDecoratedDialog(
                onCloseRequest = onCancel,
                title = title,
                state = rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DIALOG_SIZE),
                visible = true,
                resizable = false,
            ) {
                JewelDialogTitleBar(
                    modifier = Modifier.newFullscreenControls(),
                    gradientStartColor = ThemeUtils.titleBarGradientColor(),
                    controlButtonsDirection = ControlButtonsDirection.SystemNative,
                ) {
                    Text(title)
                }
                Column(
                    modifier =
                        Modifier
                            .trackActivation()
                            .fillMaxSize()
                            .background(JewelTheme.globalColors.panelBackground)
                            .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.pdf_keep_question), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.pdf_keep_dialog_description), fontSize = 13.sp)
                    PdfKeepChoice(answer, onAnswer = { answer = it })
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DefaultButton(onClick = { onAnswer(answer) }, enabled = answer.isComplete()) {
                            Text(stringResource(Res.string.pdf_keep_add))
                        }
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(Res.string.pdf_keep_cancel))
                        }
                    }
                }
            }
        }
    }
}

/** Settings › ניהול נתונים: where PDF books added are kept, the answer given at the first one, changed here. */
@Composable
fun PdfBooksCard() {
    val appSettings = LocalAppGraph.current.appSettings
    var answer by remember(appSettings) { mutableStateOf(appSettings.pdfKeep() ?: PdfKeepAnswer(PdfKeep.BesideLibrary)) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.pdf_keep_title), fontSize = 15.sp)
        Text(
            text = stringResource(Res.string.pdf_keep_description),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        PdfKeepChoice(answer) { changed ->
            answer = changed
            // Kept once it can be: a folder of one's own waits for its folder
            if (changed.isComplete()) appSettings.setPdfKeep(changed)
        }
    }
}

private val DIALOG_SIZE = DpSize(520.dp, 440.dp)
