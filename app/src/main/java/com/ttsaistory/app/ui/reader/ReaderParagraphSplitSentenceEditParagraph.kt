package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction as FoundationPressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Một hàng ô **sửa** câu trong lưới split: tái sử dụng [ReaderParagraphSplitSentenceCellRowFrame]
 * (cùng khung với chế độ chỉ xem) rồi vẽ [BasicTextField] bên trong.
 *
 * Đây là kiểu “mở rộng” hợp lệ trong Compose — composition thay vì subclass Kotlin.
 */
@Composable
internal fun ReaderParagraphSplitSentenceEditParagraphRow(
    textEditorChromeViewOnly: Boolean,
    highlightCurrentSpeakingParagraph: Boolean,
    textSelectionColors: TextSelectionColors,
    paraForEdit: TextFieldValue,
    readOnlyKeyboardHidden: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    cellFocusRequester: FocusRequester,
    onFocusChanged: (androidx.compose.ui.focus.FocusState) -> Unit,
    paragraphOutlineEditTextStyle: TextStyle,
    focusedParagraphIndex: Int,
    flatIdx: Int,
    cellParagraphInteractionSource: MutableInteractionSource,
    cellSelectOverlayInteraction: MutableInteractionSource,
    onOverlayClick: () -> Unit,
) {
    val paragraphCellFocusOrange = Color(0xFFFF9800)
    val cellOutlineColor =
        if (focusedParagraphIndex == flatIdx) {
            paragraphCellFocusOrange
        } else {
            MaterialTheme.colorScheme.outline
        }
    ReaderParagraphSplitSentenceCellRowFrame(
        textEditorChromeViewOnly = textEditorChromeViewOnly,
        highlightCurrentSpeakingParagraph = highlightCurrentSpeakingParagraph,
    ) {
        ReaderParagraphSplitSentenceEditCellContent(
            textEditorChromeViewOnly = textEditorChromeViewOnly,
            textSelectionColors = textSelectionColors,
            paraForEdit = paraForEdit,
            readOnlyKeyboardHidden = readOnlyKeyboardHidden,
            onValueChange = onValueChange,
            cellFocusRequester = cellFocusRequester,
            onFocusChanged = onFocusChanged,
            paragraphOutlineEditTextStyle = paragraphOutlineEditTextStyle,
            focusedParagraphIndex = focusedParagraphIndex,
            flatIdx = flatIdx,
            cellOutlineColor = cellOutlineColor,
            cellParagraphInteractionSource = cellParagraphInteractionSource,
            cellSelectOverlayInteraction = cellSelectOverlayInteraction,
            onOverlayClick = onOverlayClick,
        )
    }
}

/** Nội dung ô sửa (BasicTextField) — dùng trong [ReaderParagraphSplitSentenceEditParagraphRow]. */
@Composable
private fun RowScope.ReaderParagraphSplitSentenceEditCellContent(
    /** Chỉ xem (chrome): không vẽ con trỏ soạn thảo; vẫn dùng cùng ô [BasicTextField] read-only. */
    textEditorChromeViewOnly: Boolean,
    textSelectionColors: TextSelectionColors,
    paraForEdit: TextFieldValue,
    readOnlyKeyboardHidden: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    cellFocusRequester: FocusRequester,
    onFocusChanged: (androidx.compose.ui.focus.FocusState) -> Unit,
    paragraphOutlineEditTextStyle: TextStyle,
    focusedParagraphIndex: Int,
    flatIdx: Int,
    cellOutlineColor: Color,
    cellParagraphInteractionSource: MutableInteractionSource,
    cellSelectOverlayInteraction: MutableInteractionSource,
    onOverlayClick: () -> Unit,
) {
    val paragraphCellFocusOrange = Color(0xFFFF9800)
    if (readOnlyKeyboardHidden) {
        LaunchedEffect(cellSelectOverlayInteraction, flatIdx) {
            cellSelectOverlayInteraction.interactions.collect { interaction ->
                if (interaction is FoundationPressInteraction.Release) {
                    onOverlayClick()
                }
            }
        }
    }
    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = paraForEdit,
                readOnly = readOnlyKeyboardHidden,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(cellFocusRequester)
                        .onFocusChanged(onFocusChanged),
                textStyle = paragraphOutlineEditTextStyle,
                cursorBrush =
                    SolidColor(
                        when {
                            textEditorChromeViewOnly -> Color.Transparent
                            focusedParagraphIndex == flatIdx -> paragraphCellFocusOrange
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ),
                interactionSource = cellParagraphInteractionSource,
                keyboardOptions = KeyboardOptions.Default,
                keyboardActions = KeyboardActions.Default,
                maxLines = Int.MAX_VALUE,
                minLines = 1,
                decorationBox = { innerTextField ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (textEditorChromeViewOnly) {
                                        Modifier
                                    } else {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = cellOutlineColor,
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        innerTextField()
                        if (readOnlyKeyboardHidden) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = cellSelectOverlayInteraction,
                                        indication = null,
                                        onClick = {
                                            runCatching { cellFocusRequester.requestFocus() }
                                        },
                                    ),
                            )
                        }
                    }
                },
            )
        }
    }
}
