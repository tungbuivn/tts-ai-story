package com.ttsaistory.app.ui.library

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ttsaistory.app.data.LibraryCategoryRow
import com.ttsaistory.app.data.StoryLibraryRepository

@Composable
internal fun LibraryCategoryOverflowMenu(
    category: LibraryCategoryRow,
    repository: StoryLibraryRepository,
    scope: CoroutineScope,
    ctx: Context,
    onRename: () -> Unit,
    onExportClick: () -> Unit,
    onDelete: () -> Unit,
    onLibraryChanged: () -> Unit,
    postLibraryFolderImportProgress: (OpenFileProgressUi?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteCategoryConfirm by remember { mutableStateOf(false) }

    val pickCategoryCoverImage =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repository.saveCategoryCoverFromContentUri(category.id, uri)
                    }
                    Toast.makeText(ctx, "Đã đặt ảnh đại diện", Toast.LENGTH_SHORT).show()
                    onLibraryChanged()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(
                        ctx,
                        e.message ?: "Không lưu được ảnh",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

    fun launchSyncImportedCategoryFolder() {
        expanded = false
        scope.launch {
            val title = "Đang nhập lại thư mục"
            try {
                postLibraryFolderImportProgress(
                    OpenFileProgressUi(
                        0,
                        0,
                        "Đang chuẩn bị…",
                        dialogTitle = title,
                    ),
                )
                val n =
                    withContext(Dispatchers.IO) {
                        repository.syncImportedStoriesInCategory(category.id) { c, t, label ->
                            postLibraryFolderImportProgress(
                                OpenFileProgressUi(
                                    c,
                                    t,
                                    label,
                                    dialogTitle = title,
                                ),
                            )
                        }
                    }
                Toast.makeText(
                    ctx,
                    "Đã xóa nội dung cũ và nhập lại $n truyện từ thư mục đã lưu.",
                    Toast.LENGTH_LONG,
                ).show()
                onLibraryChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    ctx,
                    e.message ?: "Lỗi nhập lại thư mục",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                postLibraryFolderImportProgress(null)
            }
        }
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Tùy chọn thể loại",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Đổi tên thể loại") },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("Select image") },
                onClick = {
                    expanded = false
                    pickCategoryCoverImage.launch("image/*")
                },
            )
            DropdownMenuItem(
                text = { Text("Xuất ra…") },
                onClick = {
                    expanded = false
                    onExportClick()
                },
                enabled = category.storyCount > 0,
            )
            DropdownMenuItem(
                text = { Text("Đồng bộ thư mục") },
                onClick = { launchSyncImportedCategoryFolder() },
                enabled = category.hasImportFolder,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        "Xóa thể loại",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    showDeleteCategoryConfirm = true
                },
            )
        }
    }

    if (showDeleteCategoryConfirm) {
        DialogLibraryDeleteCategory(
            categoryName = category.name,
            onDismissRequest = { showDeleteCategoryConfirm = false },
            onConfirmDelete = {
                showDeleteCategoryConfirm = false
                onDelete()
            },
        )
    }
}
