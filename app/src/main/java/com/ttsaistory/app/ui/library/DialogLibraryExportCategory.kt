package com.ttsaistory.app.ui.library

import com.ttsaistory.app.ui.core.AppAlertDialog
import com.ttsaistory.app.ui.core.DialogSemanticTone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun DialogLibraryExportCategoryToDownloads(
    categoryName: String,
    exportFormat: LibraryCategoryExportFormat,
    onExportFormatChange: (LibraryCategoryExportFormat) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmExport: () -> Unit,
) {
    AppAlertDialog(
        tone = DialogSemanticTone.Info,
        onDismissRequest = onDismissRequest,
        title = { Text("Xuất bản") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text =
                        "Lưu dưới Download/tts-ai-story. Xuất lại cùng kiểu sẽ ghi đè file hoặc thư mục đã có.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExportFormatRadioRow(
                    selected = exportFormat == LibraryCategoryExportFormat.SeparateFilesInFolder,
                    onClick = { onExportFormatChange(LibraryCategoryExportFormat.SeparateFilesInFolder) },
                    title = "Thư mục + nhiều file .txt",
                    subtitle = "Một thư mục trùng tên truyện, mỗi chương một file 00000001.txt …",
                )
                ExportFormatRadioRow(
                    selected = exportFormat == LibraryCategoryExportFormat.MergedSingleTxt,
                    onClick = { onExportFormatChange(LibraryCategoryExportFormat.MergedSingleTxt) },
                    title = "Một file .txt (ghép)",
                    subtitle = "File duy nhất <tên truyện>.txt, nội dung các chương nối lại.",
                )
                ExportFormatRadioRow(
                    selected = exportFormat == LibraryCategoryExportFormat.SingleZip,
                    onClick = { onExportFormatChange(LibraryCategoryExportFormat.SingleZip) },
                    title = "Một file .zip",
                    subtitle = "File <tên truyện>.zip; bên trong các .txt đánh số như tùy chọn thư mục.",
                )
                ExportFormatRadioRow(
                    selected = exportFormat == LibraryCategoryExportFormat.SingleEpub,
                    onClick = { onExportFormatChange(LibraryCategoryExportFormat.SingleEpub) },
                    title = "Một file .epub",
                    subtitle = "Mục lục (nav): dòng đầu tiên của mỗi chương; mỗi chương một tệp XHTML.",
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirmExport) {
                Text("Xuất")
            }
        },
    )
}

@Composable
private fun ExportFormatRadioRow(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                )
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
