package io.github.kdroidfilter.seforimapp.android.ui.database

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.DatabaseVersion
import io.github.kdroidfilter.seforimapp.android.data.InstallError
import io.github.kdroidfilter.seforimapp.android.data.InstallPurpose
import io.github.kdroidfilter.seforimapp.android.data.InstallState
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateEvents
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateService
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateState
import io.github.kdroidfilter.seforimapp.android.delta.DeltaStrings
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.Island
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.AndroidStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.BodyText
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.InstallOption
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.InstallerWindow
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.LocalOnboardingWide
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.MetricRow
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.OnboardingIcons
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.StepIllustration
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.StepScaffold
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.TrackInstallerVisibility
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.etaSeconds
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.findActivity
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytesPerSec
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatEta
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.rememberWithNotificationPermission
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.takeReadGrants
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.icons.CheckCircle
import io.github.kdroidfilter.seforimapp.icons.Deployed_code_update
import io.github.kdroidfilter.seforimapp.icons.Download_for_offline
import io.github.kdroidfilter.seforimapp.icons.Unarchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Why the library has to be (re)installed before the app can open. */
enum class DatabaseUpdateReason {
    /** The library is there but older than the app needs, or has no release_info.txt (desktop: version check failed). */
    Outdated,

    /** Onboarding is done but the library is gone (desktop: isDatabaseMissing). */
    Missing,

    /** The library is there but SQLite cannot open it (Android only: e.g. files pushed by adb that the app may not read). */
    Broken,

    /**
     * Android: asked for in Settings (an update from files, with no network, or a download), with a library that
     * works. The flow starts at the options, and leaving it returns to the app.
     */
    Manual,
}

/**
 * What [DatabaseUpdateScreen] should be shown for, by desktop main.kt's computeStartupState rules once onboarding is
 * done: no library → [DatabaseUpdateReason.Missing]; a library whose release_info.txt is missing or too old
 * ([DatabaseVersion]) → [DatabaseUpdateReason.Outdated]; null when the library can be opened. Blocking local I/O:
 * call it off the main thread. [DatabaseUpdateReason.Broken] is for the caller to decide when opening fails.
 */
fun databaseUpdateReason(context: Context): DatabaseUpdateReason? {
    val store = (context.applicationContext as ZayitApplication).libraryStore
    if (!store.isInstalled()) return DatabaseUpdateReason.Missing
    return if (DatabaseVersion.isCompatible(DatabaseVersion.current(store))) null else DatabaseUpdateReason.Outdated
}

/** Desktop DatabaseUpdateDestination, plus [Delta]: an incremental update from the installed release (Android). */
private enum class UpdateStep { VersionCheck, Options, Online, Offline, Delta, Completion }

/** Desktop DatabaseUpdateProgressBarState anchors. */
private const val VERSION_CHECK_STEP = 0.1f
private const val OPTIONS_SELECTED_STEP = 0.2f
private const val DOWNLOAD_START_STEP = 0.3f
private const val DOWNLOAD_COMPLETE_STEP = 0.8f

/**
 * Desktop 0.6.1 `features/database/update` (DatabaseUpdateWindow, DatabaseUpdateNavHost and its five screens) as a
 * full-screen flow: version check → update options (local file or download) → online or offline install → completion,
 * which checks the new library again. The install itself is [LibraryInstaller], shared with the onboarding: it keeps
 * running in its foreground service when the user leaves, and the flow reopens on the right step.
 *
 * [onDone] is called by "פתח את היישום" once the new library is compatible (for [DatabaseUpdateReason.Broken] also by
 * "נסה שוב", to try opening it again); [onExit] by Back on the first step, where desktop closes the window.
 *
 * When the release server has a delta from the installed release ([DbDeltaUpdateService], desktop's delta updater),
 * the options offer it first, with its size: a few MB instead of the 5.5 GB bundle. Desktop 0.6.1 has the updater but
 * does not show it in this window; its UpdateOptionsScreen keeps the two install options, as here.
 */
@Composable
fun DatabaseUpdateScreen(reason: DatabaseUpdateReason, onDone: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    val installer = remember(app) { LibraryInstaller.get(app) }
    val install by installer.state.collectAsState()
    val deltaService = remember(app) { DbDeltaUpdateService.get(app) }
    val delta by deltaService.state.collectAsState()
    var stackNames by rememberSaveable { mutableStateOf(initialStack(installer, deltaService, reason).joinToString(",") { it.name }) }
    val stack = stackNames.split(',').map { UpdateStep.valueOf(it) }
    val step = stack.last()
    fun setStack(steps: List<UpdateStep>) {
        stackNames = steps.joinToString(",") { it.name }
    }
    fun go(next: UpdateStep) {
        if (step != next) setStack(stack + next)
    }
    fun replace(next: UpdateStep) = setStack(stack.dropLast(1) + next)
    fun back() {
        if (stack.size <= 1) {
            // From Settings the title bar's back arrow leaves the flow, as Back does.
            if (reason == DatabaseUpdateReason.Manual) onExit()
            return
        }
        // Leaving a failed or finished install: forget it, so the next attempt starts clean.
        if (step == UpdateStep.Online || step == UpdateStep.Offline || step == UpdateStep.Completion) installer.reset()
        setStack(stack.dropLast(1))
    }

    // The install moves the flow on, as desktop's Online/Offline screens navigate when extraction completes. The live
    // value is read: the download is started just before its screen is pushed, ahead of the collected state.
    LaunchedEffect(install, step) {
        val current = installer.state.value
        if (step != UpdateStep.Online && step != UpdateStep.Offline) return@LaunchedEffect
        when {
            current is InstallState.Done -> replace(UpdateStep.Completion)
            // A release or file too old for this app (refused before anything was deleted): the completion step says so.
            current is InstallState.Failed && current.error == InstallError.Incompatible -> replace(UpdateStep.Completion)
            // Cancelled from the notification, or the screen restored after the process was killed: back to the options.
            current is InstallState.Idle && step == UpdateStep.Online -> setStack(stack.dropLast(1))
        }
    }

    // A finished delta goes on to the completion check, like a finished install.
    LaunchedEffect(delta.lastAppliedCount, step) {
        if (step == UpdateStep.Delta && delta.lastAppliedCount != null) replace(UpdateStep.Completion)
    }

    val busy = install.let { it is InstallState.Preparing || it is InstallState.Downloading || it is InstallState.Extracting || it is InstallState.Verifying }
    val installing = (busy && (step == UpdateStep.Online || step == UpdateStep.Offline)) || (delta.phase != null && step == UpdateStep.Delta)
    BackHandler(enabled = installing) { context.findActivity()?.moveTaskToBack(true) }
    BackHandler(enabled = !installing && stack.size > 1) {
        // A finished update opens the app, as its only button does; refused files go back, whatever delta ran before.
        val refused = installer.state.value is InstallState.Failed
        if (step == UpdateStep.Completion && (installer.state.value is InstallState.Done || (!refused && deltaService.state.value.lastAppliedCount != null))) {
            installer.reset()
            onDone()
        } else {
            back()
        }
    }
    BackHandler(enabled = !installing && stack.size == 1) { onExit() }

    TrackInstallerVisibility(installer)
    val startOnline = rememberWithNotificationPermission {
        installer.purpose = InstallPurpose.Update
        installer.startOnline()
        go(UpdateStep.Online)
    }
    val startDelta = rememberWithNotificationPermission {
        deltaService.onEvent(DbDeltaUpdateEvents.ApplyClicked)
        go(UpdateStep.Delta)
    }
    var pickedFiles by rememberSaveable { mutableIntStateOf(0) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            pickedFiles = uris.size
            // The install service reads the parts one by one, possibly after the activity is gone.
            context.takeReadGrants(uris)
            installer.purpose = InstallPurpose.Update
            installer.reset()
            installer.importUris(uris)
        }
    }

    val progress = when (step) {
        UpdateStep.VersionCheck -> 0f
        UpdateStep.Options -> VERSION_CHECK_STEP
        UpdateStep.Delta -> DOWNLOAD_START_STEP + (delta.progress ?: 0f) * (DOWNLOAD_COMPLETE_STEP - DOWNLOAD_START_STEP)
        UpdateStep.Online, UpdateStep.Offline -> when (val s = install) {
            is InstallState.Preparing -> DOWNLOAD_START_STEP
            is InstallState.Downloading -> DOWNLOAD_START_STEP + s.progress * (DOWNLOAD_COMPLETE_STEP - DOWNLOAD_START_STEP)
            is InstallState.Extracting -> DOWNLOAD_START_STEP + s.progress * (DOWNLOAD_COMPLETE_STEP - DOWNLOAD_START_STEP)
            InstallState.Verifying, InstallState.Done -> DOWNLOAD_COMPLETE_STEP
            else -> OPTIONS_SELECTED_STEP
        }
        UpdateStep.Completion -> 1f
    }

    InstallerWindow(
        title = DesktopStrings.dbUpdateTitleBar,
        icon = Deployed_code_update,
        canGoBack = (stack.size > 1 || reason == DatabaseUpdateReason.Manual) && !installing,
        onBack = ::back,
        progress = progress,
    ) {
        key(step) {
            when (step) {
                UpdateStep.VersionCheck -> VersionCheckStep(reason, onContinue = { go(UpdateStep.Options) }, onRetryOpen = onDone)
                UpdateStep.Options -> OptionsStep(
                    installer,
                    deltaService.takeIf { reason == DatabaseUpdateReason.Outdated || reason == DatabaseUpdateReason.Manual },
                    onOffline = {
                        installer.reset()
                        go(UpdateStep.Offline)
                    },
                    onOnline = startOnline,
                    onDelta = startDelta,
                )
                UpdateStep.Delta -> DeltaStep(delta, onBack = ::back, onRetry = { deltaService.onEvent(DbDeltaUpdateEvents.ApplyClicked) }, onCancel = { deltaService.onEvent(DbDeltaUpdateEvents.CancelClicked) })
                UpdateStep.Online -> OnlineStep(
                    install,
                    onBack = ::back,
                    onRetry = {
                        installer.purpose = InstallPurpose.Update
                        installer.retry()
                    },
                )
                UpdateStep.Offline -> {
                    val localBundle by produceState(emptyList<File>()) {
                        value = withContext(Dispatchers.IO) { runCatching { installer.findLocalBundle() }.getOrDefault(emptyList()) }
                    }
                    OfflineStep(
                        install,
                        pickedFiles = pickedFiles,
                        localBundleFound = localBundle.isNotEmpty(),
                        onPick = {
                            pickedFiles = 0
                            pickFiles.launch(arrayOf("*/*"))
                        },
                        onUseLocal = {
                            installer.purpose = InstallPurpose.Update
                            installer.importFiles(localBundle)
                        },
                        onBack = ::back,
                        onReset = {
                            pickedFiles = 0
                            installer.reset()
                        },
                    )
                }
                UpdateStep.Completion -> CompletionStep(
                    installer,
                    // Reached through Failed(Incompatible) too: the files were refused, whatever library is installed.
                    refused = install is InstallState.Failed,
                    onOpenApp = {
                        installer.reset()
                        onDone()
                    },
                    onTryAgain = {
                        // Desktop: back to the update options, clearing the screens in between.
                        installer.reset()
                        setStack(stack.takeWhile { it != UpdateStep.Options } + UpdateStep.Options)
                    },
                )
            }
        }
    }
}

/** Where to reopen when the screen comes back while an update install is running, or has ended. */
private fun initialStack(installer: LibraryInstaller, delta: DbDeltaUpdateService, reason: DatabaseUpdateReason): List<UpdateStep> {
    // From Settings there is no version problem to show: the options come first.
    val first = if (reason == DatabaseUpdateReason.Manual) listOf(UpdateStep.Options) else listOf(UpdateStep.VersionCheck)
    val base = if (reason == DatabaseUpdateReason.Manual) first else first + UpdateStep.Options
    if (delta.isBusy && delta.state.value.phase != DbDeltaUpdateState.Phase.CheckingForUpdates) return base + UpdateStep.Delta
    // What the onboarding's install left behind is not this flow's.
    if (installer.purpose != InstallPurpose.Update) return first
    return when (val state = installer.state.value) {
        InstallState.Idle -> first
        is InstallState.Preparing -> base + if (state.online) UpdateStep.Online else UpdateStep.Offline
        is InstallState.Downloading -> base + UpdateStep.Online
        is InstallState.Extracting -> base + if (state.online) UpdateStep.Online else UpdateStep.Offline
        InstallState.Verifying -> base + if (installer.lastRunOnline) UpdateStep.Online else UpdateStep.Offline
        InstallState.Done -> base + UpdateStep.Completion
        is InstallState.Failed -> base + if (state.online) UpdateStep.Online else UpdateStep.Offline
    }
}

/** Desktop VersionCheckScreen: the warning, the current and required versions, and "המשך". */
@Composable
private fun VersionCheckStep(reason: DatabaseUpdateReason, onContinue: () -> Unit, onRetryOpen: () -> Unit) {
    val context = LocalContext.current
    val outdated = reason == DatabaseUpdateReason.Outdated
    // null until release_info.txt has been read (off the main thread); then the version, or "" when there is none.
    val currentVersion by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) { DatabaseVersion.current((context.applicationContext as ZayitApplication).libraryStore).orEmpty() }
    }
    StepScaffold(
        title = if (outdated) DesktopStrings.dbUpdateVersionCheckTitle else DesktopStrings.dbUpdateReinstallTitle,
        bottomAction = {
            if (reason == DatabaseUpdateReason.Broken) OutlineButton(DesktopStrings.dbUpdateRetry, onRetryOpen)
            AccentButton(DesktopStrings.dbUpdateContinue, onContinue)
        },
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = warningColor(), modifier = Modifier.padding(top = 8.dp).size(if (LocalOnboardingWide.current) 96.dp else 80.dp))
        if (outdated) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val version = currentVersion
                if (version != null) {
                    BodyText(DesktopStrings.dbUpdateCurrentVersion.format(version.takeIf { it.isNotEmpty() }?.let(DatabaseVersion::formatForDisplay) ?: DesktopStrings.dbUpdateVersionUnknown))
                }
                BodyText(DesktopStrings.dbUpdateRequiredVersion.format(DatabaseVersion.formatForDisplay(DatabaseVersion.MINIMUM_REQUIRED_VERSION)))
            }
        }
        BodyText(if (outdated) DesktopStrings.dbUpdateNeedToUpdate else DesktopStrings.dbUpdateDatabaseMissingError, Modifier.fillMaxWidth(0.9f))
        if (reason == DatabaseUpdateReason.Broken) {
            // Android only: a library copied from a computer usually just needs its permissions fixed.
            InfoBanner(AndroidStrings.brokenLibraryHint, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Desktop UpdateOptionsScreen: local file (offline) and download (online), side by side on a tablet; above them, when
 * [delta] finds one from the installed release, the incremental update with its size.
 */
@Composable
private fun OptionsStep(installer: LibraryInstaller, delta: DbDeltaUpdateService?, onOffline: () -> Unit, onOnline: () -> Unit, onDelta: () -> Unit) {
    val c = Zayit.colors
    val wide = LocalOnboardingWide.current
    val metered by produceState(false) { value = withContext(Dispatchers.IO) { installer.isMeteredNetwork() } }
    val offer by produceState<DbDeltaUpdateState.Offer?>(null, delta) { value = delta?.findOffer() }
    StepScaffold(title = DesktopStrings.dbUpdateOptionsTitle) {
        offer?.let { o ->
            Island(Modifier.fillMaxWidth()) {
                InstallOption(
                    DeltaStrings.available,
                    if (o.resume) DeltaStrings.resumeDetail else DeltaStrings.availableDetail.format(formatBytes(o.bytes)),
                    if (o.resume) DeltaStrings.resume else DeltaStrings.download,
                    onDelta,
                    Modifier.fillMaxWidth(),
                    Deployed_code_update,
                )
            }
        }
        if (metered) InfoBanner(AndroidStrings.meteredWarning.format(formatBytes(LibraryInstaller.ESTIMATED_BUNDLE_BYTES)))
        val offline: @Composable (Modifier) -> Unit = { m ->
            InstallOption(DesktopStrings.installationOfflineTitle, DesktopStrings.dbUpdateLocalFileDesc, DesktopStrings.dbUpdateLocalFileButton, onOffline, m, Unarchive)
        }
        val online: @Composable (Modifier) -> Unit = { m ->
            InstallOption(DesktopStrings.installationOnlineTitle, DesktopStrings.dbUpdateDownloadDesc, DesktopStrings.dbUpdateDownloadButton, onOnline, m, Download_for_offline)
        }
        if (wide) {
            // Desktop: two columns split by a vertical rule, offline first (on the right in RTL).
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                offline(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.fillMaxHeight().width(1.dp).background(c.divider))
                online(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Island(Modifier.fillMaxWidth()) { offline(Modifier.fillMaxWidth()) }
            Island(Modifier.fillMaxWidth()) { online(Modifier.fillMaxWidth()) }
        }
    }
}

/** Desktop OnlineUpdateScreen: preparing, downloading (unpacked on the fly here), errors with "חזור" / "נסה שוב". */
@Composable
private fun OnlineStep(state: InstallState, onBack: () -> Unit, onRetry: () -> Unit) {
    val c = Zayit.colors
    StepScaffold(title = DesktopStrings.dbUpdateDownloadingTitle) {
        when (state) {
            is InstallState.Downloading -> {
                StepIllustration(Download_for_offline, wideSize = 192.dp, phoneSize = 140.dp)
                BodyText(DesktopStrings.dbUpdateDownloading)
                val total = state.totalBytes
                if (total != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricRow(OnboardingIcons.FileArrowDown, DesktopStrings.onboardingDownloadProgress.format(formatBytes(state.downloadedBytes), formatBytes(total)))
                        MetricRow(OnboardingIcons.Speed, formatBytesPerSec(state.speedBytesPerSec))
                        etaSeconds(state.downloadedBytes, total, state.speedBytesPerSec)?.let { MetricRow(OnboardingIcons.Timer, formatEta(it)) }
                    }
                }
                if (state.reconnecting) BodyText(AndroidStrings.reconnecting)
                Text(AndroidStrings.backgroundHint, color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            InstallState.Verifying, InstallState.Done -> {
                StepIllustration(Unarchive, wideSize = 192.dp, phoneSize = 140.dp)
                BodyText(DesktopStrings.dbUpdateExtracting)
                CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
            is InstallState.Failed -> InstallFailure(state, DesktopStrings.dbUpdateDownloadError, onBack, onRetry)
            // Idle (before the start) and Preparing (release lookup, removal of the old library, free-space check).
            else -> {
                CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.padding(top = 48.dp).size(48.dp))
                BodyText(DesktopStrings.dbUpdatePreparingDownload)
            }
        }
    }
}

/** The incremental update's progress ([DbDeltaUpdateService]), worded like the online step; errors with "חזור" / "נסה שוב". */
@Composable
private fun DeltaStep(state: DbDeltaUpdateState, onBack: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit) {
    val c = Zayit.colors
    StepScaffold(title = DesktopStrings.dbUpdateDownloadingTitle) {
        StepIllustration(Deployed_code_update, wideSize = 192.dp, phoneSize = 140.dp)
        val error = state.errorMessage
        when {
            error != null -> {
                BodyText(DesktopStrings.dbUpdateDownloadError, Modifier.padding(top = 8.dp), fontSize = 17)
                BodyText(error, Modifier.fillMaxWidth(0.9f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlineButton(DesktopStrings.dbUpdateBack, onBack)
                    AccentButton(DesktopStrings.dbUpdateRetry, onRetry)
                }
            }
            state.phase == null && state.offer != null -> {
                // Cancelled: the downloaded part is kept, the next start resumes it.
                BodyText(state.message, Modifier.fillMaxWidth(0.9f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlineButton(DesktopStrings.dbUpdateBack, onBack)
                    AccentButton(DeltaStrings.resume, onRetry)
                }
            }
            else -> {
                BodyText(state.message.ifEmpty { DesktopStrings.dbUpdatePreparingDownload })
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.8f), color = c.accent, trackColor = c.divider)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f), color = c.accent, trackColor = c.divider)
                }
                state.detail?.let { BodyText(it) }
                Text(DeltaStrings.backgroundHint, color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (state.cancellable) OutlineButton(DeltaStrings.cancel, onCancel)
            }
        }
    }
}

/** Desktop OfflineUpdateScreen: picking the files, then the extraction, errors with "חזור" / "נסה שוב". */
@Composable
private fun OfflineStep(
    state: InstallState,
    pickedFiles: Int,
    localBundleFound: Boolean,
    onPick: () -> Unit,
    onUseLocal: () -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    val c = Zayit.colors
    StepScaffold(title = DesktopStrings.dbUpdateOfflineTitle) {
        when (state) {
            is InstallState.Extracting -> {
                StepIllustration(Unarchive, wideSize = 192.dp, phoneSize = 140.dp)
                BodyText(DesktopStrings.dbUpdateExtracting)
                BodyText("${(state.progress * 100).toInt()}%")
                Text(AndroidStrings.backgroundHint, color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            InstallState.Verifying -> {
                StepIllustration(Unarchive, wideSize = 192.dp, phoneSize = 140.dp)
                BodyText(DesktopStrings.dbUpdateExtractionCompleted)
                CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
            InstallState.Done -> {
                BodyText(DesktopStrings.dbUpdateExtractionCompleted)
                BodyText(DesktopStrings.dbUpdateDownloadSuccessMessage)
            }
            // "נסה שוב" goes back to picking the files, as on desktop.
            is InstallState.Failed -> InstallFailure(state, DesktopStrings.dbUpdateExtractionError, onBack, onReset)
            is InstallState.Preparing -> {
                StepIllustration(Unarchive, wideSize = 192.dp, phoneSize = 140.dp)
                CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
            else -> {
                StepIllustration(Unarchive, wideSize = 192.dp, phoneSize = 140.dp)
                BodyText(DesktopStrings.dbUpdateFileSelection)
                // SAF cannot reach the files next to the one picked: every part is picked at once (Android wording).
                BodyText(AndroidStrings.selectFilesMessage, Modifier.fillMaxWidth(0.9f))
                if (pickedFiles > 0) BodyText(AndroidStrings.filesSelected.format(pickedFiles))
                if (localBundleFound) {
                    InfoBanner(AndroidStrings.localBundleFound)
                    AccentButton(AndroidStrings.useLocalBundle, onUseLocal)
                }
                AccentButton(DesktopStrings.dbUpdateChooseFiles, onPick)
            }
        }
    }
}

/**
 * The error part of desktop's Online/OfflineUpdateScreen: the cleanup, disk-space or incomplete-files message
 * (DownloadErrorKind), or [title] with the detail; then "חזור" and "נסה שוב".
 */
@Composable
private fun InstallFailure(state: InstallState.Failed, title: String, onBack: () -> Unit, onRetry: () -> Unit) {
    when (state.error) {
        InstallError.InsufficientSpace -> BodyText(DesktopStrings.dbInstallInsufficientSpace, Modifier.fillMaxWidth(0.9f).padding(top = 24.dp))
        InstallError.IncompleteBundle -> BodyText(DesktopStrings.dbInstallIncompleteBundle, Modifier.fillMaxWidth(0.9f).padding(top = 24.dp))
        InstallError.FileTooLarge -> BodyText(AndroidStrings.fileTooLarge, Modifier.fillMaxWidth(0.9f).padding(top = 24.dp))
        else -> {
            BodyText(title, Modifier.padding(top = 24.dp), fontSize = 17)
            BodyText(state.detail?.takeIf { it.isNotBlank() } ?: DesktopStrings.dbUpdateDownloadErrorUnknown, Modifier.fillMaxWidth(0.9f))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlineButton(DesktopStrings.dbUpdateBack, onBack)
        AccentButton(DesktopStrings.dbUpdateRetry, onRetry)
    }
}

/**
 * Desktop CompletionScreen: checks the library again, then success ("פתח את היישום") or "הגרסה אינה תואמת" ("נסו שוב").
 * [refused]: the install refused its files (Failed, Incompatible), so the library still there (from Settings, the working
 * one) says nothing about this update: the version error is shown without a check.
 */
@Composable
private fun CompletionStep(installer: LibraryInstaller, refused: Boolean, onOpenApp: () -> Unit, onTryAgain: () -> Unit) {
    val c = Zayit.colors
    val compatible by produceState<Boolean?>(null, refused) {
        value = if (refused) {
            false
        } else {
            withContext(Dispatchers.IO) { runCatching { installer.checkInstalledLibrary() == null }.getOrDefault(false) }
        }
    }
    when (compatible) {
        true -> StepScaffold(title = DesktopStrings.dbUpdateCompletionTitle, bottomAction = { AccentButton(DesktopStrings.onboardingOpenApp, onOpenApp) }) {
            Icon(CheckCircle, contentDescription = null, tint = c.text, modifier = Modifier.padding(top = 24.dp).size(72.dp))
            BodyText(DesktopStrings.dbUpdateSuccessTitle, fontSize = 17)
            BodyText(DesktopStrings.dbUpdateSuccessMessage, Modifier.fillMaxWidth(0.9f))
        }
        false -> StepScaffold(title = DesktopStrings.dbUpdateVersionErrorTitle, bottomAction = { AccentButton(DesktopStrings.dbUpdateTryAgain, onTryAgain) }) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = c.error, modifier = Modifier.padding(top = 24.dp).size(72.dp))
            BodyText(DesktopStrings.dbUpdateVersionErrorMessage, Modifier.fillMaxWidth(0.9f))
        }
        null -> StepScaffold(title = DesktopStrings.dbUpdateCompletionTitle) {
            CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.padding(top = 48.dp).size(40.dp))
            BodyText(DesktopStrings.dbUpdateVerifyingVersion)
        }
    }
}

/** The colour of IntelliJ's General.Warning icon, which desktop shows here. */
@Composable
private fun warningColor(): Color = if (Zayit.colors.isDark) Color(0xFFF2C55C) else Color(0xFFFFAF0F)
