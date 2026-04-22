package com.ttsaistory.app.ui.library

import org.json.JSONTokener
import org.json.JSONObject

/** Kết quả evaluateJavascript (chuỗi JSON hoặc chuỗi JSON được encode thêm một lớp). */
internal fun parseJsJsonObjectFromEvaluate(evalResult: String?): JSONObject? {
    if (evalResult.isNullOrBlank() || evalResult == "null") return null
    val t = evalResult.trim()
    return try {
        when {
            t.startsWith('{') -> JSONObject(t)
            t.startsWith('"') -> {
                val inner = JSONTokener(t).nextValue() as? String ?: return null
                JSONObject(inner)
            }
            else -> JSONObject(t)
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Trích [innerText] theo từng selector: [quotedMultilineSelectorsText] là chuỗi JS đã quote,
 * nội dung là text nhiều dòng — tách bằng LF ([String.fromCharCode](10))), `querySelector` từng dòng,
 * nối kết quả bằng cùng LF (tránh literal `'\\n'`/`'\\n\\n'` bị `.replace("\\n", " ")` Kotlin làm hỏng).
 */
internal fun jsExtractContentBySelectorsScript(quotedMultilineSelectorsText: String): String =
    """
    (function(multiline){
      try {
        var NL = String.fromCharCode(10);
        var selectors = String(multiline == null ? '' : multiline)
          .split(NL)
          .map(function(s){ return String(s).trim(); })
          .filter(function(s){ return s.length > 0; });
        var parts = [];
        for (var i = 0; i < selectors.length; i++) {
          var s = selectors[i];
          var el = document.querySelector(s);
          if (!el) continue;
          var t = (el.innerText != null ? el.innerText : '') || (el.textContent || '');
          t = String(t).replace(/\\u00a0/g, ' ').replace(/\r\n/g, NL).replace(/\r/g, NL).trim();
          if (t.length) parts.push(t);
        }
        return JSON.stringify({ok:true, text: parts.join(NL)});
      } catch (e) {
        return JSON.stringify({ok:false, err: String(e && e.message ? e.message : e)});
      }
    })($quotedMultilineSelectorsText)
    """
        .trimIndent()
        .replace("\n", " ")

/**
 * Lấy URL tuyệt đối trang kế: [document.querySelector](sel) phải là thẻ `<a>` (hoặc chứa một `<a>` con);
 * chỉ dùng thuộc tính **`href`** (không dùng `element.href` DOM nếu thiếu attribute).
 * [quotedSelector], [quotedBaseUrl] = chuỗi JS đã quote.
 */
internal fun jsResolveNextPageHrefScript(
    quotedSelector: String,
    quotedBaseUrl: String,
): String =
    """
    (function(sel, base){
      try {
        if (!sel || !String(sel).trim()) return JSON.stringify({ok:false, err:'empty_sel'});
        var el = document.querySelector(sel);
        if (!el) return JSON.stringify({ok:false, err:'no_el'});
        var a = el;
        var tag = el.tagName ? String(el.tagName).toUpperCase() : '';
        if (tag !== 'A') {
          var inner = el.querySelector('a');
          if (!inner) return JSON.stringify({ok:false, err:'not_anchor'});
          a = inner;
        }
        var hrefRaw = a.getAttribute('href');
        if (hrefRaw == null) return JSON.stringify({ok:false, err:'no_href_attr'});
        hrefRaw = String(hrefRaw).trim();
        if (!hrefRaw) return JSON.stringify({ok:false, err:'empty_href'});
        var abs = new URL(hrefRaw, base).href;
        return JSON.stringify({ok:true, href: abs});
      } catch (e) {
        return JSON.stringify({ok:false, err: String(e && e.message ? e.message : e)});
      }
    })($quotedSelector, $quotedBaseUrl)
    """
        .trimIndent()
        .replace("\n", " ")
