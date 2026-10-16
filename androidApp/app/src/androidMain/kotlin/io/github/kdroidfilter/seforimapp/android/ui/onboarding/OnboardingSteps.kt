package io.github.kdroidfilter.seforimapp.android.ui.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.data.InstallError
import io.github.kdroidfilter.seforimapp.android.data.InstallState
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.Island
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.components.ZCheckRow
import io.github.kdroidfilter.seforimapp.android.ui.settings.BundledLicenceLinks
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/** Desktop message for a failed install (DownloadScreen / ExtractScreen / OfflineFileSelectionScreen). */
internal fun installErrorMessage(failed: InstallState.Failed): String = when (failed.error) {
    InstallError.InsufficientSpace -> DesktopStrings.dbInstallInsufficientSpace
    InstallError.IncompleteBundle -> DesktopStrings.dbInstallIncompleteBundle
    InstallError.Incompatible -> DesktopStrings.onboardingVersionErrorMessage
    InstallError.FileTooLarge -> AndroidStrings.fileTooLarge
    InstallError.Generic -> failed.detail?.takeIf { it.isNotBlank() }?.let { DesktopStrings.onboardingErrorWithDetail.format(it) }
        ?: DesktopStrings.onboardingErrorOccurred
}

internal val InstallState.isBusy: Boolean
    get() = this is InstallState.Preparing || this is InstallState.Downloading || this is InstallState.Extracting || this is InstallState.Verifying

@Composable
internal fun InitStep(onNext: () -> Unit) {
    val wide = LocalOnboardingWide.current
    StepScaffold(
        title = DesktopStrings.onboardingInitWelcomeTitle,
        bottomAction = { AccentButton(DesktopStrings.onboardingInitStart, onNext) },
    ) {
        BodyText(AndroidStrings.welcomeSubtitle, fontSize = 16)
        // Desktop's olive branch there; this app's icon instead, smaller since it is a full tile
        Image(
            painter = painterResource(R.drawable.pardes_app_icon),
            contentDescription = null,
            modifier = Modifier.size(if (wide) 132.dp else 112.dp).clip(RoundedCornerShape(22)),
        )
        BodyText(DesktopStrings.onboardingSetupGuide)
    }
}

@Composable
internal fun LicenceStep(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit, onNext: () -> Unit) {
    StepScaffold(
        title = DesktopStrings.licenseScreenTitle,
        bottomAction = { AccentButton(DesktopStrings.nextButton, onNext, enabled = accepted) },
    ) {
        // Its AGPL link opens the licence bundled in the APK: no network is needed this early.
        BundledLicenceLinks { MarkdownText(CONDITIONS_MARKDOWN, Modifier.fillMaxWidth()) }
        // Desktop puts the checkbox, centered, after the last paragraph.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ZCheckRow(
                text = DesktopStrings.licenseAcceptCheckbox,
                checked = accepted,
                onToggle = { onAcceptedChange(!accepted) },
                modifier = Modifier.width(IntrinsicSize.Max),
            )
        }
    }
}

@Composable
internal fun DiskSpaceStep(state: DiskState, onRecheck: () -> Unit, onNext: () -> Unit, onSelectLocation: (String) -> Unit) {
    StepScaffold(
        title = DesktopStrings.onboardingDiskTitle,
        bottomAction = { AccentButton(DesktopStrings.nextButton, onNext, enabled = state.enough && !state.loading) },
    ) {
        if (state.loading) {
            CircularProgressIndicator(color = Zayit.colors.accent, strokeWidth = 3.dp, modifier = Modifier.padding(top = 48.dp).size(36.dp))
        } else {
            val used = (state.totalBytes - state.availableBytes).coerceAtLeast(0L)
            val library = LibraryInstaller.ESTIMATED_LIBRARY_BYTES
            val freeAfter = (state.availableBytes - library).coerceAtLeast(0L)
            DiskPie(
                listOf(
                    PieSlice(used.toFloat(), PieColors.used, DesktopStrings.diskPieUsedWithValue.format(formatBytes(used))),
                    PieSlice(library.toFloat(), PieColors.library, DesktopStrings.diskPiePermanentSpace.format(formatBytes(library))),
                    PieSlice(freeAfter.toFloat(), PieColors.freeAfter, DesktopStrings.diskPieFreeAfterWithValue.format(formatBytes(freeAfter))),
                ),
            )
            // Android: with a memory card in the device, the library can go there instead of the phone.
            if (state.choices.isNotEmpty()) LocationPicker(state.choices, state.selectedKey, onSelectLocation)
            if (state.enough) {
                InfoBanner(DesktopStrings.onboardingDiskSuccessContinue, Modifier.widthIn(max = 520.dp), success = true)
            } else {
                ErrorBanner(
                    DesktopStrings.onboardingDiskErrorFreeUp + "\n" +
                        AndroidStrings.diskRequired.format(formatBytes(state.requiredBytes), formatBytes(state.availableBytes)),
                    Modifier.widthIn(max = 520.dp),
                    actionLabel = DesktopStrings.recheckButton,
                    onAction = onRecheck,
                )
                if (state.choices.isEmpty()) InfoBanner(AndroidStrings.cardHint, Modifier.widthIn(max = 520.dp))
            }
            InfoBanner(
                AndroidStrings.diskStreamingNote.format(
                    formatBytes(LibraryInstaller.ESTIMATED_BUNDLE_BYTES),
                    formatBytes(LibraryInstaller.ESTIMATED_LIBRARY_BYTES),
                    formatBytes(LibraryInstaller.ESTIMATED_DB_BYTES),
                    formatBytes(LibraryInstaller.ESTIMATED_INDEX_BYTES),
                    formatBytes(LibraryInstaller.SPACE_HEADROOM_BYTES),
                ),
                Modifier.widthIn(max = 520.dp),
            )
        }
    }
}

@Composable
internal fun InstallTypeStep(device: DeviceState, onOffline: () -> Unit, onOnline: () -> Unit, onUseExisting: () -> Unit) {
    val c = Zayit.colors
    val wide = LocalOnboardingWide.current
    StepScaffold(title = DesktopStrings.installationTitle) {
        val existing = device.existingLibraryBytes
        if (existing != null) {
            InfoBanner(AndroidStrings.existingLibraryFound.format(formatBytes(existing)), success = true)
            device.existingLibraryError?.let { ErrorBanner(installErrorMessage(InstallState.Failed(it, null, online = false))) }
            AccentButton(AndroidStrings.useExistingLibrary, onUseExisting, enabled = !device.checkingExisting)
        }
        if (device.metered) InfoBanner(AndroidStrings.meteredWarning.format(formatBytes(LibraryInstaller.ESTIMATED_BUNDLE_BYTES)))
        val onlineDesc = AndroidStrings.installationOnlineDesc.format(formatBytes(LibraryInstaller.ESTIMATED_BUNDLE_BYTES))
        if (wide) {
            // Desktop: two columns split by a vertical rule, offline on the right (first in RTL).
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                InstallOption(
                    DesktopStrings.installationOfflineTitle,
                    AndroidStrings.installationOfflineDesc,
                    DesktopStrings.installationOfflineButton,
                    onOffline,
                    Modifier.weight(1f).fillMaxHeight(),
                    OnboardingIcons.Unarchive,
                )
                Box(Modifier.fillMaxHeight().width(1.dp).background(c.divider))
                InstallOption(
                    DesktopStrings.installationOnlineTitle,
                    onlineDesc,
                    DesktopStrings.installationOnlineButton,
                    onOnline,
                    Modifier.weight(1f).fillMaxHeight(),
                    OnboardingIcons.DownloadForOffline,
                )
            }
        } else {
            Island(Modifier.fillMaxWidth()) {
                InstallOption(
                    DesktopStrings.installationOfflineTitle,
                    AndroidStrings.installationOfflineDesc,
                    DesktopStrings.installationOfflineButton,
                    onOffline,
                    Modifier.fillMaxWidth(),
                    OnboardingIcons.Unarchive,
                )
            }
            Island(Modifier.fillMaxWidth()) {
                InstallOption(
                    DesktopStrings.installationOnlineTitle,
                    onlineDesc,
                    DesktopStrings.installationOnlineButton,
                    onOnline,
                    Modifier.fillMaxWidth(),
                    OnboardingIcons.DownloadForOffline,
                )
            }
        }
    }
}

/** One column of the desktop TypeOfInstallationScreen (and UpdateOptionsScreen): title, icon, description, button. */
@Composable
internal fun InstallOption(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector,
) {
    val c = Zayit.colors
    Column(
        modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(title, color = c.text, fontSize = 22.sp, textAlign = TextAlign.Center)
        Icon(icon, contentDescription = title, tint = c.text, modifier = Modifier.size(72.dp))
        BodyText(description, fontSize = 14)
        AccentButton(buttonText, onClick)
    }
}

@Composable
internal fun DownloadStep(state: InstallState, onRetry: () -> Unit, onCancel: () -> Unit) {
    val c = Zayit.colors
    StepScaffold(
        title = DesktopStrings.onboardingDownloadingMessage,
        // Desktop has no way out of the download; a phone may need to stop a 5 GB transfer.
        bottomAction = { if (state.isBusy) OutlineButton(DesktopStrings.settingsCancel, onCancel) },
    ) {
        if (state is InstallState.Failed) {
            ErrorBanner(installErrorMessage(state), actionLabel = DesktopStrings.retryButton, onAction = onRetry)
        }
        StepIllustration(OnboardingIcons.DownloadForOffline, wideSize = 192.dp, phoneSize = 140.dp)
        val download = state as? InstallState.Downloading
        val total = download?.totalBytes
        if (download != null && total != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricRow(
                    OnboardingIcons.FileArrowDown,
                    DesktopStrings.onboardingDownloadProgress.format(formatBytes(download.downloadedBytes), formatBytes(total)),
                )
                MetricRow(OnboardingIcons.Speed, formatBytesPerSec(download.speedBytesPerSec))
                etaSeconds(download.downloadedBytes, total, download.speedBytesPerSec)?.let { MetricRow(OnboardingIcons.Timer, formatEta(it)) }
            }
            if (download.reconnecting) BodyText(AndroidStrings.reconnecting)
        } else if (state is InstallState.Preparing) {
            CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
        if (state.isBusy) {
            Text(AndroidStrings.backgroundHint, color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun OfflineFilesStep(state: InstallState, device: DeviceState, onPick: () -> Unit, onUseLocal: () -> Unit) {
    val c = Zayit.colors
    val busy = state.isBusy
    StepScaffold(title = DesktopStrings.onboardingFileSelection) {
        StepIllustration(OnboardingIcons.Unarchive, wideSize = 192.dp, phoneSize = 140.dp)
        BodyText(DesktopStrings.onboardingFileSelection)
        BodyText(AndroidStrings.selectFilesMessage, Modifier.fillMaxWidth(0.9f))
        if (device.pickedFiles > 0 && state !is InstallState.Idle) {
            BodyText(AndroidStrings.filesSelected.format(device.pickedFiles))
        }
        if (state is InstallState.Failed) ErrorBanner(installErrorMessage(state))
        if (device.localBundle.isNotEmpty()) {
            InfoBanner(AndroidStrings.localBundleFound)
            AccentButton(AndroidStrings.useLocalBundle, onUseLocal, enabled = !busy)
        }
        if (state is InstallState.Preparing) {
            CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
        AccentButton(DesktopStrings.onboardingChooseFiles, onPick, enabled = !busy)
    }
}

@Composable
internal fun ExtractStep(state: InstallState, onRetry: () -> Unit, onRestart: () -> Unit) {
    val c = Zayit.colors
    when {
        state is InstallState.Failed && state.error == InstallError.Incompatible -> {
            // Desktop VersionVerificationScreen, incompatible database.
            StepScaffold(title = DesktopStrings.onboardingVersionErrorTitle) {
                Icon(Icons.Outlined.ReportProblem, contentDescription = null, tint = c.error, modifier = Modifier.padding(top = 16.dp).size(72.dp))
                BodyText(DesktopStrings.onboardingVersionErrorTitle, fontSize = 17)
                BodyText(DesktopStrings.onboardingVersionErrorMessage, Modifier.fillMaxWidth(0.85f))
                AccentButton(DesktopStrings.onboardingRestartInstallation, onRestart)
            }
        }
        state is InstallState.Verifying || state is InstallState.Done -> {
            StepScaffold(title = DesktopStrings.onboardingExtractingMessage) {
                if (state is InstallState.Done) {
                    Icon(OnboardingIcons.Check2Circle, contentDescription = null, tint = c.text, modifier = Modifier.padding(top = 24.dp).size(72.dp))
                    BodyText(DesktopStrings.onboardingReady)
                } else {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.padding(top = 24.dp).size(40.dp))
                    BodyText(DesktopStrings.onboardingVerifyingVersion)
                }
            }
        }
        else -> {
            StepScaffold(title = DesktopStrings.onboardingExtractingMessage) {
                if (state is InstallState.Failed) {
                    ErrorBanner(installErrorMessage(state), actionLabel = DesktopStrings.retryButton, onAction = onRetry)
                }
                StepIllustration(OnboardingIcons.Unarchive, wideSize = 192.dp, phoneSize = 140.dp)
                if (state is InstallState.Extracting) {
                    BodyText("${(state.progress * 100).toInt()}%")
                }
                InfoBanner(DesktopStrings.onboardingExtractingDidYouKnow)
            }
        }
    }
}

@Composable
internal fun ProfileStep(
    profile: ProfileState,
    onFirstName: (String) -> Unit,
    onLastName: (String) -> Unit,
    onCommunity: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val wide = LocalOnboardingWide.current
    val focusManager = LocalFocusManager.current
    StepScaffold(
        title = DesktopStrings.onboardingUserInfoTitle,
        bottomAction = { AccentButton(DesktopStrings.nextButton, onNext, enabled = profile.canProceed) },
    ) {
        InfoBanner(DesktopStrings.onboardingUserInfoDescription)
        val form: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val firstName: @Composable (Modifier) -> Unit = { m ->
                    LabeledTextField(
                        DesktopStrings.onboardingUserFirstNameLabel,
                        profile.firstName,
                        onFirstName,
                        m,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Next) },
                    )
                }
                val lastName: @Composable (Modifier) -> Unit = { m ->
                    LabeledTextField(
                        DesktopStrings.onboardingUserLastNameLabel,
                        profile.lastName,
                        onLastName,
                        m,
                        imeAction = ImeAction.Done,
                        onImeAction = { focusManager.clearFocus() },
                    )
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        firstName(Modifier.weight(1f))
                        lastName(Modifier.weight(1f))
                    }
                } else {
                    firstName(Modifier.fillMaxWidth())
                    lastName(Modifier.fillMaxWidth())
                }
                LabeledDropdown(
                    DesktopStrings.onboardingUserCommunityLabel,
                    Community.entries.map { it.label },
                    profile.communityIndex,
                    onCommunity,
                    Modifier.fillMaxWidth(),
                )
            }
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                form(Modifier.weight(1f).padding(horizontal = 16.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    StepIllustration(OnboardingIcons.InkPen, wideSize = 160.dp, phoneSize = 160.dp)
                }
            }
        } else {
            StepIllustration(OnboardingIcons.InkPen, wideSize = 88.dp, phoneSize = 88.dp)
            form(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun RegionStep(
    region: RegionState,
    countries: List<String>,
    cities: List<String>,
    onCountry: (Int) -> Unit,
    onCity: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val wide = LocalOnboardingWide.current
    StepScaffold(
        title = DesktopStrings.onboardingRegionTitle,
        bottomAction = { AccentButton(DesktopStrings.nextButton, onNext, enabled = region.canProceed) },
    ) {
        InfoBanner(DesktopStrings.onboardingRegionDescription)
        val form: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Both lists filter as the user types, like desktop's SpeedSearchArea.
                LabeledDropdown(DesktopStrings.onboardingRegionCountryLabel, countries, region.countryIndex, onCountry, Modifier.fillMaxWidth(), searchable = true)
                // Desktop enables the city list once a country is chosen.
                LabeledDropdown(
                    DesktopStrings.onboardingRegionCityLabel,
                    cities,
                    region.cityIndex,
                    onCity,
                    Modifier.fillMaxWidth(),
                    enabled = region.countryIndex >= 0,
                    searchable = true,
                )
            }
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                form(Modifier.weight(1f).padding(horizontal = 16.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    StepIllustration(OnboardingIcons.FoldedMap, wideSize = 160.dp, phoneSize = 160.dp)
                }
            }
        } else {
            StepIllustration(OnboardingIcons.FoldedMap, wideSize = 88.dp, phoneSize = 88.dp)
            form(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun FinishStep(onOpenApp: () -> Unit) {
    val wide = LocalOnboardingWide.current
    Box {
        StepScaffold(
            title = DesktopStrings.onboardingReady,
            bottomAction = { AccentButton(DesktopStrings.onboardingOpenApp, onOpenApp) },
        ) {
            Text(
                DesktopStrings.onboardingFinishReadyMessage,
                color = Zayit.colors.text,
                fontSize = if (wide) 24.sp else 20.sp,
                lineHeight = if (wide) 32.sp else 28.sp,
                textAlign = TextAlign.Center,
            )
            StepIllustration(OnboardingIcons.Check2Circle, wideSize = 192.dp, phoneSize = 150.dp)
        }
        Confetti(Modifier.matchParentSize())
    }
}
