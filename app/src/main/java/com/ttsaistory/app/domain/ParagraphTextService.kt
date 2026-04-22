package com.ttsaistory.app.domain

import java.lang.Character
import kotlin.text.CharCategory

/**
 * Tách/ghép văn bản theo **đoạn** (một dòng = một đoạn, cách nhau bằng `\n`) và **câu** (dấu chấm đơn
 * và ký tự `|`).
 *
 * Định dạng lưu: giữa các **đoạn** là `\n`; trong một đoạn các **câu** nối bằng **khoảng trắng**
 * (không dùng `\n` giữa các câu).
 * Chuỗi có `\n\n` vẫn đọc được: tách theo `\n` rồi bỏ dòng rỗng, tương đương nhiều xuống dòng liên tiếp.
 */
object ParagraphTextService {

    /**
     * Chấm đơn không thuộc `..` / `...`; sau chấm là chữ Unicode, khoảng trắng hoặc hết chuỗi
     * (hỗ trợ `qua.Cuốn` không có dấu cách sau chấm).
     */
    private val singleSentenceEndDot =
        Regex("""(?<![.])\.(?![.])(?=\p{L}|\s|$)""")

    /**
     * Trong mỗi cặp `"…"`, chỉ [trim] nội dung giữa hai dấu (ví dụ `chào "bạn "` → `chào "bạn"`);
     * `chào "bạn"` giữ nguyên. Không xóa khoảng trắng bên ngoài cặp nháy.
     *
     * Nếu dấu `"` đóng là ký tự cuối của chuỗi [s] và ngay trước đó (sau khi đã trim nội dung trong nháy)
     * vẫn còn khoảng trắng ở cuối [sb] (ví dụ phần tiền tố kết thúc bằng space), gỡ các khoảng trắng đó
     * trước khi ghi `"` đóng.
     */
    private fun trimWhitespaceInsideAsciiDoubleQuotedSpans(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val open = s.indexOf('"', i)
            if (open < 0) {
                sb.append(s, i, s.length)
                break
            }
            sb.append(s, i, open + 1)
            val close = s.indexOf('"', open + 1)
            if (close < 0) {
                sb.append(s, open + 1, s.length)
                break
            }
            sb.append(s.substring(open + 1, close).trim())
            if (close == s.lastIndex) {
                while (sb.isNotEmpty() && sb.last().isWhitespace()) {
                    sb.setLength(sb.length - 1)
                }
            }
            sb.append('"')
            i = close + 1
        }
        return trimWhitespaceBeforeAsciiDoubleQuoteAtStringEnd(sb.toString())
    }

    /**
     * Chuỗi kết thúc bằng `"` ASCII: bỏ mọi khoảng trắng liền kề ngay trước dấu `"` cuối cùng
     * (sau khi đã xử lý các cặp nháy).
     */
    private fun trimWhitespaceBeforeAsciiDoubleQuoteAtStringEnd(s: String): String {
        if (s.length < 2 || s.last() != '"') return s
        val sb = StringBuilder(s)
        while (sb.length >= 2 && sb.last() == '"' && sb[sb.length - 2].isWhitespace()) {
            sb.deleteCharAt(sb.length - 2)
        }
        return sb.toString()
    }

    /**
     * [s] tại [index] có bắt đầu bằng codepoint chữ Unicode (dùng [Character.isLetter]).
     */
    private fun isUnicodeLetterCodePointAt(s: String, index: Int): Boolean {
        if (index !in s.indices) return false
        return Character.isLetter(s.codePointAt(index))
    }

    /**
     * Dấu `.` tại [dotIndex] có phải chấm đơn ranh giới câu (cùng quy tắc [singleSentenceEndDot], không `..`/`...`).
     */
    private fun dotIsSingleSentenceBoundary(s: String, dotIndex: Int): Boolean {
        if (dotIndex !in s.indices || s[dotIndex] != '.') return false
        val m = singleSentenceEndDot.find(s, dotIndex) ?: return false
        return m.range.first == dotIndex
    }

    /**
     * Với mỗi chấm đơn ranh giới câu: bỏ khoảng trắng ngay trước và sau dấu `.`;
     * nếu sau chấm (sau khi bỏ khoảng trắng) là chữ Unicode thì đảm bảo có đúng một khoảng trắng giữa `.` và chữ.
     */
    private fun normalizeSingleSentenceDotWhitespace(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '.' && dotIsSingleSentenceBoundary(s, i)) {
                while (sb.isNotEmpty() && sb.last().isWhitespace()) {
                    sb.setLength(sb.length - 1)
                }
                sb.append('.')
                i++
                while (i < s.length && s[i].isWhitespace()) i++
                if (i < s.length && isUnicodeLetterCodePointAt(s, i)) {
                    sb.append(' ')
                    val cp = s.codePointAt(i)
                    sb.appendCodePoint(cp)
                    i += Character.charCount(cp)
                }
                continue
            }
            sb.append(s[i])
            i++
        }
        return sb.toString()
    }

    /**
     * Dấu kết hợp Unicode (Mn/Mc/Me): văn bản NFD có thể tách chữ cơ bản và dấu thành nhiều `Char`;
     * [Char.isLetter] không coi các codepoint này là chữ nên nếu chỉ dùng [isLetter]/[isDigit] sẽ mất dấu.
     */
    private fun Char.isUnicodeCombiningMark(): Boolean =
        when (category) {
            CharCategory.NON_SPACING_MARK,
            CharCategory.COMBINING_SPACING_MARK,
            CharCategory.ENCLOSING_MARK -> true
            else -> false
        }

    /**
     * Chuẩn hóa một khối văn: chữ/số và một số dấu; khoảng trắng gộp; `??` → một `?`;
     * trong cặp `"…"` (ASCII) bỏ khoảng trắng thừa đầu/cuối nội dung trong nháy;
     * chấm đơn ranh giới câu (không `..`/`...`): bỏ khoảng trắng quanh `.`, nếu sau chấm là chữ Unicode thì chèn một khoảng trắng (vd. `a.b` → `a. b`).
     * Dấu ngoặc kép Unicode (“ ” « » …) được giữ như ký tự hợp lệ (paste từ nguồn khác).
     */
    fun sanitizeParagraphText(input: String): String {
        val sb = StringBuilder(input.length)
        var pendingSpace = false
        for (ch in input) {
            if (ch == '?' && sb.isNotEmpty() && sb.last() == '?') {
                continue
            }
            // ASCII `"` và dấu ngoặc kép kiểu Unicode (thường gặp khi paste từ Word/web).
            val isDoubleQuoteLike =
                ch == '"' ||
                    ch == '\u201c' || // “
                    ch == '\u201d' || // ”
                    ch == '\u201e' || // „
                    ch == '\u201f' || // ‟
                    ch == '\u00ab' || // «
                    ch == '\u00bb' // »
            val symbol =
                ch == '!' ||
                    ch == '%' ||
                    ch == '|' ||
                    ch == '(' ||
                    ch == ')' ||
                    ch == '&' ||
                    ch == '?' ||
                    ch == '.' ||
                    ch == ',' ||
                    ch == '-' ||
                    ch == ':' ||
                    isDoubleQuoteLike
            val ok = ch.isLetter() || ch.isDigit() || ch.isUnicodeCombiningMark() || symbol
            when {
                ok -> {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    pendingSpace = false
                    sb.append(ch)
                }
                ch.isWhitespace() -> {
                    if (sb.isNotEmpty()) pendingSpace = true
                }
                else -> {}
            }
        }
        val trimmed = sb.toString().trim()
        return normalizeSingleSentenceDotWhitespace(
            trimWhitespaceInsideAsciiDoubleQuotedSpans(trimmed),
        )
    }

    /**
     * Chia toàn bộ [raw] thành các **đoạn** (một đoạn = một dòng theo `\n`), đã sanitize, bỏ dòng
     * rỗng.
     */
    fun splitFullTextIntoParagraphLines(raw: String): List<String> {
        return raw
            .split('\n')
            .map { sanitizeParagraphText(it.replace("\r", "")) }
            .filter { it.isNotEmpty() }
    }

    /**
     * Chia theo dấu chấm đơn (không tách tại `..` hay `...`); [s] đã trim, không rỗng.
     */
    private fun splitOnSingleSentenceEndDots(s: String): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        var m = singleSentenceEndDot.find(s, start)
        while (m != null) {
            val dotEnd = m.range.last
            val piece = s.substring(start, dotEnd + 1).trim()
            if (piece.isNotEmpty()) out.add(piece)
            start = dotEnd + 1
            while (start < s.length && s[start].isWhitespace()) start++
            m = singleSentenceEndDot.find(s, start)
        }
        if (start < s.length) {
            val tail = s.substring(start).trim()
            if (tail.isNotEmpty()) out.add(tail)
        }
        return out
    }

    /**
     * Chia **một đoạn** (một chuỗi không chứa `\n` của đoạn logic) thành các **câu** theo ký tự `|`
     * và dấu chấm đơn; không tách tại `..` hay `...`. Ký tự `|` chỉ là ranh giới, không đưa vào câu.
     */
    fun splitParagraphIntoSentences(paragraph: String): List<String> {
        val s = paragraph.trim()
        if (s.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        for (frag in s.split('|')) {
            val t = frag.trim()
            if (t.isEmpty()) continue
            out.addAll(splitOnSingleSentenceEndDots(t))
        }
        return out
    }

    /** Nếu không tách được câu thì trả về cả [paragraph] đã sanitize (một phần tử). */
    fun sentencesFromParagraphOrWhole(paragraph: String): List<String> {
        val t = sanitizeParagraphText(paragraph.replace("\r", ""))
        if (t.isEmpty()) return emptyList()
        val sents = splitParagraphIntoSentences(t)
        return if (sents.isEmpty()) listOf(t) else sents
    }

    /** Chuẩn hóa paste / ô toàn bộ: mỗi dòng → đoạn, trong đoạn tách câu → chuỗi lưu. */
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

    /** Bỏ ô câu rỗng; bỏ đoạn không còn câu. */
    fun compactParagraphGroups(groups: List<List<String>>): List<List<String>> {
        return groups.mapNotNull { inner ->
            val subs = inner.map(::sanitizeParagraphText).filter { it.isNotEmpty() }
            if (subs.isEmpty()) null else subs
        }
    }

    /** Nối nhóm: khoảng trắng giữa các câu trong cùng đoạn, `\n` giữa các đoạn. */
    fun mergeParagraphMainGroups(groups: List<List<String>>): String {
        val c = compactParagraphGroups(groups)
        if (c.isEmpty()) return ""
        return c.joinToString(separator = "\n") { inner ->
            inner.map(::sanitizeParagraphText).filter { it.isNotEmpty() }.joinToString(separator = " ")
        }
    }

    /** Nối danh sách đoạn phẳng (mỗi phần tử một dòng độc lập) bằng `\n`. */
    fun mergeFlatParagraphLines(parts: List<String>): String {
        return parts.map(::sanitizeParagraphText).filter { it.isNotEmpty() }.joinToString(separator = "\n")
    }

    /**
     * Parse chuỗi đã lưu → `List<đoạn<List<câu>>>`.
     * Mỗi dòng (phân tách bằng `\n`) là một đoạn; dòng rỗng bỏ qua. Trong mỗi dòng tách câu theo `|` và chấm đơn.
     */
    fun parseStoredTextToParagraphGroups(raw: String): List<List<String>> {
        if (raw.isEmpty()) return listOf(listOf(""))
        val body = raw.trimEnd('\r', '\n')
        if (body.isEmpty()) return listOf(listOf(""))
        return body
            .split('\n')
            .map { sanitizeParagraphText(it.replace("\r", "")) }
            .mapNotNull { line ->
                if (line.isEmpty()) null
                else {
                    val sents =
                        splitParagraphIntoSentences(line).map(::sanitizeParagraphText).filter {
                            it.isNotEmpty()
                        }
                    if (sents.isEmpty()) listOf(line) else sents
                }
            }
            .ifEmpty { listOf(listOf("")) }
    }

    /** Danh sách phẳng (mọi câu theo thứ tự đọc / TTS). */
    fun splitIntoFlatSentences(raw: String): List<String> {
        return parseStoredTextToParagraphGroups(raw).flatten()
    }

    /**
     * Vị trí ký tự trong [raw] của từng segment phẳng (khớp [splitIntoFlatSentences]); bỏ segment
     * rỗng sau sanitize.
     */
    fun flatSegmentCharRanges(raw: String): List<IntRange> {
        val body = raw.trimEnd('\r', '\n')
        if (body.isEmpty()) return emptyList()
        return lineBasedFlatSentenceRanges(raw)
    }

    /** Mỗi dòng `\n` một đoạn; câu trong dòng theo `|`, chấm hoặc cả dòng (khoảng trắng giữa câu). */
    private fun lineBasedFlatSentenceRanges(raw: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        val s = raw
        val n = s.length
        var lineStart = 0
        var i = 0
        while (lineStart <= n) {
            if (lineStart >= n) break
            i = lineStart
            while (i < n && s[i] != '\n') i++
            val line = s.substring(lineStart, i).replace("\r", "")
            val san = sanitizeParagraphText(line)
            if (san.isNotEmpty()) {
                val locals = sentenceRangesWithinMainBlock(line)
                for (lr in locals) {
                    val g0 = lineStart + lr.first
                    val g1 = lineStart + lr.last + 1
                    if (g0 < g1 && g1 <= n && sanitizeParagraphText(s.substring(g0, g1)).isNotEmpty()) {
                        out.add(g0 until g1)
                    }
                }
            }
            lineStart = i + 1
        }
        return out
    }

    /**
     * Trong một khối đoạn (một dòng, không chứa `\n`), vị trí từng câu theo thứ tự [sentencesFromParagraphOrWhole].
     */
    private fun sentenceRangesWithinMainBlock(block: String): List<IntRange> {
        val t = block.trimEnd('\r', '\n')
        if (t.isEmpty()) return emptyList()
        val sents =
            sentencesFromParagraphOrWhole(sanitizeParagraphText(t.replace("\r", "")))
                .map { sanitizeParagraphText(it) }
                .filter { it.isNotEmpty() }
        if (sents.isEmpty()) return emptyList()
        if (sents.size == 1) {
            val inner = t.trim()
            val rel = t.indexOf(inner)
            val from = (if (rel >= 0) rel else 0)
            return listOf(from until (from + inner.length).coerceAtMost(t.length))
        }
        var p = 0
        val b = t
        val out = mutableListOf<IntRange>()
        for (sent in sents) {
            val idx = b.indexOf(sent, p)
            if (idx < 0) {
                return listOf(0 until t.length.coerceAtLeast(1))
            }
            out.add(idx until idx + sent.length)
            p = idx + sent.length
            while (p < b.length && b[p].isWhitespace()) p++
        }
        return out
    }

    fun paragraphIndexAtCharOffset(raw: String, offset: Int): Int {
        if (raw.isEmpty()) return 0
        val o = offset.coerceIn(0, raw.length)
        val ranges = flatSegmentCharRanges(raw)
        if (ranges.isEmpty()) return 0
        ranges.forEachIndexed { idx, r ->
            if (r.isEmpty()) return@forEachIndexed
            if (o < r.first) return (idx - 1).coerceAtLeast(0)
            if (o in r) return idx
            val endEx = r.last + 1
            if (o == endEx) return if (idx < ranges.lastIndex) idx + 1 else idx
        }
        return ranges.lastIndex
    }

    fun charOffsetForFlatParagraphIndex(text: String, paragraphIndex: Int): Int {
        if (text.isEmpty() || paragraphIndex <= 0) return 0
        val starts = flatSegmentCharRanges(text).map { it.first }
        if (starts.isEmpty()) return 0
        if (paragraphIndex >= starts.size) return text.length
        return starts[paragraphIndex].coerceIn(0, text.length)
    }
}
