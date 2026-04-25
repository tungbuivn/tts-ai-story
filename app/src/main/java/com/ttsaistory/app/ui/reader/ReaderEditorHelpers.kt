package com.ttsaistory.app.ui.reader

import android.widget.EditText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ttsaistory.app.domain.sanitizeParagraphText
import com.ttsaistory.app.model.AppEditorConstants
import java.util.Locale

/**
 * Gán trong [ReaderTab] khi `paragraphSplitMode` để bottom bar gọi nối / tách / xóa
 * mà không cần đăng ký lại toàn bộ lambda từ scope lồng sâu.
 */
class ReaderParagraphSplitEditActionSink {
    var joinUp: () -> Unit = {}
    var splitAtCaret: () -> Unit = {}
    var deleteCell: () -> Unit = {}
    var toggleSentenceCase: () -> Unit = {}
}

/**
 * Mỗi dòng (theo `\n`): chữ thường, viết hoa ký tự chữ cái đầu tiên của dòng (nếu có).
 */
/**
 * `true` = ô chưa toàn chữ HOA theo [String.uppercase] → nút hiện **AA** (bấm để in hoa);
 * `false` = đã bằng bản in hoa → nút hiện **Aa** (bấm để chuẩn hoá đầu dòng).
 * Chuỗi rỗng: coi như chưa “toàn HOA có nghĩa” → ưu tiên **AA**.
 */
fun paragraphSplitCaseToggleOffersUppercase(
    text: String,
    locale: Locale = Locale.getDefault(),
): Boolean {
    if (text.isEmpty()) return true
    return text != text.uppercase(locale)
}

fun normalizeParagraphSplitCellSentenceCase(
    text: String,
    locale: Locale = Locale.getDefault(),
): String =
    text.split("\n").joinToString("\n") { line ->
        if (line.isEmpty()) {
            line
        } else {
            val lower = line.lowercase(locale)
            lower.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
            }
        }
    }

/** Bỏ ô con blank; bỏ hàng không còn ô; luôn ít nhất một hàng một ô (có thể rỗng để gõ). */
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

/** Đăng ký xuống bottom bar: slider ô câu (chỉ xem) hoặc nút nối/tách/xóa (sửa ô), dán, con trỏ, cuộn đầu/cuối. */
data class ReaderBottomNavBridge(
    val paragraphSplitMode: Boolean,
    /** false khi chỉ xem: ẩn nút dán và bước con trỏ trái/phải trên bottom bar. */
    val showPasteAndCaretStep: Boolean,
    /** Chế độ theo câu chỉ xem: thanh chọn ô (0..max) trên bottom bar. */
    val showParagraphFocusSlider: Boolean,
    /**
     * Chế độ sửa theo câu: hàng nút cố định (Aa/AA / nối lên / tách tại con trỏ / xóa ô) thay cho slider,
     * tránh co dãn vùng soạn thảo khi kéo slider.
     */
    val showParagraphSplitEditBar: Boolean = false,
    val paragraphSplitEditJoinUpEnabled: Boolean = false,
    val paragraphSplitEditDeleteEnabled: Boolean = false,
    /**
     * Suy ra từ nội dung ô đang focus: `true` = chưa toàn HOA → hiện **AA**;
     * `false` = đã `text == text.uppercase()` → hiện **Aa**.
     */
    val paragraphSplitEditCaseNextIsUpper: Boolean = true,
    val onParagraphSplitEditJoinUp: () -> Unit = {},
    val onParagraphSplitEditSplitAtCaret: () -> Unit = {},
    val onParagraphSplitEditDelete: () -> Unit = {},
    /**
     * Tách chương: từ câu TTS hiện tại (đang phát, hoặc câu đầu ở ô đang sửa) đến hết → chương mới;
     * chương đang mở chỉ còn phần trước mốc đó.
     */
    val paragraphSplitEditBreakPageEnabled: Boolean = false,
    val onParagraphSplitEditBreakPage: () -> Unit = {},
    val onParagraphSplitEditCaseToggle: () -> Unit = {},
    /** Luôn ẩn IME khi đang tab Text; bật/tắt qua bottom bar, lưu prefs. */
    val readerKeyboardForceHidden: Boolean = false,
    val onReaderKeyboardForceHiddenToggle: () -> Unit = {},
    val paragraphFocusSliderMax: Int,
    val paragraphFocusSliderValue: Int,
    val onParagraphFocusSliderChange: (Int) -> Unit,
    /** Sau khi thả tay trên slider hoặc bấm +/-: đưa con trỏ vào ô (một lần), tránh requestFocus liên tục khi kéo. */
    val onParagraphFocusSliderFocusCommitted: () -> Unit,
    /**
     * Chế độ lưới câu: chỉ số câu TTS 1-based tương ứng ô đang chọn (khớp slider / chạm ô).
     * null = dòng trạng thái dùng [dbLastSpeechSentenceIndex0] / mặc định.
     */
    val readerProgressCurrentOneBased: Int? = null,
    /**
     * Chương thư viện đang mở: `last_speech_sentence_index` trong DB (0-based), `-1` = chưa có / không thư viện.
     */
    val dbLastSpeechSentenceIndex0: Int = -1,
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

/**
 * Gom 4 hàm đăng ký từ [ReaderTab] lên [AppModalNavigationDrawerScaffold] thành **một** tham số
 * (tránh lệch slot / `ClassCastException` khi composable có quá nhiều tham số kiểu hàm với compiler Compose).
 * [ExportM4aTopBarState] nằm cùng package ([ReaderTab] file).
 */
data class ReaderTabRegistrationCallbacks(
    val onRegisterExportM4aForTopBar: ((ExportM4aTopBarState?) -> Unit)? = null,
    val onRegisterParagraphDraftFlush: ((() -> Unit) -> Unit)? = null,
    val onRegisterLibraryTabTextSerializer: (((() -> String)?) -> Unit)? = null,
    val onRegisterReaderBottomNav: ((ReaderBottomNavBridge?) -> Unit)? = null,
)
