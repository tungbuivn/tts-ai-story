package com.ttsaistory.app.export

import com.ttsaistory.app.model.AppEditorConstants
import com.ttsaistory.app.ui.reader.DialogTtsExportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TtsExportUiCoordinator {
    private val _uiState = MutableStateFlow<DialogTtsExportState?>(null)
    val uiState: StateFlow<DialogTtsExportState?> = _uiState.asStateFlow()

    fun setPreparing() {
        _uiState.value =
            DialogTtsExportState(
                wavProgress = 0f,
                wavDetail = "Chuẩn bị xuất…",
                aacProgress = 0f,
                aacDetail = "Nén AAC (.m4a): 0/?",
            )
    }

    fun updateFromProgress(
        wavDone: Int,
        wavTotal: Int,
        queued: Int,
        aacDone: Int,
        aacTotal: Int,
    ) {
        val wTot = wavTotal.coerceAtLeast(1)
        val aTot = aacTotal.coerceAtLeast(1)
        _uiState.value =
            DialogTtsExportState(
                wavProgress = wavDone / wTot.toFloat(),
                wavDetail =
                    "Tổng hợp WAV: $wavDone/$wavTotal · chờ: $queued/${AppEditorConstants.TTS_EXPORT_WAV_QUEUE_MAX}",
                aacProgress = aacDone / aTot.toFloat(),
                aacDetail = "Nén AAC (.m4a): $aacDone/$aacTotal",
            )
    }

    fun clear() {
        _uiState.value = null
    }
}
