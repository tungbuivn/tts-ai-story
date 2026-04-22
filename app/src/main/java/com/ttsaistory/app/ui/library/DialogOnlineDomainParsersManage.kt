package com.ttsaistory.app.ui.library

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.data.OnlineDomainParserRow
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.data.distinctNormalizedDomainsFromUrlLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chuẩn hóa xuống dòng (giống [LibraryOnlineSelectorsManageDialog]). */
private fun normalizeLineBreaksToLf(s: String): String =
    s.replace("\r\n", "\n").replace('\r', '\n').replace('\u2028', '\n').replace('\u2029', '\n')

private fun contentSelectorsToEditorText(selectors: List<String>): String =
    selectors.joinToString(separator = "\n") { normalizeLineBreaksToLf(it) }.trimEnd('\n')

private fun editorTextToContentSelectors(text: String): List<String> =
    normalizeLineBreaksToLf(text)
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

@Composable
fun DialogOnlineDomainParsersManage(
    repository: StoryLibraryRepository,
    onDismissRequest: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var listEpoch by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<OnlineDomainParserRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreateForm by remember { mutableStateOf(false) }
    var urlLinesDraft by remember { mutableStateOf("") }
    var nextPageDraft by remember { mutableStateOf("") }
    var contentDraft by remember { mutableStateOf("") }
    var editingRow by remember { mutableStateOf<OnlineDomainParserRow?>(null) }
    var editNextPageDraft by remember { mutableStateOf("") }
    var editContentDraft by remember { mutableStateOf("") }

    LaunchedEffect(listEpoch) {
        loading = true
        rows =
            withContext(Dispatchers.IO) {
                runCatching { repository.listOnlineDomainParsers() }.getOrDefault(emptyList())
            }
        loading = false
    }

    LaunchedEffect(editingRow?.id) {
        val e = editingRow ?: return@LaunchedEffect
        editNextPageDraft = e.onlineNextPageSelector.orEmpty()
        editContentDraft = contentSelectorsToEditorText(e.contentSelectors)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Parser theo domain") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Mỗi domain chỉ một cấu hình. Khi tạo thể loại online, app khớp domain của URL với danh sách dưới để gán «Trang tiếp» và «Nội dung».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loading) {
                    Text("Đang tải…", style = MaterialTheme.typography.bodyMedium)
                } else if (!showCreateForm) {
                    if (rows.isEmpty()) {
                        Text(
                            "Chưa có parser nào. Bấm «Tạo mới» để thêm.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    row.domain,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .clickable {
                                                editingRow = row
                                            }
                                            .padding(vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    repository.deleteOnlineDomainParser(row.id)
                                                }
                                                Toast.makeText(ctx, "Đã xóa", Toast.LENGTH_SHORT).show()
                                                listEpoch++
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    ctx,
                                                    e.message ?: "Lỗi xóa",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Xóa parser",
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            showCreateForm = true
                            urlLinesDraft = ""
                            nextPageDraft = ""
                            contentDraft = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tạo mới")
                        }
                    }
                } else {
                    Text(
                        "URL (mỗi dòng một URL — hệ thống lấy domain)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedTextField(
                        value = urlLinesDraft,
                        onValueChange = { urlLinesDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://truyen.example/a\nhttps://truyen.example/b") },
                        minLines = 3,
                        maxLines = 8,
                        singleLine = false,
                    )
                    OutlinedTextField(
                        value = nextPageDraft,
                        onValueChange = { nextPageDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Trang tiếp") },
                        placeholder = {
                            Text("Một CSS selector trỏ tới thẻ <a> — lấy href làm URL trang sau")
                        },
                        minLines = 2,
                        maxLines = 6,
                        singleLine = false,
                    )
                    OutlinedTextField(
                        value = contentDraft,
                        onValueChange = { contentDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nội dung") },
                        placeholder = { Text("Mỗi dòng một selector CSS") },
                        minLines = 4,
                        maxLines = 12,
                        singleLine = false,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                showCreateForm = false
                                urlLinesDraft = ""
                                nextPageDraft = ""
                                contentDraft = ""
                            },
                        ) {
                            Text("Hủy")
                        }
                        TextButton(
                            onClick = {
                                val domains = distinctNormalizedDomainsFromUrlLines(urlLinesDraft)
                                if (domains.isEmpty()) {
                                    Toast.makeText(
                                        ctx,
                                        "Cần ít nhất một URL có domain hợp lệ",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@TextButton
                                }
                                val contentSelectors = editorTextToContentSelectors(contentDraft)
                                val nextSel = nextPageDraft.trim().ifEmpty { null }
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            for (d in domains) {
                                                repository.upsertOnlineDomainParser(
                                                    domainKey = d,
                                                    nextPageSelector = nextSel,
                                                    contentSelectors = contentSelectors,
                                                )
                                            }
                                        }
                                        Toast.makeText(
                                            ctx,
                                            "Đã lưu ${domains.size} domain",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        showCreateForm = false
                                        urlLinesDraft = ""
                                        nextPageDraft = ""
                                        contentDraft = ""
                                        listEpoch++
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            ctx,
                                            e.message ?: "Lỗi lưu",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                        ) {
                            Text("Lưu")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Đóng")
            }
        },
    )

    editingRow?.let { row ->
        AlertDialog(
            onDismissRequest = { editingRow = null },
            title = { Text("Sửa parser — ${row.domain}") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Domain: ${row.domain}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = editNextPageDraft,
                        onValueChange = { editNextPageDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Trang tiếp") },
                        placeholder = {
                            Text("Một CSS selector trỏ tới thẻ <a> — lấy href làm URL trang sau")
                        },
                        minLines = 2,
                        maxLines = 6,
                        singleLine = false,
                    )
                    OutlinedTextField(
                        value = editContentDraft,
                        onValueChange = { editContentDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nội dung") },
                        placeholder = { Text("Mỗi dòng một selector CSS") },
                        minLines = 4,
                        maxLines = 12,
                        singleLine = false,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRow = null }) {
                    Text("Hủy")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val contentSelectors = editorTextToContentSelectors(editContentDraft)
                        val nextSel = editNextPageDraft.trim().ifEmpty { null }
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    repository.upsertOnlineDomainParser(
                                        domainKey = row.domain,
                                        nextPageSelector = nextSel,
                                        contentSelectors = contentSelectors,
                                    )
                                }
                                Toast.makeText(ctx, "Đã cập nhật", Toast.LENGTH_SHORT).show()
                                editingRow = null
                                listEpoch++
                            } catch (e: Exception) {
                                Toast.makeText(
                                    ctx,
                                    e.message ?: "Lỗi lưu",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text("Lưu")
                }
            },
        )
    }
}
