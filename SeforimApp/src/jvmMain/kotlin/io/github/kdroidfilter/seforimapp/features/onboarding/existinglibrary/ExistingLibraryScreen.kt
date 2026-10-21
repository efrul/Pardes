package io.github.kdroidfilter.seforimapp.features.onboarding.existinglibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.ForeignLibraries
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Library_books
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_existing_library_explain
import seforimapp.seforimapp.generated.resources.onboarding_existing_library_found
import seforimapp.seforimapp.generated.resources.onboarding_existing_library_install_own
import seforimapp.seforimapp.generated.resources.onboarding_existing_library_title
import seforimapp.seforimapp.generated.resources.onboarding_existing_library_use

/**
 * Shown after the licence when this app has no library yet but another app's is on this machine
 * ([ForeignLibraries.find]): use that one in place, with no download and no copy, or install a library of our own.
 */
@Composable
fun ExistingLibraryScreen(
    navController: NavController,
    progressBarState: ProgressBarState = LocalAppGraph.current.onboardingProgressBarState,
) {
    LaunchedEffect(Unit) { progressBarState.setProgress(0.2f) }
    val appGraph = LocalAppGraph.current
    val library = remember { ForeignLibraries.find() }

    if (library == null) {
        // Gone since the licence screen found it (an external drive unplugged): the usual install
        LaunchedEffect(Unit) {
            navController.navigate(OnBoardingDestination.AvailableDiskSpaceScreen) {
                popUpTo<OnBoardingDestination.ExistingLibraryScreen> { inclusive = true }
            }
        }
    } else {
        ExistingLibraryView(
            ownerName = library.ownerName,
            folder = library.database.parent.orEmpty(),
            version = DatabaseVersionManager.formatVersionForDisplay(library.version),
            onUseInPlace = {
                ForeignLibraries.useInPlace(library, appGraph.appSettings, appGraph.databasePathProvider)
                // As when the licence screen finds a library: no install, straight to the user's details
                navController.navigate(OnBoardingDestination.UserProfilScreen) {
                    popUpTo<OnBoardingDestination.ExistingLibraryScreen> { inclusive = true }
                }
            },
            onInstallOwn = { navController.navigate(OnBoardingDestination.AvailableDiskSpaceScreen) },
        )
    }
}

@Composable
private fun ExistingLibraryView(
    ownerName: String,
    folder: String,
    version: String,
    onUseInPlace: () -> Unit = {},
    onInstallOwn: () -> Unit = {},
) {
    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_existing_library_title),
        bottomAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onInstallOwn) {
                    Text(stringResource(Res.string.onboarding_existing_library_install_own))
                }
                DefaultButton(onClick = onUseInPlace) {
                    Text(stringResource(Res.string.onboarding_existing_library_use))
                }
            }
        },
    ) {
        Icon(Library_books, null, modifier = Modifier.size(72.dp), tint = JewelTheme.globalColors.text.normal)
        Text(
            text = stringResource(Res.string.onboarding_existing_library_found, ownerName, version),
            fontSize = JewelTheme.typography.h2TextStyle.fontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        // A path reads left to right: its own paragraph, whose direction comes from its text
        // A path reads left to right, also in the right-to-left layout
        Text(
            text = folder,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        Text(
            text = stringResource(Res.string.onboarding_existing_library_explain),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
    }
}

@Composable
@Preview
private fun ExistingLibraryViewPreview() {
    PreviewContainer {
        ExistingLibraryView(
            ownerName = "SeforimApp",
            folder = "C:\\Users\\user\\AppData\\Roaming\\io.github.kdroidfilter.seforimapp\\databases",
            version = DatabaseVersionManager.formatVersionForDisplay("20261011170830"),
        )
    }
}
