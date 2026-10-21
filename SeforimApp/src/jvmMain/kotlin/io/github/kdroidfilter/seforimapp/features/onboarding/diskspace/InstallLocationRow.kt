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
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.kdroidfilter.seforimapp.features.settings.library.problemText
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
import java.io.File

/**
 * The folder the library will be installed to, which the user can change to another folder or drive
 * ([LibraryLocation.chooseInstallDir]); [onChange] then measures the new drive's space.
 */
@Composable
fun InstallLocationRow(
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appSettings = LocalAppGraph.current.appSettings
    var dir by remember { mutableStateOf(LibraryLocation.installDir(appSettings).path) }
    var problem by remember { mutableStateOf<LibraryLocation.Problem?>(null) }
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
                        problem = withContext(Dispatchers.IO) { LibraryLocation.chooseInstallDir(File(picked.path), appSettings) }
                        dir = LibraryLocation.installDir(appSettings).path
                        onChange()
                    }
                },
            )
        }
        problem?.let { InlineErrorBanner(text = problemText(it), modifier = Modifier.fillMaxWidth()) }
    }
}
