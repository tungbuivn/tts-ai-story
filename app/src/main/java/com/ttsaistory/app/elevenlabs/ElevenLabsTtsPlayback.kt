@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ttsaistory.app.elevenlabs

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ElevenLabsTtsPlayback {

    private fun ttsUrl(voiceId: String) =
        URL("https://api.elevenlabs.io/v1/text-to-speech/${voiceId.trim()}")

    private fun hostNeedsElevenLabsKey(urlStr: String): Boolean {
        val host =
            try {
                URL(urlStr).host.lowercase()
            } catch (_: Exception) {
                return false
            }
        return host.contains("elevenlabs.io")
    }

    @Throws(Exception::class)
    private fun downloadHttpGetToFile(
        urlStr: String,
        apiKey: String?,
        cacheDir: File,
        fileSuffix: String,
    ): File {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        if (!apiKey.isNullOrBlank()) {
            conn.setRequestProperty("xi-api-key", apiKey.trim())
        }
        conn.setRequestProperty("Accept", "audio/mpeg, audio/*, */*")
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            throw IllegalStateException(elevenLabsHttpUserMessage(code, err))
        }
        val out = File(cacheDir, "el_sample_${System.nanoTime()}$fileSuffix")
        FileOutputStream(out).use { fos -> conn.inputStream.use { input -> input.copyTo(fos) } }
        conn.disconnect()
        return out
    }

    @Throws(Exception::class)
    private fun downloadPreviewUrlToFile(
        cacheDir: File,
        apiKey: String,
        previewUrl: String,
    ): File {
        val key = if (hostNeedsElevenLabsKey(previewUrl)) apiKey.trim() else null
        return downloadHttpGetToFile(previewUrl.trim(), key, cacheDir, ".mp3")
    }

    @Throws(Exception::class)
    private fun downloadWorkspaceSampleToFile(
        cacheDir: File,
        apiKey: String,
        voiceId: String,
        sampleId: String,
    ): File {
        val vid = voiceId.trim()
        val sid = sampleId.trim()
        val urls =
            listOf(
                "https://api.elevenlabs.io/v1/voices/$vid/samples/$sid/audio",
                "https://api.elevenlabs.io/v1/voices/$vid/samples/$sid",
            )
        var last: Exception? = null
        for (u in urls) {
            try {
                return downloadHttpGetToFile(u, apiKey.trim(), cacheDir, ".mp3")
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Không tải được file mẫu giọng.")
    }

    /** Tải MP3 một đoạn văn; xóa file sau khi phát xong (ở caller). */
    @Throws(Exception::class)
    fun downloadParagraphMp3(
        cacheDir: File,
        apiKey: String,
        voiceId: String,
        modelId: String,
        languageCode: String?,
        text: String,
    ): File {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty())
        val conn = ttsUrl(voiceId).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.setRequestProperty("xi-api-key", apiKey)
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "audio/mpeg")
        val bodyJson =
            JSONObject()
                .put("text", trimmed)
                .put("model_id", modelId)
        if (!languageCode.isNullOrBlank()) {
            bodyJson.put("language_code", languageCode.trim())
        }
        val body = bodyJson.toString()
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            throw IllegalStateException(elevenLabsHttpUserMessage(code, err))
        }
        val out = File(cacheDir, "el_${System.nanoTime()}.mp3")
        FileOutputStream(out).use { fos ->
            conn.inputStream.use { input -> input.copyTo(fos) }
        }
        conn.disconnect()
        return out
    }

    private suspend fun playMp3File(appContext: Context, path: String) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val mp =
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        @Suppress("DEPRECATION")
                        setWakeMode(appContext.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                        setDataSource(path)
                        setOnCompletionListener {
                            runCatching { release() }
                            if (cont.isActive) {
                                cont.resume(Unit) {}
                            }
                        }
                        setOnErrorListener { _, what, extra ->
                            runCatching { release() }
                            if (cont.isActive) {
                                cont.resumeWith(
                                    Result.failure(
                                        IllegalStateException("MediaPlayer lỗi $what/$extra"),
                                    ),
                                )
                            }
                            true
                        }
                        prepare()
                        start()
                    }
                cont.invokeOnCancellation {
                    runCatching {
                        mp.stop()
                        mp.release()
                    }
                }
            }
        }

    /**
     * Phát tuần tự các đoạn từ [startIndex] (index trong [paragraphs], đã khớp với bookmark).
     * [onIndex] gọi trên main trước khi bắt đầu phát từng đoạn; [onFinished] khi kết thúc hoặc hết đoạn.
     */
    suspend fun playSequence(
        context: Context,
        paragraphs: List<String>,
        startIndex: Int,
        apiKey: String,
        voiceId: String,
        modelId: String,
        languageCode: String?,
        onIndex: (Int) -> Unit,
        onFinished: () -> Unit,
    ) {
        val sanitized = paragraphs.map { it.trim() }
        var from = startIndex.coerceIn(0, sanitized.lastIndex.coerceAtLeast(0))
        while (from <= sanitized.lastIndex && sanitized[from].isEmpty()) {
            from++
        }
        if (from > sanitized.lastIndex) {
            withContext(Dispatchers.Main) { onFinished() }
            return
        }
        val jobs =
            sanitized.indices
                .filter { it >= from && sanitized[it].isNotEmpty() }
                .map { it to sanitized[it] }
        if (jobs.isEmpty()) {
            withContext(Dispatchers.Main) { onFinished() }
            return
        }
        val cacheDir = File(context.cacheDir, "elevenlabs_tts").apply { mkdirs() }
        try {
            for ((idx, para) in jobs) {
                coroutineContext.ensureActive()
                val file =
                    withContext(Dispatchers.IO) {
                        downloadParagraphMp3(
                            cacheDir,
                            apiKey,
                            voiceId,
                            modelId,
                            languageCode,
                            para,
                        )
                    }
                try {
                    withContext(Dispatchers.Main) { onIndex(idx) }
                    playMp3File(context, file.absolutePath)
                } finally {
                    file.delete()
                }
            }
        } finally {
            withContext(Dispatchers.Main) { onFinished() }
        }
    }

    /**
     * Phát mẫu từ API: [ElevenLabsVoice.previewUrl] hoặc [ElevenLabsVoice.sampleId] (GET mẫu workspace).
     * Không có hai thứ trên thì ném lỗi — không dùng TTS thay thế.
     */
    suspend fun playVoiceSample(
        context: Context,
        apiKey: String,
        voice: ElevenLabsVoice,
    ) {
        val key = apiKey.trim()
        require(key.isNotEmpty())
        require(voice.hasPlayableSample()) { "Giọng không có preview hoặc sample từ API." }
        val cacheDir = File(context.cacheDir, "elevenlabs_tts").apply { mkdirs() }
        val file =
            withContext(Dispatchers.IO) {
                when {
                    !voice.previewUrl.isNullOrBlank() ->
                        downloadPreviewUrlToFile(cacheDir, key, voice.previewUrl!!)
                    else ->
                        downloadWorkspaceSampleToFile(cacheDir, key, voice.voiceId, voice.sampleId!!)
                }
            }
        try {
            playMp3File(context, file.absolutePath)
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { file.delete() }
            }
        }
    }
}
