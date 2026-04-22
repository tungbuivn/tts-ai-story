package com.ttsaistory.app.speech

/**
 * Engine phát tuần tự các đoạn văn (TTS hệ thống hoặc ElevenLabs).
 */
interface ParagraphSpeechEngine {

    /** Dừng phát của engine này (idempotent). */
    fun stopPlayback()

    /**
     * Bắt đầu phát [paragraphs] từ [startIndex].
     * @return true nếu đã queue / bắt đầu coroutine phát; false nếu không phát (toast lỗi qua callback).
     */
    fun startParagraphSequence(
        paragraphs: List<String>,
        startIndex: Int,
        callbacks: ParagraphSpeechSequenceCallbacks,
    ): Boolean

    /**
     * Chỉ TTS hệ thống dùng: sau mỗi utterance đoạn (xong/lỗi), enqueue đoạn kế nếu còn phiên phát chunked.
     */
    fun onSystemTtsParagraphUtteranceFinished(utteranceId: String?) {}
}
