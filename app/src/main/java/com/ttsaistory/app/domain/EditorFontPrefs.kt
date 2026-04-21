package com.ttsaistory.app.domain

import android.content.SharedPreferences
import com.ttsaistory.app.model.AppPreferenceKeys
import java.io.File
import java.util.Locale

fun SharedPreferences.setEditorFontPathForBothRegions(absolutePath: String) {
    edit()
        .putString(AppPreferenceKeys.KEY_EDITOR_FONT_FULL_TEXT_PATH, absolutePath)
        .putString(AppPreferenceKeys.KEY_EDITOR_FONT_PARAGRAPH_PATH, absolutePath)
        .apply()
}

fun SharedPreferences.clearEditorFontDialogPrefs() {
    edit()
        .remove(AppPreferenceKeys.KEY_EDITOR_FONT_FULL_TEXT_PATH)
        .remove(AppPreferenceKeys.KEY_EDITOR_FONT_PARAGRAPH_PATH)
        .remove(AppPreferenceKeys.KEY_EDITOR_LINE_SPACING_MULTIPLIER)
        .remove(AppPreferenceKeys.KEY_EDITOR_FONT_SIZE_SP)
        .remove(AppPreferenceKeys.KEY_EDITOR_FONT_SCAN_TREE_URI)
        .apply()
}

fun SharedPreferences.editorFontChosenDisplayName(): String? {
    val p =
        getString(AppPreferenceKeys.KEY_EDITOR_FONT_FULL_TEXT_PATH, "").orEmpty().ifEmpty {
            getString(AppPreferenceKeys.KEY_EDITOR_FONT_PARAGRAPH_PATH, "").orEmpty()
        }
    return if (p.isEmpty()) null else File(p).name
}

fun editorFontImportSafeFileName(displayName: String): String {
    var base = File(displayName).name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(72)
    val lower = base.lowercase(Locale.US)
    if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
        base = "$base.ttf"
    }
    return base
}
