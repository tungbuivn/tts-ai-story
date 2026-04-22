package com.ttsaistory.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.BuildConfig

@Composable
fun DialogAppAbout(onDismissRequest: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Giới thiệu") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "TTS AI Story",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text =
                        "Phiên bản ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                            if (BuildConfig.DEBUG) " · debug" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text =
                        "Soạn và nghe văn bản bằng TTS hệ thống hoặc ElevenLabs, " +
                            "lưu thư viện theo thể loại, import thư mục từ bộ nhớ và đồng bộ lại khi cần.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text = "Ứng dụng chạy trên thiết bị của bạn; dữ liệu thư viện nằm trong không gian lưu của app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Đóng")
            }
        },
    )
}
