package com.ttsaistory.app.speech

import com.ttsaistory.app.model.TextTabSpeechEngine

/** Chọn engine phát đoạn theo tab (TTS hệ thống / ElevenLabs / VoxStudio). */
object ParagraphSpeechEngines {

    /** Dừng mọi engine đã truyền vào (idempotent). */
    fun stopAll(vararg engines: ParagraphSpeechEngine) {
        engines.forEach { it.stopPlayback() }
    }

    fun select(
        type: TextTabSpeechEngine,
        system: ParagraphSpeechEngine,
        eleven: ParagraphSpeechEngine,
        voxStudio: ParagraphSpeechEngine,
    ): ParagraphSpeechEngine =
        when (type) {
            TextTabSpeechEngine.System -> system
            TextTabSpeechEngine.ElevenLabs -> eleven
            TextTabSpeechEngine.VoxStudio -> voxStudio
        }
}
