package com.ttsaistory.app.speech

import kotlinx.coroutines.Job

/**
 * Callback từ engine phát đoạn (TTS hệ thống / ElevenLabs) về UI / trạng thái ứng dụng.
 * Tất cả gọi trên main thread (engine đảm bảo).
 */
class ParagraphSpeechSequenceCallbacks(
    val onSpeakingParagraphIndex: (Int) -> Unit,
    val onErrorToast: (String) -> Unit,
    val onSystemQueuedUtteranceCount: (Int) -> Unit = {},
    val onElevenLabsJob: (Job?) -> Unit = {},
    val onFullSequenceFinishedForLibraryAutoAdvance: () -> Unit = {},
)
