package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun ReaderParagraphModeToggleRow(
    paragraphSplitMode: Boolean,
    onSwitchToFullTextMode: () -> Unit,
    onSwitchToParagraphSplitMode: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = onSwitchToFullTextMode,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (!paragraphSplitMode) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            Color.Transparent
                        },
                    ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Article,
                contentDescription = "Toàn bộ",
                tint =
                    if (!paragraphSplitMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        IconButton(
            onClick = onSwitchToParagraphSplitMode,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (paragraphSplitMode) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            Color.Transparent
                        },
                    ),
        ) {
            Icon(
                imageVector = Icons.Filled.DynamicFeed,
                contentDescription = "Theo đoạn",
                tint =
                    if (paragraphSplitMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
