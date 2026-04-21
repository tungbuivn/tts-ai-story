package com.ttsaistory.app.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** Đọc nội dung UTF-8 từ uri (SEND stream hoặc VIEW / Open with). */
fun readSendStreamAsText(context: Context, uri: Uri): String? =
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        }
    } catch (_: Exception) {
        null
    }

/** VIEW / Open with: chỉ nạp khi mime là text hoặc đuôi .txt (kèm octet-stream). */
fun shouldTreatViewUriAsTxt(uri: Uri, contentType: String?): Boolean {
    val type = contentType?.lowercase(Locale.ROOT).orEmpty()
    if (type.startsWith("text/")) return true
    val path = uri.path.orEmpty()
    val seg = uri.lastPathSegment.orEmpty()
    val endsTxt =
        path.endsWith(".txt", ignoreCase = true) || seg.endsWith(".txt", ignoreCase = true)
    if (type == "application/octet-stream" || type == "binary/octet-stream") {
        return endsTxt
    }
    return endsTxt
}

/** Tên file từ DocumentProvider / content (null nếu không đọc được). */
fun resolveDocumentDisplayName(context: Context, uri: Uri): String? =
    try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
                null
            }
    } catch (_: Exception) {
        null
    }

/**
 * Tên thể loại từ tên file: bỏ đuôi phổ biến (.txt, .md, …), trim; rỗng → "Mở từ file".
 */
fun fileStemForLibraryCategory(displayName: String): String {
    val trimmed = displayName.trim()
    if (trimmed.isEmpty()) return "Mở từ file"
    val dot = trimmed.lastIndexOf('.')
    if (dot <= 0 || dot >= trimmed.length - 1) return trimmed
    val ext = trimmed.substring(dot + 1).lowercase(Locale.ROOT)
    if (ext in TEXT_LIKE_FILE_EXTENSIONS) {
        return trimmed.substring(0, dot).trim().ifEmpty { "Mở từ file" }
    }
    return trimmed
}

private val TEXT_LIKE_FILE_EXTENSIONS =
    setOf(
        "txt",
        "text",
        "md",
        "markdown",
        "log",
        "csv",
        "json",
        "xml",
        "html",
        "htm",
    )
