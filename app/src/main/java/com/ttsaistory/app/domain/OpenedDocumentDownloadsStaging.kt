package com.ttsaistory.app.domain

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val STAGING_FOLDER_SEGMENT = "tts-ai-story"

/** `Download/tts-ai-story` — cùng quy ước với xuất thư viện. */
private val stagingRelativePath: String
    get() = "${Environment.DIRECTORY_DOWNLOADS}/$STAGING_FOLDER_SEGMENT"

private fun sanitizeDownloadsDisplayName(raw: String): String {
    var s = raw.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").take(120)
    if (s.isEmpty()) s = "opened_file"
    return s
}

private fun resolveStagingBaseName(displayName: String?, sourceUri: Uri): String {
    val fromMeta = displayName?.trim()?.takeIf { it.isNotEmpty() }
    if (fromMeta != null) return sanitizeDownloadsDisplayName(fromMeta)
    val seg = sourceUri.lastPathSegment?.trim().orEmpty()
    val cleaned =
        seg.substringAfterLast(':', seg).substringAfterLast('/', seg).trim()
    return sanitizeDownloadsDisplayName(cleaned.ifEmpty { "opened_file" })
}

private fun uniqueStagingFileName(baseName: String): String {
    val ts = System.currentTimeMillis()
    val dot = baseName.lastIndexOf('.')
    return if (dot > 0 && dot < baseName.length - 1) {
        val stem = baseName.substring(0, dot).take(80)
        val ext = baseName.substring(dot).take(12)
        "${stem}_$ts$ext"
    } else {
        "${baseName.take(100)}_$ts"
    }
}

/**
 * Sao chép toàn bộ nội dung từ [sourceUri] (SAF, Drive, v.v.) vào thư mục **Download/tts-ai-story**,
 * rồi trả về URI đọc lại được qua [android.content.ContentResolver] (MediaStore hoặc file cục bộ).
 */
fun copyPickedDocumentToDownloadsTtsAiStoryFolder(
    context: Context,
    sourceUri: Uri,
    displayName: String?,
): Uri {
    val resolver = context.contentResolver
    val uniqueName = uniqueStagingFileName(resolveStagingBaseName(displayName, sourceUri))
    val mimeType = resolver.getType(sourceUri) ?: "application/octet-stream"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, stagingRelativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val outUri =
            resolver.insert(collection, values)
                ?: error("Không tạo được file trong Download/tts-ai-story")
        try {
            resolver.openOutputStream(outUri)?.use { output ->
                resolver.openInputStream(sourceUri)?.use { input ->
                    input.copyTo(output, bufferSize = 8192)
                } ?: error("Không đọc được file đã chọn")
            } ?: error("Không ghi được bản sao trong Downloads")
        } catch (t: Throwable) {
            runCatching { resolver.delete(outUri, null, null) }
            throw t
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(outUri, values, null, null)
        return outUri
    }

    val downloadsRoot =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Không có thư mục Downloads")
    val dir = File(downloadsRoot, STAGING_FOLDER_SEGMENT)
    if (!dir.exists() && !dir.mkdirs()) {
        error("Không tạo được thư mục ${dir.absolutePath}")
    }
    var outFile = File(dir, uniqueName)
    var suffix = 0
    while (outFile.exists()) {
        suffix++
        val dot = uniqueName.lastIndexOf('.')
        val next =
            if (dot > 0 && dot < uniqueName.length - 1) {
                "${uniqueName.substring(0, dot)}_$suffix${uniqueName.substring(dot)}"
            } else {
                "${uniqueName}_$suffix"
            }
        outFile = File(dir, next)
    }
    resolver.openInputStream(sourceUri)?.use { input ->
        FileOutputStream(outFile).use { output -> input.copyTo(output, bufferSize = 8192) }
    } ?: error("Không đọc được file đã chọn")
    @Suppress("DEPRECATION")
    return Uri.fromFile(outFile)
}

/**
 * Xóa bản sao trong **Download/tts-ai-story** do [copyPickedDocumentToDownloadsTtsAiStoryFolder] tạo,
 * khi [importSourceUri] trỏ tới đúng file đó (MediaStore `RELATIVE_PATH` = `Download/tts-ai-story`,
 * hoặc `file://` trực tiếp trong thư mục đó — không xóa URI cây SAF, không xóa file trong thư mục con như xuất thư viện).
 */
fun deleteTtsAiStoryDownloadsStagingCopy(context: Context, importSourceUri: String?) {
    val s = importSourceUri?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val uri = runCatching { Uri.parse(s) }.getOrNull() ?: return
    if (DocumentsContract.isTreeUri(uri)) return
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return
    if (scheme == "content") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rel =
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.trim() else null
                } ?: return
            val normalized = rel.trimEnd('/').lowercase(Locale.ROOT)
            val expected = stagingRelativePath.trimEnd('/').lowercase(Locale.ROOT)
            if (normalized != expected) return
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        return
    }
    if (scheme != "file") return
    val path = uri.path ?: return
    val f = File(path)
    val parent = f.parentFile ?: return
    if (!parent.name.equals(STAGING_FOLDER_SEGMENT, ignoreCase = true)) return
    val grand = parent.parentFile ?: return
    if (!grand.name.equals(Environment.DIRECTORY_DOWNLOADS, ignoreCase = true)) return
    runCatching { f.delete() }
}
