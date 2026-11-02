package com.ttsaistory.app.voxstudio

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class VoxVoice(
    val id: String,
    val name: String,
    val lang: String?,
    val gender: String?,
    val note: String?,
    val kind: String?,
) {
    val isClone: Boolean get() = kind == "clone"
}

data class VoxEngine(
    val id: String,
    val label: String,
    val available: Boolean,
    val reason: String?,
    val requiresNetwork: Boolean,
    val outputFormat: String?,
    val canClone: Boolean,
    val voices: List<VoxVoice>,
)

data class VoxCatalog(
    val engines: List<VoxEngine>,
    val defaultEngine: String,
    val defaultVoice: String,
)

data class VoxJob(
    val id: String,
    val status: String,
    val progress: Double,
    val stage: String?,
    val error: String?,
    val format: String?,
    val duration: Double?,
    val size: Long?,
    val voice: String?,
)

/**
 * REST client cho VoxStudio (`/api/engines`, `/api/jobs`, `/api/voices`).
 * Chỉ dùng API HTTP công khai của server — không phụ thuộc code Python bên đó.
 */
object VoxStudioApi {
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val UPLOAD_TIMEOUT_MS = 120_000
    private const val AUDIO_TIMEOUT_MS = 180_000

    /** Chuẩn hoá URL người dùng nhập: thêm scheme, bỏ `/` cuối. IP nội bộ dùng HTTP. */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return VoxStudioConfig.DEFAULT_BASE_URL
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed.trimEnd('/')
        }
        val hostPort = trimmed.substringBefore('/')
        val host = hostPort.substringBefore(':').lowercase()
        val isIp = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$").matches(hostPort)
        val scheme = if (isIp || host == "localhost") "http://" else "https://"
        return (scheme + trimmed).trimEnd('/')
    }

    fun fetchCatalog(baseUrl: String, token: String): VoxCatalog {
        val root = getJson("${normalizeBaseUrl(baseUrl)}/api/engines", token)
        val arr = root.optJSONArray("engines") ?: JSONArray()
        val engines = ArrayList<VoxEngine>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            engines += parseEngine(o)
        }
        return VoxCatalog(
            engines = engines,
            defaultEngine = optStringOrNull(root, "default_engine").orEmpty(),
            defaultVoice = optStringOrNull(root, "default_voice").orEmpty(),
        )
    }

    fun createJob(
        baseUrl: String,
        token: String,
        text: String,
        engine: String,
        voice: String,
        ratePct: Int,
        volumePct: Int,
    ): VoxJob {
        val body =
            JSONObject()
                .put("text", text)
                .put("engine", engine)
                .put("rate_pct", ratePct)
                .put("volume_pct", volumePct)
        if (voice.isNotBlank()) body.put("voice", voice)
        val title = text.lineSequence().firstOrNull()?.trim()?.take(80)
        if (!title.isNullOrBlank()) body.put("title", title)
        return parseJob(postJson("${normalizeBaseUrl(baseUrl)}/api/jobs", token, body))
    }

    fun fetchJob(baseUrl: String, token: String, jobId: String): VoxJob =
        parseJob(getJson("${normalizeBaseUrl(baseUrl)}/api/jobs/$jobId", token))

    fun downloadAudio(
        baseUrl: String,
        token: String,
        jobId: String,
        format: String?,
        destDir: File,
    ): File {
        val ext =
            format
                ?.trim()
                ?.lowercase()
                ?.takeIf { it == "mp3" || it == "wav" }
                ?: "mp3"
        destDir.mkdirs()
        val out = File(destDir, "vox_${jobId}_${System.nanoTime()}.$ext")
        val conn = openConnection("${normalizeBaseUrl(baseUrl)}/api/jobs/$jobId/audio", token, "GET")
        conn.readTimeout = AUDIO_TIMEOUT_MS
        conn.setRequestProperty("Accept", "audio/mpeg, audio/wav, audio/*, */*")
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IllegalStateException(voxStudioHttpUserMessage(code, err))
            }
            conn.inputStream.use { input ->
                out.outputStream().use { fos -> input.copyTo(fos) }
            }
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        if (!out.isFile || out.length() <= 0L) {
            out.delete()
            throw IllegalStateException("VoxStudio trả về file âm thanh rỗng.")
        }
        return out
    }

    /** Upload mẫu giọng clone (multipart: name, file, ref_text). */
    fun uploadVoice(
        baseUrl: String,
        token: String,
        name: String,
        refText: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): VoxVoice {
        val boundary = "----VoxStudio${UUID.randomUUID().toString().replace("-", "")}"
        val safeFileName = sanitizeFileName(fileName)
        val conn = openConnection("${normalizeBaseUrl(baseUrl)}/api/voices", token, "POST")
        conn.doOutput = true
        conn.readTimeout = UPLOAD_TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        try {
            conn.outputStream.buffered().use { out ->
                fun writeText(value: String) = out.write(value.toByteArray(Charsets.UTF_8))

                fun writeField(fieldName: String, value: String) {
                    writeText("--$boundary\r\n")
                    writeText("Content-Disposition: form-data; name=\"$fieldName\"\r\n\r\n")
                    writeText(value)
                    writeText("\r\n")
                }

                writeField("name", name.trim())
                writeField("ref_text", refText.trim())
                writeText("--$boundary\r\n")
                writeText(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"\r\n",
                )
                writeText(
                    "Content-Type: ${mimeType.trim().ifBlank { "application/octet-stream" }}\r\n\r\n",
                )
                out.write(bytes)
                writeText("\r\n--$boundary--\r\n")
            }
            val o = JSONObject(readResponse(conn))
            return parseVoice(o)
                ?: throw IllegalStateException("Phản hồi tạo giọng clone không hợp lệ.")
        } finally {
            conn.disconnect()
        }
    }

    fun deleteVoice(baseUrl: String, token: String, voiceId: String) {
        val conn =
            openConnection(
                "${normalizeBaseUrl(baseUrl)}/api/voices/$voiceId",
                token,
                "DELETE",
            )
        try {
            readResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------------- internals

    private fun openConnection(url: String, token: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            setRequestProperty("User-Agent", "TTS-AI-Story-Android")
        }

    private fun getJson(url: String, token: String): JSONObject {
        val conn = openConnection(url, token, "GET")
        conn.setRequestProperty("Accept", "application/json")
        try {
            return JSONObject(readResponse(conn))
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(url: String, token: String, body: JSONObject): JSONObject {
        val conn = openConnection(url, token, "POST")
        conn.doOutput = true
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            val payload = body.toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }
            return JSONObject(readResponse(conn))
        } finally {
            conn.disconnect()
        }
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val text =
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException(voxStudioHttpUserMessage(code, text))
        }
        return text
    }

    private fun parseEngine(o: JSONObject): VoxEngine {
        val voices = ArrayList<VoxVoice>()
        o.optJSONArray("voices")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { v -> parseVoice(v)?.let { voices += it } }
            }
        }
        return VoxEngine(
            id = o.optString("id").trim(),
            label = o.optString("label").trim().ifEmpty { o.optString("id").trim() },
            available = o.optBoolean("available", false),
            reason = optStringOrNull(o, "reason"),
            requiresNetwork = o.optBoolean("requires_network", false),
            outputFormat = optStringOrNull(o, "output_format"),
            canClone = o.optBoolean("can_clone", false),
            voices = voices,
        )
    }

    private fun parseVoice(o: JSONObject): VoxVoice? {
        val name = o.optString("name").trim()
        val id = o.optString("id").trim().ifEmpty { name }
        if (id.isEmpty()) return null
        return VoxVoice(
            id = id,
            name = name.ifEmpty { id },
            lang = optStringOrNull(o, "lang"),
            gender = optStringOrNull(o, "gender"),
            note = optStringOrNull(o, "note"),
            kind = optStringOrNull(o, "kind"),
        )
    }

    private fun parseJob(o: JSONObject): VoxJob =
        VoxJob(
            id = o.optString("id").trim(),
            status = o.optString("status").trim(),
            progress = o.optDouble("progress", 0.0),
            stage = optStringOrNull(o, "stage"),
            error = optStringOrNull(o, "error"),
            format = optStringOrNull(o, "format"),
            duration = if (o.isNull("duration")) null else o.optDouble("duration"),
            size = if (o.isNull("size")) null else o.optLong("size"),
            voice = optStringOrNull(o, "voice"),
        )

    private fun optStringOrNull(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) {
            null
        } else {
            o.optString(key, "").trim().takeIf { it.isNotEmpty() }
        }

    private fun sanitizeFileName(raw: String): String {
        var name =
            raw
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[\\r\\n\"\\\\]"), "_")
                .trim()
                .ifBlank { "sample.m4a" }
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in VoxStudioConfig.ALLOWED_AUDIO_EXTENSIONS) {
            name = "$name.mp3"
        }
        return name
    }
}

/** Tách thông báo lỗi thân thiện từ FastAPI (`detail`) hoặc body thô. */
internal fun voxStudioHttpUserMessage(code: Int, responseBody: String): String {
    val detail =
        runCatching { JSONObject(responseBody).optString("detail", "").trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    val raw = detail ?: responseBody.trim().replace(Regex("\\s+"), " ").take(280)
    val extra = if (raw.isNotEmpty()) " $raw" else ""
    return when (code) {
        400 -> "VoxStudio (400): yêu cầu không hợp lệ.$extra"
        401 -> "VoxStudio (401): API token không đúng hoặc còn thiếu."
        404 -> "VoxStudio (404): không tìm thấy job/giọng hoặc audio chưa sẵn sàng.$extra"
        413 -> "VoxStudio (413): file mẫu vượt quá 15 MB."
        422 -> "VoxStudio (422): dữ liệu gửi lên không hợp lệ.$extra"
        429 -> "VoxStudio (429): quá nhiều yêu cầu, thử lại sau.$extra"
        500, 502, 503, 504 -> "VoxStudio ($code): server đang lỗi hoặc bận, thử lại sau.$extra"
        else -> "VoxStudio HTTP $code.$extra"
    }
}
