/**
 * Ánh xạ đường dẫn font tệp (.ttf/.otf) sang [androidx.compose.ui.text.font.FontFamily] cho vùng soạn,
 * và tiện ích chiều cao dòng theo hệ số nhân so với [androidx.compose.ui.text.TextStyle].
 */
package com.ttsaistory.app.ui.fonts

import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

/** .ttf/.otf trên đĩa → [FontFamily]; lỗi/rỗng → [FontFamily.Default]. */
fun editorFontFamilyFromStoredPath(absolutePath: String?): FontFamily {
    val p = absolutePath?.trim().orEmpty()
    if (p.isEmpty()) return FontFamily.Default
    val f = File(p)
    if (!f.isFile || !f.canRead()) return FontFamily.Default
    val ext = f.extension.lowercase(Locale.US)
    if (ext != "ttf" && ext != "otf") return FontFamily.Default
    return runCatching {
        Typeface.createFromFile(f)
        FontFamily(Font(file = f, weight = FontWeight.Normal, style = FontStyle.Normal))
    }.getOrElse { FontFamily.Default }
}

/** Chiều cao dòng (sp) ≈ cỡ chữ × [multiplier]. */
fun editorLineHeightSp(base: TextStyle, multiplier: Float): TextUnit {
    val fs = base.fontSize
    return if (fs != TextUnit.Unspecified) {
        (fs.value * multiplier).sp
    } else {
        (16f * multiplier).sp
    }
}
