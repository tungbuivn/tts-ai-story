package com.ttsaistory.app.ui.reader

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import androidx.compose.runtime.snapshotFlow
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
import com.ttsaistory.app.ui.fonts.rememberReaderTabEditorAppearance
import com.ttsaistory.app.domain.canonicalTextFromRaw
import com.ttsaistory.app.domain.charOffsetForEditorFlatCellInMerged
import com.ttsaistory.app.domain.editorUiFlatForTtsParagraphStartIndexForFlatCells
import com.ttsaistory.app.domain.editorUiFlatToTtsParagraphStartIndex
import com.ttsaistory.app.export.TtsAudioExportForegroundService
import com.ttsaistory.app.export.TtsExportUiCoordinator
import com.ttsaistory.app.domain.flatIndexFromMainSub
import com.ttsaistory.app.domain.flatIndexToMainSub
import com.ttsaistory.app.domain.hasSpeakableParagraphFrom
import com.ttsaistory.app.domain.mergeMainParagraphGroups
import com.ttsaistory.app.domain.mergeParagraphs
import com.ttsaistory.app.domain.ParagraphTextService
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
import com.ttsaistory.app.model.clearLastReadingBookmark
import com.ttsaistory.app.model.lastReadingBookmarkAppliesToStory
import com.ttsaistory.app.model.putLastReadingBookmark
import com.ttsaistory.app.model.saveLastText
import com.ttsaistory.app.ui.library.OnlineCategoryHeadlessStoryTextSync
import com.ttsaistory.app.ui.library.OnlineWebStoryNextPagePrefetch
import com.ttsaistory.app.ui.library.OnlineWebStoryViewAheadPreload
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Snapshot một trang lưới đoạn ở chế độ chỉ xem — tránh đọc [paragraphGroupFieldValues] mỗi frame. */
private data class ReaderParagraphViewPageCell(
    val mainIdx: Int,
    val subIdx: Int,
    val text: String,
)

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderTab(
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
    /** Đăng ký nút xuất AAC trên top bar; [null] khi ReaderTab huỷ (đổi tab / dispose). */
    onRegisterExportM4aForTopBar: ((ExportM4aTopBarState?) -> Unit)? = null,
    /** Đăng ký hàm flush bản nháp đoạn lên [text] trước khi app nhận share / đổi truyện thư viện. */
    onRegisterParagraphDraftFlush: ((() -> Unit) -> Unit)? = null,
    /** Đăng ký hành động bottom bar (cuộn đầu/cuối / con trỏ đầu cuối); null khi huỷ đăng ký. */
    onRegisterReaderBottomNav: ((ReaderBottomNavBridge?) -> Unit)? = null,
    systemTtsSpeechRate: Float,
    systemTtsPitch: Float,
) {
    var paragraphSplitMode by rememberSaveable { mutableStateOf(true) }
    var focusedParagraphIndex by remember { mutableIntStateOf(0) }
    var pendingFocusFlatIndex by remember { mutableIntStateOf(-1) }
    var paragraphFocusRequestToken by remember { mutableIntStateOf(0) }
    var fullTextFieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var segments by remember { mutableStateOf(emptyList<String>()) }
    /** Chỉ khi đang sửa ô: chỉ số TTS đầu câu theo từng ô phẳng (toàn truyện). */
    var flatCellTtsStart by remember { mutableStateOf(intArrayOf()) }
    /** Chỉ khi chỉ xem: chỉ số TTS cho đúng [paragraphPageItemCount] ô trên trang hiện tại. */
    var flatPageTtsStart by remember { mutableStateOf(intArrayOf()) }
    val listState = rememberLazyListState()
    var paragraphGroupFieldValues by remember {
        mutableStateOf(
            listOf(listOf(TextFieldValue("", TextRange(0)))),
        )
    }
    val flatItemCount = paragraphGroupFieldValues.sumOf { it.size }
    val paragraphSplitPageSize = AppEditorConstants.PARAGRAPH_SPLIT_PAGE_SIZE
    var paragraphSplitPageIndex by rememberSaveable { mutableIntStateOf(0) }
    var paragraphViewOnlyPageCells by remember {
        mutableStateOf(emptyList<ReaderParagraphViewPageCell>())
    }
    var playToolbarParagraphsDebounced by remember { mutableStateOf(emptyList<String>()) }
    /** Đồng bộ với [ParagraphTextService.totalItemCount] (cập nhật trong [splitIntoParagraphs] / parse). */
    val paragraphToolbarTtsTotal by ParagraphTextService.totalItemCount.collectAsState(initial = null)
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
    var exportUiFromCoordinator by remember { mutableStateOf<DialogTtsExportState?>(null) }
    var activeStoryHasWebUrl by remember { mutableStateOf(false) }
    var webContentReloadWorking by remember { mutableStateOf(false) }
    /** Có truyện trước / sau trong cùng thể loại (theo sort thư viện); null khi không có truyện đang mở. */
    var libraryAdjacentNav by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    LaunchedEffect(Unit) {
        TtsExportUiCoordinator.uiState.collect { exportUiFromCoordinator = it }
    }
    LaunchedEffect(activeLibraryStoryId, librarySyncEpoch) {
        val sid = activeLibraryStoryId
        if (sid == null) {
            activeStoryHasWebUrl = false
            libraryAdjacentNav = null
            return@LaunchedEffect
        }
        val triple =
            withContext(Dispatchers.IO) {
                val web =
                    libraryRepository.getStory(sid)?.onlinePageUrl?.trim()?.isNotEmpty() == true
                val hasPrev = libraryRepository.previousStoryInCategoryBefore(sid) != null
                val hasNext = libraryRepository.nextStoryInCategoryAfter(sid) != null
                Triple(web, hasPrev, hasNext)
            }
        activeStoryHasWebUrl = triple.first
        libraryAdjacentNav = triple.second to triple.third
    }

    LaunchedEffect(activeLibraryStoryId, librarySyncEpoch) {
        val sid = activeLibraryStoryId ?: return@LaunchedEffect
        val insertedNextPlaceholder =
            withContext(Dispatchers.IO) {
                val row = libraryRepository.getStory(sid) ?: return@withContext false
                if (row.onlinePageUrl.isNullOrBlank()) return@withContext false
                libraryRepository.ensurePlaceholderStoryForStoredOnlineNextPageUrl(sid)
            }
        if (insertedNextPlaceholder) {
            onLibraryDataChanged()
        }
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
    var webPrefetchChapterQueueLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var webStoryQueueTargetStoryId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(activeLibraryStoryId) {
        webStoryQueueTargetStoryId = null
    }

    LaunchedEffect(moveCategoryTarget?.id) {
        moveStoryTitleDraft = moveCategoryTarget?.title.orEmpty()
    }

    LaunchedEffect(textEditorChromeViewOnly) {
        if (textEditorChromeViewOnly) {
            keyboardController?.hide()
            webPrefetchChapterQueueLines = emptyList()
        }
    }

    LaunchedEffect(textEditorChromeViewOnly, activeLibraryStoryId) {
        if (textEditorChromeViewOnly) return@LaunchedEffect
        val sid = activeLibraryStoryId ?: return@LaunchedEffect
        val isWeb =
            withContext(Dispatchers.IO) {
                libraryRepository.getStory(sid)?.onlinePageUrl?.trim()?.isNotEmpty() == true
            }
        if (!isWeb) {
            webPrefetchChapterQueueLines = emptyList()
            return@LaunchedEffect
        }
        try {
            OnlineWebStoryNextPagePrefetch.prefetchForwardChainWhileEditing(
                context = ctx,
                startStoryId = sid,
                repository = libraryRepository,
                onLibraryDataChanged = onLibraryDataChanged,
                onPrefetchQueueLines = { webPrefetchChapterQueueLines = it },
                onQueueTargetStoryId = { webStoryQueueTargetStoryId = it },
            )
        } finally {
            webPrefetchChapterQueueLines = emptyList()
        }
    }

    LaunchedEffect(textEditorChromeViewOnly, activeLibraryStoryId, librarySyncEpoch) {
        if (!textEditorChromeViewOnly) return@LaunchedEffect
        val sid = activeLibraryStoryId ?: return@LaunchedEffect
        val isWeb =
            withContext(Dispatchers.IO) {
                libraryRepository.getStory(sid)?.onlinePageUrl?.trim()?.isNotEmpty() == true
            }
        if (!isWeb) return@LaunchedEffect
        OnlineWebStoryViewAheadPreload.preloadNextTenWhileViewing(
            context = ctx,
            anchorLibraryStoryId = sid,
            repository = libraryRepository,
            onLibraryDataChanged = onLibraryDataChanged,
            onQueueTargetStoryId = { webStoryQueueTargetStoryId = it },
        )
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

    val prefsBridge = rememberReaderTabPrefsBridge(prefs)
    val editorAppearance = rememberReaderTabEditorAppearance(prefs, prefsBridge.fontPrefsEpoch)
    LaunchedEffect(bookmarkResetKey, librarySyncEpoch) {
        prefsBridge.refreshBookmarkFromPrefs()
    }

    val latestParagraphSplit by rememberUpdatedState(paragraphSplitMode)
    val latestParagraphFieldGroups by rememberUpdatedState(paragraphGroupFieldValues)
    val latestOnTextChange by rememberUpdatedState(onTextChange)
    val latestParentText by rememberUpdatedState(text)
    LaunchedEffect(paragraphSplitMode, textEditorChromeViewOnly) {
        if (!paragraphSplitMode) {
            snapshotFlow { text }
                .collectLatest { snap ->
                    if (snap.isEmpty()) {
                        playToolbarParagraphsDebounced = emptyList()
                        withContext(Dispatchers.Default) { splitIntoParagraphs("") }
                        toolbarTtsSplitWorking = false
                        return@collectLatest
                    }
                    delay(AppEditorConstants.PLAY_TOOLBAR_SPLIT_DEBOUNCE_MS)
                    if (snap != text) return@collectLatest
                    toolbarTtsSplitWorking = true
                    val t0 = AnrDiagLog.begin("ReaderTab splitIntoParagraphs(fullTextToolbar) len=${snap.length}")
                    try {
                        val paras =
                            withContext(Dispatchers.Default) { splitIntoParagraphs(snap) }
                        if (snap != text) {
                            AnrDiagLog.i("ReaderTab splitIntoParagraphs(fullTextToolbar) dropped")
                            return@collectLatest
                        }
                        playToolbarParagraphsDebounced = paras
                        AnrDiagLog.end(
                            "ReaderTab splitIntoParagraphs(fullTextToolbar) n=${paras.size}",
                            t0,
                        )
                    } finally {
                        toolbarTtsSplitWorking = false
                    }
                }
            return@LaunchedEffect
        }
        // Chỉ xem: cập nhật tổng câu TTS sau debounce + split; chế độ sửa ô không chạy lại split theo mỗi lần gõ.
        if (!textEditorChromeViewOnly) {
            return@LaunchedEffect
        }
        snapshotFlow { paragraphGroupFieldValues }
            .collectLatest { groups ->
                val rows1 = groups.map { r -> r.map { it.text } }
                val merged =
                    withContext(Dispatchers.Default) { mergeMainParagraphGroups(rows1) }
                if (!paragraphSplitMode) return@collectLatest
                if (merged.isEmpty()) {
                    playToolbarParagraphsDebounced = emptyList()
                    if (latestParentText.isEmpty()) {
                        withContext(Dispatchers.Default) { splitIntoParagraphs("") }
                    }
                    toolbarTtsSplitWorking = false
                    return@collectLatest
                }
                delay(AppEditorConstants.PLAY_TOOLBAR_SPLIT_DEBOUNCE_MS)
                if (!paragraphSplitMode) return@collectLatest
                val rows2 = latestParagraphFieldGroups.map { r -> r.map { it.text } }
                val merged2 =
                    withContext(Dispatchers.Default) { mergeMainParagraphGroups(rows2) }
                if (merged != merged2) return@collectLatest
                toolbarTtsSplitWorking = true
                val t0 =
                    AnrDiagLog.begin(
                        "ReaderTab splitIntoParagraphs(mergedToolbar) len=${merged2.length}",
                    )
                try {
                    val paras =
                        withContext(Dispatchers.Default) { splitIntoParagraphs(merged2) }
                    val rows3 = latestParagraphFieldGroups.map { r -> r.map { it.text } }
                    val merged3 =
                        withContext(Dispatchers.Default) { mergeMainParagraphGroups(rows3) }
                    if (merged2 != merged3) {
                        AnrDiagLog.i(
                            "ReaderTab splitIntoParagraphs(mergedToolbar) dropped (merged changed)",
                        )
                        return@collectLatest
                    }
                    playToolbarParagraphsDebounced = paras
                    AnrDiagLog.end(
                        "ReaderTab splitIntoParagraphs(mergedToolbar) n=${paras.size}",
                        t0,
                    )
                } finally {
                    toolbarTtsSplitWorking = false
                }
            }
    }
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
                delay(timeMillis = AppEditorConstants.PARAGRAPH_FIELD_PERSIST_DEBOUNCE_MS)
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
        paragraphSplitPageIndex = 0
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
                "ReaderTab paragraphMainGroupsForEditor(leaveEditViewOnly) len=${snapshot.length}",
            )
        val segs =
            withContext(Dispatchers.Default) { paragraphMainGroupsForEditor(snapshot) }
        if (!textEditorChromeViewOnly || !paragraphSplitMode) {
            AnrDiagLog.end("ReaderTab paragraphMainGroupsForEditor(leaveEditViewOnly) CANCELLED", t0)
            return@LaunchedEffect
        }
        if (mergedParagraphFields() != snapshot) {
            AnrDiagLog.i("ReaderTab paragraphMainGroupsForEditor(leaveEditViewOnly) stale skip")
            AnrDiagLog.end("ReaderTab paragraphMainGroupsForEditor(leaveEditViewOnly) STALE", t0)
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
            "ReaderTab paragraphMainGroupsForEditor(leaveEditViewOnly) rows=${segs.size} cells=${segs.sumOf { it.size }}",
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
        prefs.edit().putLastReadingBookmark(tts, activeLibraryStoryId).apply()
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
        prefs.edit().putLastReadingBookmark(tts, activeLibraryStoryId).apply()
    }

    SideEffect {
        onRegisterParagraphDraftFlush?.invoke { flushParagraphParentPersist() }
    }

    SideEffect {
        onRegisterExportM4aForTopBar?.invoke(
            ExportM4aTopBarState(
                onClick = {
                    if (paragraphSplitMode) flushParagraphParentPersist()
                    val bodyForExport =
                        if (paragraphSplitMode) mergedParagraphFields() else text
                    ParagraphTextService.parseStoredTextToParagraphGroups(bodyForExport)
                    val exportBody =
                        ParagraphTextService.lastCachedFlatSentencesForAacExport()
                            ?.joinToString("\n")
                            ?: bodyForExport
                    enqueueTtsExport(exportBody)
                },
                enabled =
                    exportUiFromCoordinator == null &&
                        (paragraphToolbarTtsTotal ?: 0) > 0,
            ),
        )
    }

    DisposableEffect(onRegisterExportM4aForTopBar) {
        onDispose {
            onRegisterExportM4aForTopBar?.invoke(null)
        }
    }

    val latestFullTextField by rememberUpdatedState(fullTextFieldValue)
    val latestFlatItemCount by rememberUpdatedState(flatItemCount)
    val latestSpeakingParagraphIndex by rememberUpdatedState(speakingParagraphIndex)
    val latestSystemTtsPlaybackActive by rememberUpdatedState(systemTtsPlaybackActive)
    val latestElevenLabsJobActive by rememberUpdatedState(elevenLabsJobActive)
    val latestOnPlayParagraphs by rememberUpdatedState(onPlayParagraphs)

    fun scrollParagraphLazyToGlobalFlat(globalFlat: Int) {
        val n = flatItemCount
        if (n <= 0) return
        val maxPage = (n + paragraphSplitPageSize - 1) / paragraphSplitPageSize - 1
        val page = (globalFlat / paragraphSplitPageSize).coerceIn(0, maxPage)
        paragraphSplitPageIndex = page
        val ps = page * paragraphSplitPageSize
        val pageItemCount = (n - ps).coerceAtMost(paragraphSplitPageSize).coerceAtLeast(1)
        val local = (globalFlat - ps).coerceIn(0, pageItemCount - 1)
        scope.launch {
            delay(1)
            try {
                listState.scrollToItem(local)
            } catch (_: IllegalArgumentException) {
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun animateParagraphLazyToGlobalFlat(globalFlat: Int) {
        val n = flatItemCount
        if (n <= 0) return
        val maxPage = (n + paragraphSplitPageSize - 1) / paragraphSplitPageSize - 1
        val page = (globalFlat / paragraphSplitPageSize).coerceIn(0, maxPage)
        paragraphSplitPageIndex = page
        val ps = page * paragraphSplitPageSize
        val pageItemCount = (n - ps).coerceAtMost(paragraphSplitPageSize).coerceAtLeast(1)
        val local = (globalFlat - ps).coerceIn(0, pageItemCount - 1)
        scope.launch {
            delay(1)
            try {
                listState.animateScrollToItem(local)
            } catch (_: IllegalArgumentException) {
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    LaunchedEffect(paragraphSplitMode, flatItemCount, paragraphSplitPageSize) {
        if (!paragraphSplitMode || flatItemCount <= 0) {
            paragraphSplitPageIndex = 0
            return@LaunchedEffect
        }
        val maxPage = (flatItemCount + paragraphSplitPageSize - 1) / paragraphSplitPageSize - 1
        if (paragraphSplitPageIndex > maxPage) {
            paragraphSplitPageIndex = maxPage
        }
    }

    LaunchedEffect(paragraphSplitMode, textEditorChromeViewOnly) {
        if (!paragraphSplitMode) {
            flatCellTtsStart = intArrayOf()
            flatPageTtsStart = intArrayOf()
            return@LaunchedEffect
        }
        // Chỉ xem: TTS theo trang nằm trong LaunchedEffect(snapshot trang) — không giữ mảng n×Int toàn lưới.
        if (textEditorChromeViewOnly) {
            flatCellTtsStart = intArrayOf()
            return@LaunchedEffect
        }
        snapshotFlow { paragraphGroupFieldValues }
            .debounce(AppEditorConstants.TTS_CELL_PREFIX_DEBOUNCE_MS)
            .collectLatest { groups ->
                val arr =
                    withContext(Dispatchers.Default) {
                        val cells = groups.flatMap { r -> r.map { it.text } }
                        ttsParagraphStartIndexForEachFlatCell(cells)
                    }
                flatCellTtsStart = arr
            }
    }

    fun goTopOrCaretStartAction() {
        if (paragraphSplitMode) {
            paragraphSplitPageIndex = 0
            focusedParagraphIndex = 0
            pendingFocusFlatIndex = 0
            paragraphFocusRequestToken++
            scope.launch {
                delay(1)
                listState.scrollToItem(0)
            }
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
                val maxPage = (n + paragraphSplitPageSize - 1) / paragraphSplitPageSize - 1
                paragraphSplitPageIndex = maxPage.coerceAtLeast(0)
                focusedParagraphIndex = n - 1
                pendingFocusFlatIndex = n - 1
                paragraphFocusRequestToken++
                val pageStart = paragraphSplitPageIndex * paragraphSplitPageSize
                val localLast = (n - 1 - pageStart).coerceAtLeast(0)
                scope.launch {
                    delay(1)
                    listState.scrollToItem(localLast)
                }
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

    var lastBottomNavPublishKey by remember { mutableStateOf<String?>(null) }

    SideEffect {
        val cellStructureFingerprint =
            paragraphGroupFieldValues.joinToString(",") { it.size.toString() }
        val bottomNavPublishKey =
            listOf(
                paragraphSplitMode,
                textEditorChromeViewOnly,
                flatItemCount,
                focusedParagraphIndex,
                cellStructureFingerprint,
                paragraphToolbarTtsTotal?.toString() ?: "n",
                toolbarTtsSplitWorking,
                playToolbarParagraphsDebounced.size,
                webPrefetchChapterQueueLines.size,
                activeStoryHasWebUrl,
                webStoryQueueTargetStoryId?.toString() ?: "null",
            ).joinToString("|")
        if (bottomNavPublishKey == lastBottomNavPublishKey) {
            return@SideEffect
        }
        lastBottomNavPublishKey = bottomNavPublishKey
        onRegisterReaderBottomNav?.invoke(
            ReaderBottomNavBridge(
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
                    scrollParagraphLazyToGlobalFlat(idx)
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
                ttsSpeakableSentenceTotal = paragraphToolbarTtsTotal,
                ttsSentenceSplitWorking = toolbarTtsSplitWorking,
                webPrefetchChapterQueueLines = webPrefetchChapterQueueLines,
                libraryWebStoryActive = activeStoryHasWebUrl,
                webStoryQueueTargetStoryId =
                    if (activeStoryHasWebUrl) {
                        webStoryQueueTargetStoryId
                    } else {
                        null
                    },
            ),
        )
    }
    // Không key theo onRegisterReaderBottomNav: lambda từ AppTabs đổi mỗi recompose → onDispose gọi
    // invoke(null) làm mất bridge (slider / +/- không cập nhật).
    val latestRegisterBottomNav by rememberUpdatedState(onRegisterReaderBottomNav)
    DisposableEffect(Unit) {
        onDispose {
            lastBottomNavPublishKey = null
            latestRegisterBottomNav?.invoke(null)
        }
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

    LaunchedEffect(text, paragraphSplitMode, textEditorChromeViewOnly) {
        // Trong lưới đoạn + chế độ sửa ô, [text] vẫn nhảy theo debounce parent — không cần
        // paragraphsForEditor toàn văn (ReaderTab chỉ dùng segments khi không split / bookmark).
        if (paragraphSplitMode && !textEditorChromeViewOnly) return@LaunchedEffect
        val snapshot = text
        val t0 = AnrDiagLog.begin("ReaderTab paragraphsForEditor len=${snapshot.length}")
        val computed =
            withContext(Dispatchers.Default) { paragraphsForEditor(snapshot) }
        if (snapshot == text) {
            segments = computed
            AnrDiagLog.end("ReaderTab paragraphsForEditor segs=${computed.size}", t0)
        } else {
            AnrDiagLog.i("ReaderTab paragraphsForEditor dropped (text changed)")
        }
    }

    LaunchedEffect(librarySyncEpoch) {
        if (librarySyncEpoch <= 0) return@LaunchedEffect
        toolbarTtsSplitWorking = false
        if (!paragraphSplitMode) {
            return@LaunchedEffect
        }
        toolbarTtsSplitWorking = true
        persistDebouncer.job?.cancel()
        persistDebouncer.job = null
        val snapshot = text
        val t0 =
            AnrDiagLog.begin(
                "ReaderTab paragraphMainGroupsForEditor(librarySyncEpoch=$librarySyncEpoch) len=${snapshot.length}",
            )
        val segs =
            withContext(Dispatchers.Default) { paragraphMainGroupsForEditor(snapshot) }
        if (snapshot != text || !paragraphSplitMode) {
            AnrDiagLog.end("ReaderTab paragraphMainGroupsForEditor libSync CANCELLED", t0)
            toolbarTtsSplitWorking = false
            return@LaunchedEffect
        }
        AnrDiagLog.end(
            "ReaderTab paragraphMainGroupsForEditor libSync rows=${segs.size} cells=${segs.sumOf { it.size }}",
            t0,
        )
        paragraphGroupFieldValues =
            segs.map { row ->
                row.map { s ->
                    TextFieldValue(text = s, selection = TextRange(s.length))
                }
            }
        prevParagraphSplitMode = true
        try {
            val mergedFromLib =
                withContext(Dispatchers.Default) { mergeMainParagraphGroups(segs) }
            if (mergedFromLib.isEmpty()) {
                playToolbarParagraphsDebounced = emptyList()
                if (text.isEmpty()) {
                    withContext(Dispatchers.Default) { splitIntoParagraphs("") }
                }
            } else {
                val paras =
                    withContext(Dispatchers.Default) { splitIntoParagraphs(mergedFromLib) }
                playToolbarParagraphsDebounced = paras
            }
        } finally {
            toolbarTtsSplitWorking = false
        }
        val savedParagraph =
            if (prefs.lastReadingBookmarkAppliesToStory(activeLibraryStoryId)) {
                prefs.getInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, -1)
            } else {
                -1
            }
        val newCellCount = segs.sumOf { it.size }
        if (savedParagraph < 0 && newCellCount > 0) {
            paragraphSplitPageIndex = 0
            focusedParagraphIndex = 0
            pendingFocusFlatIndex = 0
            paragraphFocusRequestToken++
            scope.launch {
                delay(1)
                try {
                    listState.scrollToItem(0)
                } catch (_: IllegalArgumentException) {
                } catch (e: CancellationException) {
                    throw e
                }
            }
        }
    }

    LaunchedEffect(paragraphSplitMode, text) {
        if (!paragraphSplitMode) {
            prevParagraphSplitMode = false
            return@LaunchedEffect
        }
        // Đang sửa ô: không gọi paragraphMainGroupsForEditor theo mỗi lần [text] (debounce) —
        // thoát "chỉ xem" đã flush + chuẩn hoá lưới trong LaunchedEffect(textEditorChromeViewOnly).
        if (!textEditorChromeViewOnly && prevParagraphSplitMode == true) {
            return@LaunchedEffect
        }
        persistDebouncer.job?.cancel()
        persistDebouncer.job = null
        val snapshot = text
        val tParse =
            AnrDiagLog.begin(
                "ReaderTab paragraphMainGroupsForEditor(splitMode+text) len=${snapshot.length}",
            )
        val segs =
            withContext(Dispatchers.Default) { paragraphMainGroupsForEditor(snapshot) }
        if (snapshot != text || !paragraphSplitMode) {
            AnrDiagLog.end("ReaderTab paragraphMainGroupsForEditor(splitMode+text) CANCELLED", tParse)
            return@LaunchedEffect
        }
        AnrDiagLog.end(
            "ReaderTab paragraphMainGroupsForEditor(splitMode+text) rows=${segs.size}",
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
            if (!textEditorChromeViewOnly) {
                toolbarTtsSplitWorking = true
                try {
                    val rows = paragraphGroupFieldValues.map { r -> r.map { it.text } }
                    val mergedR =
                        withContext(Dispatchers.Default) { mergeMainParagraphGroups(rows) }
                    if (mergedR.isEmpty()) {
                        playToolbarParagraphsDebounced = emptyList()
                        if (latestParentText.isEmpty()) {
                            withContext(Dispatchers.Default) { splitIntoParagraphs("") }
                        }
                    } else {
                        val paras =
                            withContext(Dispatchers.Default) { splitIntoParagraphs(mergedR) }
                        playToolbarParagraphsDebounced = paras
                    }
                } finally {
                    toolbarTtsSplitWorking = false
                }
            }
            return@LaunchedEffect
        }
        val cellCount = paragraphGroupFieldValues.sumOf { it.size }
        val tMap = AnrDiagLog.begin("ReaderTab fieldRows.map MAIN cells=$cellCount")
        val fieldRows = paragraphGroupFieldValues.map { row -> row.map { it.text } }
        AnrDiagLog.end("ReaderTab fieldRows.map MAIN", tMap)
        val tMerge = AnrDiagLog.begin("ReaderTab mergeMainParagraphGroups(Default)")
        val merged =
            withContext(Dispatchers.Default) { mergeMainParagraphGroups(fieldRows) }
        AnrDiagLog.end("ReaderTab mergeMainParagraphGroups(Default) mergedLen=${merged.length}", tMerge)
        if (snapshot != text || !paragraphSplitMode) return@LaunchedEffect
        if (merged != text) {
            paragraphGroupFieldValues =
                segs.map { row ->
                    row.map { s ->
                        TextFieldValue(text = s, selection = TextRange(s.length))
                    }
                }
            if (!textEditorChromeViewOnly) {
                toolbarTtsSplitWorking = true
                try {
                    val rows = paragraphGroupFieldValues.map { r -> r.map { it.text } }
                    val mergedR =
                        withContext(Dispatchers.Default) { mergeMainParagraphGroups(rows) }
                    if (mergedR.isEmpty()) {
                        playToolbarParagraphsDebounced = emptyList()
                        if (latestParentText.isEmpty()) {
                            withContext(Dispatchers.Default) { splitIntoParagraphs("") }
                        }
                    } else {
                        val paras =
                            withContext(Dispatchers.Default) { splitIntoParagraphs(mergedR) }
                        playToolbarParagraphsDebounced = paras
                    }
                } finally {
                    toolbarTtsSplitWorking = false
                }
            }
        }
    }

    /** Lưới đoạn + vừa tắt chỉ xem: cập nhật tổng câu bottom bar (không chạy khi đang gõ vì [textEditorChromeViewOnly] không đổi). */
    LaunchedEffect(textEditorChromeViewOnly, paragraphSplitMode) {
        if (textEditorChromeViewOnly || !paragraphSplitMode) return@LaunchedEffect
        yield()
        toolbarTtsSplitWorking = true
        try {
            val rows = paragraphGroupFieldValues.map { r -> r.map { it.text } }
            val mergedR = withContext(Dispatchers.Default) { mergeMainParagraphGroups(rows) }
            if (textEditorChromeViewOnly || !paragraphSplitMode) return@LaunchedEffect
            if (mergedR.isEmpty()) {
                playToolbarParagraphsDebounced = emptyList()
                if (latestParentText.isEmpty()) {
                    withContext(Dispatchers.Default) { splitIntoParagraphs("") }
                }
            } else {
                val paras = withContext(Dispatchers.Default) { splitIntoParagraphs(mergedR) }
                playToolbarParagraphsDebounced = paras
            }
        } finally {
            toolbarTtsSplitWorking = false
        }
    }

    LaunchedEffect(
        paragraphSplitMode,
        segments.size,
        bookmarkResetKey,
        librarySyncEpoch,
        activeLibraryStoryId,
    ) {
        if (!paragraphSplitMode) return@LaunchedEffect
        if (didScrollToSavedBookmark.value) return@LaunchedEffect
        if (segments.isEmpty()) return@LaunchedEffect
        delay(48)
        val cellCount = paragraphGroupFieldValues.sumOf { it.size }
        if (cellCount <= 0) return@LaunchedEffect
        val saved =
            if (prefs.lastReadingBookmarkAppliesToStory(activeLibraryStoryId)) {
                prefs.getInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, -1)
            } else {
                -1
            }
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
        scrollParagraphLazyToGlobalFlat(ui)
        didScrollToSavedBookmark.value = true
    }

    // [speakingParagraphIndex] là tham số từ parent — snapshotFlow không theo dõi được → slider không
    // theo câu đang phát; dùng LaunchedEffect + key để mỗi lần đổi câu TTS đều cập nhật slider/focus.
    LaunchedEffect(
        paragraphSplitMode,
        flatItemCount,
        speakingParagraphIndex,
        prefsBridge.trackedLastReadingParagraphIndex,
        prefsBridge.trackedLastReadingParagraphStoryId,
        activeLibraryStoryId,
    ) {
        if (!paragraphSplitMode || flatItemCount <= 0) return@LaunchedEffect
        val sp = speakingParagraphIndex
        val saved =
            if (prefs.lastReadingBookmarkAppliesToStory(activeLibraryStoryId)) {
                prefsBridge.trackedLastReadingParagraphIndex
            } else {
                -1
            }
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
                animateParagraphLazyToGlobalFlat(idx)
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
                animateParagraphLazyToGlobalFlat(idx)
            }
        }
    }

    LaunchedEffect(paragraphSplitMode, textEditorChromeViewOnly) {
        if (paragraphSplitMode || textEditorChromeViewOnly) return@LaunchedEffect
        delay(16)
        fullTextFocusRequester.requestFocus()
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
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
            ReaderSpeechEngineRow(
                speechEngine = speechEngine,
                onSpeechEngineChange = onSpeechEngineChange,
                engineControlsEnabled = exportUiFromCoordinator == null,
                libraryStoryPickerEnabled = exportUiFromCoordinator == null,
                onOpenLibraryStoryPicker = ::openLibraryStoryPickerFromToolbar,
            )
            ReaderToolbarActionsColumn(
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
                    val bookmarkRaw =
                        prefs.getInt(AppPreferenceKeys.KEY_LAST_READING_PARAGRAPH_INDEX, -1)
                    val bookmark =
                        if (prefs.lastReadingBookmarkAppliesToStory(activeLibraryStoryId)) {
                            bookmarkRaw
                        } else {
                            -1
                        }
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
                showReloadWebContent = activeStoryHasWebUrl,
                onReloadWebContentClick = {
                    keyboardController?.hide()
                    val sid = activeLibraryStoryId ?: return@ReaderToolbarActionsColumn
                    if (paragraphSplitMode) flushParagraphParentPersist()
                    scope.launch {
                        webContentReloadWorking = true
                        try {
                            OnlineCategoryHeadlessStoryTextSync.syncOnlineStoryFromWebPage(
                                context = ctx,
                                storyId = sid,
                                repository = libraryRepository,
                                bypassHttpCache = true,
                            )
                            val body =
                                withContext(Dispatchers.IO) {
                                    libraryRepository.readStoryText(sid)
                                }
                            if (body == null) {
                                Toast.makeText(
                                    ctx,
                                    "Không đọc được nội dung sau khi tải.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@launch
                            }
                            val cleaned = canonicalTextFromRaw(body)
                            onTextChange(cleaned)
                            onLibraryFileSynced()
                            onLibraryDataChanged()
                            Toast.makeText(
                                ctx,
                                "Đã tải lại nội dung từ web.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(
                                ctx,
                                e.message ?: "Không tải lại được từ web",
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            webContentReloadWorking = false
                        }
                    }
                },
                reloadWebContentEnabled =
                    exportUiFromCoordinator == null && !webContentReloadWorking,
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
                onNavigatePrevLibraryStoryClick = {
                    keyboardController?.hide()
                    val sid = activeLibraryStoryId ?: return@ReaderToolbarActionsColumn
                    if (paragraphSplitMode) flushParagraphParentPersist()
                    scope.launch {
                        val prev =
                            withContext(Dispatchers.IO) {
                                libraryRepository.previousStoryInCategoryBefore(sid)
                            }
                        if (prev == null) {
                            Toast.makeText(
                                ctx,
                                "Không có truyện trước trong thể loại.",
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        onOpenLibraryStory(prev.id)
                    }
                },
                navigatePrevLibraryStoryEnabled =
                    exportUiFromCoordinator == null &&
                        activeLibraryStoryId != null &&
                        libraryAdjacentNav?.first == true,
                onNavigateNextLibraryStoryClick = {
                    keyboardController?.hide()
                    val sid = activeLibraryStoryId ?: return@ReaderToolbarActionsColumn
                    if (paragraphSplitMode) flushParagraphParentPersist()
                    scope.launch {
                        val next =
                            withContext(Dispatchers.IO) {
                                libraryRepository.nextStoryInCategoryAfter(sid)
                            }
                        if (next == null) {
                            Toast.makeText(
                                ctx,
                                "Không có truyện sau trong thể loại.",
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        onOpenLibraryStory(next.id)
                    }
                },
                navigateNextLibraryStoryEnabled =
                    exportUiFromCoordinator == null &&
                        activeLibraryStoryId != null &&
                        libraryAdjacentNav?.second == true,
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
            DialogReaderExportM4a(
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
        if (showNewLibraryStoryDialog) {
            DialogReaderNewLibraryStory(
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
                            prefs.clearLastReadingBookmark()
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
            DialogReaderMoveStoryCategory(
                story = st,
                moveCategoryCategories = moveCategoryCategories,
                moveStoryTitleDraft = moveStoryTitleDraft,
                onMoveStoryTitleDraftChange = { moveStoryTitleDraft = it },
                onDismissRequest = { moveCategoryTarget = null },
                onSaveTitleClick = {
                    launchReaderRenameStoryInMoveCategory(
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
            fun clearParagraphCellText(atFlat: Int) {
                val gl = paragraphGroupFieldValues.map { it.toMutableList() }.toMutableList()
                if (gl.isEmpty()) return
                val (m, s) = flatIndexToMainSub(gl, atFlat)
                if (m !in gl.indices || s !in gl[m].indices) return
                gl[m][s] = TextFieldValue("", TextRange(0))
                paragraphGroupFieldValues = compactParagraphGroupFieldValues(gl)
                clampFlatFocus()
                paragraphFocusRequestToken++
                scheduleDebouncedParagraphParentPersist()
            }
            val paragraphPageStartFlat = paragraphSplitPageIndex * paragraphSplitPageSize
            val paragraphPageEndFlat =
                (paragraphPageStartFlat + paragraphSplitPageSize).coerceAtMost(flatItemCount)
            val paragraphPageItemCount = (paragraphPageEndFlat - paragraphPageStartFlat).coerceAtLeast(0)
            LaunchedEffect(
                textEditorChromeViewOnly,
                paragraphSplitMode,
                paragraphSplitPageIndex,
                paragraphSplitPageSize,
                flatItemCount,
                paragraphGroupFieldValues,
            ) {
                if (!paragraphSplitMode || !textEditorChromeViewOnly || flatItemCount <= 0) {
                    paragraphViewOnlyPageCells = emptyList()
                    flatPageTtsStart = intArrayOf()
                    return@LaunchedEffect
                }
                val pageStart = paragraphSplitPageIndex * paragraphSplitPageSize
                val pageEnd =
                    (pageStart + paragraphSplitPageSize).coerceAtMost(flatItemCount)
                val groups = paragraphGroupFieldValues
                val (built, pageTts) =
                    withContext(Dispatchers.Default) {
                        val cells = groups.flatMap { r -> r.map { it.text } }
                        val fullTts = ttsParagraphStartIndexForEachFlatCell(cells)
                        val pageList = ArrayList<ReaderParagraphViewPageCell>(pageEnd - pageStart)
                        var f = 0
                        outer@ for (mi in groups.indices) {
                            for (si in groups[mi].indices) {
                                if (f >= pageEnd) break@outer
                                if (f >= pageStart) {
                                    pageList.add(
                                        ReaderParagraphViewPageCell(
                                            mainIdx = mi,
                                            subIdx = si,
                                            text = cells[f],
                                        ),
                                    )
                                }
                                f++
                            }
                        }
                        pageList to fullTts.copyOfRange(pageStart, pageEnd)
                    }
                if (!paragraphSplitMode || !textEditorChromeViewOnly) {
                    return@LaunchedEffect
                }
                paragraphViewOnlyPageCells = built
                flatPageTtsStart = pageTts
            }
            val paragraphViewSplitTextStyle =
                remember(editorAppearance.editorBodyStyle, editorAppearance.paragraphEditorFontFamily, editorAppearance.editorLineSpacingMultiplier) {
                    editorAppearance.editorBodyStyle.copy(
                        lineHeight = editorLineHeightSp(editorAppearance.editorBodyStyle, editorAppearance.editorLineSpacingMultiplier),
                        fontFamily = editorAppearance.paragraphEditorFontFamily,
                    )
                }
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
            val paragraphCellSentenceLabelStyle =
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = editorAppearance.paragraphEditorFontFamily,
                )
            val paragraphCellSelectionColors =
                OutlinedTextFieldDefaults.colors().textSelectionColors
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
                    count = paragraphPageItemCount,
                    key = { localIdx ->
                        val flatIdx = paragraphPageStartFlat + localIdx
                        if (textEditorChromeViewOnly &&
                            paragraphPageItemCount > 0 &&
                            paragraphViewOnlyPageCells.size == paragraphPageItemCount
                        ) {
                            val c = paragraphViewOnlyPageCells[localIdx]
                            "${c.mainIdx}_${c.subIdx}"
                        } else {
                            val p = flatIndexToMainSub(paragraphGroupFieldValues, flatIdx)
                            "${p.first}_${p.second}"
                        }
                    },
                    contentType = { _ -> "paragraphCell" },
                ) { localIdx ->
                    val flatIdx = paragraphPageStartFlat + localIdx
                    val snapshotOk =
                        textEditorChromeViewOnly &&
                            paragraphPageItemCount > 0 &&
                            paragraphViewOnlyPageCells.size == paragraphPageItemCount
                    val viewCell = paragraphViewOnlyPageCells.getOrNull(localIdx)
                    val (mainIdx, subIdx) =
                        if (snapshotOk && viewCell != null) {
                            viewCell.mainIdx to viewCell.subIdx
                        } else {
                            flatIndexToMainSub(paragraphGroupFieldValues, flatIdx)
                        }
                    val ttsStartAtCell =
                        if (textEditorChromeViewOnly &&
                            paragraphPageItemCount > 0 &&
                            flatPageTtsStart.size == paragraphPageItemCount
                        ) {
                            flatPageTtsStart.getOrNull(localIdx) ?: 0
                        } else if (flatIdx < flatCellTtsStart.size) {
                            flatCellTtsStart[flatIdx]
                        } else {
                            0
                        }
                    val highlightCurrentSpeakingParagraph =
                        speakingParagraphIndex >= 0 &&
                            ttsStartAtCell == speakingParagraphIndex
                    val paraForEdit: TextFieldValue? =
                        if (textEditorChromeViewOnly) {
                            null
                        } else {
                            paragraphGroupFieldValues.getOrNull(mainIdx)?.getOrNull(subIdx)
                                ?: return@items
                        }
                    val viewLineText: String =
                        if (textEditorChromeViewOnly) {
                            if (snapshotOk && viewCell != null) {
                                viewCell.text
                            } else {
                                paragraphGroupFieldValues.getOrNull(mainIdx)?.getOrNull(subIdx)?.text
                                    ?: return@items
                            }
                        } else {
                            ""
                        }
                    val cellFocusRequester = remember(flatIdx) { FocusRequester() }
                    LaunchedEffect(
                        paragraphSplitMode,
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
                    // Chỉ ô đang focus lắng nghe interaction — tránh N coroutine collect trên danh sách dài.
                    if (!textEditorChromeViewOnly && focusedParagraphIndex == flatIdx) {
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
                                    text = viewLineText,
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
                                val cellOutlineColor =
                                    if (focusedParagraphIndex == flatIdx) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    if (focusedParagraphIndex == flatIdx) {
                                        IconButton(
                                            onClick = {
                                                keyboardController?.hide()
                                                clearParagraphCellText(flatIdx)
                                            },
                                            enabled = paraForEdit!!.text.isNotEmpty(),
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Xóa nội dung câu",
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Câu ${subIdx + 1}",
                                            style = paragraphCellSentenceLabelStyle,
                                            modifier = Modifier.padding(bottom = 4.dp),
                                        )
                                        CompositionLocalProvider(
                                            LocalTextSelectionColors provides paragraphCellSelectionColors,
                                        ) {
                                            BasicTextField(
                                                value = paraForEdit!!,
                                                onValueChange = outVc@{ newVal ->
                                                    val old =
                                                        paragraphGroupFieldValues
                                                            .getOrNull(mainIdx)
                                                            ?.getOrNull(subIdx)
                                                            ?: return@outVc
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
                                                        .fillMaxWidth()
                                                        .heightIn(max = 320.dp)
                                                        .focusRequester(cellFocusRequester)
                                                        .onFocusChanged { fs ->
                                                            if (fs.isFocused) {
                                                                focusedParagraphIndex = flatIdx
                                                                persistLastReadingBookmarkFromEditorFieldFlat(
                                                                    latestParagraphFieldGroups,
                                                                    flatIdx,
                                                                )
                                                            }
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
                                                textStyle = paragraphOutlineEditTextStyle,
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                                                                .border(
                                                                    width = 1.dp,
                                                                    color = cellOutlineColor,
                                                                    shape = RoundedCornerShape(8.dp),
                                                                )
                                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    ) {
                                                        innerTextField()
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    if (focusedParagraphIndex == flatIdx) {
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
                                                            "Đặt con trỏ giữa nội dung để tách thành đoạn mới (như xuống dòng).",
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
    DialogReaderStoryPicker(
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
        onDeleteStory = { storyId ->
            scope.launch {
                val cid = libraryStoryPickerCategoryId ?: return@launch
                try {
                    withContext(Dispatchers.IO) {
                        libraryRepository.deleteStory(storyId)
                    }
                    libraryStoryPickerStories =
                        withContext(Dispatchers.IO) {
                            libraryRepository.listStories(cid)
                        }
                    onLibraryDataChanged()
                } catch (e: Exception) {
                    Toast.makeText(
                        ctx,
                        e.message ?: "Không xóa được truyện",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
    )
    }
}

/** Trạng thái nút xuất AAC trên top bar; [null] khi ReaderTab không gắn. */
data class ExportM4aTopBarState(
    val onClick: () -> Unit,
    val enabled: Boolean,
)
