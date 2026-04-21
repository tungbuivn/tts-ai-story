package com.ttsaistory.app.domain

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.file.Paths
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.io.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Số chương EPUB xử lý đồng thời tối đa (đọc XHTML + chuyển text trong bộ nhớ). */
private const val EPUB_CHAPTER_CONVERT_PARALLELISM = 10

fun uriLooksLikeEpubArchive(context: Context, uri: Uri, displayName: String?): Boolean {
    val name = displayName?.trim().orEmpty()
    if (name.endsWith(".epub", ignoreCase = true)) return true
    val t = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
    if (t.contains("epub")) return true
    return uri.lastPathSegment?.endsWith(".epub", ignoreCase = true) == true
}

fun safeCategoryNameFromEpubDisplayName(displayName: String): String {
    var s = displayName.trim()
    if (s.endsWith(".epub", ignoreCase = true)) s = s.dropLast(5).trim()
    s = s.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).trim()
    return s.ifEmpty { "EPUB" }
}

private data class ManifestItem(
    val href: String,
    val mediaType: String?,
)

private fun shouldReadSpineItemAsHtmlChapter(mediaType: String?, href: String): Boolean {
    val m = mediaType?.lowercase(Locale.US).orEmpty()
    if (m.contains("ncx") || m.contains("dtbncx")) return false
    if (m.contains("svg")) return false
    if (m.contains("application/vnd.adobe.page-template+xml")) return false
    val h = href.lowercase(Locale.US)
    return m.contains("html") ||
        m.contains("xhtml") ||
        h.endsWith(".html") ||
        h.endsWith(".xhtml") ||
        h.endsWith(".htm")
}

private fun readZipEntryText(zip: ZipFile, entryPath: String): String? {
    val norm = entryPath.replace('\\', '/').trimStart('/')
    val direct = zip.getEntry(norm) ?: zip.getEntry(norm.trimStart('/'))
    val entry =
        direct
            ?: zip.entries().asSequence().map { it.name }.firstOrNull {
                it.replace('\\', '/').trimStart('/') == norm
            }?.let { zip.getEntry(it) }
            ?: zip.entries().asSequence().map { it.name }.firstOrNull {
                it.replace('\\', '/').trimStart('/').equals(norm, ignoreCase = true)
            }?.let { zip.getEntry(it) }
            ?: return null
    return zip.getInputStream(entry).use { ins ->
        ins.readBytes().toString(Charsets.UTF_8).trimStart('\uFEFF')
    }
}

private fun readContainerOpfPath(containerXml: String): String? {
    val m =
        Regex(
            """full-path\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        ).find(containerXml)
    return m?.groupValues?.get(1)?.trim()?.replace('\\', '/')
}

private fun parseManifestItems(opfXml: String): Map<String, ManifestItem> {
    val out = mutableMapOf<String, ManifestItem>()
    val itemTag = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE)
    for (mt in itemTag.findAll(opfXml)) {
        val chunk = mt.value
        val id =
            Regex("""\bid\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(chunk)?.groupValues?.get(1)
                ?: continue
        val href =
            Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(chunk)?.groupValues?.get(1)
                ?: continue
        val media =
            Regex("""media-type\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(chunk)?.groupValues?.get(1)
        out[id] = ManifestItem(href = href, mediaType = media)
    }
    return out
}

private fun parseSpineIdrefs(opfXml: String): List<String> {
    val out = mutableListOf<String>()
    val itemref = Regex("""<itemref\b[^>]*>""", RegexOption.IGNORE_CASE)
    for (mt in itemref.findAll(opfXml)) {
        val chunk = mt.value
        val idref =
            Regex("""\bidref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(chunk)?.groupValues?.get(1)
                ?: continue
        out.add(idref)
    }
    return out
}

private fun resolveHrefAgainstOpf(opfPathInZip: String, hrefRaw: String): String {
    val href =
        runCatching {
            URLDecoder.decode(hrefRaw.trim(), Charsets.UTF_8.name())
        }.getOrElse { hrefRaw.trim() }
    val opfParent = opfPathInZip.substringBeforeLast("/", "").trim('/')
    val combined =
        if (opfParent.isEmpty()) {
            Paths.get(href)
        } else {
            Paths.get(opfParent, href)
        }
    return combined.normalize().toString().replace('\\', '/')
}

/**
 * Đọc EPUB từ [epubUri], chuyển mỗi mục spine (X)HTML thành text, ghi
 * `00000001.txt`, `00000002.txt`, … vào [destDir] (đã được dọn trước).
 *
 * Luồng xử lý (sau khi đã có thứ tự spine và đường dẫn entry):
 * 1. **Hàng đợi raw** — đọc nội dung HTML từ ZIP (song song, tối đa [EPUB_CHAPTER_CONVERT_PARALLELISM]).
 * 2. **Parse** HTML/XHTML → plain text (consumer song song trên cùng hàng đợi raw).
 * 3. **Hàng đợi lưu** — `(tên file .txt, nội dung)` theo thứ tự đánh số chương; một coroutine ghi đĩa.
 *
 * Ghi file đánh số theo thứ tự spine (bỏ chương rỗng sau parse).
 *
 * @param onChapterFileWritten sau mỗi file `NNNNNNNN.txt` ghi xong (vd. hàng đợi nhập thư viện giống ZIP).
 * @return số chương đã ghi
 */
suspend fun importEpubChaptersAsNumberedTxtFiles(
    context: Context,
    epubUri: Uri,
    destDir: File,
    onProgress: ((String) -> Unit)? = null,
    onChapterFileWritten: ((fileName: String, file: File) -> Unit)? = null,
): Int {
    destDir.mkdirs()
    if (!destDir.isDirectory) error("Không tạo được thư mục EPUB")
    val tmp =
        File(context.cacheDir, "epub_work_${System.currentTimeMillis()}.epub").apply {
            deleteOnExit()
        }
    context.contentResolver.openInputStream(epubUri)?.use { ins ->
        FileOutputStream(tmp).use { outs -> ins.copyTo(outs) }
    } ?: error("Không mở được file EPUB")
    try {
        val chapterPathsInSpineOrder =
            ZipFile(tmp).use { zip ->
                if (zip.getEntry("META-INF/encryption.xml") != null) {
                    error("EPUB có mã hoá — không hỗ trợ.")
                }
                val containerXml =
                    readZipEntryText(zip, "META-INF/container.xml")
                        ?: error("Thiếu META-INF/container.xml")
                val opfRel =
                    readContainerOpfPath(containerXml)?.trim()?.replace('\\', '/')
                        ?: error("Không đọc được đường dẫn package.opf")
                val opfXml =
                    readZipEntryText(zip, opfRel)
                        ?: error("Không đọc được $opfRel")
                val manifest = parseManifestItems(opfXml)
                var idrefs = parseSpineIdrefs(opfXml)
                if (idrefs.isEmpty()) {
                    idrefs =
                        manifest.entries
                            .filter { (_, item) ->
                                shouldReadSpineItemAsHtmlChapter(item.mediaType, item.href)
                            }
                            .map { it.key }
                }
                val paths = ArrayList<String>(idrefs.size)
                for (idref in idrefs) {
                    val item = manifest[idref] ?: continue
                    if (!shouldReadSpineItemAsHtmlChapter(item.mediaType, item.href)) continue
                    paths.add(resolveHrefAgainstOpf(opfRel, item.href))
                }
                if (paths.isEmpty()) {
                    error("Không đọc được chương HTML/XHTML nào từ EPUB.")
                }
                paths
            }
        val n = chapterPathsInSpineOrder.size
        val parallelism = EPUB_CHAPTER_CONVERT_PARALLELISM.coerceAtLeast(1)
        return coroutineScope {
            val rawHtmlChannel = Channel<Pair<Int, String>>(Channel.UNLIMITED)
            val plains = arrayOfNulls<String?>(n)

            val readJob =
                launch(Dispatchers.IO) {
                    try {
                        coroutineScope {
                            (0 until n)
                                .map { i ->
                                    async(Dispatchers.IO) {
                                        val pathInZip = chapterPathsInSpineOrder[i]
                                        val rawHtml =
                                            ZipFile(tmp).use { z ->
                                                readZipEntryText(z, pathInZip) ?: ""
                                            }
                                        rawHtmlChannel.send(i to rawHtml)
                                        onProgress?.invoke(pathInZip)
                                    }
                                }
                                .awaitAll()
                        }
                    } finally {
                        rawHtmlChannel.close()
                    }
                }

            val parseWorkers =
                List(parallelism) {
                    launch(Dispatchers.Default) {
                        for ((i, rawHtml) in rawHtmlChannel) {
                            val plain =
                                if (rawHtml.isBlank()) {
                                    null
                                } else {
                                    htmlOrXhtmlToPlainText(rawHtml).trim().ifEmpty { null }
                                }
                            plains[i] = plain
                        }
                    }
                }

            readJob.join()
            parseWorkers.forEach { it.join() }

            val saveChannel = Channel<Pair<String, String>>(Channel.UNLIMITED)
            val saver =
                launch(Dispatchers.IO) {
                    for ((name, plain) in saveChannel) {
                        val outFile = File(destDir, name)
                        outFile.writeText(plain, Charsets.UTF_8)
                        onChapterFileWritten?.invoke(name, outFile)
                    }
                }

            val chapterIndex =
                withContext(Dispatchers.IO) {
                    var idx = 0
                    for (i in 0 until n) {
                        val plain = plains[i] ?: continue
                        idx++
                        val name = String.format(Locale.US, "%08d.txt", idx)
                        saveChannel.send(name to plain)
                    }
                    saveChannel.close()
                    idx
                }

            saver.join()
            if (chapterIndex == 0) {
                error("Không đọc được chương HTML/XHTML nào từ EPUB.")
            }
            chapterIndex
        }
    } finally {
        tmp.delete()
    }
}

/** Kiểm tra nhanh stream có magic ZIP (PK) — một số provider gửi EPUB dạng octet-stream. */
fun streamLooksLikeZipOrEpub(context: Context, uri: Uri): Boolean {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val header = ByteArray(4)
            val n = ins.read(header)
            n >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        } == true
    }.getOrElse { false }
}
