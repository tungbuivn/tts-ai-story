package com.ttsaistory.app.domain

fun sanitizeParagraphText(input: String) = ParagraphTextService.sanitizeParagraphText(input)

fun splitMainParagraphGroups(raw: String) = ParagraphTextService.parseStoredTextToParagraphGroups(raw)

fun compactParagraphGroups(groups: List<List<String>>) =
    ParagraphTextService.compactParagraphGroups(groups)

fun mergeMainParagraphGroups(groups: List<List<String>>) =
    ParagraphTextService.mergeParagraphMainGroups(groups)

fun splitIntoParagraphs(raw: String) = ParagraphTextService.splitIntoFlatSentences(raw)

fun segmentStartOffsetsInRaw(raw: String) =
    ParagraphTextService.flatSegmentCharRanges(raw).map { it.first }

fun canonicalTextFromRaw(raw: String) = ParagraphTextService.canonicalTextFromRaw(raw)

fun paragraphsForEditor(raw: String): List<String> {
    val parts =
        splitIntoParagraphs(raw).map(::sanitizeParagraphText).filter { it.isNotEmpty() }
    return if (parts.isEmpty()) listOf("") else parts
}

/** Nhóm đoạn chính → các ô con; luôn ít nhất một đoạn chính với một ô. */
fun paragraphMainGroupsForEditor(raw: String): List<List<String>> {
    val g = splitMainParagraphGroups(raw).map { row -> row.map(::sanitizeParagraphText) }
    return if (g.isEmpty() || g.all { main -> main.all { it.isEmpty() } }) {
        listOf(listOf(""))
    } else {
        g
    }
}

fun mergeParagraphs(parts: List<String>) = ParagraphTextService.mergeFlatParagraphLines(parts)

fun paragraphIndexAtTextOffset(raw: String, offset: Int) =
    ParagraphTextService.paragraphIndexAtCharOffset(raw, offset)

fun textOffsetAtParagraphStart(text: String, paragraphIndex: Int) =
    ParagraphTextService.charOffsetForFlatParagraphIndex(text, paragraphIndex)

fun charOffsetForEditorFlatCellInMerged(groupsText: List<List<String>>, flatIndex: Int): Int {
    val merged = mergeMainParagraphGroups(groupsText)
    val c = compactParagraphGroups(groupsText)
    if (c.isEmpty()) return 0
    var flat = 0
    var pos = 0
    for ((mi, inner) in c.withIndex()) {
        val pieces = inner.map(::sanitizeParagraphText).filter { it.isNotEmpty() }
        for ((pi, piece) in pieces.withIndex()) {
            if (flat == flatIndex) return pos.coerceIn(0, merged.length)
            pos += piece.length
            if (pi < pieces.lastIndex) pos += 1
            flat++
        }
        if (mi < c.lastIndex) {
            val nextPieces =
                c[mi + 1].map(::sanitizeParagraphText).filter { it.isNotEmpty() }
            if (pieces.isNotEmpty() && nextPieces.isNotEmpty()) pos += 1
        }
    }
    return merged.length
}

fun <T> flatIndexFromMainSub(groups: List<List<T>>, mainIdx: Int, subIdx: Int): Int {
    var f = 0
    for (mi in groups.indices) {
        for (si in groups[mi].indices) {
            if (mi == mainIdx && si == subIdx) return f
            f++
        }
    }
    return (f - 1).coerceAtLeast(0)
}

fun <T> flatIndexToMainSub(groups: List<List<T>>, flatIdx: Int): Pair<Int, Int> {
    var f = 0
    for (mi in groups.indices) {
        for (si in groups[mi].indices) {
            if (f == flatIdx) return mi to si
            f++
        }
    }
    val lm = groups.lastIndex.coerceAtLeast(0)
    return lm to groups[lm].lastIndex.coerceAtLeast(0)
}

fun hasSpeakableParagraphFrom(paragraphs: List<String>, startIndex: Int): Boolean {
    if (paragraphs.isEmpty()) return false
    val s = paragraphs.map(::sanitizeParagraphText)
    val from = startIndex.coerceIn(0, s.lastIndex)
    return (from..s.lastIndex).any { s[it].isNotEmpty() }
}

/**
 * Chỉ số ô theo đoạn (LazyColumn) khác [splitIntoParagraphs]: danh sách TTS bỏ ô rỗng sau sanitize.
 * Ánh xạ ô UI [editorUiFlat] → chỉ số dùng cho [splitIntoParagraphs] / TTS (đếm các ô có chữ trước ô đích).
 * Dùng [editorUiFlatToTtsParagraphStartIndexForFlatCells] khi đã có [cells] phẳng để tránh flatten lặp.
 */
fun editorUiFlatToTtsParagraphStartIndexForFlatCells(
    cells: List<String>,
    editorUiFlat: Int,
): Int {
    if (cells.isEmpty()) return 0
    var k = editorUiFlat.coerceIn(0, cells.lastIndex)
    while (k < cells.size && sanitizeParagraphText(cells[k]).isEmpty()) {
        k++
    }
    if (k >= cells.size) return 0
    var tts = 0
    for (i in 0 until k) {
        if (sanitizeParagraphText(cells[i]).isNotEmpty()) {
            tts++
        }
    }
    return tts
}

/**
 * Với mỗi ô phẳng `i`, trả về cùng kết quả [editorUiFlatToTtsParagraphStartIndexForFlatCells](cells, i)
 * — một lượt O(n) thay vì O(i) mỗi ô khi compose LazyColumn.
 */
fun ttsParagraphStartIndexForEachFlatCell(cells: List<String>): IntArray {
    val n = cells.size
    if (n == 0) return intArrayOf()
    val nonEmpty = BooleanArray(n) { i -> sanitizeParagraphText(cells[i]).isNotEmpty() }
    val prefixBefore = IntArray(n + 1)
    for (i in 0 until n) {
        prefixBefore[i + 1] = prefixBefore[i] + if (nonEmpty[i]) 1 else 0
    }
    val firstNonEmptyFrom = IntArray(n)
    var next = n
    for (i in n - 1 downTo 0) {
        if (nonEmpty[i]) next = i
        firstNonEmptyFrom[i] = next
    }
    return IntArray(n) { ei ->
        val e = ei.coerceIn(0, n - 1)
        val k = firstNonEmptyFrom[e]
        if (k >= n) 0 else prefixBefore[k]
    }
}

fun <T> flatIndexToMainSubPairs(groups: List<List<T>>): List<Pair<Int, Int>> {
    val cap = groups.sumOf { it.size }
    val out = ArrayList<Pair<Int, Int>>(cap)
    for (mi in groups.indices) {
        for (si in groups[mi].indices) {
            out.add(mi to si)
        }
    }
    return out
}

fun editorUiFlatToTtsParagraphStartIndex(groups: List<List<String>>, editorUiFlat: Int): Int =
    editorUiFlatToTtsParagraphStartIndexForFlatCells(groups.flatten(), editorUiFlat)

/**
 * Ngược [editorUiFlatToTtsParagraphStartIndexForFlatCells]: ô UI lớn nhất sao cho chỉ số TTS đầu câu ≤
 * [targetTts]. Một lượt O(n) trên [cells], không gọi forward từng ô.
 */
fun editorUiFlatForTtsParagraphStartIndexForFlatCells(cells: List<String>, targetTts: Int): Int {
    val n = cells.size
    if (n == 0 || targetTts <= 0) return 0
    val isNonEmpty = BooleanArray(n) { i -> sanitizeParagraphText(cells[i]).isNotEmpty() }
    val firstNonEmptyFrom = IntArray(n)
    var next = n
    for (i in n - 1 downTo 0) {
        if (isNonEmpty[i]) next = i
        firstNonEmptyFrom[i] = next
    }
    val nonEmptyBeforeK = IntArray(n + 1)
    for (i in 0 until n) {
        nonEmptyBeforeK[i + 1] = nonEmptyBeforeK[i] + if (isNonEmpty[i]) 1 else 0
    }
    var best = 0
    for (ui in 0 until n) {
        val k = firstNonEmptyFrom[ui]
        val start = if (k >= n) 0 else nonEmptyBeforeK[k]
        if (start <= targetTts) best = ui
    }
    return best
}

/** Ngược [editorUiFlatToTtsParagraphStartIndex]: ô UI cuối cùng có chỉ số TTS đầu câu ≤ [targetTts]. */
fun editorUiFlatForTtsParagraphStartIndex(groups: List<List<String>>, targetTts: Int): Int {
    val cells = groups.flatten()
    if (cells.isEmpty()) return 0
    return editorUiFlatForTtsParagraphStartIndexForFlatCells(cells, targetTts)
}

fun hasExportableText(fullText: String): Boolean =
    splitIntoParagraphs(fullText).any { sanitizeParagraphText(it).isNotEmpty() }
