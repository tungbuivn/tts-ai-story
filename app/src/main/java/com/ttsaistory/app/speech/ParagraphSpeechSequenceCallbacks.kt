package com.ttsaistory.app.speech

import kotlinx.coroutines.Job

/**
 * Callback từ engine phát đoạn (TTS hệ thống / ElevenLabs / VoxStudio) về UI / trạng thái ứng dụng.
 * Tất cả gọi trên main thread (engine đảm bảo).
 */
class ParagraphSpeechSequenceCallbacks(
    val onSpeakingParagraphIndex: (Int) -> Unit,
    val onErrorToast: (String) -> Unit,
    val onSystemQueuedUtteranceCount: (Int) -> Unit = {},
    /** Job của engine remote (ElevenLabs/VoxStudio) — UI dùng để bật/tắt nút Dừng. */
    val onRemoteTtsJob: (Job?) -> Unit = {},
    /** Engine remote đang chờ tổng hợp/đệm audio trước khi phát. */
    val onRemoteTtsPreparing: (Boolean) -> Unit = {},
    val onFullSequenceFinishedForLibraryAutoAdvance: () -> Unit = {},
)
