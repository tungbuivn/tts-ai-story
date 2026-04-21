package com.ttsaistory.app.model

enum class TextTabSpeechEngine(val storageValue: String) {
    System("system"),
    ElevenLabs("elevenlabs"),
    ;

    companion object {
        fun fromStorage(value: String?): TextTabSpeechEngine =
            entries.find { it.storageValue == value } ?: System
    }
}
