package com.ttsaistory.app.ui.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ttsaistory.app.data.LibraryStoryRow
import kotlinx.coroutines.delay

/** [TopAppBar] mặc định Material3; khớp [AppMainTopAppBar]. */
private val APP_TOP_APP_BAR_HEIGHT = 64.dp

/** [TabRow] hai tab Text / Thư viện trong [AppModalNavigationDrawerScaffold]. */
private val APP_TAB_ROW_HEIGHT = 48.dp

/** Dành chỗ cho [AppMainBottomBar] + nút điều hướng đoạn. */
private val APP_BOTTOM_BAR_RESERVE = 88.dp

/**
 * Hộp thoại chọn truyện trong một thể loại — **full** vùng nội dung tab Text (cùng kích thước
 * với editor: dưới status bar + top bar + tab row, trên bottom bar), không căn lệch phải.
 * [LazyColumn] + `key`; lọc tên debounce. Đổi thứ tự hiển thị (sort_order) khi không lọc.
 */
@Composable
internal fun DialogStoryPicker(
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
) {
    val listState = rememberLazyListState()
    var filterText by remember { mutableStateOf("") }
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

    val statusPad = WindowInsets.statusBars.asPaddingValues()
    val navPad = WindowInsets.navigationBars.asPaddingValues()
    val topChrome =
        statusPad.calculateTopPadding() + APP_TOP_APP_BAR_HEIGHT + APP_TAB_ROW_HEIGHT
    val bottomChrome = navPad.calculateBottomPadding() + APP_BOTTOM_BAR_RESERVE

    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = scrimInteraction,
                            indication = null,
                            onClick = onDismissRequest,
                        ),
            )
            Card(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = topChrome,
                            bottom = bottomChrome,
                        ),
                shape = RoundedCornerShape(0.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                if (categoryTitle.isNotBlank()) {
                                    "Truyện — $categoryTitle"
                                } else {
                                    "Truyện trong thể loại"
                                },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    }
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Lọc theo tên") },
                        placeholder = { Text("${stories.size} truyện") },
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        displayed.isEmpty() ->
                            Text(
                                text = "Không có truyện khớp bộ lọc.",
                                style = MaterialTheme.typography.bodyMedium,
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
                                                                alpha = 0.72f,
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
                                                        start = 12.dp,
                                                        top = 10.dp,
                                                        end = 4.dp,
                                                        bottom = 10.dp,
                                                    ),
                                        ) {
                                            if (editing) {
                                                Text(
                                                    text = "Đang mở",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            Text(
                                                text =
                                                    story.title.ifBlank {
                                                        "Không tiêu đề (#${story.id})"
                                                    },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight =
                                                    if (editing) {
                                                        FontWeight.SemiBold
                                                    } else {
                                                        FontWeight.Normal
                                                    },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
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
                                                reorderEnabled && idx < displayed.lastIndex,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowDown,
                                                contentDescription = "Chuyển xuống",
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                    }
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Đóng")
                    }
                }
            }
        }
    }
}
