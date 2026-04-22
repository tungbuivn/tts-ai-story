package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.ttsaistory.app.data.LibraryStoryRow
import com.ttsaistory.app.ui.core.AppAlertDialog
import com.ttsaistory.app.ui.core.DialogSemanticTone
import kotlinx.coroutines.delay

/**
 * Bảng chọn truyện trong một thể loại — kiểu **end drawer**: **không dùng [androidx.compose.ui.window.Dialog]**,
 * hai lớp `Box` chồng nhau: scrim full màn (trái bấm đóng) + dải phải **full chiều cao** vùng tab (nền
 * [androidx.compose.material3.MaterialTheme.colorScheme.surface] kín, không để lộ nội dung phía dưới),
 * bên trong là [Card]. [BackHandler] gọi [onDismissRequest]. [LazyColumn] + `key`; lọc debounce;
 * đổi thứ tự (sort_order) khi không lọc; xóa truyện ([onDeleteStory]) sau khi xác nhận [AppAlertDialog].
 * Mỗi dòng hiển thị [LibraryStoryRow.lastSpeechSentenceIndex] (câu 1-based); hàng [currentStoryId] được tô nổi.
 */
@Composable
internal fun DialogReaderStoryPicker(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    categoryTitle: String,
    loading: Boolean,
    stories: List<LibraryStoryRow>,
    /** Thể loại đang mở — cần để gọi [com.ttsaistory.app.data.StoryLibraryRepository.moveStoryOrderInCategory]. */
    categoryId: Long?,
    currentStoryId: Long?,
    onStorySelected: (Long) -> Unit,
    /** [delta] `-1` lên trên, `+1` xuống dưới (chỉ khi danh sách không bị lọc). */
    onMoveStoryOrder: (storyId: Long, delta: Int) -> Unit,
    onDeleteStory: (storyId: Long) -> Unit,
) {
    val listState = rememberLazyListState()
    var filterText by remember { mutableStateOf("") }
    var deleteConfirmStory by remember { mutableStateOf<LibraryStoryRow?>(null) }
    var debouncedFilter by remember { mutableStateOf("") }
    LaunchedEffect(filterText) {
        delay(220)
        debouncedFilter = filterText
    }

    val displayed =
        remember(stories, debouncedFilter) {
            val q = debouncedFilter.trim()
            if (q.isEmpty()) stories
            else stories.filter { it.title.contains(q, ignoreCase = true) }
        }

    val reorderEnabled =
        remember(categoryId, loading, stories, debouncedFilter) {
            categoryId != null &&
                    !loading &&
                    stories.size > 1 &&
                    debouncedFilter.isBlank()
        }

    LaunchedEffect(visible) {
        if (!visible) deleteConfirmStory = null
    }

    LaunchedEffect(currentStoryId, displayed, loading, visible) {
        if (!visible || loading) return@LaunchedEffect
        val id = currentStoryId ?: return@LaunchedEffect
        val idx = displayed.indexOfFirst { it.id == id }
        if (idx < 0) return@LaunchedEffect
        delay(48)
        val total = listState.layoutInfo.totalItemsCount
        if (idx < total) {
            listState.scrollToItem(index = idx)
        }
    }

    if (!visible) return

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val panelMaxWidth = minOf(300.dp, (screenWidthDp * 0.72f).dp)
    val surface = MaterialTheme.colorScheme.surface

    BackHandler(onBack = onDismissRequest)
    Box(Modifier.fillMaxSize()) {
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .widthIn(min = 220.dp, max = panelMaxWidth)
                    .background(surface),
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),

                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),

            ) {
                Column(
                        modifier =
                            Modifier
                                .fillMaxSize(),


                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    val firstId = stories.firstOrNull()?.id ?: return@IconButton
                                    onStorySelected(firstId)
                                },
                                enabled = !loading && stories.isNotEmpty(),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = "Mở truyện đầu danh sách",
                                )
                            }
                            Text(
                                text =
                                    if (categoryTitle.isNotBlank()) {
                                        "Truyện — $categoryTitle"
                                    } else {
                                        "Truyện trong thể loại"
                                    },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleSmall,
                            label = {
                                Text(
                                    "Lọc theo tên",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            placeholder = {
                                Text(
                                    "${stories.size} truyện",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            enabled = !loading && stories.isNotEmpty(),
                        )
                        if (!reorderEnabled && stories.size > 1 && debouncedFilter.isNotBlank()) {
                            Text(
                                text = "Xóa bộ lọc để sắp xếp thứ tự truyện.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            loading ->
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }

                            stories.isEmpty() ->
                                Text(
                                    text = "Không có truyện trong thể loại này.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                )

                            displayed.isEmpty() ->
                                Text(
                                    text = "Không có truyện khớp bộ lọc.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )

                            else ->
                                LazyColumn(
                                    state = listState,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    itemsIndexed(
                                        displayed,
                                        key = { _, s -> s.id },
                                    ) { idx, story ->
                                        val editing = story.id == currentStoryId
                                        val rowShape = RoundedCornerShape(10.dp)
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    .clip(rowShape)
                                                    .background(
                                                        color =
                                                            if (editing) {
                                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                                    alpha = 0.88f,
                                                                )
                                                            } else {
                                                                MaterialTheme.colorScheme.surface
                                                            },
                                                        shape = rowShape,
                                                    )
                                                    .then(
                                                        if (editing) {
                                                            Modifier.border(
                                                                width = 2.dp,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = rowShape,
                                                            )
                                                        } else {
                                                            Modifier
                                                        },
                                                    ),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .clickable { onStorySelected(story.id) }
                                                        .padding(
                                                            start = 10.dp,
                                                            top = 10.dp,
                                                            end = 4.dp,
                                                            bottom = 10.dp,
                                                        ),
                                            ) {
                                                if (editing) {
                                                    Text(
                                                        text = "Đang mở — đọc gần nhất",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Text(
                                                    text =
                                                        story.title.ifBlank {
                                                            "Không tiêu đề (#${story.id})"
                                                        },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight =
                                                        if (editing) {
                                                            FontWeight.SemiBold
                                                        } else {
                                                            FontWeight.Normal
                                                        },
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                val lastReadLine =
                                                    if (story.lastSpeechSentenceIndex >= 0) {
                                                        "Câu đọc lần cuối: ${story.lastSpeechSentenceIndex + 1}"
                                                    } else {
                                                        ""
                                                    }
                                                Text(
                                                    text = lastReadLine,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color =
                                                        if (editing) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                            ) {
                                                IconButton(
                                                    onClick = { onMoveStoryOrder(story.id, -1) },
                                                    enabled = reorderEnabled && idx > 0,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                                        contentDescription = "Chuyển lên",
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onMoveStoryOrder(story.id, 1) },
                                                    enabled =
                                                        reorderEnabled &&
                                                            idx < displayed.lastIndex,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                                        contentDescription = "Chuyển xuống",
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { deleteConfirmStory = story },
                                                enabled = !loading,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Xóa truyện",
                                                )
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                        }
                    }
                }
            }
        deleteConfirmStory?.let { pending ->
            val titleText =
                pending.title.ifBlank { "Không tiêu đề (#${pending.id})" }
            AppAlertDialog(
                tone = DialogSemanticTone.Error,
                onDismissRequest = { deleteConfirmStory = null },
                title = { Text("Xóa truyện?") },
                text = { Text(titleText) },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteStory(pending.id)
                            deleteConfirmStory = null
                        },
                    ) {
                        Text("Xóa")
                    }
                },
            )
        }
    }
}
