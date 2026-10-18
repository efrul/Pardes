package io.github.kdroidfilter.seforimapp.android.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.InstallState
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.ui.components.IslandShape
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.shell.SystemBarsOver
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/** Width from which the flow is shown as a centered card, like the desktop setup window. */
private val WideBreakpoint = 840.dp

/**
 * First-run setup, mirroring desktop Zayit's onboarding window: welcome → licence → disk space → install
 * type → download (or offline file selection) → extraction → personal details → region → finish.
 *
 * [onFinished] is called by "פתח את היישום"; MainActivity then shows the app if the library is installed.
 */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(app))
    val stack by vm.stack.collectAsState()
    val install by vm.installer.state.collectAsState()
    val step = stack.last()

    val installing = step in OnboardingViewModel.InstallSteps && install.isBusy
    val canGoBack = stack.size > 1 && !installing
    // While the library installs, back leaves the app (the download goes on in the background); otherwise it
    // returns to the previous step, as the arrow in the desktop title bar does.
    BackHandler(enabled = installing) { context.findActivity()?.moveTaskToBack(true) }
    BackHandler(enabled = canGoBack) { vm.back() }

    TrackInstallerVisibility(vm.installer)
    val startOnline = rememberWithNotificationPermission(vm::startOnline)
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        // The install service reads the parts one by one, possibly after the activity is gone.
        context.takeReadGrants(uris)
        vm.importUris(uris)
    }

    val progress = when (step) {
        OnboardingStep.Download -> {
            val downloaded = when (val state = install) {
                is InstallState.Downloading -> state.progress
                InstallState.Verifying, InstallState.Done -> 1f
                else -> 0f
            }
            // Online, unpacking happens during the download: the bar runs from the download to the profile anchor.
            OnboardingStep.Download.anchor + (OnboardingStep.Profile.anchor - OnboardingStep.Download.anchor) * downloaded
        }
        OnboardingStep.Extract -> when (val state = install) {
            is InstallState.Extracting -> OnboardingStep.Extract.anchor + (OnboardingStep.Profile.anchor - OnboardingStep.Extract.anchor) * state.progress
            InstallState.Verifying, InstallState.Done -> OnboardingStep.Profile.anchor
            else -> OnboardingStep.Extract.anchor
        }
        else -> step.anchor
    }

    val content: @Composable () -> Unit = {
        key(step) {
            when (step) {
                OnboardingStep.Init -> InitStep(onNext = { vm.go(OnboardingStep.Licence) })
                OnboardingStep.Licence -> {
                    val accepted by vm.licenceAccepted.collectAsState()
                    LicenceStep(accepted, vm::setLicenceAccepted, vm::licenceNext)
                }
                OnboardingStep.DiskSpace -> {
                    val disk by vm.disk.collectAsState()
                    DiskSpaceStep(disk, onRecheck = vm::refreshDisk, onNext = { vm.go(OnboardingStep.InstallType) }, onSelectLocation = vm::selectLocation)
                }
                OnboardingStep.InstallType -> {
                    val device by vm.device.collectAsState()
                    val disk by vm.disk.collectAsState()
                    InstallTypeStep(
                        device,
                        onlineAllowed = disk.enough,
                        onOffline = { vm.go(OnboardingStep.OfflineFiles) },
                        onOnline = startOnline,
                        onUseExisting = vm::useExistingLibrary,
                    )
                }
                OnboardingStep.Download -> {
                    // Desktop starts the download on entering the screen.
                    LaunchedEffect(Unit) { if (vm.installer.state.value == InstallState.Idle) vm.startOnline() }
                    DownloadStep(install, onRetry = vm::retry, onCancel = vm::cancelInstall)
                }
                OnboardingStep.OfflineFiles -> {
                    val device by vm.device.collectAsState()
                    OfflineFilesStep(
                        install,
                        device,
                        onPick = { pickFiles.launch(arrayOf("*/*")) },
                        onUseLocal = vm::importLocalBundle,
                    )
                }
                OnboardingStep.Extract -> ExtractStep(install, onRetry = vm::retry, onRestart = vm::restartInstallation)
                OnboardingStep.Profile -> {
                    val profile by vm.profile.collectAsState()
                    ProfileStep(profile, vm::setFirstName, vm::setLastName, vm::setCommunity, vm::profileNext)
                }
                OnboardingStep.Region -> {
                    val region by vm.region.collectAsState()
                    RegionStep(region, vm.countries, vm.citiesOf(region.countryIndex), vm::setCountry, vm::setCity, vm::regionNext)
                }
                OnboardingStep.Finish -> FinishStep(onOpenApp = onFinished)
            }
        }
    }

    InstallerWindow(DesktopStrings.onboardingTitleBar, OnboardingIcons.InstallDesktop, canGoBack, onBack = { vm.back() }, progress, content)
}

/**
 * Desktop InstallerWindow (core/presentation/components), shared by the onboarding and the database update: on
 * phones full screen, from [WideBreakpoint] the desktop setup window centered on the ground; the title bar and the
 * step progress bar on top.
 */
@Composable
internal fun InstallerWindow(
    title: String,
    icon: ImageVector,
    canGoBack: Boolean,
    onBack: () -> Unit,
    progress: Float,
    content: @Composable () -> Unit,
) {
    val c = Zayit.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(c.ground)) {
        val wide = maxWidth >= WideBreakpoint && maxHeight >= 520.dp
        // Phone: the title bar under the status bar (light gold in the light theme), the panel under the navigation
        // bar; tablet: the ground around the setup window
        if (wide) SystemBarsOver(status = c.ground, navigation = c.ground) else SystemBarsOver(status = c.titleBarTone, navigation = c.panel)
        CompositionLocalProvider(LocalOnboardingWide provides wide) {
            if (wide) {
                // Tablet: the desktop setup window, centered on the window ground.
                Box(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 820.dp)
                            .fillMaxWidth()
                            .heightIn(max = 620.dp)
                            .fillMaxHeight()
                            .shadow(12.dp, IslandShape)
                            .clip(IslandShape)
                            .background(c.panel)
                            .border(1.dp, c.panelBorder, IslandShape),
                    ) {
                        SetupTitleBar(title, icon, canGoBack, onBack, insetStatusBar = false)
                        StepProgressBar(progress)
                        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) { content() }
                    }
                }
            } else {
                // Phone: full screen, same title bar and progress bar on top.
                Column(Modifier.fillMaxSize().background(c.panel)) {
                    SetupTitleBar(title, icon, canGoBack, onBack, insetStatusBar = true)
                    StepProgressBar(progress)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { content() }
                }
            }
        }
    }
}

/** While a setup screen is shown, the install service skips its "finished" notification (the user sees the result). */
@Composable
internal fun TrackInstallerVisibility(installer: LibraryInstaller) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, installer) {
        installer.uiVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> installer.uiVisible = true
                Lifecycle.Event.ON_STOP -> installer.uiVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            installer.uiVisible = false
        }
    }
}

/**
 * The foreground service shows the download progress, so notifications are asked for before [start] runs. Android 13's
 * permission: before it, notifications need no asking.
 */
@Composable
internal fun rememberWithNotificationPermission(start: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            start()
        } else {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Desktop InstallerWindow title bar: gradient, the window's title with its icon, and a back arrow. */
@Composable
private fun SetupTitleBar(title: String, icon: ImageVector, canGoBack: Boolean, onBack: () -> Unit, insetStatusBar: Boolean) {
    val c = Zayit.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.titleBar)
            .then(if (insetStatusBar) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .height(44.dp),
    ) {
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = c.text, fontSize = 14.sp, maxLines = 1)
            Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(16.dp))
        }
        if (canGoBack) {
            ZIconButton(
                Icons.AutoMirrored.Outlined.ArrowBack,
                DesktopStrings.previousButton,
                onBack,
                Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
            )
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Keeps read access to documents picked with the system picker (or to a picked folder's tree) once the activity that
 * picked them is gone: the install and update services read them later in the background, and the picker's own
 * grant ends with the activity (a swipe from Recents). The run that reads them gives them back ([releaseReadGrants]).
 */
internal fun Context.takeReadGrants(uris: Collection<Uri>) {
    uris.forEach { runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
}

/** Gives back what [takeReadGrants] took: each document's grant and, for a document of a picked folder, the folder's. */
internal fun Context.releaseReadGrants(uris: Collection<Uri>) {
    val grants = uris.flatMapTo(LinkedHashSet<Uri>()) { uri ->
        val authority = uri.authority
        val tree = runCatching {
            if (authority != null && DocumentsContract.isTreeUri(uri)) DocumentsContract.buildTreeDocumentUri(authority, DocumentsContract.getTreeDocumentId(uri)) else null
        }.getOrNull()
        listOfNotNull(uri, tree)
    }
    grants.forEach { runCatching { contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
}
