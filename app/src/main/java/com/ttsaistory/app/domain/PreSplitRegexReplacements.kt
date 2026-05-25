package com.ttsaistory.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Một quy tắc thay thế chuỗi (regex Kotlin) áp dụng **trước** khi gộp dòng / tách câu
 * ([ParagraphSentenceSplitting.parseStoredTextToFlatSentences]).
 */
data class PreSplitRegexReplacementRule(
    val pattern: String,
    val replacement: String,
    val enabled: Boolean = true,
)

object PreSplitRegexReplacements {
    fun defaultRulesJson(): String = "[]"

    fun parseRulesJson(json: String?): List<PreSplitRegexReplacementRule> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json.trim())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pattern = o.optString("pattern", "")
                    val replacement = o.optString("replacement", "")
                    val enabled = !o.has("enabled") || o.optBoolean("enabled", true)
                    if (pattern.isNotEmpty()) {
                        add(PreSplitRegexReplacementRule(pattern, replacement, enabled))
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun rulesToJson(rules: List<PreSplitRegexReplacementRule>): String {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject().apply {
                    put("pattern", r.pattern)
                    put("replacement", r.replacement)
                    put("enabled", r.enabled)
                },
            )
        }
        return arr.toString()
    }

    private fun compileOrNull(pattern: String): Regex? = runCatching { Regex(pattern) }.getOrNull()

    /**
     * Áp dụng lần lượt các quy tắc [enabled]; bỏ qua [pattern] không biên dịch được.
     */
    fun applyRules(text: String, rules: List<PreSplitRegexReplacementRule>): String {
        var s = text
        for (rule in rules) {
            if (!rule.enabled) continue
            val rx = compileOrNull(rule.pattern) ?: continue
            s = rx.replace(s, rule.replacement)
        }
        return s
    }

    fun tryReplaceOne(text: String, pattern: String, replacement: String): Result<String> =
        runCatching {
            Regex(pattern).replace(text, replacement)
        }
}
