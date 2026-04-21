package com.ttsaistory.app.ui.tab

import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ttsaistory.app.model.AppPreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun EditorFontConfigDialog(
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    /** Tăng mỗi lần mở màn cài font — ép quét lại danh sách (kể cả khi đường dẫn không đổi). */
    openSession: Int,
) {
    val ctx = LocalContext.current
    var dirDraft by remember { mutableStateOf(editorFontScanDirOrDefault(prefs)) }
    var treeUriStr by remember {
        mutableStateOf(prefs.getString(AppPreferenceKeys.KEY_EDITOR_FONT_SCAN_TREE_URI, "").orEmpty())
    }
    var listed by remember { mutableStateOf<List<EditorFontPickRow>>(emptyList()) }
    var lineSpacing by remember {
        mutableFloatStateOf(
            prefs
                .getFloat(
                    AppPreferenceKeys.KEY_EDITOR_LINE_SPACING_MULTIPLIER,
                    AppPreferenceKeys.DEFAULT_EDITOR_LINE_SPACING_MULTIPLIER,
                )
                .coerceIn(1f, 2.5f),
        )
    }
    var fontSize by remember {
        mutableFloatStateOf(
            prefs
                .getFloat(
                    AppPreferenceKeys.KEY_EDITOR_FONT_SIZE_SP,
                    AppPreferenceKeys.DEFAULT_EDITOR_FONT_SIZE_SP,
                )
                .coerceIn(12f, 28f),
        )
    }

    LaunchedEffect(dirDraft) {
        delay(400)
        prefs.edit().putString(AppPreferenceKeys.KEY_EDITOR_FONT_SCAN_DIR, dirDraft.trim()).apply()
    }
    LaunchedEffect(openSession) {
        dirDraft = editorFontScanDirOrDefault(prefs)
        treeUriStr = prefs.getString(AppPreferenceKeys.KEY_EDITOR_FONT_SCAN_TREE_URI, "").orEmpty()
    }
    LaunchedEffect(dirDraft, treeUriStr, openSession) {
        listed = withContext(Dispatchers.IO) { buildEditorFontPickList(ctx, dirDraft, treeUriStr) }
    }

    val pickTree =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure {
                Toast.makeText(ctx, "Không lưu được quyền đọc thư mục.", Toast.LENGTH_LONG).show()
            }
            prefs.edit().putString(AppPreferenceKeys.KEY_EDITOR_FONT_SCAN_TREE_URI, uri.toString()).apply()
            treeUriStr = uri.toString()
        }

    EditorFontConfigAlertDialog(
        prefs = prefs,
        onDismiss = onDismiss,
        dirDraft = dirDraft,
        onDirChange = { dirDraft = it },
        onTreeUriStrChange = { treeUriStr = it },
        listed = listed,
        lineSpacing = lineSpacing,
        onLineSpacingChange = { v ->
            lineSpacing = v
            prefs.edit().putFloat(AppPreferenceKeys.KEY_EDITOR_LINE_SPACING_MULTIPLIER, v).apply()
        },
        fontSize = fontSize,
        onFontSizeChange = { v ->
            fontSize = v
            prefs.edit().putFloat(AppPreferenceKeys.KEY_EDITOR_FONT_SIZE_SP, v).apply()
        },
        onPickTree = { pickTree.launch(null) },
    )
}
