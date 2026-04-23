package com.ttsaistory.app.domain

import java.lang.Character
import kotlin.text.CharCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tách/ghép văn bản theo **đoạn** (một dòng = một đoạn, cách nhau bằng `\n`; dòng tiếp theo bắt đầu
 * bằng chữ thường sau khi trim thì gộp vào đoạn trước) và **câu** (dấu chấm đơn và ký tự `|`).
 *
 * Định dạng lưu: giữa các **đoạn** là `\n`; trong một đoạn các **câu** nối bằng **khoảng trắng**
 * (không dùng `\n` giữa các câu).
 * Chuỗi có `\n\n` vẫn đọc được: tách theo `\n` rồi bỏ dòng rỗng, tương đương nhiều xuống dòng liên tiếp.
 */
object ParagraphTextService {

    /**
     * Tổng số **câu** (ô phẳng sau parse, bỏ rỗng sau [sanitizeParagraphText]) — cập nhật trong
     * [parseStoredTextToParagraphGroups]. `null` = chưa có lần parse gần đây cho ngữ cảnh hiện tại
     * (ví dụ vừa đổi truyện, bottom bar có thể coi là đang tính).
     */
    private val _totalItemCount = MutableStateFlow<Int?>(null)
    val totalItemCount: StateFlow<Int?> = _totalItemCount.asStateFlow()

    /** Khóa cho cache [parseStoredTextToParagraphGroups] (gọi từ UI + luồng nền). */
    private val parseStoredTextCacheLock = Any()
    private var parseStoredTextCacheRaw: String? = null
    private var parseStoredTextCacheResult: List<List<String>>? = null

    /** Danh sách câu phẳng lần parse cuối — export AAC dùng trực tiếp, không cần so khớp raw. */
    private var aacExportCacheFlatSentences: List<String>? = null

    private fun publishAacExportFlatCache(groups: List<List<String>>) {
        aacExportCacheFlatSentences = groups.flatten()
    }

    private fun publishTotalItemCountFromGroups(groups: List<List<String>>) {
        val n =
            groups.sumOf { row ->
                row.count { sanitizeParagraphText(it).isNotEmpty() }
            }
        _totalItemCount.value = n
    }

    /**
     * Chấm đơn không thuộc `..` / `...`; sau chấm là chữ Unicode, khoảng trắng hoặc hết chuỗi
     * (hỗ trợ `qua.Cuốn` không có dấu cách sau chấm).
     * Chấm sau chữ số + ký tự tiếp theo (bullet / thập phân), sau từ viết tắt danh xưng / tháng,
     * hoặc chữ cái đơn kiểu tên đệm (`J. Smith`) bị loại ở [dotIsSingleSentenceBoundary].
     */
    private val singleSentenceEndDot =
        Regex("""(?<![.])\.(?![.])(?=\p{L}|\s|$)""")

    /** Từ chữ cái ngay trước `.` (không kéo qua ký tự không phải chữ). */
    private fun lettersTokenEndingAt(s: String, lastLetterIndex: Int): String {
        if (lastLetterIndex !in s.indices || !s[lastLetterIndex].isLetter()) return ""
        var j = lastLetterIndex
        while (j >= 0 && s[j].isLetter()) j--
        return s.substring(j + 1, lastLetterIndex + 1)
    }

    private val honorificLikeTokensLowercase =
        setOf(
            "dr",
            "mr",
            "mrs",
            "ms",
            "prof",
            "sr",
            "jr",
            "st",
            "vs",
            "etc",
            "inc",
            "ltd",
            "corp",
            "co",
            "jan",
            "feb",
            "mar",
            "apr",
            "jun",
            "jul",
            "aug",
            "sep",
            "sept",
            "oct",
            "nov",
            "dec",
            "mme",
            "mlle",
            "mgr",
            "hon",
            "rev",
            "gen",
            "col",
            "capt",
            "lt",
            "sgt",
            "maj",
            "rep",
            "sen",
            "gov",
            "atty",
            "md",
            "mba",
            "ba",
            "ma",
            "bs",
            "jd",
            "rn",
            "phd",
            "dds",
        )

    /**
     * `Dr.`, `Mr.`, `Prof.`, … hoặc chữ cái Latin **một chữ** viết hoa (tên đệm `J.`, `T.`) khi
     * trước đó là ranh giới từ và sau `.` là khoảng trắng rồi chữ hoa / hết chuỗi — không ranh giới câu.
     */
    private fun dotIsHonorificOrSingleInitialAbbreviation(s: String, dotIndex: Int): Boolean {
        if (dotIndex <= 0 || dotIndex >= s.length || s[dotIndex] != '.') return false
        val lastLetter = dotIndex - 1
        if (!s[lastLetter].isLetter()) return false
        val token = lettersTokenEndingAt(s, lastLetter)
        if (token.isNotEmpty() && token.lowercase() in honorificLikeTokensLowercase) return true
        if (token.length != 1) return false
        val only = token[0]
        if (!only.isUpperCase() || !only.isLetter()) return false
        if (lastLetter > 0 && s[lastLetter - 1].isLetter()) return false
        val prev = if (lastLetter > 0) s[lastLetter - 1] else '\u0000'
        val boundaryOk =
            lastLetter == 0 ||
                prev.isWhitespace() ||
                prev in "([{`'\"«—–-"
        if (!boundaryOk) return false
        var k = dotIndex + 1
        while (k < s.length && s[k].isWhitespace()) k++
        if (k >= s.length) return true
        val after = s[k]
        return after.isLetter() && after.isUpperCase()
    }

    /** `*`, `_`, `+`, `-` (markdown / gạch): gộp lặp, bỏ khoảng trắng kề dấu, dòng chỉ một dấu → rỗng. */
    private val markdownLikeMarkerChars = setOf('*', '_', '+', '-')

    private fun collapseConsecutiveSameMarkdownMarkers(s: String): String {
        val out = StringBuilder(s.length)
        for (c in s) {
            if (out.isNotEmpty() && c == out.last() && c in markdownLikeMarkerChars) {
                continue
            }
            out.append(c)
        }
        return out.toString()
    }

    private fun removeWhitespaceAdjacentToMarkdownMarkers(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isWhitespace()) {
                val prev = out.lastOrNull()
                val next = s.getOrNull(i + 1)
                if ((prev != null && prev in markdownLikeMarkerChars) ||
                    (next != null && next in markdownLikeMarkerChars)
                ) {
                    i++
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /**
     * - Gộp các ký tự giống nhau liền nhau trong `*`, `_`, `+`, `-` thành một.
     * - Xóa mọi khoảng trắng (Unicode) kề một trong các dấu đó; lặp ổn định với bước gộp.
     * - Nếu sau [trim] còn đúng một ký tự và là một trong các dấu trên → chuỗi rỗng.
     */
    private fun normalizeMarkdownLikeMarkers(s: String): String {
        var t = s
        var iter = 0
        while (iter < 8) {
            iter++
            val a = removeWhitespaceAdjacentToMarkdownMarkers(t)
            val b = collapseConsecutiveSameMarkdownMarkers(a)
            if (a == t && b == t) break
            t = b
        }
        val x = t.trim()
        if (x.length == 1 && x[0] in markdownLikeMarkerChars) return ""
        return t.trim()
    }

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
     * `chữ số` ngay trước `.`, sau `.` có thể có khoảng trắng, rồi còn ít nhất một ký tự — bullet đánh số
     * (`1. Mục`, `2.Tiêu đề`) hoặc số thập phân (`12.34`): **không** ranh giới câu, giữ nguyên khi tách/normalize.
     */
    private fun dotIsDigitLeadingBulletOrDecimalDot(s: String, dotIndex: Int): Boolean {
        if (dotIndex <= 0 || dotIndex >= s.length || s[dotIndex] != '.') return false
        if (!s[dotIndex - 1].isDigit()) return false
        var j = dotIndex + 1
        while (j < s.length && s[j].isWhitespace()) j++
        return j < s.length
    }

    /**
     * Dấu `.` tại [dotIndex] có phải chấm đơn ranh giới câu (cùng quy tắc [singleSentenceEndDot], không `..`/`...`).
     */
    private fun dotIsSingleSentenceBoundary(s: String, dotIndex: Int): Boolean {
        if (dotIndex !in s.indices || s[dotIndex] != '.') return false
        if (dotIsDigitLeadingBulletOrDecimalDot(s, dotIndex)) return false
        if (dotIsHonorificOrSingleInitialAbbreviation(s, dotIndex)) return false
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
     * với `*`, `_`, `+`, `-`: bỏ khoảng trắng kề dấu, gộp dấu giống liền nhau thành một, dòng chỉ còn một dấu → rỗng;
     * chấm đơn ranh giới câu (không `..`/`...`): bỏ khoảng trắng quanh `.`, nếu sau chấm là chữ Unicode thì chèn một khoảng trắng (vd. `a.b` → `a. b`).
     * Dấu ngoặc kép Unicode (“ ” « » …) được giữ như ký tự hợp lệ (paste từ nguồn khác).
     * Sau các bước trên: tại **đầu câu** (đầu khối, sau `|`, sau `.` `!` `?`, sau xuống dòng), nếu gặp `-` rồi ngay một ký tự không phải khoảng trắng thì chèn một khoảng trắng (vd. `-Chào` → `- Chào`) — chạy cuối để không bị bước markdown gỡ khoảng kề `-`.
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
                    ch == '*' ||
                    ch == '_' ||
                    ch == '+' ||
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
        val normalized =
            normalizeSingleSentenceDotWhitespace(
                normalizeMarkdownLikeMarkers(
                    trimWhitespaceInsideAsciiDoubleQuotedSpans(trimmed),
                ),
            )
        return insertSpaceAfterSentenceLeadingHyphenBeforeNonWhitespace(normalized)
    }

    /**
     * (1) Đầu câu: sau `|`, `.` `!` `?`, hoặc `\n` / `\r`, hoặc đầu chuỗi — nếu `-` rồi ký tự không phải
     * khoảng trắng thì chèn một khoảng trắng sau `-`.
     * (2) Gạch ngang giữa **hai chữ** Unicode (`\p{L}`), không áp dụng khi có chữ số (`3-4`, `a-2`).
     * (3) Gạch ngang ngay sau `.` `!` `?` (khoảng trắng tùy chọn) rồi tới ký tự không trắng: chèn khoảng
     * (vd. `nói.-Chào` → `nói. - Chào`).
     */
    private fun insertSpaceAfterSentenceLeadingHyphenBeforeNonWhitespace(s: String): String {
        if (s.isEmpty()) return s
        val out = StringBuilder(s.length + 8)
        var atSentenceStart = true
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (atSentenceStart) {
                if (ch.isWhitespace()) {
                    out.append(ch)
                    i++
                    continue
                }
                if (ch == '-' && i + 1 < s.length && !s[i + 1].isWhitespace()) {
                    out.append("- ")
                    atSentenceStart = false
                    i++
                    continue
                }
                atSentenceStart = false
            }
            out.append(ch)
            when (ch) {
                '|' -> atSentenceStart = true
                '.', '!', '?' -> atSentenceStart = true
                '\n', '\r' -> atSentenceStart = true
                else -> {}
            }
            i++
        }
        return insertSpacesAroundHyphenBetweenNonWhitespaceNeighbors(out.toString())
    }

    private fun insertSpacesAroundHyphenBetweenNonWhitespaceNeighbors(s: String): String {
        var t =
            s.replace(HyphenAfterSentencePunctBeforeNonSpace) { m ->
                m.groupValues[1] + " - "
            }
        t = t.replace(HyphenBetweenUnicodeLetters, " - ")
        return t
    }

    /** Sau `.` `!` `?`, tùy khoảng trắng, rồi `-` rồi ký tự không trắng — nhóm 1 là dấu câu. */
    private val HyphenAfterSentencePunctBeforeNonSpace = Regex("""([.!?])(\s*)-(?=\S)""")

    /** Hai chữ Unicode (bỏ số / ký tự khác) hai bên `-`. */
    private val HyphenBetweenUnicodeLetters = Regex("""(?<=\p{L})-(?=\p{L})""")

    /**
     * Chia toàn bộ [raw] thành các **đoạn** (một đoạn = một dòng theo `\n`), đã sanitize, bỏ dòng
     * rỗng. Dòng sau dòng trống không gộp; dòng không trống mà sau khi trim bắt đầu bằng chữ thường
     * thì gộp vào đoạn trước (cùng quy tắc [parseStoredTextToParagraphGroups]).
     */
    fun splitFullTextIntoParagraphLines(raw: String): List<String> {
        val body = raw.trimEnd('\r', '\n')
        if (body.isEmpty()) return emptyList()
        return mergeNewlineSplitLinesIfLowercaseContinuation(physicalLinesWithBodyCharMap(body), body)
            .map { sanitizeParagraphText(it.first) }
            .filter { it.isNotEmpty() }
    }

    /** Mỗi dòng vật lý (tách bằng `\n` trong [body]): văn bản (bỏ `\r`) + map mỗi ký tự → chỉ số trong [body]. */
    private fun physicalLinesWithBodyCharMap(body: String): List<Pair<String, IntArray>> {
        val lines = mutableListOf<Pair<String, IntArray>>()
        val n = body.length
        var lineStart = 0
        while (lineStart <= n) {
            if (lineStart >= n) break
            var i = lineStart
            while (i < n && body[i] != '\n') i++
            lines.add(lineTextAndBodyCharMap(body, lineStart, i))
            lineStart = i + 1
        }
        return lines
    }

    private fun lineTextAndBodyCharMap(body: String, lineStart: Int, lineEndExclusive: Int): Pair<String, IntArray> {
        val sb = StringBuilder(lineEndExclusive - lineStart)
        val map = mutableListOf<Int>()
        for (p in lineStart until lineEndExclusive) {
            val ch = body[p]
            if (ch == '\r') continue
            sb.append(ch)
            map.add(p)
        }
        return sb.toString() to map.toIntArray()
    }

    private fun lineStartsWithLowercaseLetterAfterTrim(text: String): Boolean {
        val t = text.trimStart()
        if (t.isEmpty()) return false
        val ch = t.firstOrNull { it.isLetter() } ?: return false
        return ch.isLowerCase()
    }

    /**
     * Gộp các dòng vật lý liên tiếp: nếu dòng [i] (không rỗng) bắt đầu bằng chữ thường sau trim
     * thì nối vào đoạn đang tích lũy với khoảng trắng (ký tự `\n` gốc map vào vị trí khoảng trắng logic).
     * Dòng rỗng → kết thúc đoạn (không gộp qua dòng trống).
     */
    private fun mergeNewlineSplitLinesIfLowercaseContinuation(
        physical: List<Pair<String, IntArray>>,
        body: String,
    ): List<Pair<String, IntArray>> {
        if (physical.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<String, IntArray>>()
        var bufText: StringBuilder? = null
        var bufMap: MutableList<Int>? = null

        fun flush() {
            val b = bufText ?: return
            val t = b.toString()
            if (t.isNotEmpty()) {
                out.add(t to bufMap!!.toIntArray())
            }
            bufText = null
            bufMap = null
        }

        for ((text, map) in physical) {
            if (text.isEmpty()) {
                flush()
                continue
            }
            val startsLower = lineStartsWithLowercaseLetterAfterTrim(text)
            if (bufText == null) {
                bufText = StringBuilder(text)
                bufMap = map.toMutableList()
            } else if (startsLower) {
                val cur = bufText ?: continue
                val curMap = bufMap ?: continue
                val trimLead = (text.length - text.trimStart().length).coerceAtMost(map.size)
                val addText = text.substring(trimLead)
                val addMap = map.copyOfRange(trimLead, map.size)
                if (addMap.isEmpty()) continue
                val joinIdx =
                    (addMap[0] - 1).takeIf { j ->
                        j >= 0 && j < body.length && body[j] == '\n'
                    } ?: addMap[0]
                if (cur.isNotEmpty() && addText.isNotEmpty()) {
                    cur.append(' ')
                    curMap.add(joinIdx)
                }
                cur.append(addText)
                curMap.addAll(addMap.toList())
            } else {
                flush()
                bufText = StringBuilder(text)
                bufMap = map.toMutableList()
            }
        }
        flush()
        return out
    }

    /**
     * Chia theo dấu chấm đơn (không tách tại `..` hay `...`); [s] đã trim, không rỗng.
     */
    private fun splitOnSingleSentenceEndDots(s: String): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        var m = singleSentenceEndDot.find(s, start)
        while (m != null) {
            val dotStart = m.range.first
            if (!dotIsSingleSentenceBoundary(s, dotStart)) {
                m = singleSentenceEndDot.find(s, dotStart + 1)
                continue
            }
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
     * Sau khi tách `\n`, dòng không rỗng mà bắt đầu bằng chữ thường (sau trim) được gộp vào đoạn trước;
     * dòng rỗng kết thúc đoạn. Trong mỗi đoạn logic tách câu theo `|` và chấm đơn.
     *
     * Kết quả được cache theo tham số [raw] (so sánh nội dung `==`); gọi lại với cùng chuỗi trả về
     * cùng tham chiếu danh sách đã parse — không sửa đổi từ bên ngoài.
     */
    fun parseStoredTextToParagraphGroups(raw: String): List<List<String>> {
        synchronized(parseStoredTextCacheLock) {
            val cachedRaw = parseStoredTextCacheRaw
            val cached = parseStoredTextCacheResult
            if (cachedRaw != null && cached != null && cachedRaw == raw) {
                return cached
            }
            val result = parseStoredTextToParagraphGroupsUncached(raw)
            parseStoredTextCacheRaw = raw
            parseStoredTextCacheResult = result
            return result
        }
    }

    private fun parseStoredTextToParagraphGroupsUncached(raw: String): List<List<String>> {
        if (raw.isEmpty()) {
            val empty = listOf(listOf(""))
            publishTotalItemCountFromGroups(empty)
            publishAacExportFlatCache(empty)
            return empty
        }
        val body = raw.trimEnd('\r', '\n')
        if (body.isEmpty()) {
            val empty = listOf(listOf(""))
            publishTotalItemCountFromGroups(empty)
            publishAacExportFlatCache(empty)
            return empty
        }
        val mergedLines =
            mergeNewlineSplitLinesIfLowercaseContinuation(physicalLinesWithBodyCharMap(body), body)
        val result =
            mergedLines
                .map { (line, _) -> sanitizeParagraphText(line.replace("\r", "")) }
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
        publishTotalItemCountFromGroups(result)
        publishAacExportFlatCache(result)
        return result
    }

    /**
     * Danh sách câu phẳng sau lần [parseStoredTextToParagraphGroups] gần nhất — dùng cho export AAC
     * (cùng ranh giới câu với TTS). Bản sao từ cache; `null` nếu chưa từng parse trong phiên này.
     */
    fun lastCachedFlatSentencesForAacExport(): List<String>? {
        synchronized(parseStoredTextCacheLock) {
            return aacExportCacheFlatSentences?.toList()
        }
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

    /** Mỗi đoạn logic sau gộp dòng (xem [mergeNewlineSplitLinesIfLowercaseContinuation]); câu theo `|`, chấm. */
    private fun lineBasedFlatSentenceRanges(raw: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        val s = raw
        val body = s.trimEnd('\r', '\n')
        if (body.isEmpty()) return out
        val merged =
            mergeNewlineSplitLinesIfLowercaseContinuation(physicalLinesWithBodyCharMap(body), body)
        for ((logical, charMap) in merged) {
            val san = sanitizeParagraphText(logical)
            if (san.isEmpty()) continue
            val locals = sentenceRangesWithinMainBlock(logical)
            for (lr in locals) {
                if (lr.first !in charMap.indices || lr.last !in charMap.indices) continue
                val g0 = charMap[lr.first]
                val g1 = charMap[lr.last] + 1
                val slice = logical.substring(lr.first, lr.last + 1)
                if (g0 < g1 && g1 <= s.length && sanitizeParagraphText(slice).isNotEmpty()) {
                    out.add(g0 until g1)
                }
            }
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
