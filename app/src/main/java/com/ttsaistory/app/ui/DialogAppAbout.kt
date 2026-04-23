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
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "TTS AI Story",
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text =
                        "Phiên bản ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                            if (BuildConfig.DEBUG) " · debug" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "Chức năng",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text =
                        "Soạn và đọc truyện bằng TTS hệ thống hoặc ElevenLabs; lưu thư viện theo thể loại; " +
                            "nhập thư mục từ bộ nhớ; đồng bộ / làm mới nội dung truyện web khi cần.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text = "File & chia sẻ",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text =
                        "• Văn bản: .txt, text/* (kể cả HTML qua Chia sẻ).\n" +
                            "• EPUB: .epub (application/epub+zip).\n" +
                            "• ZIP: .zip / application/zip (gói truyện nén).\n" +
                            "• PDF: .pdf.\n" +
                            "• Mở bằng / Gửi tới app / Xử lý văn bản (PROCESS_TEXT) — tùy máy và app nguồn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "File xuất (output)",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text =
                        "• Thư viện — xuất theo thể loại: .txt (một file ghép hoặc nhiều file .txt đánh số), " +
                            ".zip (một file .txt ghép hoặc nhiều .txt trong zip), .epub — lưu dưới Download/tts-ai-story.\n" +
                            "• Đọc TTS — xuất AAC: .m4a (cả truyện) — lưu dưới Music/tts-ai-story (đường dẫn cụ thể hiện khi xuất xong).",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "Dữ liệu",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text =
                        "Ứng dụng chạy trên thiết bị của bạn; thư viện và file nhập nằm trong không gian lưu riêng của app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "Liên hệ",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text = "Tác giả: tungbuivn\nEmail: tungbuivn@gmail.com",
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
