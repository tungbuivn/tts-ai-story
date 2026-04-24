package com.ttsaistory.app.domain

import android.speech.tts.TextToSpeech

const val TTS_UTTERANCE_PREFIX = "tts_para_"

fun parseTtsParagraphIndex(utteranceId: String?): Int? {
    return utteranceId
        ?.removePrefix(TTS_UTTERANCE_PREFIX)
        ?.toIntOrNull()
}

/**
 * Danh sách (index đoạn gốc → nội dung đã sanitize) để phát tuần tự từ [startIndex].
 */
fun buildParagraphSpeakJobs(
    paragraphs: List<String>,
    startIndex: Int,
): List<Pair<Int, String>> {
    if (paragraphs.isEmpty()) return emptyList()
    val sanitized = paragraphs.map(::sanitizeParagraphText)
    var from = startIndex.coerceIn(0, sanitized.lastIndex.coerceAtLeast(0))
    while (from <= sanitized.lastIndex && sanitized[from].isEmpty()) {
        from++
    }
    if (from > sanitized.lastIndex) return emptyList()
    return sanitized.indices
        .filter { it >= from && sanitized[it].isNotEmpty() }
        .map { i -> i to sanitized[i] }
}

fun speakParagraphUtterance(
    tts: TextToSpeech?,
    originalParagraphIndex: Int,
    text: String,
    queueMode: Int,
): Boolean {
    if (tts == null) return false
    val result =
        tts.speak(
            text,
            queueMode,
            null,
            "$TTS_UTTERANCE_PREFIX$originalParagraphIndex",
        )
    return result != TextToSpeech.ERROR
}

/**
 * Đọc từ paragraph [startIndex] đến hết; utteranceId = index gốc để highlight đúng ô.
 * @return cặp (thành công, số utterance đã queue) — dùng số utterance để biết khi nào đọc hết loạt.
 */
/** Đếm từ (khoảng trắng) cho ước lượng từ/phút khi phát TTS — đồng bộ với nội dung đã sanitize gửi engine. */
fun wordCountForTtsPlaybackWpm(text: String): Int {
    val t = text.trim()
    if (t.isEmpty()) return 0
    return t.split(Regex("\\s+")).count { it.isNotEmpty() }
}

fun speakParagraphsSequential(
    tts: TextToSpeech?,
    paragraphs: List<String>,
    startIndex: Int = 0,
): Pair<Boolean, Int> {
    val jobs = buildParagraphSpeakJobs(paragraphs, startIndex)
    if (jobs.isEmpty() || tts == null) return false to 0
    jobs.forEachIndexed { k, (origIdx, paragraph) ->
        val queueMode =
            if (k == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (!speakParagraphUtterance(tts, origIdx, paragraph, queueMode)) return false to 0
    }
    return true to jobs.size
}
