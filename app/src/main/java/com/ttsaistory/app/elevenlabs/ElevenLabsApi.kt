package com.ttsaistory.app.elevenlabs

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ElevenLabsVoice(
    val voiceId: String,
    val name: String,
    val category: String?,
    val description: String?,
    /** Nhãn từ API (vd. `language`, `accent`, `gender`…). */
    val labels: Map<String, String> = emptyMap(),
    /** Từ `/v2/voices` → `verified_languages[].language` (ISO, chữ thường). */
    val verifiedLanguageTags: List<String> = emptyList(),
    /** `preview_url` từ API (nếu có). */
    val previewUrl: String? = null,
    /** `samples[0].sample_id` — dùng GET `/v1/voices/.../samples/.../audio`. */
    val sampleId: String? = null,
) {
    fun hasPlayableSample(): Boolean =
        !previewUrl.isNullOrBlank() || !sampleId.isNullOrBlank()

    /** `labels["language"]` — ElevenLabs dùng để gợi ý ngôn ngữ giọng. */
    val labelLanguage: String?
        get() = labels["language"]?.trim()?.takeIf { it.isNotEmpty() }

    /** `labels["gender"]` — thường `male` / `female` / `neutral`. */
    val labelGender: String?
        get() = labels["gender"]?.trim()?.takeIf { it.isNotEmpty() }

    /** Chuỗi hiển thị giới tính (gần với API, có map tiếng Việt cho giá trị phổ biến). */
    fun genderLabelForUi(): String? {
        val raw = labelGender ?: return null
        return when (raw.lowercase()) {
            "male", "m" -> "Nam"
            "female", "f" -> "Nữ"
            "neutral" -> "Trung tính"
            else ->
                raw.replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase() else c.toString()
                }
        }
    }

    /**
     * Nhóm để lọc UI: `male` / `female` / `neutral` / `other` (nhãn lạ),
     * hoặc `null` khi không có `labels.gender`.
     */
    fun genderFilterBucket(): String? {
        val g = labelGender?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (g) {
            "male", "m" -> "male"
            "female", "f" -> "female"
            "neutral" -> "neutral"
            else -> "other"
        }
    }

    fun matchesTtsLanguage(iso: String): Boolean {
        val i = iso.lowercase()
        val lab = labelLanguage?.lowercase()?.trim()
        if (!lab.isNullOrEmpty() && (lab == i || lab.startsWith("$i-") || lab.startsWith("${i}_"))) {
            return true
        }
        return verifiedLanguageTags.any { t ->
            t == i || t.startsWith("$i-") || t.startsWith("${i}_")
        }
    }
}

data class ElevenLabsModel(
    val modelId: String,
    val name: String,
    val canDoTextToSpeech: Boolean,
)

/**
 * Hạn mức kỳ billing từ [GET /v1/user/subscription](https://api.elevenlabs.io/v1/user/subscription).
 * Hiển thị dạng `remaining/total` (vd. `9321/10000`).
 */
data class ElevenLabsSubscriptionQuota(
    val remaining: Long,
    val total: Long,
) {
    fun displayAsRatio(): String = "$remaining/$total"
}

/**
 * REST ElevenLabs (vd. `/v2/voices`). SDK `elevenlabs-android` trong repo mẫu chủ yếu cho ConvAI,
 * không thay thế endpoint liệt kê giọng — vẫn dùng API HTTP chính thức.
 */
object ElevenLabsApi {

    /**
     * `auto`: toàn bộ giọng trong cache (sắp xếp theo tên). `vi`/`en`/khác: **chỉ** giọng khớp
     * ngôn ngữ (`labels.language` + `verified_languages`).
     */
    fun voicesMatchingLanguageOnly(
        languageUiKey: String,
        all: List<ElevenLabsVoice>,
    ): List<ElevenLabsVoice> {
        val byName = compareBy<ElevenLabsVoice> { it.name.lowercase() }
        if (languageUiKey == "auto") return all.sortedWith(byName)
        val iso =
            when (languageUiKey) {
                "vi" -> "vi"
                "en" -> "en"
                else -> languageUiKey.lowercase()
            }
        return all.filter { it.matchesTtsLanguage(iso) }.sortedWith(byName)
    }

    /**
     * `filterKey`: `all` | `male` | `female` | `neutral` | `none` (không nhãn gender) | `other`.
     */
    fun voicesMatchingGenderFilter(
        filterKey: String,
        voices: List<ElevenLabsVoice>,
    ): List<ElevenLabsVoice> {
        if (filterKey == "all") return voices
        return voices.filter { v ->
            val b = v.genderFilterBucket()
            when (filterKey) {
                "male" -> b == "male"
                "female" -> b == "female"
                "neutral" -> b == "neutral"
                "none" -> b == null
                "other" -> b == "other"
                else -> true
            }
        }
    }

    private fun httpGetString(url: String, apiKey: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("xi-api-key", apiKey)
        val code = conn.responseCode
        val text =
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException(elevenLabsHttpUserMessage(code, text))
        }
        return text
    }

    private fun JSONObject.subscriptionLongField(vararg keys: String): Long? {
        for (k in keys) {
            if (!has(k)) continue
            if (opt(k) === JSONObject.NULL) continue
            when (val v = opt(k)) {
                is Number -> return v.toLong()
                is String ->
                    v.trim()
                        .replace(",", "")
                        .replace(" ", "")
                        .toLongOrNull()
                        ?.let { return it }
            }
        }
        return null
    }

    private fun parseSubscriptionQuotaJson(root: JSONObject): ElevenLabsSubscriptionQuota? {
        val o = root.optJSONObject("subscription") ?: root

        val charTotal = o.subscriptionLongField("character_limit", "characterLimit")
        val charUsed = o.subscriptionLongField("character_count", "characterCount", "characters_used")
        val charRemaining =
            o.subscriptionLongField(
                "character_remaining",
                "remaining_characters",
                "credits_remaining",
                "remaining_credits",
            )

        if (charTotal != null && charTotal > 0L) {
            val rem =
                when {
                    charRemaining != null -> charRemaining
                    charUsed != null -> (charTotal - charUsed).coerceAtLeast(0L)
                    else -> return null
                }.coerceIn(0L, charTotal)
            return ElevenLabsSubscriptionQuota(remaining = rem, total = charTotal)
        }

        val creditTotal =
            o.subscriptionLongField(
                "total_credits",
                "max_credits",
                "monthly_credits",
                "credit_limit",
                "credits_limit",
            )
        val creditRem =
            o.subscriptionLongField(
                "remaining_credits",
                "credits_remaining",
                "available_credits",
            )
        if (creditTotal != null && creditTotal > 0L && creditRem != null) {
            return ElevenLabsSubscriptionQuota(
                remaining = creditRem.coerceIn(0L, creditTotal),
                total = creditTotal,
            )
        }

        return null
    }

    private fun parseVerifiedLanguages(o: JSONObject): List<String> {
        val arr = o.optJSONArray("verified_languages") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                e.optString("language", "")
                    .trim()
                    .lowercase()
                    .takeIf { it.isNotEmpty() }
                    ?.let { add(it) }
            }
        }
    }

    private fun parseVoiceJson(o: JSONObject): ElevenLabsVoice? {
        val id =
            o.optString("voice_id", "")
                .ifEmpty { o.optString("voiceId", "") }
        if (id.isEmpty()) return null
        val labels = linkedMapOf<String, String>()
        o.optJSONObject("labels")?.let { lo ->
            val keys = lo.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                labels[k] = lo.optString(k, "")
            }
        }
        val preview =
            o.optString("preview_url", "")
                .ifEmpty { o.optString("previewUrl", "") }
                .ifEmpty { null }
        val firstSampleId =
            o.optJSONArray("samples")?.optJSONObject(0)?.let { s ->
                s.optString("sample_id", "")
                    .ifEmpty { s.optString("sampleId", "") }
                    .ifEmpty { null }
            }
        return ElevenLabsVoice(
            voiceId = id,
            name = o.optString("name", id),
            category = o.optString("category", "").ifEmpty { null },
            description = o.optString("description", "").ifEmpty { null },
            labels = labels,
            verifiedLanguageTags = parseVerifiedLanguages(o),
            previewUrl = preview,
            sampleId = firstSampleId,
        )
    }

    /** Giọng thư viện `/v1/shared-voices` (shape khác v2). */
    private fun parseSharedVoiceJson(o: JSONObject): ElevenLabsVoice? {
        val id = o.optString("voice_id", "").ifEmpty { return null }
        val labels = linkedMapOf<String, String>()
        o.optString("language", "").trim().takeIf { it.isNotEmpty() }?.let { labels["language"] = it }
        o.optString("accent", "").trim().takeIf { it.isNotEmpty() }?.let { labels["accent"] = it }
        o.optString("gender", "").trim().takeIf { it.isNotEmpty() }?.let { labels["gender"] = it }
        o.optString("age", "").trim().takeIf { it.isNotEmpty() }?.let { labels["age"] = it }
        o.optString("use_case", "").trim().takeIf { it.isNotEmpty() }?.let { labels["use_case"] = it }
        val verified = mutableListOf<String>()
        o.optJSONArray("verified_languages")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("language", "")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
                    verified.add(it)
                }
            }
        }
        val preview =
            o.optString("preview_url", "")
                .ifEmpty { o.optString("previewUrl", "") }
                .ifEmpty { null }
        val firstSampleId =
            o.optJSONArray("samples")?.optJSONObject(0)?.let { s ->
                s.optString("sample_id", "")
                    .ifEmpty { s.optString("sampleId", "") }
                    .ifEmpty { null }
            }
        return ElevenLabsVoice(
            voiceId = id,
            name = o.optString("name", id),
            category = o.optString("category", "").ifEmpty { null },
            description = o.optString("description", "").ifEmpty { null },
            labels = labels,
            verifiedLanguageTags = verified,
            previewUrl = preview,
            sampleId = firstSampleId,
        )
    }

    /**
     * Thư viện giọng dùng chung (`/v1/shared-voices`), có tham số `language` (vd. `vi`).
     * Phân trang theo `page` + `has_more`.
     */
    private fun fetchSharedVoicesPaged(apiKey: String, languageIso: String?): List<ElevenLabsVoice> {
        val out = linkedMapOf<String, ElevenLabsVoice>()
        var page = 0
        while (page < 60) {
            val q = StringBuilder("page_size=100&page=$page")
            if (!languageIso.isNullOrBlank()) {
                q.append("&language=").append(URLEncoder.encode(languageIso, Charsets.UTF_8.name()))
            }
            val body = httpGetString("https://api.elevenlabs.io/v1/shared-voices?$q", apiKey.trim())
            val root = JSONObject(body)
            val arr = root.optJSONArray("voices") ?: break
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                parseSharedVoiceJson(o)?.let { out.putIfAbsent(it.voiceId, it) }
            }
            if (!root.optBoolean("has_more", false)) break
            page++
        }
        return out.values.toList()
    }

    /** GET `/v1/voices` (một trang, không phân trang). */
    private fun fetchVoicesV1Legacy(apiKey: String): List<ElevenLabsVoice> {
        val body = httpGetString("https://api.elevenlabs.io/v1/voices", apiKey.trim())
        val root = JSONObject(body)
        val arr = root.optJSONArray("voices") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                parseVoiceJson(o)?.let { add(it) }
            }
        }
    }

    /**
     * GET `/v2/voices` với `page_size=100` và lặp theo `next_page_token` (mặc định API chỉ ~10/trang).
     * Lỗi mạng/HTTP thì fallback `/v1/voices`.
     */
    suspend fun fetchVoices(apiKey: String): List<ElevenLabsVoice> =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isEmpty()) return@withContext emptyList()
            try {
                val merged = linkedMapOf<String, ElevenLabsVoice>()
                var nextToken: String? = null
                var guard = 0
                while (guard++ < 500) {
                    val qs = StringBuilder("page_size=100&sort=name&sort_direction=asc")
                    if (!nextToken.isNullOrBlank()) {
                        qs.append("&next_page_token=")
                        qs.append(URLEncoder.encode(nextToken, Charsets.UTF_8.name()))
                    }
                    val url = "https://api.elevenlabs.io/v2/voices?$qs"
                    val body = httpGetString(url, key)
                    val root = JSONObject(body)
                    val arr = root.optJSONArray("voices") ?: break
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        parseVoiceJson(o)?.let { merged[it.voiceId] = it }
                    }
                    val hasMore = root.optBoolean("has_more", false)
                    val token = root.optString("next_page_token", "").ifBlank { null }
                    if (arr.length() == 0) break
                    if (!hasMore || token == null) break
                    nextToken = token
                }
                if (merged.isNotEmpty()) {
                    for (v in fetchSharedVoicesPaged(key, "vi")) {
                        merged.putIfAbsent(v.voiceId, v)
                    }
                    for (v in fetchSharedVoicesPaged(key, "en")) {
                        merged.putIfAbsent(v.voiceId, v)
                    }
                    return@withContext merged.values.sortedBy { it.name.lowercase() }
                }
            } catch (_: Exception) {
                // fallback v1
            }
            val legacy = fetchVoicesV1Legacy(key).associateBy { it.voiceId }.toMutableMap()
            for (v in fetchSharedVoicesPaged(key, "vi")) legacy.putIfAbsent(v.voiceId, v)
            for (v in fetchSharedVoicesPaged(key, "en")) legacy.putIfAbsent(v.voiceId, v)
            legacy.values.sortedBy { it.name.lowercase() }
        }

    suspend fun fetchSubscriptionQuota(apiKey: String): ElevenLabsSubscriptionQuota? =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isEmpty()) return@withContext null
            val body = httpGetString("https://api.elevenlabs.io/v1/user/subscription", key)
            parseSubscriptionQuotaJson(JSONObject(body))
        }

    suspend fun fetchTextToSpeechModels(apiKey: String): List<ElevenLabsModel> =
        withContext(Dispatchers.IO) {
            val body = httpGetString("https://api.elevenlabs.io/v1/models", apiKey.trim())
            val arr: JSONArray =
                when {
                    body.trim().startsWith("[") -> JSONArray(body)
                    else -> {
                        val o = JSONObject(body)
                        o.optJSONArray("models")
                            ?: o.optJSONArray("data")
                            ?: return@withContext emptyList()
                    }
                }
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id =
                        o.optString("model_id", "")
                            .ifEmpty { o.optString("modelId", "") }
                    if (id.isEmpty()) continue
                    val tts = o.optBoolean("can_do_text_to_speech", true)
                    if (!tts) continue
                    add(
                        ElevenLabsModel(
                            modelId = id,
                            name = o.optString("name", id),
                            canDoTextToSpeech = tts,
                        ),
                    )
                }
            }.sortedBy { it.name.lowercase() }
        }
}

/**
 * Giải thích lỗi HTTP từ api.elevenlabs.io cho người dùng.
 * 402 = Payment Required: **không** đồng nghĩa luôn là «hết số còn/tổng» trên màn subscription.
 */
internal fun elevenLabsHttpUserMessage(code: Int, responseBody: String): String {
    val body = responseBody.trim().take(400)
    val extra = if (body.isNotEmpty()) " Chi tiết: ${body.take(280)}" else ""
    return when (code) {
        401 -> "ElevenLabs (401): API key không hợp lệ hoặc không có quyền.$extra"
        402 ->
            "ElevenLabs (402 Payment Required): từ chối vì điều kiện gói hoặc thanh toán — " +
                "ví dụ hóa đơn quá hạn, voice/model không thuộc gói, hoặc loại hạn mức khác với số còn/tổng lấy từ /v1/user/subscription trên app. " +
                "Vẫn còn số trên màn nhưng 402 là điều có thể xảy ra; đọc khối Chi tiết (JSON) và Billing trên elevenlabs.io.$extra"
        403 -> "ElevenLabs (403): truy cập bị từ chối.$extra"
        404 -> "ElevenLabs (404): không tìm thấy tài nguyên.$extra"
        429 -> "ElevenLabs (429): vượt giới hạn tần suất, thử lại sau.$extra"
        else -> "ElevenLabs HTTP $code.$extra"
    }
}
