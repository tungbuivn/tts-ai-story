package com.ttsaistory.app

import android.app.Application
import android.os.StrictMode
import com.ttsaistory.app.export.TtsExportNotifications

class TtsStoryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TtsExportNotifications.ensureChannels(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build(),
            )
            AnrDiagLog.i("TtsStoryApp onCreate StrictMode(thread+vm)=penaltyLog")
        }
    }
}
