package com.ttsaistory.app.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ttsaistory.app.MainActivity
import com.ttsaistory.app.R

object TtsExportNotifications {
    const val CHANNEL_PROGRESS_ID = "tts_export_progress"
    const val CHANNEL_COMPLETE_ID = "tts_export_complete"
    private const val REQUEST_OPEN_APP = 0
    private const val REQUEST_CANCEL = 1

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val progressCh =
            NotificationChannel(
                CHANNEL_PROGRESS_ID,
                context.getString(R.string.tts_export_notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        val completeCh =
            NotificationChannel(
                CHANNEL_COMPLETE_ID,
                context.getString(R.string.tts_export_notif_channel_complete),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        nm.createNotificationChannel(progressCh)
        nm.createNotificationChannel(completeCh)
    }

    fun openAppPendingIntent(context: Context): PendingIntent {
        val i =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val mut = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, REQUEST_OPEN_APP, i, mut)
    }

    fun cancelExportPendingIntent(context: Context): PendingIntent {
        val i =
            Intent(context, TtsAudioExportForegroundService::class.java).apply {
                action = TtsAudioExportForegroundService.ACTION_CANCEL
            }
        val mut = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(context, REQUEST_CANCEL, i, mut)
    }

    fun buildProgress(
        context: Context,
        wavDetail: String,
        aacDetail: String,
        wavProgress: Int,
        wavMax: Int,
    ): Notification {
        val max = wavMax.coerceAtLeast(1)
        val prog = wavProgress.coerceIn(0, max)
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.tts_export_notif_progress_title))
            .setContentText(wavDetail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$wavDetail\n$aacDetail"))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(max, prog, false)
            .addAction(
                0,
                context.getString(R.string.tts_export_notif_action_cancel),
                cancelExportPendingIntent(context),
            )
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildComplete(context: Context, savedPath: String): Notification {
        val text = context.getString(R.string.tts_export_notif_complete_text, savedPath)
        return NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.tts_export_notif_complete_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppPendingIntent(context))
            .setAutoCancel(true)
            .build()
    }

    fun buildError(context: Context, message: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.tts_export_notif_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppPendingIntent(context))
            .setAutoCancel(true)
            .build()
    }
}
