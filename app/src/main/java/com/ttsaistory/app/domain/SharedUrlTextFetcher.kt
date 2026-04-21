package com.ttsaistory.app.domain

import android.net.Uri
import androidx.core.text.HtmlCompat
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val URL_FETCH_MAX_BYTES = 2_500_000

private val URL_IN_TEXT =
    Pattern.compile("""https?://[^\s<>"']+""", Pattern.CASE_INSENSITIVE)

/**
 * Trích URL http(s) từ văn bản share (một dòng là URL, hoặc dòng đầu / khớp đầu tiên).
 * Trả về null nếu không có URL hợp lệ.
 */
fun parseHttpUrlFromSharedText(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null

    fun trimTrailingPunctuation(u: String): String =
        u.trimEnd(' ', '\t', '.', ',', ';', ':', ')', ']', '"', '\'', '»', '”')

    fun normalizeUrl(candidate: String): String? {
        val s = trimTrailingPunctuation(candidate.trim())
        if (!s.startsWith("http://", ignoreCase = true) &&
            !s.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return try {
            val uri = Uri.parse(s)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") null else uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    val firstLine = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    normalizeUrl(firstLine)?.let { return it }

    val m = URL_IN_TEXT.matcher(t)
    if (m.find()) {
        normalizeUrl(m.group())?.let { return it }
    }
    return null
}

private fun parseCharsetFromContentType(contentType: String?): String? {
    if (contentType.isNullOrBlank()) return null
    val idx = contentType.indexOf("charset=", ignoreCase = true)
    if (idx < 0) return null
    return contentType
        .substring(idx + 8)
        .substringBefore(';')
        .substringBefore(',')
        .trim()
        .removeSurrounding("\"")
        .ifBlank { null }
}

private fun InputStream.readAtMostBytes(max: Int): ByteArray {
    val out = ByteArrayOutputStream(minOf(8192, max))
    val buf = ByteArray(8192)
    var total = 0
    while (total < max) {
        val toRead = minOf(buf.size, max - total)
        val n = read(buf, 0, toRead)
        if (n <= 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

/**
 * Tải nội dung [urlString] (chỉ http/https). HTML được chuyển sang chữ; vượt quá [URL_FETCH_MAX_BYTES] thì cắt bớt.
 */
suspend fun fetchUrlAsPlainText(urlString: String): String =
    withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 45_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 TTS-AI-Story/1.0",
        )
        conn.setRequestProperty("Accept", "text/html,text/plain;q=0.9,*/*;q=0.8")
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            val err =
                conn.errorStream?.use { it.readAtMostBytes(16_384) }?.toString(Charsets.UTF_8)?.trim()
            error("HTTP $code${if (err.isNullOrEmpty()) "" else ": ${err.take(200)}"}")
        }
        val contentTypeHeader = conn.contentType
        val contentType = contentTypeHeader?.lowercase(Locale.ROOT).orEmpty()
        val bytes = conn.inputStream.use { it.readAtMostBytes(URL_FETCH_MAX_BYTES) }
        val charsetName = parseCharsetFromContentType(contentTypeHeader)
        val charset =
            try {
                charsetName?.let { Charset.forName(it) } ?: Charsets.UTF_8
            } catch (_: Exception) {
                Charsets.UTF_8
            }
        var raw = String(bytes, charset)
        if (bytes.size >= URL_FETCH_MAX_BYTES) {
            raw += "\n\n[… đã cắt bớt do trang quá dài]"
        }
        val trimmedStart = raw.trimStart()
        val asPlain =
            when {
                contentType.contains("text/html") ||
                    trimmedStart.startsWith("<!DOCTYPE", ignoreCase = true) ||
                    trimmedStart.startsWith("<html", ignoreCase = true) ||
                    trimmedStart.startsWith("<head", ignoreCase = true) ||
                    trimmedStart.startsWith("<body", ignoreCase = true) ->
                    HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                else -> raw
            }
        asPlain
            .replace(Regex("[\t\r\u000C]+"), " ")
            .replace(Regex(" *\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
