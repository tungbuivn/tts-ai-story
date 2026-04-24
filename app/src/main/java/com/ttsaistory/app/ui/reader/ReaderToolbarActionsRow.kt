package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Cột nút công cụ tab nhập văn: xem/chỉnh sửa, phát, dừng, (truyện web) tải lại nội dung,
 * chương trước/sau trong truyện, chuyển truyện (thư viện), chương mới; một nút chuyển toàn văn / lưới đoạn khi đang chỉnh sửa. (Xuất AAC trên top bar.)
 */
@Composable
internal fun ReaderToolbarActionsColumn(
    modifier: Modifier = Modifier,
    textEditorChromeViewOnly: Boolean,
    onToggleEditorChromeViewOnly: () -> Unit,
    onPlayParagraphsClick: () -> Unit,
    playParagraphsEnabled: Boolean,
    onStopSpeechClick: () -> Unit,
    stopSpeechEnabled: Boolean,
    showReloadWebContent: Boolean,
    onReloadWebContentClick: () -> Unit,
    reloadWebContentEnabled: Boolean,
    onMoveStoryCategoryClick: () -> Unit,
    moveStoryCategoryEnabled: Boolean,
    onNewLibraryStoryClick: () -> Unit,
    newLibraryStoryEnabled: Boolean,
    onNavigatePrevLibraryStoryClick: () -> Unit,
    navigatePrevLibraryStoryEnabled: Boolean,
    onNavigateNextLibraryStoryClick: () -> Unit,
    navigateNextLibraryStoryEnabled: Boolean,
    paragraphSplitMode: Boolean,
    onSwitchToFullTextMode: () -> Unit,
    onSwitchToParagraphSplitMode: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onToggleEditorChromeViewOnly) {
                Icon(
                    imageVector =
                        if (textEditorChromeViewOnly) {
                            Icons.Filled.Edit
                        } else {
                            Icons.Filled.Visibility
                        },
                    contentDescription =
                        if (textEditorChromeViewOnly) {
                            "Chỉnh sửa"
                        } else {
                            "Chỉ xem"
                        },
                )
            }
            IconButton(
                onClick = onPlayParagraphsClick,
                enabled = playParagraphsEnabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "Phát từ bookmark hoặc từ câu đầu",
                )
            }
            IconButton(
                onClick = onStopSpeechClick,
                enabled = stopSpeechEnabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Dừng đọc",
                )
            }
            if (!textEditorChromeViewOnly) {
                IconButton(
                    onClick = {
                        if (paragraphSplitMode) {
                            onSwitchToFullTextMode()
                        } else {
                            onSwitchToParagraphSplitMode()
                        }
                    },
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            ),
                ) {
                    Icon(
                        imageVector =
                            if (paragraphSplitMode) {
                                Icons.Filled.DynamicFeed
                            } else {
                                Icons.AutoMirrored.Filled.Article
                            },
                        contentDescription =
                            if (paragraphSplitMode) {
                                "Đang theo đoạn — chạm để toàn văn"
                            } else {
                                "Đang toàn văn — chạm để theo đoạn"
                            },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (showReloadWebContent) {
                IconButton(
                    onClick = onReloadWebContentClick,
                    enabled = reloadWebContentEnabled,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Tải lại nội dung từ web",
                    )
                }
            }
            IconButton(
                onClick = onNavigatePrevLibraryStoryClick,
                enabled = navigatePrevLibraryStoryEnabled,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Chương trước trong truyện",
                )
            }
            IconButton(
                onClick = onNavigateNextLibraryStoryClick,
                enabled = navigateNextLibraryStoryEnabled,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Chương sau trong truyện",
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onMoveStoryCategoryClick,
                enabled = moveStoryCategoryEnabled,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Chuyển truyện (thư viện)",
                )
            }
            IconButton(
                onClick = onNewLibraryStoryClick,
                enabled = newLibraryStoryEnabled,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.NoteAdd,
                    contentDescription = "Truyện mới (trống)",
                )
            }
        }
    }
}
