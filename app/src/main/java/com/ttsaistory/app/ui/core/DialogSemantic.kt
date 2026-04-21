package com.ttsaistory.app.ui.core

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Bo góc [androidx.compose.material3.AlertDialog] dùng chung (rất nhẹ). */
internal val AppDialogShape: Shape = RoundedCornerShape(5.dp)

/**
 * Tông semantic cho AlertDialog:
 * [titleContentColor] theo tone; nền và màu chữ nội dung trùng [AlertDialogDefaults] (M3).
 */
internal enum class DialogSemanticTone {
    Info,
    Warning,
    Error,
}

@Immutable
internal data class DialogSemanticColors(
    val containerColor: Color,
    val titleContentColor: Color,
    val textContentColor: Color,
)

@Composable
internal fun dialogSemanticColors(tone: DialogSemanticTone): DialogSemanticColors {
    val s = MaterialTheme.colorScheme
    val defaultContainer = AlertDialogDefaults.containerColor
    val defaultText = AlertDialogDefaults.textContentColor
    return when (tone) {
        DialogSemanticTone.Info ->
            DialogSemanticColors(
                containerColor = defaultContainer,
                titleContentColor = s.onPrimaryContainer,
                textContentColor = defaultText,
            )
        DialogSemanticTone.Warning ->
            DialogSemanticColors(
                containerColor = defaultContainer,
                titleContentColor = s.onTertiaryContainer,
                textContentColor = defaultText,
            )
        DialogSemanticTone.Error ->
            DialogSemanticColors(
                containerColor = defaultContainer,
                titleContentColor = s.onErrorContainer,
                textContentColor = defaultText,
            )
    }
}
