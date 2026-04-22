package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ttsaistory.app.ui.core.CancelButton

@Composable
internal fun DialogReaderExportM4a(
    exportUi: DialogTtsExportState,
    onCancelExport: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .width(300.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Đang xuất AAC (.m4a)",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = exportUi.wavDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { exportUi.wavProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                )
                Text(
                    text = exportUi.aacDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { exportUi.aacProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                )
                CancelButton(
                    onClick = onCancelExport,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
