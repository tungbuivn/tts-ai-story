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

private const val PDF_DEFERRED_ONLINE_URL_SCHEME = "pdf-lazy"

data class DeferredPdfPageSpec(
    val sourcePdfPath: String,
    val pageIndex1: Int,
    val totalPages: Int,
)

fun deferredPdfPageOnlineUrl(
    sourcePdfPath: String,
    pageIndex1: Int,
    totalPages: Int,
): String {
    val src = Uri.encode(sourcePdfPath.trim())
    return "$PDF_DEFERRED_ONLINE_URL_SCHEME://page/$pageIndex1?total=$totalPages&src=$src"
}

fun parseDeferredPdfPageOnlineUrl(url: String?): DeferredPdfPageSpec? {
    val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return null
    if (!uri.scheme.equals(PDF_DEFERRED_ONLINE_URL_SCHEME, ignoreCase = true)) return null
    if (!uri.host.equals("page", ignoreCase = true)) return null
    val segs = uri.pathSegments
    if (segs.isEmpty()) return null
    val page = segs[0].toIntOrNull() ?: return null
    val total = uri.getQueryParameter("total")?.toIntOrNull() ?: return null
    val src = Uri.decode(uri.getQueryParameter("src") ?: return null).trim()
    if (src.isEmpty() || page <= 0 || total <= 0 || page > total) return null
    return DeferredPdfPageSpec(sourcePdfPath = src, pageIndex1 = page, totalPages = total)
}

fun isDeferredPdfPageOnlineUrl(url: String?): Boolean = parseDeferredPdfPageOnlineUrl(url) != null

suspend fun copyPdfUriToLocalFile(
    context: Context,
    pdfUri: Uri,
    outputFile: File,
) {
    withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(pdfUri)?.use { ins ->
            FileOutputStream(outputFile).use { outs -> ins.copyTo(outs) }
        } ?: error("Không mở được file PDF")
    }
}

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

private data class PdfHeaderFooterRules(
    val repeatingTop: Set<String>,
    val repeatingBottom: Set<String>,
    val maxBandLines: Int,
)

private fun detectRepeatedPdfHeaderFooterRules(
    pageTexts: List<String>,
    maxBandLines: Int = 5,
): PdfHeaderFooterRules {
    if (pageTexts.isEmpty()) {
        return PdfHeaderFooterRules(
            repeatingTop = emptySet(),
            repeatingBottom = emptySet(),
            maxBandLines = maxBandLines,
        )
    }
    val blocks = pageTexts.map { splitPdfPageLines(it) }
    val n = blocks.size
    val minPagesWithLine =
        when {
            n <= 1 -> 2
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
    return PdfHeaderFooterRules(
        repeatingTop = topKeyHits.filter { it.value >= minPagesWithLine }.keys,
        repeatingBottom = bottomKeyHits.filter { it.value >= minPagesWithLine }.keys,
        maxBandLines = maxBandLines,
    )
}

private fun stripPdfPageByRules(pageText: String, rules: PdfHeaderFooterRules): String {
    val m = splitPdfPageLines(pageText).toMutableList()
    while (m.isNotEmpty()) {
        val k = normalizePdfLineKey(m.first())
        if (k.length >= 3 && k in rules.repeatingTop) {
            m.removeAt(0)
        } else if (isLikelyStandalonePageNumberLine(m.first())) {
            m.removeAt(0)
        } else {
            break
        }
    }
    while (m.isNotEmpty()) {
        val k = normalizePdfLineKey(m.last())
        if (k.length >= 3 && k in rules.repeatingBottom) {
            m.removeAt(m.lastIndex)
        } else if (isLikelyStandalonePageNumberLine(m.last())) {
            m.removeAt(m.lastIndex)
        } else {
            break
        }
    }
    return stripPageNumberMargins(m).joinToString("\n").trim()
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
    onPageFileWritten: (suspend (fileName: String, file: File) -> Unit)? = null,
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
                val samplePageLimit = 10
                val samplePages = ArrayList<String>(minOf(totalPages, samplePageLimit))
                val probeUntil = minOf(totalPages, samplePageLimit)
                for (p in 1..probeUntil) {
                    onProgress?.invoke("Phân tích header/footer: trang $p / $probeUntil")
                    stripper.startPage = p
                    stripper.endPage = p
                    val pageText = stripper.getText(doc).trim()
                    if (pageText.isNotEmpty()) {
                        samplePages.add(pageText)
                    }
                }
                if (samplePages.isEmpty()) {
                    error("Không trích được chữ nào từ PDF (PDF ảnh / trang trống).")
                }
                onProgress?.invoke("Đang suy luận header/footer lặp…")
                val rules = detectRepeatedPdfHeaderFooterRules(samplePages)
                var written = 0
                for (p in 1..totalPages) {
                    onProgress?.invoke("Trang $p / $totalPages")
                    stripper.startPage = p
                    stripper.endPage = p
                    val pageTextRaw = stripper.getText(doc).trim()
                    if (pageTextRaw.isEmpty()) continue
                    val pageText = stripPdfPageByRules(pageTextRaw, rules)
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
