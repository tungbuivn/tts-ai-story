package com.ttsaistory.app.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.ttsaistory.app.data.looksLikeWebCategoryUrl
import com.ttsaistory.app.ui.core.AppAlertDialog
import com.ttsaistory.app.ui.core.DialogSemanticTone
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun DialogLibraryNewCategory(
    categoryNameDraft: String,
    onCategoryNameDraftChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    /**
     * [trimmedName] đã trim, không rỗng.
     * [treatAsOnlineWebCategory] = [looksLikeWebCategoryUrl](trimmedName) — URL → truyện online, text thường → thể loại thường.
     */
    onConfirmCreate: (trimmedName: String, treatAsOnlineWebCategory: Boolean) -> Unit,
) {
    val asUrl = looksLikeWebCategoryUrl(categoryNameDraft)
    val ctx = LocalContext.current
    AppAlertDialog(
        tone = DialogSemanticTone.Info,
        onDismissRequest = onDismissRequest,
        title = { Text("Truyện mới (thư viện)") },
        text = {
            OutlinedTextField(
                value = categoryNameDraft,
                onValueChange = onCategoryNameDraftChange,
                label = { Text(if (asUrl) "URL hoặc tên" else "Tên") },
                supportingText =
                    if (asUrl) {
                        {
                            Text(
                                "Nhận diện URL — tạo truyện online. Text không phải URL → thể loại thường. Parser: ☰ → Cấu hình Parser online.",
                            )
                        }
                    } else {
                        null
                    },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val cm =
                                ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cm.primaryClip
                            if (clip == null || clip.itemCount <= 0) {
                                Toast.makeText(ctx, "Clipboard trống", Toast.LENGTH_SHORT).show()
                            } else {
                                val pasted = clip.getItemAt(0).coerceToText(ctx).toString()
                                if (pasted.isBlank()) {
                                    Toast.makeText(ctx, "Clipboard trống", Toast.LENGTH_SHORT).show()
                                } else {
                                    onCategoryNameDraftChange(pasted)
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "Dán từ clipboard",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = categoryNameDraft.trim()
                    if (n.isNotEmpty()) {
                        onConfirmCreate(n, looksLikeWebCategoryUrl(n))
                    }
                },
            ) {
                Text("Tạo")
            }
        },
    )
}
