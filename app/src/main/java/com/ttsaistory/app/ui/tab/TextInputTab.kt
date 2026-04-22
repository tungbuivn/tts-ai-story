package com.ttsaistory.app.ui.tab

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.ttsaistory.app.AnrDiagLog
import com.ttsaistory.app.data.LibraryCategoryRow
import com.ttsaistory.app.data.LibraryStoryRow
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.elevenlabs.ElevenLabsPrefKeys
import com.ttsaistory.app.ui.fonts.editorLineHeightSp
import com.ttsaistory.app.ui.fonts.rememberTextInputTabEditorAppearance
import com.ttsaistory.app.domain.canonicalTextFromRaw
import com.ttsaistory.app.domain.charOffsetForEditorFlatCellInMerged
import com.ttsaistory.app.domain.editorUiFlatForTtsParagraphStartIndexForFlatCells
import com.ttsaistory.app.domain.editorUiFlatToTtsParagraphStartIndex
import com.ttsaistory.app.export.TtsAudioExportForegroundService
import com.ttsaistory.app.export.TtsExportUiCoordinator
import com.ttsaistory.app.domain.flatIndexFromMainSub
import com.ttsaistory.app.domain.flatIndexToMainSub
import com.ttsaistory.app.domain.flatIndexToMainSubPairs
import com.ttsaistory.app.domain.hasExportableText
import com.ttsaistory.app.domain.hasSpeakableParagraphFrom
import com.ttsaistory.app.domain.mergeMainParagraphGroups
import com.ttsaistory.app.domain.mergeParagraphs
import com.ttsaistory.app.domain.paragraphIndexAtTextOffset
import com.ttsaistory.app.domain.paragraphMainGroupsForEditor
import com.ttsaistory.app.domain.paragraphsForEditor
import com.ttsaistory.app.domain.segmentStartOffsetsInRaw
import com.ttsaistory.app.domain.sanitizeParagraphText
import com.ttsaistory.app.domain.splitIntoParagraphs
import com.ttsaistory.app.domain.textOffsetAtParagraphStart
import com.ttsaistory.app.domain.ttsParagraphStartIndexForEachFlatCell
import com.ttsaistory.app.model.AppEditorConstants
import com.ttsaistory.app.model.AppPreferenceKeys
import com.ttsaistory.app.model.TextTabSpeechEngine
import com.ttsaistory.app.model.saveLastText
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TextInputTab(
    modifier: Modifier = Modifier,
    prefs: SharedPreferences,
    text: String,
    onTextChange: (String) -> Unit,
    tts: TextToSpeech?,
    ttsReady: Boolean,
    speechEngine: TextTabSpeechEngine,
    onSpeechEngineChange: (TextTabSpeechEngine) -> Unit,
    speechEngineReady: Boolean,
    elevenLabsJobActive: Boolean,
    /** true khi TTS hệ thống còn utterance trong loạt đọc — không dùng [TextToSpeech.isSpeaking] (dễ lệch / không recompose). */
    systemTtsPlaybackActive: Boolean,
    onStopAllSpeechReading: () -> Unit,
    onPlayParagraphs: (List<String>, Int) -> Unit,
    speakingParagraphIndex: Int,
    bookmarkResetKey: Any,
    libraryRepository: StoryLibraryRepository,
    activeLibraryStoryId: Long?,
    librarySyncEpoch: Int,
    onLibraryFileSynced: () -> Unit,
    onLibraryDataChanged: () -> Unit,
    onSavedLibraryStory: (Long) -> Unit,
    /** Mở một truyện thư viện khác (đồng bộ tab Text / thư viện). */
    onOpenLibraryStory: (Long) -> Unit,
    /** Đăng ký hàm flush bản nháp đoạn lên [text] trước khi app nhận share / đổi truyện thư viện. */
    onRegisterParagraphDraftFlush: ((() -> Unit) -> Unit)? = null,
    /** Đăng ký hành động bottom bar (cuộn đầu/cuối / con trỏ đầu cuối); null khi huỷ đăng ký. */
    onRegisterTextTabBottomNav: ((TextTabBottomNavBridge?) -> Unit)? = null,
    systemTtsSpeechRate: Float,
    systemTtsPitch: Float,
) {
    var paragraphSplitMode by rememberSaveable { mutableStateOf(true) }
    var focusedParagraphIndex by remember { mutableIntStateOf(0) }
    var pendingFocusFlatIndex by remember { mutableIntStateOf(-1) }
    var paragraphFocusRequestToken by remember { mutableIntStateOf(0) }
    var fullTextFieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var segments by remember { mutableStateOf(emptyList<String>()) }
    val listState = rememberLazyListState()
    var paragraphGroupFieldValues by remember {
        mutableStateOf(
            listOf(listOf(TextFieldValue("", TextRange(0)))),
        )
    }
    val flatItemCount = paragraphGroupFieldValues.sumOf { it.size }
    val mergedForPlayToolbar =
        remember(paragraphGroupFieldValues, paragraphSplitMode, text) {
            if (paragraphSplitMode) {
                mergeMainParagraphGroups(
                    paragraphGroupFieldValues.map { r -> r.map { it.text } },
                )
            } else {
                text
            }
        }
    var playToolbarParagraphsDebounced by remember { mutableStateOf(emptyList<String>()) }
    var toolbarTtsSpeakableCount by remember { mutableStateOf<Int?>(null) }
    var toolbarTtsSplitWorking by remember { mutableStateOf(false) }
    var prevParagraphSplitMode by remember { mutableStateOf<Boolean?>(null) }
    val didScrollToSavedBookmark =
        remember(paragraphSplitMode, bookmarkResetKey, librarySyncEpoch) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textTabToolbarScrollState = rememberScrollState()
    val fullTextScrollState = rememberScrollState()
    val fullTextFocusRequester = remember { FocusRequester() }
    val fullTextNativeEditRef = remember { java.util.concurrent.atomic.AtomicReference<EditText?>(null) }
    val nativeTextProgrammatic = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var fullTextNativeFocused by remember { mutableStateOf(false) }
    var exportUiFromCoordinator by remember { mutableStateOf<TtsExportDialogState?>(null) }
    LaunchedEffect(Unit) {
        TtsExportUiCoordinator.uiState.collect { exportUiFromCoordinator = it }
    }
    val pendingPostNotifExport = remember { mutableStateOf<(() -> Unit)?>(null) }
    val postNotifLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingPostNotifExport.value?.invoke()
            pendingPostNotifExport.value = null
        }

    fun enqueueTtsExport(exportBody: String) {
        val run: () -> Unit = {
            scope.launch(Dispatchers.IO) {
                val bodyFile =
                    File(ctx.cacheDir, "tts_export_body_${System.currentTimeMillis()}.txt")
                try {
                    bodyFile.writeText(exportBody, Charsets.UTF_8)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            ctx,
                            "Lỗi ghi file tạm: ${e.message ?: e.javaClass.simpleName}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    val out = "tts_story_${System.currentTimeMillis()}.m4a"
                    val i =
                        Intent(ctx, TtsAudioExportForegroundService::class.java).apply {
                            action = TtsAudioExportForegroundService.ACTION_START
                            putExtra(
                                TtsAudioExportForegroundService.EXTRA_BODY_PATH,
                                bodyFile.absolutePath,
                            )
                            putExtra(TtsAudioExportForegroundService.EXTRA_OUTPUT_NAME, out)
                            putExtra(TtsAudioExportForegroundService.EXTRA_SPEECH_RATE, systemTtsSpeechRate)
                            putExtra(TtsAudioExportForegroundService.EXTRA_PITCH, systemTtsPitch)
                            tts?.voice?.let { v ->
                                putExtra(TtsAudioExportForegroundService.EXTRA_VOICE_NAME, v.name)
                                putExtra(
                                    TtsAudioExportForegroundService.EXTRA_VOICE_LOCALE,
                                    v.locale?.toLanguageTag(),
                                )
                            }
                        }
                    ContextCompat.startForegroundService(ctx, i)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingPostNotifExport.value = run
            postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            run()
        }
    }

    var showNewLibraryStoryDialog by remember { mutableStateOf(false) }
    var newStoryNewCategoryName by remember { mutableStateOf("") }
    var newStorySelectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var newStoryCategories by remember { mutableStateOf<List<LibraryCategoryRow>>(emptyList()) }
    var moveCategoryTarget by remember { mutableStateOf<LibraryStoryRow?>(null) }
    var moveCategoryCategories by remember { mutableStateOf<List<LibraryCategoryRow>>(emptyList()) }
    var libraryStoryPickerOpen by remember { mutableStateOf(false) }
    var libraryStoryPickerLoading by remember { mutableStateOf(false) }
    var libraryStoryPickerCategoryTitle by remember { mutableStateOf("") }
    var libraryStoryPickerStories by remember { mutableStateOf<List<LibraryStoryRow>>(emptyList()) }
    var libraryStoryPickerCategoryId by remember { mutableStateOf<Long?>(null) }
    val libraryStoryPickerLoadJob = remember { mutableStateOf<Job?>(null) }
    var moveStoryTitleDraft by remember { mutableStateOf("") }
    var textEditorChromeViewOnly by rememberSaveable { mutableStateOf(true) }
    var wasTextEditorChromeViewOnly by remember { mutableStateOf(textEditorChromeViewOnly) }

    LaunchedEffect(moveCategoryTarget?.id) {
        moveStoryTitleDraft = moveCategoryTarget?.title.orEmpty()
    }

    LaunchedEffect(textEditorChromeViewOnly) {
        if (textEditorChromeViewOnly) {
            keyboardController?.hide()
        }
    }

    fun openLibraryStoryPickerFromToolbar() {
        keyboardController?.hide()
        if (exportUiFromCoordinator != null) return
        libraryStoryPickerLoadJob.value?.cancel()
        libraryStoryPickerOpen = true
        libraryStoryPickerLoading = true
        libraryStoryPickerStories = emptyList()
        libraryStoryPickerCategoryId = null
        libraryStoryPickerCategoryTitle = ""
        libraryStoryPickerLoadJob.value =
            scope.launch {
                val triple =
                    withContext(Dispatchers.IO) {
                        val sid = activeLibraryStoryId
                        if (sid != null) {
                            val row = libraryRepository.getStory(sid)
                            if (row != null) {
                                val cats = libraryRepository.listCategories()
                                val cat = cats.find { it.id == row.categoryId }
                                if (cat != null) {
                                    return@withContext Triple(
                                        row.categoryId,
                                        cat.name,
                                        libraryRepository.listStories(row.categoryId),
                                    )
                                }
                            }
                        }
                        val cats = libraryRepository.listCategories()
                        val first = cats.firstOrNull() ?: return@withContext null
                        Triple(
                            first.id,
                            first.name,
                            libraryRepository.listStories(first.id),
                        )
                    }
                withContext(Dispatchers.Main) {
                    if (!libraryStoryPickerOpen) return@withContext
                    if (triple == null) {
                        Toast.makeText(
                            ctx,
                            "Chưa có thể loại trong thư viện.",
                            Toast.LENGTH_LONG,
                        ).show()
                        libraryStoryPickerOpen = false
                        libraryStoryPickerLoading = false
                        return@withContext
                    }
                    val (cid, cname, stories) = triple
                    libraryStoryPickerCategoryTitle = cname
                    libraryStoryPickerCategoryId = cid
                    libraryStoryPickerStories = stories
                    libraryStoryPickerLoading = false
                }
            }
    }

    val persistDebouncer =
        remember {
            object {
                var job: Job? = null
            }
        }

    val prefsBridge = rememberTextInputTabPrefsBridge(prefs)
    val editorAppearance = rememberTextInputTabEditorAppearance(prefs, prefsBridge.fontPrefsEpoch)
    LaunchedEffect(bookmarkResetKey, librarySyncEpoch) {
        prefsBridge.refreshBookmarkFromPrefs()
    }

    val latestParagraphSplit by rememberUpdatedState(paragraphSplitMode)
    val latestParagraphFieldGroups by rememberUpdatedState(paragraphGroupFieldValues)
    val latestOnTextChange by rememberUpdatedState(onTextChange)
    val latestParentText by rememberUpdatedState(text)
    val fullTextNativeTypingSink = remember { mutableStateOf<(String) -> Unit>({}) }
    SideEffect {
        fullTextNativeTypingSink.value = { s -> latestOnTextChange(s) }
    }

    fun mergedParagraphFields(): String =
        mergeMainParagraphGroups(paragraphGroupFieldValues.map { r -> r.map { it.text } })

    fun scheduleDebouncedParagraphParentPersist() {
        persistDebouncer.job?.cancel()
        persistDebouncer.job =
            scope.launch {
                delay(AppEditorConstants.PARAGRAPH_FIELD_PERSIST_DEBOUNCE_MS)
                val rows = paragraphGroupFieldValues.map { r -> r.map { it.text } }
                val merged =
                    withContext(Dispatchers.Default) { mergeMainParagraphGroups(rows) }
                onTextChange(merged)
                persistDebouncer.job = null
            }
    }

    fun flushParagraphParentPersist() {
        persistDebouncer.job?.cancel()
        persistDebouncer.job = null
        val merged = mergedParagraphFields()
        if (merged != text) onTextChange(merged)
    }

    fun switchToolbarToFullTextMode() {
        if (paragraphSplitMode) {
            flushParagraphParentPersist()
            val mergedNow = mergedParagraphFields()
            val groups = paragraphGroupFieldValues
            val cellCount = groups.sumOf { it.size }
            if (cellCount <= 0) {
                fullTextFieldValue =
                    TextFieldValue(mergedNow, TextRange(mergedNow.length))
            } else {
                val maxFlat = cellCount - 1
                val flat = focusedParagraphIndex.coerceIn(0, maxFlat)
                val (m, s) = flatIndexToMainSub(groups, flat)
                val tf = groups[m][s]
                val sel = tf.selection
                val lo = minOf(sel.start, sel.end).coerceIn(0, tf.text.length)
                val hi = maxOf(sel.start, sel.end).coerceIn(0, tf.text.length)
                val texts = groups.map { row -> row.map { it.text } }
                val base =
                    charOffsetForEditorFlatCellInMerged(
                        texts,
                        flat,
                    )
                val gStart = (base + lo).coerceIn(0, mergedNow.length)
                val gEnd = (base + hi).coerceIn(0, mergedNow.length)
                fullTextFieldValue = TextFieldValue(mergedNow, TextRange(gStart, gEnd))
            }
        }
        paragraphSplitMode = false
    }

    fun switchToolbarToParagraphSplitMode() {
        if (!paragraphSplitMode) {
            onTextChange(canonicalTextFromRaw(text))
        }
        paragraphSplitMode = true
    }

    LaunchedEffect(textEditorChromeViewOnly) {
        val enteredViewOnly = textEditorChromeViewOnly && !wasTextEditorChromeViewOnly
        wasTextEditorChromeViewOnly = textEditorChromeViewOnly
        if (!enteredViewOnly) return@LaunchedEffect
        if (!paragraphSplitMode) return@LaunchedEffect
        flushParagraphParentPersist()
        val snapshot = mergedParagraphFields()
        val t0 =
            AnrDiagLog.begin(
                "TextInputTab paragraphMainGroupsForEditor(leaveEditViewOnly) len=${snapshot.length}",
            )
        val segs =
            withContext(Dispatchers.Default) { paragraphMainGroupsForEditor(snapshot) }
        if (!textEditorChromeViewOnly || !paragraphSplitMode) {
            AnrDiagLog.end("TextInputTab paragraphMainGroupsForEditor(leaveEditViewOnly) CANCELLED", t0)
            return@LaunchedEffect
        }
        if (mergedParagraphFields() != snapshot) {
            AnrDiagLog.i("TextInputTab paragraphMainGroupsForEditor(leaveEditViewOnly) stale skip")
            AnrDiagLog.end("TextInputTab paragraphMainGroupsForEditor(leaveEditViewOnly) STALE", t0)
            return@LaunchedEffect
        }
        paragraphGroupFieldValues =
            segs.map { row ->
                row.map { s ->
                    TextFieldValue(text = s, selection = TextRange(s.length))
                }
            }
        prevParagraphSplitMode = true
        AnrDiagLog.end(
            "TextInputTab paragraphMainGroupsForEditor(leaveEditViewOnly) rows=${segs.size} cells=${segs.sumOf { it.size }}",
            t0,
        )
    }

    /** Đồng bộ prefs bookmark TTS khi ô UI (flat) đổi — chỉ dùng ánh xạ ô→TTS, không merge/split cả văn bản (tránh ANR). */
    fun persistLastReadingBookmarkFromEditorFlat(
        groups: List<List<String>>,
        editorUiFlat: Int,
    ) {
        if (!paragraphSplitMode || groups.isEmpty()) return
        val maxUi = (groups.sumOf { it.size } - 1).coerceAtLeast(0)
        val v = editorUiFlat.coerceIn(0, maxUi)
        val tts = editorUiFlatToTtsParagraphStartIndex(groups, v).coerceAtLeast(0)
        prefs.edit().putInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, tts).apply()
    }

    /** Như [persistLastReadingBookmarkFromEditorFlat] nhưng không tạo bản sao List<List<String>> toàn lưới (chạm ô). */
    fun persistLastReadingBookmarkFromEditorFieldFlat(
        fieldGroups: List<List<TextFieldValue>>,
        editorUiFlat: Int,
    ) {
        if (!paragraphSplitMode || fieldGroups.isEmpty()) return
        val maxUi = (fieldGroups.sumOf { it.size } - 1).coerceAtLeast(0)
        val v = editorUiFlat.coerceIn(0, maxUi)
        val tts = editorFlatToTtsBookmarkIndex(fieldGroups, v).coerceAtLeast(0)
        prefs.edit().putInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, tts).apply()
    }

    SideEffect {
        onRegisterParagraphDraftFlush?.invoke { flushParagraphParentPersist() }
    }

    val latestFullTextField by rememberUpdatedState(fullTextFieldValue)
    val latestFlatItemCount by rememberUpdatedState(flatItemCount)
    val latestSpeakingParagraphIndex by rememberUpdatedState(speakingParagraphIndex)
    val latestSystemTtsPlaybackActive by rememberUpdatedState(systemTtsPlaybackActive)
    val latestElevenLabsJobActive by rememberUpdatedState(elevenLabsJobActive)
    val latestOnPlayParagraphs by rememberUpdatedState(onPlayParagraphs)

    fun goTopOrCaretStartAction() {
        if (paragraphSplitMode) {
            scope.launch { listState.scrollToItem(0) }
        } else {
            val et = fullTextNativeEditRef.get()
            if (et != null &&
                latestParentText.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS
            ) {
                nativeTextProgrammatic.set(true)
                et.setSelection(0, 0)
                et.scrollTo(0, 0)
                nativeTextProgrammatic.set(false)
                fullTextFieldValue =
                    TextFieldValue(et.text.toString(), TextRange(0))
            } else {
                val t = latestFullTextField.text
                fullTextFieldValue = TextFieldValue(t, TextRange(0))
                scope.launch { fullTextScrollState.scrollTo(0) }
            }
        }
    }

    fun goBottomOrCaretEndAction() {
        if (paragraphSplitMode) {
            val n = latestFlatItemCount
            if (n > 0) {
                scope.launch { listState.scrollToItem(n - 1) }
            }
        } else {
            val et = fullTextNativeEditRef.get()
            if (et != null &&
                latestParentText.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS
            ) {
                val t = et.text.toString()
                val len = t.length
                nativeTextProgrammatic.set(true)
                et.setSelection(len, len)
                et.post {
                    try {
                        if (len <= 0) {
                            et.scrollTo(0, 0)
                        } else {
                            val layout = et.layout
                            if (layout != null) {
                                val line =
                                    layout.getLineForOffset((len - 1).coerceAtLeast(0))
                                val y = layout.getLineTop(line).coerceAtLeast(0)
                                et.scrollTo(0, y)
                            }
                        }
                    } finally {
                        nativeTextProgrammatic.set(false)
                    }
                }
                fullTextFieldValue = TextFieldValue(t, TextRange(len))
            } else {
                val t = latestFullTextField.text
                val len = t.length
                fullTextFieldValue = TextFieldValue(t, TextRange(len))
                scope.launch {
                    fullTextScrollState.scrollTo(fullTextScrollState.maxValue)
                }
            }
        }
    }

    SideEffect {
        onRegisterTextTabBottomNav?.invoke(
            TextTabBottomNavBridge(
                paragraphSplitMode = paragraphSplitMode,
                showPasteAndCaretStep = !textEditorChromeViewOnly,
                showParagraphFocusSlider = paragraphSplitMode && flatItemCount > 0,
                paragraphFocusSliderMax = (flatItemCount - 1).coerceAtLeast(0),
                paragraphFocusSliderValue =
                    focusedParagraphIndex.coerceIn(0, (flatItemCount - 1).coerceAtLeast(0)),
                onParagraphFocusSliderChange = { newUiFlat ->
                    val gl = latestParagraphFieldGroups
                    val maxUi = (gl.sumOf { it.size } - 1).coerceAtLeast(0)
                    val v = newUiFlat.coerceIn(0, maxUi)
                    focusedParagraphIndex = v
                    pendingFocusFlatIndex = v
                    paragraphFocusRequestToken++
                    persistLastReadingBookmarkFromEditorFieldFlat(gl, v)
                },
                onParagraphFocusSliderFocusCommitted = {
                    val gl = latestParagraphFieldGroups
                    val maxUi = (gl.sumOf { it.size } - 1).coerceAtLeast(0)
                    val idx = focusedParagraphIndex.coerceIn(0, maxUi)
                    val wasPlaying =
                        latestSpeakingParagraphIndex >= 0 ||
                            latestSystemTtsPlaybackActive ||
                            latestElevenLabsJobActive
                    if (paragraphSplitMode && wasPlaying) {
                        val groups = gl.map { r -> r.map { it.text } }
                        val merged = mergeMainParagraphGroups(groups)
                        val paras = splitIntoParagraphs(merged)
                        val startTts =
                            editorUiFlatToTtsParagraphStartIndex(groups, idx).coerceIn(
                                0,
                                (paras.size - 1).coerceAtLeast(0),
                            )
                        if (hasSpeakableParagraphFrom(paras, startTts)) {
                            latestOnPlayParagraphs(paras, startTts)
                        }
                    }
                    scope.launch {
                        try {
                            val total = listState.layoutInfo.totalItemsCount
                            if (total > 0) {
                                listState.scrollToItem(idx.coerceIn(0, total - 1))
                            }
                        } catch (_: IllegalArgumentException) {
                        } catch (e: CancellationException) {
                            throw e
                        }
                    }
                },
                readerProgressCurrentOneBased =
                    if (paragraphSplitMode && flatItemCount > 0) {
                        val groups =
                            paragraphGroupFieldValues.map { row -> row.map { it.text } }
                        val ui =
                            focusedParagraphIndex.coerceIn(
                                0,
                                (flatItemCount - 1).coerceAtLeast(0),
                            )
                        editorUiFlatToTtsParagraphStartIndex(groups, ui) + 1
                    } else {
                        null
                    },
                pasteFromClipboard = {
                    if (textEditorChromeViewOnly) {
                        Toast.makeText(
                            ctx,
                            "Bật chỉnh sửa (icon bút) để dán hoặc sửa nội dung.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        val cm =
                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip
                        if (clip == null || clip.itemCount <= 0) {
                            Toast.makeText(ctx, "Clipboard trống", Toast.LENGTH_SHORT).show()
                        } else {
                            val pasted = clip.getItemAt(0).coerceToText(ctx).toString()
                            if (pasted.isEmpty()) {
                                Toast.makeText(ctx, "Clipboard trống", Toast.LENGTH_SHORT).show()
                            } else if (paragraphSplitMode) {
                            val gl =
                                paragraphGroupFieldValues
                                    .map { it.toMutableList() }
                                    .toMutableList()
                            if (gl.isNotEmpty()) {
                                val maxFlat = (gl.sumOf { it.size } - 1).coerceAtLeast(0)
                                val flat = focusedParagraphIndex.coerceIn(0, maxFlat)
                                val (m, s) = flatIndexToMainSub(gl, flat)
                                if (m in gl.indices && s in gl[m].indices) {
                                    val cur = gl[m][s]
                                    val sel = cur.selection
                                    val a = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
                                    val b = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
                                    val newText =
                                        cur.text.substring(0, a) + pasted + cur.text.substring(b)
                                    val caret = a + pasted.length
                                    gl[m][s] = TextFieldValue(newText, TextRange(caret))
                                    paragraphGroupFieldValues =
                                        compactParagraphGroupFieldValues(gl)
                                    val newMax =
                                        (paragraphGroupFieldValues.sumOf { it.size } - 1)
                                            .coerceAtLeast(0)
                                    focusedParagraphIndex =
                                        focusedParagraphIndex.coerceIn(0, newMax)
                                    scheduleDebouncedParagraphParentPersist()
                                }
                            }
                        } else {
                            val et = fullTextNativeEditRef.get()
                            if (et != null &&
                                latestParentText.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS
                            ) {
                                val cur = et.text.toString()
                                val a =
                                    minOf(et.selectionStart, et.selectionEnd).coerceIn(0, cur.length)
                                val b =
                                    maxOf(et.selectionStart, et.selectionEnd).coerceIn(0, cur.length)
                                val newText = cur.substring(0, a) + pasted + cur.substring(b)
                                val caret = a + pasted.length
                                nativeTextProgrammatic.set(true)
                                et.setText(newText)
                                et.setSelection(caret, caret)
                                nativeTextProgrammatic.set(false)
                                fullTextFieldValue = TextFieldValue(newText, TextRange(caret))
                                latestOnTextChange(newText)
                            } else {
                                val cur = latestFullTextField
                                val sel = cur.selection
                                val a = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
                                val b = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
                                val newText = cur.text.substring(0, a) + pasted + cur.text.substring(b)
                                val caret = a + pasted.length
                                fullTextFieldValue = TextFieldValue(newText, TextRange(caret))
                                onTextChange(newText)
                            }
                        }
                    }
                    }
                },
                moveCaretLeft = {
                    if (paragraphSplitMode) {
                        val gl =
                            paragraphGroupFieldValues
                                .map { it.toMutableList() }
                                .toMutableList()
                        if (gl.isNotEmpty()) {
                            val maxFlat = (gl.sumOf { it.size } - 1).coerceAtLeast(0)
                            val flat = focusedParagraphIndex.coerceIn(0, maxFlat)
                            val (m, s) = flatIndexToMainSub(gl, flat)
                            if (m in gl.indices && s in gl[m].indices) {
                                val cur = gl[m][s]
                                gl[m][s] = moveCaretLeftInField(cur)
                                paragraphGroupFieldValues =
                                    compactParagraphGroupFieldValues(gl)
                                scheduleDebouncedParagraphParentPersist()
                            }
                        }
                    } else {
                        val et = fullTextNativeEditRef.get()
                        if (et != null &&
                            latestParentText.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS
                        ) {
                            val tv =
                                TextFieldValue(
                                    et.text.toString(),
                                    TextRange(et.selectionStart, et.selectionEnd),
                                )
                            val moved = moveCaretLeftInField(tv)
                            nativeTextProgrammatic.set(true)
                            et.setSelection(moved.selection.start, moved.selection.end)
                            nativeTextProgrammatic.set(false)
                            fullTextFieldValue = moved
                        } else {
                            fullTextFieldValue = moveCaretLeftInField(latestFullTextField)
                        }
                    }
                },
                moveCaretRight = {
                    if (paragraphSplitMode) {
                        val gl =
                            paragraphGroupFieldValues
                                .map { it.toMutableList() }
                                .toMutableList()
                        if (gl.isNotEmpty()) {
                            val maxFlat = (gl.sumOf { it.size } - 1).coerceAtLeast(0)
                            val flat = focusedParagraphIndex.coerceIn(0, maxFlat)
                            val (m, s) = flatIndexToMainSub(gl, flat)
                            if (m in gl.indices && s in gl[m].indices) {
                                val cur = gl[m][s]
                                gl[m][s] = moveCaretRightInField(cur)
                                paragraphGroupFieldValues =
                                    compactParagraphGroupFieldValues(gl)
                                scheduleDebouncedParagraphParentPersist()
                            }
                        }
                    } else {
                        val et = fullTextNativeEditRef.get()
                        if (et != null &&
                            latestParentText.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS
                        ) {
                            val tv =
                                TextFieldValue(
                                    et.text.toString(),
                                    TextRange(et.selectionStart, et.selectionEnd),
                                )
                            val moved = moveCaretRightInField(tv)
                            nativeTextProgrammatic.set(true)
                            et.setSelection(moved.selection.start, moved.selection.end)
                            nativeTextProgrammatic.set(false)
                            fullTextFieldValue = moved
                        } else {
                            fullTextFieldValue = moveCaretRightInField(latestFullTextField)
                        }
                    }
                },
                goTopOrCaretStart = { goTopOrCaretStartAction() },
                goBottomOrCaretEnd = { goBottomOrCaretEndAction() },
                ttsSpeakableSentenceTotal = toolbarTtsSpeakableCount,
                ttsSentenceSplitWorking = toolbarTtsSplitWorking,
            ),
        )
    }
    // Không key theo onRegisterTextTabBottomNav: lambda từ AppTabs đổi mỗi recompose → onDispose gọi
    // invoke(null) làm mất bridge (slider / +/- không cập nhật).
    val latestRegisterBottomNav by rememberUpdatedState(onRegisterTextTabBottomNav)
    DisposableEffect(Unit) {
        onDispose { latestRegisterBottomNav?.invoke(null) }
    }

    DisposableEffect(Unit) {
        onDispose {
            persistDebouncer.job?.cancel()
            persistDebouncer.job = null
            if (latestParagraphSplit) {
                val merged =
                    mergeMainParagraphGroups(
                        latestParagraphFieldGroups.map { r -> r.map { tf -> tf.text } },
                    )
                if (merged != latestParentText) {
                    latestOnTextChange(merged)
                }
            }
        }
    }

    LaunchedEffect(text, paragraphSplitMode) {
        if (paragraphSplitMode) return@LaunchedEffect
        if (fullTextFieldValue.text == text) return@LaunchedEffect
        val et = fullTextNativeEditRef.get()
        if (et != null && text.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS) {
            fullTextFieldValue =
                TextFieldValue(
                    text,
                    TextRange(
                        et.selectionStart.coerceIn(0, text.length),
                        et.selectionEnd.coerceIn(0, text.length),
                    ),
                )
            return@LaunchedEffect
        }
        val sel = fullTextFieldValue.selection
        val end = text.length
        val start = sel.start.coerceIn(0, end)
        val a = sel.end.coerceIn(0, end)
        fullTextFieldValue = TextFieldValue(text, TextRange(start, a))
    }

    LaunchedEffect(text) {
        val snapshot = text
        val t0 = AnrDiagLog.begin("TextInputTab paragraphsForEditor len=${snapshot.length}")
        val computed =
            withContext(Dispatchers.Default) { paragraphsForEditor(snapshot) }
        if (snapshot == text) {
            segments = computed
            AnrDiagLog.end("TextInputTab paragraphsForEditor segs=${computed.size}", t0)
        } else {
            AnrDiagLog.i("TextInputTab paragraphsForEditor dropped (text changed)")
        }
    }

    LaunchedEffect(mergedForPlayToolbar, paragraphSplitMode) {
        val snap = mergedForPlayToolbar
        if (snap.isEmpty()) {
            playToolbarParagraphsDebounced = emptyList()
            // Lưới ô chưa khớp [text] (vừa mở truyện / đổi file): merge rỗng nhưng parent vẫn có chữ —
            // không gán 0 (bottom bar tưởng hết câu); chờ merge đầy rồi split lại.
            toolbarTtsSpeakableCount =
                if (paragraphSplitMode && latestParentText.isNotEmpty()) {
                    null
                } else {
                    0
                }
            toolbarTtsSplitWorking = false
            return@LaunchedEffect
        }
        if (snap.isNotEmpty()) delay(AppEditorConstants.PLAY_TOOLBAR_SPLIT_DEBOUNCE_MS)
        if (snap != mergedForPlayToolbar) return@LaunchedEffect
        // Bỏ tổng cũ trước khi bật working — tránh bottom bar tưởng split xong (còn số cũ) trong khi dialog vẫn "đang tách".
        toolbarTtsSpeakableCount = null
        toolbarTtsSplitWorking = true
        val t0 = AnrDiagLog.begin("TextInputTab splitIntoParagraphs(mergedToolbar) len=${snap.length}")
        try {
            val paras =
                withContext(Dispatchers.Default) { splitIntoParagraphs(snap) }
            if (snap != mergedForPlayToolbar) {
                AnrDiagLog.i("TextInputTab splitIntoParagraphs(mergedToolbar) dropped (merged changed)")
                return@LaunchedEffect
            }
            playToolbarParagraphsDebounced = paras
            toolbarTtsSpeakableCount =
                paras.count { sanitizeParagraphText(it).isNotEmpty() }
            AnrDiagLog.end(
                "TextInputTab splitIntoParagraphs(mergedToolbar) n=${paras.size}",
                t0,
            )
        } finally {
            toolbarTtsSplitWorking = false
        }
    }

    LaunchedEffect(librarySyncEpoch) {
        if (librarySyncEpoch <= 0) return@LaunchedEffect
        toolbarTtsSplitWorking = false
        toolbarTtsSpeakableCount = null
        if (!paragraphSplitMode) return@LaunchedEffect
        persistDebouncer.job?.cancel()
        persistDebouncer.job = null
        val snapshot = text
        val t0 =
            AnrDiagLog.begin(
                "TextInputTab paragraphMainGroupsForEditor(librarySyncEpoch=$librarySyncEpoch) len=${snapshot.length}",
            )
        val segs =
            withContext(Dispatchers.Default) { paragraphMainGroupsForEditor(snapshot) }
        if (snapshot != text || !paragraphSplitMode) {
            AnrDiagLog.end("TextInputTab paragraphMainGroupsForEditor libSync CANCELLED", t0)
            return@LaunchedEffect
        }
        AnrDiagLog.end(
            "TextInputTab paragraphMainGroupsForEditor libSync rows=${segs.size} cells=${segs.sumOf { it.size }}",
            t0,
        )
        paragraphGroupFieldValues =
            segs.map { row ->
                row.map { s ->
                    TextFieldValue(text = s, selection = TextRange(s.length))
                }
            }
        prevParagraphSplitMode = true
    }

    LaunchedEffect(paragraphSplitMode, text) {
        if (!paragraphSplitMode) {
            prevParagraphSplitMode = false
            return@LaunchedEffect
        }
        persistDebouncer.job?.cancel()
        persistDebouncer.job = null
        val snapshot = text
        val tParse =
            AnrDiagLog.begin(
                "TextInputTab paragraphMainGroupsForEditor(splitMode+text) len=${snapshot.length}",
            )
        val segs =
            withContext(Dispatchers.Default) { paragraphMainGroupsForEditor(snapshot) }
        if (snapshot != text || !paragraphSplitMode) {
            AnrDiagLog.end("TextInputTab paragraphMainGroupsForEditor(splitMode+text) CANCELLED", tParse)
            return@LaunchedEffect
        }
        AnrDiagLog.end(
            "TextInputTab paragraphMainGroupsForEditor(splitMode+text) rows=${segs.size}",
            tParse,
        )
        if (prevParagraphSplitMode != true) {
            paragraphGroupFieldValues =
                segs.map { row ->
                    row.map { s ->
                        TextFieldValue(text = s, selection = TextRange(s.length))
                    }
                }
            prevParagraphSplitMode = true
            return@LaunchedEffect
        }
        val cellCount = paragraphGroupFieldValues.sumOf { it.size }
        val tMap = AnrDiagLog.begin("TextInputTab fieldRows.map MAIN cells=$cellCount")
        val fieldRows = paragraphGroupFieldValues.map { row -> row.map { it.text } }
        AnrDiagLog.end("TextInputTab fieldRows.map MAIN", tMap)
        val tMerge = AnrDiagLog.begin("TextInputTab mergeMainParagraphGroups(Default)")
        val merged =
            withContext(Dispatchers.Default) { mergeMainParagraphGroups(fieldRows) }
        AnrDiagLog.end("TextInputTab mergeMainParagraphGroups(Default) mergedLen=${merged.length}", tMerge)
        if (snapshot != text || !paragraphSplitMode) return@LaunchedEffect
        if (merged != text) {
            paragraphGroupFieldValues =
                segs.map { row ->
                    row.map { s ->
                        TextFieldValue(text = s, selection = TextRange(s.length))
                    }
                }
        }
    }

    LaunchedEffect(
        paragraphSplitMode,
        segments.size,
        bookmarkResetKey,
        librarySyncEpoch,
    ) {
        if (!paragraphSplitMode) return@LaunchedEffect
        if (didScrollToSavedBookmark.value) return@LaunchedEffect
        if (segments.isEmpty()) return@LaunchedEffect
        delay(48)
        val cellCount = paragraphGroupFieldValues.sumOf { it.size }
        if (cellCount <= 0) return@LaunchedEffect
        val saved = prefs.getInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, -1)
        if (saved < 0) {
            didScrollToSavedBookmark.value = true
            return@LaunchedEffect
        }
        val maxFlat = (cellCount - 1).coerceAtLeast(0)
        val ui =
            withContext(Dispatchers.Default) {
                val cells = paragraphGroupFieldValues.flatMap { r -> r.map { it.text } }
                editorUiFlatForTtsParagraphStartIndexForFlatCells(cells, saved)
                    .coerceIn(0, maxFlat)
            }
        focusedParagraphIndex = ui
        pendingFocusFlatIndex = ui
        paragraphFocusRequestToken++
        try {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                listState.scrollToItem(ui.coerceIn(0, total - 1))
            }
        } catch (_: IllegalArgumentException) {
        } catch (e: CancellationException) {
            throw e
        }
        didScrollToSavedBookmark.value = true
    }

    // [speakingParagraphIndex] là tham số từ parent — snapshotFlow không theo dõi được → slider không
    // theo câu đang phát; dùng LaunchedEffect + key để mỗi lần đổi câu TTS đều cập nhật slider/focus.
    LaunchedEffect(
        paragraphSplitMode,
        flatItemCount,
        speakingParagraphIndex,
        prefsBridge.trackedLastReadingParagraphIndex,
    ) {
        if (!paragraphSplitMode || flatItemCount <= 0) return@LaunchedEffect
        val sp = speakingParagraphIndex
        val saved = prefsBridge.trackedLastReadingParagraphIndex
        val gl = latestParagraphFieldGroups
        val maxFlat = (flatItemCount - 1).coerceAtLeast(0)
        when {
            sp >= 0 -> {
                val idx =
                    withContext(Dispatchers.Default) {
                        val cells = gl.flatMap { row -> row.map { it.text } }
                        editorUiFlatForTtsParagraphStartIndexForFlatCells(cells, sp)
                            .coerceIn(0, maxFlat)
                    }
                focusedParagraphIndex = idx
                pendingFocusFlatIndex = idx
                paragraphFocusRequestToken++
                try {
                    listState.animateScrollToItem(idx)
                } catch (_: IllegalArgumentException) {
                } catch (e: CancellationException) {
                    throw e
                }
            }
            saved < 0 -> {
                focusedParagraphIndex = 0
                pendingFocusFlatIndex = 0
                paragraphFocusRequestToken++
            }
            else -> {
                val idx =
                    withContext(Dispatchers.Default) {
                        val cells = gl.flatMap { row -> row.map { it.text } }
                        editorUiFlatForTtsParagraphStartIndexForFlatCells(cells, saved)
                            .coerceIn(0, maxFlat)
                    }
                focusedParagraphIndex = idx
                pendingFocusFlatIndex = idx
                paragraphFocusRequestToken++
                try {
                    listState.animateScrollToItem(idx)
                } catch (_: IllegalArgumentException) {
                } catch (e: CancellationException) {
                    throw e
                }
            }
        }
    }

    LaunchedEffect(paragraphSplitMode, textEditorChromeViewOnly) {
        if (paragraphSplitMode || textEditorChromeViewOnly) return@LaunchedEffect
        delay(16)
        fullTextFocusRequester.requestFocus()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(textTabToolbarScrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextInputTabSpeechEngineRow(
                speechEngine = speechEngine,
                onSpeechEngineChange = onSpeechEngineChange,
                engineControlsEnabled = exportUiFromCoordinator == null,
                libraryStoryPickerEnabled = exportUiFromCoordinator == null,
                onOpenLibraryStoryPicker = ::openLibraryStoryPickerFromToolbar,
            )
            TextInputTabToolbarActionsColumn(
                modifier = Modifier.fillMaxWidth(),
                textEditorChromeViewOnly = textEditorChromeViewOnly,
                onToggleEditorChromeViewOnly = {
                    keyboardController?.hide()
                    textEditorChromeViewOnly = !textEditorChromeViewOnly
                },
                onPlayParagraphsClick = {
                    keyboardController?.hide()
                    if (paragraphSplitMode) flushParagraphParentPersist()
                    val src =
                        fullTextBlockBodyForToolbar(
                            paragraphSplitMode,
                            mergedParagraphFields(),
                            text,
                        )
                    val paras = splitIntoParagraphs(src)
                    val bookmark =
                        prefs.getInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, -1)
                    val maxP = (paras.size - 1).coerceAtLeast(0)
                    val resumeIdx =
                        if (bookmark >= 0) {
                            bookmark.coerceIn(0, maxP)
                        } else {
                            0
                        }
                    onPlayParagraphs(paras, resumeIdx)
                },
                playParagraphsEnabled =
                    exportUiFromCoordinator == null &&
                        speakingParagraphIndex < 0 &&
                        !(
                            speechEngine == TextTabSpeechEngine.System &&
                                systemTtsPlaybackActive
                        ) &&
                        !(speechEngine == TextTabSpeechEngine.ElevenLabs && elevenLabsJobActive),
                onStopSpeechClick = { onStopAllSpeechReading() },
                stopSpeechEnabled =
                    (tts != null && ttsReady) ||
                        elevenLabsJobActive ||
                        speakingParagraphIndex >= 0,
                onExportM4aClick = {
                    if (paragraphSplitMode) flushParagraphParentPersist()
                    val exportBody =
                        if (paragraphSplitMode) mergedParagraphFields() else text
                    enqueueTtsExport(exportBody)
                },
                exportM4aEnabled =
                    exportUiFromCoordinator == null &&
                        hasExportableText(
                            if (paragraphSplitMode) mergedParagraphFields() else text,
                        ),
                onMoveStoryCategoryClick = {
                    keyboardController?.hide()
                    val sid = activeLibraryStoryId
                    if (sid != null) {
                        scope.launch {
                            val row =
                                withContext(Dispatchers.IO) { libraryRepository.getStory(sid) }
                            if (row == null) {
                                Toast.makeText(
                                    ctx,
                                    "Không tìm thấy truyện trong thư viện",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@launch
                            }
                            moveCategoryCategories =
                                withContext(Dispatchers.IO) {
                                    libraryRepository.listCategories()
                                }
                            moveCategoryTarget = row
                        }
                    }
                },
                moveStoryCategoryEnabled =
                    activeLibraryStoryId != null &&
                        exportUiFromCoordinator == null,
                onNewLibraryStoryClick = {
                    keyboardController?.hide()
                    scope.launch {
                        val (cats, preferredCategoryId) =
                            withContext(Dispatchers.IO) {
                                val list = libraryRepository.listCategories()
                                val preferred =
                                    activeLibraryStoryId?.let { sid ->
                                        libraryRepository.getStory(sid)?.categoryId
                                    }
                                list to preferred
                            }
                        newStoryCategories = cats
                        newStorySelectedCategoryId =
                            when {
                                preferredCategoryId != null &&
                                    cats.any { it.id == preferredCategoryId } ->
                                    preferredCategoryId
                                else -> cats.firstOrNull()?.id
                            }
                        newStoryNewCategoryName = ""
                        showNewLibraryStoryDialog = true
                    }
                },
                newLibraryStoryEnabled = exportUiFromCoordinator == null,
                paragraphSplitMode = paragraphSplitMode,
                onSwitchToFullTextMode = ::switchToolbarToFullTextMode,
                onSwitchToParagraphSplitMode = ::switchToolbarToParagraphSplitMode,
            )
        }
        exportUiFromCoordinator?.let { exportUi ->
            DialogExportM4AAudio(
                exportUi = exportUi,
                onCancelExport = {
                    ctx.startService(
                        Intent(ctx, TtsAudioExportForegroundService::class.java).apply {
                            action = TtsAudioExportForegroundService.ACTION_CANCEL
                        },
                    )
                },
            )
        }
        DialogStoryPicker(
            visible = libraryStoryPickerOpen,
            onDismissRequest = {
                libraryStoryPickerLoadJob.value?.cancel()
                libraryStoryPickerOpen = false
                libraryStoryPickerCategoryId = null
            },
            categoryTitle = libraryStoryPickerCategoryTitle,
            loading = libraryStoryPickerLoading,
            stories = libraryStoryPickerStories,
            categoryId = libraryStoryPickerCategoryId,
            currentStoryId = activeLibraryStoryId,
            onStorySelected = { id ->
                if (paragraphSplitMode) flushParagraphParentPersist()
                libraryStoryPickerOpen = false
                libraryStoryPickerCategoryId = null
                onOpenLibraryStory(id)
            },
            onMoveStoryOrder = { storyId, delta ->
                scope.launch {
                    val cid = libraryStoryPickerCategoryId ?: return@launch
                    try {
                        withContext(Dispatchers.IO) {
                            libraryRepository.moveStoryOrderInCategory(storyId, cid, delta)
                        }
                        libraryStoryPickerStories =
                            withContext(Dispatchers.IO) {
                                libraryRepository.listStories(cid)
                            }
                        onLibraryDataChanged()
                    } catch (e: Exception) {
                        Toast.makeText(
                            ctx,
                            e.message ?: "Không đổi được thứ tự",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
        if (showNewLibraryStoryDialog) {
            DialogTextTabNewLibraryStory(
                categories = newStoryCategories,
                newCategoryNameDraft = newStoryNewCategoryName,
                onNewCategoryNameDraftChange = { newStoryNewCategoryName = it },
                selectedCategoryId = newStorySelectedCategoryId,
                onSelectedCategoryIdChange = { newStorySelectedCategoryId = it },
                onDismissRequest = { showNewLibraryStoryDialog = false },
                onConfirmCreateClick = {
                    scope.launch {
                        try {
                            val catId =
                                if (newStoryNewCategoryName.isNotBlank()) {
                                    withContext(Dispatchers.IO) {
                                        libraryRepository.insertCategory(
                                            newStoryNewCategoryName.trim(),
                                        )
                                    }
                                } else {
                                    newStorySelectedCategoryId
                                        ?: run {
                                            Toast.makeText(
                                                ctx,
                                                "Chọn hoặc tạo thể loại",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            return@launch
                                        }
                                }
                            val title =
                                withContext(Dispatchers.IO) {
                                    val n =
                                        libraryRepository.nextUntitledInboundStorySuffix(
                                            catId,
                                        )
                                    "không tên $n"
                                }
                            val newId =
                                withContext(Dispatchers.IO) {
                                    libraryRepository.insertStory(catId, title, "")
                                }
                            prefs
                                .edit()
                                .putInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, -1)
                                .commit()
                            onSavedLibraryStory(newId)
                            onTextChange(canonicalTextFromRaw(""))
                            onLibraryDataChanged()
                            Toast.makeText(
                                ctx,
                                "Đã tạo: $title",
                                Toast.LENGTH_SHORT,
                            ).show()
                            showNewLibraryStoryDialog = false
                        } catch (e: Exception) {
                            Toast.makeText(
                                ctx,
                                e.message ?: "Lỗi tạo truyện",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )
        }
        moveCategoryTarget?.let { st ->
            DialogTextTabMoveStoryCategory(
                story = st,
                moveCategoryCategories = moveCategoryCategories,
                moveStoryTitleDraft = moveStoryTitleDraft,
                onMoveStoryTitleDraftChange = { moveStoryTitleDraft = it },
                onDismissRequest = { moveCategoryTarget = null },
                onSaveTitleClick = {
                    launchRenameStoryInMoveCategoryDialog(
                        scope = scope,
                        context = ctx,
                        storyId = st.id,
                        titleDraft = moveStoryTitleDraft,
                        libraryRepository = libraryRepository,
                        onRenamed = { moveCategoryTarget = it },
                        onLibraryDataChanged = onLibraryDataChanged,
                    )
                },
                onMoveToCategoryClick = { cat ->
                    val storyId = st.id
                    val draft = moveStoryTitleDraft
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val trimmed =
                                    draft.trim().ifEmpty { "Không tiêu đề" }
                                val row =
                                    libraryRepository.getStory(storyId)
                                        ?: error("Không tìm thấy truyện")
                                if (trimmed != row.title.trim()) {
                                    libraryRepository.renameStory(
                                        storyId,
                                        trimmed,
                                    )
                                }
                                libraryRepository.moveStoryToCategory(
                                    storyId,
                                    cat.id,
                                )
                            }
                            Toast.makeText(
                                ctx,
                                "Đã chuyển sang \"${cat.name}\"",
                                Toast.LENGTH_SHORT,
                            ).show()
                            moveCategoryTarget = null
                            onLibraryDataChanged()
                        } catch (e: Exception) {
                            Toast.makeText(
                                ctx,
                                e.message ?: "Lỗi",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )
        }
        if (paragraphSplitMode) {
            fun clampFlatFocus() {
                val maxFlat = (paragraphGroupFieldValues.sumOf { it.size } - 1).coerceAtLeast(0)
                focusedParagraphIndex = focusedParagraphIndex.coerceIn(0, maxFlat)
                if (pendingFocusFlatIndex >= 0) {
                    pendingFocusFlatIndex = pendingFocusFlatIndex.coerceIn(0, maxFlat)
                }
            }
            fun mergeParagraphBackward(atFlat: Int, requireCaretAtStart: Boolean = true): Boolean {
                if (atFlat <= 0) return false
                val gl = paragraphGroupFieldValues.map { it.toMutableList() }.toMutableList()
                if (gl.isEmpty()) return false
                val (pm, ps) = flatIndexToMainSub(gl, atFlat - 1)
                val (cm, cs) = flatIndexToMainSub(gl, atFlat)
                val cur = gl[cm][cs]
                val sel = cur.selection
                if (requireCaretAtStart && (sel.start != sel.end || sel.start != 0)) return false
                val prev = gl[pm][ps]
                val pt = prev.text
                val ct = cur.text
                val joinSpace =
                    when {
                        pt.isEmpty() || ct.isEmpty() -> ""
                        pt.last().isWhitespace() || ct.first().isWhitespace() -> ""
                        else -> " "
                    }
                val mergedText = pt + joinSpace + ct
                val caretAfterJoin = (pt + joinSpace).length
                val newSel = TextRange(caretAfterJoin, caretAfterJoin)
                if (pm == cm) {
                    val row = gl[pm].toMutableList()
                    row[ps] = TextFieldValue(mergedText, newSel)
                    row.removeAt(cs)
                    gl[pm] = row
                } else {
                    val rowP = gl[pm].toMutableList()
                    rowP[ps] = TextFieldValue(mergedText, newSel)
                    gl[pm] = rowP
                    val rowC = gl[cm].toMutableList()
                    rowC.removeAt(cs)
                    if (rowC.isEmpty()) gl.removeAt(cm)
                    else gl[cm] = rowC
                }
                paragraphGroupFieldValues = compactParagraphGroupFieldValues(gl)
                when {
                    focusedParagraphIndex == atFlat -> focusedParagraphIndex = atFlat - 1
                    focusedParagraphIndex > atFlat -> focusedParagraphIndex -= 1
                }
                pendingFocusFlatIndex = atFlat - 1
                paragraphFocusRequestToken++
                clampFlatFocus()
                scheduleDebouncedParagraphParentPersist()
                return true
            }
            fun splitParagraphForward(atFlat: Int): Boolean {
                val (m, s) = flatIndexToMainSub(paragraphGroupFieldValues, atFlat)
                val gl = paragraphGroupFieldValues.map { it.toMutableList() }.toMutableList()
                val cur = gl[m][s]
                val t = cur.text
                val sel = cur.selection
                val a = minOf(sel.start, sel.end)
                val b = maxOf(sel.start, sel.end)
                val before = t.substring(0, a)
                val after = t.substring(b)
                val sa = sanitizeParagraphText(after)
                val sb = sanitizeParagraphText(before)
                when {
                    sa.isEmpty() && sb.isNotEmpty() -> {
                        gl[m][s] = TextFieldValue(text = before, selection = TextRange(before.length))
                        paragraphGroupFieldValues = compactParagraphGroupFieldValues(gl)
                        clampFlatFocus()
                        scheduleDebouncedParagraphParentPersist()
                        return true
                    }
                    sb.isEmpty() && sa.isNotEmpty() -> {
                        gl[m][s] = TextFieldValue(text = after, selection = TextRange(0))
                        paragraphGroupFieldValues = compactParagraphGroupFieldValues(gl)
                        clampFlatFocus()
                        scheduleDebouncedParagraphParentPersist()
                        return true
                    }
                    sb.isEmpty() && sa.isEmpty() -> {
                        val row = gl[m].toMutableList()
                        row.removeAt(s)
                        if (row.isEmpty()) gl.removeAt(m) else gl[m] = row
                        paragraphGroupFieldValues = compactParagraphGroupFieldValues(gl)
                        clampFlatFocus()
                        scheduleDebouncedParagraphParentPersist()
                        return true
                    }
                }
                val row = gl[m].toMutableList()
                row[s] = TextFieldValue(text = before, selection = TextRange(before.length))
                val tailCells =
                    if (s < row.lastIndex) {
                        row.slice((s + 1)..row.lastIndex)
                    } else {
                        emptyList()
                    }
                if (s < row.lastIndex) {
                    row.subList(s + 1, row.size).clear()
                }
                gl[m] = row
                val newMainRow =
                    mutableListOf(TextFieldValue(text = after, selection = TextRange(0))).apply {
                        addAll(tailCells)
                    }
                gl.add(m + 1, newMainRow)
                paragraphGroupFieldValues = compactParagraphGroupFieldValues(gl)
                clampFlatFocus()
                val newMain = (m + 1).coerceAtMost(paragraphGroupFieldValues.lastIndex)
                focusedParagraphIndex =
                    flatIndexFromMainSub(paragraphGroupFieldValues, newMain, 0)
                pendingFocusFlatIndex = focusedParagraphIndex
                paragraphFocusRequestToken++
                scheduleDebouncedParagraphParentPersist()
                return true
            }
            /** Tách thành đoạn mới (xuống dòng trong file). Chỉ khi hai phần quanh con trỏ đều còn chữ sau chuẩn hoá. */
            fun splitParagraphAtCaretForToolbar(atFlat: Int): Boolean {
                val (m, s) = flatIndexToMainSub(paragraphGroupFieldValues, atFlat)
                val cur = paragraphGroupFieldValues[m][s]
                val t = cur.text
                val sel = cur.selection
                val a = minOf(sel.start, sel.end)
                val b = maxOf(sel.start, sel.end)
                val before = t.substring(0, a)
                val after = t.substring(b)
                if (sanitizeParagraphText(before).isEmpty() ||
                    sanitizeParagraphText(after).isEmpty()
                ) {
                    return false
                }
                splitParagraphForward(atFlat)
                return true
            }
            // Không dùng flatItemCount làm khóa: [1,1] và [2] cùng tổng 2 nhưng cặp (main,sub) khác —
            // stale pairs gây IndexOutOfBounds khi đọc paragraphGroupFieldValues[main][sub].
            val paragraphRowSizesFingerprint =
                paragraphGroupFieldValues.joinToString(",") { it.size.toString() }
            val flatCellTexts =
                remember(paragraphGroupFieldValues, paragraphRowSizesFingerprint) {
                    paragraphGroupFieldValues.flatMap { row -> row.map { it.text } }
                }
            val paragraphViewSplitTextStyle =
                remember(editorAppearance.editorBodyStyle, editorAppearance.paragraphEditorFontFamily, editorAppearance.editorLineSpacingMultiplier) {
                    editorAppearance.editorBodyStyle.copy(
                        lineHeight = editorLineHeightSp(editorAppearance.editorBodyStyle, editorAppearance.editorLineSpacingMultiplier),
                        fontFamily = editorAppearance.paragraphEditorFontFamily,
                    )
                }
            val flatCellTtsStart =
                remember(flatCellTexts) { ttsParagraphStartIndexForEachFlatCell(flatCellTexts) }
            val flatMainSubPairs =
                remember(paragraphGroupFieldValues, paragraphRowSizesFingerprint) {
                    flatIndexToMainSubPairs(paragraphGroupFieldValues)
                }
            val density = LocalDensity.current
            val paragraphCellVerticalGap =
                remember(
                    density.fontScale,
                    editorAppearance.editorFontSizeSp,
                    editorAppearance.editorLineSpacingMultiplier,
                ) {
                    (editorAppearance.editorFontSizeSp *
                        editorAppearance.editorLineSpacingMultiplier *
                        density.fontScale)
                        .dp
                }
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                verticalArrangement =
                    Arrangement.spacedBy(
                        if (textEditorChromeViewOnly) paragraphCellVerticalGap else 10.dp,
                    ),
            ) {
                items(
                    count = flatMainSubPairs.size,
                    key = { flatIdx ->
                        val p = flatMainSubPairs.getOrNull(flatIdx)
                        if (p != null) "${p.first}_${p.second}" else "missing_$flatIdx"
                    },
                ) { flatIdx ->
                    val pair = flatMainSubPairs.getOrNull(flatIdx) ?: return@items
                    val (mainIdx, subIdx) = pair
                    val ttsStartAtCell =
                        if (flatIdx < flatCellTtsStart.size) flatCellTtsStart[flatIdx] else 0
                    val highlightCurrentSpeakingParagraph =
                        speakingParagraphIndex >= 0 &&
                            ttsStartAtCell == speakingParagraphIndex
                    val para =
                        paragraphGroupFieldValues.getOrNull(mainIdx)?.getOrNull(subIdx)
                            ?: return@items
                    val cellFocusRequester = remember(flatIdx) { FocusRequester() }
                    LaunchedEffect(
                        paragraphSplitMode,
                        flatItemCount,
                        focusedParagraphIndex,
                        paragraphFocusRequestToken,
                        textEditorChromeViewOnly,
                        flatIdx,
                    ) {
                        if (!paragraphSplitMode || flatItemCount <= 0) return@LaunchedEffect
                        if (focusedParagraphIndex != flatIdx) return@LaunchedEffect
                        try {
                            cellFocusRequester.requestFocus()
                        } catch (_: IllegalStateException) {
                        } catch (_: IllegalArgumentException) {
                        }
                    }
                    val cellParagraphInteractionSource = remember { MutableInteractionSource() }
                    if (!textEditorChromeViewOnly) {
                        LaunchedEffect(cellParagraphInteractionSource, flatIdx, paragraphSplitMode) {
                            if (!paragraphSplitMode) return@LaunchedEffect
                            cellParagraphInteractionSource.interactions.collect { interaction ->
                                if (interaction is PressInteraction.Release) {
                                    focusedParagraphIndex = flatIdx
                                    persistLastReadingBookmarkFromEditorFieldFlat(
                                        latestParagraphFieldGroups,
                                        flatIdx,
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
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
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    },
                                ),
                    ) {
                        if (!textEditorChromeViewOnly && subIdx == 0) {
                            Text(
                                text = "Đoạn ${mainIdx + 1}",
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = editorAppearance.paragraphEditorFontFamily,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                            )
                        }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .onPreInterceptKeyBeforeSoftKeyboard { ev ->
                                        if (textEditorChromeViewOnly) {
                                            return@onPreInterceptKeyBeforeSoftKeyboard false
                                        }
                                        if (ev.type != KeyEventType.KeyDown) {
                                            return@onPreInterceptKeyBeforeSoftKeyboard false
                                        }
                                        val nk = ev.nativeKeyEvent
                                        val isBackspaceLike =
                                            ev.key == Key.Backspace ||
                                                ev.key == Key.Delete ||
                                                nk?.keyCode == AndroidKeyEvent.KEYCODE_DEL
                                        if (!isBackspaceLike) {
                                            return@onPreInterceptKeyBeforeSoftKeyboard false
                                        }
                                        mergeParagraphBackward(flatIdx)
                                    }
                                    .then(
                                        if (textEditorChromeViewOnly) {
                                            Modifier
                                        } else {
                                            Modifier.padding(start = 4.dp, bottom = 6.dp)
                                        },
                                    )
                                    .background(
                                        color =
                                            if (highlightCurrentSpeakingParagraph) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                Color.Transparent
                                            },
                                        shape =
                                            RoundedCornerShape(
                                                if (textEditorChromeViewOnly) 6.dp else 8.dp,
                                            ),
                                    )
                                    .padding(
                                        if (textEditorChromeViewOnly) 0.dp else 4.dp,
                                    ),
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (textEditorChromeViewOnly) {
                                Text(
                                    text = para.text,
                                    style = paragraphViewSplitTextStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .heightIn(max = 320.dp)
                                            .focusRequester(cellFocusRequester)
                                            .focusable()
                                            .clickable {
                                                focusedParagraphIndex = flatIdx
                                                persistLastReadingBookmarkFromEditorFieldFlat(
                                                    latestParagraphFieldGroups,
                                                    flatIdx,
                                                )
                                            },
                                )
                            } else {
                                OutlinedTextField(
                                    value = para,
                                    readOnly = false,
                                    interactionSource = cellParagraphInteractionSource,
                                    textStyle =
                                        editorAppearance.editorBodyStyle.copy(
                                            fontFamily = editorAppearance.paragraphEditorFontFamily,
                                            lineHeight =
                                                editorLineHeightSp(
                                                    editorAppearance.editorBodyStyle,
                                                    editorAppearance.editorLineSpacingMultiplier,
                                                ),
                                        ),
                                    onValueChange = outVc@{ newVal ->
                                        val old = para
                                        if (flatIdx > 0 &&
                                            old.selection.collapsed &&
                                            old.selection.start == 0 &&
                                            old.text.isNotEmpty() &&
                                            newVal.text == old.text.drop(1)
                                        ) {
                                            if (mergeParagraphBackward(flatIdx)) return@outVc
                                        }
                                        val gl =
                                            paragraphGroupFieldValues
                                                .map { it.toMutableList() }
                                                .toMutableList()
                                        if (mainIdx in gl.indices && subIdx in gl[mainIdx].indices) {
                                            gl[mainIdx][subIdx] = newVal
                                            paragraphGroupFieldValues =
                                                compactParagraphGroupFieldValues(gl)
                                            clampFlatFocus()
                                            scheduleDebouncedParagraphParentPersist()
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .heightIn(max = 320.dp)
                                            .focusRequester(cellFocusRequester)
                                            .onFocusChanged { fs ->
                                                if (fs.isFocused) focusedParagraphIndex = flatIdx
                                            }
                                            .onPreviewKeyEvent { ev ->
                                                if (ev.type != KeyEventType.KeyDown) {
                                                    return@onPreviewKeyEvent false
                                                }
                                                when {
                                                    ev.key == Key.Backspace ||
                                                        ev.key == Key.Delete ->
                                                        mergeParagraphBackward(flatIdx)
                                                    (ev.key == Key.Enter || ev.key == Key.NumPadEnter) &&
                                                        ev.isShiftPressed ->
                                                        splitParagraphForward(flatIdx)
                                                    else -> false
                                                }
                                            },
                                    label = {
                                        Text(
                                            "Câu ${subIdx + 1}",
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = editorAppearance.paragraphEditorFontFamily,
                                                ),
                                        )
                                    },
                                    minLines = 2,
                                    colors = OutlinedTextFieldDefaults.colors(),
                                )
                            }
                            if (!textEditorChromeViewOnly) {
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (flatIdx > 0) {
                                    IconButton(
                                        onClick = {
                                            keyboardController?.hide()
                                            if (!mergeParagraphBackward(
                                                    flatIdx,
                                                    requireCaretAtStart = false,
                                                )
                                            ) {
                                                Toast.makeText(
                                                    ctx,
                                                    "Không thể nối với câu trước.",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.MergeType,
                                            contentDescription = "Nối với câu trước",
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        if (focusedParagraphIndex != flatIdx) {
                                            Toast.makeText(
                                                ctx,
                                                "Chạm vào ô câu này, đặt con trỏ tại chỗ tách, rồi bấm lại.",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            return@IconButton
                                        }
                                        if (!splitParagraphAtCaretForToolbar(flatIdx)) {
                                            Toast.makeText(
                                                ctx,
                                                "Đặt con trỏ giữa nội dung để tách thành đoạn mới (xuống dòng).",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Filled.HorizontalSplit,
                                        contentDescription = "Tách đoạn mới tại con trỏ (như xuống dòng)",
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }
        } else {
            val useNativeHugeEditor = text.length >= AppEditorConstants.FULL_TEXT_NATIVE_EDITOR_MIN_CHARS
            val fullTextInteractionSource = remember { MutableInteractionSource() }
            val fullTextColors = OutlinedTextFieldDefaults.colors()
            val fullTextFocused by fullTextInteractionSource.collectIsFocusedAsState()
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
            CompositionLocalProvider(
                LocalTextSelectionColors provides fullTextColors.textSelectionColors,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
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
                                    fullTextFieldValue = v
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
    }
}
