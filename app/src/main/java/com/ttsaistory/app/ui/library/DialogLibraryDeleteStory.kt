package com.ttsaistory.app.ui.library

import com.ttsaistory.app.ui.core.AppAlertDialog
import com.ttsaistory.app.ui.core.DialogSemanticTone
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun DialogLibraryDeleteStory(
    storyTitle: String,
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AppAlertDialog(
        tone = DialogSemanticTone.Error,
        onDismissRequest = onDismissRequest,
        title = { Text("Xóa truyện?") },
        text = { Text(storyTitle) },
        confirmButton = {
            Button(onClick = onConfirmDelete) {
                Text("Xóa")
            }
        },
    )
}
