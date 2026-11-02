package com.ttsaistory.app.voxstudio

/** Hằng số dùng chung cho engine TTS VoxStudio (server self-hosted). */
object VoxStudioConfig {
    /** Tailscale HTTPS của server pro400; có thể đổi trong Cấu hình VoxStudio. */
    const val DEFAULT_BASE_URL: String = "https://pro400.tailaf56ea.ts.net"

    const val DEFAULT_ENGINE: String = "vieneu"
    const val SAMPLE_TEXT: String = "Xin chào, đây là giọng đọc thử từ VoxStudio."

    /** Server giới hạn file mẫu clone tối đa 15 MB. */
    const val MAX_UPLOAD_BYTES: Int = 15 * 1024 * 1024

    val ALLOWED_AUDIO_EXTENSIONS: Set<String> =
        setOf("wav", "mp3", "m4a", "ogg", "flac", "webm")

    /** Job VieNeu lần đầu có thể tải model vài phút; chờ tối đa 15 phút/đoạn. */
    const val JOB_TIMEOUT_MS: Long = 15 * 60 * 1000L
}
