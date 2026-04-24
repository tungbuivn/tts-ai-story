package com.ttsaistory.app.ui.reader

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.defaultMinSize
import com.ttsaistory.app.model.AppEditorConstants
import com.ttsaistory.app.ui.fonts.ReaderTabEditorAppearance
import com.ttsaistory.app.ui.fonts.editorLineHeightSp
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Khối soạn thảo toàn văn (Compose [BasicTextField] hoặc [EditText] khi văn bản rất dài).
 */
@Composable
internal fun ReaderTabFullTextEditor(
    modifier: Modifier = Modifier,
    text: String,
    fullTextFieldValue: TextFieldValue,
    onFullTextFieldValueChange: (TextFieldValue) -> Unit,
    onTextChange: (String) -> Unit,
    textEditorChromeViewOnly: Boolean,
    editorAppearance: ReaderTabEditorAppearance,
    fullTextScrollState: androidx.compose.foundation.ScrollState,
    fullTextFocusRequester: FocusRequester,
    fullTextNativeEditRef: AtomicReference<EditText?>,
    nativeTextProgrammatic: AtomicBoolean,
    fullTextNativeTypingSink: androidx.compose.runtime.MutableState<(String) -> Unit>,
) {
    val useNativeHugeEditor = text.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS
    val fullTextInteractionSource = remember { MutableInteractionSource() }
    val fullTextColors = OutlinedTextFieldDefaults.colors()
    val fullTextFocused by fullTextInteractionSource.collectIsFocusedAsState()
    var fullTextNativeFocused by remember { mutableStateOf(false) }
    val outlineFocused = if (useNativeHugeEditor) fullTextNativeFocused else fullTextFocused
    val fullTextStyle =
        MaterialTheme.typography.bodyLarge.merge(
            TextStyle(
                fontFamily = editorAppearance.fullEditorFontFamily,
                fontSize = editorAppearance.editorFontSizeSp.sp,
                lineHeight =
                    editorLineHeightSp(
                        editorAppearance.editorBodyStyle,
                        editorAppearance.editorLineSpacingMultiplier,
                    ),
                color =
                    if (fullTextFocused) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
        )
    val scrollbarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    val maxScroll = fullTextScrollState.maxValue
    val scrollValue = fullTextScrollState.value
    val fullTextFieldShape = OutlinedTextFieldDefaults.shape
    val fullTextOutlineColor =
        if (outlineFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }
    val density = LocalDensity.current
    val nativePadHpx = with(density) { 16.dp.roundToPx() }
    val nativePadTopPx = with(density) { 4.dp.roundToPx() }
    val nativePadBottomPx = with(density) { 12.dp.roundToPx() }
    val nativeOnSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    fun applyNativeFullTextTypeface(et: EditText) {
        val p = editorAppearance.fullEditorFontPath.trim()
        if (p.isNotEmpty()) {
            val tf = runCatching { Typeface.createFromFile(p) }.getOrNull()
            et.typeface = tf ?: Typeface.DEFAULT
        } else {
            et.typeface = Typeface.DEFAULT
        }
        et.setLineSpacing(0f, editorAppearance.editorLineSpacingMultiplier)
    }
    CompositionLocalProvider(LocalTextSelectionColors provides fullTextColors.textSelectionColors) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(fullTextFieldShape)
                        .border(1.dp, fullTextOutlineColor, fullTextFieldShape)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = fullTextFieldShape,
                        ),
            ) {
                Text(
                    text = "Nội dung",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = editorAppearance.fullEditorFontFamily,
                        ),
                    color =
                        if (outlineFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 0.dp),
                )
                if (useNativeHugeEditor) {
                    AndroidView(
                        factory = { context ->
                            EditText(context).apply {
                                inputType =
                                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                gravity = Gravity.TOP or Gravity.START
                                isVerticalScrollBarEnabled = true
                                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                                setHorizontallyScrolling(false)
                                includeFontPadding = false
                                applyNativeFullTextTypeface(this)
                                addTextChangedListener(
                                    object : TextWatcher {
                                        override fun beforeTextChanged(
                                            s: CharSequence?,
                                            start: Int,
                                            count: Int,
                                            after: Int,
                                        ) {
                                        }

                                        override fun onTextChanged(
                                            s: CharSequence?,
                                            start: Int,
                                            before: Int,
                                            count: Int,
                                        ) {
                                        }

                                        override fun afterTextChanged(s: Editable?) {
                                            if (nativeTextProgrammatic.get()) return
                                            fullTextNativeTypingSink.value(s?.toString().orEmpty())
                                        }
                                    },
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(fullTextFocusRequester),
                        update = { et ->
                            fullTextNativeEditRef.set(et)
                            val viewOnly = textEditorChromeViewOnly
                            et.isFocusable = !viewOnly
                            et.isFocusableInTouchMode = !viewOnly
                            et.isCursorVisible = !viewOnly
                            applyNativeFullTextTypeface(et)
                            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, editorAppearance.editorFontSizeSp)
                            et.setTextColor(nativeOnSurfaceArgb)
                            et.setPadding(
                                nativePadHpx,
                                nativePadTopPx,
                                nativePadHpx,
                                nativePadBottomPx,
                            )
                            et.setOnFocusChangeListener { _, hasFocus ->
                                fullTextNativeFocused = hasFocus
                            }
                            if (!nativeTextProgrammatic.get()) {
                                val cur = et.text?.toString().orEmpty()
                                if (cur != text) {
                                    nativeTextProgrammatic.set(true)
                                    try {
                                        et.setText(text)
                                        val ss =
                                            fullTextFieldValue.selection.start
                                                .coerceIn(0, text.length)
                                        val se =
                                            fullTextFieldValue.selection.end
                                                .coerceIn(0, text.length)
                                        et.setSelection(ss, se)
                                    } finally {
                                        nativeTextProgrammatic.set(false)
                                    }
                                }
                            }
                        },
                        onRelease = { released ->
                            fullTextNativeEditRef.compareAndSet(released, null)
                        },
                    )
                } else {
                    BasicTextField(
                        value = fullTextFieldValue,
                        readOnly = textEditorChromeViewOnly,
                        onValueChange = { v ->
                            onFullTextFieldValueChange(v)
                            onTextChange(v.text)
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(fullTextFocusRequester)
                                .padding(
                                    start = 16.dp,
                                    end = if (maxScroll > 0) 26.dp else 16.dp,
                                    top = 0.dp,
                                    bottom = 12.dp,
                                )
                                .verticalScroll(fullTextScrollState)
                                .defaultMinSize(
                                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                                    minHeight = OutlinedTextFieldDefaults.MinHeight,
                                ),
                        textStyle = fullTextStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions.Default,
                        keyboardActions = KeyboardActions.Default,
                        interactionSource = fullTextInteractionSource,
                        singleLine = false,
                        maxLines = Int.MAX_VALUE,
                        minLines = 1,
                    )
                }
            }
            if (!useNativeHugeEditor && maxScroll > 0) {
                Canvas(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(5.dp)
                            .padding(top = 36.dp, bottom = 12.dp, start = 2.dp),
                ) {
                    val trackH = size.height
                    val thumbH =
                        (trackH * trackH / (trackH + maxScroll)).coerceIn(
                            36.dp.toPx(),
                            trackH * 0.92f,
                        )
                    val yRange = (trackH - thumbH).coerceAtLeast(0f)
                    val y =
                        if (maxScroll > 0) {
                            (scrollValue / maxScroll.toFloat()) * yRange
                        } else {
                            0f
                        }
                    drawRoundRect(
                        color = scrollbarColor,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, thumbH),
                        cornerRadius = CornerRadius(size.width / 2f, size.width / 2f),
                    )
                }
            }
        }
    }
}
