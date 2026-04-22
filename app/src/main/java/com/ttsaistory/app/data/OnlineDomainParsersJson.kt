package com.ttsaistory.app.data

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private const val EXPORT_JSON_VERSION = 1

/** Xuất danh sách parser domain ra JSON (có version, thụt dòng). */
fun onlineDomainParsersToExportJson(rows: List<OnlineDomainParserRow>): String {
    val parsers = JSONArray()
    for (r in rows.sortedBy { it.domain.lowercase(Locale.ROOT) }) {
        val o = JSONObject()
        o.put("domain", r.domain)
        if (r.onlineNextPageSelector.isNullOrBlank()) {
            o.put("nextPageSelector", JSONObject.NULL)
        } else {
            o.put("nextPageSelector", r.onlineNextPageSelector)
        }
        val ja = JSONArray()
        for (s in r.contentSelectors) {
            ja.put(s)
        }
        o.put("contentSelectors", ja)
        parsers.put(o)
    }
    val root = JSONObject()
    root.put("version", EXPORT_JSON_VERSION)
    root.put("parsers", parsers)
    return root.toString(2)
}

data class OnlineDomainParserImportEntry(
    val domain: String,
    val nextPageSelector: String?,
    val contentSelectors: List<String>,
)

/**
 * Đọc JSON xuất từ [onlineDomainParsersToExportJson], hoặc mảng thuần `[{ "domain": ... }, ...]`.
 *
 * @throws IllegalArgumentException nếu không phải JSON hợp lệ hoặc thiếu `parsers` khi là object.
 */
fun parseOnlineDomainParsersImportJson(text: String): List<OnlineDomainParserImportEntry> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    val arr: JSONArray =
        when {
            trimmed.startsWith('[') -> JSONArray(trimmed)
            trimmed.startsWith('{') -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("parsers")
                    ?: throw IllegalArgumentException("Thiếu khóa \"parsers\" (mảng).")
            }
            else -> throw IllegalArgumentException("File không phải JSON hợp lệ.")
        }
    val out = mutableListOf<OnlineDomainParserImportEntry>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val domainRaw = o.optString("domain", "").trim()
        if (domainRaw.isEmpty()) continue
        val domain =
            domainRaw
                .lowercase(Locale.ROOT)
                .removePrefix("www.")
                .trim()
        if (domain.isEmpty()) continue
        val next =
            when {
                !o.has("nextPageSelector") || o.get("nextPageSelector") == JSONObject.NULL -> null
                else -> o.optString("nextPageSelector", "").trim().takeIf { it.isNotEmpty() }
            }
        val contentArr = o.optJSONArray("contentSelectors")
        val selectors =
            buildList {
                if (contentArr != null) {
                    for (j in 0 until contentArr.length()) {
                        contentArr.optString(j, "").trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }
        out.add(OnlineDomainParserImportEntry(domain, next, selectors))
    }
    return out
}
