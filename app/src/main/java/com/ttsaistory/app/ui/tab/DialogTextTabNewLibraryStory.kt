package com.ttsaistory.app.ui.tab

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.data.LibraryCategoryRow
import com.ttsaistory.app.ui.core.CancelButton

@Composable
internal fun DialogTextTabNewLibraryStory(
    categories: List<LibraryCategoryRow>,
    newCategoryNameDraft: String,
    onNewCategoryNameDraftChange: (String) -> Unit,
    selectedCategoryId: Long?,
    onSelectedCategoryIdChange: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmCreateClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Truyện mới") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Tạo file trống, tiêu đề không tên (số thứ tự). Chọn thể loại:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (categories.isEmpty()) {
                    Text(
                        text = "Chưa có thể loại — nhập tên thể loại mới bên dưới.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Thể loại",
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
                    label = { Text("Hoặc thể loại mới (ưu tiên)") },
                    singleLine = true,
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
