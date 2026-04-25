package com.ttsaistory.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
import com.ttsaistory.app.model.TextTabSpeechEngine
import com.ttsaistory.app.ui.reader.ExportM4aTopBarState
import com.ttsaistory.app.ui.reader.ReaderService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppMainTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    topBarTint: Color,
    onNavigationMenuClick: () -> Unit,
    tabIndex: Int,
    textTabSpeechEngine: TextTabSpeechEngine,
    onTopBarTextSettingsClick: () -> Unit,
    onLibraryAddCategoryClick: () -> Unit,
    onLibraryImportFolderClick: () -> Unit,
    /** Tab Text: xuất AAC; null khi không gắn ReaderTab (vd. tab Thư viện). */
    exportM4aTopBar: ExportM4aTopBarState?,
    readerService: ReaderService,
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to lerp(scheme.primaryContainer, scheme.surface, 0.18f),
                        1f to topBarTint,
                    ),
                ),
        title = { Text("TTS AI Story") },
        navigationIcon = {
            IconButton(onClick = onNavigationMenuClick) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "Menu",
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = scheme.onSurface,
                navigationIconContentColor = scheme.onSurface,
                actionIconContentColor = scheme.onSurface,
            ),
        actions = {
            if (tabIndex == 0) {
                if (readerService.deferredFetchHasRemaining) {
                    val fetchEnabled = readerService.deferredFetchContinueEnabled
                    val fetchWorking = readerService.deferredFetchWorking
                    val fetchSpinTransition = rememberInfiniteTransition(label = "fetch-spin")
                    val fetchSpinDeg by fetchSpinTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(durationMillis = 1100, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                        label = "fetch-spin-deg",
                    )
                    IconButton(
                        onClick = {
                            readerService.deferredFetchContinueEnabled =
                                !readerService.deferredFetchContinueEnabled
                        },
                        modifier =
                            Modifier.background(
                                color =
                                    when {
                                        fetchWorking -> scheme.tertiaryContainer.copy(alpha = 0.95f)
                                        fetchEnabled -> scheme.primaryContainer.copy(alpha = 0.9f)
                                        else -> Color.Transparent
                                    },
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = "Continue fetch",
                            modifier =
                                Modifier.rotate(
                                    if (fetchWorking) fetchSpinDeg else 0f,
                                ),
                            tint =
                                if (fetchEnabled) {
                                    if (fetchWorking) {
                                        scheme.tertiary
                                    } else {
                                        scheme.primary
                                    }
                                } else {
                                    scheme.onSurfaceVariant
                                },
                        )
                    }
                }
                exportM4aTopBar?.let { ex ->
                    IconButton(
                        onClick = ex.onClick,
                        enabled = ex.enabled,
                    ) {
                        Icon(
                            Icons.Filled.AudioFile,
                            contentDescription = "Lưu AAC (.m4a)",
                        )
                    }
                }
                IconButton(onClick = onTopBarTextSettingsClick) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription =
                            when (textTabSpeechEngine) {
                                TextTabSpeechEngine.System ->
                                    "Cấu hình TTS hệ thống"
                                TextTabSpeechEngine.ElevenLabs ->
                                    "Cấu hình ElevenLabs"
                            },
                    )
                }
            }
            if (tabIndex == 1) {
                IconButton(onClick = onLibraryAddCategoryClick) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Thêm truyện",
                    )
                }
                IconButton(onClick = onLibraryImportFolderClick) {
                    Icon(
                        Icons.Filled.CreateNewFolder,
                        contentDescription = "Import thư mục",
                    )
                }
            }
        },
    )
}
