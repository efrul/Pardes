package io.github.kdroidfilter.seforimapp.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.search.SemanticPackManager
import io.github.kdroidfilter.seforimapp.android.search.SemanticPackManager.Available
import io.github.kdroidfilter.seforimapp.android.search.SemanticPackManager.Work
import io.github.kdroidfilter.seforimapp.android.search.SemanticSearchSupport
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.rememberWithNotificationPermission
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PackProblem
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases

/**
 * Port of Pardes desktop's features/settings/semantic/SemanticPackCard, in Settings › ניהול נתונים after the library's
 * location (as desktop's after LibraryLocationCard): Pardes's semantic search, its pack beside the library, downloaded
 * or imported from files ([SemanticPackManager]). Android: hidden on a device that cannot run the model
 * ([SemanticSearchSupport]); the files of an import are picked all together, as the system's picker shows no folder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SemanticPackSection() {
    if (!SemanticSearchSupport.supported) return
    val context = LocalContext.current
    val manager = remember(context) { SemanticPackManager.get(context) }
    val state by manager.state.collectAsState()
    LaunchedEffect(manager) { manager.refresh() }
    // Desktop's picker filters on json, zst, zip and the part numbers; providers label them inconsistently, so every
    // file is offered and the installer tells which it takes
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) manager.import(uris)
    }
    // Android: both go on in a foreground service with a notification, as the library's moves and updates do
    val install = rememberWithNotificationPermission { manager.install() }
    val pickFiles = rememberWithNotificationPermission { importLauncher.launch(arrayOf("*/*")) }
    val uriHandler = LocalUriHandler.current
    val c = Zayit.colors
    SettingsCard(title = SemanticPackStrings.title) {
        Text(SemanticPackStrings.description, fontSize = 13.sp, lineHeight = 19.sp, color = c.textSecondary)
        if (!state.hasLibrary) {
            Text(SemanticPackStrings.noLibrary, fontSize = 14.sp, color = c.text)
            return@SettingsCard
        }
        Text(installedText(state.installed), fontSize = 14.sp, lineHeight = 20.sp, color = c.text)
        Text(availableText(state.available, state.installed), fontSize = 13.sp, lineHeight = 19.sp, color = c.textSecondary)

        when (val work = state.work) {
            is Work.Downloading -> {
                LinearProgressIndicator(
                    progress = { (work.read.toFloat() / work.total.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = c.accent,
                    trackColor = c.divider,
                )
                Text(
                    SemanticPackStrings.downloading.format(formatBytes(work.read), formatBytes(work.total)),
                    fontSize = 13.sp,
                    color = c.textSecondary,
                )
            }
            is Work.Unpacking -> {
                LinearProgressIndicator(progress = { work.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
                Text(SemanticPackStrings.unpacking, fontSize = 13.sp, color = c.textSecondary)
            }
            Work.Ready -> InfoBanner(SemanticPackStrings.ready, success = true)
            is Work.Failed -> ErrorBanner(problemText(work.problem))
            Work.Idle -> Unit
        }
        state.startFailure?.let { failure -> InfoBanner(SemanticPackStrings.startFailed.format(failure)) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            when {
                state.busy -> OutlineButton(SemanticPackStrings.cancel, manager::cancel)
                state.work == Work.Ready -> AccentButton(SemanticPackStrings.restart, manager::restart)
                else -> {
                    offered(state.available, state.installed)?.let { pack ->
                        val label =
                            if (state.installed is InstalledPack.InUse) SemanticPackStrings.update.format(pack.pack) else SemanticPackStrings.download
                        AccentButton(label, install)
                    }
                    OutlineButton(SemanticPackStrings.import, pickFiles)
                    if (state.available is Available.Unreachable) {
                        OutlineButton(SemanticPackStrings.releasesPage, { uriHandler.openSafely(SemanticPackReleases.RELEASES_PAGE) })
                    }
                    if (state.work is Work.Failed || state.startFailure != null) OutlineButton(SemanticPackStrings.dismiss, manager::dismiss)
                }
            }
        }
        // Android: the picker gives the files picked, not their folder
        if (!state.busy && state.work != Work.Ready) Text(SemanticPackStrings.importHint, fontSize = 12.sp, lineHeight = 17.sp, color = c.textSecondary)
    }
}

/** The newest pack, when it is not the one installed. */
private fun offered(
    available: Available,
    installed: InstalledPack,
): SemanticPackReleases.Available? {
    val pack = (available as? Available.Pack)?.pack ?: return null
    return pack.takeUnless { installed is InstalledPack.InUse && installed.pack != null && installed.pack >= it.pack }
}

private fun installedText(installed: InstalledPack): String =
    when (installed) {
        InstalledPack.None -> SemanticPackStrings.notInstalled
        is InstalledPack.InUse -> {
            val lines = installed.lines?.let { "%,d".format(it) } ?: "?"
            installed.pack?.let { SemanticPackStrings.installed.format(it, lines) } ?: SemanticPackStrings.installedUnnumbered.format(lines)
        }
        InstalledPack.NoModel -> SemanticPackStrings.noModel
        InstalledPack.NoIndex -> SemanticPackStrings.noIndex
        InstalledPack.OtherModel -> SemanticPackStrings.otherModel
        is InstalledPack.Unreadable -> SemanticPackStrings.unreadable.format(installed.message)
    }

private fun availableText(
    available: Available,
    installed: InstalledPack,
): String =
    when (available) {
        Available.Checking -> SemanticPackStrings.checking
        is Available.Pack ->
            if (offered(available, installed) == null) {
                SemanticPackStrings.upToDate
            } else {
                SemanticPackStrings.available.format(available.pack.pack, formatBytes(available.pack.bundleBytes))
            }
        Available.NoneYet -> SemanticPackStrings.noneYet
        Available.Unreachable -> SemanticPackStrings.unreachable
    }

/** Why the pack was not installed, for the user. */
private fun problemText(problem: Throwable): String =
    when (problem) {
        is PackProblem.NotAPack -> SemanticPackStrings.problemNotAPack.format(isolatedName(problem.name))
        is PackProblem.Missing -> SemanticPackStrings.problemMissing.format(isolatedName(problem.name))
        is PackProblem.Corrupted -> SemanticPackStrings.problemCorrupted.format(isolatedName(problem.name))
        is PackProblem.Unusable -> SemanticPackStrings.problemUnusable.format(problem.reason)
        is PackProblem.NoSpace ->
            SemanticPackStrings.problemNoSpace.format(isolatedName(problem.dir), formatBytes(problem.needed), formatBytes(problem.free))
        else -> SemanticPackStrings.problemFailed.format(problem.message ?: problem.toString())
    }

/** A file name or path inside a Hebrew sentence, kept left to right (desktop's isolatedPath). */
private fun isolatedName(name: String) = "⁦$name⁩"
