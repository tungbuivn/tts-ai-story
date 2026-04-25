package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.domain.flatIndexToMainSub
import com.ttsaistory.app.ui.fonts.ReaderTabEditorAppearance
import com.ttsaistory.app.ui.fonts.editorLineHeightSp

/** Viền ô bookmark `last_speech_sentence_index` (chỉ xem, không phát). */
private val LastSpeechBookmarkBorderColor = Color(0xFF4527A0)
private val LocalSelectedSentenceBorderColor = Color(0xFFFF9800)

/**
 * Khung hàng chung cho ô câu (nền ô sửa, viền bookmark tím, viền câu đang phát = [primary]).
 * Chế độ chỉ xem và chế độ sửa dùng cùng khung; nội dung bên trong do [content] cung cấp.
 */
@Composable
internal fun ReaderParagraphSplitSentenceCellRowFrame(
    textEditorChromeViewOnly: Boolean,
    highlightCurrentSpeakingParagraph: Boolean,
    lastSpeechBookmarkBorder: Boolean,
    localSelectedBorder: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val bookmarkShape = RoundedCornerShape(6.dp)
    val editCellShape = RoundedCornerShape(8.dp)
    val outerShape = if (textEditorChromeViewOnly) bookmarkShape else editCellShape
    val speakingBorderColor = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (lastSpeechBookmarkBorder && textEditorChromeViewOnly) {
                        Modifier.border(2.dp, LastSpeechBookmarkBorderColor, bookmarkShape)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (localSelectedBorder) {
                        Modifier.border(2.dp, LocalSelectedSentenceBorderColor, outerShape)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (highlightCurrentSpeakingParagraph) {
                        Modifier.border(2.dp, speakingBorderColor, outerShape)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (textEditorChromeViewOnly) {
                        Modifier
                    } else {
                        Modifier
                            .background(
                                color =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.45f,
                                    ),
                                shape = editCellShape,
                            )
                    },
                ),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/**
 * Lưới LazyColumn theo câu (split). Chỉ xem và sửa đều dùng
 * [ReaderParagraphSplitSentenceEditParagraphRow] ([BasicTextField] read-only khi chỉ xem).
 */
@Composable
internal fun ReaderParagraphSplitSentenceLazyGrid(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    paragraphPageItemCount: Int,
    paragraphPageStartFlat: Int,
    paragraphGroupFieldValues: List<List<TextFieldValue>>,
    textEditorChromeViewOnly: Boolean,
    flatCellTtsStart: IntArray,
    speakingParagraphIndex: Int,
    dbLastSpeechSentenceIndex0: Int,
    systemTtsPlaybackActive: Boolean,
    elevenLabsJobActive: Boolean,
    paragraphSplitMode: Boolean,
    focusedParagraphIndex: Int,
    localSelectedParagraphIndex: Int,
    flatItemCount: Int,
    paragraphFocusRequestToken: Int,
    readerKeyboardForceHidden: Boolean,
    editorAppearance: ReaderTabEditorAppearance,
    onUserSelectedParagraphSplitCell: (flatIdx: Int) -> Unit,
    hideSoftInputWhenReaderForceHidden: () -> Unit,
    onMergeParagraphBackwardFromCell: (flatIdx: Int) -> Boolean,
    onParagraphCellValueChange: (
        mainIdx: Int,
        subIdx: Int,
        flatIdx: Int,
        newVal: TextFieldValue,
        tryMergeWithPreviousCell: () -> Boolean,
    ) -> Unit,
) {
    val paragraphOutlineEditTextStyle =
        remember(
            editorAppearance.editorBodyStyle,
            editorAppearance.paragraphEditorFontFamily,
            editorAppearance.editorLineSpacingMultiplier,
        ) {
            editorAppearance.editorBodyStyle.copy(
                fontFamily = editorAppearance.paragraphEditorFontFamily,
                lineHeight =
                    editorLineHeightSp(
                        editorAppearance.editorBodyStyle,
                        editorAppearance.editorLineSpacingMultiplier,
                    ),
            )
        }
    val paragraphCellSelectionColors = OutlinedTextFieldDefaults.colors().textSelectionColors
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = paragraphPageItemCount,
            key = { localIdx -> paragraphPageStartFlat + localIdx },
            contentType = { _ -> "paragraphCell" },
        ) { localIdx ->
            val flatIdx = paragraphPageStartFlat + localIdx
            val (mainIdx, subIdx) = flatIndexToMainSub(paragraphGroupFieldValues, flatIdx)
            val ttsStartAtCell =
                if (flatIdx < flatCellTtsStart.size) {
                    flatCellTtsStart[flatIdx]
                } else {
                    0
                }
            val highlightCurrentSpeakingParagraph =
                speakingParagraphIndex >= 0 && ttsStartAtCell == speakingParagraphIndex
            val localSelectedBorder = textEditorChromeViewOnly && localSelectedParagraphIndex == flatIdx
            val lastSpeechBookmarkBorder =
                textEditorChromeViewOnly &&
                    !localSelectedBorder &&
                    speakingParagraphIndex < 0 &&
                    !systemTtsPlaybackActive &&
                    !elevenLabsJobActive &&
                    dbLastSpeechSentenceIndex0 >= 0 &&
                    ttsStartAtCell == dbLastSpeechSentenceIndex0
            val paraForEdit =
                paragraphGroupFieldValues.getOrNull(mainIdx)?.getOrNull(subIdx)
                    ?: return@items
            val cellFocusRequester = remember(flatIdx) { FocusRequester() }
            val cellSelectOverlayInteraction = remember(flatIdx) { MutableInteractionSource() }
            val readOnlyCell =
                textEditorChromeViewOnly || readerKeyboardForceHidden
            LaunchedEffect(
                paragraphSplitMode,
                focusedParagraphIndex,
                paragraphFocusRequestToken,
                textEditorChromeViewOnly,
                flatIdx,
                readerKeyboardForceHidden,
            ) {
                if (!paragraphSplitMode || flatItemCount <= 0) return@LaunchedEffect
                if (focusedParagraphIndex != flatIdx) return@LaunchedEffect
                val shouldRequestFocus =
                    !textEditorChromeViewOnly ||
                        !readerKeyboardForceHidden
                if (shouldRequestFocus) {
                    try {
                        cellFocusRequester.requestFocus()
                    } catch (_: IllegalStateException) {
                    } catch (_: IllegalArgumentException) {
                    }
                }
                hideSoftInputWhenReaderForceHidden()
            }
            val cellParagraphInteractionSource = remember { MutableInteractionSource() }
            if (!textEditorChromeViewOnly && focusedParagraphIndex == flatIdx) {
                LaunchedEffect(cellParagraphInteractionSource, flatIdx, paragraphSplitMode) {
                    if (!paragraphSplitMode) return@LaunchedEffect
                    cellParagraphInteractionSource.interactions.collect { interaction ->
                        if (interaction is PressInteraction.Release) {
                            onUserSelectedParagraphSplitCell(flatIdx)
                        }
                    }
                }
            }
            ReaderParagraphSplitSentenceEditParagraphRow(
                textEditorChromeViewOnly = textEditorChromeViewOnly,
                highlightCurrentSpeakingParagraph = highlightCurrentSpeakingParagraph,
                lastSpeechBookmarkBorder = lastSpeechBookmarkBorder,
                localSelectedBorder = localSelectedBorder,
                textSelectionColors = paragraphCellSelectionColors,
                paraForEdit = paraForEdit,
                readOnlyKeyboardHidden = readOnlyCell,
                onValueChange = { newVal ->
                    onParagraphCellValueChange(
                        mainIdx,
                        subIdx,
                        flatIdx,
                        newVal,
                    ) {
                        onMergeParagraphBackwardFromCell(flatIdx)
                    }
                },
                cellFocusRequester = cellFocusRequester,
                onFocusChanged = { fs ->
                    if (fs.isFocused) {
                        onUserSelectedParagraphSplitCell(flatIdx)
                        hideSoftInputWhenReaderForceHidden()
                    }
                },
                paragraphOutlineEditTextStyle = paragraphOutlineEditTextStyle,
                focusedParagraphIndex = focusedParagraphIndex,
                flatIdx = flatIdx,
                cellParagraphInteractionSource = cellParagraphInteractionSource,
                cellSelectOverlayInteraction = cellSelectOverlayInteraction,
                onOverlayClick = { onUserSelectedParagraphSplitCell(flatIdx) },
            )
        }
    }
}
