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
import kotlinx.coroutines.suspendCancellableCoroutine

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
