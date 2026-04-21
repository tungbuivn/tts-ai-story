package com.ttsaistory.app.ui.library

import com.ttsaistory.app.ui.core.AppAlertDialog
import com.ttsaistory.app.ui.core.DialogSemanticTone
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun DialogLibraryDeleteCategory(
    categoryName: String,
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AppAlertDialog(
        tone = DialogSemanticTone.Error,
        onDismissRequest = onDismissRequest,
        title = { Text("Xóa thể loại?") },
        text = {
            Text("Xóa \"$categoryName\" và mọi truyện trong đó (file trên đĩa).")
        },
        confirmButton = {
            Button(onClick = onConfirmDelete) {
                Text("Xóa")
            }
        },
    )
}
