package com.ttsaistory.app.ui.library

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [multilineSelectorText]: nhiều dòng, mỗi dòng một CSS selector (giống ô «Nội dung» trong dialog).
 * Trong WebView, JS `split('\n')` rồi `querySelector` từng dòng, nối `innerText` bằng `\n`.
 */
suspend fun extractPlainTextFromWebViewForSelectors(
    webView: WebView,
    multilineSelectorText: String,
): String =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val quoted = JSONObject.quote(multilineSelectorText)
            val script = jsExtractContentBySelectorsScript(quoted)
            webView.evaluateJavascript(script) { encoded ->
                val o = parseJsJsonObjectFromEvaluate(encoded)
                if (o == null) {
                    cont.resumeWithException(IllegalStateException("Không đọc được nội dung trang"))
                    return@evaluateJavascript
                }
                if (!o.optBoolean("ok")) {
                    val err = o.optString("err").ifEmpty { "Lỗi trích nội dung" }
                    cont.resumeWithException(IllegalStateException(err))
                    return@evaluateJavascript
                }
                cont.resume(o.optString("text"))
            }
        }
    }

/** Tiện ích khi đã có sẵn [List] selector (cùng thứ tự với nhiều dòng). */
suspend fun extractPlainTextFromWebViewForSelectors(
    webView: WebView,
    selectors: List<String>,
): String =
    extractPlainTextFromWebViewForSelectors(
        webView,
        selectors.joinToString("\n"),
    )

/**
 * null nếu không có selector / không tìm thấy phần tử / không phải `<a>` (hoặc không có con `<a>`) /
 * thiếu hoặc rỗng thuộc tính **`href`**.
 */
suspend fun resolveNextPageAbsoluteUrlFromWebView(
    webView: WebView,
    nextPageSelector: String?,
    baseUrlForRelative: String,
): String? {
    val sel = nextPageSelector?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val base = baseUrlForRelative.trim().takeIf { it.isNotEmpty() } ?: return null
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val script =
                jsResolveNextPageHrefScript(
                    JSONObject.quote(sel),
                    JSONObject.quote(base),
                )
            webView.evaluateJavascript(script) { encoded ->
                val o = parseJsJsonObjectFromEvaluate(encoded)
                if (o == null || !o.optBoolean("ok")) {
                    cont.resume(null)
                    return@evaluateJavascript
                }
                cont.resume(o.optString("href", "").trim().takeIf { it.isNotEmpty() })
            }
        }
    }
}
