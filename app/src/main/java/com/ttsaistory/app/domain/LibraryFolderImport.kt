package com.ttsaistory.app.domain

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Locale
import kotlin.io.bufferedReader

data class DocumentTreeFileEntry(
    val uri: Uri,
    val relativePath: String,
)

private fun collectAllFilesRecursive(
    dir: DocumentFile,
    prefix: String,
): List<DocumentTreeFileEntry> {
    val out = mutableListOf<DocumentTreeFileEntry>()
    for (child in dir.listFiles().orEmpty()) {
        val name = child.name ?: continue
        val rel = if (prefix.isEmpty()) name else "$prefix/$name"
        when {
            child.isFile -> out.add(DocumentTreeFileEntry(child.uri, rel))
            child.isDirectory -> out.addAll(collectAllFilesRecursive(child, rel))
        }
    }
    return out
}

/** Mọi file trong cây SAF (đệ quy), sắp xếp theo [DocumentTreeFileEntry.relativePath]. */
fun listDocumentTreeFilesSorted(context: Context, treeUri: Uri): List<DocumentTreeFileEntry> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Không mở được thư mục")
    if (!root.isDirectory) error("Không phải thư mục")
    return collectAllFilesRecursive(root, "").sortedBy { it.relativePath }
}

/** Quét thư mục cục bộ (vd. giải nén zip) thành cùng định dạng entry như SAF. */
private fun listLocalDirectoryTreeFilesSorted(rootDir: File): List<DocumentTreeFileEntry> {
    val rootCanon = rootDir.canonicalFile
    if (!rootCanon.isDirectory) return emptyList()
    return rootCanon
        .walkTopDown()
        .filter { it.isFile }
        .map { f ->
            val rel =
                rootCanon
                    .toURI()
                    .relativize(f.canonicalFile.toURI())
                    .path
                    .trim('/')
                    .replace('\\', '/')
            DocumentTreeFileEntry(Uri.fromFile(f), rel)
        }
        .sortedBy { it.relativePath }
        .toList()
}

/**
 * Giá trị lưu trong `categories.import_folder_tree_uri`: cây SAF (`content:`) hoặc thư mục cục bộ
 * (`file:///…` / đường dẫn tuyệt đối).
 */
fun listImportFolderFilesSorted(context: Context, storedUriOrPath: String): List<DocumentTreeFileEntry> {
    val t = storedUriOrPath.trim()
    if (t.isEmpty()) return emptyList()
    val parsed = runCatching { Uri.parse(t) }.getOrNull()
    val scheme = parsed?.scheme?.lowercase(Locale.US)
    if (scheme == "file") {
        val p = parsed?.path ?: return emptyList()
        val root = File(p).canonicalFile
        if (root.isDirectory) return listLocalDirectoryTreeFilesSorted(root)
        return emptyList()
    }
    if (parsed != null && DocumentsContract.isTreeUri(parsed)) {
        return listDocumentTreeFilesSorted(context, parsed)
    }
    if (t.startsWith("/")) {
        val root = File(t).canonicalFile
        if (root.isDirectory) return listLocalDirectoryTreeFilesSorted(root)
    }
    error("Không hỗ trợ import_folder_tree_uri (${t.take(96)})")
}

/** Đọc nội dung UTF-8 một entry từ [listImportFolderFilesSorted] (SAF hoặc `file:`). */
fun readUtf8FromImportTreeEntry(context: Context, entry: DocumentTreeFileEntry): String {
    val scheme = entry.uri.scheme?.lowercase(Locale.US)
    if (scheme == "file") {
        val path = entry.uri.path ?: error("file URI không có path")
        return File(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    return readUtf8FromDocumentUri(context, entry.uri)
}

/** Đọc một URI tài liệu (file) UTF-8. */
fun readUtf8FromDocumentUri(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    return resolver.openInputStream(uri)?.use { ins ->
        ins.bufferedReader(Charsets.UTF_8).readText()
    } ?: error("Không mở được file")
}

/**
 * Đọc **mọi file** trong cây SAF (đệ quy), nối nội dung — chỉ dùng cho truyện cũ lưu URI cây
 * ([android.provider.DocumentsContract.isTreeUri]); import mới không ghép nữa.
 */
fun readMergedUtf8FromDocumentTree(context: Context, treeUri: Uri): String {
    val sorted = listDocumentTreeFilesSorted(context, treeUri)
    if (sorted.isEmpty()) error("Thư mục không có file")
    val resolver = context.contentResolver
    val parts = ArrayList<String>(sorted.size)
    for (item in sorted) {
        runCatching {
            resolver.openInputStream(item.uri)?.use { ins ->
                parts.add(ins.bufferedReader(Charsets.UTF_8).readText())
            }
        }
    }
    if (parts.isEmpty()) error("Không đọc được nội dung file")
    return parts.joinToString("\n\n")
}

fun documentTreeDisplayName(context: Context, treeUri: Uri): String =
    DocumentFile.fromTreeUri(context, treeUri)?.name?.trim().orEmpty()
