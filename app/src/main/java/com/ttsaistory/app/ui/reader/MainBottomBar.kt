package com.ttsaistory.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.model.TextTabSpeechEngine
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun MainBottomBar(
    readerService: ReaderService,
    tabIndex: Int,
    text: String,
    speakingParagraphIndex: Int,
    readerBottomNavBridge: ReaderBottomNavBridge?,
    /** Đổi khi mở/sync truyện — tính lại tổng câu TTS cùng lúc vào tab Text. */
    librarySyncEpoch: Int,
    activeLibraryStoryId: Long?,
    textTabSpeechEngine: TextTabSpeechEngine,
    /** Đang phát loạt TTS hệ thống (tab Văn bản). */
    systemTtsPlaybackActive: Boolean,
    /** WPM ước lượng; null trong câu đầu chưa có câu nào đọc xong. */
    systemTtsMeasuredWpm: Int?,
) {
    val paragraphServiceTotal by readerService.totalItemCount.collectAsState(initial = null)
    val navBridge = readerBottomNavBridge
    val toolbarWorking = tabIndex == 0 && navBridge?.ttsSentenceSplitWorking == true
    val totalSpeakable =
        when {
            tabIndex != 0 -> 0
            paragraphServiceTotal != null -> paragraphServiceTotal!!
            else -> 0
        }
    // Chỉ "đang tính" khi service chưa có tổng.
    val progressStillLoading =
        tabIndex == 0 &&
                !toolbarWorking &&
                paragraphServiceTotal == null
    SideEffect {
        if (tabIndex == 0) {
            ReaderReadingProgress.totalSpeakableSentenceCount.intValue = totalSpeakable
        }
    }
    val lastSpeechFromDb = navBridge?.dbLastSpeechSentenceIndex0 ?: -1
    val speaking = speakingParagraphIndex
    val fromFocusedCell = readerBottomNavBridge?.readerProgressCurrentOneBased
    val paragraphSplitEditBarShows = readerBottomNavBridge?.showParagraphSplitEditBar == true
    // Khi không phát: `last_speech_sentence_index` trong DB phản ánh câu TTS lưu cho chương đang mở.
    // Ngoại lệ: chế độ sửa theo ô (nối/tách/xóa) — luôn hiện chỉ số theo ô đang chọn (kể cả khi luôn ẩn phím).
    val curOneBased =
        when {
            progressStillLoading ->
                when {
                    speaking >= 0 -> speaking + 1
                    paragraphSplitEditBarShows && fromFocusedCell != null -> fromFocusedCell
                    lastSpeechFromDb >= 0 -> lastSpeechFromDb + 1
                    fromFocusedCell != null -> fromFocusedCell
                    else -> 1
                }

            totalSpeakable <= 0 -> 0
            speaking >= 0 -> (speaking + 1).coerceIn(1, totalSpeakable)
            paragraphSplitEditBarShows && fromFocusedCell != null ->
                fromFocusedCell.coerceIn(1, totalSpeakable)
            lastSpeechFromDb >= 0 ->
                (lastSpeechFromDb + 1).coerceIn(1, totalSpeakable)

            fromFocusedCell != null ->
                fromFocusedCell.coerceIn(1, totalSpeakable)

            else -> 1
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            fun withWebQueueStoryIdSuffix(base: String): String {
                if (tabIndex != 0) return base
                val bridge = readerBottomNavBridge
                val q = bridge?.webStoryQueueTargetStoryId
                if (bridge?.libraryWebStoryActive != true || q == null) return base
                return "$base (id=$q)"
            }
            val statusLine =
                when {
                    progressStillLoading ->
                        withWebQueueStoryIdSuffix("Đang tính tiến độ…")

                    totalSpeakable <= 0 ->
                        withWebQueueStoryIdSuffix(
                            "Chưa có câu để đọc (nội dung trống hoặc không tách được).",
                        )

                    else -> {
                        val pct = (curOneBased * 100.0) / totalSpeakable
                        withWebQueueStoryIdSuffix(
                            String.format(
                                Locale.US,
                                "%d / %d — %.2f%%",
                                curOneBased,
                                totalSpeakable,
                                pct,
                            ),
                        )
                    }
                }
            Column(modifier = Modifier.fillMaxWidth()) {
                if (tabIndex == 0) {
                    val nav = readerBottomNavBridge
                    if (nav != null && nav.showParagraphFocusSlider) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    nav.onParagraphFocusSliderChange(
                                        (nav.paragraphFocusSliderValue - 1).coerceAtLeast(0),
                                    )
                                    nav.onParagraphFocusSliderFocusCommitted()
                                },
                                enabled = nav.paragraphFocusSliderValue > 0,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Ô trước",
                                )
                            }
                            key(nav.paragraphFocusSliderMax) {
                                Box(modifier = Modifier.weight(1f)) {
                                    Slider(
                                        modifier = Modifier.fillMaxWidth(),
                                        value = nav.paragraphFocusSliderValue.toFloat(),
                                        onValueChange = { f ->
                                            nav.onParagraphFocusSliderChange(
                                                f.roundToInt().coerceIn(0, nav.paragraphFocusSliderMax),
                                            )
                                        },
                                        onValueChangeFinished = {
                                            nav.onParagraphFocusSliderFocusCommitted()
                                        },
                                        valueRange = 0f..nav.paragraphFocusSliderMax.toFloat(),
                                        steps = 0,
                                    )
                                    Text(
                                        text = nav.paragraphFocusSliderValue.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier =
                                            Modifier
                                                .align(Alignment.Center)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                                    shape = RoundedCornerShape(10.dp),
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    nav.onParagraphFocusSliderChange(
                                        (nav.paragraphFocusSliderValue + 1)
                                            .coerceAtMost(nav.paragraphFocusSliderMax),
                                    )
                                    nav.onParagraphFocusSliderFocusCommitted()
                                },
                                enabled =
                                    nav.paragraphFocusSliderValue < nav.paragraphFocusSliderMax,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Ô sau",
                                )
                            }
                        }
                    } else if (nav != null && nav.showParagraphSplitEditBar) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            IconButton(
                                onClick = { nav.onParagraphSplitEditCaseToggle() },
                                modifier =
                                    Modifier.semantics {
                                        contentDescription =
                                            if (nav.paragraphSplitEditCaseNextIsUpper) {
                                                "IN HOA toàn bộ câu đang chọn"
                                            } else {
                                                "Chuẩn hoá: chữ thường, viết hoa đầu mỗi dòng"
                                            }
                                    },
                            ) {
                                Text(
                                    text = if (nav.paragraphSplitEditCaseNextIsUpper) "AA" else "Aa",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = { nav.onParagraphSplitEditJoinUp() },
                                enabled = nav.paragraphSplitEditJoinUpEnabled,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MergeType,
                                    contentDescription = "Nối với câu trước",
                                )
                            }
                            IconButton(onClick = { nav.onParagraphSplitEditSplitAtCaret() }) {
                                Icon(
                                    imageVector = Icons.Filled.HorizontalSplit,
                                    contentDescription = "Tách xuống dòng tại con trỏ",
                                )
                            }
                            IconButton(
                                onClick = { nav.onParagraphSplitEditDelete() },
                                enabled = nav.paragraphSplitEditDeleteEnabled,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Xóa nội dung câu",
                                )
                            }
                            IconButton(
                                onClick = { nav.onParagraphSplitEditBreakPage() },
                                enabled = nav.paragraphSplitEditBreakPageEnabled,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCut,
                                    contentDescription = "Tách chương: phần từ câu hiện tại tới hết thành chương mới",
                                )
                            }
                        }
                    }
                    val prefetchLines = nav?.webPrefetchChapterQueueLines.orEmpty()
                    val deferredProgress = readerService.deferredFetchProgressLabel.trim()
                    if (prefetchLines.isNotEmpty() || deferredProgress.isNotEmpty()) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 96.dp)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            if (deferredProgress.isNotEmpty()) {
                                Text(
                                    text = "Đang nạp: $deferredProgress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                )
                            }
                            prefetchLines.take(5).forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                )
                            }
                            if (prefetchLines.size > 5) {
                                Text(
                                    text = "… +${prefetchLines.size - 5} mục",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    ) {
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (tabIndex == 0 &&
                            textTabSpeechEngine == TextTabSpeechEngine.System &&
                            systemTtsPlaybackActive
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text =
                                    systemTtsMeasuredWpm?.let { wpm ->
                                        "≈ $wpm từ/phút"
                                    } ?: "Đang đo tốc độ đọc…",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (tabIndex == 0) {
                        val navRow = readerBottomNavBridge
                        if (navRow != null) {
                            if (navRow.showParagraphSplitEditBar) {
                                IconButton(onClick = { navRow.onReaderKeyboardForceHiddenToggle() }) {
                                    Icon(
                                        imageVector =
                                            if (navRow.readerKeyboardForceHidden) {
                                                Icons.Outlined.KeyboardHide
                                            } else {
                                                Icons.Outlined.Keyboard
                                            },
                                        contentDescription =
                                            if (navRow.readerKeyboardForceHidden) {
                                                "Đang luôn ẩn bàn phím — bấm để cho phép hiện"
                                            } else {
                                                "Luôn ẩn bàn phím — bấm để bật"
                                            },
                                        tint =
                                            if (navRow.readerKeyboardForceHidden) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                            }
                            if (navRow.showPasteAndCaretStep) {
                                IconButton(onClick = { navRow.pasteFromClipboard() }) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentPaste,
                                        contentDescription = "Dán từ clipboard",
                                    )
                                }
                                IconButton(onClick = { navRow.moveCaretLeft() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Con trỏ sang trái",
                                    )
                                }
                                IconButton(onClick = { navRow.moveCaretRight() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Con trỏ sang phải",
                                    )
                                }
                            }
                            IconButton(onClick = { navRow.goTopOrCaretStart() }) {
                                Icon(
                                    imageVector =
                                        if (navRow.paragraphSplitMode) {
                                            Icons.Filled.VerticalAlignTop
                                        } else {
                                            Icons.Filled.FirstPage
                                        },
                                    contentDescription =
                                        if (navRow.paragraphSplitMode) {
                                            "Cuộn lên đầu danh sách câu"
                                        } else {
                                            "Con trỏ đầu văn bản"
                                        },
                                )
                            }
                            IconButton(onClick = { navRow.goBottomOrCaretEnd() }) {
                                Icon(
                                    imageVector =
                                        if (navRow.paragraphSplitMode) {
                                            Icons.Filled.VerticalAlignBottom
                                        } else {
                                            Icons.AutoMirrored.Filled.LastPage
                                        },
                                    contentDescription =
                                        if (navRow.paragraphSplitMode) {
                                            "Cuộn xuống cuối danh sách câu"
                                        } else {
                                            "Con trỏ cuối văn bản"
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
