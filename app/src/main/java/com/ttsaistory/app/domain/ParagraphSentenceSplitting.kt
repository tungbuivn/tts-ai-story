package com.ttsaistory.app.domain

import java.lang.Character
import kotlin.text.CharCategory

/**
 * Tách chuỗi đã lưu / khối văn thành **câu** (chấm đơn, dòng logic, gộp newline theo chữ thường),
 * chuẩn hóa khối [sanitizeParagraphText], ranh giới ký tự trong raw — tách khỏi [ParagraphTextService] (state, cache, AAC).
 */
object ParagraphSentenceSplitting {
    /** Sau chấm ranh giới: chữ, khoảng trắng, kết chuỗi, hoặc `-` (gạch đầu đoạn sau `. -`). */
    private val singleSentenceEndDot =
        Regex("""(?<![.])\.(?![.])(?=\p{L}|\s|-|$)""")

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

    /** Sau các dấu này + khoảng trắng + `-` là gạch đoạn thoại, không gộp như markdown. */
    private val sentenceEndBeforeHyphenDash = setOf('.', '!', '?')

    private fun removeWhitespaceAdjacentToMarkdownMarkers(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isWhitespace()) {
                val prev = out.lastOrNull()
                val next = s.getOrNull(i + 1)
                if (next == '-' && prev != null && prev in sentenceEndBeforeHyphenDash) {
                    out.append(c)
                    i++
                    continue
                }
                if (prev == '-' && next != null && i + 1 < s.length &&
                    Character.isLetter(s.codePointAt(i + 1))
                ) {
                    out.append(c)
                    i++
                    continue
                }
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
     * nếu sau chấm (sau khi bỏ khoảng trắng) là chữ Unicode thì đảm bảo có đúng một khoảng trắng giữa `.` và chữ;
     * nếu là `-` (gạch sau dấu câu) thì giữ `. -` để [singleSentenceEndDot] vẫn nhận ranh giới câu.
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
                when {
                    i < s.length && isUnicodeLetterCodePointAt(s, i) -> {
                        sb.append(' ')
                        val cp = s.codePointAt(i)
                        sb.appendCodePoint(cp)
                        i += Character.charCount(cp)
                    }
                    i < s.length && s[i] == '-' -> {
                        sb.append(' ')
                        sb.append('-')
                        i++
                    }
                    else -> {}
                }
                continue
            }
            sb.append(s[i])
            i++
        }
        return sb.toString()
    }
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
     * Trước hết: [stripHyperlinksAndUrlLiterals] (http(s)/ftp/www/mailto, markdown, `<a>`, `<url>`);
     * `.- ` trước một ký tự → `. - ` (ranh giới câu tại chấm, giữ gạch đầu đoạn sau).
     */
    fun sanitizeParagraphText(input: String): String {
        val stripped = stripHyperlinksAndUrlLiterals(input)
        val linkStripped =
            dotHyphenSpaceSentenceBreak.replace(
                punctuatedVvBeforeEllipsis.replace(stripped, "..."),
                ". - ",
            )
        val sb = StringBuilder(linkStripped.length)
        var pendingSpace = false
        for (ch in linkStripped) {
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
     * `v.v` / `v.v.` sau dấu câu, khoảng trắng tùy chọn, rồi `...` hoặc dấu `…` — gộp thành `...`
     * (tránh tách câu lạc quanh "v.v").
     */
    private val punctuatedVvBeforeEllipsis =
        Regex("""(?i)(?<=[.,;:!?…、，。？）」』])\s*v\.v\.?\s*(?:\.{3}|…+)""")

    /**
     * `.- ` (chấm + gạch + khoảng) rồi còn ký tự phía sau — hết câu tại chấm, chuẩn hóa thành `. - `
     * để [splitOnSingleSentenceEndDots] tách được hai câu mà vẫn giữ gạch nói trước câu sau.
     */
    private val dotHyphenSpaceSentenceBreak = Regex("""\.- (?=.)""")

    /** Markdown `![alt](url)` — giữ mô tả (có thể rỗng), bỏ URL. */
    private val reMarkdownImage = Regex("""!\[([^\]]*)\]\([^)]*\)""")

    /** Markdown `[text](url)` — giữ phần hiển thị, bỏ URL. */
    private val reMarkdownLink = Regex("""\[([^\]]*)\]\([^)]*\)""")

    /** Thẻ `<a …>…</a>` — giữ nội dung hiển thị (bỏ thuộc tính href). */
    private val reHtmlAnchor = Regex("""(?i)<a\s[^>]*>([\s\S]*?)</a>""")

    /** `<https://…>` */
    private val reAngleBracketUrl = Regex("""<(?i)https?://[^>\s]+>""")

    /** `mailto:…` */
    private val reMailto = Regex("""(?i)mailto:[^\s<>()\[\]{}'"]+""")

    /**
     * `http(s)://…`, `ftp://…`, `www.…` — không bắt giữa chữ/số (tránh cắt nhầm path trong từ).
     * Dấu câu đuôi thường không thuộc URL được tách ra và giữ lại.
     */
    private val reBareWebUrl =
        Regex("""(?i)(?<![\p{L}\p{N}])(?:https?://|ftp://|www\.)[^\s<>\[\](){}'"]+""")

    private fun trimTrailingUrlPunctuation(u: String): String =
        u.trimEnd(
            '.',
            ',',
            ';',
            ':',
            '!',
            '?',
            ')',
            ']',
            '}',
            '»',
            '"',
            '\'',
            '…',
            '，',
            '。',
            '、',
        )

    /**
     * Gỡ URL / mailto / thẻ liên kết / markdown link khỏi văn bản (giữ nhãn hiển thị khi có).
     * Gọi đầu [sanitizeParagraphText] để mọi tách câu / TTS không còn chuỗi link.
     */
    private fun stripHyperlinksAndUrlLiterals(s: String): String {
        var t = s
        var iter = 0
        while (iter < 12) {
            iter++
            val before = t
            t = reMarkdownImage.replace(t) { m -> m.groupValues[1].trim() }
            t = reMarkdownLink.replace(t) { m -> m.groupValues[1].trim() }
            t = reHtmlAnchor.replace(t) { m -> m.groupValues[1].trim() }
            if (t == before) break
        }
        t = reAngleBracketUrl.replace(t, "")
        t = reMailto.replace(t, "")
        t =
            reBareWebUrl.replace(t) { m ->
                val full = m.value
                val core = trimTrailingUrlPunctuation(full)
                full.substring(core.length)
            }
        return t
    }
    /**
     * Chia toàn bộ [raw] thành các **dòng logic** (sau gộp `\n` theo chữ thường đầu dòng), đã sanitize, bỏ dòng
     * rỗng. Dòng sau dòng trống không gộp; dòng không trống mà sau khi trim bắt đầu bằng chữ thường
     * thì gộp vào khối trước (cùng quy tắc gộp dòng như khi [parseStoredTextToFlatSentences] tách câu).
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
     * thì nối vào khối đang tích lũy với khoảng trắng (ký tự `\n` gốc map vào vị trí khoảng trắng logic).
     * Dòng rỗng → kết thúc khối dòng logic (không gộp qua dòng trống).
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
     * Chia **một khối một dòng** (chuỗi không chứa `\n` sau gộp dòng logic) thành các **câu** theo dấu chấm đơn;
     * không tách tại `..` hay `...`. Ký tự `|` không còn là ranh giới câu (giữ nguyên trong nội dung nếu có).
     */
    fun splitParagraphIntoSentences(paragraph: String): List<String> {
        val s = paragraph.trim()
        if (s.isEmpty()) return emptyList()
        return splitOnSingleSentenceEndDots(s)
    }

    /** Nếu không tách được câu thì trả về cả [paragraph] đã sanitize (một phần tử). */
    fun sentencesFromParagraphOrWhole(paragraph: String): List<String> {
        val t = sanitizeParagraphText(paragraph.replace("\r", ""))
        if (t.isEmpty()) return emptyList()
        val sents = splitParagraphIntoSentences(t)
        return if (sents.isEmpty()) listOf(t) else sents
    }

    fun parseStoredTextToFlatSentences(raw: String): List<String> {
        if (raw.isEmpty()) {
            val empty = listOf("")
            return empty
        }
        val body = raw.trimEnd('\r', '\n')
        if (body.isEmpty()) {
            val empty = listOf("")
            return empty
        }
        val mergedLines =
            mergeNewlineSplitLinesIfLowercaseContinuation(physicalLinesWithBodyCharMap(body), body)
        val flat =
            buildList {
                for ((line, _) in mergedLines) {
                    val sanitizedLine = sanitizeParagraphText(line.replace("\r", ""))
                    if (sanitizedLine.isEmpty()) continue
                    val sents =
                        splitParagraphIntoSentences(sanitizedLine).map(::sanitizeParagraphText).filter {
                            it.isNotEmpty()
                        }
                    if (sents.isEmpty()) {
                        add(sanitizedLine)
                    } else {
                        addAll(sents)
                    }
                }
            }
        val result = if (flat.isEmpty()) listOf("") else flat
        return result
    }

    /**
     * Vị trí ký tự trong [raw] của từng segment phẳng (khớp [parseStoredTextToFlatSentences]); bỏ segment
     * rỗng sau sanitize.
     */
    fun flatSegmentCharRanges(raw: String): List<IntRange> {
        val body = raw.trimEnd('\r', '\n')
        if (body.isEmpty()) return emptyList()
        return lineBasedFlatSentenceRanges(raw)
    }

    /** Mỗi khối dòng logic sau gộp (xem [mergeNewlineSplitLinesIfLowercaseContinuation]); câu theo chấm đơn. */
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
     * Trong một khối một dòng (không chứa `\n`), vị trí từng câu theo thứ tự [sentencesFromParagraphOrWhole].
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
