package com.ttsaistory.app

import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Log chẩn đoán ANR / main-thread: lọc logcat `adb logcat -s TtsAnrDiag:*`.
 * Chỉ ghi khi [BuildConfig.DEBUG].
 */
object AnrDiagLog {
    const val TAG = "TtsAnrDiag"

    fun isEnabled(): Boolean = BuildConfig.DEBUG

    private fun prefix(): String {
        val main = Looper.myLooper() == Looper.getMainLooper()
        return "[${Thread.currentThread().name}${if (main) "|MAIN" else ""}]"
    }

    fun i(message: String) {
        if (!isEnabled()) return
        // ERROR để dễ thấy trên ROM lọc log mức thấp (logcat -s TtsAnrDiag:E hoặc *:E).
        Log.e(TAG, "${prefix()} $message")
    }

    /** Bắt đầu khối có thể chậm — đối chiếu với [end] trong log. */
    fun begin(section: String): Long {
        if (!isEnabled()) return 0L
        val t = SystemClock.elapsedRealtime()
        Log.e(TAG, "${prefix()} BEGIN $section")
        return t
    }

    fun end(section: String, startElapsed: Long) {
        if (!isEnabled()) return
        val dt = SystemClock.elapsedRealtime() - startElapsed
        Log.e(TAG, "${prefix()} END $section (${dt}ms)")
    }
}
