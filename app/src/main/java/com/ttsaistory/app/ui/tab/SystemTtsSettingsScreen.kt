package com.ttsaistory.app.ui.tab

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemTtsSettingsScreen(
    modifier: Modifier = Modifier,
    ttsReady: Boolean,
    tts: TextToSpeech?,
    voices: List<Voice>,
    selectedVoice: Voice?,
    onSelectedVoiceChange: (Voice) -> Unit,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechRateChangeFinished: () -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onPitchChangeFinished: () -> Unit,
    onResetToSystemDefaults: () -> Unit,
    sampleText: String,
    onSampleTextChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Cấu hình TTS hệ thống") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Đóng",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                if (!ttsReady || tts == null) {
                    Text(
                        text = "Đang tải động cơ TTS…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
            item {
                Text(
                    text =
                        "Tốc độ đọc: ${"%.2f".format(speechRate)} × (1,0 = mặc định Android)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Slider(
                    value = speechRate,
                    onValueChange = onSpeechRateChange,
                    onValueChangeFinished = onSpeechRateChangeFinished,
                    valueRange = 0.25f..2.5f,
                    enabled = ttsReady && tts != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Cao độ (pitch): ${"%.2f".format(pitch)} × (1,0 = mặc định)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Slider(
                    value = pitch,
                    onValueChange = onPitchChange,
                    onValueChangeFinished = onPitchChangeFinished,
                    valueRange = 0.5f..2.0f,
                    enabled = ttsReady && tts != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Văn bản thử",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                OutlinedTextField(
                    value = sampleText,
                    onValueChange = onSampleTextChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text("Nhập đoạn văn để nghe thử…") },
                )
            }
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                ) {
                    Button(
                        onClick = {
                            val engine = tts ?: return@Button
                            val toSpeak = sampleText.trim()
                            if (toSpeak.isEmpty()) {
                                Toast.makeText(
                                    ctx,
                                    "Nhập nội dung thử trước khi phát.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@Button
                            }
                            engine.stop()
                            engine.speak(
                                toSpeak,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "system_tts_preview",
                            )
                        },
                        enabled = ttsReady && tts != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text("Phát mẫu")
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        tts?.stop()
                        onResetToSystemDefaults()
                    },
                    enabled = ttsReady && tts != null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                ) {
                    Text("Khôi phục mặc định hệ thống")
                }
            }
            item {
                Text(
                    text = "Giọng nói (tiếng Việt)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
            }
            if (voices.isEmpty()) {
                item {
                    Text(
                        text = "Không có giọng tiếng Việt từ engine TTS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(voices, key = { _, v -> v.name }) { index, voice ->
                    val selected = voice.name == selectedVoice?.name
                    val displayLabel = "Voice ${index + 1}"
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    },
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clickable(enabled = ttsReady && tts != null) {
                                    onSelectedVoiceChange(voice)
                                },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    if (ttsReady && tts != null) {
                                        onSelectedVoiceChange(voice)
                                    }
                                },
                                enabled = ttsReady && tts != null,
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(
                                    text = displayLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = voice.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text =
                                        voice.locale?.toLanguageTag()?.takeIf { it.isNotEmpty() }
                                            ?: "—",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
