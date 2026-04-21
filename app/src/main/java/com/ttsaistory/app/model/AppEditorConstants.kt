package com.ttsaistory.app.model

object AppEditorConstants {
    /** Chỉ đẩy text lên parent sau khi ngừng sửa ô câu (tránh parse/tách lại mỗi phím). */
    const val PARAGRAPH_FIELD_PERSIST_DEBOUNCE_MS = 1000L

    /** Chế độ một khối: dùng EditText khi văn bản ≥ ngưỡng (Compose quá nặng với văn cực dài). */
    const val FULL_TEXT_NATIVE_EDITOR_MIN_CHARS = 120_000

    /** Debounce tách đoạn cho trạng thái nút Play (không ảnh hưởng onClick — vẫn tính lúc bấm). */
    const val PLAY_TOOLBAR_SPLIT_DEBOUNCE_MS = 160L

    /** Ghi file truyện thư viện đang mở sau khi ngừng sửa (đồng bộ với tab Text). */
    const val LIBRARY_FILE_AUTOSAVE_DEBOUNCE_MS = 1_200L

    /** Số file WAV đoạn tối đa chờ trong hàng đợi trước khi nạp vào AAC (producer bị chặn khi đầy). */
    const val TTS_EXPORT_WAV_QUEUE_MAX = 10

    /** Bitrate AAC-LC khi xuất .m4a. */
    const val TTS_EXPORT_AAC_BITRATE = 128_000
}
