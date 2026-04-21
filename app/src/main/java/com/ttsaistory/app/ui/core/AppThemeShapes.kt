package com.ttsaistory.app.ui.core

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Bo góc qua [androidx.compose.material3.MaterialTheme]: [Shapes.small] / [Shapes.medium]
 * dùng cho nhiều token hình (kể cả nút khi M3 resolve token → shape scale).
 */
internal val AppThemeShapes: Shapes =
    Shapes().copy(
        small = RoundedCornerShape(5.dp),
        medium = RoundedCornerShape(5.dp),
    )
