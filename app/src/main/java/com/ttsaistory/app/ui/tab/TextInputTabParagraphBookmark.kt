package com.ttsaistory.app.ui.tab

import androidx.compose.ui.text.input.TextFieldValue
import com.ttsaistory.app.domain.sanitizeParagraphText

/**
 * Giống [editorUiFlatToTtsParagraphStartIndex] nhưng duyệt lưới [TextFieldValue] một lần,
 * không tạo `map { it.text }` cho mọi ô.
 */
internal fun editorFlatToTtsBookmarkIndex(
    fieldGroups: List<List<TextFieldValue>>,
    editorUiFlat: Int,
): Int {
    if (fieldGroups.isEmpty()) return 0
    val cellCount = fieldGroups.sumOf { it.size }
    if (cellCount == 0) return 0
    var k = editorUiFlat.coerceIn(0, cellCount - 1)
    while (k < cellCount) {
        val t = cellTextAtFlatIndex(fieldGroups, k)
        if (sanitizeParagraphText(t).isNotEmpty()) break
        k++
    }
    if (k >= cellCount) return 0
    var tts = 0
    var flat = 0
    for (row in fieldGroups) {
        for (tf in row) {
            if (flat >= k) return tts
            if (sanitizeParagraphText(tf.text).isNotEmpty()) tts++
            flat++
        }
    }
    return tts
}

internal fun cellTextAtFlatIndex(
    fieldGroups: List<List<TextFieldValue>>,
    targetFlat: Int,
): String {
    var f = 0
    for (row in fieldGroups) {
        for (tf in row) {
            if (f == targetFlat) return tf.text
            f++
        }
    }
    return ""
}
