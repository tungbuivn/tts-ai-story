package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Cột nút công cụ tab nhập văn: xem/chỉnh sửa, phát, dừng, xuất AAC, (truyện web) tải lại nội dung,
 * chuyển thể loại, truyện mới; kèm hàng chế độ đoạn khi đang chỉnh sửa.
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
    onExportM4aClick: () -> Unit,
    exportM4aEnabled: Boolean,
    showReloadWebContent: Boolean,
    onReloadWebContentClick: () -> Unit,
    reloadWebContentEnabled: Boolean,
    onMoveStoryCategoryClick: () -> Unit,
    moveStoryCategoryEnabled: Boolean,
    onNewLibraryStoryClick: () -> Unit,
    newLibraryStoryEnabled: Boolean,
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
            IconButton(
                onClick = onExportM4aClick,
                enabled = exportM4aEnabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.AudioFile,
                    contentDescription = "Lưu AAC (.m4a)",
                )
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
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onMoveStoryCategoryClick,
                enabled = moveStoryCategoryEnabled,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Chuyển thể loại",
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
        if (!textEditorChromeViewOnly) {
            ReaderParagraphModeToggleRow(
                paragraphSplitMode = paragraphSplitMode,
                onSwitchToFullTextMode = onSwitchToFullTextMode,
                onSwitchToParagraphSplitMode = onSwitchToParagraphSplitMode,
            )
        }
    }
}
