package com.ttsaistory.app.elevenlabs

object ElevenLabsConfig {
    const val API_KEY: String = "sk_4bc9fa7d47e315bf86404497cedf7f6fb03a54580b5a0aa3"

    /** Giọng mặc định (Rachel, đa ngôn ngữ). Có thể đổi trong cấu hình sau. */
    const val DEFAULT_VOICE_ID: String = "21m00Tcm4TlvDq8ikWAM"

    const val MODEL_ID: String = "eleven_multilingual_v2"

    /** ISO 639-1 gửi lên API `language_code` (mặc định khi chưa cấu hình). */
    const val DEFAULT_LANGUAGE_CODE: String = "vi"
}
