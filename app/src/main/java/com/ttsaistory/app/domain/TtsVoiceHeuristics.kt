package com.ttsaistory.app.domain

import android.speech.tts.Voice
import java.util.Locale

/** Giọng TTS hệ thống coi là tiếng Việt (locale hoặc gợi ý trong tên — một số engine báo locale null). */
fun isVietnameseTtsVoice(voice: Voice): Boolean {
    val loc = voice.locale
    if (loc != null) {
        if (loc.language.equals("vi", ignoreCase = true)) return true
        if (loc.toLanguageTag().startsWith("vi-", ignoreCase = true)) return true
    }
    val n = voice.name.lowercase(Locale.ROOT)
    return n.contains("vi-vn") ||
        n.contains("-vi-") ||
        n.contains("_vi_") ||
        n.contains("vietnamese") ||
        n.endsWith("-vi") ||
        n.endsWith("_vi")
}
