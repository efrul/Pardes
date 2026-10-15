package io.github.kdroidfilter.seforimapp.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.DatabaseVersion
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateEvents
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateService
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateState
import io.github.kdroidfilter.seforimapp.android.delta.DeltaStrings
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.AndroidStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.rememberWithNotificationPermission
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop 0.6.1 `features/settings/dbupdate/DbDeltaUpdateSection.kt` in Settings › ניהול נתונים: the status text
 * (idle, checking, downloading, applying, done, error, up to date, too old), a progress bar while a phase runs, the
 * "check for updates" button (disabled while busy) and "dismiss" for a message. Desktop's English literals are in
 * Hebrew ([DeltaStrings]). For touch the check stops at the update it found, with its download size and the
 * metered-network warning, and runs only on "הורד עכשיו"; a run can be cancelled until the database part commits.
 * The state lives in [DbDeltaUpdateService], so the section reopens on a running update.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DbDeltaUpdateSection() {
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    val service = remember(app) { DbDeltaUpdateService.get(app) }
    val state by service.state.collectAsState()
    val busy = state.phase != null
    val hasMessage = state.message.isNotEmpty() || state.errorMessage != null
    TrackSectionVisibility(service)
    val version by produceState<String?>(null, state.lastAppliedCount) {
        value = withContext(Dispatchers.IO) { DatabaseVersion.current(app.libraryStore).orEmpty() }
    }
    val metered by produceState(false, state.offer) {
        value = state.offer != null && withContext(Dispatchers.IO) { LibraryInstaller.get(app).isMeteredNetwork() }
    }
    val start = rememberWithNotificationPermission { service.onEvent(DbDeltaUpdateEvents.ApplyClicked) }
    val c = Zayit.colors

    SettingsCard(title = DeltaStrings.title) {
        version?.let { v ->
            Text(
                DesktopStrings.dbUpdateCurrentVersion.format(v.takeIf { it.isNotEmpty() }?.let(DatabaseVersion::formatForDisplay) ?: DesktopStrings.dbUpdateVersionUnknown),
                fontSize = 13.sp,
                color = c.textSecondary,
            )
        }
        val offer = state.offer
        val text = when {
            state.errorMessage != null -> null
            state.needsFullBundle || state.lastAppliedCount != null -> state.message
            busy -> state.message.ifEmpty { state.phase.toString() }
            offer != null -> null
            state.message.isNotEmpty() -> state.message
            else -> DeltaStrings.idle
        }
        when {
            state.errorMessage != null -> ErrorBanner(state.errorMessage!!)
            state.lastAppliedCount != null || state.upToDate -> InfoBanner(state.message, success = true)
            offer != null -> InfoBanner(if (offer.resume) state.message else "${DeltaStrings.available}: ${state.message}")
            text != null -> Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = c.text)
        }
        if (offer != null && metered && !offer.resume) InfoBanner(AndroidStrings.meteredWarning.format(formatBytes(offer.bytes)))
        if (busy) {
            val progress = state.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            }
            state.detail?.let { Text(it, fontSize = 13.sp, color = c.textSecondary) }
            if (state.phase != DbDeltaUpdateState.Phase.CheckingForUpdates) Text(DeltaStrings.backgroundHint, fontSize = 12.sp, color = c.textSecondary)
        }
        // Wraps on a 240dp phone instead of squeezing the buttons.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                busy -> AccentButton(DeltaStrings.working, {}, enabled = false)
                offer != null -> AccentButton(if (offer.resume) DeltaStrings.resume else DeltaStrings.download, start)
                else -> AccentButton(if (state.upToDate) DeltaStrings.checkAgain else DeltaStrings.check, { service.onEvent(DbDeltaUpdateEvents.CheckClicked) })
            }
            if (busy && (state.cancellable || state.phase == DbDeltaUpdateState.Phase.CheckingForUpdates)) {
                OutlineButton(DeltaStrings.cancel, { service.onEvent(DbDeltaUpdateEvents.CancelClicked) })
            }
            if (hasMessage && !busy && offer == null) OutlineButton(DeltaStrings.dismiss, { service.onEvent(DbDeltaUpdateEvents.ClearMessage) })
        }
    }
}

/** While the section is on screen the update's end is shown here, not as a notification (as LibraryInstaller.uiVisible). */
@Composable
private fun TrackSectionVisibility(service: DbDeltaUpdateService) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, service) {
        service.uiVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> service.uiVisible = true
                Lifecycle.Event.ON_STOP -> service.uiVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            service.uiVisible = false
        }
    }
}
