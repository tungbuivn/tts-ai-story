package com.ttsaistory.app.domain

import org.json.JSONArray

/** Ghi/đọc danh sách câu xuất AAC (mỗi phần tử một chuỗi, có thể chứa `\n`). */
object TtsExportPartsSnapshot {

    fun encode(parts: List<String>): String {
        val a = JSONArray()
        parts.forEach { a.put(it) }
        return a.toString()
    }

    fun decode(json: String): List<String> {
        val a = JSONArray(json.trim())
        return List(a.length()) { i -> a.getString(i) }
    }
}
