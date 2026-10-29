package io.github.kdroidfilter.seforimapp.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
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
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackInstaller
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.features.settings.semantic.SemanticPackPrompt

/**
 * Port of Pardes desktop's features/settings/semantic/SemanticPackCard, in Settings › ניהול נתונים after the library's
 * location (as desktop's after LibraryLocationCard): Pardes's semantic search, its files beside the library, downloaded
 * or imported ([SemanticPackManager]). While none is in use it says how to install them, and a start opens the settings
 * on it ([SemanticPackPrompt]), marked and scrolled into view. Android: hidden on a device that cannot run the model
 * ([SemanticSearchSupport]); the files of an import are picked all together, as the system's picker shows no folder.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    // The settings opened at start for the card (SemanticPackPrompt)
    val marked = remember { SemanticPackPrompt.takeFocus() }
    val inView = remember { BringIntoViewRequester() }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    if (marked) {
        // Android: the page goes on under the navigation bar, so the card comes up above it, by the cards' gap
        val density = LocalDensity.current
        val below = WindowInsets.navigationBars.getBottom(density) + with(density) { 12.dp.toPx() }
        // Once the page is laid out, and again once the check for the files has settled the card's lines
        LaunchedEffect(state.available == Available.Checking) {
            withFrameNanos {}
            inView.bringIntoView(Rect(0f, 0f, cardSize.width.toFloat(), cardSize.height + below))
        }
    }
    SettingsCard(
        Modifier.onSizeChanged { cardSize = it }.bringIntoViewRequester(inView),
        title = SemanticPackStrings.title,
        marked = marked,
    ) {
        Text(SemanticPackStrings.description, fontSize = 13.sp, lineHeight = 19.sp, color = c.textSecondary)
        if (!state.hasLibrary) {
            Text(SemanticPackStrings.noLibrary, fontSize = 14.sp, color = c.text)
            return@SettingsCard
        }
        // No files in use: the card says how to install them, and opens at every start until they are (SemanticPackPrompt)
        val missing = state.installed !is InstalledPack.InUse
        // While the files download, unpack or wait for the restart, the releases and how to install say nothing more
        val settled = !state.busy && state.work != Work.Ready
        Text(installedText(state.installed), fontSize = 14.sp, lineHeight = 20.sp, color = c.text)
        if (settled) {
            Text(availableText(state.available, state.installed), fontSize = 13.sp, lineHeight = 19.sp, color = c.textSecondary)
            // With internet while the button is or will be there; from the zips whenever files are out
            if (missing && state.available != Available.NoneYet) HowToInstall(online = state.available != Available.Unreachable)
        }

        WorkStatus(state.work)
        // Why, in the log (PendingSemanticPack.runOnce)
        if (state.startFailure != null) InfoBanner(SemanticPackStrings.startFailed)

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
                    if (missing || state.available is Available.Unreachable) {
                        OutlineButton(SemanticPackStrings.releasesPage, { uriHandler.openSafely(releasesPage(state.available)) })
                    }
                    if (state.work is Work.Failed || state.startFailure != null) OutlineButton(SemanticPackStrings.dismiss, manager::dismiss)
                }
            }
        }
        if (settled) {
            // Android: the picker gives the files picked, not their folder
            Text(SemanticPackStrings.importHint, fontSize = 12.sp, lineHeight = 17.sp, color = c.textSecondary)
            if (missing) CheckRow(SemanticPackStrings.prompt, state.promptAtStart, manager::setPromptAtStart)
        }
    }
}

/** The download's or the unpacking's progress, files ready for the restart, or why they were not installed. */
@Composable
private fun WorkStatus(work: Work) {
    val c = Zayit.colors
    when (work) {
        is Work.Downloading -> {
            LinearProgressIndicator(
                progress = { (work.read.toFloat() / work.total.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = c.accent,
                trackColor = c.divider,
            )
            Text(SemanticPackStrings.downloading.format(formatBytes(work.read), formatBytes(work.total)), fontSize = 13.sp, color = c.textSecondary)
        }
        is Work.Unpacking -> {
            LinearProgressIndicator(progress = { work.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            Text(SemanticPackStrings.unpacking, fontSize = 13.sp, color = c.textSecondary)
        }
        Work.Ready -> InfoBanner(SemanticPackStrings.ready, success = true)
        is Work.Failed -> ErrorBanner(problemText(work.problem))
        Work.Idle -> Unit
    }
}

/** How to install the files: here with internet ([online]: the button is offered), or from their two zips. */
@Composable
private fun HowToInstall(online: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (online) HowToLine(SemanticPackStrings.onlineLabel, SemanticPackStrings.online)
        HowToLine(
            SemanticPackStrings.offlineLabel,
            SemanticPackStrings.offline.format(
                fileName(SemanticPackReleases.RELEASES_PAGE.removePrefix("https://")),
                fileName(SemanticPack.MODEL_ZIP),
                fileName(SemanticPack.INDEX_ZIP),
            ),
        )
    }
}

@Composable
private fun HowToLine(
    label: String,
    text: String,
) {
    val c = Zayit.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
            append(' ')
            append(text)
        },
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = c.text,
    )
}

/** The newest files' release, or all of them before one is known. */
private fun releasesPage(available: Available): String = (available as? Available.Pack)?.pack?.page ?: SemanticPackReleases.RELEASES_PAGE

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
        is InstalledPack.Unreadable -> SemanticPackStrings.unreadable.format(isolatedName(installed.message))
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
                val bytes = available.pack.bundleBytes
                SemanticPackStrings.available.format(available.pack.pack, formatBytes(bytes), formatBytes(SemanticPackInstaller.installBytes(bytes)))
            }
        Available.NoneYet -> SemanticPackStrings.noneYet
        Available.Unreachable -> SemanticPackStrings.unreachable
    }

/** Why the files were not installed, for the user; a reason in English stays one piece in the Hebrew line. */
private fun problemText(problem: Throwable): String =
    when (problem) {
        is PackProblem.NotAPack -> SemanticPackStrings.problemNotAPack.format(fileName(problem.name))
        is PackProblem.Missing -> SemanticPackStrings.problemMissing.format(fileName(problem.name))
        is PackProblem.Corrupted -> SemanticPackStrings.problemCorrupted.format(fileName(problem.name))
        is PackProblem.Unusable -> SemanticPackStrings.problemUnusable.format(isolatedName(problem.reason))
        is PackProblem.NoSpace -> SemanticPackStrings.problemNoSpace.format(formatBytes(problem.needed), formatBytes(problem.free))
        else -> SemanticPackStrings.problemFailed.format(isolatedName(problem.message ?: problem.toString()))
    }

/** A file name or path inside a Hebrew sentence, kept left to right (desktop's isolatedPath). */
private fun isolatedName(name: String) = "⁦$name⁩"

/** A file's name or address as one piece in a Hebrew line: left to right, and never cut at a hyphen (desktop's fileName). */
private fun fileName(name: String): String = isolatedName(name.replace("-", "-⁠"))
