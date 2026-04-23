package com.ttsaistory.app.domain

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.copyTo

private fun normalizePdfLineKey(s: String): String =
    s.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

private fun splitPdfPageLines(pageText: String): List<String> =
    pageText.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }

/** Dòng chỉ là số trang / "trang 12" / "12 / 100" — gỡ ở đầu/cuối trang không cần đếm lặp. */
private fun isLikelyStandalonePageNumberLine(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return false
    if (t.length > 32) return false
    if (Regex("^\\d{1,5}$").matches(t)) return true
    if (Regex("^\\d{1,5}\\s*[/|]\\s*\\d{1,5}$").matches(t)) return true
    if (Regex("^page\\s+\\d{1,5}$", RegexOption.IGNORE_CASE).matches(t)) return true
    if (Regex("^trang\\s+\\d{1,5}$", RegexOption.IGNORE_CASE).matches(t)) return true
    return false
}

/**
 * Gỡ header/footer: dòng (đủ dài) xuất hiện ở **vùng đầu** hoặc **vùng cuối** của đủ nhiều trang
 * được coi là lặp và bị bỏ khỏi đầu/cuối mỗi trang; thêm quy tắc số trang đơn.
 */
internal fun stripRepeatedPdfHeaderFooter(
    pageTexts: List<String>,
    maxBandLines: Int = 5,
): List<String> {
    if (pageTexts.isEmpty()) return pageTexts
    val blocks = pageTexts.map { splitPdfPageLines(it) }
    val n = blocks.size
    if (n < 2) {
        return blocks.map { stripPageNumberMargins(it).joinToString("\n").trim() }
    }
    val minPagesWithLine =
        when {
            n <= 2 -> n
            n <= 6 -> max(2, (n * 0.5).toInt())
            else -> max(3, (n * 0.4).toInt())
        }

    val topKeyHits = mutableMapOf<String, Int>()
    val bottomKeyHits = mutableMapOf<String, Int>()
    for (lines in blocks) {
        if (lines.isEmpty()) continue
        val h = maxBandLines.coerceAtMost(lines.size)
        lines
            .take(h)
            .map { normalizePdfLineKey(it) }
            .filter { it.length >= 3 }
            .distinct()
            .forEach { k -> topKeyHits[k] = (topKeyHits[k] ?: 0) + 1 }
        lines
            .takeLast(h)
            .map { normalizePdfLineKey(it) }
            .filter { it.length >= 3 }
            .distinct()
            .forEach { k -> bottomKeyHits[k] = (bottomKeyHits[k] ?: 0) + 1 }
    }

    val repeatingTop = topKeyHits.filter { it.value >= minPagesWithLine }.keys
    val repeatingBottom = bottomKeyHits.filter { it.value >= minPagesWithLine }.keys

    return blocks.map { lines ->
        val m = lines.toMutableList()
        while (m.isNotEmpty()) {
            val k = normalizePdfLineKey(m.first())
            if (k.length >= 3 && k in repeatingTop) {
                m.removeAt(0)
            } else if (isLikelyStandalonePageNumberLine(m.first())) {
                m.removeAt(0)
            } else {
                break
            }
        }
        while (m.isNotEmpty()) {
            val k = normalizePdfLineKey(m.last())
            if (k.length >= 3 && k in repeatingBottom) {
                m.removeAt(m.lastIndex)
            } else if (isLikelyStandalonePageNumberLine(m.last())) {
                m.removeAt(m.lastIndex)
            } else {
                break
            }
        }
        stripPageNumberMargins(m).joinToString("\n").trim()
    }
}

private fun stripPageNumberMargins(lines: List<String>): List<String> {
    val m = lines.toMutableList()
    while (m.isNotEmpty() && isLikelyStandalonePageNumberLine(m.first())) m.removeAt(0)
    while (m.isNotEmpty() && isLikelyStandalonePageNumberLine(m.last())) m.removeAt(m.lastIndex)
    return m
}

fun uriLooksLikePdf(context: Context, uri: Uri, displayName: String?): Boolean {
    val name = displayName?.trim().orEmpty()
    if (name.endsWith(".pdf", ignoreCase = true)) return true
    val t = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
    if (t == "application/pdf" || t.endsWith("/pdf")) return true
    val seg = uri.lastPathSegment.orEmpty()
    return seg.endsWith(".pdf", ignoreCase = true)
}

fun safeCategoryNameFromPdfDisplayName(displayName: String): String {
    var s = displayName.trim()
    if (s.endsWith(".pdf", ignoreCase = true)) s = s.dropLast(4).trim()
    s = s.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).trim()
    return s.ifEmpty { "PDF" }
}

/**
 * Đọc PDF từ [pdfUri], trích chữ từng trang (bỏ trang rỗng), ghi `00000001.txt`, `00000002.txt`, …
 * vào [destDir] (đã được dọn trước).
 *
 * @param onPageFileWritten sau mỗi file ghi xong (hàng đợi nhập thư viện giống EPUB).
 * @return số trang đã ghi (có nội dung)
 */
suspend fun importPdfPagesAsNumberedTxtFiles(
    context: Context,
    pdfUri: Uri,
    destDir: File,
    onProgress: ((String) -> Unit)? = null,
    onPageFileWritten: ((fileName: String, file: File) -> Unit)? = null,
): Int =
    withContext(Dispatchers.IO) {
        destDir.mkdirs()
        if (!destDir.isDirectory) error("Không tạo được thư mục PDF")
        val tmp =
            File(context.cacheDir, "pdf_work_${System.currentTimeMillis()}.pdf").apply {
                deleteOnExit()
            }
        try {
            context.contentResolver.openInputStream(pdfUri)?.use { ins ->
                FileOutputStream(tmp).use { outs -> ins.copyTo(outs) }
            } ?: error("Không mở được file PDF")

            PDDocument.load(tmp).use { doc ->
                if (doc.isEncrypted) {
                    error("PDF có mật khẩu — không hỗ trợ.")
                }
                val totalPages = doc.numberOfPages
                if (totalPages <= 0) {
                    error("PDF không có trang.")
                }
                val stripper =
                    PDFTextStripper().apply {
                        sortByPosition = true
                    }
                val rawPages = ArrayList<String>(totalPages)
                for (p in 1..totalPages) {
                    onProgress?.invoke("Trang $p / $totalPages")
                    stripper.startPage = p
                    stripper.endPage = p
                    val pageText = stripper.getText(doc).trim()
                    if (pageText.isNotEmpty()) {
                        rawPages.add(pageText)
                    }
                }
                if (rawPages.isEmpty()) {
                    error("Không trích được chữ nào từ PDF (PDF ảnh / trang trống).")
                }
                onProgress?.invoke("Đang bỏ header/footer lặp…")
                val cleanedPages = stripRepeatedPdfHeaderFooter(rawPages)
                var written = 0
                for (pageText in cleanedPages) {
                    if (pageText.isBlank()) continue
                    written++
                    val name = String.format(Locale.US, "%08d.txt", written)
                    val outFile = File(destDir, name)
                    outFile.writeText(pageText, Charsets.UTF_8)
                    onPageFileWritten?.invoke(name, outFile)
                }
                if (written == 0) {
                    error("Không trích được chữ nào từ PDF (PDF ảnh / trang trống).")
                }
                written
            }
        } finally {
            tmp.delete()
        }
    }
