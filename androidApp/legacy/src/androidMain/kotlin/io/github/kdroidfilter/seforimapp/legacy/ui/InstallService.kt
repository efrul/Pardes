package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import io.github.kdroidfilter.seforimapp.android.data.InstallState
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.legacy.PardesApp
import io.github.kdroidfilter.seforimapp.legacy.data.LegacyInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The main app's LibraryInstallService for this edition: keeps the process in the foreground while [LegacyInstaller]
 * unpacks, copies or moves a library, so that it goes on when the screen is left, and shows its progress in a
 * notification worded as the main app's (the onboarding's, or desktop DatabaseUpdateWindow's for a library replaced
 * from Settings), with ביטול. The work itself runs in the installer; this service only mirrors its state and stops
 * with it. Notification.Builder as Android 4.4 has it; a channel from Android 8.0, where the edition may run too.
 */
class InstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastUpdateMs = 0L
    private var lastKind: Class<*>? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val installer = PardesApp.of(this).installer
        if (intent?.action == ACTION_CANCEL) {
            // The latest start, which stop() must name for the service to stop.
            lastStartId = startId
            installer.cancel()
            if (observer == null) stopSelf(startId) // a stale tap, with nothing running
            return START_NOT_STICKY
        }
        lastStartId = startId
        ensureChannel()
        val started = runCatching { startForeground(NOTIFICATION_ID, progressNotification(installer, installer.state.value)) }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observer == null) {
            observer = scope.launch { installer.state.collect { onState(installer, it) } }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onState(installer: LegacyInstaller, state: InstallState) {
        when (state) {
            InstallState.Idle -> stop(null)
            InstallState.Done -> stop(if (installer.uiVisible) null else resultNotification(installer, done = true))
            is InstallState.Failed -> stop(if (installer.uiVisible) null else resultNotification(installer, done = false))
            else -> {
                val now = System.currentTimeMillis()
                // The system drops updates posted faster than a few per second; once a second is plenty.
                if (state.javaClass != lastKind || now - lastUpdateMs >= 1_000L) {
                    lastKind = state.javaClass
                    lastUpdateMs = now
                    notificationManager().notify(NOTIFICATION_ID, progressNotification(installer, state))
                }
            }
        }
    }

    // stopForeground(Boolean): what Android 4.4 to 6.0 have (deprecated from Android 13).
    @Suppress("DEPRECATION")
    private fun stop(result: Notification?) {
        observer?.cancel()
        observer = null
        stopForeground(true)
        if (result != null) notificationManager().notify(RESULT_NOTIFICATION_ID, result)
        // Not stopped if a new run has started the service again in the meantime.
        stopSelf(lastStartId)
    }

    // Notification.Builder(Context) and addAction(int, …): what Android 4.4 has (deprecated from Android 8.0 and 6.0).
    @Suppress("DEPRECATION")
    private fun progressNotification(installer: LegacyInstaller, state: InstallState): Notification {
        val update = installer.replacing
        val builder = baseBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                DesktopStrings.settingsCancel,
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, InstallService::class.java).setAction(ACTION_CANCEL),
                    immutable(PendingIntent.FLAG_UPDATE_CURRENT),
                ),
            )
        when (state) {
            is InstallState.Extracting -> {
                builder.setContentTitle(if (update) DesktopStrings.dbUpdateExtracting else DesktopStrings.onboardingExtractingMessage)
                val total = installer.inputBytes
                val percent = "${(state.progress * 100).toInt()}%"
                builder.setContentText(
                    if (total > 0) "$percent · " + DesktopStrings.onboardingDownloadProgress.format(formatBytes((state.progress * total).toLong()), formatBytes(total)) else percent,
                )
                builder.setProgress(PROGRESS_MAX, (state.progress * PROGRESS_MAX).toInt(), false)
            }
            InstallState.Verifying -> {
                builder.setContentTitle(if (update) DesktopStrings.dbUpdateVerifyingVersion else DesktopStrings.onboardingVerifyingVersion)
                builder.setProgress(0, 0, true)
            }
            else -> {
                builder.setContentTitle(if (update) DesktopStrings.dbUpdateTitleBar else DesktopStrings.onboardingTitleBar)
                builder.setProgress(0, 0, true)
            }
        }
        return builder.build()
    }

    private fun resultNotification(installer: LegacyInstaller, done: Boolean): Notification =
        baseBuilder()
            .setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(
                when {
                    installer.replacing && done -> DesktopStrings.dbUpdateSuccessTitle
                    installer.replacing -> DesktopStrings.dbUpdateExtractionError
                    done -> DesktopStrings.onboardingReady
                    else -> DesktopStrings.onboardingErrorOccurred
                },
            )
            .setContentText(if (installer.replacing) DesktopStrings.dbUpdateTitleBar else DesktopStrings.onboardingTitleBar)
            .setAutoCancel(true)
            .build()

    private fun baseBuilder(): Notification.Builder {
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder.setShowWhen(false)
        // The install screen, over the app's task: its progress, or what the run ended with.
        val open = Intent(this, InstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        builder.setContentIntent(PendingIntent.getActivity(this, 0, open, immutable(PendingIntent.FLAG_UPDATE_CURRENT)))
        return builder
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notificationManager(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "library_install"
        private const val CHANNEL_NAME = "התקנת הספרייה"
        private const val NOTIFICATION_ID = 4101
        private const val RESULT_NOTIFICATION_ID = 4102
        private const val PROGRESS_MAX = 1000
        private const val ACTION_CANCEL = "io.github.kdroidfilter.seforimapp.legacy.action.CANCEL_LIBRARY_INSTALL"

        /** FLAG_IMMUTABLE where Android has it (6.0) and asks for it (12). */
        private fun immutable(flags: Int): Int = if (Build.VERSION.SDK_INT >= 23) flags or PendingIntent.FLAG_IMMUTABLE else flags

        /** Called by [LegacyInstaller] when a run starts, from the install screen (the app is in the foreground). */
        fun start(context: Context) {
            runCatching { context.startService(Intent(context, InstallService::class.java)) }
        }
    }
}
