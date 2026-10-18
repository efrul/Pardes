package io.github.kdroidfilter.seforimapp.android.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import androidx.core.content.IntentCompat
import io.github.kdroidfilter.seforimapp.android.showForegroundImmediately
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The sibling of [io.github.kdroidfilter.seforimapp.android.delta.LibraryDeltaService] for the app's own update: keeps
 * the process in the foreground while [AppUpdater] downloads and checks the APK, with the progress in a notification
 * and a cancel action. When the download ends and no update view is on screen, a notification says the update is ready
 * (its tap opens the dialog) or that it failed. The work itself runs in [AppUpdater]; this only mirrors its state.
 */
class AppUpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastUpdateMs = 0L
    private var lastPhase: AppUpdateState.Phase? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val updater = AppUpdater.get(this)
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            updater.cancel()
            if (observer == null) stopSelf(startId) // a stale tap, with nothing running
            return START_NOT_STICKY
        }
        AppUpdateNotifications.ensureChannel(this)
        val started = runCatching {
            startForeground(AppUpdateNotifications.PROGRESS_ID, progressNotification(updater.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observer == null) observer = scope.launch { updater.state.collect { onState(updater, it) } }
        return START_NOT_STICKY
    }

    /** Android 15+ caps dataSync services at 6 h a day; the download keeps running without the notification. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onState(updater: AppUpdater, state: AppUpdateState) {
        val phase = state.phase
        if (phase != AppUpdateState.Phase.Downloading && phase != AppUpdateState.Phase.Verifying) {
            stop()
            if (!updater.uiVisible) {
                when {
                    state.ready -> AppUpdateNotifications.ready(this, state.version.orEmpty())
                    state.errorMessage != null -> AppUpdateNotifications.failed(this, state.errorMessage)
                }
            }
            return
        }
        val now = System.currentTimeMillis()
        // The system drops updates posted faster than a few per second; once a second is plenty.
        if (phase != lastPhase || now - lastUpdateMs >= 1_000L) {
            lastPhase = phase
            lastUpdateMs = now
            AppUpdateNotifications.manager(this).notify(AppUpdateNotifications.PROGRESS_ID, progressNotification(state))
        }
    }

    private fun stop() {
        observer?.cancel()
        observer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private fun progressNotification(state: AppUpdateState): Notification {
        val downloading = state.phase == AppUpdateState.Phase.Downloading
        val builder = AppUpdateNotifications.builder(this, AppUpdateNotifications.showIntent(this))
            .setSmallIcon(if (downloading) android.R.drawable.stat_sys_download else android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .showForegroundImmediately()
            .setContentTitle(AppUpdateStrings.downloadingTitle)
            .setContentText(if (downloading) state.detail ?: AppUpdateStrings.downloading else AppUpdateStrings.verifying)
        val progress = state.progress
        if (downloading && progress != null) builder.setProgress(PROGRESS_MAX, (progress * PROGRESS_MAX).toInt(), false) else builder.setProgress(0, 0, true)
        if (downloading) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    AppUpdateStrings.cancel,
                    PendingIntent.getService(
                        this,
                        3,
                        Intent(this, AppUpdateService::class.java).setAction(ACTION_CANCEL),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build(),
            )
        }
        return builder.build()
    }

    companion object {
        private const val PROGRESS_MAX = 1000
        private const val ACTION_CANCEL = "io.github.kdroidfilter.seforimapp.android.action.CANCEL_APP_UPDATE"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, AppUpdateService::class.java)) }
        }
    }
}

/** The update's notifications: the download's progress (the service's), and what waits for a tap or has happened. */
internal object AppUpdateNotifications {
    const val PROGRESS_ID = 4121
    private const val RESULT_ID = 4122
    private const val CHANNEL_ID = "app_update"

    fun manager(context: Context): NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel(context: Context) {
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL_ID, AppUpdateStrings.channel, NotificationManager.IMPORTANCE_LOW))
    }

    fun builder(context: Context, tap: PendingIntent?): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID).setShowWhen(false).apply { if (tap != null) setContentIntent(tap) }

    /** Opens the app on the update dialog (MainActivity takes [AppUpdater.ACTION_SHOW]). */
    fun showIntent(context: Context): PendingIntent? = launch(context, AppUpdater.ACTION_SHOW)

    private fun launch(context: Context, action: String?): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            if (action != null) intent.setAction(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            PendingIntent.getActivity(context, if (action == null) 0 else 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /** The APK is checked and waits: tap to install. */
    fun ready(context: Context, version: String) =
        post(context, android.R.drawable.stat_sys_download_done, AppUpdateStrings.readyTitle, AppUpdateStrings.readyText.format(version), showIntent(context))

    fun failed(context: Context, message: String) =
        post(context, android.R.drawable.stat_notify_error, AppUpdateStrings.failedTitle, message, showIntent(context))

    /**
     * Android's confirmation, which an app off screen may not open itself: the tap opens the dialog, whose "המשך
     * בהתקנה" opens it (after checking no library work would be cut short by the update).
     */
    fun confirm(context: Context) =
        post(context, android.R.drawable.stat_sys_download_done, AppUpdateStrings.confirmTitle, AppUpdateStrings.confirmText, showIntent(context))

    /** Posted by the new version: the update ended the old process, and Android opens nothing by itself after it. */
    fun updated(context: Context, version: String) =
        post(context, android.R.drawable.stat_sys_download_done, AppUpdateStrings.updatedTo.format(version), AppUpdateStrings.tapToOpen, launch(context, null))

    fun cancelResult(context: Context) {
        runCatching { manager(context).cancel(RESULT_ID) }
    }

    private fun post(context: Context, icon: Int, title: String, text: String, tap: PendingIntent?) {
        runCatching {
            ensureChannel(context)
            manager(context).notify(
                RESULT_ID,
                builder(context, tap).setSmallIcon(icon).setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build(),
            )
        }
    }
}

/** PackageInstaller's answer to the update's session (an explicit broadcast; not exported). */
class AppUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS) return
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        // IntentCompat: the typed getParcelableExtra of API 33 has a bug (b/232589966)
        val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        AppUpdater.get(context).onInstallStatus(session, status, detail, confirm)
    }

    companion object {
        const val ACTION_STATUS = "io.github.kdroidfilter.seforimapp.android.action.APP_UPDATE_STATUS"
    }
}

/**
 * Android's ACTION_MY_PACKAGE_REPLACED, received by the new version: when it is the one [AppUpdater] installed, a
 * notification says so and opens the app (the update ended the old one, and Android reopens nothing by itself).
 */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) announce(context)
    }

    companion object {
        /**
         * Once, from whichever comes first: this broadcast, or the session's STATUS_SUCCESS, which also reaches the new
         * version (the Jelly Star, Android 13, sent no MY_PACKAGE_REPLACED for some self-updates).
         */
        fun announce(context: Context) {
            val updater = AppUpdater.get(context)
            val version = updater.installingVersion() ?: return
            if (AppReleases.compareVersions(version, updater.installedVersion) != 0) return
            updater.markUpdated(version)
            AppUpdateNotifications.updated(context, version)
        }
    }
}
