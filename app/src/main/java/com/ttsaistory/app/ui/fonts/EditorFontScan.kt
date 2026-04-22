/**
 * Quét thư mục đĩa hoặc cây SAF để liệt kê font .ttf/.otf, đọc thư mục quét từ prefs,
 * và dựng danh sách hàng chọn font cho hộp thoại cấu hình.
 */
package com.ttsaistory.app.ui.fonts

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ttsaistory.app.model.AppPreferenceKeys
import java.io.File
import java.util.Locale

fun listEditorFontFilesInDirectory(dirPath: String): List<String> {
    val dir = File(dirPath.trim())
    if (!dir.isDirectory || !dir.canRead()) return emptyList()
    return dir
        .listFiles()
        .orEmpty()
        .filter { f ->
            f.isFile &&
                f.canRead() &&
                when (f.extension.lowercase(Locale.US)) {
                    "ttf", "otf" -> true
                    else -> false
                }
        }
        .sortedBy { it.name.lowercase(Locale.US) }
        .map { it.absolutePath }
}

fun listEditorFontUrisUnderTree(context: Context, treeUri: Uri): List<Pair<String, Uri>> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    if (!root.isDirectory) return emptyList()
    return root
        .listFiles()
        .asSequence()
        .filter { it.isFile }
        .mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            val lower = name.lowercase(Locale.US)
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) return@mapNotNull null
            name to doc.uri
        }
        .sortedBy { it.first.lowercase(Locale.US) }
        .toList()
}

fun editorFontScanDirOrDefault(prefs: SharedPreferences): String {
    val s = prefs.getString(AppPreferenceKeys.KEY_EDITOR_FONT_SCAN_DIR, null)?.trim().orEmpty()
    val base = if (s.isNotEmpty()) s else AppPreferenceKeys.DEFAULT_EDITOR_FONT_SCAN_DIR
    return when (base) {
        "/sdcard/fonts", "/storage/sdcard0/fonts" -> AppPreferenceKeys.DEFAULT_EDITOR_FONT_SCAN_DIR
        else -> base
    }
}

data class EditorFontPickRow(
    val label: String,
    val absolutePath: String?,
    val pickUri: Uri?,
)

fun buildEditorFontPickList(context: Context, dirPath: String, treeUriStr: String): List<EditorFontPickRow> {
    if (treeUriStr.isNotEmpty()) {
        val uri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: return emptyList()
        return listEditorFontUrisUnderTree(context, uri).map { (name, u) ->
            EditorFontPickRow(label = name, absolutePath = null, pickUri = u)
        }
    }
    return listEditorFontFilesInDirectory(dirPath.trim()).map { path ->
        EditorFontPickRow(label = File(path).name, absolutePath = path, pickUri = null)
    }
}
