package com.ttsaistory.app.domain

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

    fun setChapterText(text: String) {
        
        val flat = parseStoredTextToSentences(text)
        _chapterParagraphs.value = flat
        _chapterText.value = flat.joinToString("\n")
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

    private fun parseStoredTextToSentences(raw: String): List<String> {
        synchronized(parseStoredTextCacheLock) {
            val cachedRaw = parseStoredTextCacheRaw
            val flat = parseStoredTextCacheSentences
            if (cachedRaw != null && flat != null && cachedRaw == raw) {
                return flat
            }
            val resultFlat = parseStoredTextToSentencesUncached(raw)
            parseStoredTextCacheRaw = raw
            parseStoredTextCacheSentences = resultFlat
            return resultFlat
        }
    }

    private fun parseStoredTextToSentencesUncached(raw: String): List<String> {
        val result = ParagraphSentenceSplitting.parseStoredTextToFlatSentences(raw)
        publishTotalItemCountFromFlat(result)
        return result
    }

    fun splitIntoFlatSentences(raw: String): List<String> = parseStoredTextToSentences(raw)

    fun flatSegmentCharRanges(raw: String): List<IntRange> =
        ParagraphSentenceSplitting.flatSegmentCharRanges(raw)

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
        val lines = splitFullTextIntoParagraphLines(raw)
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
        ParagraphSentenceSplitting.paragraphIndexAtCharOffset(raw, offset)

    fun charOffsetForFlatParagraphIndex(text: String, paragraphIndex: Int): Int =
        ParagraphSentenceSplitting.charOffsetForFlatParagraphIndex(text, paragraphIndex)
}
