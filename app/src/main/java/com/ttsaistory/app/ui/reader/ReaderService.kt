package com.ttsaistory.app.ui.reader

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ttsaistory.app.data.OnlineDomainParserRow
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.data.normalizedOnlineParserDomainKey
import com.ttsaistory.app.domain.ParagraphTextService
import com.ttsaistory.app.domain.sanitizeParagraphText
import com.ttsaistory.app.model.AppPreferenceKeys
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trạng thái đọc/ghi sống **ngoài** [ReaderTab] (giữ trong [remember] ở [com.ttsaistory.app.ui.AppTabs])
 * để không mất khi chuyển tab — [ReaderTab] bị gỡ khỏi composition khi sang Thư viện.
 *
 * Mở rộng dần các trường cần giữ; nội dung chương vẫn do `text` / thư viện ở parent.
 */
@Stable
class ReaderService(prefs: SharedPreferences) {
    /** Lưới câu (theo ô) hay toàn văn. */
    var paragraphSplitMode: Boolean by mutableStateOf(true)

    private val libraryChapterLoadUiActiveState = mutableStateOf(false)

    /**
     * Trạng thái dialog «Đang tải chương» (vẽ ở [com.ttsaistory.app.ui.AppModalNavigationDrawerScaffold], không phụ thuộc tab).
     * Chỉ nên đổi qua [setLibraryChapterLoadUiActive] để áp dụng snapshot ngay.
     */
    val libraryChapterLoadUiActive: Boolean
        get() = libraryChapterLoadUiActiveState.value

    /**
     * Bật/tắt UI tải chương và gọi [androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications] ngay sau khi gán.
     */
    fun setLibraryChapterLoadUiActive(active: Boolean) {
        libraryChapterLoadUiActiveState.value = active
        Snapshot.sendApplyNotifications()
    }

    /** Trang lưới câu (chia trang). */
    var paragraphSplitPageIndex: Int by mutableIntStateOf(0)

    /** Token ép recompose / focus lưới. */
    var paragraphFocusRequestToken: Int by mutableIntStateOf(0)

    /**
     * Focus ô do user chọn thủ công (slider/chạm ô), tách riêng khỏi bookmark DB
     * để không vô tình ghi `last_speech_sentence_index`.
     */
    var paragraphManualFocusFlatIndex: Int by mutableIntStateOf(-1)

    /** Ô câu user vừa chạm/chọn trong view theo đoạn (viền cam cục bộ). */
    var paragraphLocalSelectedFlatIndex: Int by mutableIntStateOf(-1)

    /**
     * Đang phát TTS (theo đoạn / hệ thống / ElevenLabs) — dùng để không vẽ viền cam chọn ô
     * khi đọc; [ReaderTab] cập nhật mỗi composition.
     */
    var isPlaying: Boolean by mutableStateOf(false)

    /** Ẩn bàn phím mềm khi đọc (ReaderTab vẫn ghi prefs khi đổi). */
    var readerKeyboardForceHidden: Boolean by mutableStateOf(
        prefs.getBoolean(AppPreferenceKeys.KEY_READER_FORCE_HIDE_SOFT_KEYBOARD, false),
    )

    /**
     * State parse của tab Soạn/Sửa, giữ qua vòng đời compose của [ReaderTab]
     * để quay lại tab không phải parse lại chỉ để khôi phục UI/progress.
     */
    private val _totalItemCount = MutableStateFlow<Int?>(null)
    val totalItemCount: StateFlow<Int?> = _totalItemCount.asStateFlow()

    /** Tổng số dòng (theo `\n` trong chuỗi chuẩn hoá LF) — cập nhật cùng [setChapterText]. */
    private val _chapterLineCount = MutableStateFlow(0)
    val chapterLineCount: StateFlow<Int> = _chapterLineCount.asStateFlow()

    private val _chapterText = MutableStateFlow("")
    val chapterText: StateFlow<String> = _chapterText.asStateFlow()

    private val _chapterParagraphs = MutableStateFlow<List<String>>(emptyList())
    val chapterParagraphs: StateFlow<List<String>> = _chapterParagraphs.asStateFlow()

    /** Tick làm mới WPM khi TTS hệ thống đang phát (giữ qua đổi chapter). */
    var systemTtsWpmLiveTick: Int by mutableIntStateOf(0)
    /** Nội dung đoạn đang phát (index gốc -> văn đã sanitize) để đếm WPM. */
    var systemTtsWpmOrigToText: Map<Int, String> by mutableStateOf(emptyMap())
    /** Tổng thời gian phát các utterance đoạn đã hoàn tất (ms). */
    var systemTtsWpmSpeechMsAccum: Long by mutableLongStateOf(0L)
    /** Tổng số từ các đoạn đã hoàn tất để tính WPM. */
    var systemTtsWpmWordsAccum: Int by mutableIntStateOf(0)
    /** Mốc thời gian bắt đầu từng đoạn tts_para_* (elapsedRealtime). */
    val systemTtsWpmStartElapsedByParagraph = mutableStateMapOf<Int, Long>()

    /** Có nguồn deferred (pdf/zip/epub lazy) và vẫn còn item chưa nạp hết. */
    var deferredFetchHasRemaining: Boolean by mutableStateOf(false)
    /** User bật/tắt continue fetch nền cho deferred source. */
    var deferredFetchContinueEnabled: Boolean by mutableStateOf(false)
    /** Worker continue fetch đang chạy nền. */
    var deferredFetchWorking: Boolean by mutableStateOf(false)
    /** Nhãn tiến trình deferred fetch để hiển thị ở bottom bar (vd. "PDF 12 / 300"). */
    var deferredFetchProgressLabel: String by mutableStateOf("")

    /**
     * Parser online theo domain (bảng `online_domain_parsers`) — cache trong service để
     * headless sync / prefetch dùng bản mới nhất mà không phụ thuộc copy selector lên thể loại.
     * Khóa: [normalizedOnlineParserDomainKey] (host thường, không `www.`).
     */
    private var onlineDomainParsersByKey: Map<String, OnlineDomainParserRow> by mutableStateOf(emptyMap())

    fun replaceOnlineDomainParsersCache(rows: List<OnlineDomainParserRow>) {
        onlineDomainParsersByKey =
            rows.associateBy { r ->
                r.domain.trim().lowercase(Locale.ROOT).removePrefix("www.").trim()
            }
    }

    /**
     * Selector nội dung + selector trang kế từ cache parser cho [pageUrl].
     * `null` nếu không có domain / không có parser / không có selector nội dung.
     */
    fun selectorsForOnlinePage(pageUrl: String): Pair<List<String>, String?>? {
        val key = normalizedOnlineParserDomainKey(pageUrl) ?: return null
        val row = onlineDomainParsersByKey[key] ?: return null
        val content = row.contentSelectors.map { it.trim() }.filter { it.isNotEmpty() }
        if (content.isEmpty()) return null
        return content to row.onlineNextPageSelector
    }

    /**
     * Parse [text] -> [chapterParagraphs]/[chapterText] và cập nhật [totalItemCount], [chapterLineCount].
     * Nếu có chapter thư viện thì chuẩn hóa lại file khi canonical khác raw.
     */
    fun setChapterText(
        text: String,
        chapterId: Long? = null,
        libraryRepository: StoryLibraryRepository? = null,
    ) {
        val textNorm = text.replace("\r\n", "\n").replace('\r', '\n')
        _chapterLineCount.value =
            if (textNorm.isEmpty()) {
                0
            } else {
                textNorm.split("\n").size
            }
        val flat = ParagraphTextService.parseStoredTextToSentences(textNorm)
        val canonical = flat.joinToString("\n")
        _chapterParagraphs.value = flat
        _chapterText.value = canonical
        _totalItemCount.value = flat.count { sanitizeParagraphText(it).isNotEmpty() }
        val sid = chapterId
        val repo = libraryRepository
        if (sid != null && sid > 0L && repo != null && canonical != textNorm) {
            repo.updateStoryTextIfExists(sid, canonical)
        }
    }

    fun snapshotChapterParagraphsForExport(): List<String> =
        _chapterParagraphs.value.map(::sanitizeParagraphText).filter { it.isNotEmpty() }
}
