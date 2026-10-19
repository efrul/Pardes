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
 * Keeps the process in the foreground while [LibraryMover] copies the library to or from a memory card (many
 * minutes for 13 GB), with its progress and a cancel action in a notification. Like [LibraryInstallService], it only
 * mirrors the mover's state and stops with it.
 */
class LibraryMoveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastUpdateMs = 0L
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mover = LibraryMover.get(this)
        if (intent?.action == ACTION_CANCEL) {
            // The latest start, which stop() must name for the service to stop.
            lastStartId = startId
            mover.cancel()
            if (observer == null) stopSelf(startId) // a stale tap, with nothing running
            return START_NOT_STICKY
        }
        lastStartId = startId
        notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW))
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, progressNotification(mover.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observer == null) observer = scope.launch { mover.state.collect(::onState) }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stop(null)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onState(state: MoveState) {
        when (state) {
            MoveState.Idle -> stop(null)
            // The app starts again right after: the result stays in a notification of its own.
            is MoveState.Done -> stop(resultNotification(MoveUiStrings.doneTitle, MoveUiStrings.doneText.format(state.target), done = true))
            is MoveState.Failed -> stop(resultNotification(MoveUiStrings.failedTitle, state.message, done = false))
            else -> {
                val now = System.currentTimeMillis()
                if (state !is MoveState.Copying || now - lastUpdateMs >= 1_000L) {
                    lastUpdateMs = now
                    notificationManager().notify(NOTIFICATION_ID, progressNotification(state))
                }
            }
        }
    }

    private fun stop(result: Notification?) {
        observer?.cancel()
        observer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (result != null) notificationManager().notify(RESULT_NOTIFICATION_ID, result)
        stopSelf(lastStartId)
    }

    private fun progressNotification(state: MoveState): Notification {
        val builder = baseBuilder()
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .showForegroundImmediately()
        when (state) {
            is MoveState.Copying -> {
                builder.setContentTitle(MoveUiStrings.movingTo.format(state.target))
                if (state.totalBytes > 0) {
                    val sizes = DesktopStrings.onboardingDownloadProgress.format(formatBytes(state.copiedBytes), formatBytes(state.totalBytes))
                    builder.setContentText(if (state.speedBytesPerSec > 0) "$sizes · ${formatBytesPerSec(state.speedBytesPerSec)}" else sizes)
                    builder.setProgress(PROGRESS_MAX, (state.progress * PROGRESS_MAX).toInt(), false)
                } else {
                    builder.setProgress(0, 0, true)
                }
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        DesktopStrings.settingsCancel,
                        PendingIntent.getService(
                            this,
                            1,
                            Intent(this, LibraryMoveService::class.java).setAction(ACTION_CANCEL),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    ).build(),
                )
            }
            is MoveState.Finishing -> {
                builder.setContentTitle(MoveUiStrings.finishing)
                builder.setProgress(0, 0, true)
            }
            else -> {
                builder.setContentTitle(MoveUiStrings.title)
                builder.setProgress(0, 0, true)
            }
        }
        return builder.build()
    }

    private fun resultNotification(title: String, text: String, done: Boolean): Notification =
        baseBuilder()
            .setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
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

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "library_move"
        private const val CHANNEL_NAME = "העברת הספרייה"
        private const val NOTIFICATION_ID = 4111
        private const val RESULT_NOTIFICATION_ID = 4112
        private const val PROGRESS_MAX = 1000
        private const val ACTION_CANCEL = "io.github.kdroidfilter.seforimapp.android.action.CANCEL_LIBRARY_MOVE"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LibraryMoveService::class.java)) }
        }
    }
}

/** Texts of the move's screen and notification (Android only). */
internal object MoveUiStrings {
    const val title = "העברת הספרייה"
    const val movingTo = "מעביר את הספרייה אל %1\$s"
    const val finishing = "בודק את העותק החדש ומשלים את ההעברה…"
    const val doneTitle = "הספרייה הועברה"
    const val doneText = "הספרייה נמצאת כעת ב%1\$s."
    const val failedTitle = "העברת הספרייה נכשלה"
}
