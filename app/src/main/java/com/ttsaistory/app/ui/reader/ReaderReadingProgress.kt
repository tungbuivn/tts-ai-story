package com.ttsaistory.app.ui.reader

import androidx.compose.runtime.mutableIntStateOf

/**
 * Trạng thái TTS dùng chung: tổng số câu có thể đọc (bottom bar).
 *
 * Dùng [androidx.compose.runtime.MutableIntState.intValue] trong `@Composable` để Compose
 * subscribe snapshot khi giá trị đổi.
 *
 * Vị trí đọc lưu trong DB `last_speech_sentence_index` theo chương — không qua prefs bookmark.
 *
 * Tiến trình tách câu cho thanh công cụ / dialog "Đang tách câu" **không** dùng object này;
 * nó đi qua [ReaderBottomNavBridge.ttsSentenceSplitWorking] và [ReaderBottomNavBridge.ttsSpeakableSentenceTotal]
 * (đồng bộ [com.ttsaistory.app.domain.ParagraphTextService.totalItemCount]).
 */
object ReaderReadingProgress {
    /** Tổng số câu TTS (sau tách câu, đếm ô có nội dung) — bottom bar đồng bộ sau mỗi lần tính. */
    val totalSpeakableSentenceCount = mutableIntStateOf(0)
}
