package com.ttsaistory.app.speech

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ttsaistory.app.domain.speakParagraphsSequential

class SystemParagraphSpeechEngine(
    private val tts: () -> TextToSpeech?,
    private val requestAudioFocus: () -> Unit,
    private val abandonAudioFocus: () -> Unit,
) : ParagraphSpeechEngine {

    override fun stopPlayback() {
        tts()?.stop()
        abandonAudioFocus()
    }

    override fun startParagraphSequence(
        paragraphs: List<String>,
        startIndex: Int,
        callbacks: ParagraphSpeechSequenceCallbacks,
    ): Boolean {
        requestAudioFocus()
        val (ok, n) = speakParagraphsSequential(tts(), paragraphs, startIndex)
        if (!ok || n == 0) {
            abandonAudioFocus()
            callbacks.onErrorToast("Không phát được TTS.")
            return false
        }
        callbacks.onSystemQueuedUtteranceCount(n)
        callbacks.onElevenLabsJob(null)
        return true
    }

    companion object {
        fun utteranceProgressListener(
            progressHandler: Handler,
            sink: SystemTtsUtteranceProgressSink,
        ): UtteranceProgressListener {
            fun dispatch(action: () -> Unit) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    action()
                } else {
                    progressHandler.post(action)
                }
            }
            return object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    dispatch { sink.onUtteranceStart(utteranceId) }
                }

                override fun onDone(utteranceId: String?) {
                    dispatch { sink.onUtteranceDone(utteranceId) }
                }

                override fun onError(utteranceId: String?) {
                    dispatch { sink.onUtteranceError(utteranceId) }
                }
            }
        }
    }
}
