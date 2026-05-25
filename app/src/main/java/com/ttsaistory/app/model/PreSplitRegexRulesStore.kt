package com.ttsaistory.app.model

import android.content.SharedPreferences
import com.ttsaistory.app.domain.PreSplitRegexReplacementRule
import com.ttsaistory.app.domain.PreSplitRegexReplacements

/** Bản trong RAM của quy tắc regex tiền xử lý — đồng bộ từ [SharedPreferences] khi mở app / sau khi lưu. */
object PreSplitRegexRulesStore {
    @Volatile
    private var rules: List<PreSplitRegexReplacementRule> = emptyList()

    fun rulesSnapshot(): List<PreSplitRegexReplacementRule> = rules

    fun refreshFrom(prefs: SharedPreferences) {
        val json =
            prefs.getString(AppPreferenceKeys.KEY_PRE_SPLIT_REGEX_RULES_JSON, null)
                ?: PreSplitRegexReplacements.defaultRulesJson()
        rules = PreSplitRegexReplacements.parseRulesJson(json)
    }

    fun saveToPrefs(prefs: SharedPreferences, newRules: List<PreSplitRegexReplacementRule>) {
        prefs
            .edit()
            .putString(
                AppPreferenceKeys.KEY_PRE_SPLIT_REGEX_RULES_JSON,
                PreSplitRegexReplacements.rulesToJson(newRules),
            ).apply()
        rules = newRules
    }
}
