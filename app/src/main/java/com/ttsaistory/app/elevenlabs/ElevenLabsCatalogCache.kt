package com.ttsaistory.app.elevenlabs

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class ElevenLabsCatalogSnapshot(
    val voices: List<ElevenLabsVoice>,
    val models: List<ElevenLabsModel>,
    val savedAtMillis: Long,
)

object ElevenLabsCatalogCache {
    private const val FILE_NAME = "elevenlabs_catalog_cache.json"

    private fun file(ctx: Context): File = File(ctx.cacheDir, FILE_NAME)

    private fun apiKeyFingerprint(apiKey: String): String =
        apiKey.trim().takeLast(16).ifEmpty { "empty" }

    fun read(context: Context, currentApiKey: String): ElevenLabsCatalogSnapshot? {
        val f = file(context)
        if (!f.isFile || f.length() == 0L) return null
        return try {
            val root = JSONObject(f.readText(Charsets.UTF_8))
            val fp = root.optString("apiKeyFp", "")
            if (fp.isNotEmpty() && fp != apiKeyFingerprint(currentApiKey)) {
                return null
            }
            val savedAt = root.optLong("savedAt", 0L).takeIf { it > 0 } ?: return null
            val voices = mutableListOf<ElevenLabsVoice>()
            root.optJSONArray("voices")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { o -> voiceFromJson(o) }?.let { voices.add(it) }
                }
            }
            val models = mutableListOf<ElevenLabsModel>()
            root.optJSONArray("models")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { o -> modelFromJson(o) }?.let { models.add(it) }
                }
            }
            ElevenLabsCatalogSnapshot(voices = voices, models = models, savedAtMillis = savedAt)
        } catch (_: Exception) {
            null
        }
    }

    fun write(context: Context, apiKey: String, voices: List<ElevenLabsVoice>, models: List<ElevenLabsModel>) {
        val root = JSONObject()
        root.put("savedAt", System.currentTimeMillis())
        root.put("apiKeyFp", apiKeyFingerprint(apiKey))
        root.put(
            "voices",
            JSONArray().apply {
                for (v in voices) {
                    put(voiceToJson(v))
                }
            },
        )
        root.put(
            "models",
            JSONArray().apply {
                for (m in models) {
                    put(modelToJson(m))
                }
            },
        )
        file(context).writeText(root.toString(), Charsets.UTF_8)
    }

    private fun voiceToJson(v: ElevenLabsVoice): JSONObject =
        JSONObject().apply {
            put("voiceId", v.voiceId)
            put("name", v.name)
            v.category?.let { put("category", it) }
            v.description?.let { put("description", it) }
            put(
                "labels",
                JSONObject().apply {
                    for ((k, v) in v.labels) {
                        put(k, v)
                    }
                },
            )
            put("verified", JSONArray().apply { v.verifiedLanguageTags.forEach { put(it) } })
            v.previewUrl?.let { put("previewUrl", it) }
            v.sampleId?.let { put("sampleId", it) }
        }

    private fun voiceFromJson(o: JSONObject): ElevenLabsVoice? {
        val id = o.optString("voiceId", "").ifEmpty { o.optString("voice_id", "") }
        if (id.isEmpty()) return null
        val labels = linkedMapOf<String, String>()
        o.optJSONObject("labels")?.let { lo ->
            val it = lo.keys()
            while (it.hasNext()) {
                val k = it.next()
                labels[k] = lo.optString(k, "")
            }
        }
        val verified = buildList {
            o.optJSONArray("verified")?.let { arr ->
                for (i in 0 until arr.length()) {
                    when (val x = arr.opt(i)) {
                        is String ->
                            x.trim().lowercase().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }
        }
        val previewUrl = o.optString("previewUrl", "").ifEmpty { null }
        val sampleId = o.optString("sampleId", "").ifEmpty { null }
        return ElevenLabsVoice(
            voiceId = id,
            name = o.optString("name", id),
            category = o.optString("category", "").ifEmpty { null },
            description = o.optString("description", "").ifEmpty { null },
            labels = labels,
            verifiedLanguageTags = verified,
            previewUrl = previewUrl,
            sampleId = sampleId,
        )
    }

    private fun modelToJson(m: ElevenLabsModel): JSONObject =
        JSONObject().apply {
            put("modelId", m.modelId)
            put("name", m.name)
            put("tts", m.canDoTextToSpeech)
        }

    private fun modelFromJson(o: JSONObject): ElevenLabsModel? {
        val id = o.optString("modelId", "").ifEmpty { return null }
        return ElevenLabsModel(
            modelId = id,
            name = o.optString("name", id),
            canDoTextToSpeech = o.optBoolean("tts", true),
        )
    }
}
