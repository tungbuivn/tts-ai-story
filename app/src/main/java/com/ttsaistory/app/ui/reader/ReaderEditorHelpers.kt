package com.ttsaistory.app.ui.reader

import android.widget.EditText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ttsaistory.app.domain.sanitizeParagraphText
import com.ttsaistory.app.model.AppEditorConstants

/** Bỏ ô con blank; bỏ đoạn không còn ô; luôn ít nhất một đoạn một ô (có thể rỗng để gõ). */
fun compactParagraphGroupFieldValues(
    gl: List<List<TextFieldValue>>,
): List<List<TextFieldValue>> {
    val rows =
        gl.mapNotNull { row ->
            val kept = row.filter { sanitizeParagraphText(it.text).isNotEmpty() }
            if (kept.isEmpty()) null else kept
        }
    return if (rows.isEmpty()) listOf(listOf(TextFieldValue("", TextRange(0)))) else rows
}

fun moveCaretLeftInField(cur: TextFieldValue): TextFieldValue {
    val sel = cur.selection
    val a = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
    val b = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
    val caret = if (a != b) a else (a - 1).coerceAtLeast(0)
    return TextFieldValue(cur.text, TextRange(caret))
}

fun moveCaretRightInField(cur: TextFieldValue): TextFieldValue {
    val sel = cur.selection
    val a = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
    val b = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
    val len = cur.text.length
    val caret = if (a != b) b else (b + 1).coerceAtMost(len)
    return TextFieldValue(cur.text, TextRange(caret))
}

fun fullTextBlockBodyForToolbar(
    paragraphSplitMode: Boolean,
    paragraphMerged: String,
    parentText: String,
): String = if (paragraphSplitMode) paragraphMerged else parentText

fun fullTextBlockCaretForToolbar(
    paragraphSplitMode: Boolean,
    parentText: String,
    fullTextFieldValue: TextFieldValue,
    nativeEdit: EditText?,
): Int {
    if (paragraphSplitMode) return 0
    val et = nativeEdit
    if (et != null && parentText.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS) {
        return et.selectionStart.coerceIn(0, parentText.length)
    }
    return fullTextFieldValue.selection.start.coerceIn(0, parentText.length)
}

/** Đăng ký xuống bottom bar: slider ô đoạn, dán, con trỏ trái/phải, cuộn đầu/cuối (tách đoạn) hoặc con trỏ đầu/cuối (toàn bộ). */
data class ReaderBottomNavBridge(
    val paragraphSplitMode: Boolean,
    /** false khi chỉ xem: ẩn nút dán và bước con trỏ trái/phải trên bottom bar. */
    val showPasteAndCaretStep: Boolean,
    /** Chế độ theo đoạn: thanh chọn ô (0..max) trên bottom bar. */
    val showParagraphFocusSlider: Boolean,
    val paragraphFocusSliderMax: Int,
    val paragraphFocusSliderValue: Int,
    val onParagraphFocusSliderChange: (Int) -> Unit,
    /** Sau khi thả tay trên slider hoặc bấm +/-: đưa con trỏ vào ô (một lần), tránh requestFocus liên tục khi kéo. */
    val onParagraphFocusSliderFocusCommitted: () -> Unit,
    /**
     * Chế độ tách đoạn: chỉ số câu TTS 1-based tương ứng ô đang chọn (khớp slider / chạm ô).
     * null = dòng trạng thái dùng bookmark prefs / mặc định.
     */
    val readerProgressCurrentOneBased: Int? = null,
    val pasteFromClipboard: () -> Unit,
    val moveCaretLeft: () -> Unit,
    val moveCaretRight: () -> Unit,
    val goTopOrCaretStart: () -> Unit,
    val goBottomOrCaretEnd: () -> Unit,
    /**
     * Tổng số câu TTS (sau [sanitizeParagraphText], bỏ rỗng) khớp [splitIntoParagraphs] trên nguồn
     * thanh công cụ Phát; null nếu chưa có kết quả sau lần tách gần nhất.
     */
    val ttsSpeakableSentenceTotal: Int? = null,
    /** Đang chạy [splitIntoParagraphs] trên luồng nền — bottom bar có thể hiện dialog tiến trình. */
    val ttsSentenceSplitWorking: Boolean = false,
)
