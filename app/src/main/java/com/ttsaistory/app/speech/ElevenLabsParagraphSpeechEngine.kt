package com.ttsaistory.app.speech

import android.content.Context
import android.content.SharedPreferences
import com.ttsaistory.app.elevenlabs.ElevenLabsConfig
import com.ttsaistory.app.elevenlabs.ElevenLabsPrefKeys
import com.ttsaistory.app.elevenlabs.ElevenLabsTtsPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class ElevenLabsParagraphSpeechEngine(
    private val appContext: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
) : ParagraphSpeechEngine {

    private var activeJob: Job? = null

    override fun stopPlayback() {
        activeJob?.cancel()
        activeJob = null
    }

    override fun startParagraphSequence(
        paragraphs: List<String>,
        startIndex: Int,
        callbacks: ParagraphSpeechSequenceCallbacks,
    ): Boolean {
        val key = ElevenLabsPrefKeys.resolveApiKey(prefs)
        if (key.isEmpty()) {
            callbacks.onErrorToast("Chưa cấu hình API ElevenLabs.")
            return false
        }
        stopPlayback()
        val elJob =
            scope.launch {
                val thisPlayJob = currentCoroutineContext().job
                var playedAnyParagraph = false
                try {
                    val languageCode: String? =
                        if (!prefs.contains(ElevenLabsPrefKeys.LANGUAGE_CODE)) {
                            ElevenLabsConfig.DEFAULT_LANGUAGE_CODE
                        } else {
                            prefs.getString(ElevenLabsPrefKeys.LANGUAGE_CODE, "")?.trim()
                                ?.takeIf { it.isNotEmpty() }
                        }
                    val voiceId = ElevenLabsPrefKeys.resolveVoiceIdForLanguage(prefs, languageCode)
                    val modelId =
                        prefs.getString(ElevenLabsPrefKeys.MODEL_ID, null)?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: ElevenLabsConfig.MODEL_ID
                    ElevenLabsTtsPlayback.playSequence(
                        context = appContext,
                        paragraphs = paragraphs,
                        startIndex = startIndex,
                        apiKey = key,
                        voiceId = voiceId,
                        modelId = modelId,
                        languageCode = languageCode,
                        onIndex = { idx ->
                            playedAnyParagraph = true
                            callbacks.onSpeakingParagraphIndex(idx)
                        },
                        onFinished = {
                            callbacks.onSpeakingParagraphIndex(-1)
                        },
                    )
                    if (playedAnyParagraph) {
                        callbacks.onFullSequenceFinishedForLibraryAutoAdvance()
                    }
                } catch (e: CancellationException) {
                    callbacks.onSpeakingParagraphIndex(-1)
                    throw e
                } catch (e: Exception) {
                    callbacks.onSpeakingParagraphIndex(-1)
                    callbacks.onErrorToast(
                        "ElevenLabs: ${e.message ?: e.javaClass.simpleName}",
                    )
                } finally {
                    if (activeJob === thisPlayJob) {
                        activeJob = null
                    }
                    callbacks.onElevenLabsJob(null)
                }
            }
        activeJob = elJob
        callbacks.onElevenLabsJob(elJob)
        return true
    }
}
