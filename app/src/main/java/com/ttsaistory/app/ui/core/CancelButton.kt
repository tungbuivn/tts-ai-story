package com.ttsaistory.app.ui.core

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal open class ErrorToneDialogDismissButtonStyle {
    @Composable
    open fun buttonColors() =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
}

/** Nút Hủy / đóng dialog (tone lỗi) — gọi `CancelButton(onClick = { ... })`. */
internal object CancelButton : ErrorToneDialogDismissButtonStyle() {
    @Composable
    operator fun invoke(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        text: String = "Hủy",
    ) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = buttonColors(),
        ) {
            Text(text)
        }
    }
}
