package com.ttsaistory.app.ui.library

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ttsaistory.app.data.StoryLibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chuẩn hóa mọi kiểu xuống dòng về LF (tránh CRLF / CR / LS khiến tách/ghép sai). */
private fun normalizeLineBreaksToLf(s: String): String =
    s.replace("\r\n", "\n").replace('\r', '\n').replace('\u2028', '\n').replace('\u2029', '\n')

/** Ghép danh sách selector thành một chuỗi nhiều dòng (LF giữa các selector). */
private fun contentSelectorsToEditorText(selectors: List<String>): String =
    selectors.joinToString(separator = "\n") { normalizeLineBreaksToLf(it) }.trimEnd('\n')

/** Tách ô «Nội dung» thành danh sách selector (mỗi dòng một phần tử, LF thật). */
private fun editorTextToContentSelectors(text: String): List<String> =
    normalizeLineBreaksToLf(text)
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * Hai ô: **Trang tiếp** (một selector CSS trỏ tới `<a>`, lấy `href` khi ghi DB) và **Nội dung**
 * (mỗi dòng = một selector).
 * Bấm **OK** mới ghi DB; [onConfirmed] / [onCancelled] để parent đóng / đồng bộ.
 */
@Composable
internal fun LibraryOnlineSelectorsManageDialog(
    categoryId: Long,
    repository: StoryLibraryRepository,
    ctx: Context,
    scope: CoroutineScope,
    onConfirmed: () -> Unit,
    onCancelled: () -> Unit,
    title: String = "Cấu hình online",
) {
    var nextPageDraft by remember(categoryId) { mutableStateOf("") }
    var contentDraft by remember(categoryId) { mutableStateOf("") }

    LaunchedEffect(categoryId) {
        val list =
            withContext(Dispatchers.IO) {
                runCatching { repository.getOnlineContentSelectorsForCategory(categoryId) }
                    .getOrDefault(emptyList())
            }
        contentDraft = contentSelectorsToEditorText(list)
        val next =
            withContext(Dispatchers.IO) {
                runCatching { repository.getOnlineNextPageSelectorForCategory(categoryId) }
                    .getOrNull()
            }
        nextPageDraft = next?.let { normalizeLineBreaksToLf(it) }.orEmpty()
    }

    fun applyAndConfirm() {
        scope.launch {
            try {
                val contentSelectors = editorTextToContentSelectors(contentDraft)
                withContext(Dispatchers.IO) {
                    repository.setOnlineNextPageSelectorForCategory(
                        categoryId,
                        nextPageDraft.trim().ifEmpty { null },
                    )
                    repository.replaceOnlineContentSelectors(categoryId, contentSelectors)
                    repository.resetOnlineContentParseStateForStoriesInCategory(categoryId)
                }
                Toast.makeText(ctx, "Đã lưu", Toast.LENGTH_SHORT).show()
                onConfirmed()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "Lỗi lưu", Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onCancelled,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
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
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = contentDraft,
                        onValueChange = { contentDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nội dung") },
                        placeholder = { Text("Mỗi dòng một selector CSS") },
                        minLines = 5,
                        maxLines = 16,
                        singleLine = false,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onCancelled) {
                            Text("Hủy")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { applyAndConfirm() }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
