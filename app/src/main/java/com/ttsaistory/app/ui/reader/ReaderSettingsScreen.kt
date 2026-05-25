package com.ttsaistory.app.ui.reader

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.domain.ParagraphTextService
import com.ttsaistory.app.domain.PreSplitRegexReplacementRule
import com.ttsaistory.app.domain.PreSplitRegexReplacements
import com.ttsaistory.app.model.AppPreferenceKeys
import com.ttsaistory.app.model.PreSplitRegexRulesStore

private data class RuleEditorRow(
    val stableId: Long,
    val pattern: String,
    val replacement: String,
    val enabled: Boolean,
) {
    fun toRule(): PreSplitRegexReplacementRule =
        PreSplitRegexReplacementRule(pattern = pattern, replacement = replacement, enabled = enabled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsScreen(
    prefs: SharedPreferences,
    readerService: ReaderService,
    storyLibrary: StoryLibraryRepository,
    activeLibraryStoryId: Long?,
    chapterEditorTextForReparse: String,
    onDismissRequest: () -> Unit,
    onChapterCanonicalUpdated: (String) -> Unit,
) {
    var firstMsText by remember {
        mutableStateOf(
            prefs
                .getInt(
                    AppPreferenceKeys.KEY_READER_IME_HIDE_DELAY_FIRST_MS,
                    AppPreferenceKeys.DEFAULT_READER_IME_HIDE_DELAY_FIRST_MS,
                ).toString(),
        )
    }
    var secondMsText by remember {
        mutableStateOf(
            prefs
                .getInt(
                    AppPreferenceKeys.KEY_READER_IME_HIDE_DELAY_SECOND_MS,
                    AppPreferenceKeys.DEFAULT_READER_IME_HIDE_DELAY_SECOND_MS,
                ).toString(),
        )
    }
    val initialParsed =
        remember {
            PreSplitRegexReplacements.parseRulesJson(
                prefs.getString(AppPreferenceKeys.KEY_PRE_SPLIT_REGEX_RULES_JSON, null),
            )
        }
    var nextRuleId by remember { mutableLongStateOf(initialParsed.size.toLong()) }
    val ruleRows: SnapshotStateList<RuleEditorRow> =
        remember {
            initialParsed
                .mapIndexed { i, r ->
                    RuleEditorRow(
                        stableId = i.toLong(),
                        pattern = r.pattern,
                        replacement = r.replacement,
                        enabled = r.enabled,
                    )
                }.toMutableStateList()
        }
    var sampleText by remember { mutableStateOf("") }
    var pipelineTestOutput by remember { mutableStateOf("") }
    var singleRuleTestOutput by remember { mutableStateOf("") }

    fun updateRuleRow(stableId: Long, block: (RuleEditorRow) -> RuleEditorRow) {
        val ix = ruleRows.indexOfFirst { it.stableId == stableId }
        if (ix >= 0) {
            ruleRows[ix] = block(ruleRows[ix])
        }
    }

    fun persistAndClose() {
        val a =
            firstMsText.toIntOrNull()?.coerceIn(0, 2000)
                ?: AppPreferenceKeys.DEFAULT_READER_IME_HIDE_DELAY_FIRST_MS
        val b =
            secondMsText.toIntOrNull()?.coerceIn(0, 5000)
                ?: AppPreferenceKeys.DEFAULT_READER_IME_HIDE_DELAY_SECOND_MS
        prefs
            .edit()
            .putInt(AppPreferenceKeys.KEY_READER_IME_HIDE_DELAY_FIRST_MS, a)
            .putInt(AppPreferenceKeys.KEY_READER_IME_HIDE_DELAY_SECOND_MS, b)
            .apply()
        PreSplitRegexRulesStore.saveToPrefs(prefs, ruleRows.map { it.toRule() })
        ParagraphTextService.invalidateStoredTextParseCache()
        val sid = activeLibraryStoryId?.takeIf { it > 0L }
        val canon =
            readerService.setChapterText(
                chapterEditorTextForReparse,
                chapterId = sid,
                libraryRepository = if (sid != null) storyLibrary else null,
            )
        onChapterCanonicalUpdated(canon)
        onDismissRequest()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                navigationIcon = {
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Đóng")
                    }
                },
                actions = {
                    TextButton(onClick = { persistAndClose() }) {
                        Text("Lưu")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "Độ trễ ẩn bàn phím — khi bật \"luôn ẩn bàn phím\" ở chế độ sửa theo câu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = firstMsText,
                    onValueChange = { firstMsText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Lần 1 (ms, mặc định 20)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = secondMsText,
                    onValueChange = { secondMsText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Lần 2 (ms, mặc định 80)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Thay thế regex trước khi tách câu",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        "Mỗi mục: biểu thức regex Kotlin, chuỗi thay thế (hỗ trợ \$1…). " +
                            "Áp dụng theo thứ tự danh sách; pattern không hợp lệ sẽ bị bỏ qua khi đọc truyện.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = sampleText,
                    onValueChange = { sampleText = it },
                    label = { Text("Văn mẫu để thử") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            singleRuleTestOutput = ""
                            pipelineTestOutput =
                                ParagraphTextService.previewFullPreprocess(
                                    sampleText,
                                    ruleRows.map { it.toRule() },
                                )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Chạy thử toàn bộ tiền xử lý (theo form)")
                    }
                }
            }
            item {
                if (pipelineTestOutput.isNotEmpty()) {
                    OutlinedTextField(
                        value = pipelineTestOutput,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kết quả (LF + che chữ + regex bật trên form)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
            item {
                if (singleRuleTestOutput.isNotEmpty()) {
                    OutlinedTextField(
                        value = singleRuleTestOutput,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kết quả thử một quy tắc") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val id = nextRuleId
                        nextRuleId = id + 1
                        ruleRows.add(RuleEditorRow(stableId = id, pattern = "", replacement = "", enabled = true))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Thêm quy tắc", modifier = Modifier.padding(start = 8.dp))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ruleRows.forEachIndexed { displayIndex, row ->
                        key(row.stableId) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Quy tắc ${displayIndex + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Bật", style = MaterialTheme.typography.bodySmall)
                                            Switch(
                                                checked = row.enabled,
                                                onCheckedChange = { v ->
                                                    updateRuleRow(row.stableId) { it.copy(enabled = v) }
                                                },
                                            )
                                            IconButton(
                                                onClick = { ruleRows.removeAll { it.stableId == row.stableId } },
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Xóa")
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = row.pattern,
                                        onValueChange = { v ->
                                            updateRuleRow(row.stableId) { it.copy(pattern = v) }
                                        },
                                        label = { Text("Regex (tìm)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        textStyle =
                                            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    )
                                    OutlinedTextField(
                                        value = row.replacement,
                                        onValueChange = { v ->
                                            updateRuleRow(row.stableId) { it.copy(replacement = v) }
                                        },
                                        label = { Text("Thay bằng") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        textStyle =
                                            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            pipelineTestOutput = ""
                                            singleRuleTestOutput =
                                                PreSplitRegexReplacements.tryReplaceOne(
                                                    sampleText,
                                                    row.pattern,
                                                    row.replacement,
                                                ).fold(
                                                    onSuccess = { it },
                                                    onFailure = { e ->
                                                        "Lỗi: ${e.message ?: e.javaClass.simpleName}"
                                                    },
                                                )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Chạy thử mục này trên văn mẫu")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
