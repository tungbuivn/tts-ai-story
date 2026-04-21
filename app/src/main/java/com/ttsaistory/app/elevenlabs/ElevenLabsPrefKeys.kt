package com.ttsaistory.app.elevenlabs

import android.content.SharedPreferences
import org.json.JSONObject

object ElevenLabsPrefKeys {
    const val API_KEY = "elevenlabs_api_key"
    const val VOICE_ID = "elevenlabs_voice_id"
    /** JSON: `{"vi":"voiceId","en":"...","auto":"..."}` — giọng riêng theo ngôn ngữ / chế độ tự động. */
    const val VOICE_BY_LANGUAGE_JSON = "elevenlabs_voice_by_language_json"
    const val MODEL_ID = "elevenlabs_model_id"
    const val LANGUAGE_CODE = "elevenlabs_language_code"

    /** Bucket lưu prefs: `vi`, `en`, `auto` (ứng với API không gửi language_code). */
    fun languageBucketForApiCode(apiLanguageCode: String?): String =
        if (apiLanguageCode.isNullOrBlank()) {
            "auto"
        } else {
            apiLanguageCode.trim().lowercase()
        }

    /**
     * Giọng dùng khi TTS: theo [apiLanguageCode] (null/"" = chế độ tự động → bucket `auto`),
     * fallback [VOICE_ID] rồi [ElevenLabsConfig.DEFAULT_VOICE_ID].
     */
    fun resolveVoiceIdForLanguage(
        prefs: SharedPreferences,
        apiLanguageCode: String?,
    ): String {
        val bucket = languageBucketForApiCode(apiLanguageCode)
        prefs.getString(VOICE_BY_LANGUAGE_JSON, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                val id = o.optString(bucket, "").trim()
                if (id.isNotEmpty()) return id
            }
        }
        return prefs.getString(VOICE_ID, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: ElevenLabsConfig.DEFAULT_VOICE_ID
    }

    /** [languageUiKey]: `vi` | `en` | `auto` */
    fun saveVoiceForLanguageUi(
        prefs: SharedPreferences,
        languageUiKey: String,
        voiceId: String,
    ) {
        val bucket =
            when (languageUiKey) {
                "vi" -> "vi"
                "en" -> "en"
                "auto" -> "auto"
                else -> "auto"
            }
        val trimmed = voiceId.trim()
        val base =
            runCatching { JSONObject(prefs.getString(VOICE_BY_LANGUAGE_JSON, null) ?: "{}") }
                .getOrElse { JSONObject() }
        base.put(bucket, trimmed)
        prefs
            .edit()
            .putString(VOICE_BY_LANGUAGE_JSON, base.toString())
            .putString(VOICE_ID, trimmed)
            .apply()
    }

    /** Key trong prefs, nếu trống thì dùng [ElevenLabsConfig.API_KEY] (mặc định build). */
    fun resolveApiKey(prefs: SharedPreferences): String {
        val fromPref = prefs.getString(API_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
        return fromPref ?: ElevenLabsConfig.API_KEY.trim()
    }

    fun saveApiKey(prefs: SharedPreferences, raw: String) {
        val t = raw.trim()
        prefs.edit().apply {
            if (t.isEmpty()) {
                remove(API_KEY)
            } else {
                putString(API_KEY, t)
            }
        }.apply()
    }
}
