package com.ttsaistory.app.export

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.ttsaistory.app.R
import com.ttsaistory.app.domain.TtsExportPartsSnapshot
import com.ttsaistory.app.domain.exportFullTextToAacM4a
import com.ttsaistory.app.model.AppEditorConstants
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TtsAudioExportForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.ttsaistory.app.export.START"
        const val ACTION_CANCEL = "com.ttsaistory.app.export.CANCEL"
        const val EXTRA_PARTS_SNAPSHOT_PATH = "parts_snapshot_path"
        const val EXTRA_OUTPUT_NAME = "output_name"
        const val EXTRA_SPEECH_RATE = "speech_rate"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_VOICE_NAME = "voice_name"
        const val EXTRA_VOICE_LOCALE = "voice_locale"
        private const val NOTIF_PROGRESS = 7101
        private const val NOTIF_COMPLETE = 7102
    }

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.Main.immediate)
    private var exportJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        TtsExportNotifications.ensureChannels(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            exportJob?.cancel()
            if (exportJob == null) {
                finishForegroundQuietly()
                TtsExportUiCoordinator.clear()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            return START_NOT_STICKY
        }
        if (exportJob?.isActive == true) {
            return START_NOT_STICKY
        }

        val snapshotPath =
            intent.getStringExtra(EXTRA_PARTS_SNAPSHOT_PATH) ?: return START_NOT_STICKY
        val outputName = intent.getStringExtra(EXTRA_OUTPUT_NAME) ?: return START_NOT_STICKY
        val speechRate = intent.getFloatExtra(EXTRA_SPEECH_RATE, 1f)
        val pitch = intent.getFloatExtra(EXTRA_PITCH, 1f)
        val voiceName = intent.getStringExtra(EXTRA_VOICE_NAME)
        val voiceLocale = intent.getStringExtra(EXTRA_VOICE_LOCALE)

        val initialNotif =
            TtsExportNotifications.buildProgress(
                this,
                "Đang chuẩn bị…",
                "Nén AAC (.m4a): 0/?",
                0,
                1,
            )
        startForegroundTyped(initialNotif)

        exportJob =
            scope.launch {
                TtsExportUiCoordinator.setPreparing()
                try {
                    val parts =
                        withContext(Dispatchers.IO) {
                            val f = File(snapshotPath)
                            try {
                                TtsExportPartsSnapshot.decode(f.readText(Charsets.UTF_8))
                            } finally {
                                runCatching { f.delete() }
                            }
                        }

                    if (parts.isEmpty()) {
                        throw IllegalStateException("Snapshot xuất AAC rỗng hoặc không đọc được")
                    }

                    var wavMaxForNotif = 1
                    val savedPath =
                        exportFullTextToAacM4a(
                            applicationContext,
                            null,
                            parts,
                            outputName,
                            speechRate,
                            pitch,
                            { wavDone, wavTotal, queued, aacDone, aacTotal ->
                                wavMaxForNotif = wavTotal.coerceAtLeast(1)
                                TtsExportUiCoordinator.updateFromProgress(
                                    wavDone,
                                    wavTotal,
                                    queued,
                                    aacDone,
                                    aacTotal,
                                )
                                val wavDetail =
                                    "Tổng hợp WAV: $wavDone/$wavTotal · chờ: $queued/${AppEditorConstants.TTS_EXPORT_WAV_QUEUE_MAX}"
                                val aacDetail = "Nén AAC (.m4a): $aacDone/$aacTotal"
                                notificationManager.notify(
                                    NOTIF_PROGRESS,
                                    TtsExportNotifications.buildProgress(
                                        this@TtsAudioExportForegroundService,
                                        wavDetail,
                                        aacDetail,
                                        wavDone,
                                        wavMaxForNotif,
                                    ),
                                )
                            },
                            preferredTtsVoiceName = voiceName,
                            preferredTtsLocaleTag = voiceLocale,
                        )

                    finishForegroundQuietly()
                    notificationManager.cancel(NOTIF_PROGRESS)
                    notificationManager.notify(
                        NOTIF_COMPLETE,
                        TtsExportNotifications.buildComplete(
                            this@TtsAudioExportForegroundService,
                            savedPath,
                        ),
                    )
                    mainHandler.post {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.tts_export_notif_complete_text, savedPath),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                } catch (e: CancellationException) {
                    finishForegroundQuietly()
                    notificationManager.cancel(NOTIF_PROGRESS)
                    mainHandler.post {
                        Toast.makeText(
                            applicationContext,
                            "Đã hủy xuất AAC",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    throw e
                } catch (e: Exception) {
                    finishForegroundQuietly()
                    notificationManager.cancel(NOTIF_PROGRESS)
                    val msg = e.message ?: e.javaClass.simpleName
                    notificationManager.notify(
                        NOTIF_COMPLETE,
                        TtsExportNotifications.buildError(
                            this@TtsAudioExportForegroundService,
                            msg,
                        ),
                    )
                    mainHandler.post {
                        Toast.makeText(
                            applicationContext,
                            "Lỗi xuất AAC: $msg",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                } finally {
                    TtsExportUiCoordinator.clear()
                    exportJob = null
                    stopSelf()
                }
            }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        supervisor.cancel()
        super.onDestroy()
    }

    private fun startForegroundTyped(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIF_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_PROGRESS, notification)
        }
    }

    private fun finishForegroundQuietly() {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }
}
