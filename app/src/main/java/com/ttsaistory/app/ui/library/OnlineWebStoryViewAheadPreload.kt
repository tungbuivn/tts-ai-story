package com.ttsaistory.app.ui.library

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.ttsaistory.app.data.LibraryStoryRow
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.data.normalizeOnlineStoryPageUrlForMatch
import com.ttsaistory.app.ui.reader.ReaderService
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Khi **chỉ xem** truyện web: nền tải / parse theo chuỗi `online_next_page_url` từ chương đang mở.
 *
 * - Mặc định (không bật fetch-all): tối đa [VIEW_AHEAD_NEXT_CHAPTER_LIMIT] chương **kế** sau chương
 *   đang mở — cùng ý với cửa sổ prefetch EPUB/PDF (10 mục).
 * - Khi user bật Continue fetch (fetch-all): quét toàn chuỗi phía trước; mỗi lần vào worker chỉ
 *   sync tối đa [VIEW_AHEAD_NEXT_CHAPTER_LIMIT] chương rồi nhường vòng lặp ngoài — tránh một lần
 *   chạy tải «mãi mãi»; cờ «còn việc» chỉ dùng `tailStillHasNextPage` khi crawl toàn chuỗi.
 */
object OnlineWebStoryViewAheadPreload {
    /** Giống `DEFERRED_ARCHIVE_PREFETCH_DELTA` trong AppTabs: số chương kế tối đa khi không fetch-all. */
    const val VIEW_AHEAD_NEXT_CHAPTER_LIMIT = 10

    /**
     * @param crawlEntireForwardChain `false` (mặc định): chỉ xét tối đa [VIEW_AHEAD_NEXT_CHAPTER_LIMIT]
     *   chương kế sau anchor. `true` khi Continue fetch: quét cả chuỗi; [tailStillHasNextPage] chỉ
     *   áp dụng trong chế độ này.
     */
    suspend fun hasForwardPrefetchRemaining(
        repository: StoryLibraryRepository,
        anchorLibraryStoryId: Long,
        crawlEntireForwardChain: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val maxHops =
                if (crawlEntireForwardChain) {
                    null
                } else {
                    VIEW_AHEAD_NEXT_CHAPTER_LIMIT
                }
            val (firstPending, tailStillHasNextPage) =
                scanForwardOnlinePrefetchState(
                    repository = repository,
                    anchorStoryId = anchorLibraryStoryId,
                    recursionDepth = 0,
                    maxSuccessorHopsFromAnchor = maxHops,
                )
            firstPending != null || (crawlEntireForwardChain && tailStillHasNextPage)
        }

    /**
     * Dùng cho nút Continue fetch trên top bar: còn việc trong cửa sổ 10 chương **hoặc** còn việc
     * sâu hơn trong chuỗi web (tránh ẩn nút khi 10 chương kế đã xong nhưng truyện vẫn dài phía sau).
     */
    suspend fun hasWebForwardPrefetchAnyRemainingForTopBar(
        repository: StoryLibraryRepository,
        anchorLibraryStoryId: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (hasForwardPrefetchRemaining(repository, anchorLibraryStoryId, crawlEntireForwardChain = false)) {
                return@withContext true
            }
            hasForwardPrefetchRemaining(repository, anchorLibraryStoryId, crawlEntireForwardChain = true)
        }

    /**
     * @param crawlEntireForwardChain `false`: chỉ tìm/sync trong [VIEW_AHEAD_NEXT_CHAPTER_LIMIT] chương kế.
     * @param maxWebSyncRoundsPerRun tối đa số lần mở WebView mỗi lần gọi (mặc định = cửa sổ 10 chương).
     */
    suspend fun preloadNextTenWhileViewing(
        context: Context,
        anchorLibraryStoryId: Long,
        repository: StoryLibraryRepository,
        readerService: ReaderService? = null,
        onLibraryDataChanged: () -> Unit,
        onQueueTargetStoryId: (Long?) -> Unit = {},
        crawlEntireForwardChain: Boolean = false,
        maxWebSyncRoundsPerRun: Int = VIEW_AHEAD_NEXT_CHAPTER_LIMIT,
    ) {
        val app = context.applicationContext
        var lastWebFetchElapsedMs = 0L
        val maxHops =
            if (crawlEntireForwardChain) {
                null
            } else {
                VIEW_AHEAD_NEXT_CHAPTER_LIMIT
            }
        try {
            var round = 0
            while (round < maxWebSyncRoundsPerRun && coroutineContext.isActive) {
                round++
                val targetId =
                    withContext(Dispatchers.IO) {
                        scanForwardOnlinePrefetchState(
                            repository = repository,
                            anchorStoryId = anchorLibraryStoryId,
                            recursionDepth = 0,
                            maxSuccessorHopsFromAnchor = maxHops,
                        ).first
                    } ?: break

                withContext(Dispatchers.Main) { onQueueTargetStoryId(targetId) }
                enforceMinGapBeforeWebFetch(lastWebFetchElapsedMs)
                val ok =
                    syncOnlineStoryWithRetries(
                        context = app,
                        storyId = targetId,
                        repository = repository,
                        readerService = readerService,
                    )
                if (!ok) break
                lastWebFetchElapsedMs = SystemClock.elapsedRealtime()
                withContext(Dispatchers.Main) { onLibraryDataChanged() }
            }
        } finally {
            withContext(Dispatchers.Main) { onQueueTargetStoryId(null) }
        }
    }

    /** Giới hạn bước duyệt chuỗi next-page mỗi lần gọi (truyện web không biết trước độ dài). */
    private const val MAX_CHAIN_PROBE_STEPS = 512

    /** Giới hạn số lần nối tiếp khi chuỗi toàn chương đã parse — tránh đệ quy quá sâu. */
    private const val MAX_SCAN_RECURSION = 128

    private const val MIN_MS_BETWEEN_WEB_FETCHES = 5_000L
    private const val MAX_ATTEMPTS_PER_WEB_FETCH = 5
    private const val RETRY_DELAY_MS = 5_000L

    /**
     * @param maxSuccessorHopsFromAnchor `null` = không giới hạn độ sâu theo anchor. Khác `null` =
     *   chỉ xét chương neo (hop 0) và tối đa N chương **kế** (hop 1..N).
     * @return `first` = id chương đầu tiên cần sync; `second` = (chỉ khi crawl không giới hạn) còn
     *   trang kế http(s) nhưng chưa có bản ghi kế sau khi đã thử chèn.
     */
    private fun scanForwardOnlinePrefetchState(
        repository: StoryLibraryRepository,
        anchorStoryId: Long,
        recursionDepth: Int,
        maxSuccessorHopsFromAnchor: Int?,
    ): Pair<Long?, Boolean> {
        if (recursionDepth > MAX_SCAN_RECURSION) {
            return Pair(null, false)
        }
        val useTailFlag = maxSuccessorHopsFromAnchor == null
        var current = anchorStoryId
        var hopFromAnchor = 0
        var steps = 0
        while (steps < MAX_CHAIN_PROBE_STEPS) {
            steps++
            if (maxSuccessorHopsFromAnchor != null && hopFromAnchor > maxSuccessorHopsFromAnchor) {
                return Pair(null, false)
            }
            repository.ensurePlaceholderStoryForStoredOnlineNextPageUrl(current)
            val row = repository.getStory(current) ?: return Pair(null, false)
            if (repository.storyNeedsOnlineContentRefresh(row)) {
                return Pair(row.id, false)
            }
            val nextUrl =
                row.onlineNextPageUrl?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return Pair(null, false)
            if (isOnlineNextSelfLoop(row, nextUrl)) {
                return Pair(null, false)
            }
            if (!isHttpOrHttpsUrl(nextUrl)) {
                return Pair(null, false)
            }
            var nextRow =
                repository.findStoryInCategoryByOnlinePageUrl(row.categoryId, nextUrl)
            if (nextRow == null) {
                repository.ensureOnlineNextChapterStoryRow(
                    currentStoryId = row.id,
                    nextPageOverride = nextUrl,
                )
                nextRow =
                    repository.findStoryInCategoryByOnlinePageUrl(row.categoryId, nextUrl)
            }
            if (nextRow == null) {
                return Pair(null, row.onlineContentParseOk && useTailFlag)
            }
            if (nextRow.id == current) {
                return Pair(null, false)
            }
            val nextHop = hopFromAnchor + 1
            if (maxSuccessorHopsFromAnchor != null && nextHop > maxSuccessorHopsFromAnchor) {
                return Pair(null, false)
            }
            hopFromAnchor = nextHop
            current = nextRow.id
        }
        if (!useTailFlag) {
            return Pair(null, false)
        }
        repository.ensurePlaceholderStoryForStoredOnlineNextPageUrl(current)
        val tail = repository.getStory(current) ?: return Pair(null, false)
        if (repository.storyNeedsOnlineContentRefresh(tail)) {
            return Pair(tail.id, false)
        }
        val tailNext =
            tail.onlineNextPageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return Pair(null, false)
        if (isOnlineNextSelfLoop(tail, tailNext) || !isHttpOrHttpsUrl(tailNext)) {
            return Pair(null, false)
        }
        val deeper = repository.findStoryInCategoryByOnlinePageUrl(tail.categoryId, tailNext)
        if (deeper != null && repository.storyNeedsOnlineContentRefresh(deeper)) {
            return Pair(deeper.id, false)
        }
        if (deeper != null) {
            return scanForwardOnlinePrefetchState(
                repository = repository,
                anchorStoryId = deeper.id,
                recursionDepth = recursionDepth + 1,
                maxSuccessorHopsFromAnchor = null,
            )
        }
        repository.ensureOnlineNextChapterStoryRow(
            currentStoryId = tail.id,
            nextPageOverride = tailNext,
        )
        val deeperRetry =
            repository.findStoryInCategoryByOnlinePageUrl(tail.categoryId, tailNext)
        return when {
            deeperRetry != null && repository.storyNeedsOnlineContentRefresh(deeperRetry) ->
                Pair(deeperRetry.id, false)
            deeperRetry != null ->
                scanForwardOnlinePrefetchState(
                    repository = repository,
                    anchorStoryId = deeperRetry.id,
                    recursionDepth = recursionDepth + 1,
                    maxSuccessorHopsFromAnchor = null,
                )
            else -> Pair(null, tail.onlineContentParseOk && useTailFlag)
        }
    }

    private fun isOnlineNextSelfLoop(
        row: LibraryStoryRow,
        nextUrl: String,
    ): Boolean {
        val selfUrl = row.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return normalizeOnlineStoryPageUrlForMatch(selfUrl) ==
            normalizeOnlineStoryPageUrlForMatch(nextUrl)
    }

    private fun isHttpOrHttpsUrl(url: String): Boolean {
        val s = Uri.parse(url).scheme?.lowercase(Locale.ROOT)
        return s == "http" || s == "https"
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
        readerService: ReaderService?,
    ): Boolean {
        for (attempt in 1..MAX_ATTEMPTS_PER_WEB_FETCH) {
            try {
                OnlineCategoryHeadlessStoryTextSync.syncOnlineStoryFromWebPage(
                    context = context,
                    storyId = storyId,
                    repository = repository,
                    bypassHttpCache = false,
                    readerService = readerService,
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
}
