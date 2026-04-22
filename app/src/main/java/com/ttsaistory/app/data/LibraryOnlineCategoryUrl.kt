package com.ttsaistory.app.data

import android.net.Uri
import java.util.Locale

/** Chuẩn hóa chuỗi người dùng nhập (có thể thiếu scheme) thành URL tuyệt đối. */
fun normalizeWebCategoryUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return ""
    return if (t.contains("://", ignoreCase = true)) t else "https://$t"
}

/** true nếu sau khi chuẩn hóa là http(s) và có host. */
fun looksLikeWebCategoryUrl(input: String): Boolean {
    val normalized = normalizeWebCategoryUrl(input.trim())
    if (normalized.isEmpty()) return false
    val uri = Uri.parse(normalized)
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    return !uri.host.isNullOrBlank()
}

/** So khớp hai URL trang truyện online (chuẩn hóa nhẹ, bỏ fragment). */
/**
 * Chuẩn hóa một dòng URL (hoặc host) thành **domain** dùng làm khóa parser: chữ thường, bỏ tiền tố `www.`.
 * Trả `null` nếu không có host hợp lệ.
 */
fun normalizedOnlineParserDomainKey(input: String): String? {
    val t = input.trim()
    if (t.isEmpty()) return null
    val candidate = normalizeWebCategoryUrl(t)
    if (!looksLikeWebCategoryUrl(t) && !looksLikeWebCategoryUrl(candidate)) return null
    val uri = Uri.parse(candidate)
    val host = uri.host?.trim()?.lowercase(Locale.ROOT) ?: return null
    if (host.isEmpty()) return null
    return host.removePrefix("www.")
}

/** Mỗi dòng một URL — trích các domain khác nhau (đã chuẩn hóa). */
fun distinctNormalizedDomainsFromUrlLines(raw: String): List<String> =
    raw.lineSequence()
        .mapNotNull { normalizedOnlineParserDomainKey(it) }
        .distinct()
        .toList()

fun normalizeOnlineStoryPageUrlForMatch(input: String): String {
    val raw = normalizeWebCategoryUrl(input.trim())
    if (raw.isEmpty()) return ""
    return try {
        val u = Uri.parse(raw)
        u.buildUpon()
            .fragment(null)
            .build()
            .toString()
            .trim()
            .trimEnd('/')
            .lowercase(Locale.ROOT)
    } catch (_: Exception) {
        raw.trim().trimEnd('/').lowercase(Locale.ROOT)
    }
}
