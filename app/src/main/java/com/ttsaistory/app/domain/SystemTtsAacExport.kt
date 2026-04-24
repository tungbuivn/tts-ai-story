package com.ttsaistory.app.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.ttsaistory.app.model.AppEditorConstants
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Xuất các đoạn [parts] thành một file .m4a (AAC): tổng hợp WAV từng đoạn TTS, hàng đợi tối đa
 * [AppEditorConstants.TTS_EXPORT_WAV_QUEUE_MAX]; chỉ khi consumer lấy file khỏi hàng đợi để nén AAC
 * thì producer mới có thể `send` đoạn WAV tiếp theo (và do đó mới tổng hợp tiếp trong vòng lặp).
 *
 * @param parts Các đoạn TTS (thường từ [ParagraphTextService.snapshotChapterParagraphsForExport] + file snapshot).
 * @param onProgress (wavDone, wavTotal, queued, aacDone, aacTotal) — gọi trên Main.
 * @param preferredTtsVoiceName Khi [mainTts] null (ví dụ export từ Foreground Service), thử gán giọng theo tên/locale.
 */
suspend fun exportFullTextToAacM4a(
    context: Context,
    mainTts: TextToSpeech?,
    parts: List<String>,
    outputFileName: String,
    speechRate: Float,
    pitch: Float,
    onProgress: suspend (wavDone: Int, wavTotal: Int, queued: Int, aacDone: Int, aacTotal: Int) -> Unit,
    preferredTtsVoiceName: String? = null,
    preferredTtsLocaleTag: String? = null,
): String {
    val segments = parts.map(::sanitizeParagraphText).filter { it.isNotEmpty() }
    if (segments.isEmpty()) throw IllegalStateException("Không có nội dung")

    val n = segments.size
    val engine = awaitTextToSpeechEngine(context)
    try {
        if (mainTts != null) {
            mainTts.voice?.let { engine.voice = it }
        } else {
            applyPreferredVoiceIfPossible(engine, preferredTtsVoiceName, preferredTtsLocaleTag)
        }
        engine.setSpeechRate(speechRate)
        engine.setPitch(pitch)

        val channel = Channel<File>(capacity = AppEditorConstants.TTS_EXPORT_WAV_QUEUE_MAX)
        val m4aTmp = File(context.cacheDir, "tts_export_aac_${System.currentTimeMillis()}.m4a")
        val produced = AtomicInteger(0)
        val encoded = AtomicInteger(0)

        suspend fun report() {
            val p = produced.get()
            val e = encoded.get()
            val queued = (p - e).coerceIn(0, AppEditorConstants.TTS_EXPORT_WAV_QUEUE_MAX)
            withContext(Dispatchers.Main) {
                onProgress(p, n, queued, e, n)
            }
        }

        withContext(Dispatchers.Main) {
            onProgress(0, n, 0, 0, n)
        }

        coroutineScope {
            val consumerDone = async(Dispatchers.IO) {
                var writer: AacM4aFileWriter? = null
                try {
                    writer = AacM4aFileWriter(m4aTmp)
                    val w = writer!!
                    repeat(n) {
                        val wav = channel.receive()
                        try {
                            w.appendFromWavFile(wav)
                        } finally {
                            runCatching { wav.delete() }
                        }
                        encoded.incrementAndGet()
                        report()
                    }
                    w.finish()
                } catch (t: Throwable) {
                    writer?.releaseQuietly()
                    throw t
                }
            }
            val producerDone = async(Dispatchers.Main) {
                try {
                    for (i in 0 until n) {
                        yield()
                        val tmp = File.createTempFile("wavseg_", ".wav", context.cacheDir)
                        synthesizeToFileSuspend(engine, segments[i], tmp, "aac_export_wav_$i")
                        channel.send(tmp)
                        produced.incrementAndGet()
                        report()
                    }
                } finally {
                    channel.close()
                }
            }
            producerDone.await()
            consumerDone.await()
        }

        val baseName =
            outputFileName.lowercase().let { l ->
                when {
                    l.endsWith(".m4a") -> outputFileName.dropLast(4)
                    l.endsWith(".wav") -> outputFileName.dropLast(4)
                    else -> outputFileName
                }
            }
        val displayName = "$baseName.m4a"
        val savedPath =
            withContext(Dispatchers.IO) {
                exportM4aToMusicTtsAiStory(context, displayName, m4aTmp)
            }
        runCatching { m4aTmp.delete() }
        withContext(Dispatchers.Main) {
            onProgress(n, n, 0, n, n)
        }
        return savedPath
    } finally {
        engine.stop()
        engine.shutdown()
    }
}

private fun applyPreferredVoiceIfPossible(
    engine: TextToSpeech,
    name: String?,
    localeTag: String?,
) {
    if (name.isNullOrBlank()) return
    val loc =
        localeTag?.takeIf { it.isNotBlank() }?.let {
            runCatching { Locale.forLanguageTag(it) }.getOrNull()
        }
    val voices: Set<Voice> = engine.voices ?: return
    val match =
        voices.firstOrNull { v ->
            v.name == name && (loc == null || v.locale == loc)
        } ?: voices.firstOrNull { it.name == name }
    match?.let { engine.voice = it }
}
