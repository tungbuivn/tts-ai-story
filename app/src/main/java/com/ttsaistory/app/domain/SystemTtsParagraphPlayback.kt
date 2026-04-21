package com.ttsaistory.app.domain

import android.speech.tts.TextToSpeech

const val TTS_UTTERANCE_PREFIX = "tts_para_"

fun parseTtsParagraphIndex(utteranceId: String?): Int? {
    return utteranceId
        ?.removePrefix(TTS_UTTERANCE_PREFIX)
        ?.toIntOrNull()
}

/**
 * Đọc từ paragraph [startIndex] đến hết; utteranceId = index gốc để highlight đúng ô.
 * @return cặp (thành công, số utterance đã queue) — dùng số utterance để biết khi nào đọc hết loạt.
 */
fun speakParagraphsSequential(
    tts: TextToSpeech?,
    paragraphs: List<String>,
    startIndex: Int = 0,
): Pair<Boolean, Int> {
    if (tts == null || paragraphs.isEmpty()) return false to 0
    val sanitized = paragraphs.map(::sanitizeParagraphText)
    var from = startIndex.coerceIn(0, sanitized.lastIndex.coerceAtLeast(0))
    while (from <= sanitized.lastIndex && sanitized[from].isEmpty()) {
        from++
    }
    if (from > sanitized.lastIndex) return false to 0
    val jobs =
        sanitized.indices
            .filter { it >= from && sanitized[it].isNotEmpty() }
            .map { i -> i to sanitized[i] }
    if (jobs.isEmpty()) return false to 0
    jobs.forEachIndexed { k, (origIdx, paragraph) ->
        val queueMode =
            if (k == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result =
            tts.speak(
                paragraph,
                queueMode,
                null,
                "$TTS_UTTERANCE_PREFIX$origIdx",
            )
        if (result == TextToSpeech.ERROR) return false to 0
    }
    return true to jobs.size
}
