package com.ttsaistory.app.ui

import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.elevenlabs.ElevenLabsPrefKeys
import com.ttsaistory.app.model.LibraryCategoryToolbarCommand
import com.ttsaistory.app.model.TextTabSpeechEngine
import com.ttsaistory.app.ui.DialogOnlineDomainParsersManage
import com.ttsaistory.app.ui.library.OpenFileProgressUi
import com.ttsaistory.app.ui.library.LibraryTab
import com.ttsaistory.app.ui.reader.ExportM4aTopBarState
import com.ttsaistory.app.ui.reader.MainBottomBar
import com.ttsaistory.app.ui.reader.DialogReaderImeHideDelays
import com.ttsaistory.app.ui.reader.ReaderTab
import com.ttsaistory.app.ui.reader.ReaderBottomNavBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalNavigationDrawerScaffold(
    drawerState: DrawerState,
    coroutineScope: CoroutineScope,
    tabIndex: Int,
    onTabIndexChange: (Int) -> Unit,
    onOpenElevenLabsFromDrawer: () -> Unit,
    onOpenSystemTtsFromDrawer: () -> Unit,
    onNavigateLibraryToolbar: (LibraryCategoryToolbarCommand) -> Unit,
    onTopBarTextSettingsClick: () -> Unit,
    onOpenEditorFontConfigFromDrawer: () -> Unit,
    textTabSpeechEngine: TextTabSpeechEngine,
    prefs: SharedPreferences,
    text: String,
    speakingParagraphIndex: Int,
    readerBottomNavBridge: ReaderBottomNavBridge?,
    storyLibrary: StoryLibraryRepository,
    libraryRefreshTrigger: Int,
    libraryToolbarCommand: LibraryCategoryToolbarCommand?,
    onLibraryToolbarCommandConsumed: () -> Unit,
    activeLibraryStoryId: Long?,
    onLibraryChanged: () -> Unit,
    librarySyncEpoch: Int,
    tts: TextToSpeech?,
    ttsReady: Boolean,
    elevenLabsPlayJob: Job?,
    /** Loạt đọc TTS hệ thống còn utterance (không dùng [TextToSpeech.isSpeaking] cho nút Play). */
    systemTtsPlaybackActive: Boolean,
    /** WPM ước lượng từ thời gian phát thực tế; null khi chưa đủ dữ liệu (câu đầu). */
    systemTtsMeasuredWpm: Int?,
    onEditorTextChange: (String) -> Unit,
    onTextTabSpeechEngineChange: (TextTabSpeechEngine) -> Unit,
    onStopAllSpeechReading: () -> Unit,
    onPlayParagraphs: (List<String>, Int) -> Unit,
    onLibraryFileSynced: () -> Unit,
    onLibraryDataChanged: () -> Unit,
    onSavedLibraryStoryFromEditor: (Long) -> Unit,
    onRegisterParagraphDraftFlush: ((() -> Unit) -> Unit)?,
    /** Tab Đọc & soạn: chuỗi chuẩn hoá để ghi file thư viện khi đổi chương; `null` khi huỷ đăng ký. */
    onRegisterLibraryTabTextSerializer: (((() -> String)?) -> Unit)?,
    onRegisterExportM4aForTopBar: ((ExportM4aTopBarState?) -> Unit)?,
    exportM4aTopBar: ExportM4aTopBarState?,
    onRegisterReaderBottomNav: ((ReaderBottomNavBridge?) -> Unit)?,
    systemTtsSpeechRate: Float,
    systemTtsPitch: Float,
    onOpenStoryFromLibrary: suspend (Long) -> Boolean,
    /** Mở file văn bản qua SAF (bộ nhớ / thẻ SD). */
    onOpenTextFileFromStorage: () -> Unit,
    /** Tab Thư viện: mở SAF chọn thư mục để import thành các chương trong một truyện. */
    onLibraryImportFolderRequested: () -> Unit,
    /** Cập nhật popup tiến trình import / nhập lại thư mục (luồng chính). */
    postLibraryFolderImportProgress: (OpenFileProgressUi?) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scheme = MaterialTheme.colorScheme
    // Nền chrome: pha primaryContainer + surface cho top bar / tab, tách khỏi nội dung.
    val topBarTint = lerp(scheme.primaryContainer, scheme.surfaceContainerHigh, 0.38f)
    val tabStripTint = lerp(scheme.primaryContainer, scheme.surface, 0.48f)
    val drawerBase = lerp(scheme.primaryContainer, scheme.surfaceContainerLow, 0.22f)
    val drawerHeaderGradientEnd = lerp(scheme.primary.copy(alpha = 0.14f), drawerBase, 0.55f)
    var showAboutDialog by remember { mutableStateOf(false) }
    var showReaderImeHideDelaysDialog by remember { mutableStateOf(false) }
    var showDrawerOnlineDomainParserManage by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Tab Text: khi drawer đóng, tắt vuốt mép; tab Thư viện bật; khi drawer mở luôn bật cử chỉ (scrim / vuốt đóng).
        gesturesEnabled = drawerState.isOpen || tabIndex != 0,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = drawerBase,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to drawerHeaderGradientEnd,
                                    1f to drawerBase,
                                ),
                            ),
                ) {
                    Text(
                        text = "TTS AI Story",
                        modifier =
                            Modifier
                                .padding(horizontal = 28.dp)
                                .padding(top = 24.dp, bottom = 20.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.primary,
                    )
                }
                NavigationDrawerItem(
                    icon = {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    },
                    label = { Text("Mở file…") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onOpenTextFileFromStorage()
                        }
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Article,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Đọc & soạn") },
                    selected = tabIndex == 0,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onTabIndexChange(0)
                        }
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Filled.DynamicFeed,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Thư viện") },
                    selected = tabIndex == 1,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onTabIndexChange(1)
                        }
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Cấu hình ElevenLabs") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onOpenElevenLabsFromDrawer()
                        }
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Cấu hình TTS hệ thống") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onOpenSystemTtsFromDrawer()
                        }
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(Icons.Filled.Web, contentDescription = null)
                    },
                    label = { Text("Cấu hình Parser online") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            showDrawerOnlineDomainParserManage = true
                        }
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Outlined.FontDownload,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Fonts") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onOpenEditorFontConfigFromDrawer()
                        }
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(Icons.Outlined.Timer, contentDescription = null)
                    },
                    label = { Text("Độ trễ ẩn bàn phím") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            showReaderImeHideDelaysDialog = true
                        }
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                    },
                    label = { Text("Giới thiệu") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            showAboutDialog = true
                        }
                    },
                )
            }
        },
    ) {
        Scaffold(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AppMainTopAppBar(
                    scrollBehavior = scrollBehavior,
                    topBarTint = topBarTint,
                    onNavigationMenuClick = { coroutineScope.launch { drawerState.open() } },
                    tabIndex = tabIndex,
                    textTabSpeechEngine = textTabSpeechEngine,
                    onTopBarTextSettingsClick = onTopBarTextSettingsClick,
                    onLibraryAddCategoryClick = {
                        onNavigateLibraryToolbar(LibraryCategoryToolbarCommand.AddCategory)
                    },
                    onLibraryImportFolderClick = onLibraryImportFolderRequested,
                    exportM4aTopBar = exportM4aTopBar,
                )
            },
            bottomBar = {
                MainBottomBar(
                    tabIndex = tabIndex,
                    text = text,
                    speakingParagraphIndex = speakingParagraphIndex,
                    readerBottomNavBridge = readerBottomNavBridge,
                    librarySyncEpoch = librarySyncEpoch,
                    activeLibraryStoryId = activeLibraryStoryId,
                    textTabSpeechEngine = textTabSpeechEngine,
                    systemTtsPlaybackActive = systemTtsPlaybackActive,
                    systemTtsMeasuredWpm = systemTtsMeasuredWpm,
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                // zIndex + Surface tách TabRow khỏi nội dung phía dưới; Box + clipToBounds giới hạn vùng vẽ.
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .zIndex(1f),
                    color = Color.Transparent,
                    shadowElevation = 3.dp,
                ) {
                    TabRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        0f to tabStripTint,
                                        1f to lerp(tabStripTint, scheme.surface, 0.4f),
                                    ),
                                ),
                        selectedTabIndex = tabIndex,
                        containerColor = Color.Transparent,
                        contentColor = scheme.onSurface,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    ) {
                        Tab(
                            selected = tabIndex == 0,
                            onClick = { onTabIndexChange(0) },
                            text = { Text("Đọc & soạn") },
                        )
                        Tab(
                            selected = tabIndex == 1,
                            onClick = { onTabIndexChange(1) },
                            text = { Text("Thư viện") },
                        )
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .zIndex(0f)
                            .clipToBounds(),
                ) {
                    when (tabIndex) {
                        0 ->
                            ReaderTab(
                                modifier = Modifier.fillMaxSize(),
                                prefs = prefs,
                                text = text,
                                onTextChange = onEditorTextChange,
                                tts = tts,
                                ttsReady = ttsReady,
                                speechEngine = textTabSpeechEngine,
                                onSpeechEngineChange = onTextTabSpeechEngineChange,
                                speechEngineReady =
                                    when (textTabSpeechEngine) {
                                        TextTabSpeechEngine.System -> ttsReady && tts != null
                                        TextTabSpeechEngine.ElevenLabs ->
                                            ElevenLabsPrefKeys.resolveApiKey(prefs).isNotEmpty()
                                    },
                                elevenLabsJobActive = elevenLabsPlayJob?.isActive == true,
                                systemTtsPlaybackActive = systemTtsPlaybackActive,
                                onStopAllSpeechReading = onStopAllSpeechReading,
                                onPlayParagraphs = onPlayParagraphs,
                                speakingParagraphIndex = speakingParagraphIndex,
                                bookmarkResetKey = activeLibraryStoryId ?: 0L,
                                libraryRepository = storyLibrary,
                                activeLibraryStoryId = activeLibraryStoryId,
                                librarySyncEpoch = librarySyncEpoch,
                                onLibraryFileSynced = onLibraryFileSynced,
                                onLibraryDataChanged = onLibraryDataChanged,
                                onSavedLibraryStory = onSavedLibraryStoryFromEditor,
                                onOpenLibraryStory = onOpenStoryFromLibrary,
                                onRegisterParagraphDraftFlush = onRegisterParagraphDraftFlush,
                                onRegisterLibraryTabTextSerializer =
                                    onRegisterLibraryTabTextSerializer,
                                onRegisterExportM4aForTopBar = onRegisterExportM4aForTopBar,
                                onRegisterReaderBottomNav = onRegisterReaderBottomNav,
                                systemTtsSpeechRate = systemTtsSpeechRate,
                                systemTtsPitch = systemTtsPitch,
                            )
                        1 ->
                            LibraryTab(
                                modifier = Modifier.fillMaxSize(),
                                repository = storyLibrary,
                                refreshTrigger = libraryRefreshTrigger,
                                toolbarCommand = libraryToolbarCommand,
                                onToolbarCommandConsumed = onLibraryToolbarCommandConsumed,
                                activeEditingStoryId = activeLibraryStoryId,
                                onLibraryChanged = onLibraryChanged,
                                onOpenStory = onOpenStoryFromLibrary,
                                postLibraryFolderImportProgress = postLibraryFolderImportProgress,
                            )
                    }
                }
            }
        }
    }

    if (showDrawerOnlineDomainParserManage) {
        DialogOnlineDomainParsersManage(
            repository = storyLibrary,
            onDismissRequest = { showDrawerOnlineDomainParserManage = false },
        )
    }

    if (showReaderImeHideDelaysDialog) {
        DialogReaderImeHideDelays(
            onDismissRequest = { showReaderImeHideDelaysDialog = false },
            prefs = prefs,
        )
    }

    if (showAboutDialog) {
        DialogAppAbout(onDismissRequest = { showAboutDialog = false })
    }
}
