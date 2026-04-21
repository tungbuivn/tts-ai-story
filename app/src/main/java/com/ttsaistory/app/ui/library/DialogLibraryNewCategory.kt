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
internal fun DialogLibraryNewCategory(
    categoryNameDraft: String,
    onCategoryNameDraftChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    /** [trimmedName] đã được trim và không rỗng (nút Tạo chỉ gọi khi hợp lệ). */
    onConfirmCreate: (trimmedName: String) -> Unit,
) {
    AppAlertDialog(
        tone = DialogSemanticTone.Info,
        onDismissRequest = onDismissRequest,
        title = { Text("Thể loại mới") },
        text = {
            OutlinedTextField(
                value = categoryNameDraft,
                onValueChange = onCategoryNameDraftChange,
                label = { Text("Tên") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = categoryNameDraft.trim()
                    if (n.isNotEmpty()) onConfirmCreate(n)
                },
            ) {
                Text("Tạo")
            }
        },
    )
}
