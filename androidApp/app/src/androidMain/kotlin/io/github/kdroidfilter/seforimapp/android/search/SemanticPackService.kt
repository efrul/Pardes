package io.github.kdroidfilter.seforimapp.android.search

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
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.settings.SemanticPackStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android only: keeps the process in the foreground while [SemanticPackManager] downloads or unpacks a semantic pack
 * (2 GB, minutes on a phone), so it goes on when the user leaves the settings, and shows its progress in a notification,
 * with the settings card's words. The work runs in [SemanticPackManager]; this service only mirrors its state and stops
 * with it, as LibraryInstallService does for the library.
 */
class SemanticPackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastUpdateMs = 0L
    private var lastKind: Class<*>? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = SemanticPackManager.get(this)
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            manager.cancel()
            if (observer == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW))
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, progressNotification(manager.state.value.work), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observer == null) observer = scope.launch { manager.state.collect { onWork(it.work) } }
        return START_NOT_STICKY
    }

    /** Android 15+ caps dataSync services at 6 h a day; the work keeps running without the notification. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stop(null)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onWork(work: SemanticPackManager.Work) {
        when (work) {
            is SemanticPackManager.Work.Downloading, is SemanticPackManager.Work.Unpacking -> {
                val now = System.currentTimeMillis()
                // The system drops updates posted faster than a few per second; once a second is plenty
                if (work.javaClass != lastKind || now - lastUpdateMs >= 1_000L) {
                    lastKind = work.javaClass
                    lastUpdateMs = now
                    notificationManager().notify(NOTIFICATION_ID, progressNotification(work))
                }
            }
            SemanticPackManager.Work.Ready -> stop(resultNotification(SemanticPackStrings.ready))
            is SemanticPackManager.Work.Failed -> stop(resultNotification(SemanticPackStrings.failedNotification))
            SemanticPackManager.Work.Idle -> stop(null)
        }
    }

    private fun stop(result: Notification?) {
        observer?.cancel()
        observer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (result != null) notificationManager().notify(RESULT_NOTIFICATION_ID, result)
        stopSelf(lastStartId)
    }

    private fun progressNotification(work: SemanticPackManager.Work): Notification {
        val builder = baseBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .showForegroundImmediately()
            .setContentTitle(SemanticPackStrings.title)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    SemanticPackStrings.cancel,
                    PendingIntent.getService(
                        this,
                        2,
                        Intent(this, SemanticPackService::class.java).setAction(ACTION_CANCEL),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build(),
            )
        when (work) {
            is SemanticPackManager.Work.Downloading -> {
                builder.setContentText(SemanticPackStrings.downloading.format(formatBytes(work.read), formatBytes(work.total)))
                if (work.total > 0) builder.setProgress(PROGRESS_MAX, (work.read * PROGRESS_MAX / work.total).toInt(), false) else builder.setProgress(0, 0, true)
            }
            is SemanticPackManager.Work.Unpacking -> {
                builder.setContentText(SemanticPackStrings.unpacking)
                builder.setProgress(PROGRESS_MAX, (work.progress * PROGRESS_MAX).toInt(), false)
            }
            else -> builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun resultNotification(text: String): Notification =
        baseBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(SemanticPackStrings.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

    private fun baseBuilder(): Notification.Builder {
        val builder = Notification.Builder(this, CHANNEL_ID).setShowWhen(false)
        // Like a launcher tap: brings the running task back instead of starting a new activity
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return builder
    }

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "semantic_pack"
        private const val CHANNEL_NAME = "החיפוש לפי משמעות"
        private const val NOTIFICATION_ID = 4131
        private const val RESULT_NOTIFICATION_ID = 4132
        private const val PROGRESS_MAX = 1000
        private const val ACTION_CANCEL = "io.github.kdroidfilter.seforimapp.android.action.CANCEL_SEMANTIC_PACK"

        /** Called by [SemanticPackManager] when a download or an import starts (from the visible settings). */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, SemanticPackService::class.java)) }
        }
    }
}
