package com.ttsaistory.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Trạng thái dialog log khi bắt đầu luồng mở tệp (SAF). */
data class OpenFileProgressLogUi(
    /** Tên hiển thị tài liệu (nếu có). */
    val displayName: String?,
    /** Trạng thái / tên entry ZIP đang giải nén (cập nhật từ [onFileExtracted] trong AppTabs). */
    val message: String = "Đang xử lý…",
    /** Sau khi giải nén ZIP xong — thanh tiến độ chuyển sang đầy (100%). */
    val progressCompleted: Boolean = false,
)

/**
 * Dialog riêng cho bước đầu mở tệp — tiêu đề cố định **Open file progress**, không dùng chung
 * [OpenFileProgressDialog] (tiến trình nhập EPUB/ZIP/thư viện).
 */
@Composable
fun OpenFileProgressLogDialog(ui: OpenFileProgressLogUi?) {
    val s = ui ?: return
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
                        .width(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Open file progress",
                    style = MaterialTheme.typography.titleMedium,
                )
                val name = s.displayName?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    Text(
                        text = "Tệp: $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                )
                if (s.progressCompleted) {
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
