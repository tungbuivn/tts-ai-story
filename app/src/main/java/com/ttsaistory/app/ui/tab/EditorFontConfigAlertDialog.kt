package com.ttsaistory.app.ui.tab

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.domain.clearEditorFontDialogPrefs
import com.ttsaistory.app.domain.copyUriToEditorFontFile
import com.ttsaistory.app.domain.editorFontChosenDisplayName
import com.ttsaistory.app.domain.editorFontImportSafeFileName
import com.ttsaistory.app.domain.setEditorFontPathForBothRegions
import com.ttsaistory.app.model.AppPreferenceKeys
import java.util.Locale

@Composable
internal fun EditorFontConfigAlertDialog(
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    dirDraft: String,
    onDirChange: (String) -> Unit,
    onTreeUriStrChange: (String) -> Unit,
    listed: List<EditorFontPickRow>,
    lineSpacing: Float,
    onLineSpacingChange: (Float) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    onPickTree: () -> Unit,
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font vùng soạn") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorFontSizeSlider(fontSize, onFontSize = onFontSizeChange)
                HorizontalDivider()
                EditorLineSpacingSlider(lineSpacing, onLineSpacing = onLineSpacingChange)
                HorizontalDivider()
                EditorScanDirectoryField(
                    dirDraft = dirDraft,
                    onDirChange = onDirChange,
                    onPickTree = onPickTree,
                )
                EditorFontPickList(
                    rows = listed,
                    onPickRow = { row ->
                        val path =
                            row.absolutePath
                                ?: row.pickUri?.let { uri ->
                                    val dest = "import_both_" + editorFontImportSafeFileName(row.label)
                                    copyUriToEditorFontFile(ctx, uri, dest)?.absolutePath
                                }
                        if (path.isNullOrEmpty()) {
                            Toast.makeText(ctx, "Không đọc được font.", Toast.LENGTH_SHORT).show()
                        } else {
                            prefs.setEditorFontPathForBothRegions(path)
                            Toast.makeText(ctx, "Đã gán «${row.label}».", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                HorizontalDivider()
                Text(
                    "Font: ${prefs.editorFontChosenDisplayName() ?: "Mặc định hệ thống"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    prefs.clearEditorFontDialogPrefs()
                    onTreeUriStrChange("")
                    onLineSpacingChange(AppPreferenceKeys.DEFAULT_EDITOR_LINE_SPACING_MULTIPLIER)
                    onFontSizeChange(AppPreferenceKeys.DEFAULT_EDITOR_FONT_SIZE_SP)
                    Toast.makeText(ctx, "Đã đặt lại mặc định.", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("Mặc định")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Đóng")
            }
        },
    )
}

@Composable
private fun EditorFontSizeSlider(
    value: Float,
    onFontSize: (Float) -> Unit,
) {
    Text("Cỡ chữ", style = MaterialTheme.typography.titleSmall)
    Slider(
        value = value,
        onValueChange = onFontSize,
        valueRange = 12f..28f,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        String.format(Locale.US, "%.0f sp", value),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EditorLineSpacingSlider(
    value: Float,
    onLineSpacing: (Float) -> Unit,
) {
    Text("Khoảng cách dòng", style = MaterialTheme.typography.titleSmall)
    Slider(
        value = value,
        onValueChange = onLineSpacing,
        valueRange = 1f..2.5f,
        steps = 29,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EditorScanDirectoryField(
    dirDraft: String,
    onDirChange: (String) -> Unit,
    onPickTree: () -> Unit,
) {
    OutlinedTextField(
        value = dirDraft,
        onValueChange = onDirChange,
        label = { Text("Thư mục quét") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onPickTree) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Chọn thư mục")
            }
        },
    )
}

@Composable
private fun EditorFontPickList(
    rows: List<EditorFontPickRow>,
    onPickRow: (EditorFontPickRow) -> Unit,
) {
    Text("Tệp (${rows.size})", style = MaterialTheme.typography.labelLarge)
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
    ) {
        items(rows, key = { it.pickUri?.toString() ?: it.absolutePath ?: it.label }) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPickRow(row) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
