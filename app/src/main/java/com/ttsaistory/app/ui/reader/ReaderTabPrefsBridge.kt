package com.ttsaistory.app.ui.reader

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ttsaistory.app.model.AppPreferenceKeys

@Stable
internal class ReaderTabPrefsBridge(private val prefs: SharedPreferences) {
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

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey != null && changedKey in fontKeys) {
                fontPrefsEpoch++
            }
        }

    fun register() {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
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
