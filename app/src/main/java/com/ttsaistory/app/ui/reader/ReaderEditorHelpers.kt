package com.ttsaistory.app.ui.reader

import android.widget.EditText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ttsaistory.app.domain.sanitizeParagraphText
import com.ttsaistory.app.model.AppEditorConstants

/**
 * Gán trong [ReaderTab] khi `paragraphSplitMode` để bottom bar gọi nối / tách / xóa
 * mà không cần đăng ký lại toàn bộ lambda từ scope lồng sâu.
 */
class ReaderParagraphSplitEditActionSink {
    var joinUp: () -> Unit = {}
    var splitAtCaret: () -> Unit = {}
    var deleteCell: () -> Unit = {}
}

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

/** Đăng ký xuống bottom bar: slider ô đoạn (chỉ xem) hoặc nút nối/tách/xóa (sửa ô), dán, con trỏ, cuộn đầu/cuối. */
data class ReaderBottomNavBridge(
    val paragraphSplitMode: Boolean,
    /** false khi chỉ xem: ẩn nút dán và bước con trỏ trái/phải trên bottom bar. */
    val showPasteAndCaretStep: Boolean,
    /** Chế độ theo đoạn chỉ xem: thanh chọn ô (0..max) trên bottom bar. */
    val showParagraphFocusSlider: Boolean,
    /**
     * Chế độ sửa theo đoạn: hàng nút cố định (nối lên / tách tại con trỏ / xóa ô) thay cho slider,
     * tránh co dãn vùng soạn thảo khi kéo slider.
     */
    val showParagraphSplitEditBar: Boolean = false,
    val paragraphSplitEditJoinUpEnabled: Boolean = false,
    val paragraphSplitEditDeleteEnabled: Boolean = false,
    val onParagraphSplitEditJoinUp: () -> Unit = {},
    val onParagraphSplitEditSplitAtCaret: () -> Unit = {},
    val onParagraphSplitEditDelete: () -> Unit = {},
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
     * Tổng số câu TTS — snapshot [ParagraphTextService.totalItemCount] (cập nhật khi parse / [splitIntoParagraphs]);
     * null khi chưa có tổng (ví dụ đang defer theo ô / merge).
     */
    val ttsSpeakableSentenceTotal: Int? = null,
    /** Đang chạy [splitIntoParagraphs] trên luồng nền — bottom bar có thể hiện dialog tiến trình. */
    val ttsSentenceSplitWorking: Boolean = false,
    /**
     * Khi chỉnh sửa truyện web: các dòng mô tả chương/URL đang trong hàng đợi tải nền (prefetch trang sau).
     */
    val webPrefetchChapterQueueLines: List<String> = emptyList(),
    /** Truyện thư viện đang mở là nguồn web (`online_page_url`). */
    val libraryWebStoryActive: Boolean = false,
    /**
     * Id truyện sắp được / đang được headless parse trong hàng đợi nền (chỉ xem hoặc prefetch sửa);
     * null khi không còn mục trong queue.
     */
    val webStoryQueueTargetStoryId: Long? = null,
)
