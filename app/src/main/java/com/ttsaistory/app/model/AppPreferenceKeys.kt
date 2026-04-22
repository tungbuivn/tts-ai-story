package com.ttsaistory.app.model

import android.content.SharedPreferences

object AppPreferenceKeys {
    const val PREF_NAME = "tts_ai_story_prefs"
    const val KEY_LAST_TEXT = "last_text_content"
    const val KEY_LAST_READING_PARAGRAPH_INDEX = "last_reading_paragraph_index"
    /**
     * Truyện thư viện gắn với [KEY_LAST_READING_PARAGRAPH_INDEX] (last story speaking id);
     * -1 = văn bản không gắn file thư viện. Luôn cập nhật cùng lúc với DB `last_speech_sentence_index`
     * qua [com.ttsaistory.app.data.StoryLibraryRepository.updateLastSpeechSentenceIndex].
     */
    const val KEY_LAST_READING_PARAGRAPH_STORY_ID = "last_reading_paragraph_story_id"
    const val KEY_TEXT_TAB_SPEECH_ENGINE = "text_tab_speech_engine"
    const val KEY_SYSTEM_TTS_VOICE_NAME = "system_tts_voice_name"
    const val KEY_SYSTEM_TTS_SPEECH_RATE = "system_tts_speech_rate"
    const val KEY_SYSTEM_TTS_PITCH = "system_tts_pitch"
    const val KEY_SYSTEM_TTS_SAMPLE_TEXT = "system_tts_sample_text"
    const val KEY_ACTIVE_LIBRARY_STORY_ID = "active_library_story_id"

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

fun SharedPreferences.Editor.putLastReadingBookmark(
    paragraphIndex0Based: Int,
    libraryStoryId: Long?,
): SharedPreferences.Editor {
    putInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, paragraphIndex0Based)
    if (paragraphIndex0Based < 0 || libraryStoryId == null || libraryStoryId <= 0L) {
        putLong(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_STORY_ID, -1L)
    } else {
        putLong(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_STORY_ID, libraryStoryId)
    }
    return this
}

fun SharedPreferences.clearLastReadingBookmark() {
    edit()
        .putInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, -1)
        .putLong(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_STORY_ID, -1L)
        .apply()
}

/**
 * Chỉ số câu trong prefs có cùng ngữ cảnh truyện với [activeLibraryStoryId] không.
 * Cài đặt cũ không có [AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_STORY_ID] → luôn true (giữ hành vi cũ).
 */
fun SharedPreferences.lastReadingBookmarkAppliesToStory(activeLibraryStoryId: Long?): Boolean {
    if (!contains(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_STORY_ID)) {
        return true
    }
    val sid = getLong(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_STORY_ID, -1L)
    return when {
        activeLibraryStoryId == null -> sid <= 0L
        else -> sid == activeLibraryStoryId
    }
}
