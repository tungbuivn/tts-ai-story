package com.ttsaistory.app.speech

/**
 * Nhận sự kiện utterance từ [android.speech.tts.TextToSpeech] (sau khi đưa về main thread).
 */
interface SystemTtsUtteranceProgressSink {
    fun onUtteranceStart(utteranceId: String?)

    fun onUtteranceDone(utteranceId: String?)

    fun onUtteranceError(utteranceId: String?)
}
