package com.ttsaistory.app.ui.reader

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ttsaistory.app.model.AppPreferenceKeys

@Stable
internal class ReaderTabPrefsBridge(private val prefs: SharedPreferences) {
    private val bookmarkKey = AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX
    private val bookmarkStoryKey = AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_STORY_ID
    private val fontKeys =
        setOf(
            AppPreferenceKeys.KEY_EDITOR_FONT_FULL_TEXT_PATH,
            AppPreferenceKeys.KEY_EDITOR_FONT_PARAGRAPH_PATH,
            AppPreferenceKeys.KEY_EDITOR_FONT_SCAN_DIR,
            AppPreferenceKeys.KEY_EDITOR_LINE_SPACING_MULTIPLIER,
            AppPreferenceKeys.KEY_EDITOR_FONT_SIZE_SP,
        )

    var fontPrefsEpoch by mutableIntStateOf(0)
        internal set

    var trackedLastReadingParagraphIndex by mutableIntStateOf(prefs.getInt(bookmarkKey, -1))
        internal set

    var trackedLastReadingParagraphStoryId by mutableLongStateOf(prefs.getLong(bookmarkStoryKey, -1L))
        internal set

    fun refreshBookmarkFromPrefs() {
        trackedLastReadingParagraphIndex = prefs.getInt(bookmarkKey, -1)
        trackedLastReadingParagraphStoryId = prefs.getLong(bookmarkStoryKey, -1L)
    }

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == null || changedKey == bookmarkKey || changedKey == bookmarkStoryKey) {
                trackedLastReadingParagraphIndex = prefs.getInt(bookmarkKey, -1)
                trackedLastReadingParagraphStoryId = prefs.getLong(bookmarkStoryKey, -1L)
            }
            if (changedKey != null && changedKey in fontKeys) {
                fontPrefsEpoch++
            }
        }

    fun register() {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        trackedLastReadingParagraphIndex = prefs.getInt(bookmarkKey, -1)
        trackedLastReadingParagraphStoryId = prefs.getLong(bookmarkStoryKey, -1L)
    }

    fun unregister() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }
}

@Composable
internal fun rememberReaderTabPrefsBridge(prefs: SharedPreferences): ReaderTabPrefsBridge {
    val bridge = remember(prefs) { ReaderTabPrefsBridge(prefs) }
    DisposableEffect(prefs) {
        bridge.register()
        onDispose { bridge.unregister() }
    }
    return bridge
}
