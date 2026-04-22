/**
 * Sao chép nội dung font từ [android.net.Uri] (Storage Access Framework) vào thư mục nội bộ của app
 * để dùng ổn định sau khi người dùng chọn file qua hệ thống.
 */
package com.ttsaistory.app.ui.fonts

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Copy font từ [uri] (SAF) vào `filesDir/editor_fonts/[destSimpleName]`. */
fun copyUriToEditorFontFile(context: Context, uri: Uri, destSimpleName: String): File? {
    val dir = File(context.filesDir, "editor_fonts").apply { mkdirs() }
    val out = File(dir, destSimpleName)
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: return null
        out.takeIf { it.isFile && it.length() > 0L }
    } catch (_: Throwable) {
        runCatching { out.delete() }
        null
    }
}
