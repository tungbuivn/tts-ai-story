package com.ttsaistory.app.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.data.LibraryCategoryRow
import com.ttsaistory.app.data.LibraryStoryRow

@Composable
internal fun DialogTextTabMoveStoryCategory(
    story: LibraryStoryRow,
    moveCategoryCategories: List<LibraryCategoryRow>,
    moveStoryTitleDraft: String,
    onMoveStoryTitleDraftChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSaveTitleClick: () -> Unit,
    onMoveToCategoryClick: (LibraryCategoryRow) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Chuyển thể loại / đổi tên") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Tiêu đề",
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = moveStoryTitleDraft,
                    onValueChange = onMoveStoryTitleDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Tên truyện") },
                )
                TextButton(
                    onClick = onSaveTitleClick,
                    enabled = moveStoryTitleDraft.trim() != story.title.trim(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Lưu tên")
                }
                Text(
                    "Chuyển sang thể loại",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (moveCategoryCategories.none { it.id != story.categoryId }) {
                    Text(
                        "Tạo thêm một thể loại khác để có thể chuyển.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                moveCategoryCategories
                    .filter { it.id != story.categoryId }
                    .forEach { cat ->
                        TextButton(
                            onClick = { onMoveToCategoryClick(cat) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(cat.name)
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Đóng") }
        },
    )
}
