package com.ttsaistory.app.domain

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

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
