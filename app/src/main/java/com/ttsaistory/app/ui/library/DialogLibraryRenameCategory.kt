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
internal fun DialogLibraryRenameCategory(
    nameDraft: String,
    onNameDraftChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    /** [trimmedName] đã được trim và không rỗng (nút Lưu chỉ gọi khi hợp lệ). */
    onConfirmSave: (trimmedName: String) -> Unit,
) {
    AppAlertDialog(
        tone = DialogSemanticTone.Info,
        onDismissRequest = onDismissRequest,
        title = { Text("Đổi tên") },
        text = {
            OutlinedTextField(
                value = nameDraft,
                onValueChange = onNameDraftChange,
                label = { Text("Tên mới") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = nameDraft.trim()
                    if (n.isNotEmpty()) onConfirmSave(n)
                },
            ) {
                Text("Lưu")
            }
        },
    )
}
