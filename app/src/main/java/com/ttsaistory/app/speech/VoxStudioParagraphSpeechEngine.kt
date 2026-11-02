package com.ttsaistory.app.speech

import android.content.Context
import android.content.SharedPreferences
import com.ttsaistory.app.voxstudio.VoxStudioPrefKeys
import com.ttsaistory.app.voxstudio.VoxStudioTtsPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class VoxStudioParagraphSpeechEngine(
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
        val config = VoxStudioPrefKeys.resolveConfig(prefs)
        if (config.token.isEmpty()) {
            callbacks.onErrorToast("Chưa cấu hình API token VoxStudio.")
            return false
        }
        if (config.baseUrl.isEmpty()) {
            callbacks.onErrorToast("Chưa cấu hình địa chỉ server VoxStudio.")
            return false
        }
        stopPlayback()
        val voxJob =
            scope.launch {
                val thisPlayJob = currentCoroutineContext().job
                var playedAnyParagraph = false
                try {
                    VoxStudioTtsPlayback.playSequence(
                        context = appContext,
                        paragraphs = paragraphs,
                        startIndex = startIndex,
                        config = config,
                        onIndex = { idx ->
                            playedAnyParagraph = true
                            callbacks.onSpeakingParagraphIndex(idx)
                        },
                        onFinished = {
                            callbacks.onSpeakingParagraphIndex(-1)
                        },
                        onPreparing = { preparing ->
                            callbacks.onRemoteTtsPreparing(preparing)
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
                        "VoxStudio: ${e.message ?: e.javaClass.simpleName}",
                    )
                } finally {
                    if (activeJob === thisPlayJob) {
                        activeJob = null
                    }
                    callbacks.onRemoteTtsPreparing(false)
                    callbacks.onRemoteTtsJob(null)
                }
            }
        activeJob = voxJob
        callbacks.onRemoteTtsJob(voxJob)
        return true
    }
}
