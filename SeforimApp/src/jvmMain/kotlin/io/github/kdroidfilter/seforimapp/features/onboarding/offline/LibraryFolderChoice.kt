package io.github.kdroidfilter.seforimapp.features.onboarding.offline

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
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.ForeignLibraries
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_library_folder_choose
import seforimapp.seforimapp.generated.resources.onboarding_library_folder_explain
import seforimapp.seforimapp.generated.resources.onboarding_library_folder_no_index
import seforimapp.seforimapp.generated.resources.onboarding_library_folder_none
import seforimapp.seforimapp.generated.resources.onboarding_library_folder_old
import seforimapp.seforimapp.generated.resources.onboarding_library_folder_pieces
import seforimapp.seforimapp.generated.resources.onboarding_library_folder_question
import seforimapp.seforimapp.generated.resources.onboarding_library_folder_unknown
import java.io.File

/**
 * The offline install's other way, under its files' button: a folder that already holds a library (brought from
 * another computer, on an external drive, another app's) is used where it is, as an installed Zayit's is
 * ([ForeignLibraries.useInPlace]): nothing is extracted or copied, and the folder is never this app's to delete. A
 * folder that holds no library this app can open says why ([ForeignLibraries.inFolder]). Two lines, a question with
 * its link and a note, which is all the room the screen has left under its button.
 */
@Composable
fun LibraryFolderChoice(navController: NavController) {
    val appGraph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<ForeignLibraries.InFolder?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.onboarding_library_folder_question))
            Link(
                text = stringResource(Res.string.onboarding_library_folder_choose),
                onClick = {
                    scope.launch {
                        // Off the GTK event-loop thread, as the files' picker (see pickDatabaseParts)
                        val folder = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }?.path ?: return@launch
                        val inFolder = withContext(Dispatchers.IO) { ForeignLibraries.inFolder(File(folder)) }
                        found = inFolder
                        if (inFolder is ForeignLibraries.InFolder.Library) {
                            ForeignLibraries.useInPlace(inFolder.library, appGraph.appSettings, appGraph.databasePathProvider)
                            // As when another app's library is used in place: no install, straight to the user's details
                            navController.navigate(OnBoardingDestination.UserProfilScreen) {
                                popUpTo<OnBoardingDestination.OfflineFileSelectionScreen> { inclusive = true }
                            }
                        }
                    }
                },
            )
        }
        val problem = problemOf(found)
        Text(
            text = problem ?: stringResource(Res.string.onboarding_library_folder_explain),
            textAlign = TextAlign.Center,
            color = if (problem != null) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
    }
}

/** Why the folder chosen is not used, in the reader's words; null for none chosen yet, and for a library. */
@Composable
private fun problemOf(found: ForeignLibraries.InFolder?): String? =
    when (found) {
        null, is ForeignLibraries.InFolder.Library -> null
        ForeignLibraries.InFolder.NoLibrary -> stringResource(Res.string.onboarding_library_folder_none)
        ForeignLibraries.InFolder.InPieces -> stringResource(Res.string.onboarding_library_folder_pieces)
        ForeignLibraries.InFolder.NoSearchIndex -> stringResource(Res.string.onboarding_library_folder_no_index)
        ForeignLibraries.InFolder.UnknownRelease -> stringResource(Res.string.onboarding_library_folder_unknown)
        is ForeignLibraries.InFolder.TooOld ->
            stringResource(Res.string.onboarding_library_folder_old, DatabaseVersionManager.formatVersionForDisplay(found.version))
    }
