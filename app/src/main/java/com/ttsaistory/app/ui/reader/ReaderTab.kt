package com.ttsaistory.app.ui.reader

import android.app.Activity
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.AnrDiagLog
import com.ttsaistory.app.data.LibraryCategoryRow
import com.ttsaistory.app.data.LibraryStoryRow
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.elevenlabs.ElevenLabsPrefKeys
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
import com.ttsaistory.app.domain.mergeParagraphGridToStoredText
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
import com.ttsaistory.app.model.saveLastText
import com.ttsaistory.app.ui.library.OnlineCategoryHeadlessStoryTextSync
import com.ttsaistory.app.ui.library.OnlineWebStoryNextPagePrefetch
import com.ttsaistory.app.ui.library.OnlineWebStoryViewAheadPreload
import java.util.Locale
import kotlinx.coroutines.FlowPreview
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

/**
 * Ô phẳng lưới split: ưu tiên [speakingParagraphIndex]; khi không phát dùng
 * [dbLastSpeechSentenceIndex0] từ DB (`last_speech_sentence_index`) khi có chương thư viện; không có → 0.
 */
private fun readerSplitFlatFocusFromSpeechAndDb(
    paragraphSplitMode: Boolean,
    flatItemCount: Int,
    speakingParagraphIndex: Int,
    activeLibraryStoryId: Long?,
    dbLastSpeechSentenceIndex0: Int,
    fieldGroups: List<List<TextFieldValue>>,
): Int {
    if (!paragraphSplitMode || flatItemCount <= 0) return 0
    val resumeTts0 =
        when {
            speakingParagraphIndex >= 0 -> speakingParagraphIndex
            activeLibraryStoryId != null &&
                activeLibraryStoryId > 0L &&
                dbLastSpeechSentenceIndex0 >= 0 -> dbLastSpeechSentenceIndex0
            else -> -1
        }
    if (resumeTts0 < 0) return 0
    val cells = fieldGroups.flatMap { r -> r.map { it.text } }
    return editorUiFlatForTtsParagraphStartIndexForFlatCells(cells, resumeTts0)
        .coerceIn(0, flatItemCount - 1)
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
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
    onOpenLibraryStory: suspend (Long) -> Boolean,
    /** Đăng ký nút xuất AAC trên top bar; [null] khi ReaderTab huỷ (đổi tab / dispose). */
    onRegisterExportM4aForTopBar: ((ExportM4aTopBarState?) -> Unit)? = null,
    /** Đăng ký hàm flush bản nháp lưới câu lên [text] trước khi app nhận share / đổi truyện thư viện. */
    onRegisterParagraphDraftFlush: ((() -> Unit) -> Unit)? = null,
    /** Đăng ký hàm trả về chuỗi chuẩn hoá để ghi file thư viện khi đổi chương; `null` khi huỷ. */
    onRegisterLibraryTabTextSerializer: (((() -> String)?) -> Unit)? = null,
    /** Đăng ký hành động bottom bar (cuộn đầu/cuối / con trỏ đầu cuối); null khi huỷ đăng ký. */
    onRegisterReaderBottomNav: ((ReaderBottomNavBridge?) -> Unit)? = null,
    systemTtsSpeechRate: Float,
    systemTtsPitch: Float,
) {
    var paragraphSplitMode by rememberSaveable { mutableStateOf(true) }
    var paragraphFocusRequestToken by remember { mutableIntStateOf(0) }
    /** Sau «ghép vào truyện trước» khi đích là truyện đang mở: khôi phục ô đang focus sau khi parent sync lưới. */
    var postMergeParagraphFocusFlatToRestore by remember { mutableStateOf<Int?>(null) }
    var fullTextFieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var segments by remember { mutableStateOf(emptyList<String>()) }
    /** Chỉ khi đang sửa ô: chỉ số TTS đầu câu theo từng ô phẳng (toàn truyện). */
    var flatCellTtsStart by remember { mutableStateOf(intArrayOf()) }
    val listState = rememberLazyListState()
    var paragraphGroupFieldValues by remember {
        mutableStateOf(
            listOf(listOf(TextFieldValue("", TextRange(0)))),
        )
    }
    val flatItemCount = paragraphGroupFieldValues.sumOf { it.size }
    /** `last_speech_sentence_index` của chương thư viện đang mở (đọc từ DB). */
    var dbLastSpeechSentenceIndex0 by remember { mutableIntStateOf(-1) }
    val paragraphSplitPageSize = AppEditorConstants.PARAGRAPH_SPLIT_PAGE_SIZE
    var paragraphSplitPageIndex by rememberSaveable { mutableIntStateOf(0) }
    var playToolbarParagraphsDebounced by remember { mutableStateOf(emptyList<String>()) }
    /** Đồng bộ với [ParagraphTextService.totalItemCount] (cập nhật trong [splitIntoParagraphs] / parse). */
    val paragraphToolbarTtsTotal by ParagraphTextService.totalItemCount.collectAsState(initial = null)
    var toolbarTtsSplitWorking by remember { mutableStateOf(false) }
    var prevParagraphSplitMode by remember { mutableStateOf<Boolean?>(null) }
    /** [librarySyncEpoch] đã được phản ánh lên [paragraphGroupFieldValues] (mở chương / đồng bộ file). */
    var paragraphGridLastAppliedLibraryEpoch by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var readerKeyboardForceHidden by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.KEY_READER_FORCE_HIDE_SOFT_KEYBOARD, false),
        )
    }
    DisposableEffect(readerKeyboardForceHidden) {
        val act = ctx as? Activity
        val window = act?.window
        if (window != null && readerKeyboardForceHidden) {
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            )
        }
        onDispose {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
    }
    fun hideSoftInputWhenReaderForceHidden() {
        if (!readerKeyboardForceHidden) return
        keyboardController?.hide()
        val act = ctx as? Activity ?: return
        val imm =
            act.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val token = act.window?.decorView?.windowToken ?: return
        imm.hideSoftInputFromWindow(token, 0)
        val delayFirstMs =
            prefs
                .getInt(
                    AppPreferenceKeys.KEY_READER_IME_HIDE_DELAY_FIRST_MS,
                    AppPreferenceKeys.DEFAULT_READER_IME_HIDE_DELAY_FIRST_MS,
                )
                .coerceIn(0, 2000)
        val delaySecondMs =
            prefs
                .getInt(
                    AppPreferenceKeys.KEY_READER_IME_HIDE_DELAY_SECOND_MS,
                    AppPreferenceKeys.DEFAULT_READER_IME_HIDE_DELAY_SECOND_MS,
                )
                .coerceIn(0, 5000)
        scope.launch {
            delay(timeMillis = delayFirstMs.toLong())
            keyboardController?.hide()
            imm.hideSoftInputFromWindow(token, 0)
            delay(timeMillis=delaySecondMs.toLong())
            keyboardController?.hide()
            imm.hideSoftInputFromWindow(token, 0)
        }
    }
    val textTabToolbarScrollState = rememberScrollState()
    val fullTextScrollState = rememberScrollState()
    val fullTextFocusRequester = remember { FocusRequester() }
    val fullTextNativeEditRef = remember { java.util.concurrent.atomic.AtomicReference<EditText?>(null) }
    val nativeTextProgrammatic = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

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
    /** Fallback khi [activeLibraryStoryId] còn trỏ tới truyện đã xóa (vd. vừa ghép) — tránh rơi vào thể loại đầu tiên ngẫu nhiên. */
    var lastLibraryStoryPickerCategoryId by remember { mutableStateOf<Long?>(null) }
    val libraryStoryPickerLoadJob = remember { mutableStateOf<Job?>(null) }
    var moveStoryTitleDraft by remember { mutableStateOf("") }
    var textEditorChromeViewOnly by rememberSaveable { mutableStateOf(true) }
    var wasTextEditorChromeViewOnly by remember { mutableStateOf(textEditorChromeViewOnly) }
    var webPrefetchChapterQueueLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var webStoryQueueTargetStoryId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(activeLibraryStoryId) {
        webStoryQueueTargetStoryId = null
    }

    LaunchedEffect(activeLibraryStoryId, librarySyncEpoch) {
        val sid = activeLibraryStoryId ?: return@LaunchedEffect
        val cid =
            withContext(Dispatchers.IO) {
                libraryRepository.getStory(sid)?.categoryId
            } ?: return@LaunchedEffect
        lastLibraryStoryPickerCategoryId = cid
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
                val sidSnapshot = activeLibraryStoryId
                val lastCatSnapshot = lastLibraryStoryPickerCategoryId
                val triple =
                    withContext(Dispatchers.IO) {
                        if (sidSnapshot != null) {
                            val row = libraryRepository.getStory(sidSnapshot)
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
                        val lc = lastCatSnapshot
                        if (lc != null) {
                            val cats = libraryRepository.listCategories()
                            val cat = cats.find { it.id == lc }
                            if (cat != null) {
                                return@withContext Triple(
                                    lc,
                                    cat.name,
                                    libraryRepository.listStories(lc),
                                )
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
                            "Chưa có truyện trong thư viện.",
                            Toast.LENGTH_LONG,
                        ).show()
                        libraryStoryPickerOpen = false
                        libraryStoryPickerLoading = false
                        return@withContext
                    }
                    val (cid, cname, stories) = triple
                    lastLibraryStoryPickerCategoryId = cid
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
    val readerSplitFlatFocusIndex =
        readerSplitFlatFocusFromSpeechAndDb(
            paragraphSplitMode,
            flatItemCount,
            speakingParagraphIndex,
            activeLibraryStoryId,
            dbLastSpeechSentenceIndex0,
            paragraphGroupFieldValues,
        )
    val editorAppearance = rememberReaderTabEditorAppearance(prefs, prefsBridge.fontPrefsEpoch)
    LaunchedEffect(activeLibraryStoryId, librarySyncEpoch, bookmarkResetKey) {
        val sid = activeLibraryStoryId
        if (sid == null || sid <= 0L) {
            dbLastSpeechSentenceIndex0 = -1
            return@LaunchedEffect
        }
        val idx =
            withContext(Dispatchers.IO) {
                libraryRepository.getStory(sid)?.lastSpeechSentenceIndex
            } ?: -1
        dbLastSpeechSentenceIndex0 = idx
    }

    val latestParagraphSplit by rememberUpdatedState(paragraphSplitMode)
    val latestParagraphFieldGroups by rememberUpdatedState(paragraphGroupFieldValues)
    val latestOnTextChange by rememberUpdatedState(onTextChange)
    val latestParentText by rememberUpdatedState(text)
    val paragraphSplitEditSink = remember { ReaderParagraphSplitEditActionSink() }
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
                    withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(rows1) }
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
                    withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(rows2) }
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
                        withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(rows3) }
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
        mergeParagraphGridToStoredText(paragraphGroupFieldValues.map { r -> r.map { it.text } })

    /** Chuỗi lưu thư viện từ trạng thái editor hiện tại (lưới câu hoặc toàn văn). */
    fun serializedLibraryBodyNow(): String {
        val raw =
            if (paragraphSplitMode) mergedParagraphFields() else fullTextFieldValue.text
        return canonicalTextFromRaw(raw)
    }

    fun scheduleDebouncedParagraphParentPersist() {
        persistDebouncer.job?.cancel()
        persistDebouncer.job =
            scope.launch {
                delay(timeMillis = AppEditorConstants.PARAGRAPH_FIELD_PERSIST_DEBOUNCE_MS)
                val rows = paragraphGroupFieldValues.map { r -> r.map { it.text } }
                val merged =
                    withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(rows) }
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

    fun enqueueTtsExport() {
        val run: () -> Unit = {
            scope.launch {
                val exportBody =
                    ParagraphTextService.lastCachedFlatSentencesForAacExport()
                        ?.joinToString("\n")
                        .orEmpty()
                if (exportBody.isBlank()) {
                    Toast.makeText(
                        ctx,
                        "Không có nội dung để xuất",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                val bodyFile =
                    withContext(Dispatchers.IO) {
                        val f =
                            File(ctx.cacheDir, "tts_export_body_${System.currentTimeMillis()}.txt")
                        try {
                            f.writeText(exportBody, Charsets.UTF_8)
                            f
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    ctx,
                                    "Lỗi ghi file tạm: ${e.message ?: e.javaClass.simpleName}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            null
                        }
                    }
                if (bodyFile == null) return@launch
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
                val flat = readerSplitFlatFocusIndex.coerceIn(0, maxFlat)
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
        val prevChrome = wasTextEditorChromeViewOnly
        val enteredViewOnly = textEditorChromeViewOnly && !prevChrome
        val enteredEdit = !textEditorChromeViewOnly && prevChrome
        wasTextEditorChromeViewOnly = textEditorChromeViewOnly
        if (enteredEdit) {
            if (paragraphSplitMode) {
                ParagraphTextService.setChapterText(text)
                val cells =
                    ParagraphTextService.chapterParagraphs.value
                        .map(::sanitizeParagraphText)
                        .filter { it.isNotEmpty() }
                val row = if (cells.isEmpty()) listOf("") else cells
                paragraphGroupFieldValues =
                    listOf(row.map { s -> TextFieldValue(s, TextRange(s.length)) })
                prevParagraphSplitMode = true
                paragraphGridLastAppliedLibraryEpoch = librarySyncEpoch
            } else {
                val t = text
                fullTextFieldValue = TextFieldValue(t, TextRange(t.length))
            }
        }
        if (!enteredViewOnly) return@LaunchedEffect
        if (!paragraphSplitMode) return@LaunchedEffect
        flushParagraphParentPersist()
        if (!textEditorChromeViewOnly || !paragraphSplitMode) return@LaunchedEffect
        val cellCount = paragraphGroupFieldValues.sumOf { it.size }
        val t0 =
            AnrDiagLog.begin(
                "ReaderTab preserveSplitCells(leaveEditViewOnly) cells=$cellCount",
            )
        // Không gọi paragraphMainGroupsForEditor(merge→parse): merge nối ô bằng khoảng trắng
        // làm mất ranh giới câu không có dấu câu / | — giữ đúng từng ô sau flush.
        paragraphGroupFieldValues =
            paragraphGroupFieldValues.map { row ->
                row.map { tf ->
                    val t = tf.text
                    TextFieldValue(t, TextRange(t.length))
                }
            }
        prevParagraphSplitMode = true
        paragraphGridLastAppliedLibraryEpoch = librarySyncEpoch
        AnrDiagLog.end("ReaderTab preserveSplitCells(leaveEditViewOnly) done", t0)
    }

    /** Đồng bộ prefs bookmark TTS khi ô UI (flat) đổi — chỉ dùng ánh xạ ô→TTS, không merge/split cả văn bản (tránh ANR). */
    fun persistLastReadingBookmarkFromEditorFlat(
        groups: List<List<String>>,
        editorUiFlat: Int,
    ) {
        if (!paragraphSplitMode || groups.isEmpty()) return
        val sid = activeLibraryStoryId ?: return
        if (sid <= 0L) return
        val maxUi = (groups.sumOf { it.size } - 1).coerceAtLeast(0)
        val v = editorUiFlat.coerceIn(0, maxUi)
        val tts = editorUiFlatToTtsParagraphStartIndex(groups, v).coerceAtLeast(0)
        dbLastSpeechSentenceIndex0 = tts
        scope.launch(Dispatchers.IO) {
            libraryRepository.updateLastSpeechSentenceIndex(sid, tts)
        }
    }

    /** Như [persistLastReadingBookmarkFromEditorFlat] nhưng không tạo bản sao List<List<String>> toàn lưới (chạm ô). */
    fun persistLastReadingBookmarkFromEditorFieldFlat(
        fieldGroups: List<List<TextFieldValue>>,
        editorUiFlat: Int,
    ) {
        if (!paragraphSplitMode || fieldGroups.isEmpty()) return
        val sid = activeLibraryStoryId ?: return
        if (sid <= 0L) return
        val maxUi = (fieldGroups.sumOf { it.size } - 1).coerceAtLeast(0)
        val v = editorUiFlat.coerceIn(0, maxUi)
        val tts = editorFlatToTtsBookmarkIndex(fieldGroups, v).coerceAtLeast(0)
        dbLastSpeechSentenceIndex0 = tts
        scope.launch(Dispatchers.IO) {
            libraryRepository.updateLastSpeechSentenceIndex(sid, tts)
        }
    }

    /**
     * User chọn một ô (chỉ xem hoặc sửa): ghi bookmark câu TTS + DB (truyện thư viện) / prefs;
     * ô highlight theo [readerSplitFlatFocusIndex] (đọc + câu đang phát).
     */
    fun onUserSelectedParagraphSplitCell(flatIdx: Int) {
        if (!paragraphSplitMode) return
        val gl = latestParagraphFieldGroups
        val maxUi = (gl.sumOf { it.size } - 1).coerceAtLeast(0)
        if (maxUi < 0) return
        val v = flatIdx.coerceIn(0, maxUi)
        if (v == readerSplitFlatFocusIndex) {
            paragraphFocusRequestToken++
        }
        persistLastReadingBookmarkFromEditorFieldFlat(gl, v)
    }

    SideEffect {
        onRegisterParagraphDraftFlush?.invoke { flushParagraphParentPersist() }
    }

    SideEffect {
        onRegisterExportM4aForTopBar?.invoke(
            ExportM4aTopBarState(
                onClick = { enqueueTtsExport() },
                enabled =
                    exportUiFromCoordinator == null &&
                        textEditorChromeViewOnly &&
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
    DisposableEffect(onRegisterLibraryTabTextSerializer) {
        onRegisterLibraryTabTextSerializer?.invoke { serializedLibraryBodyNow() }
        onDispose { onRegisterLibraryTabTextSerializer?.invoke(null) }
    }
    val latestFlatItemCount by rememberUpdatedState(flatItemCount)
    val latestSpeakingParagraphIndex by rememberUpdatedState(speakingParagraphIndex)
    val latestSystemTtsPlaybackActive by rememberUpdatedState(systemTtsPlaybackActive)
    val latestElevenLabsJobActive by rememberUpdatedState(elevenLabsJobActive)
    val latestOnPlayParagraphs by rememberUpdatedState(onPlayParagraphs)

    fun scrollParagraphLazyToGlobalFlat(
        globalFlat: Int,
        /** Khi gọi ngay sau cập nhật lưới ô (vd. merge), [flatItemCount] có thể còn snapshot frame trước — truyền tổng ô mới. */
        flatCountForPaging: Int? = null,
    ) {
        val n = flatCountForPaging ?: flatItemCount
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
            persistLastReadingBookmarkFromEditorFieldFlat(paragraphGroupFieldValues, 0)
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
                persistLastReadingBookmarkFromEditorFieldFlat(paragraphGroupFieldValues, n - 1)
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
        val splitEditFocusFingerprint =
            if (
                paragraphSplitMode &&
                !textEditorChromeViewOnly &&
                flatItemCount > 0
            ) {
                val fi = readerSplitFlatFocusIndex.coerceIn(0, flatItemCount - 1)
                val (m, s) = flatIndexToMainSub(paragraphGroupFieldValues, fi)
                val tf = paragraphGroupFieldValues.getOrNull(m)?.getOrNull(s)
                "${fi}|${tf?.text?.length ?: 0}"
            } else {
                ""
            }
        val bottomNavPublishKey =
            listOf(
                paragraphSplitMode,
                textEditorChromeViewOnly,
                flatItemCount,
                readerSplitFlatFocusIndex,
                dbLastSpeechSentenceIndex0,
                cellStructureFingerprint,
                splitEditFocusFingerprint,
                paragraphToolbarTtsTotal?.toString() ?: "n",
                toolbarTtsSplitWorking,
                playToolbarParagraphsDebounced.size,
                webPrefetchChapterQueueLines.size,
                activeStoryHasWebUrl,
                webStoryQueueTargetStoryId?.toString() ?: "null",
                readerKeyboardForceHidden,
            ).joinToString("|")
        if (bottomNavPublishKey == lastBottomNavPublishKey) {
            return@SideEffect
        }
        lastBottomNavPublishKey = bottomNavPublishKey
        onRegisterReaderBottomNav?.invoke(
            ReaderBottomNavBridge(
                paragraphSplitMode = paragraphSplitMode,
                showPasteAndCaretStep = !textEditorChromeViewOnly,
                showParagraphFocusSlider =
                    paragraphSplitMode && textEditorChromeViewOnly && flatItemCount > 0,
                showParagraphSplitEditBar =
                    paragraphSplitMode && !textEditorChromeViewOnly && flatItemCount > 0,
                paragraphSplitEditJoinUpEnabled =
                    paragraphSplitMode &&
                        !textEditorChromeViewOnly &&
                        flatItemCount > 0 &&
                        readerSplitFlatFocusIndex > 0,
                paragraphSplitEditDeleteEnabled =
                    if (paragraphSplitMode && !textEditorChromeViewOnly && flatItemCount > 0) {
                        val fi = readerSplitFlatFocusIndex.coerceIn(0, flatItemCount - 1)
                        val (m, s) = flatIndexToMainSub(paragraphGroupFieldValues, fi)
                        paragraphGroupFieldValues.getOrNull(m)?.getOrNull(s)?.text?.isNotEmpty() == true
                    } else {
                        false
                    },
                onParagraphSplitEditJoinUp = { paragraphSplitEditSink.joinUp() },
                onParagraphSplitEditSplitAtCaret = { paragraphSplitEditSink.splitAtCaret() },
                onParagraphSplitEditDelete = { paragraphSplitEditSink.deleteCell() },
                readerKeyboardForceHidden = readerKeyboardForceHidden,
                onReaderKeyboardForceHiddenToggle = {
                    readerKeyboardForceHidden = !readerKeyboardForceHidden
                    prefs
                        .edit()
                        .putBoolean(
                            AppPreferenceKeys.KEY_READER_FORCE_HIDE_SOFT_KEYBOARD,
                            readerKeyboardForceHidden,
                        )
                        .apply()
                    if (readerKeyboardForceHidden) {
                        focusManager.clearFocus(force = true)
                    }
                    hideSoftInputWhenReaderForceHidden()
                },
                paragraphFocusSliderMax = (flatItemCount - 1).coerceAtLeast(0),
                paragraphFocusSliderValue =
                    readerSplitFlatFocusIndex.coerceIn(0, (flatItemCount - 1).coerceAtLeast(0)),
                onParagraphFocusSliderChange = { newUiFlat ->
                    onUserSelectedParagraphSplitCell(newUiFlat)
                    paragraphFocusRequestToken++
                },
                onParagraphFocusSliderFocusCommitted = {
                    val gl = latestParagraphFieldGroups
                    val maxUi = (gl.sumOf { it.size } - 1).coerceAtLeast(0)
                    val idx = readerSplitFlatFocusIndex.coerceIn(0, maxUi)
                    val wasPlaying =
                        latestSpeakingParagraphIndex >= 0 ||
                            latestSystemTtsPlaybackActive ||
                            latestElevenLabsJobActive
                    if (paragraphSplitMode && wasPlaying) {
                        val groups = gl.map { r -> r.map { it.text } }
                        val merged = mergeParagraphGridToStoredText(groups)
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
                            readerSplitFlatFocusIndex.coerceIn(
                                0,
                                (flatItemCount - 1).coerceAtLeast(0),
                            )
                        editorUiFlatToTtsParagraphStartIndex(groups, ui) + 1
                    } else {
                        null
                    },
                dbLastSpeechSentenceIndex0 = dbLastSpeechSentenceIndex0,
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
                                val flat = readerSplitFlatFocusIndex.coerceIn(0, maxFlat)
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
                                    persistLastReadingBookmarkFromEditorFieldFlat(
                                        paragraphGroupFieldValues,
                                        flat.coerceIn(0, newMax),
                                    )
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
                            val flat = readerSplitFlatFocusIndex.coerceIn(0, maxFlat)
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
                            val flat = readerSplitFlatFocusIndex.coerceIn(0, maxFlat)
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
                    mergeParagraphGridToStoredText(
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
        // Trong lưới câu + chế độ sửa ô, [text] vẫn nhảy theo debounce parent — không cần
        // paragraphsForEditor toàn văn (ReaderTab chỉ dùng segments khi không split / bookmark).
        if (paragraphSplitMode && !textEditorChromeViewOnly) return@LaunchedEffect
        val snapshot = text
        val t0 = AnrDiagLog.begin("ReaderTab paragraphsForEditor len=${snapshot.length}")
        val computed =
            withContext(Dispatchers.Default) {
                ParagraphTextService.setChapterText(snapshot)
                paragraphsForEditor(snapshot)
            }
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
            postMergeParagraphFocusFlatToRestore = null
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
            withContext(Dispatchers.Default) {
                ParagraphTextService.setChapterText(snapshot)
                paragraphMainGroupsForEditor()
            }
        if (snapshot != text || !paragraphSplitMode) {
            AnrDiagLog.end("ReaderTab paragraphMainGroupsForEditor libSync CANCELLED", t0)
            toolbarTtsSplitWorking = false
            postMergeParagraphFocusFlatToRestore = null
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
        paragraphGridLastAppliedLibraryEpoch = librarySyncEpoch
        try {
            val mergedFromLib =
                withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(segs) }
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
            activeLibraryStoryId?.takeIf { it > 0L }?.let { sid ->
                withContext(Dispatchers.IO) {
                    libraryRepository.getStory(sid)?.lastSpeechSentenceIndex
                }
            } ?: -1
        val newCellCount = segs.sumOf { it.size }
        if (savedParagraph < 0 &&
            newCellCount > 0 &&
            postMergeParagraphFocusFlatToRestore == null
        ) {
            paragraphSplitPageIndex = 0
            persistLastReadingBookmarkFromEditorFieldFlat(paragraphGroupFieldValues, 0)
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
        val mergeRestoreFlat = postMergeParagraphFocusFlatToRestore
        if (mergeRestoreFlat != null) {
            postMergeParagraphFocusFlatToRestore = null
            delay(120)
            if (snapshot != text || !paragraphSplitMode) return@LaunchedEffect
            val maxFlat = (newCellCount - 1).coerceAtLeast(0)
            if (maxFlat >= 0) {
                val ui = mergeRestoreFlat.coerceIn(0, maxFlat)
                persistLastReadingBookmarkFromEditorFieldFlat(paragraphGroupFieldValues, ui)
                paragraphFocusRequestToken++
                scrollParagraphLazyToGlobalFlat(ui, flatCountForPaging = newCellCount)
            }
        }
    }

    LaunchedEffect(paragraphSplitMode, text, librarySyncEpoch) {
        if (!paragraphSplitMode) {
            prevParagraphSplitMode = false
            return@LaunchedEffect
        }
        // Đang sửa ô: không parse lại từ [text] khi cùng [librarySyncEpoch] đã áp lưới (tránh đè bản nháp
        // khi parent chậm debounce). Mở chương / sync bump epoch → LaunchedEffect(librarySyncEpoch) hoặc
        // nhánh dưới (merged != text) vẫn chạy.
        if (!textEditorChromeViewOnly &&
            prevParagraphSplitMode == true &&
            librarySyncEpoch == paragraphGridLastAppliedLibraryEpoch
        ) {
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
            withContext(Dispatchers.Default) {
                ParagraphTextService.setChapterText(snapshot)
                paragraphMainGroupsForEditor()
            }
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
            paragraphGridLastAppliedLibraryEpoch = librarySyncEpoch
            if (!textEditorChromeViewOnly) {
                toolbarTtsSplitWorking = true
                try {
                    val rows = paragraphGroupFieldValues.map { r -> r.map { it.text } }
                    val mergedR =
                        withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(rows) }
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
        val tMerge = AnrDiagLog.begin("ReaderTab mergeParagraphGridToStoredText(Default)")
        val merged =
            withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(fieldRows) }
        AnrDiagLog.end("ReaderTab mergeParagraphGridToStoredText(Default) mergedLen=${merged.length}", tMerge)
        if (snapshot != text || !paragraphSplitMode) return@LaunchedEffect
        if (merged != text) {
            paragraphGroupFieldValues =
                segs.map { row ->
                    row.map { s ->
                        TextFieldValue(text = s, selection = TextRange(s.length))
                    }
                }
            paragraphGridLastAppliedLibraryEpoch = librarySyncEpoch
            if (!textEditorChromeViewOnly) {
                toolbarTtsSplitWorking = true
                try {
                    val rows = paragraphGroupFieldValues.map { r -> r.map { it.text } }
                    val mergedR =
                        withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(rows) }
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

    /** Lưới câu + vừa tắt chỉ xem: cập nhật tổng câu bottom bar (không chạy khi đang gõ vì [textEditorChromeViewOnly] không đổi). */
    LaunchedEffect(textEditorChromeViewOnly, paragraphSplitMode) {
        if (textEditorChromeViewOnly || !paragraphSplitMode) return@LaunchedEffect
        yield()
        toolbarTtsSplitWorking = true
        try {
            val rows = paragraphGroupFieldValues.map { r -> r.map { it.text } }
            val mergedR = withContext(Dispatchers.Default) { mergeParagraphGridToStoredText(rows) }
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

    // Cuộn lưới split tới ô ánh xạ từ câu TTS đang phát hoặc `last_speech_sentence_index` trong DB.
    LaunchedEffect(
        paragraphSplitMode,
        librarySyncEpoch,
        readerSplitFlatFocusIndex,
        paragraphFocusRequestToken,
        flatItemCount,
        bookmarkResetKey,
        segments.size,
        activeLibraryStoryId,
    ) {
        if (!paragraphSplitMode || flatItemCount <= 0) return@LaunchedEffect
        if (textEditorChromeViewOnly && segments.isEmpty()) return@LaunchedEffect
        delay(32)
        animateParagraphLazyToGlobalFlat(readerSplitFlatFocusIndex)
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
                    if (textEditorChromeViewOnly) {
                        val wasPlaying =
                            speakingParagraphIndex >= 0 ||
                                (
                                    speechEngine == TextTabSpeechEngine.System &&
                                        systemTtsPlaybackActive
                                ) ||
                                (speechEngine == TextTabSpeechEngine.ElevenLabs && elevenLabsJobActive)
                        if (wasPlaying) {
                            onStopAllSpeechReading()
                        }
                    }
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
                    val bookmark = dbLastSpeechSentenceIndex0
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
                        textEditorChromeViewOnly &&
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
                                    "Không tìm thấy chương trong thư viện",
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
                    scope.launch {
                        val prev =
                            withContext(Dispatchers.IO) {
                                libraryRepository.previousStoryInCategoryBefore(sid)
                            }
                        if (prev == null) {
                            Toast.makeText(
                                ctx,
                                "Không có chương trước trong truyện.",
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
                    scope.launch {
                        val next =
                            withContext(Dispatchers.IO) {
                                libraryRepository.nextStoryInCategoryAfter(sid)
                            }
                        if (next == null) {
                            Toast.makeText(
                                ctx,
                                "Không có chương sau trong truyện.",
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
                                                "Chọn hoặc tạo truyện",
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
                                e.message ?: "Lỗi tạo chương",
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
                                        ?: error("Không tìm thấy chương")
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
                val cellTotal = paragraphGroupFieldValues.sumOf { it.size }
                val maxFlat = (cellTotal - 1).coerceAtLeast(0)
                val bf =
                    readerSplitFlatFocusFromSpeechAndDb(
                        paragraphSplitMode,
                        cellTotal,
                        speakingParagraphIndex,
                        activeLibraryStoryId,
                        dbLastSpeechSentenceIndex0,
                        paragraphGroupFieldValues,
                    )
                if (maxFlat >= 0 && bf > maxFlat) {
                    persistLastReadingBookmarkFromEditorFieldFlat(paragraphGroupFieldValues, maxFlat)
                }
                if (cellTotal <= 0) {
                    paragraphSplitPageIndex = 0
                } else {
                    val maxPage =
                        (cellTotal + paragraphSplitPageSize - 1) / paragraphSplitPageSize - 1
                    if (paragraphSplitPageIndex > maxPage) {
                        paragraphSplitPageIndex = maxPage
                    }
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
                val preTotal = paragraphGroupFieldValues.sumOf { it.size }
                val curFlat =
                    readerSplitFlatFocusFromSpeechAndDb(
                        paragraphSplitMode,
                        preTotal,
                        speakingParagraphIndex,
                        activeLibraryStoryId,
                        dbLastSpeechSentenceIndex0,
                        paragraphGroupFieldValues,
                    )
                paragraphGroupFieldValues = compactParagraphGroupFieldValues(gl)
                val newTotal = paragraphGroupFieldValues.sumOf { it.size }
                val maxNew = (newTotal - 1).coerceAtLeast(0)
                val newFlat =
                    when {
                        curFlat == atFlat -> atFlat - 1
                        curFlat > atFlat -> curFlat - 1
                        else -> curFlat
                    }.coerceIn(0, maxNew)
                persistLastReadingBookmarkFromEditorFieldFlat(paragraphGroupFieldValues, newFlat)
                paragraphFocusRequestToken++
                clampFlatFocus()
                scrollParagraphLazyToGlobalFlat(
                    newFlat,
                    flatCountForPaging = newTotal,
                )
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
                val newFlat = flatIndexFromMainSub(paragraphGroupFieldValues, newMain, 0)
                persistLastReadingBookmarkFromEditorFieldFlat(paragraphGroupFieldValues, newFlat)
                paragraphFocusRequestToken++
                scheduleDebouncedParagraphParentPersist()
                return true
            }
            /** Tách xuống dòng mới trong file. Chỉ khi hai phần quanh con trỏ đều còn chữ sau chuẩn hoá. */
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
            SideEffect {
                paragraphSplitEditSink.joinUp = {
                    keyboardController?.hide()
                    val flat = readerSplitFlatFocusIndex
                    if (flat > 0 &&
                        !mergeParagraphBackward(flat, requireCaretAtStart = false)
                    ) {
                        Toast.makeText(
                            ctx,
                            "Không thể nối với câu trước.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                paragraphSplitEditSink.splitAtCaret = {
                    keyboardController?.hide()
                    val flat = readerSplitFlatFocusIndex
                    if (!splitParagraphAtCaretForToolbar(flat)) {
                        Toast.makeText(
                            ctx,
                            "Đặt con trỏ giữa nội dung để tách xuống dòng (như Enter).",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                paragraphSplitEditSink.deleteCell = {
                    keyboardController?.hide()
                    clearParagraphCellText(readerSplitFlatFocusIndex)
                }
            }
            val paragraphPageStartFlat = paragraphSplitPageIndex * paragraphSplitPageSize
            val paragraphPageEndFlat =
                (paragraphPageStartFlat + paragraphSplitPageSize).coerceAtMost(flatItemCount)
            val paragraphPageItemCount = (paragraphPageEndFlat - paragraphPageStartFlat).coerceAtLeast(0)
            ReaderParagraphSplitSentenceLazyGrid(
                listState = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                paragraphPageItemCount = paragraphPageItemCount,
                paragraphPageStartFlat = paragraphPageStartFlat,
                paragraphGroupFieldValues = paragraphGroupFieldValues,
                textEditorChromeViewOnly = textEditorChromeViewOnly,
                flatCellTtsStart = flatCellTtsStart,
                speakingParagraphIndex = speakingParagraphIndex,
                paragraphSplitMode = paragraphSplitMode,
                focusedParagraphIndex = readerSplitFlatFocusIndex,
                flatItemCount = flatItemCount,
                paragraphFocusRequestToken = paragraphFocusRequestToken,
                readerKeyboardForceHidden = readerKeyboardForceHidden,
                editorAppearance = editorAppearance,
                onUserSelectedParagraphSplitCell = ::onUserSelectedParagraphSplitCell,
                hideSoftInputWhenReaderForceHidden = ::hideSoftInputWhenReaderForceHidden,
                onMergeParagraphBackwardFromCell = { mergeParagraphBackward(it) },
                onParagraphCellValueChange = cell@{ mainIdx, subIdx, flatIdx, newVal, tryMerge ->
                    if (textEditorChromeViewOnly) return@cell
                    if (readerKeyboardForceHidden) return@cell
                    val old =
                        paragraphGroupFieldValues.getOrNull(mainIdx)?.getOrNull(subIdx)
                            ?: return@cell
                    if (flatIdx > 0 &&
                        old.selection.collapsed &&
                        old.selection.start == 0 &&
                        old.text.isNotEmpty() &&
                        newVal.text == old.text.drop(1)
                    ) {
                        if (tryMerge()) return@cell
                    }
                    val gl =
                        paragraphGroupFieldValues.map { it.toMutableList() }.toMutableList()
                    if (mainIdx in gl.indices && subIdx in gl[mainIdx].indices) {
                        gl[mainIdx][subIdx] = newVal
                        paragraphGroupFieldValues = compactParagraphGroupFieldValues(gl)
                        clampFlatFocus()
                        scheduleDebouncedParagraphParentPersist()
                    }
                },
            )
        } else {
            ReaderTabFullTextEditor(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                text = text,
                fullTextFieldValue = fullTextFieldValue,
                onFullTextFieldValueChange = { fullTextFieldValue = it },
                onTextChange = onTextChange,
                textEditorChromeViewOnly = textEditorChromeViewOnly,
                editorAppearance = editorAppearance,
                fullTextScrollState = fullTextScrollState,
                fullTextFocusRequester = fullTextFocusRequester,
                fullTextNativeEditRef = fullTextNativeEditRef,
                nativeTextProgrammatic = nativeTextProgrammatic,
                fullTextNativeTypingSink = fullTextNativeTypingSink,
            )
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
            scope.launch {
                onOpenLibraryStory(id)
            }
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
                        e.message ?: "Không xóa được chương",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        onJoinStoryIntoPrevious = { appendFromId, targetId ->
            scope.launch {
                val cid = libraryStoryPickerCategoryId ?: return@launch
                try {
                    if (paragraphSplitMode) flushParagraphParentPersist()
                    val bodyToPersist = serializedLibraryBodyNow()
                    val sid = activeLibraryStoryId
                    if (paragraphSplitMode && sid == targetId && flatItemCount > 0) {
                        postMergeParagraphFocusFlatToRestore =
                            readerSplitFlatFocusIndex.coerceIn(0, flatItemCount - 1)
                    }
                    withContext(Dispatchers.IO) {
                        if (sid != null) {
                            val saveCurrentToLibrary =
                                !textEditorChromeViewOnly ||
                                    sid == appendFromId ||
                                    sid == targetId
                            if (saveCurrentToLibrary) {
                                libraryRepository.updateStoryTextIfExists(sid, bodyToPersist)
                            }
                        }
                        libraryRepository.joinAppendStoryIntoTarget(targetId, appendFromId)
                    }
                    libraryStoryPickerStories =
                        withContext(Dispatchers.IO) {
                            libraryRepository.listStories(cid)
                        }
                    lastLibraryStoryPickerCategoryId = cid
                    libraryStoryPickerOpen = false
                    libraryStoryPickerCategoryId = null
                    // Phải mở chương đích *trước* onLibraryDataChanged: bump libraryRefreshTrigger khi active còn
                    // trỏ chương vừa xóa (append) khiến LaunchedEffect(AppTabs) gán active = null → mất prev/next & highlight.
                    val opened = onOpenLibraryStory(targetId)
                    if (!opened) {
                        Toast.makeText(
                            ctx,
                            "Không mở được chương đích sau ghép.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    onLibraryDataChanged()
                } catch (e: Exception) {
                    postMergeParagraphFocusFlatToRestore = null
                    Toast.makeText(
                        ctx,
                        e.message ?: "Không ghép được chương",
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
