package io.github.kdroidfilter.seforimapp.android.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.kdroidfilter.seforimapp.android.showForegroundImmediately
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytesPerSec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process in the foreground while [LibraryInstaller] downloads or unpacks the library, so a
 * multi-GB install goes on when the user leaves the app, and shows its progress in a notification, worded like the
 * desktop window of the flow that started it (onboarding, or DatabaseUpdateWindow: [InstallPurpose]).
 * The work itself runs in [LibraryInstaller]; this service only mirrors its state and stops with it.
 */
class LibraryInstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastUpdateMs = 0L
    private var lastKind: Class<*>? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val installer = LibraryInstaller.get(this)
        if (intent?.action == ACTION_CANCEL) {
            // The latest start, which stop() must name for the service to stop.
            lastStartId = startId
            installer.cancel()
            if (observer == null) stopSelf(startId) // a stale tap, with nothing running
            return START_NOT_STICKY
        }
        lastStartId = startId
        ensureChannel()
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, progressNotification(installer.state.value, installer.purpose), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observer == null) {
            observer = scope.launch { installer.state.collect { onState(installer, it) } }
        }
        return START_NOT_STICKY
    }

    /** Android 15+ caps dataSync services at 6 h a day; the install keeps running without the notification. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stop(null)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onState(installer: LibraryInstaller, state: InstallState) {
        when (state) {
            InstallState.Idle -> stop(null)
            InstallState.Done -> stop(if (installer.uiVisible) null else resultNotification(installer.purpose, done = true))
            is InstallState.Failed -> stop(if (installer.uiVisible) null else resultNotification(installer.purpose, done = false))
            else -> {
                val now = System.currentTimeMillis()
                // The system drops updates posted faster than a few per second; once a second is plenty.
                if (state.javaClass != lastKind || now - lastUpdateMs >= 1_000L) {
                    lastKind = state.javaClass
                    lastUpdateMs = now
                    notificationManager().notify(NOTIFICATION_ID, progressNotification(state, installer.purpose))
                }
            }
        }
    }

    private fun stop(result: Notification?) {
        observer?.cancel()
        observer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (result != null) notificationManager().notify(RESULT_NOTIFICATION_ID, result)
        // Not stopped if a new run has started the service again in the meantime.
        stopSelf(lastStartId)
    }

    /** The wording of the flow that started the run: the onboarding's, or desktop DatabaseUpdateWindow's (db_update_*). */
    private fun progressNotification(state: InstallState, purpose: InstallPurpose): Notification {
        val update = purpose == InstallPurpose.Update
        val builder = baseBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .showForegroundImmediately()
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    DesktopStrings.settingsCancel,
                    PendingIntent.getService(
                        this,
                        1,
                        Intent(this, LibraryInstallService::class.java).setAction(ACTION_CANCEL),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build(),
            )
        when (state) {
            is InstallState.Downloading -> {
                builder.setContentTitle(if (update) DesktopStrings.dbUpdateDownloading else DesktopStrings.onboardingDownloadingMessage)
                val total = state.totalBytes
                if (total != null && total > 0) {
                    val sizes = DesktopStrings.onboardingDownloadProgress.format(formatBytes(state.downloadedBytes), formatBytes(total))
                    builder.setContentText(if (state.speedBytesPerSec > 0) "$sizes · ${formatBytesPerSec(state.speedBytesPerSec)}" else sizes)
                    builder.setProgress(PROGRESS_MAX, (state.progress * PROGRESS_MAX).toInt(), false)
                } else {
                    builder.setProgress(0, 0, true)
                }
            }
            is InstallState.Extracting -> {
                builder.setContentTitle(if (update) DesktopStrings.dbUpdateExtracting else DesktopStrings.onboardingExtractingMessage)
                builder.setContentText("${(state.progress * 100).toInt()}%")
                builder.setProgress(PROGRESS_MAX, (state.progress * PROGRESS_MAX).toInt(), false)
            }
            InstallState.Verifying -> {
                builder.setContentTitle(if (update) DesktopStrings.dbUpdateVerifyingVersion else DesktopStrings.onboardingVerifyingVersion)
                builder.setProgress(0, 0, true)
            }
            else -> {
                builder.setContentTitle(
                    when {
                        update && state is InstallState.Preparing && state.online -> DesktopStrings.dbUpdatePreparingDownload
                        update -> DesktopStrings.dbUpdateTitleBar
                        else -> DesktopStrings.onboardingTitleBar
                    },
                )
                builder.setProgress(0, 0, true)
            }
        }
        return builder.build()
    }

    private fun resultNotification(purpose: InstallPurpose, done: Boolean): Notification =
        baseBuilder()
            .setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(
                when {
                    purpose == InstallPurpose.Update && done -> DesktopStrings.dbUpdateSuccessTitle
                    purpose == InstallPurpose.Update -> DesktopStrings.dbUpdateDownloadError
                    done -> DesktopStrings.onboardingReady
                    else -> DesktopStrings.onboardingErrorOccurred
                },
            )
            .setContentText(if (purpose == InstallPurpose.Update) DesktopStrings.dbUpdateTitleBar else DesktopStrings.onboardingTitleBar)
            .setAutoCancel(true)
            .build()

    private fun baseBuilder(): Notification.Builder {
        val builder = Notification.Builder(this, CHANNEL_ID).setShowWhen(false)
        // Like a launcher tap: brings the running task back instead of starting a new activity.
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return builder
    }

    private fun ensureChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "library_install"
        private const val CHANNEL_NAME = "התקנת הספרייה"
        private const val NOTIFICATION_ID = 4101
        private const val RESULT_NOTIFICATION_ID = 4102
        private const val PROGRESS_MAX = 1000
        private const val ACTION_CANCEL = "io.github.kdroidfilter.seforimapp.android.action.CANCEL_LIBRARY_INSTALL"

        /** Called by [LibraryInstaller] when a run starts (always from the visible onboarding or database update). */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LibraryInstallService::class.java)) }
        }
    }
}
