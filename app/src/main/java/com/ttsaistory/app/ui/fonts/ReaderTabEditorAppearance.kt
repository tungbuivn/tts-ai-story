/**
 * Gom trạng thái hiển thị tab đọc (đường dẫn font, [androidx.compose.ui.text.font.FontFamily],
 * cỡ chữ, khoảng cách dòng, [androidx.compose.ui.text.TextStyle] nền) từ prefs và epoch làm mới font để Compose remember.
 */
package com.ttsaistory.app.ui.fonts

import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.ttsaistory.app.model.AppPreferenceKeys

@Immutable
internal data class ReaderTabEditorAppearance(
    val fullEditorFontPath: String,
    val fullEditorFontFamily: FontFamily,
    val paragraphEditorFontFamily: FontFamily,
    val editorLineSpacingMultiplier: Float,
    val editorFontSizeSp: Float,
    val editorBodyStyle: TextStyle,
)

@Composable
internal fun rememberReaderTabEditorAppearance(
    prefs: SharedPreferences,
    fontPrefsEpoch: Int,
): ReaderTabEditorAppearance {
    val fullEditorFontPath =
        prefs.getString(AppPreferenceKeys.KEY_EDITOR_FONT_FULL_TEXT_PATH, "").orEmpty()
    val paragraphEditorFontPath =
        prefs.getString(AppPreferenceKeys.KEY_EDITOR_FONT_PARAGRAPH_PATH, "").orEmpty()
    val fullEditorFontFamily =
        remember(fullEditorFontPath, fontPrefsEpoch) {
            editorFontFamilyFromStoredPath(fullEditorFontPath)
        }
    val paragraphEditorFontFamily =
        remember(paragraphEditorFontPath, fontPrefsEpoch) {
            editorFontFamilyFromStoredPath(paragraphEditorFontPath)
        }
    val editorLineSpacingMultiplier =
        remember(fontPrefsEpoch) {
            prefs
                .getFloat(
                    AppPreferenceKeys.KEY_EDITOR_LINE_SPACING_MULTIPLIER,
                    AppPreferenceKeys.DEFAULT_EDITOR_LINE_SPACING_MULTIPLIER,
                )
                .coerceIn(1f, 2.5f)
        }
    val editorFontSizeSp =
        remember(fontPrefsEpoch) {
            prefs
                .getFloat(
                    AppPreferenceKeys.KEY_EDITOR_FONT_SIZE_SP,
                    AppPreferenceKeys.DEFAULT_EDITOR_FONT_SIZE_SP,
                )
                .coerceIn(12f, 28f)
        }
    val bodyLargeFromTheme = MaterialTheme.typography.bodyLarge
    val editorBodyStyle =
        remember(bodyLargeFromTheme, editorFontSizeSp) {
            bodyLargeFromTheme.copy(fontSize = editorFontSizeSp.sp)
        }
    return ReaderTabEditorAppearance(
        fullEditorFontPath = fullEditorFontPath,
        fullEditorFontFamily = fullEditorFontFamily,
        paragraphEditorFontFamily = paragraphEditorFontFamily,
        editorLineSpacingMultiplier = editorLineSpacingMultiplier,
        editorFontSizeSp = editorFontSizeSp,
        editorBodyStyle = editorBodyStyle,
    )
}
