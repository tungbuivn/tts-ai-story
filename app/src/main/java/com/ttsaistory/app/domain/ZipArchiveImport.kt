package com.ttsaistory.app.domain

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ZIP_DEFERRED_ONLINE_URL_SCHEME = "zip-lazy"

data class DeferredZipEntrySpec(
    val sourceZipPath: String,
    val entryIndex1: Int,
    val totalEntries: Int,
)

fun deferredZipEntryOnlineUrl(
    sourceZipPath: String,
    entryIndex1: Int,
    totalEntries: Int,
): String {
    val src = Uri.encode(sourceZipPath.trim())
    return "$ZIP_DEFERRED_ONLINE_URL_SCHEME://entry/$entryIndex1?total=$totalEntries&src=$src"
}

fun parseDeferredZipEntryOnlineUrl(url: String?): DeferredZipEntrySpec? {
    val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return null
    if (!uri.scheme.equals(ZIP_DEFERRED_ONLINE_URL_SCHEME, ignoreCase = true)) return null
    if (!uri.host.equals("entry", ignoreCase = true)) return null
    val segs = uri.pathSegments
    if (segs.isEmpty()) return null
    val idx = segs[0].toIntOrNull() ?: return null
    val total = uri.getQueryParameter("total")?.toIntOrNull() ?: return null
    val src = Uri.decode(uri.getQueryParameter("src") ?: return null).trim()
    if (src.isEmpty() || idx <= 0 || total <= 0 || idx > total) return null
    return DeferredZipEntrySpec(src, idx, total)
}

private fun isLikelyTextEntryName(name: String): Boolean {
    val n = name.lowercase(Locale.ROOT)
    return n.endsWith(".txt") || n.endsWith(".text") || n.endsWith(".md") ||
        n.endsWith(".log") || n.endsWith(".csv")
}

suspend fun listZipTextEntryNames(zipFile: File): List<String> =
    withContext(Dispatchers.IO) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name.trim().replace('\\', '/') }
                .filter { it.isNotEmpty() && isLikelyTextEntryName(it) }
                .sorted()
                .toList()
        }
    }

suspend fun readZipTextEntryByIndex(zipFile: File, entryIndex1: Int): String =
    withContext(Dispatchers.IO) {
        val names = listZipTextEntryNames(zipFile)
        if (entryIndex1 !in 1..names.size) return@withContext ""
        val name = names[entryIndex1 - 1]
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(name) ?: return@withContext ""
            zip.getInputStream(entry).use { ins ->
                ins.readBytes().toString(Charsets.UTF_8).trimStart('\uFEFF').trim()
            }
        }
    }

fun uriLooksLikeZipArchive(context: Context, uri: Uri, displayName: String?): Boolean {
    val name = displayName?.trim().orEmpty()
    if (name.endsWith(".zip", ignoreCase = true)) return true
    val t = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
    if (t == "application/zip" || t == "application/x-zip-compressed") return true
    val seg = uri.lastPathSegment.orEmpty()
    return seg.endsWith(".zip", ignoreCase = true)
}

/** Tên thể loại / thư mục giải nén từ tên file .zip (bỏ đuôi, làm sạch ký tự path). */
fun safeCategoryNameFromZipDisplayName(displayName: String): String {
    var s = displayName.trim()
    if (s.endsWith(".zip", ignoreCase = true)) s = s.dropLast(4).trim()
    s = s.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).trim()
    return s.ifEmpty { "Zip" }
}

/**
 * Trả về file đích an toàn cho entry (không phải thư mục, không zip slip), hoặc null nếu bỏ qua.
 */
private fun zipEntryOutputFileOrNull(
    entry: ZipEntry,
    destinationDir: File,
    destCanonical: File,
): Pair<String, File>? {
    if (entry.isDirectory) return null
    val rawName = entry.name.trim().removePrefix("/").replace('\\', '/')
    if (rawName.isEmpty() || rawName.startsWith("..") || "/../" in "/$rawName/") return null
    val outFile = File(destinationDir, rawName)
    val outCanon = outFile.canonicalFile
    if (!outCanon.path.startsWith(destCanonical.path + File.separator)) return null
    return rawName to outFile
}

/**
 * Giải nén toàn bộ entry (bỏ thư mục) vào [destinationDir], giữ cấu trúc con.
 * Bỏ qua entry thoát khỏi [destinationDir] (zip slip).
 *
 * @param onFileExtracted Sau mỗi file ghi xong: `(completedCount, entryName)` với `completedCount` tăng
 * dần 1, 2, 3… (không có tổng số entry vì stream ZIP tuần tự). Callback thường chạy trên IO — UI nên `post` lên main.
 * @return Số file đã giải nén thành công.
 */
fun extractZipContentToDirectory(
    context: Context,
    zipUri: Uri,
    destinationDir: File,
    onFileExtracted: ((completedCount: Int, entryName: String) -> Unit)? = null,
): Int {
    destinationDir.mkdirs()
    if (!destinationDir.isDirectory) error("Không tạo được thư mục giải nén")
    val destCanonical = destinationDir.canonicalFile
    val ins =
        context.contentResolver.openInputStream(zipUri)?.let { BufferedInputStream(it) }
            ?: error("Không mở được file zip")
    var written = 0
    ins.use { bis ->
        ZipInputStream(bis).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val planned = zipEntryOutputFileOrNull(entry, destinationDir, destCanonical)
                if (planned == null) {
                    zis.closeEntry()
                    continue
                }
                val (rawName, outFile) = planned
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                zis.closeEntry()
                written++
                onFileExtracted?.invoke(written, rawName)
            }
        }
    }
    return written
}
