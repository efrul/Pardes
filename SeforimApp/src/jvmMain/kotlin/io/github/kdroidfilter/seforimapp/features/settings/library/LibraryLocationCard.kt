package io.github.kdroidfilter.seforimapp.features.settings.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.library_location_back_default
import seforimapp.seforimapp.generated.resources.library_location_cancel
import seforimapp.seforimapp.generated.resources.library_location_copies
import seforimapp.seforimapp.generated.resources.library_location_delete_confirm
import seforimapp.seforimapp.generated.resources.library_location_delete_copy
import seforimapp.seforimapp.generated.resources.library_location_delete_failed
import seforimapp.seforimapp.generated.resources.library_location_description
import seforimapp.seforimapp.generated.resources.library_location_details
import seforimapp.seforimapp.generated.resources.library_location_folder
import seforimapp.seforimapp.generated.resources.library_location_foreign
import seforimapp.seforimapp.generated.resources.library_location_keep_copy
import seforimapp.seforimapp.generated.resources.library_location_missing
import seforimapp.seforimapp.generated.resources.library_location_move
import seforimapp.seforimapp.generated.resources.library_location_move_confirm
import seforimapp.seforimapp.generated.resources.library_location_move_to
import seforimapp.seforimapp.generated.resources.library_location_restart_note
import seforimapp.seforimapp.generated.resources.library_location_switch
import seforimapp.seforimapp.generated.resources.library_location_title
import seforimapp.seforimapp.generated.resources.library_problem_already_there
import seforimapp.seforimapp.generated.resources.library_problem_copy_mismatch
import seforimapp.seforimapp.generated.resources.library_problem_drive_missing
import seforimapp.seforimapp.generated.resources.library_problem_failed
import seforimapp.seforimapp.generated.resources.library_problem_fat32
import seforimapp.seforimapp.generated.resources.library_problem_into_itself
import seforimapp.seforimapp.generated.resources.library_problem_library_there
import seforimapp.seforimapp.generated.resources.library_problem_no_library
import seforimapp.seforimapp.generated.resources.library_problem_no_space
import seforimapp.seforimapp.generated.resources.library_problem_not_writable
import java.io.File

/** Settings › ניהול נתונים: where the library is, moving it, and the copies kept by earlier moves. */
@Composable
fun LibraryLocationCard() {
    val viewModel: LibraryLocationViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(8.dp)
    val chooseFolder: () -> Unit = {
        scope.launch {
            // Off the GTK event-loop thread, as the data screen's other pickers (see pickDatabaseParts)
            val directory = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }
            directory?.let { viewModel.choose(File(it.path)) }
        }
    }

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
        Text(text = stringResource(Res.string.library_location_title), fontSize = 15.sp)
        Text(
            text = stringResource(Res.string.library_location_description),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        state.current?.let { place -> PlaceLines(place) }
        if (!state.exists) {
            InlineErrorBanner(text = stringResource(Res.string.library_location_missing), modifier = Modifier.fillMaxWidth())
        }
        state.foreignOwner?.let { owner ->
            InlineInformationBanner(text = stringResource(Res.string.library_location_foreign, owner), modifier = Modifier.fillMaxWidth())
        }

        val proposed = state.proposed
        if (proposed == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = chooseFolder, enabled = state.exists) {
                    Text(stringResource(Res.string.library_location_move))
                }
                if (!state.isDefault && state.exists) {
                    OutlinedButton(onClick = viewModel::backToDefault) {
                        Text(stringResource(Res.string.library_location_back_default))
                    }
                }
            }
        } else {
            PathText(stringResource(Res.string.library_location_move_to, isolatedPath(proposed)))
            CheckboxRow(
                text = stringResource(Res.string.library_location_keep_copy),
                checked = state.keepCopy || state.foreignOwner != null,
                onCheckedChange = viewModel::setKeepCopy,
                enabled = state.foreignOwner == null,
            )
            state.problem?.let { InlineErrorBanner(text = problemText(it), modifier = Modifier.fillMaxWidth()) }
            Text(
                text = stringResource(Res.string.library_location_restart_note),
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(onClick = viewModel::confirmMove, enabled = state.problem == null) {
                    Text(stringResource(Res.string.library_location_move_confirm))
                }
                OutlinedButton(onClick = viewModel::cancel) {
                    Text(stringResource(Res.string.library_location_cancel))
                }
            }
        }

        if (state.copies.isNotEmpty()) {
            Text(text = stringResource(Res.string.library_location_copies), fontSize = 13.sp)
            state.copies.forEach { copy ->
                CopyRow(copy, onSwitch = { viewModel.switchTo(copy.dir) }, onDelete = { viewModel.deleteCopy(copy.dir) })
            }
        }
        if (state.notDeleted.isNotEmpty()) {
            InlineErrorBanner(
                text = stringResource(Res.string.library_location_delete_failed, state.notDeleted.joinToString { isolatedPath(it) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlaceLines(place: LibraryLocationViewModel.Place) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PathText(stringResource(Res.string.library_location_folder, isolatedPath(place.dir)))
        Text(
            text =
                stringResource(
                    Res.string.library_location_details,
                    isolatedPath(place.drive),
                    formatBytes(place.bytes),
                    formatBytes(place.freeBytes),
                ),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
    }
}

@Composable
private fun CopyRow(
    copy: LibraryLocationViewModel.Place,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PlaceLines(copy)
        if (confirmDelete) {
            InlineErrorBanner(
                text = stringResource(Res.string.library_location_delete_confirm, isolatedPath(copy.dir)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (confirmDelete) {
                DefaultButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(Res.string.library_location_delete_copy)) }
                OutlinedButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.library_location_cancel)) }
            } else {
                OutlinedButton(onClick = onSwitch) { Text(stringResource(Res.string.library_location_switch)) }
                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(Res.string.library_location_delete_copy)) }
            }
        }
    }
}

@Composable
private fun PathText(text: String) {
    Text(text = text, fontSize = 13.sp)
}

/** A path isolated left to right, so that it reads right inside a right-to-left line. */
fun isolatedPath(path: String): String = "\u2066$path\u2069"

/** Why the library cannot go where it was asked to, for the user. */
@Composable
fun problemText(problem: LibraryLocation.Problem): String =
    when (problem) {
        LibraryLocation.Problem.NoLibrary -> stringResource(Res.string.library_problem_no_library)
        LibraryLocation.Problem.AlreadyThere -> stringResource(Res.string.library_problem_already_there)
        LibraryLocation.Problem.IntoItself -> stringResource(Res.string.library_problem_into_itself)
        is LibraryLocation.Problem.LibraryThere -> stringResource(Res.string.library_problem_library_there, isolatedPath(problem.dir))
        is LibraryLocation.Problem.Fat32 -> stringResource(Res.string.library_problem_fat32, isolatedPath(problem.root))
        is LibraryLocation.Problem.DriveMissing -> stringResource(Res.string.library_problem_drive_missing, isolatedPath(problem.root))
        is LibraryLocation.Problem.NotWritable -> stringResource(Res.string.library_problem_not_writable, isolatedPath(problem.dir))
        is LibraryLocation.Problem.NoSpace ->
            stringResource(
                Res.string.library_problem_no_space,
                isolatedPath(problem.root),
                formatBytes(problem.neededBytes),
                formatBytes(problem.freeBytes),
            )
        is LibraryLocation.Problem.CopyMismatch ->
            stringResource(Res.string.library_problem_copy_mismatch, formatBytes(problem.copiedBytes), formatBytes(problem.expectedBytes))
        is LibraryLocation.Problem.Failed -> stringResource(Res.string.library_problem_failed, problem.message)
    }
