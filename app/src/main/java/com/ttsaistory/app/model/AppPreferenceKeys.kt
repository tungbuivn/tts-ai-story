package com.ttsaistory.app.model

import android.content.SharedPreferences

object AppPreferenceKeys {
    const val PREF_NAME = "tts_ai_story_prefs"
    const val KEY_LAST_TEXT = "last_text_content"
    const val KEY_TEXT_TAB_SPEECH_ENGINE = "text_tab_speech_engine"
    const val KEY_SYSTEM_TTS_VOICE_NAME = "system_tts_voice_name"
    const val KEY_SYSTEM_TTS_SPEECH_RATE = "system_tts_speech_rate"
    const val KEY_SYSTEM_TTS_PITCH = "system_tts_pitch"
    const val KEY_SYSTEM_TTS_SAMPLE_TEXT = "system_tts_sample_text"
    const val KEY_ACTIVE_LIBRARY_STORY_ID = "active_library_story_id"

    /**
     * Tab Text: khi `true`, luôn ẩn bàn phím mềm (SOFT_INPUT_STATE_ALWAYS_HIDDEN).
     * Mặc định nếu chưa lưu pref: `false` — bàn phím hiện bình thường.
     */
    const val KEY_READER_FORCE_HIDE_SOFT_KEYBOARD = "reader_force_hide_soft_keyboard"

    /** Khoảng chờ (ms) trước lần ẩn IME lặp 1 sau focus / đổi ô — [hideSoftInputWhenReaderForceHidden]. */
    const val KEY_READER_IME_HIDE_DELAY_FIRST_MS = "reader_ime_hide_delay_first_ms"
    /** Khoảng chờ (ms) trước lần ẩn IME lặp 2. */
    const val KEY_READER_IME_HIDE_DELAY_SECOND_MS = "reader_ime_hide_delay_second_ms"

    const val DEFAULT_READER_IME_HIDE_DELAY_FIRST_MS = 20
    const val DEFAULT_READER_IME_HIDE_DELAY_SECOND_MS = 80

    const val DEFAULT_EDITOR_FONT_SCAN_DIR = "/storage/emulated/0/fonts"

    const val KEY_EDITOR_FONT_SCAN_DIR = "editor_font_scan_dir"
    const val KEY_EDITOR_FONT_SCAN_TREE_URI = "editor_font_scan_tree_uri"
    const val KEY_EDITOR_FONT_FULL_TEXT_PATH = "editor_font_full_text_path"
    const val KEY_EDITOR_FONT_PARAGRAPH_PATH = "editor_font_paragraph_path"

    const val KEY_EDITOR_LINE_SPACING_MULTIPLIER = "editor_line_spacing_multiplier"
    const val DEFAULT_EDITOR_LINE_SPACING_MULTIPLIER = 1.5f

    const val KEY_EDITOR_FONT_SIZE_SP = "editor_font_size_sp"
    const val DEFAULT_EDITOR_FONT_SIZE_SP = 16f

    val DEFAULT_SYSTEM_TTS_SAMPLE_TEXT =
        "Xin chào, đây là giọng đọc thử. Bạn có thể sửa đoạn văn này và bấm Phát mẫu để nghe trước khi đọc truyện."
}

fun SharedPreferences.saveLastText(value: String) {
    edit().putString(AppPreferenceKeys.KEY_LAST_TEXT, value).commit()
}
