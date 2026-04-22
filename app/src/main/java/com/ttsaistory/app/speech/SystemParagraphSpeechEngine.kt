package com.ttsaistory.app.speech

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ttsaistory.app.domain.buildParagraphSpeakJobs
import com.ttsaistory.app.domain.speakParagraphUtterance
import com.ttsaistory.app.model.AppEditorConstants

class SystemParagraphSpeechEngine(
    private val tts: () -> TextToSpeech?,
    private val requestAudioFocus: () -> Unit,
    private val abandonAudioFocus: () -> Unit,
) : ParagraphSpeechEngine {

    @Volatile
    private var sessionJobs: List<Pair<Int, String>>? = null

    /** Chỉ số kế trong [sessionJobs] cần gửi [TextToSpeech.speak] (QUEUE_ADD). */
    @Volatile
    private var sessionNextEnqueueIndex: Int = 0

    /** Số utterance đoạn đã báo xong (onDone/onError) trong phiên hiện tại. */
    @Volatile
    private var sessionUtterancesCompleted: Int = 0

    /**
     * Số utterance còn trong pipeline TTS (đã [speak] nhưng chưa hoàn tất callback),
     * gần đúng độ sâu hàng đợi rolling chunk.
     */
    fun queuedParagraphUtterancePipelineDepth(): Int {
        val jobs = sessionJobs ?: return 0
        return (sessionNextEnqueueIndex - sessionUtterancesCompleted).coerceAtLeast(0)
    }

    override fun stopPlayback() {
        sessionJobs = null
        sessionNextEnqueueIndex = 0
        sessionUtterancesCompleted = 0
        tts()?.stop()
        abandonAudioFocus()
    }

    override fun onSystemTtsParagraphUtteranceFinished(utteranceId: String?) {
        val jobs = sessionJobs ?: return
        sessionUtterancesCompleted = (sessionUtterancesCompleted + 1).coerceAtMost(jobs.size)
        val engine = tts() ?: return
        if (sessionNextEnqueueIndex >= jobs.size) return
        val (origIdx, paragraph) = jobs[sessionNextEnqueueIndex]
        if (!speakParagraphUtterance(engine, origIdx, paragraph, TextToSpeech.QUEUE_ADD)) {
            sessionJobs = null
            sessionNextEnqueueIndex = 0
            sessionUtterancesCompleted = 0
            return
        }
        sessionNextEnqueueIndex++
    }

    override fun startParagraphSequence(
        paragraphs: List<String>,
        startIndex: Int,
        callbacks: ParagraphSpeechSequenceCallbacks,
    ): Boolean {
        val jobs = buildParagraphSpeakJobs(paragraphs, startIndex)
        if (jobs.isEmpty()) {
            abandonAudioFocus()
            callbacks.onErrorToast("Không phát được TTS.")
            return false
        }
        val engine = tts()
        if (engine == null) {
            abandonAudioFocus()
            callbacks.onErrorToast("Không phát được TTS.")
            return false
        }
        requestAudioFocus()
        sessionJobs = jobs
        sessionUtterancesCompleted = 0
        val cap = AppEditorConstants.SYSTEM_TTS_PLAY_MAX_ENQUEUED_UTTERANCES
        val initialBatch = minOf(cap, jobs.size)
        sessionNextEnqueueIndex = initialBatch
        for (k in 0 until initialBatch) {
            val (origIdx, paragraph) = jobs[k]
            val queueMode =
                if (k == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (!speakParagraphUtterance(engine, origIdx, paragraph, queueMode)) {
                sessionJobs = null
                sessionNextEnqueueIndex = 0
                sessionUtterancesCompleted = 0
                abandonAudioFocus()
                callbacks.onErrorToast("Không phát được TTS.")
                return false
            }
        }
        callbacks.onSystemQueuedUtteranceCount(jobs.size)
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

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    dispatch { sink.onUtteranceError(utteranceId) }
                }
            }
        }
    }
}
