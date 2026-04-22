package com.ttsaistory.app.data

import org.json.JSONArray
import org.json.JSONException

/** Giải mã cột `online_content_selector`: JSON mảng hoặc một chuỗi selector cũ (legacy). */
fun decodeOnlineContentSelectors(raw: String?): List<String> {
    val t = raw?.trim() ?: return emptyList()
    if (t.isEmpty()) return emptyList()
    if (t.startsWith("[")) {
        return try {
            val ja = JSONArray(t)
            buildList {
                for (i in 0 until ja.length()) {
                    val s = ja.optString(i, "").trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        } catch (_: JSONException) {
            listOf(t)
        }
    }
    return listOf(t)
}

/** Ghi danh sách selector nội dung thành JSON mảng. */
fun encodeOnlineContentSelectors(selectors: List<String>): String {
    val ja = JSONArray()
    for (s in selectors.map { it.trim() }.filter { it.isNotEmpty() }) {
        ja.put(s)
    }
    return ja.toString()
}
