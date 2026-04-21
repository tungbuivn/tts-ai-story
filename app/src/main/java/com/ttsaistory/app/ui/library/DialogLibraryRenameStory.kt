package com.ttsaistory.app.ui.library

import com.ttsaistory.app.ui.core.AppAlertDialog
import com.ttsaistory.app.ui.core.DialogSemanticTone
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun DialogLibraryRenameStory(
    titleDraft: String,
    onTitleDraftChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    /** Tên sau trim; rỗng được thay bằng "Không tiêu đề" (giống logic lưu truyện). */
    onConfirmSave: (resolvedTitle: String) -> Unit,
) {
    AppAlertDialog(
        tone = DialogSemanticTone.Info,
        onDismissRequest = onDismissRequest,
        title = { Text("Đổi tên truyện") },
        text = {
            OutlinedTextField(
                value = titleDraft,
                onValueChange = onTitleDraftChange,
                label = { Text("Tên mới") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = titleDraft.trim().ifEmpty { "Không tiêu đề" }
                    onConfirmSave(n)
                },
            ) {
                Text("Lưu")
            }
        },
    )
}
