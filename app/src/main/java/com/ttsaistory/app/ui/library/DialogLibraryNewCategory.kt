package com.ttsaistory.app.ui.library

import com.ttsaistory.app.data.looksLikeWebCategoryUrl
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
    /**
     * [trimmedName] đã trim, không rỗng.
     * [treatAsOnlineWebCategory] true khi nhận diện URL — tạo thể loại online; selector theo domain từ DB (menu Parser online).
     */
    onConfirmCreate: (trimmedName: String, treatAsOnlineWebCategory: Boolean) -> Unit,
) {
    val asUrl = looksLikeWebCategoryUrl(categoryNameDraft)
    AppAlertDialog(
        tone = DialogSemanticTone.Info,
        onDismissRequest = onDismissRequest,
        title = { Text("Thể loại mới") },
        text = {
            OutlinedTextField(
                value = categoryNameDraft,
                onValueChange = onCategoryNameDraftChange,
                label = { Text(if (asUrl) "URL hoặc tên" else "Tên") },
                supportingText = {
                    if (asUrl) {
                        Text(
                            "Nhận diện URL — tạo thể loại online. Cấu hình selector theo domain trong menu ☰ → Cấu hình Parser online.",
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = categoryNameDraft.trim()
                    if (n.isNotEmpty()) onConfirmCreate(n, looksLikeWebCategoryUrl(n))
                },
            ) {
                Text("Tạo")
            }
        },
    )
}
