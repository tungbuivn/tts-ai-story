package com.ttsaistory.app.domain

import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.model.PreSplitRegexRulesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trạng thái chương đang mở, parse phẳng ([chapterParagraphs]), và **ghép** lưới / dòng lưu thư viện.
 * Logic tách chuỗi đã lưu thành **câu** nằm trong [ParagraphSentenceSplitting].
 */
object ParagraphTextService {

    /**
     * Tổng số **câu** (ô phẳng sau parse, bỏ rỗng sau [sanitizeParagraphText]) — cập nhật khi parse.
     * `null` = chưa có lần parse gần đây cho ngữ cảnh hiện tại.
     */
    private val _totalItemCount = MutableStateFlow<Int?>(null)
    val totalItemCount: StateFlow<Int?> = _totalItemCount.asStateFlow()

    private val _chapterText = MutableStateFlow("")
    val chapterText: StateFlow<String> = _chapterText.asStateFlow()

    private val _chapterParagraphs = MutableStateFlow<List<String>>(emptyList())
    val chapterParagraphs: StateFlow<List<String>> = _chapterParagraphs.asStateFlow()

    /**
     * Bản chụp các câu phẳng hiện tại (sau [sanitizeParagraphText], bỏ rỗng) để xuất AAC —
     * danh sách mới, không ràng buộc [chapterParagraphs] sau khi gọi.
     */
    fun snapshotChapterParagraphsForExport(): List<String> =
        _chapterParagraphs.value.map(::sanitizeParagraphText).filter { it.isNotEmpty() }

    /**
     * Parse [text] → [chapterParagraphs] / [chapterText] (nối ô phẳng bằng `\n`).
     * Nếu [chapterId] và [libraryRepository] có mà chuỗi sau parse khác [text] (chỉ chuẩn hóa `\r\n`),
     * ghi lại nội dung chuẩn vào thư viện (file + DB).
     */
    fun setChapterText(
        text: String,
        chapterId: Long? = null,
        libraryRepository: StoryLibraryRepository? = null,
    ) {
        var textNorm = text.replace("\r\n", "\n").replace('\r', '\n')
        
        val flat = parseStoredTextToSentences2(textNorm)
        val canonical = flat.joinToString("\n")
        _chapterParagraphs.value = flat
        _chapterText.value = canonical
        val sid = chapterId
        val repo = libraryRepository
        if (sid != null && sid > 0L && repo != null && canonical != textNorm) {
            repo.updateStoryTextIfExists(sid, canonical)
        }
    }

    /**
     * Chuẩn hóa vài dạng che chữ (web) trước khi parse: `ch.` cùng dòng hoặc xuống dòng rồi `ết`
     * (`. ` không ăn newline trong regex mặc định — dùng `[\r\n]*` giữa) → `chết`;
     * `gi*t` / `ch*t` (dấu `*` thật) → `giết` / `chết`.
     */
    private fun normalizeCensorshipKillWords(text: String): String {
        var s =
            Regex("""(?i)ch\.\s*[\r\n]*\s*ết""").replace(text, "chết")
        s = Regex("""(?i)gi\*t""").replace(s, "giết")
        s = Regex("""(?i)ch\*t""").replace(s, "chết")
        return s
    }

    private fun normalizeLineEndingsForParse(s: String): String =
        s.replace("\r\n", "\n").replace('\r', '\n')

    /** LF + che chữ + [rulesOverride] — dùng khi thử regex trong cài đặt (chưa lưu prefs). */
    fun previewFullPreprocess(araw: String, rulesOverride: List<PreSplitRegexReplacementRule>): String {
        val normalized = normalizeLineEndingsForParse(araw)
        val afterCensor = normalizeCensorshipKillWords(normalized)
        return PreSplitRegexReplacements.applyRules(afterCensor, rulesOverride)
    }

    /** Chuẩn hoá LF + che chữ tích hợp + quy tắc regex người dùng — dùng trước mọi tách câu / ranh giới. */
    fun fullPreprocessBeforeSplit(araw: String): String =
        previewFullPreprocess(araw, PreSplitRegexRulesStore.rulesSnapshot())

    fun invalidateStoredTextParseCache() {
        synchronized(parseStoredTextCacheLock) {
            parseStoredTextCacheRaw = null
            parseStoredTextCacheSentences = null
        }
    }

    private val parseStoredTextCacheLock = Any()
    private var parseStoredTextCacheRaw: String? = null
    private var parseStoredTextCacheSentences: List<String>? = null

    private fun publishTotalItemCountFromFlat(sentences: List<String>) {
        val n = sentences.count { sanitizeParagraphText(it).isNotEmpty() }
        _totalItemCount.value = n
    }

    fun sanitizeParagraphText(input: String): String =
        ParagraphSentenceSplitting.sanitizeParagraphText(input)

    fun splitFullTextIntoParagraphLines(raw: String): List<String> =
        ParagraphSentenceSplitting.splitFullTextIntoParagraphLines(raw)

    fun splitParagraphIntoSentences(paragraph: String): List<String> =
        ParagraphSentenceSplitting.splitParagraphIntoSentences(paragraph)

    fun sentencesFromParagraphOrWhole(paragraph: String): List<String> =
        ParagraphSentenceSplitting.sentencesFromParagraphOrWhole(paragraph)

    private fun parseStoredTextToSentences2(raw: String): List<String> {
        val pre = fullPreprocessBeforeSplit(raw)
        synchronized(parseStoredTextCacheLock) {
            val cachedRaw = parseStoredTextCacheRaw
            val flat = parseStoredTextCacheSentences
            if (cachedRaw != null && flat != null && cachedRaw == pre) {
                return flat
            }
            val resultFlat = ParagraphSentenceSplitting.parseStoredTextToFlatSentences(pre)
            publishTotalItemCountFromFlat(resultFlat)
            parseStoredTextCacheRaw = pre
            parseStoredTextCacheSentences = resultFlat
            return resultFlat
        }
    }

    /** khi download new chapter cũng cần chuẩn hóa để ghi vào db */
    fun parseStoredTextToSentences(raw: String): List<String> {
        val resultFlat = parseStoredTextToSentencesUncached(raw)
        return resultFlat
    }

    private fun parseStoredTextToSentencesUncached(araw: String): List<String> {
        val raw = fullPreprocessBeforeSplit(araw)
        return ParagraphSentenceSplitting.parseStoredTextToFlatSentences(raw)
    }

    fun splitIntoFlatSentences(raw: String): List<String> = parseStoredTextToSentences(raw)

    fun flatSegmentCharRanges(raw: String): List<IntRange> =
        ParagraphSentenceSplitting.flatSegmentCharRanges(fullPreprocessBeforeSplit(raw))

    /** Bỏ ô câu rỗng; bỏ hàng lưới không còn câu. */
    fun compactParagraphGroups(groups: List<List<String>>): List<List<String>> {
        return groups.mapNotNull { inner ->
            val subs = inner.map(::sanitizeParagraphText).filter { it.isNotEmpty() }
            if (subs.isEmpty()) null else subs
        }
    }

    fun mergeParagraphMainGroups(groups: List<List<String>>): String {
        val c = compactParagraphGroups(groups)
        if (c.isEmpty()) return ""
        return c.joinToString(separator = "\n") { inner ->
            inner.map(::sanitizeParagraphText).filter { it.isNotEmpty() }.joinToString(separator = " ")
        }
    }

    fun mergeParagraphGridToStoredText(groups: List<List<String>>): String {
        val c = compactParagraphGroups(groups)
        if (c.isEmpty()) return ""
        return c.joinToString(separator = "\n") { inner ->
            inner.map(::sanitizeParagraphText).filter { it.isNotEmpty() }.joinToString(separator = "\n")
        }
    }

    fun mergeFlatParagraphLines(parts: List<String>): String {
        return parts.map(::sanitizeParagraphText).filter { it.isNotEmpty() }.joinToString(separator = "\n")
    }

    fun canonicalTextFromRaw(raw: String): String {
        val pre = fullPreprocessBeforeSplit(raw)
        val lines = ParagraphSentenceSplitting.splitFullTextIntoParagraphLines(pre)
        if (lines.isEmpty()) return ""
        val groups =
            lines.map { line ->
                sentencesFromParagraphOrWhole(line).map(::sanitizeParagraphText).filter {
                    it.isNotEmpty()
                }.ifEmpty { listOf("") }
            }
        return mergeParagraphMainGroups(compactParagraphGroups(groups))
    }

    fun paragraphIndexAtCharOffset(raw: String, offset: Int): Int =
        ParagraphSentenceSplitting.paragraphIndexAtCharOffset(fullPreprocessBeforeSplit(raw), offset)

    fun charOffsetForFlatParagraphIndex(text: String, paragraphIndex: Int): Int =
        ParagraphSentenceSplitting.charOffsetForFlatParagraphIndex(
            fullPreprocessBeforeSplit(text),
            paragraphIndex,
        )
}
