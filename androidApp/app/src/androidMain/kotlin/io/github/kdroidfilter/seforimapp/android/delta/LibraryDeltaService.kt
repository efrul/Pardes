package io.github.kdroidfilter.seforimapp.android.delta

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
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The sibling of [io.github.kdroidfilter.seforimapp.android.data.LibraryInstallService] for incremental updates: keeps
 * the process in the foreground while [DbDeltaUpdateService] downloads and applies a delta (a phone may take several
 * minutes to hash 8 GB and re-index the changed lines), with the progress in a notification and, until the database
 * part is committed, a cancel action. The work itself runs in [DbDeltaUpdateService]; this only mirrors its state.
 */
class LibraryDeltaService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastUpdateMs = 0L
    private var lastMessage: String? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val service = DbDeltaUpdateService.get(application as ZayitApplication)
        if (intent?.action == ACTION_CANCEL) {
            service.cancel()
            return START_NOT_STICKY
        }
        lastStartId = startId
        ensureChannel()
        val started = runCatching {
            startForeground(NOTIFICATION_ID, progressNotification(service.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observer == null) observer = scope.launch { service.state.collect { onState(service, it) } }
        return START_NOT_STICKY
    }

    /** Android 15+ caps dataSync services at 6 h a day; the update keeps running without the notification. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stop(null)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onState(service: DbDeltaUpdateService, state: DbDeltaUpdateState) {
        if (state.phase == null) {
            val done = state.lastAppliedCount != null
            val failed = state.errorMessage != null
            stop(if (service.uiVisible || !(done || failed)) null else resultNotification(done, state.errorMessage))
            return
        }
        val now = System.currentTimeMillis()
        // The system drops updates posted faster than a few per second; once a second is plenty.
        if (state.message != lastMessage || now - lastUpdateMs >= 1_000L) {
            lastMessage = state.message
            lastUpdateMs = now
            notificationManager().notify(NOTIFICATION_ID, progressNotification(state))
        }
    }

    private fun stop(result: Notification?) {
        observer?.cancel()
        observer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (result != null) notificationManager().notify(RESULT_NOTIFICATION_ID, result)
        stopSelf(lastStartId)
    }

    private fun progressNotification(state: DbDeltaUpdateState): Notification {
        val builder = baseBuilder()
            .setSmallIcon(if (state.phase == DbDeltaUpdateState.Phase.Downloading) android.R.drawable.stat_sys_download else android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(state.message.ifEmpty { DeltaStrings.title })
            .setContentText(state.detail ?: DeltaStrings.title)
        val progress = state.progress
        if (progress != null) builder.setProgress(PROGRESS_MAX, (progress * PROGRESS_MAX).toInt(), false) else builder.setProgress(0, 0, true)
        if (state.cancellable) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    DesktopStrings.settingsCancel,
                    PendingIntent.getService(
                        this,
                        2,
                        Intent(this, LibraryDeltaService::class.java).setAction(ACTION_CANCEL),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build(),
            )
        }
        return builder.build()
    }

    private fun resultNotification(done: Boolean, error: String?): Notification =
        baseBuilder()
            .setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (done) DesktopStrings.dbUpdateSuccessTitle else DesktopStrings.dbUpdateDownloadError)
            .setContentText(error ?: DeltaStrings.title)
            .setAutoCancel(true)
            .build()

    private fun baseBuilder(): Notification.Builder {
        val builder = Notification.Builder(this, CHANNEL_ID).setShowWhen(false)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return builder
    }

    private fun ensureChannel() {
        notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID, DesktopStrings.dbUpdateTitleBar, NotificationManager.IMPORTANCE_LOW))
    }

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "library_delta"
        private const val NOTIFICATION_ID = 4111
        private const val RESULT_NOTIFICATION_ID = 4112
        private const val PROGRESS_MAX = 1000
        private const val ACTION_CANCEL = "io.github.kdroidfilter.seforimapp.android.action.CANCEL_LIBRARY_DELTA"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LibraryDeltaService::class.java)) }
        }
    }
}
