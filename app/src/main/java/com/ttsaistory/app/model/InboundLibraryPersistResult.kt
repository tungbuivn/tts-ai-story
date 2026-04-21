package com.ttsaistory.app.model

data class InboundLibraryPersistResult(
    val cleanedText: String,
    val storyId: Long,
    val savedTitle: String,
)
