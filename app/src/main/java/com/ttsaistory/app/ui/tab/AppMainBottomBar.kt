package com.ttsaistory.app.ui.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FirstPage
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttsaistory.app.AnrDiagLog
import com.ttsaistory.app.domain.sanitizeParagraphText
import kotlin.math.roundToInt
import com.ttsaistory.app.domain.splitIntoParagraphs
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppMainBottomBar(
    tabIndex: Int,
    text: String,
    speakingParagraphIndex: Int,
    textTabBottomNavBridge: TextTabBottomNavBridge?,
    /** Đổi khi mở/sync truyện — tính lại tổng câu TTS cùng lúc vào tab Text. */
    librarySyncEpoch: Int,
    activeLibraryStoryId: Long?,
) {
    var totalSpeakableDeferred by remember { mutableStateOf<Int?>(null) }
    // Không key theo [text]: tránh splitIntoParagraphs toàn văn mỗi lần gõ; chỉ khi vào tab Text
    // (lần đầu hoặc từ tab khác) hoặc đổi truyện/sync ([librarySyncEpoch], [activeLibraryStoryId]).
    // [textTabBottomNavBridge?.paragraphSplitMode]: khi bật theo đoạn, [text] có thể rỗng / chưa flush
    // trong khi nội dung nằm ở ô — không gán 0; tổng câu lấy từ bridge (toolbar) sau split.
    LaunchedEffect(
        tabIndex,
        librarySyncEpoch,
        activeLibraryStoryId,
        textTabBottomNavBridge?.paragraphSplitMode,
    ) {
        if (tabIndex != 0) return@LaunchedEffect
        // Xóa tổng cũ ngay khi đổi truyện / tab / chế độ — tránh một vài frame vẫn dùng mẫu số
        // của file trước trong khi bookmark prefs đã là file mới → coerce / % sai khi mở từ thư viện.
        totalSpeakableDeferred = null
        if (text.isEmpty()) {
            if (textTabBottomNavBridge?.paragraphSplitMode == true) {
                totalSpeakableDeferred = null
                AnrDiagLog.i(
                    "AppMainBottomBar speakableCount text empty paragraph mode -> defer toolbar",
                )
            } else {
                totalSpeakableDeferred = 0
                AnrDiagLog.i("AppMainBottomBar speakableCount text empty -> 0")
            }
            return@LaunchedEffect
        }
        val snap = text
        val t0 =
            AnrDiagLog.begin(
                "AppMainBottomBar splitIntoParagraphs+count len=${snap.length} tab=$tabIndex epoch=$librarySyncEpoch sid=$activeLibraryStoryId",
            )
        val n =
            withContext(Dispatchers.Default) {
                splitIntoParagraphs(snap).count { sanitizeParagraphText(it).isNotEmpty() }
            }
        if (snap == text) {
            totalSpeakableDeferred = n
            AnrDiagLog.end(
                "AppMainBottomBar splitIntoParagraphs+count -> n=$n",
                t0,
            )
        } else {
            AnrDiagLog.i("AppMainBottomBar speakableCount dropped (text changed mid-job)")
        }
    }
    val navBridge = textTabBottomNavBridge
    val toolbarSpeakableTotal = if (tabIndex == 0) navBridge?.ttsSpeakableSentenceTotal else null
    val toolbarWorking = tabIndex == 0 && navBridge?.ttsSentenceSplitWorking == true
    val totalSpeakable =
        when {
            tabIndex != 0 -> totalSpeakableDeferred ?: 0
            toolbarSpeakableTotal != null -> toolbarSpeakableTotal
            else -> totalSpeakableDeferred ?: 0
        }
    // Chỉ "đang tính" khi chưa có cả tổng từ [text] (deferred) lẫn từ toolbar; không kẹt vì theo đoạn
    // trong khi deferred đã có — tổng hiển thị vẫn ưu tiên toolbar khi có ([totalSpeakable] phía trên).
    val progressStillLoading =
        tabIndex == 0 &&
            !toolbarWorking &&
            toolbarSpeakableTotal == null &&
            totalSpeakableDeferred == null
    SideEffect {
        if (tabIndex == 0) {
            StoryReadingProgressGlobal.totalSpeakableSentenceCount.intValue = totalSpeakable
        }
    }
    val lastReadingParagraphIndex = StoryReadingProgressGlobal.currentSentenceIndex0Based.intValue
    val speaking = speakingParagraphIndex
    val fromFocusedCell = textTabBottomNavBridge?.readerProgressCurrentOneBased
    // Khi không phát: bookmark (prefs, vừa persist khi Stop) phản ánh câu TTS thực tế; ô focus
    // có thể chưa theo kịp TTS nên không được ưu tiên hơn bookmark — tránh nhảy về bookmark/ô cũ.
    val curOneBased =
        when {
            progressStillLoading ->
                when {
                    speaking >= 0 -> speaking + 1
                    lastReadingParagraphIndex >= 0 -> lastReadingParagraphIndex + 1
                    fromFocusedCell != null -> fromFocusedCell
                    else -> 1
                }
            totalSpeakable <= 0 -> 0
            speaking >= 0 -> (speaking + 1).coerceIn(1, totalSpeakable)
            lastReadingParagraphIndex >= 0 ->
                (lastReadingParagraphIndex + 1).coerceIn(1, totalSpeakable)
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
        val statusLine =
            when {
                progressStillLoading -> "Đang tính tiến độ…"
                totalSpeakable <= 0 ->
                    "Chưa có câu để đọc (nội dung trống hoặc không tách được)."
                else -> {
                    val pct = (curOneBased * 100.0) / totalSpeakable
                    String.format(
                        Locale.US,
                        "%d / %d — %.2f%%",
                        curOneBased,
                        totalSpeakable,
                        pct,
                    )
                }
            }
        Column(modifier = Modifier.fillMaxWidth()) {
            if (tabIndex == 0) {
                val nav = textTabBottomNavBridge
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
                            Slider(
                                modifier = Modifier.weight(1f),
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
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusLine,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tabIndex == 0) {
                    val navRow = textTabBottomNavBridge
                    if (navRow != null) {
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
                                        "Cuộn lên đầu danh sách đoạn"
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
                                        "Cuộn xuống cuối danh sách đoạn"
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
