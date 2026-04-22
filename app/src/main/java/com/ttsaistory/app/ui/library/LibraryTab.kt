package com.ttsaistory.app.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ttsaistory.app.data.LibraryCategoryRow
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.model.LibraryCategoryToolbarCommand

@Composable
fun LibraryTab(
    modifier: Modifier = Modifier,
    repository: StoryLibraryRepository,
    refreshTrigger: Int,
    toolbarCommand: LibraryCategoryToolbarCommand?,
    onToolbarCommandConsumed: () -> Unit,
    /** Truyện đang được mở/chỉnh sửa ở tab Text (null nếu không gắn file thư viện). */
    activeEditingStoryId: Long? = null,
    onOpenStory: (Long) -> Unit,
    onPlayCategory: (Long) -> Unit,
    onLibraryChanged: () -> Unit,
    postLibraryFolderImportProgress: (OpenFileProgressUi?) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<LibraryCategoryRow>>(emptyList()) }
    val preferredOpenStoryId by rememberUpdatedState(activeEditingStoryId)

    var showAddCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LibraryCategoryRow?>(null) }
    var renameText by remember { mutableStateOf("") }
    /** Hộp thoại xác nhận xuất thể loại (Downloads). */
    var exportConfirmTarget by remember { mutableStateOf<LibraryCategoryRow?>(null) }
    var exportFormat by remember { mutableStateOf(LibraryCategoryExportFormat.SeparateFilesInFolder) }
    /** Thể loại đang kéo đổi thứ tự — dùng để highlight dòng tương ứng. */
    var draggingCategoryId by remember { mutableStateOf<Long?>(null) }
    /** Thể loại chứa truyện đang mở ở tab Text (null nếu không có truyện thư viện đang sửa). */
    var categoryIdForActiveEdit by remember { mutableStateOf<Long?>(null) }

    fun reload() {
        scope.launch {
            categories = withContext(Dispatchers.IO) { repository.listCategories() }
        }
    }

    /** Hoán vị một bậc trong lúc kéo (UI cập nhật ngay); [direction] chỉ +1 hoặc -1. */
    fun reorderCategoryOneStep(fromIndex: Int, direction: Int) {
        require(direction == 1 || direction == -1)
        if (categories.isEmpty()) return
        val toIndex = fromIndex + direction
        if (toIndex !in categories.indices) return
        val m = categories.toMutableList()
        val item = m.removeAt(fromIndex)
        m.add(toIndex, item)
        categories = m
    }

    fun persistCategoryDisplayOrderAfterDrag() {
        if (categories.isEmpty()) return
        val ids = categories.map { it.id }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.reorderCategoryDisplayOrder(ids)
                }
                onLibraryChanged()
            } catch (e: Exception) {
                Toast.makeText(
                    ctx,
                    e.message ?: "Lỗi đổi thứ tự thể loại",
                    Toast.LENGTH_SHORT,
                ).show()
                reload()
            }
        }
    }

    fun launchExportCategoryToDownloads(cat: LibraryCategoryRow) {
        val exportDialogTitle = "Đang xuất Downloads"
        fun postExportProgress(completed: Int, total: Int, label: String) {
            postLibraryFolderImportProgress(
                OpenFileProgressUi(
                    completed,
                    total,
                    label,
                    dialogTitle = exportDialogTitle,
                ),
            )
        }
        scope.launch {
            try {
                postExportProgress(0, 0, "Đang chuẩn bị…")
                val path =
                    withContext(Dispatchers.IO) {
                        when (exportFormat) {
                            LibraryCategoryExportFormat.SeparateFilesInFolder ->
                                repository.exportCategoryStoriesSeparateFilesToDownloads(
                                    cat.id,
                                    cat.name,
                                    onProgress = { c, t, label -> postExportProgress(c, t, label) },
                                )
                            LibraryCategoryExportFormat.MergedSingleTxt ->
                                repository.exportCategoryMergedTextToDownloads(
                                    cat.id,
                                    cat.name,
                                    onProgress = { c, t, label -> postExportProgress(c, t, label) },
                                )
                            LibraryCategoryExportFormat.SingleZip ->
                                repository.exportCategoryZipToDownloads(
                                    cat.id,
                                    cat.name,
                                    mergeSingleFile = false,
                                    onProgress = { c, t, label -> postExportProgress(c, t, label) },
                                )
                            LibraryCategoryExportFormat.SingleEpub ->
                                repository.exportCategoryEpubToDownloads(
                                    cat.id,
                                    cat.name,
                                    onProgress = { c, t, label -> postExportProgress(c, t, label) },
                                )
                        }
                    }
                Toast.makeText(
                    ctx,
                    "Đã lưu: $path",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    ctx,
                    e.message ?: "Lỗi ghi file",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                postLibraryFolderImportProgress(null)
                exportConfirmTarget = null
            }
        }
    }

    LaunchedEffect(refreshTrigger) {
        reload()
    }

    LaunchedEffect(activeEditingStoryId, refreshTrigger) {
        val sid = activeEditingStoryId
        categoryIdForActiveEdit =
            if (sid == null) {
                null
            } else {
                withContext(Dispatchers.IO) { repository.getStory(sid)?.categoryId }
            }
    }

    val categoryListState = rememberLazyListState()

    LaunchedEffect(categories, categoryIdForActiveEdit, activeEditingStoryId, draggingCategoryId) {
        if (draggingCategoryId != null) return@LaunchedEffect
        if (activeEditingStoryId == null) return@LaunchedEffect
        val cid = categoryIdForActiveEdit ?: return@LaunchedEffect
        val idx = categories.indexOfFirst { it.id == cid }
        if (idx >= 0) {
            categoryListState.animateScrollToItem(idx)
        }
    }

    LaunchedEffect(toolbarCommand) {
        when (toolbarCommand) {
            null -> Unit
            LibraryCategoryToolbarCommand.AddCategory -> {
                newCategoryName = ""
                showAddCategory = true
                onToolbarCommandConsumed()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // Scaffold lồng: tắt contentWindowInsets mặc định (system bars) để tránh padding top thừa dưới TabRow.
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        ) { padding ->
        LazyColumn(
            state = categoryListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            
            itemsIndexed(categories, key = { _, cat -> cat.id }) { _, cat ->
                val isDraggingThis = draggingCategoryId == cat.id
                val highlightsEditingStoryCategory =
                    !isDraggingThis &&
                        activeEditingStoryId != null &&
                        cat.id == categoryIdForActiveEdit
                val cardShape = RoundedCornerShape(12.dp)
                val borderModifier =
                    when {
                        isDraggingThis ->
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                cardShape,
                            )
                        highlightsEditingStoryCategory ->
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.tertiary,
                                cardShape,
                            )
                        else -> Modifier
                    }
                val cardContainerColor =
                    when {
                        isDraggingThis ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        highlightsEditingStoryCategory ->
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    }
                Card(
                    modifier = Modifier.fillMaxWidth().then(borderModifier),
                    shape = cardShape,
                    colors =
                        CardDefaults.cardColors(
                            containerColor = cardContainerColor,
                        ),
                ) {
                    Column(
                        Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryReorderDragHandle(
                                categoryId = cat.id,
                                listSize = categories.size,
                                resolveIndex = { categories.indexOfFirst { it.id == cat.id } },
                                onMoveOneStep = { from, dir -> reorderCategoryOneStep(from, dir) },
                                onDragReorderCommitted = { persistCategoryDisplayOrderAfterDrag() },
                                onDragHighlightChange = { active ->
                                    draggingCategoryId = if (active) cat.id else null
                                },
                            )
                            if (!cat.coverImagePath.isNullOrBlank()) {
                                CategoryCoverThumb(
                                    coverImagePath = cat.coverImagePath,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (cat.storyCount <= 0) {
                                                Toast.makeText(
                                                    ctx,
                                                    "Chưa có truyện trong thể loại.",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                return@clickable
                                            }
                                            scope.launch {
                                                val id =
                                                    withContext(Dispatchers.IO) {
                                                        repository.resolveStoryIdToOpenForCategory(
                                                            cat.id,
                                                            preferredOpenStoryId,
                                                        )
                                                    }
                                                if (id != null) {
                                                    onOpenStory(id)
                                                } else {
                                                    Toast.makeText(
                                                        ctx,
                                                        "Chưa có truyện trong thể loại.",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                        },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (cat.isOnline) {
                                        Text(
                                            "Web",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(start = 6.dp),
                                        )
                                    }
                                }
                                Text(
                                    "${cat.storyCount} truyện",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { onPlayCategory(cat.id) },
                                enabled = cat.storyCount > 0,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = "Phát cả thể loại",
                                )
                            }
                            LibraryCategoryOverflowMenu(
                                category = cat,
                                repository = repository,
                                scope = scope,
                                ctx = ctx,
                                onRename = {
                                    renameTarget = cat
                                    renameText = cat.name
                                },
                                onExportClick = {
                                    exportFormat = LibraryCategoryExportFormat.SeparateFilesInFolder
                                    exportConfirmTarget = cat
                                },
                                onDelete = {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                repository.deleteCategory(cat.id)
                                            }
                                            Toast.makeText(ctx, "Đã xóa", Toast.LENGTH_SHORT).show()
                                            reload()
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                ctx,
                                                e.message ?: "Lỗi",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                },
                                onLibraryChanged = onLibraryChanged,
                                postLibraryFolderImportProgress = postLibraryFolderImportProgress,
                            )
                        }
                    }
                }
            }
        }
        }
    }

    exportConfirmTarget?.let { cat ->
        DialogLibraryExportCategoryToDownloads(
            categoryName = cat.name,
            exportFormat = exportFormat,
            onExportFormatChange = { exportFormat = it },
            onDismissRequest = { exportConfirmTarget = null },
            onConfirmExport = { launchExportCategoryToDownloads(cat) },
        )
    }

    if (showAddCategory) {
        DialogLibraryNewCategory(
            categoryNameDraft = newCategoryName,
            onCategoryNameDraftChange = { newCategoryName = it },
            onDismissRequest = { showAddCategory = false },
            onConfirmCreate = { trimmed, treatAsOnline ->
                scope.launch {
                    try {
                        if (treatAsOnline) {
                            val (id, url) =
                                withContext(Dispatchers.IO) {
                                    repository.insertOnlineLibraryCategory(trimmed)
                                }
                            Toast.makeText(
                                    ctx,
                                    "Đã tạo thể loại online và một truyện (URL đã lưu). Selector theo domain trong Cấu hình Parser online.",
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                            showAddCategory = false
                            newCategoryName = ""
                            reload()
                            onLibraryChanged()
                        } else {
                            withContext(Dispatchers.IO) {
                                repository.insertCategory(trimmed)
                            }
                            Toast.makeText(ctx, "Đã tạo thể loại", Toast.LENGTH_SHORT).show()
                            showAddCategory = false
                            newCategoryName = ""
                            reload()
                            onLibraryChanged()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            ctx,
                            e.message ?: "Lỗi",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }

    renameTarget?.let { cat ->
        DialogLibraryRenameCategory(
            nameDraft = renameText,
            onNameDraftChange = { renameText = it },
            onDismissRequest = { renameTarget = null },
            onConfirmSave = { n ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repository.renameCategory(cat.id, n) }
                        Toast.makeText(ctx, "Đã đổi tên", Toast.LENGTH_SHORT).show()
                        renameTarget = null
                        reload()
                    } catch (e: Exception) {
                        Toast.makeText(
                            ctx,
                            e.message ?: "Lỗi",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun CategoryReorderDragHandle(
    categoryId: Long,
    listSize: Int,
    resolveIndex: () -> Int,
    onMoveOneStep: (fromIndex: Int, direction: Int) -> Unit,
    onDragReorderCommitted: () -> Unit,
    onDragHighlightChange: (active: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    val dragAccum = remember { floatArrayOf(0f) }
    val resolveIndexLatest by rememberUpdatedState(resolveIndex)
    val listSizeLatest by rememberUpdatedState(listSize)
    val onMoveOneStepLatest by rememberUpdatedState(onMoveOneStep)
    val onDragReorderCommittedLatest by rememberUpdatedState(onDragReorderCommitted)
    val onDragHighlightChangeLatest by rememberUpdatedState(onDragHighlightChange)
    Box(
        modifier =
            Modifier
                .padding(end = 2.dp)
                .pointerInput(categoryId, thresholdPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragAccum[0] = 0f
                            onDragHighlightChangeLatest(true)
                        },
                        onDrag = { _, dragAmount ->
                            dragAccum[0] += dragAmount.y
                            val threshold = thresholdPx
                            while (true) {
                                val idx = resolveIndexLatest()
                                if (idx < 0) break
                                val maxIdx = (listSizeLatest - 1).coerceAtLeast(0)
                                val y = dragAccum[0]
                                if (y >= threshold && idx < maxIdx) {
                                    onMoveOneStepLatest(idx, 1)
                                    dragAccum[0] -= threshold
                                } else if (y <= -threshold && idx > 0) {
                                    onMoveOneStepLatest(idx, -1)
                                    dragAccum[0] += threshold
                                } else {
                                    break
                                }
                            }
                        },
                        onDragEnd = {
                            dragAccum[0] = 0f
                            onDragHighlightChangeLatest(false)
                            onDragReorderCommittedLatest()
                        },
                        onDragCancel = {
                            dragAccum[0] = 0f
                            onDragHighlightChangeLatest(false)
                            onDragReorderCommittedLatest()
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Kéo để đổi thứ tự",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
