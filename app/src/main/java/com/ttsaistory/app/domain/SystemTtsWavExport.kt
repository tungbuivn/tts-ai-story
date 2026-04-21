package com.ttsaistory.app.domain

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

suspend fun awaitTextToSpeechEngine(context: Context): TextToSpeech =
    suspendCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val slot = arrayOfNulls<TextToSpeech>(1)
        slot[0] =
            TextToSpeech(context.applicationContext) { status ->
                handler.post {
                    val e = slot[0]
                    if (e == null || status != TextToSpeech.SUCCESS) {
                        cont.resumeWithException(IllegalStateException("Không khởi tạo được TTS xuất file"))
                    } else {
                        cont.resume(e)
                    }
                }
            }
    }

suspend fun synthesizeToFileSuspend(
    tts: TextToSpeech,
    text: CharSequence,
    file: File,
    utteranceId: String,
) = suspendCancellableCoroutine { cont ->
    val listener =
        object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                tts.setOnUtteranceProgressListener(null)
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onError(utteranceId: String?) {
                tts.setOnUtteranceProgressListener(null)
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("Lỗi synthesize: $utteranceId"),
                    )
                }
            }
        }
    cont.invokeOnCancellation {
        runCatching {
            tts.stop()
            tts.setOnUtteranceProgressListener(null)
        }
    }
    tts.setOnUtteranceProgressListener(listener)
    val r = tts.synthesizeToFile(text, null, file, utteranceId)
    if (r == TextToSpeech.ERROR) {
        tts.setOnUtteranceProgressListener(null)
        cont.resumeWithException(IllegalStateException("synthesizeToFile trả về ERROR"))
    }
}

/** Xuất toàn bộ văn bản từ đầu đến hết thành một file WAV (bỏ qua vị trí đang đọc). */
suspend fun exportFullTextToWav(
    context: Context,
    mainTts: TextToSpeech?,
    fullText: String,
    outputFileName: String,
    speechRate: Float,
    pitch: Float,
    onProgress: (Float) -> Unit,
): String {
    val parts =
        splitIntoParagraphs(fullText).map(::sanitizeParagraphText).filter { it.isNotEmpty() }
    if (parts.isEmpty()) throw IllegalStateException("Không có nội dung")

    onProgress(0f)
    val engine = awaitTextToSpeechEngine(context)
    try {
        mainTts?.voice?.let { engine.voice = it }
        engine.setSpeechRate(speechRate)
        engine.setPitch(pitch)
        val temps = mutableListOf<File>()
        try {
            val n = parts.size
            for (i in 0 until n) {
                yield()
                val tmp = File.createTempFile("wavseg_", ".wav", context.cacheDir)
                temps.add(tmp)
                synthesizeToFileSuspend(engine, parts[i], tmp, "wav_export_$i")
                onProgress((i + 1f) / (n + 1f))
            }
            onProgress(0.92f)
            val savedPath =
                withContext(Dispatchers.IO) {
                    exportWavToMusicTtsAiStoryFromChunkFiles(
                        context,
                        outputFileName,
                        temps,
                    )
                }
            onProgress(1f)
            return savedPath
        } finally {
            temps.forEach { runCatching { it.delete() } }
        }
    } finally {
        engine.stop()
        engine.shutdown()
    }
}
