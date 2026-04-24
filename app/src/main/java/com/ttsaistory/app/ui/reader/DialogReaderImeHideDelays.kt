package com.ttsaistory.app.ui.reader

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.model.AppPreferenceKeys

@Composable
fun DialogReaderImeHideDelays(
    onDismissRequest: () -> Unit,
    prefs: SharedPreferences,
) {
    var firstMsText by remember {
        mutableStateOf(
            prefs
                .getInt(
                    AppPreferenceKeys.KEY_READER_IME_HIDE_DELAY_FIRST_MS,
                    AppPreferenceKeys.DEFAULT_READER_IME_HIDE_DELAY_FIRST_MS,
                )
                .toString(),
        )
    }
    var secondMsText by remember {
        mutableStateOf(
            prefs
                .getInt(
                    AppPreferenceKeys.KEY_READER_IME_HIDE_DELAY_SECOND_MS,
                    AppPreferenceKeys.DEFAULT_READER_IME_HIDE_DELAY_SECOND_MS,
                )
                .toString(),
        )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Độ trễ ẩn bàn phím") },
        text = {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text =
                        "Áp dụng khi bật \"luôn ẩn bàn phím\" ở chế độ sửa theo câu. " +
                            "Hai lần ẩn IME lặp lại sau mỗi khoảng (ms); 0 = không chờ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = firstMsText,
                    onValueChange = { firstMsText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Lần 1 (mặc định 20 ms)") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = secondMsText,
                    onValueChange = { secondMsText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Lần 2 (mặc định 80 ms)") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
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
                    onDismissRequest()
                },
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Huỷ")
            }
        },
    )
}
