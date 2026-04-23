package com.ttsaistory.app.ui.reader

import androidx.compose.runtime.mutableIntStateOf

/**
 * Trạng thái đọc TTS dùng chung: tổng số câu có thể đọc và bookmark câu (0-based, -1 = chưa có).
 *
 * Dùng [androidx.compose.runtime.MutableIntState.intValue] trong `@Composable` để Compose
 * subscribe snapshot và recompose khi giá trị đổi (cập nhật từ prefs listener, bottom bar, v.v.).
 *
 * Tiến trình tách câu cho thanh công cụ / dialog "Đang tách câu" **không** dùng object này;
 * nó đi qua [ReaderBottomNavBridge.ttsSentenceSplitWorking] và [ReaderBottomNavBridge.ttsSpeakableSentenceTotal]
 * (đồng bộ [com.ttsaistory.app.domain.ParagraphTextService.totalItemCount]).
 */
object ReaderReadingProgress {
    /** Tổng số câu TTS (sau tách câu, đếm ô có nội dung) — bottom bar đồng bộ sau mỗi lần tính. */
    val totalSpeakableSentenceCount = mutableIntStateOf(0)

    /** Chỉ số câu đang đọc / bookmark trong prefs (0-based), `-1` khi chưa gán. */
    val currentSentenceIndex0Based = mutableIntStateOf(-1)
}
