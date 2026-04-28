package com.ttsaistory.app.ui.reader

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.data.LibraryCategoryRow
import com.ttsaistory.app.data.looksLikeWebCategoryUrl
import com.ttsaistory.app.ui.core.CancelButton

@Composable
internal fun DialogReaderNewLibraryStory(
    categories: List<LibraryCategoryRow>,
    newCategoryNameDraft: String,
    onNewCategoryNameDraftChange: (String) -> Unit,
    selectedCategoryId: Long?,
    onSelectedCategoryIdChange: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmCreateClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val asUrl = looksLikeWebCategoryUrl(newCategoryNameDraft)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Chương mới") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Tạo file trống, tiêu đề không tên (số thứ tự). Chọn truyện:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (categories.isEmpty()) {
                    Text(
                        text = "Chưa có truyện nào — nhập tên truyện mới bên dưới.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Truyện",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = categories,
                            key = { it.id },
                        ) { cat ->
                            val selected = selectedCategoryId == cat.id
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            color =
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.45f,
                                                    )
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.35f,
                                                    )
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                        .clickable { onSelectedCategoryIdChange(cat.id) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newCategoryNameDraft,
                    onValueChange = onNewCategoryNameDraftChange,
                    label = {
                        Text(if (asUrl) "URL trang (https://…)" else "Hoặc tên truyện mới (ưu tiên)")
                    },
                    supportingText =
                        if (asUrl) {
                            {
                                Text(
                                    "Nhận diện URL — tạo thể loại online + mở chương đầu. Không phải URL → thể loại thường.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            null
                        },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val cm =
                                    ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cm.primaryClip
                                if (clip == null || clip.itemCount <= 0) {
                                    Toast.makeText(ctx, "Clipboard trống", Toast.LENGTH_SHORT).show()
                                } else {
                                    val pasted = clip.getItemAt(0).coerceToText(ctx).toString()
                                    if (pasted.isBlank()) {
                                        Toast.makeText(ctx, "Clipboard trống", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onNewCategoryNameDraftChange(pasted)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Dán từ clipboard",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirmCreateClick) {
                Text("Tạo")
            }
        },
        dismissButton = {
            CancelButton(onClick = onDismissRequest)
        },
    )
}
