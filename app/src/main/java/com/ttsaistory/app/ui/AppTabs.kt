package com.ttsaistory.app.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.PowerManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ttsaistory.app.AnrDiagLog
import com.ttsaistory.app.MainActivity
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.elevenlabs.ElevenLabsPrefKeys
import com.ttsaistory.app.elevenlabs.ElevenLabsSettingsScreen
import com.ttsaistory.app.domain.copyPickedDocumentToDownloadsTtsAiStoryFolder
import com.ttsaistory.app.domain.canonicalTextFromRaw
import com.ttsaistory.app.domain.documentTreeDisplayName
import com.ttsaistory.app.domain.fetchUrlAsPlainText
import com.ttsaistory.app.domain.isVietnameseTtsVoice
import com.ttsaistory.app.domain.parseHttpUrlFromSharedText
import com.ttsaistory.app.domain.buildParagraphSpeakJobs
import com.ttsaistory.app.domain.parseTtsParagraphIndex
import com.ttsaistory.app.domain.wordCountForTtsPlaybackWpm
import com.ttsaistory.app.domain.persistInboundSharedTextToLibrary
import com.ttsaistory.app.domain.persistOpenedTextFileToLibrary
import com.ttsaistory.app.domain.readSendStreamAsText
import com.ttsaistory.app.domain.resolveDocumentDisplayName
import com.ttsaistory.app.domain.uriLooksLikeEpubArchive
import com.ttsaistory.app.domain.uriLooksLikePdf
import com.ttsaistory.app.domain.uriLooksLikeZipArchive
import com.ttsaistory.app.domain.shouldTreatViewUriAsTxt
import com.ttsaistory.app.domain.splitIntoParagraphs
import com.ttsaistory.app.domain.sanitizeParagraphText
import com.ttsaistory.app.model.AppEditorConstants
import com.ttsaistory.app.model.AppPreferenceKeys
import com.ttsaistory.app.model.InboundLibraryPersistResult
import com.ttsaistory.app.model.LibraryCategoryToolbarCommand
import com.ttsaistory.app.model.TextTabSpeechEngine
import com.ttsaistory.app.ui.library.OpenFileProgressDialog
import com.ttsaistory.app.ui.library.OpenFileProgressLogDialog
import com.ttsaistory.app.ui.library.OnlineCategoryHeadlessStoryTextSync
import com.ttsaistory.app.ui.library.OpenFileProgressLogUi
import com.ttsaistory.app.ui.library.OpenFileProgressUi
import com.ttsaistory.app.ui.fonts.EditorFontConfigDialog
import com.ttsaistory.app.ui.reader.ExportM4aTopBarState
import com.ttsaistory.app.ui.SystemTtsSettingsScreen
import com.ttsaistory.app.ui.reader.ReaderBottomNavBridge
import com.ttsaistory.app.model.saveLastText
import com.ttsaistory.app.speech.ElevenLabsParagraphSpeechEngine
import com.ttsaistory.app.speech.ParagraphSpeechEngines
import com.ttsaistory.app.speech.ParagraphSpeechSequenceCallbacks
import com.ttsaistory.app.speech.SystemParagraphSpeechEngine
import com.ttsaistory.app.speech.SystemTtsUtteranceProgressSink
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Mở một truyện thư viện theo id (đọc file, bookmark, tab Văn bản).
 * Dùng chung cho mở từ thư viện và sau khi nhập ZIP/EPUB/PDF.
 * @return false nếu không mở được (đã khôi phục [active] khi đọc file lỗi)
 */
private suspend fun openLibraryStoryByIdForMainTabs(
    context: Context,
    storyLibrary: StoryLibraryRepository,
    storyId: Long,
    previousActiveLibraryStoryId: Long?,
    stopAllSpeechReading: () -> Unit,
    setActiveLibraryStoryId: (Long?) -> Unit,
    setText: (String) -> Unit,
    prefs: SharedPreferences,
    bumpLibraryRefresh: () -> Unit,
    bumpLibrarySyncEpoch: () -> Unit,
    onSwitchToTextTab: () -> Unit,
    paragraphDraftFlush: (() -> Unit)?,
    serializeOpenTabTextForLibrary: () -> String,
): Boolean {
    stopAllSpeechReading()
    paragraphDraftFlush?.invoke()
    yield()
    val prevSid = previousActiveLibraryStoryId
    if (prevSid != null && prevSid != storyId) {
        val prevRowStillPresent =
            withContext(Dispatchers.IO) { storyLibrary.getStory(prevSid) != null }
        // Sau «ghép vào chương trước», chương nguồn đã bị xóa — không cố ghi nháp lên id đó (updateStoryTextIfExists trả false → Toast sai).
        if (prevRowStillPresent) {
            val body = serializeOpenTabTextForLibrary()
            val diskCanon =
                withContext(Dispatchers.IO) {
                    storyLibrary.readStoryText(prevSid)?.let { canonicalTextFromRaw(it) }.orEmpty()
                }
            if (body != diskCanon) {
                val ok =
                    withContext(Dispatchers.IO) {
                        storyLibrary.updateStoryTextIfExists(prevSid, body)
                    }
                if (!ok) {
                    Toast.makeText(
                        context,
                        "Không lưu được chương trước khi đổi truyện.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                bumpLibraryRefresh()
            }
        }
    }
    val rowForRefresh =
        withContext(Dispatchers.IO) {
            storyLibrary.getStory(storyId)
        }
    if (rowForRefresh == null) {
        Toast.makeText(
            context,
            "Không tìm thấy chương trong thư viện.",
            Toast.LENGTH_SHORT,
        ).show()
        return false
    }
    setActiveLibraryStoryId(storyId)
    if (storyLibrary.storyNeedsOnlineContentRefresh(rowForRefresh)) {
        try {
            OnlineCategoryHeadlessStoryTextSync.syncOnlineStoryFromWebPage(
                context = context,
                storyId = storyId,
                repository = storyLibrary,
            )
            bumpLibraryRefresh()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: "Không tải được nội dung từ web",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val body =
        withContext(Dispatchers.IO) {
            storyLibrary.readStoryText(storyId)
        }
    if (body == null) {
        Toast.makeText(
            context,
            "Không đọc được file chương.",
            Toast.LENGTH_LONG,
        ).show()
        setActiveLibraryStoryId(previousActiveLibraryStoryId)
        return false
    }
    val cleaned = canonicalTextFromRaw(body)
    setText(cleaned)
    prefs.saveLastText(cleaned)
    bumpLibrarySyncEpoch()
    val insertedNextPlaceholder =
        withContext(Dispatchers.IO) {
            storyLibrary.ensurePlaceholderStoryForStoredOnlineNextPageUrl(storyId)
        }
    if (insertedNextPlaceholder) {
        bumpLibraryRefresh()
    }
    onSwitchToTextTab()
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTabs() {
    LaunchedEffect(Unit) {
        AnrDiagLog.i("AppTabs first composition")
    }

    // --- Trạng thái tab, thư viện, tiến độ nhập tệp ---
    var tabIndex by remember { mutableIntStateOf(0) }
    var elevenLabsSettingsVisible by remember { mutableStateOf(false) }
    var systemTtsSettingsVisible by remember { mutableStateOf(false) }
    var showEditorFontConfigDialog by remember { mutableStateOf(false) }
    var editorFontConfigOpenSession by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val context = LocalContext.current
    val activity = remember(context) { context as ComponentActivity }

    val prefs =
        remember(context) {
            context.applicationContext.getSharedPreferences(AppPreferenceKeys.PREF_NAME, Context.MODE_PRIVATE)
        }
    var text by remember {
        mutableStateOf(
            canonicalTextFromRaw(prefs.getString(AppPreferenceKeys.KEY_LAST_TEXT, "") ?: ""),
        )
    }
    val storyLibrary = remember { StoryLibraryRepository(context.applicationContext) }
    var activeLibraryStoryId by remember(prefs) {
        val initial: Long? =
            if (prefs.contains(AppPreferenceKeys.KEY_ACTIVE_LIBRARY_STORY_ID)) {
                prefs.getLong(AppPreferenceKeys.KEY_ACTIVE_LIBRARY_STORY_ID, -1L).takeIf { it > 0L }
            } else {
                null
            }
        mutableStateOf(initial)
    }
    var libraryRefreshTrigger by remember { mutableIntStateOf(0) }
    /** Tăng khi mở truyện từ thư viện / ghi file thư viện — ép đồng bộ lại ô theo đoạn với [text]. */
    var librarySyncEpoch by remember { mutableIntStateOf(0) }
    var libraryToolbarCommand by remember { mutableStateOf<LibraryCategoryToolbarCommand?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var openFileProgress by remember { mutableStateOf<OpenFileProgressUi?>(null) }
    /** Dialog log riêng bước đầu mở tệp SAF — [OpenFileProgressLogDialog]. */
    var openFileProgressLog by remember { mutableStateOf<OpenFileProgressLogUi?>(null) }
    val importProgressMainHandler = remember { Handler(Looper.getMainLooper()) }
    val postLibraryFolderImportProgress =
        remember {
            { progress: OpenFileProgressUi? ->
                importProgressMainHandler.post { openFileProgress = progress }
                Unit
            }
        }
    val safArchiveImportLogBridge =
        OpenFileProgressLogBridge(
            importProgressMainHandler,
            getLog = { openFileProgressLog },
            setLog = { openFileProgressLog = it },
        )
    /** Gán trong [SideEffect] sau [stopAllSpeechReading] — launcher SAF được khai báo trước trong file. */
    val archiveImportCategoryDoneHandler =
        remember {
            object {
                var consume: (Long) -> Unit = {}
            }
        }
    val latestActiveLibraryStoryId by rememberUpdatedState(activeLibraryStoryId)
    val latestTextForLibraryAutosave by rememberUpdatedState(text)
    val latestLibraryStoryId by rememberUpdatedState(activeLibraryStoryId)
    /** Gán sau [tryAutoAdvanceToNextLibraryStoryInCategory] qua [SideEffect] — tránh vòng tham chiếu với [launchParagraphPlayback]. */
    val libraryStoryAutoAdvanceHook =
        remember {
            object {
                var run: () -> Unit = {}
            }
        }
    val libraryFileAutosaveHolder =
        remember {
            object {
                var job: Job? = null
            }
        }
    var prevLibrarySidForAutosave by remember { mutableStateOf<Long?>(null) }
    /** Gọi [flushParagraphParentPersist] từ [ReaderTab] trước khi ghi file thư viện / nhận share. */
    var paragraphDraftFlush by remember { mutableStateOf<(() -> Unit)?>(null) }
    /** Chuỗi chuẩn hoá từ editor tab Text (lưới + toàn văn); null khi Reader chưa đăng ký / đã dispose. */
    var libraryTabTextSerializer by remember { mutableStateOf<(() -> String)?>(null) }
    /** Tab Text: nút xuất AAC trên top bar (ReaderTab đăng ký). */
    var exportM4aTopBar by remember { mutableStateOf<ExportM4aTopBarState?>(null) }
    /** Tab Text: cuộn đầu/cuối danh sách đoạn hoặc con trỏ đầu/cuối (chế độ toàn bộ). */
    var readerBottomNavBridge by remember { mutableStateOf<ReaderBottomNavBridge?>(null) }
    // --- Mở tài liệu SAF (ZIP/EPUB/PDF: importOpened*ArchiveFromSaf) ---
    val openTextDocumentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                coroutineScope.launch {
                    try {
                        libraryFileAutosaveHolder.job?.cancel()
                        libraryFileAutosaveHolder.job = null
                        paragraphDraftFlush?.invoke()
                        val sid = latestLibraryStoryId
                        if (sid != null) {
                            val body =
                                canonicalTextFromRaw(latestTextForLibraryAutosave)
                            val saved =
                                withContext(Dispatchers.IO) {
                                    storyLibrary.updateStoryTextIfExists(sid, body)
                                }
                            if (!saved) {
                                activeLibraryStoryId = null
                            }
                            libraryRefreshTrigger++
                        }
                        val displayName =
                            withContext(Dispatchers.IO) {
                                resolveDocumentDisplayName(activity, uri)
                            }
                        openFileProgressLog =
                            OpenFileProgressLogUi(
                                displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
                                message = "Đang sao chép vào Download/tts-ai-story…",
                            )
                        val stagedUri =
                            try {
                                withContext(Dispatchers.IO) {
                                    copyPickedDocumentToDownloadsTtsAiStoryFolder(
                                        activity,
                                        uri,
                                        displayName,
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                openFileProgressLog = null
                                importProgressMainHandler.post { openFileProgressLog = null }
                                Toast.makeText(
                                    activity,
                                    e.message ?: "Không sao chép được file vào Downloads",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }
                        openFileProgressLog =
                            openFileProgressLog?.copy(message = "Đang xử lý…")
                        if (uriLooksLikeEpubArchive(activity, stagedUri, displayName)) {
                            importOpenedEpubArchiveFromSaf(
                                activity,
                                storyLibrary,
                                stagedUri,
                                displayName,
                                safArchiveImportLogBridge,
                                onFinishedArchiveImport = { archiveImportCategoryDoneHandler.consume(it) },
                            )
                            return@launch
                        }
                        if (uriLooksLikePdf(activity, stagedUri, displayName)) {
                            importOpenedPdfArchiveFromSaf(
                                activity,
                                storyLibrary,
                                stagedUri,
                                displayName,
                                safArchiveImportLogBridge,
                                onFinishedArchiveImport = { archiveImportCategoryDoneHandler.consume(it) },
                            )
                            return@launch
                        }
                        if (uriLooksLikeZipArchive(activity, stagedUri, displayName)) {
                            importOpenedZipArchiveFromSaf(
                                activity,
                                storyLibrary,
                                stagedUri,
                                displayName,
                                safArchiveImportLogBridge,
                                onFinishedArchiveImport = { archiveImportCategoryDoneHandler.consume(it) },
                            )
                            return@launch
                        }
                        importProgressMainHandler.post { openFileProgressLog = null }
                        try {
                            val raw =
                                withContext(Dispatchers.IO) {
                                    readSendStreamAsText(activity, stagedUri)
                                }
                            if (raw == null) {
                                openFileProgressLog = null
                                Toast.makeText(
                                    activity,
                                    "Không đọc được file.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@launch
                            }
                            val r =
                                persistOpenedTextFileToLibrary(
                                    raw,
                                    storyLibrary,
                                    displayName,
                                )
                            text = r.cleanedText
                            prefs.saveLastText(r.cleanedText)
                            activeLibraryStoryId = r.storyId
                            librarySyncEpoch++
                            libraryRefreshTrigger++
                            tabIndex = 0
                            Toast.makeText(
                                activity,
                                "Đã mở: ${r.savedTitle}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } finally {
                            openFileProgressLog = null
                            importProgressMainHandler.post { openFileProgressLog = null }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        openFileProgressLog = null
                        postLibraryFolderImportProgress(null)
                        Toast.makeText(
                            activity,
                            e.message ?: "Lỗi mở file",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )

    // --- Nhập cả thư mục (document tree) ---
    val openImportFolderTreeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                Toast.makeText(
                    activity,
                    "Không lưu được quyền đọc thư mục: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                try {
                    val folderName =
                        withContext(Dispatchers.IO) {
                            documentTreeDisplayName(activity, uri).trim().ifEmpty {
                                "Import thư mục"
                            }
                        }
                    val categoryId =
                        withContext(Dispatchers.IO) {
                            storyLibrary.getOrCreateCategoryByName(folderName)
                        }
                    postLibraryFolderImportProgress(
                        OpenFileProgressUi(0, 0, "Đang quét thư mục…"),
                    )
                    val importedCount =
                        withContext(Dispatchers.IO) {
                            val n =
                                storyLibrary.importFolderAsSeparateStories(
                                    categoryId,
                                    uri,
                                    folderName,
                                    onProgress = { completed, total, label ->
                                        postLibraryFolderImportProgress(
                                            OpenFileProgressUi(completed, total, label),
                                        )
                                    },
                                )
                            storyLibrary.setCategoryImportFolderTreeUri(categoryId, uri.toString())
                            n
                        }
                    postLibraryFolderImportProgress(null)
                    Toast.makeText(
                        activity,
                        "Đã import $importedCount chương (mỗi file một chương) vào truyện «$folderName».",
                        Toast.LENGTH_LONG,
                    ).show()
                    libraryRefreshTrigger++
                    tabIndex = 1
                } catch (e: CancellationException) {
                    postLibraryFolderImportProgress(null)
                    throw e
                } catch (e: Exception) {
                    postLibraryFolderImportProgress(null)
                    Toast.makeText(
                        activity,
                        e.message ?: "Lỗi import thư mục",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    /** Xóa [activeLibraryStoryId] nếu truyện không còn (đã xóa trong thư viện). */
    LaunchedEffect(activeLibraryStoryId, libraryRefreshTrigger) {
        val sid = activeLibraryStoryId ?: return@LaunchedEffect
        val exists = withContext(Dispatchers.IO) { storyLibrary.getStory(sid) != null }
        if (!exists) {
            activeLibraryStoryId = null
        }
    }
    LaunchedEffect(activeLibraryStoryId) {
        prefs
            .edit()
            .apply {
                val sid = activeLibraryStoryId
                if (sid == null) {
                    remove(AppPreferenceKeys.KEY_ACTIVE_LIBRARY_STORY_ID)
                } else {
                    putLong(AppPreferenceKeys.KEY_ACTIVE_LIBRARY_STORY_ID, sid)
                }
            }
            .apply()
        val was = prevLibrarySidForAutosave
        val now = activeLibraryStoryId
        val cancelPending = now == null || (was != null && was != now)
        if (cancelPending) {
            libraryFileAutosaveHolder.job?.cancel()
            libraryFileAutosaveHolder.job = null
        }
        prevLibrarySidForAutosave = now
    }
    LaunchedEffect(tabIndex) {
        if (tabIndex != 1) return@LaunchedEffect
        val sid = activeLibraryStoryId ?: return@LaunchedEffect
        libraryFileAutosaveHolder.job?.cancel()
        libraryFileAutosaveHolder.job = null
        try {
            val saved =
                withContext(Dispatchers.IO) {
                    storyLibrary.updateStoryTextIfExists(sid, canonicalTextFromRaw(text))
                }
            if (!saved) {
                activeLibraryStoryId = null
            }
            libraryRefreshTrigger++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: "Lỗi ghi file thư viện",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    var textTabSpeechEngine by remember {
        mutableStateOf(
            TextTabSpeechEngine.fromStorage(prefs.getString(AppPreferenceKeys.KEY_TEXT_TAB_SPEECH_ENGINE, null)),
        )
    }
    var elevenLabsPlayJob by remember { mutableStateOf<Job?>(null) }
    val latestTextTabSpeechEngine by rememberUpdatedState(textTabSpeechEngine)

    // --- Intent SEND / VIEW / PROCESS_TEXT → thư viện ---
    DisposableEffect(activity, coroutineScope) {
        val flushCurrentOpenLibraryStoryBeforeInboundImport: suspend () -> Unit = {
            libraryFileAutosaveHolder.job?.cancel()
            libraryFileAutosaveHolder.job = null
            paragraphDraftFlush?.invoke()
            val sid = latestLibraryStoryId
            if (sid != null) {
                val body = canonicalTextFromRaw(latestTextForLibraryAutosave)
                val saved =
                    withContext(Dispatchers.IO) {
                        storyLibrary.updateStoryTextIfExists(sid, body)
                    }
                if (!saved) {
                    activeLibraryStoryId = null
                }
                libraryRefreshTrigger++
            }
        }

        fun clearShareIntent() {
            activity.setIntent(Intent(activity, MainActivity::class.java))
        }

        fun commitInboundPersistResult(r: InboundLibraryPersistResult) {
            text = r.cleanedText
            prefs.saveLastText(r.cleanedText)
            activeLibraryStoryId = r.storyId
            librarySyncEpoch++
            libraryRefreshTrigger++
            tabIndex = 0
            clearShareIntent()
            Toast.makeText(
                activity,
                "Đã lưu thư viện: ${r.savedTitle}",
                Toast.LENGTH_SHORT,
            ).show()
        }

        fun applyInboundTextToLibraryFromRaw(raw: String) {
            coroutineScope.launch {
                try {
                    flushCurrentOpenLibraryStoryBeforeInboundImport()
                    val r =
                        persistInboundSharedTextToLibrary(
                            raw,
                            storyLibrary,
                            latestLibraryStoryId,
                        )
                    commitInboundPersistResult(r)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(
                        activity,
                        e.message ?: "Lỗi lưu thư viện",
                        Toast.LENGTH_LONG,
                    ).show()
                    clearShareIntent()
                }
            }
        }

        fun applyInboundFromSharedHttpUrl(url: String, subject: String?) {
            coroutineScope.launch {
                try {
                    flushCurrentOpenLibraryStoryBeforeInboundImport()
                    Toast.makeText(activity, "Đang tải trang…", Toast.LENGTH_SHORT).show()
                    val body = fetchUrlAsPlainText(url)
                    if (body.isBlank()) {
                        Toast.makeText(
                            activity,
                            "Trang không có nội dung chữ.",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }
                    val combined =
                        if (subject.isNullOrBlank()) {
                            body
                        } else {
                            "$subject\n\n$body"
                        }
                    val r =
                        persistInboundSharedTextToLibrary(
                            combined,
                            storyLibrary,
                            latestLibraryStoryId,
                        )
                    commitInboundPersistResult(r)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(
                        activity,
                        e.message ?: "Lỗi tải URL / lưu thư viện",
                        Toast.LENGTH_LONG,
                    ).show()
                    clearShareIntent()
                }
            }
        }

        fun consumeSendIntent() {
            val intent = activity.intent ?: return
            if (intent.action != Intent.ACTION_SEND) return

            val extraText =
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!extraText.isNullOrEmpty()) {
                val subject =
                    intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.trim()
                val url = parseHttpUrlFromSharedText(extraText)
                // Bỏ intent ngay — nếu không, compose dispose / ON_RESUME lặp sẽ import trùng
                // (coroutine persist xong mới clear là quá muộn).
                clearShareIntent()
                if (url != null) {
                    applyInboundFromSharedHttpUrl(url, subject)
                } else {
                    applyInboundTextToLibraryFromRaw(extraText)
                }
                return
            }

            val streamUri: Uri? =
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            if (streamUri == null) return

            val resolvedType = intent.type ?: activity.contentResolver.getType(streamUri)
            if (resolvedType != null &&
                (resolvedType.startsWith("image/") || resolvedType.startsWith("video/"))
            ) {
                Toast.makeText(
                    activity,
                    "Chỉ hỗ trợ file văn bản (text).",
                    Toast.LENGTH_SHORT,
                ).show()
                clearShareIntent()
                return
            }

            clearShareIntent()
            coroutineScope.launch {
                try {
                    val sendDisplayName =
                        withContext(Dispatchers.IO) {
                            resolveDocumentDisplayName(activity, streamUri)
                        }
                    if (uriLooksLikePdf(activity, streamUri, sendDisplayName)) {
                        flushCurrentOpenLibraryStoryBeforeInboundImport()
                        openFileProgressLog =
                            OpenFileProgressLogUi(
                                displayName = sendDisplayName?.trim()?.takeIf { it.isNotEmpty() },
                                message = "Đang xử lý PDF…",
                            )
                        importOpenedPdfArchiveFromSaf(
                            activity,
                            storyLibrary,
                            streamUri,
                            sendDisplayName,
                            safArchiveImportLogBridge,
                            onFinishedArchiveImport = { archiveImportCategoryDoneHandler.consume(it) },
                        )
                        return@launch
                    }
                    val raw =
                        withContext(Dispatchers.IO) {
                            readSendStreamAsText(activity, streamUri)
                        }
                    if (raw == null) {
                        Toast.makeText(
                            activity,
                            "Không đọc được nội dung file.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        clearShareIntent()
                        return@launch
                    }
                    flushCurrentOpenLibraryStoryBeforeInboundImport()
                    val r =
                        persistInboundSharedTextToLibrary(
                            raw,
                            storyLibrary,
                            latestLibraryStoryId,
                        )
                    commitInboundPersistResult(r)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    openFileProgressLog = null
                    importProgressMainHandler.post { openFileProgressLog = null }
                    Toast.makeText(
                        activity,
                        e.message ?: "Lỗi lưu thư viện",
                        Toast.LENGTH_LONG,
                    ).show()
                    clearShareIntent()
                }
            }
        }

        fun consumeViewIntent() {
            val intent = activity.intent ?: return
            if (intent.action != Intent.ACTION_VIEW) return
            val uri = intent.data ?: return
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return
            if (scheme != "content" && scheme != "file") return

            val resolvedType = intent.type ?: activity.contentResolver.getType(uri)
            if (resolvedType != null &&
                (resolvedType.startsWith("image/") || resolvedType.startsWith("video/"))
            ) {
                Toast.makeText(
                    activity,
                    "Chỉ hỗ trợ mở file văn bản (.txt).",
                    Toast.LENGTH_SHORT,
                ).show()
                clearShareIntent()
                return
            }
            val viewDisplayName =
                runCatching {
                    resolveDocumentDisplayName(activity, uri)
                }.getOrNull()
            if (uriLooksLikePdf(activity, uri, viewDisplayName)) {
                clearShareIntent()
                coroutineScope.launch {
                    try {
                        flushCurrentOpenLibraryStoryBeforeInboundImport()
                        openFileProgressLog =
                            OpenFileProgressLogUi(
                                displayName = viewDisplayName?.trim()?.takeIf { it.isNotEmpty() },
                                message = "Đang sao chép vào Download/tts-ai-story…",
                            )
                        val stagedViewPdf =
                            withContext(Dispatchers.IO) {
                                copyPickedDocumentToDownloadsTtsAiStoryFolder(
                                    activity,
                                    uri,
                                    viewDisplayName,
                                )
                            }
                        openFileProgressLog =
                            openFileProgressLog?.copy(message = "Đang xử lý PDF…")
                        importOpenedPdfArchiveFromSaf(
                            activity,
                            storyLibrary,
                            stagedViewPdf,
                            viewDisplayName,
                            safArchiveImportLogBridge,
                            onFinishedArchiveImport = { archiveImportCategoryDoneHandler.consume(it) },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        openFileProgressLog = null
                        importProgressMainHandler.post { openFileProgressLog = null }
                        Toast.makeText(
                            activity,
                            e.message ?: "Lỗi nhập PDF",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                return
            }
            val viewLooksEpub = uriLooksLikeEpubArchive(activity, uri, viewDisplayName)
            if (viewLooksEpub) {
                clearShareIntent()
                coroutineScope.launch {
                    try {
                        flushCurrentOpenLibraryStoryBeforeInboundImport()
                        openFileProgressLog =
                            OpenFileProgressLogUi(
                                displayName = viewDisplayName?.trim()?.takeIf { it.isNotEmpty() },
                                message = "Đang sao chép vào Download/tts-ai-story…",
                            )
                        val stagedViewEpub =
                            withContext(Dispatchers.IO) {
                                copyPickedDocumentToDownloadsTtsAiStoryFolder(
                                    activity,
                                    uri,
                                    viewDisplayName,
                                )
                            }
                        openFileProgressLog =
                            openFileProgressLog?.copy(message = "Đang xử lý EPUB…")
                        importOpenedEpubArchiveFromSaf(
                            activity,
                            storyLibrary,
                            stagedViewEpub,
                            viewDisplayName,
                            safArchiveImportLogBridge,
                            onFinishedArchiveImport = { archiveImportCategoryDoneHandler.consume(it) },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        openFileProgressLog = null
                        importProgressMainHandler.post { openFileProgressLog = null }
                        Toast.makeText(
                            activity,
                            e.message ?: "Lỗi nhập EPUB",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                return
            }
            if (uriLooksLikeZipArchive(activity, uri, viewDisplayName) && !viewLooksEpub) {
                clearShareIntent()
                coroutineScope.launch {
                    try {
                        flushCurrentOpenLibraryStoryBeforeInboundImport()
                        openFileProgressLog =
                            OpenFileProgressLogUi(
                                displayName = viewDisplayName?.trim()?.takeIf { it.isNotEmpty() },
                                message = "Đang sao chép vào Download/tts-ai-story…",
                            )
                        val stagedViewZip =
                            withContext(Dispatchers.IO) {
                                copyPickedDocumentToDownloadsTtsAiStoryFolder(
                                    activity,
                                    uri,
                                    viewDisplayName,
                                )
                            }
                        openFileProgressLog =
                            openFileProgressLog?.copy(message = "Đang xử lý ZIP…")
                        importOpenedZipArchiveFromSaf(
                            activity,
                            storyLibrary,
                            stagedViewZip,
                            viewDisplayName,
                            safArchiveImportLogBridge,
                            onFinishedArchiveImport = { archiveImportCategoryDoneHandler.consume(it) },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        openFileProgressLog = null
                        importProgressMainHandler.post { openFileProgressLog = null }
                        Toast.makeText(
                            activity,
                            e.message ?: "Lỗi nhập ZIP",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                return
            }
            if (!shouldTreatViewUriAsTxt(uri, resolvedType, viewDisplayName)) return

            clearShareIntent()
            coroutineScope.launch {
                try {
                    val stagedViewTxt =
                        withContext(Dispatchers.IO) {
                            copyPickedDocumentToDownloadsTtsAiStoryFolder(
                                activity,
                                uri,
                                viewDisplayName,
                            )
                        }
                    val raw =
                        withContext(Dispatchers.IO) {
                            readSendStreamAsText(activity, stagedViewTxt)
                        }
                    if (raw == null) {
                        Toast.makeText(
                            activity,
                            "Không đọc được file.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        clearShareIntent()
                        return@launch
                    }
                    flushCurrentOpenLibraryStoryBeforeInboundImport()
                    val r =
                        persistInboundSharedTextToLibrary(
                            raw,
                            storyLibrary,
                            latestLibraryStoryId,
                        )
                    commitInboundPersistResult(r)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(
                        activity,
                        e.message ?: "Lỗi lưu thư viện",
                        Toast.LENGTH_LONG,
                    ).show()
                    clearShareIntent()
                }
            }
        }

        fun consumeProcessTextIntent() {
            val intent = activity.intent ?: return
            if (intent.action != Intent.ACTION_PROCESS_TEXT) return
            val proc =
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                    ?: intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            if (proc.isNullOrEmpty()) return
            val subject =
                intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.trim()
            val url = parseHttpUrlFromSharedText(proc)
            clearShareIntent()
            if (url != null) {
                applyInboundFromSharedHttpUrl(url, subject)
            } else {
                applyInboundTextToLibraryFromRaw(proc)
            }
        }

        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    consumeSendIntent()
                    consumeViewIntent()
                    consumeProcessTextIntent()
                }
            }
        activity.lifecycle.addObserver(observer)
        // Không gọi consume* ngay sau addObserver (trùng với ON_RESUME → duplicate story).
        // Nếu composition chạy khi activity đã RESUMED, ON_RESUME đã qua nên xử lý intent một lần ở đây.
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            consumeSendIntent()
            consumeViewIntent()
            consumeProcessTextIntent()
        }
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // --- TTS hệ thống, ElevenLabs, đọc theo đoạn, wake lock / màn hình ---
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var systemTtsSpeechRate by remember {
        mutableFloatStateOf(
            if (prefs.contains(AppPreferenceKeys.KEY_SYSTEM_TTS_SPEECH_RATE)) {
                prefs.getFloat(AppPreferenceKeys.KEY_SYSTEM_TTS_SPEECH_RATE, 1f)
            } else {
                1f
            },
        )
    }
    var systemTtsPitch by remember {
        mutableFloatStateOf(
            if (prefs.contains(AppPreferenceKeys.KEY_SYSTEM_TTS_PITCH)) {
                prefs.getFloat(AppPreferenceKeys.KEY_SYSTEM_TTS_PITCH, 1f)
            } else {
                1f
            },
        )
    }
    var systemTtsSampleText by remember {
        mutableStateOf(
            prefs.getString(AppPreferenceKeys.KEY_SYSTEM_TTS_SAMPLE_TEXT, null)?.trim()?.takeIf { it.isNotEmpty() }
                ?: AppPreferenceKeys.DEFAULT_SYSTEM_TTS_SAMPLE_TEXT,
        )
    }

    var speakingParagraphIndex by remember { mutableIntStateOf(-1) }
    /** Số utterance TTS hệ thống còn trong loạt đọc truyện (utteranceId dạng tts_para_*). */
    var systemTtsStoryUtterancesRemaining by remember { mutableIntStateOf(0) }
    /** Số utterance TTS hệ thống đang phát (preview, đọc truyện) — dùng giữ màn hình vì [TextToSpeech.isSpeaking] không gây recompose. */
    var systemTtsUtteranceDepth by remember { mutableIntStateOf(0) }
    /** Nội dung đoạn đang phát (index gốc → văn đã sanitize) để đếm từ khi đo WPM. */
    var systemTtsWpmOrigToText by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    /** Tổng thời gian phát các utterance đoạn đã hoàn tất (ms, [SystemClock.elapsedRealtime]). */
    var systemTtsWpmSpeechMsAccum by remember { mutableLongStateOf(0L) }
    var systemTtsWpmWordsAccum by remember { mutableIntStateOf(0) }
    var systemTtsWpmUtteranceStartElapsedMs by remember { mutableStateOf<Long?>(null) }
    /** Làm mới UI WPM định kỳ khi đang phát. */
    var systemTtsWpmLiveTick by remember { mutableIntStateOf(0) }
    /** Giữ audio focus khi đọc TTS hệ thống — một số máy tắt màn hình không chuyển đoạn nếu app không có focus. */
    var systemTtsAudioFocusRequest by remember { mutableStateOf<AudioFocusRequest?>(null) }

    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val engineSlot = arrayOfNulls<TextToSpeech>(1)
        val engine =
            TextToSpeech(context) { status ->
                handler.post {
                    if (status != TextToSpeech.SUCCESS) return@post
                    val ttsEngine = engineSlot[0] ?: return@post
                    val list =
                        (ttsEngine.voices?.toList().orEmpty())
                            .filter(::isVietnameseTtsVoice)
                            .sortedWith(
                                compareBy({ it.locale?.toLanguageTag().orEmpty() }, { it.name }),
                            )
                    voices = list
                    val savedName =
                        prefs.getString(AppPreferenceKeys.KEY_SYSTEM_TTS_VOICE_NAME, null)?.trim()?.takeIf {
                            it.isNotEmpty()
                        }
                    val current = ttsEngine.voice
                    selectedVoice =
                        savedName?.let { sn -> list.find { it.name == sn } }
                            ?: current
                                ?.takeIf(::isVietnameseTtsVoice)
                                ?.let { c -> list.find { it.name == c.name } }
                            ?: list.firstOrNull()
                    val rate =
                        if (prefs.contains(AppPreferenceKeys.KEY_SYSTEM_TTS_SPEECH_RATE)) {
                            prefs.getFloat(AppPreferenceKeys.KEY_SYSTEM_TTS_SPEECH_RATE, 1f)
                        } else {
                            1f
                        }
                    val pitchV =
                        if (prefs.contains(AppPreferenceKeys.KEY_SYSTEM_TTS_PITCH)) {
                            prefs.getFloat(AppPreferenceKeys.KEY_SYSTEM_TTS_PITCH, 1f)
                        } else {
                            1f
                        }
                    ttsEngine.setSpeechRate(rate)
                    ttsEngine.setPitch(pitchV)
                    systemTtsSpeechRate = rate
                    systemTtsPitch = pitchV
                    ttsReady = true
                }
            }
        engineSlot[0] = engine
        tts = engine
        onDispose {
            handler.removeCallbacksAndMessages(null)
            systemTtsUtteranceDepth = 0
            engine.stop()
            engine.shutdown()
        }
    }

    LaunchedEffect(selectedVoice, ttsReady) {
        val engine = tts ?: return@LaunchedEffect
        val voice = selectedVoice ?: return@LaunchedEffect
        if (ttsReady) {
            engine.voice = voice
        }
    }

    /** Ghi bookmark = câu TTS đang phát (để Stop / đọc hết vẫn bấm Play đọc tiếp). */
    fun persistBookmarkIfSpeaking() {
        val idx = speakingParagraphIndex
        if (idx < 0) return
        val sid = latestActiveLibraryStoryId
        if (sid != null) {
            coroutineScope.launch(Dispatchers.IO) {
                storyLibrary.updateLastSpeechSentenceIndex(sid, idx)
            }
        }
    }

    /** Giữ CPU khi tắt màn hình: giữa các utterance [systemTtsUtteranceDepth] có thể = 0 nhưng loạt truyện vẫn còn. */
    val voicePlaybackNeedsWakeLock =
        systemTtsUtteranceDepth > 0 ||
            systemTtsStoryUtterancesRemaining > 0 ||
            (elevenLabsPlayJob?.isActive == true)
    LaunchedEffect(voicePlaybackNeedsWakeLock) {
        if (!voicePlaybackNeedsWakeLock) return@LaunchedEffect
        val app = activity.applicationContext
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val tag = "${app.packageName}:voice_playback"
        val wakeLock =
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
                setReferenceCounted(false)
            }
        wakeLock.acquire()
        try {
            awaitCancellation()
        } finally {
            runCatching {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    val keepScreenOnForVoicePlayback =
        systemTtsUtteranceDepth > 0 ||
            systemTtsStoryUtterancesRemaining > 0 ||
            (elevenLabsPlayJob?.isActive == true)
    DisposableEffect(keepScreenOnForVoicePlayback) {
        val window = activity.window
        if (keepScreenOnForVoicePlayback) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(speakingParagraphIndex, activeLibraryStoryId) {
        if (speakingParagraphIndex >= 0) {
            val sid = activeLibraryStoryId
            if (sid != null) {
                withContext(Dispatchers.IO) {
                    storyLibrary.updateLastSpeechSentenceIndex(sid, speakingParagraphIndex)
                }
            }
        }
    }

    fun abandonSystemTtsAudioFocus() {
        val req = systemTtsAudioFocusRequest ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.abandonAudioFocusRequest(req)
        }
        systemTtsAudioFocusRequest = null
    }

    val stopAllSpeechReadingRef = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    fun requestSystemTtsAudioFocusForPlayback() {
        abandonSystemTtsAudioFocus()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        val req =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        -> {
                            Handler(Looper.getMainLooper()).post {
                                stopAllSpeechReadingRef.value?.invoke(true)
                            }
                        }
                    }
                }
                .build()
        if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            systemTtsAudioFocusRequest = req
        }
    }

    val systemParagraphSpeechEngine =
        remember {
            SystemParagraphSpeechEngine(
                tts = { tts },
                requestAudioFocus = { requestSystemTtsAudioFocusForPlayback() },
                abandonAudioFocus = { abandonSystemTtsAudioFocus() },
            )
        }
    val elevenParagraphSpeechEngine =
        remember(context, prefs, coroutineScope) {
            ElevenLabsParagraphSpeechEngine(
                appContext = context.applicationContext,
                prefs = prefs,
                scope = coroutineScope,
            )
        }

    fun stopAllSpeechReading(persistBookmarkOnStop: Boolean = true) {
        systemTtsStoryUtterancesRemaining = 0
        systemTtsWpmOrigToText = emptyMap()
        systemTtsWpmSpeechMsAccum = 0L
        systemTtsWpmWordsAccum = 0
        systemTtsWpmUtteranceStartElapsedMs = null
        if (persistBookmarkOnStop) {
            persistBookmarkIfSpeaking()
        }
        ParagraphSpeechEngines.stopAll(
            systemParagraphSpeechEngine,
            elevenParagraphSpeechEngine,
        )
        systemTtsUtteranceDepth = 0
        elevenLabsPlayJob = null
        speakingParagraphIndex = -1
    }

    SideEffect {
        stopAllSpeechReadingRef.value = { persist -> stopAllSpeechReading(persist) }
        archiveImportCategoryDoneHandler.consume = { categoryId ->
            coroutineScope.launch {
                libraryRefreshTrigger++
                val firstId =
                    withContext(Dispatchers.IO) {
                        storyLibrary.listStories(categoryId).firstOrNull()?.id
                    }
                if (firstId == null) {
                    tabIndex = 1
                    return@launch
                }
                val prev = activeLibraryStoryId
                val ok =
                    openLibraryStoryByIdForMainTabs(
                        context = context,
                        storyLibrary = storyLibrary,
                        storyId = firstId,
                        previousActiveLibraryStoryId = prev,
                        stopAllSpeechReading = {
                            stopAllSpeechReadingRef.value?.invoke(true)
                        },
                        setActiveLibraryStoryId = { activeLibraryStoryId = it },
                        setText = { text = it },
                        prefs = prefs,
                        bumpLibraryRefresh = { libraryRefreshTrigger++ },
                        bumpLibrarySyncEpoch = { librarySyncEpoch++ },
                        onSwitchToTextTab = { tabIndex = 0 },
                        paragraphDraftFlush = paragraphDraftFlush,
                        serializeOpenTabTextForLibrary = {
                            libraryTabTextSerializer?.invoke()
                                ?: canonicalTextFromRaw(text)
                        },
                    )
                if (!ok) {
                    tabIndex = 1
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            elevenLabsPlayJob?.cancel()
            abandonSystemTtsAudioFocus()
        }
    }

    fun launchParagraphPlayback(paragraphs: List<String>, startIndex: Int) {
        stopAllSpeechReading(persistBookmarkOnStop = false)
        if (textTabSpeechEngine == TextTabSpeechEngine.System) {
            val jobs = buildParagraphSpeakJobs(paragraphs, startIndex)
            systemTtsWpmOrigToText = jobs.associate { it.first to it.second }
            systemTtsWpmSpeechMsAccum = 0L
            systemTtsWpmWordsAccum = 0
            systemTtsWpmUtteranceStartElapsedMs = null
        }
        val speechCallbacks =
            ParagraphSpeechSequenceCallbacks(
                onSpeakingParagraphIndex = { speakingParagraphIndex = it },
                onErrorToast = { msg ->
                    val len =
                        if (msg.startsWith("ElevenLabs")) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    Toast.makeText(context, msg, len).show()
                },
                onSystemQueuedUtteranceCount = { systemTtsStoryUtterancesRemaining = it },
                onElevenLabsJob = { elevenLabsPlayJob = it },
                onFullSequenceFinishedForLibraryAutoAdvance = {
                    if (latestActiveLibraryStoryId != null &&
                        latestTextTabSpeechEngine == TextTabSpeechEngine.ElevenLabs
                    ) {
                        libraryStoryAutoAdvanceHook.run()
                    }
                },
            )
        ParagraphSpeechEngines
            .select(
                textTabSpeechEngine,
                systemParagraphSpeechEngine,
                elevenParagraphSpeechEngine,
            )
            .startParagraphSequence(paragraphs, startIndex, speechCallbacks)
    }

    /** Khi phát hết mọi đoạn của truyện thư viện đang mở: tự mở truyện kế trong cùng thể loại (nếu có) và phát tiếp. */
    fun tryAutoAdvanceToNextLibraryStoryInCategory() {
        coroutineScope.launch {
            try {
                val finishedSid = latestActiveLibraryStoryId ?: return@launch
                paragraphDraftFlush?.invoke()
                yield()
                val bodyToFinish =
                    libraryTabTextSerializer?.invoke()
                        ?: canonicalTextFromRaw(latestTextForLibraryAutosave)
                val saved =
                    withContext(Dispatchers.IO) {
                        storyLibrary.updateStoryTextIfExists(finishedSid, bodyToFinish)
                    }
                if (!saved) {
                    activeLibraryStoryId = null
                    return@launch
                }
                libraryRefreshTrigger++
                val nextRow =
                    withContext(Dispatchers.IO) {
                        storyLibrary.nextStoryInCategoryAfter(finishedSid)
                    } ?: return@launch
                val nextBody =
                    withContext(Dispatchers.IO) {
                        storyLibrary.readStoryText(nextRow.id)
                    } ?: return@launch
                val cleaned = canonicalTextFromRaw(nextBody)
                text = cleaned
                prefs.saveLastText(cleaned)
                activeLibraryStoryId = nextRow.id
                librarySyncEpoch++
                libraryRefreshTrigger++
                tabIndex = 0
                val paras = splitIntoParagraphs(cleaned)
                launchParagraphPlayback(paras, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.message ?: "Lỗi chuyển chương tiếp theo",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    SideEffect {
        libraryStoryAutoAdvanceHook.run = { tryAutoAdvanceToNextLibraryStoryInCategory() }
    }

    DisposableEffect(tts, textTabSpeechEngine) {
        val engine = tts
        if (engine == null) {
            onDispose { }
        } else {
            // TTS gọi listener trên luồng nền; Compose mutableState / auto-advance phải chạy trên main.
            val progressHandler = Handler(Looper.getMainLooper())
            val utteranceSink =
                object : SystemTtsUtteranceProgressSink {
                    override fun onUtteranceStart(utteranceId: String?) {
                        systemTtsUtteranceDepth++
                        speakingParagraphIndex = parseTtsParagraphIndex(utteranceId) ?: -1
                        if (parseTtsParagraphIndex(utteranceId) != null) {
                            systemTtsWpmUtteranceStartElapsedMs = SystemClock.elapsedRealtime()
                        }
                    }

                    override fun onUtteranceDone(utteranceId: String?) {
                        val paraIdx = parseTtsParagraphIndex(utteranceId)
                        if (paraIdx != null) {
                            val start = systemTtsWpmUtteranceStartElapsedMs
                            val now = SystemClock.elapsedRealtime()
                            if (start != null) {
                                systemTtsWpmSpeechMsAccum += (now - start).coerceAtLeast(0L)
                            }
                            systemTtsWpmUtteranceStartElapsedMs = null
                            val spoken = systemTtsWpmOrigToText[paraIdx]
                            if (!spoken.isNullOrEmpty()) {
                                systemTtsWpmWordsAccum += wordCountForTtsPlaybackWpm(spoken)
                            }
                        }
                        systemTtsUtteranceDepth =
                            (systemTtsUtteranceDepth - 1).coerceAtLeast(0)
                        speakingParagraphIndex = -1
                        val wasParagraph = parseTtsParagraphIndex(utteranceId) != null
                        if (wasParagraph && systemTtsStoryUtterancesRemaining > 0) {
                            systemTtsStoryUtterancesRemaining--
                        }
                        if (wasParagraph &&
                            systemTtsStoryUtterancesRemaining == 0 &&
                            latestActiveLibraryStoryId != null &&
                            textTabSpeechEngine == TextTabSpeechEngine.System
                        ) {
                            libraryStoryAutoAdvanceHook.run()
                        }
                        if (wasParagraph && textTabSpeechEngine == TextTabSpeechEngine.System) {
                            systemParagraphSpeechEngine.onSystemTtsParagraphUtteranceFinished(
                                utteranceId,
                            )
                        }
                    }

                    override fun onUtteranceError(utteranceId: String?) {
                        val paraIdxErr = parseTtsParagraphIndex(utteranceId)
                        if (paraIdxErr != null) {
                            val start = systemTtsWpmUtteranceStartElapsedMs
                            val now = SystemClock.elapsedRealtime()
                            if (start != null) {
                                systemTtsWpmSpeechMsAccum += (now - start).coerceAtLeast(0L)
                            }
                            systemTtsWpmUtteranceStartElapsedMs = null
                            val spoken = systemTtsWpmOrigToText[paraIdxErr]
                            if (!spoken.isNullOrEmpty()) {
                                systemTtsWpmWordsAccum += wordCountForTtsPlaybackWpm(spoken)
                            }
                        }
                        systemTtsUtteranceDepth =
                            (systemTtsUtteranceDepth - 1).coerceAtLeast(0)
                        speakingParagraphIndex = -1
                        if (parseTtsParagraphIndex(utteranceId) != null &&
                            systemTtsStoryUtterancesRemaining > 0
                        ) {
                            systemTtsStoryUtterancesRemaining--
                        }
                        if (parseTtsParagraphIndex(utteranceId) != null &&
                            textTabSpeechEngine == TextTabSpeechEngine.System
                        ) {
                            systemParagraphSpeechEngine.onSystemTtsParagraphUtteranceFinished(
                                utteranceId,
                            )
                        }
                    }
                }
            val listener =
                SystemParagraphSpeechEngine.utteranceProgressListener(
                    progressHandler,
                    utteranceSink,
                )
            engine.setOnUtteranceProgressListener(listener)
            onDispose {
                progressHandler.removeCallbacksAndMessages(null)
                engine.setOnUtteranceProgressListener(null)
            }
        }
    }

    val systemTtsPlaybackActive =
        systemTtsUtteranceDepth > 0 || systemTtsStoryUtterancesRemaining > 0

    val playbackActiveForWpmTick = rememberUpdatedState(systemTtsPlaybackActive)
    val engineForWpmTick = rememberUpdatedState(textTabSpeechEngine)
    LaunchedEffect(systemTtsPlaybackActive, textTabSpeechEngine) {
        while (playbackActiveForWpmTick.value && engineForWpmTick.value == TextTabSpeechEngine.System) {
            delay(300)
            systemTtsWpmLiveTick++
        }
    }

    val systemTtsMeasuredWpm: Int? = run {
        systemTtsWpmLiveTick // ghim recompose theo tick khi đang phát
        if (textTabSpeechEngine != TextTabSpeechEngine.System || !systemTtsPlaybackActive) {
            null
        } else {
            val now = SystemClock.elapsedRealtime()
            val partial =
                systemTtsWpmUtteranceStartElapsedMs?.let { (now - it).coerceAtLeast(0L) } ?: 0L
            val denom = (systemTtsWpmSpeechMsAccum + partial).coerceAtLeast(1L)
            if (systemTtsWpmWordsAccum <= 0) null
            else ((systemTtsWpmWordsAccum * 60000L + denom / 2) / denom).toInt()
        }
    }

    // --- Scaffold + hộp thoại font / cài đặt giọng ---
    Box(modifier = Modifier.fillMaxSize()) {
        AppModalNavigationDrawerScaffold(
            drawerState = drawerState,
            coroutineScope = coroutineScope,
            tabIndex = tabIndex,
            onTabIndexChange = { tabIndex = it },
            onOpenElevenLabsFromDrawer = { elevenLabsSettingsVisible = true },
            onOpenSystemTtsFromDrawer = { systemTtsSettingsVisible = true },
            onNavigateLibraryToolbar = { cmd ->
                tabIndex = 1
                libraryToolbarCommand = cmd
            },
            onTopBarTextSettingsClick = {
                when (textTabSpeechEngine) {
                    TextTabSpeechEngine.System -> {
                        elevenLabsSettingsVisible = false
                        systemTtsSettingsVisible = true
                    }
                    TextTabSpeechEngine.ElevenLabs -> {
                        systemTtsSettingsVisible = false
                        elevenLabsSettingsVisible = true
                    }
                }
            },
            onOpenEditorFontConfigFromDrawer = {
                editorFontConfigOpenSession++
                showEditorFontConfigDialog = true
            },
            textTabSpeechEngine = textTabSpeechEngine,
            prefs = prefs,
            text = text,
            speakingParagraphIndex = speakingParagraphIndex,
            readerBottomNavBridge = readerBottomNavBridge,
            storyLibrary = storyLibrary,
            libraryRefreshTrigger = libraryRefreshTrigger,
            libraryToolbarCommand = libraryToolbarCommand,
            onLibraryToolbarCommandConsumed = { libraryToolbarCommand = null },
            activeLibraryStoryId = activeLibraryStoryId,
            onLibraryChanged = { libraryRefreshTrigger++ },
            librarySyncEpoch = librarySyncEpoch,
            tts = tts,
            ttsReady = ttsReady,
            elevenLabsPlayJob = elevenLabsPlayJob,
            systemTtsPlaybackActive = systemTtsPlaybackActive,
            systemTtsMeasuredWpm = systemTtsMeasuredWpm,
            onEditorTextChange = { newText ->
                text = newText
                prefs.saveLastText(newText)
                val sid = activeLibraryStoryId
                if (sid == null) {
                    libraryFileAutosaveHolder.job?.cancel()
                    libraryFileAutosaveHolder.job = null
                } else {
                    libraryFileAutosaveHolder.job?.cancel()
                    libraryFileAutosaveHolder.job =
                        coroutineScope.launch {
                            try {
                                delay(AppEditorConstants.LIBRARY_FILE_AUTOSAVE_DEBOUNCE_MS)
                                val storyId = latestLibraryStoryId ?: return@launch
                                val body =
                                    canonicalTextFromRaw(
                                        latestTextForLibraryAutosave,
                                    )
                                val ok =
                                    withContext(Dispatchers.IO) {
                                        storyLibrary.updateStoryTextIfExists(storyId, body)
                                    }
                                if (!ok) {
                                    activeLibraryStoryId = null
                                }
                                libraryRefreshTrigger++
                            } catch (_: CancellationException) {
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    e.message ?: "Lỗi ghi file thư viện",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                }
            },
            onTextTabSpeechEngineChange = { next ->
                if (next != textTabSpeechEngine) {
                    stopAllSpeechReading()
                    textTabSpeechEngine = next
                    prefs
                        .edit()
                        .putString(AppPreferenceKeys.KEY_TEXT_TAB_SPEECH_ENGINE, next.storageValue)
                        .apply()
                }
            },
            onStopAllSpeechReading = { stopAllSpeechReading() },
            onPlayParagraphs = { paras, startIdx ->
                launchParagraphPlayback(paras, startIdx)
            },
            onLibraryFileSynced = { librarySyncEpoch++ },
            onLibraryDataChanged = { libraryRefreshTrigger++ },
            onSavedLibraryStoryFromEditor = { id ->
                activeLibraryStoryId = id
                librarySyncEpoch++
            },
                onRegisterParagraphDraftFlush = { flush ->
                    paragraphDraftFlush = flush
                },
                onRegisterLibraryTabTextSerializer = { serializer ->
                    libraryTabTextSerializer = serializer
                },
            onRegisterExportM4aForTopBar = { exportM4aTopBar = it },
            exportM4aTopBar = exportM4aTopBar,
            onRegisterReaderBottomNav = { bridge ->
                readerBottomNavBridge = bridge
            },
            systemTtsSpeechRate = systemTtsSpeechRate,
            systemTtsPitch = systemTtsPitch,
            onOpenStoryFromLibrary = { storyId ->
                openLibraryStoryByIdForMainTabs(
                    context = context,
                    storyLibrary = storyLibrary,
                    storyId = storyId,
                    previousActiveLibraryStoryId = latestActiveLibraryStoryId,
                    stopAllSpeechReading = { stopAllSpeechReading() },
                    setActiveLibraryStoryId = { activeLibraryStoryId = it },
                    setText = { text = it },
                    prefs = prefs,
                    bumpLibraryRefresh = { libraryRefreshTrigger++ },
                    bumpLibrarySyncEpoch = { librarySyncEpoch++ },
                    onSwitchToTextTab = { tabIndex = 0 },
                    paragraphDraftFlush = paragraphDraftFlush,
                    serializeOpenTabTextForLibrary = {
                        libraryTabTextSerializer?.invoke()
                            ?: canonicalTextFromRaw(text)
                    },
                )
            },
            onOpenTextFileFromStorage = {
                openTextDocumentLauncher.launch(
                    arrayOf(
                        "text/plain",
                        "text/*",
                        "application/octet-stream",
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/epub+zip",
                        "application/pdf",
                        "*/*",
                    ),
                )
            },
            onLibraryImportFolderRequested = { openImportFolderTreeLauncher.launch(null) },
            postLibraryFolderImportProgress = postLibraryFolderImportProgress,
        )
    }

    fun applySystemTtsResetToDefaults() {
        prefs
            .edit()
            .remove(AppPreferenceKeys.KEY_SYSTEM_TTS_VOICE_NAME)
            .remove(AppPreferenceKeys.KEY_SYSTEM_TTS_SPEECH_RATE)
            .remove(AppPreferenceKeys.KEY_SYSTEM_TTS_PITCH)
            .apply()
        val engine = tts ?: return
        engine.setSpeechRate(1f)
        engine.setPitch(1f)
        systemTtsSpeechRate = 1f
        systemTtsPitch = 1f
        val list = voices
        val defVoice = engine.defaultVoice
        val next =
            defVoice?.let { d -> list.find { it.name == d.name } }
                ?: engine.voice?.let { c -> list.find { it.name == c.name } }
                ?: list.firstOrNull()
        selectedVoice = next
        if (next != null) {
            engine.voice = next
        }
        Toast.makeText(
            context,
            "Đã khôi phục mặc định TTS hệ thống.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    OpenFileProgressLogDialog(ui = openFileProgressLog)
    OpenFileProgressDialog(progress = openFileProgress)

    if (showEditorFontConfigDialog) {
        EditorFontConfigDialog(
            prefs = prefs,
            onDismiss = { showEditorFontConfigDialog = false },
            openSession = editorFontConfigOpenSession,
        )
    }
    if (elevenLabsSettingsVisible) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(4f),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            ElevenLabsSettingsScreen(
                prefs = prefs,
                onClose = { elevenLabsSettingsVisible = false },
            )
        }
    }
    if (systemTtsSettingsVisible) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(4f),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            SystemTtsSettingsScreen(
                ttsReady = ttsReady,
                tts = tts,
                voices = voices,
                selectedVoice = selectedVoice,
                onSelectedVoiceChange = { v ->
                    selectedVoice = v
                    prefs.edit().putString(AppPreferenceKeys.KEY_SYSTEM_TTS_VOICE_NAME, v.name).apply()
                },
                speechRate = systemTtsSpeechRate,
                onSpeechRateChange = { v ->
                    systemTtsSpeechRate = v
                    if (ttsReady) {
                        tts?.setSpeechRate(v)
                    }
                },
                onSpeechRateChangeFinished = {
                    prefs
                        .edit()
                        .putFloat(AppPreferenceKeys.KEY_SYSTEM_TTS_SPEECH_RATE, systemTtsSpeechRate)
                        .apply()
                },
                pitch = systemTtsPitch,
                onPitchChange = { v ->
                    systemTtsPitch = v
                    if (ttsReady) {
                        tts?.setPitch(v)
                    }
                },
                onPitchChangeFinished = {
                    prefs.edit().putFloat(AppPreferenceKeys.KEY_SYSTEM_TTS_PITCH, systemTtsPitch).apply()
                },
                onResetToSystemDefaults = { applySystemTtsResetToDefaults() },
                sampleText = systemTtsSampleText,
                onSampleTextChange = { next ->
                    systemTtsSampleText = next
                    prefs.edit().putString(AppPreferenceKeys.KEY_SYSTEM_TTS_SAMPLE_TEXT, next).apply()
                },
                onClose = { systemTtsSettingsVisible = false },
            )
        }
    }
}
