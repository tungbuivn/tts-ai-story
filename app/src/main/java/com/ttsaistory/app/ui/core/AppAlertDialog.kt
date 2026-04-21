package com.ttsaistory.app.ui.core

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * [AlertDialog] app: [AppDialogShape], [dialogSemanticColors], nút Hủy mặc định [CancelButton].
 * [includeDefaultDismiss] = false khi chỉ cần [confirmButton] (vd. chỉ nút "Đóng").
 */
@Composable
internal fun AppAlertDialog(
    tone: DialogSemanticTone,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    includeDefaultDismiss: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val dialogColors = dialogSemanticColors(tone)
    val resolvedDismiss: (@Composable () -> Unit)? =
        dismissButton
            ?: if (includeDefaultDismiss) {
                { CancelButton(onClick = onDismissRequest) }
            } else {
                null
            }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = resolvedDismiss,
        icon = icon,
        title = title,
        text = text,
        shape = AppDialogShape,
        containerColor = dialogColors.containerColor,
        titleContentColor = dialogColors.titleContentColor,
        textContentColor = dialogColors.textContentColor,
        properties = properties,
    )
}
