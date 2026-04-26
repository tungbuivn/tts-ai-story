package com.ttsaistory.app.ui.library

import android.content.Context
import android.os.SystemClock
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.data.normalizeOnlineStoryPageUrlForMatch
import com.ttsaistory.app.ui.reader.ReaderService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Khi **chỉ xem** truyện web: nền tải / parse tối đa 10 chương kế tính từ truyện đang mở.
 *
 * Lặp: đọc DB → đảm bảo placeholder chương kế → lấy chuỗi tối đa 10 `id` tiếp theo; tìm chương đầu
 * tiên còn cần parse; headless sync (có retry + khoảng trễ giữa các lần mở WebView); gọi
 * [onLibraryDataChanged] rồi **truy vấn lại** chuỗi từ cùng anchor cho đến khi 10 chương đều đã parse
 * hoặc không còn bước kế / lỗi.
 */
object OnlineWebStoryViewAheadPreload {
    private const val MAX_NEXT_STORIES = 10
    private const val MIN_MS_BETWEEN_WEB_FETCHES = 5_000L
    private const val MAX_ATTEMPTS_PER_WEB_FETCH = 5
    private const val RETRY_DELAY_MS = 5_000L
    /** Tránh vòng lặp vô hạn nếu DB / chuỗi thay đổi bất thường. */
    private const val MAX_ROUNDS = 60

    suspend fun preloadNextTenWhileViewing(
        context: Context,
        anchorLibraryStoryId: Long,
        repository: StoryLibraryRepository,
        readerService: ReaderService? = null,
        onLibraryDataChanged: () -> Unit,
        onQueueTargetStoryId: (Long?) -> Unit = {},
    ) {
        val app = context.applicationContext
        var lastWebFetchElapsedMs = 0L
        try {
            var round = 0
            while (round < MAX_ROUNDS && coroutineContext.isActive) {
                round++
                val chainIds =
                    withContext(Dispatchers.IO) {
                        buildSuccessorChainIds(
                            repository = repository,
                            anchorStoryId = anchorLibraryStoryId,
                            maxDepth = MAX_NEXT_STORIES,
                        )
                    }
                if (chainIds.isEmpty()) break

                val targetId =
                    withContext(Dispatchers.IO) {
                        chainIds.firstOrNull { sid ->
                            val r = repository.getStory(sid) ?: return@firstOrNull false
                            repository.storyNeedsOnlineContentRefresh(r)
                        }
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

    /**
     * Trả về tối đa [maxDepth] id truyện **kế** (không gồm anchor): mỗi bước
     * [StoryLibraryRepository.ensurePlaceholderStoryForStoredOnlineNextPageUrl] rồi theo
     * `online_next_page_url` → truyện có `online_page_url` khớp.
     */
    private fun buildSuccessorChainIds(
        repository: StoryLibraryRepository,
        anchorStoryId: Long,
        maxDepth: Int,
    ): List<Long> {
        val ids = ArrayList<Long>(maxDepth.coerceAtMost(MAX_NEXT_STORIES))
        var parentId = anchorStoryId
        repeat(maxDepth.coerceAtMost(MAX_NEXT_STORIES)) {
            repository.ensurePlaceholderStoryForStoredOnlineNextPageUrl(parentId)
            val row = repository.getStory(parentId) ?: return ids
            val nextUrl =
                row.onlineNextPageUrl?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return ids
            val self = row.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (self != null &&
                normalizeOnlineStoryPageUrlForMatch(self) ==
                    normalizeOnlineStoryPageUrlForMatch(nextUrl)
            ) {
                return ids
            }
            val next =
                repository.findStoryInCategoryByOnlinePageUrl(row.categoryId, nextUrl)
                    ?: return ids
            if (next.id == parentId) return ids
            ids.add(next.id)
            parentId = next.id
        }
        return ids
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
