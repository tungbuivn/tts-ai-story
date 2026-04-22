package com.ttsaistory.app.ui.library

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.ttsaistory.app.data.LibraryStoryRow
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.data.normalizeOnlineStoryPageUrlForMatch
import com.ttsaistory.app.data.normalizeWebCategoryUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Khi đang chỉnh sửa truyện web: theo chuỗi [LibraryStoryRow.onlineNextPageUrl] tối đa 10 bước.
 * URL đã có truyện tương ứng trong DB thì bỏ qua tải WebView (chỉ nhảy tiếp).
 * Mỗi lần thật sự tải WebView: cách lần trước tối thiểu 5 giây; thất bại thì chờ 5 giây và thử lại, tối đa 5 lần.
 */
object OnlineWebStoryNextPagePrefetch {
    private const val MIN_MS_BETWEEN_WEB_FETCHES = 5_000L
    private const val MAX_FORWARD_HOPS = 10
    private const val MAX_ATTEMPTS_PER_WEB_FETCH = 5
    private const val RETRY_DELAY_MS = 5_000L

    private enum class PrefetchQueueUiPhase {
        /** Chỉ liệt kê các URL sẽ cần WebView (chưa tới bước chờ/tải). */
        IDLE,
        /** Đang chờ khoảng trễ tối thiểu giữa hai lần mở WebView. */
        WAITING_GAP,
        /** Đang chạy headless sync cho URL đầu hàng đợi. */
        SYNCING,
    }

    suspend fun prefetchForwardChainWhileEditing(
        context: Context,
        startStoryId: Long,
        repository: StoryLibraryRepository,
        onLibraryDataChanged: () -> Unit,
        onPrefetchQueueLines: (List<String>) -> Unit = {},
        onQueueTargetStoryId: (Long?) -> Unit = {},
    ) {
        try {
            prefetchForwardChainWhileEditingInner(
                context = context,
                startStoryId = startStoryId,
                repository = repository,
                onLibraryDataChanged = onLibraryDataChanged,
                onPrefetchQueueLines = onPrefetchQueueLines,
                onQueueTargetStoryId = onQueueTargetStoryId,
            )
        } finally {
            withContext(Dispatchers.Main) {
                onPrefetchQueueLines(emptyList())
                onQueueTargetStoryId(null)
            }
        }
    }

    private suspend fun prefetchForwardChainWhileEditingInner(
        context: Context,
        startStoryId: Long,
        repository: StoryLibraryRepository,
        onLibraryDataChanged: () -> Unit,
        onPrefetchQueueLines: (List<String>) -> Unit,
        onQueueTargetStoryId: (Long?) -> Unit,
    ) {
        val app = context.applicationContext
        var currentStoryId = startStoryId
        var hops = 0
        var lastWebFetchElapsedMs: Long = 0L

        suspend fun emitQueue(
            phase: PrefetchQueueUiPhase,
        ) {
            val lines =
                withContext(Dispatchers.IO) {
                    buildPrefetchQueueDisplayLines(
                        repository = repository,
                        fromStoryId = currentStoryId,
                        remainingHopsBudget = (MAX_FORWARD_HOPS - hops).coerceAtLeast(0),
                        phase = phase,
                    )
                }
            withContext(Dispatchers.Main) { onPrefetchQueueLines(lines) }
        }

        while (hops < MAX_FORWARD_HOPS && coroutineContext.isActive) {
            withContext(Dispatchers.Main) { onQueueTargetStoryId(null) }
            emitQueue(PrefetchQueueUiPhase.IDLE)
            val row =
                withContext(Dispatchers.IO) { repository.getStory(currentStoryId) } ?: return
            val nextUrl =
                row.onlineNextPageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
            val selfUrl = row.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (selfUrl != null &&
                normalizeOnlineStoryPageUrlForMatch(selfUrl) ==
                    normalizeOnlineStoryPageUrlForMatch(nextUrl)
            ) {
                return
            }

            val existing =
                withContext(Dispatchers.IO) {
                    repository.findStoryInCategoryByOnlinePageUrl(row.categoryId, nextUrl)
                }

            if (existing != null) {
                if (existing.id == currentStoryId) return
                val needsWeb =
                    withContext(Dispatchers.IO) {
                        repository.storyNeedsOnlineContentRefresh(existing)
                    }
                if (!needsWeb) {
                    currentStoryId = existing.id
                    hops++
                    continue
                }
                withContext(Dispatchers.Main) { onQueueTargetStoryId(existing.id) }
                emitQueue(PrefetchQueueUiPhase.WAITING_GAP)
                enforceMinGapBeforeWebFetch(lastWebFetchElapsedMs)
                emitQueue(PrefetchQueueUiPhase.SYNCING)
                val ok =
                    syncOnlineStoryWithRetries(
                        app,
                        existing.id,
                        repository,
                    )
                if (!ok) return
                lastWebFetchElapsedMs = SystemClock.elapsedRealtime()
                withContext(Dispatchers.Main) { onLibraryDataChanged() }
                currentStoryId = existing.id
                hops++
                continue
            }

            emitQueue(PrefetchQueueUiPhase.WAITING_GAP)
            enforceMinGapBeforeWebFetch(lastWebFetchElapsedMs)
            emitQueue(PrefetchQueueUiPhase.SYNCING)
            val title =
                withContext(Dispatchers.IO) {
                    repository.suggestUniqueStoryTitle(
                        row.categoryId,
                        provisionalTitleForOnlinePageUrl(nextUrl),
                    )
                }
            val newId =
                withContext(Dispatchers.IO) {
                    repository.insertStory(
                        categoryId = row.categoryId,
                        title = title,
                        body = "",
                        onlinePageUrl = normalizeWebCategoryUrl(nextUrl),
                    )
                }
            withContext(Dispatchers.Main) { onQueueTargetStoryId(newId) }
            val ok =
                syncOnlineStoryWithRetries(
                    app,
                    newId,
                    repository,
                )
            if (!ok) {
                withContext(Dispatchers.IO) { repository.deleteStory(newId) }
                return
            }
            lastWebFetchElapsedMs = SystemClock.elapsedRealtime()
            withContext(Dispatchers.Main) { onLibraryDataChanged() }
            currentStoryId = newId
            hops++
        }
    }

    private suspend fun enforceMinGapBeforeWebFetch(lastSuccessElapsedMs: Long) {
        if (lastSuccessElapsedMs <= 0L) return
        val elapsed = SystemClock.elapsedRealtime() - lastSuccessElapsedMs
        val wait = MIN_MS_BETWEEN_WEB_FETCHES - elapsed
        if (wait > 0) delay(wait)
    }

    private suspend fun syncOnlineStoryWithRetries(
        context: Context,
        storyId: Long,
        repository: StoryLibraryRepository,
    ): Boolean {
        for (attempt in 1..MAX_ATTEMPTS_PER_WEB_FETCH) {
            try {
                OnlineCategoryHeadlessStoryTextSync.syncOnlineStoryFromWebPage(
                    context = context,
                    storyId = storyId,
                    repository = repository,
                    bypassHttpCache = false,
                )
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt >= MAX_ATTEMPTS_PER_WEB_FETCH) return false
                delay(RETRY_DELAY_MS)
            }
        }
        return false
    }

    /**
     * Liệt kê các URL sẽ cần tải bằng WebView (bỏ qua bước chỉ khớp DB đã có nội dung).
     * Dừng sau URL tạo truyện mới vì chưa biết `online_next_page_url` trước khi parse.
     */
    private fun computeRemainingWebDownloadUrls(
        repository: StoryLibraryRepository,
        fromStoryId: Long,
        maxHopsRemaining: Int,
    ): List<String> {
        if (maxHopsRemaining <= 0) return emptyList()
        val urls = mutableListOf<String>()
        var cid = fromStoryId
        var hopsWalked = 0
        while (hopsWalked < maxHopsRemaining) {
            val row = repository.getStory(cid) ?: break
            val nu =
                row.onlineNextPageUrl?.trim()?.takeIf { it.isNotEmpty() }
                    ?: break
            if (isOnlineNextSelfLoop(row, nu)) break
            val ex = repository.findStoryInCategoryByOnlinePageUrl(row.categoryId, nu)
            if (ex == null) {
                urls.add(nu)
                break
            }
            if (ex.id == cid) break
            if (repository.storyNeedsOnlineContentRefresh(ex)) {
                urls.add(nu)
                cid = ex.id
                hopsWalked++
                continue
            }
            cid = ex.id
            hopsWalked++
        }
        return urls
    }

    private fun isOnlineNextSelfLoop(
        row: LibraryStoryRow,
        nextUrl: String,
    ): Boolean {
        val selfUrl = row.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return normalizeOnlineStoryPageUrlForMatch(selfUrl) ==
            normalizeOnlineStoryPageUrlForMatch(nextUrl)
    }

    private fun buildPrefetchQueueDisplayLines(
        repository: StoryLibraryRepository,
        fromStoryId: Long,
        remainingHopsBudget: Int,
        phase: PrefetchQueueUiPhase,
    ): List<String> {
        val urls = computeRemainingWebDownloadUrls(repository, fromStoryId, remainingHopsBudget)
        if (urls.isEmpty()) return emptyList()
        return urls.mapIndexed { index, url ->
            val short = shortWebUrlForUi(url)
            when {
                index == 0 && phase == PrefetchQueueUiPhase.WAITING_GAP ->
                    "⏳ Chờ trước khi tải: $short"

                index == 0 && phase == PrefetchQueueUiPhase.SYNCING ->
                    "▶ Đang tải: $short"

                else -> "○ Hàng đợi: $short"
            }
        }
    }

    private fun shortWebUrlForUi(url: String): String {
        val norm = normalizeWebCategoryUrl(url)
        val uri = Uri.parse(norm)
        val host = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: return norm.take(48)
        val segments =
            uri.path?.trim('/')?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
        val tail = segments.takeLast(2).joinToString("/")
        val s =
            if (tail.isNotEmpty()) {
                "$host/…/$tail"
            } else {
                host
            }
        return s.take(56)
    }

    private fun provisionalTitleForOnlinePageUrl(url: String): String {
        val norm = normalizeWebCategoryUrl(url)
        val uri = Uri.parse(norm)
        val host = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: "web"
        val segments = uri.path?.trim('/')?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
        val seg = segments.lastOrNull()?.take(60) ?: uri.lastPathSegment?.take(60) ?: "trang"
        return "$host — $seg".take(120)
    }
}
