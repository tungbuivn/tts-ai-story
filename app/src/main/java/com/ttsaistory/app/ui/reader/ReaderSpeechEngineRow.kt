package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.model.TextTabSpeechEngine

/** Hàng chọn engine đọc (TTS hệ thống / ElevenLabs) trên tab nhập văn. */
@Composable
internal fun ReaderSpeechEngineRow(
    speechEngine: TextTabSpeechEngine,
    onSpeechEngineChange: (TextTabSpeechEngine) -> Unit,
    engineControlsEnabled: Boolean,
    libraryStoryPickerEnabled: Boolean,
    onOpenLibraryStoryPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Đọc:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChip(
                selected = speechEngine == TextTabSpeechEngine.System,
                onClick = { onSpeechEngineChange(TextTabSpeechEngine.System) },
                enabled = engineControlsEnabled,
                label = { Text("TTS hệ thống") },
            )
            FilterChip(
                selected = speechEngine == TextTabSpeechEngine.ElevenLabs,
                onClick = { onSpeechEngineChange(TextTabSpeechEngine.ElevenLabs) },
                enabled = engineControlsEnabled,
                label = { Text("ElevenLabs") },
            )
        }
        IconButton(
            onClick = onOpenLibraryStoryPicker,
            enabled = engineControlsEnabled && libraryStoryPickerEnabled,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "Danh sách truyện trong thể loại",
            )
        }
    }
}
