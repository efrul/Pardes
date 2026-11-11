package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.kdroidfilter.seforimapp.features.settings.library.problemText
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.ForeignLibraries
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.library_install_change
import seforimapp.seforimapp.generated.resources.library_install_location
import seforimapp.seforimapp.generated.resources.library_install_there
import seforimapp.seforimapp.generated.resources.onboarding_existing_library_use
import java.io.File

/**
 * The folder the library will be installed to, which the user can change to another folder or drive
 * ([LibraryLocation.chooseInstallDir]); [onChange] then measures the new drive's space. A folder that already holds a
 * library this app can open ([ForeignLibraries.inFolder]) is no place to install to, but its library is offered to be
 * used where it is, with no install and so whatever space is free; [onUseLibrary] is called once it is.
 */
@Composable
fun InstallLocationRow(
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    onUseLibrary: () -> Unit = {},
) {
    val appGraph = LocalAppGraph.current
    val appSettings = appGraph.appSettings
    var dir by remember { mutableStateOf(LibraryLocation.installDir(appSettings).path) }
    var problem by remember { mutableStateOf<LibraryLocation.Problem?>(null) }
    var libraryThere by remember { mutableStateOf<ForeignLibraries.ForeignLibrary?>(null) }
    val scope = rememberCoroutineScope()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.library_install_location, isolatedPath(dir)))
            Link(
                text = stringResource(Res.string.library_install_change),
                onClick = {
                    scope.launch {
                        // Off the GTK event-loop thread, as the settings' pickers
                        val picked = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() } ?: return@launch
                        val found = withContext(Dispatchers.IO) { ForeignLibraries.inFolder(File(picked.path)) }
                        libraryThere = (found as? ForeignLibraries.InFolder.Library)?.library
                        problem = withContext(Dispatchers.IO) { LibraryLocation.chooseInstallDir(File(picked.path), appSettings) }
                        dir = LibraryLocation.installDir(appSettings).path
                        onChange()
                    }
                },
            )
        }
        val library = libraryThere
        if (library != null) {
            // Not a problem to report: two lines, which leave the screen's banner its room
            Text(
                text = stringResource(Res.string.library_install_there, DatabaseVersionManager.formatVersionForDisplay(library.version)),
                textAlign = TextAlign.Center,
            )
            Link(
                text = stringResource(Res.string.onboarding_existing_library_use),
                onClick = {
                    ForeignLibraries.useInPlace(library, appSettings, appGraph.databasePathProvider)
                    onUseLibrary()
                },
            )
        } else {
            problem?.let { InlineErrorBanner(text = problemText(it), modifier = Modifier.fillMaxWidth()) }
        }
    }
}
