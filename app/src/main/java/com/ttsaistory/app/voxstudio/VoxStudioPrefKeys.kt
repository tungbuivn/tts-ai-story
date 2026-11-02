package com.ttsaistory.app.voxstudio

import android.content.SharedPreferences

/** Khoá SharedPreferences + đọc/ghi cấu hình engine VoxStudio. */
object VoxStudioPrefKeys {
    const val BASE_URL = "voxstudio_base_url"
    const val API_TOKEN = "voxstudio_api_token"
    const val ENGINE = "voxstudio_engine"
    const val VOICE = "voxstudio_voice"
    const val RATE_PCT = "voxstudio_rate_pct"
    const val VOLUME_PCT = "voxstudio_volume_pct"

    data class Config(
        val baseUrl: String,
        val token: String,
        val engine: String,
        /** `""` = để server tự chọn giọng mặc định. */
        val voice: String,
        val ratePct: Int,
        val volumePct: Int,
    )

    fun resolveBaseUrl(prefs: SharedPreferences): String =
        VoxStudioApi.normalizeBaseUrl(
            prefs.getString(BASE_URL, null)?.trim().orEmpty(),
        )

    fun resolveToken(prefs: SharedPreferences): String =
        prefs.getString(API_TOKEN, null)?.trim().orEmpty()

    fun resolveEngine(prefs: SharedPreferences): String =
        prefs.getString(ENGINE, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: VoxStudioConfig.DEFAULT_ENGINE

    fun resolveVoice(prefs: SharedPreferences): String =
        prefs.getString(VOICE, null)?.trim().orEmpty()

    fun resolveRatePct(prefs: SharedPreferences): Int =
        prefs.getInt(RATE_PCT, 0).coerceIn(-50, 100)

    fun resolveVolumePct(prefs: SharedPreferences): Int =
        prefs.getInt(VOLUME_PCT, 100).coerceIn(0, 200)

    fun resolveConfig(prefs: SharedPreferences): Config =
        Config(
            baseUrl = resolveBaseUrl(prefs),
            token = resolveToken(prefs),
            engine = resolveEngine(prefs),
            voice = resolveVoice(prefs),
            ratePct = resolveRatePct(prefs),
            volumePct = resolveVolumePct(prefs),
        )

    /** Đủ cấu hình để gọi API: chỉ cần token (base URL có mặc định). */
    fun isConfigured(prefs: SharedPreferences): Boolean = resolveToken(prefs).isNotEmpty()

    fun saveConnection(prefs: SharedPreferences, baseUrl: String, token: String) {
        prefs
            .edit()
            .putString(BASE_URL, VoxStudioApi.normalizeBaseUrl(baseUrl))
            .putString(API_TOKEN, token.trim())
            .apply()
    }

    fun saveEngine(prefs: SharedPreferences, engineId: String) {
        prefs.edit().putString(ENGINE, engineId.trim()).apply()
    }

    fun saveVoice(prefs: SharedPreferences, voiceId: String) {
        prefs.edit().putString(VOICE, voiceId.trim()).apply()
    }

    fun saveRatePct(prefs: SharedPreferences, value: Int) {
        prefs.edit().putInt(RATE_PCT, value.coerceIn(-50, 100)).apply()
    }

    fun saveVolumePct(prefs: SharedPreferences, value: Int) {
        prefs.edit().putInt(VOLUME_PCT, value.coerceIn(0, 200)).apply()
    }
}
