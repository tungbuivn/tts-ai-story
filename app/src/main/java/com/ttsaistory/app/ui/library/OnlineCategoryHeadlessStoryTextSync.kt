package com.ttsaistory.app.ui.library

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.domain.canonicalTextFromRaw
import com.ttsaistory.app.domain.ParagraphTextService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OnlineCategoryHeadlessStoryTextSync {
    suspend fun syncFirstStoryFromCategoryPage(
        context: Context,
        categoryId: Long,
        pageUrl: String,
        repository: StoryLibraryRepository,
    ) {
        val firstStoryId =
            withContext(Dispatchers.IO) {
                repository.listStories(categoryId).firstOrNull()?.id
            } ?: error("Chưa có chương trong truyện")
        syncOnlineStoryContentFromWebView(
            context = context,
            storyId = firstStoryId,
            categoryId = categoryId,
            pageUrl = pageUrl,
            repository = repository,
        )
    }

    suspend fun syncOnlineStoryFromWebPage(
        context: Context,
        storyId: Long,
        repository: StoryLibraryRepository,
        bypassHttpCache: Boolean = false,
    ) {
        val row =
            withContext(Dispatchers.IO) {
                repository.getStory(storyId)
            } ?: error("Không tìm thấy chương")
        val url =
            row.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("Truyện không có online_page_url")
        syncOnlineStoryContentFromWebView(
            context = context,
            storyId = storyId,
            categoryId = row.categoryId,
            pageUrl = url,
            repository = repository,
            bypassHttpCache = bypassHttpCache,
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun syncOnlineStoryContentFromWebView(
        context: Context,
        storyId: Long,
        categoryId: Long,
        pageUrl: String,
        repository: StoryLibraryRepository,
        bypassHttpCache: Boolean = false,
    ) {
        val selectors =
            withContext(Dispatchers.IO) {
                repository
                    .getOnlineContentSelectorsForCategory(categoryId)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        if (selectors.isEmpty()) {
            error("Chưa có selector nội dung")
        }
        val multiline = selectors.joinToString("\n")

        withContext(Dispatchers.Main) {
            val wv = WebView(context)
            try {
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.javaScriptCanOpenWindowsAutomatically = true
                wv.settings.cacheMode =
                    if (bypassHttpCache) {
                        WebSettings.LOAD_NO_CACHE
                    } else {
                        WebSettings.LOAD_DEFAULT
                    }
                wv.settings.userAgentString =
                    wv.settings.userAgentString + " TtsAiStoryWebCategory/1"
                wv.webChromeClient = WebChromeClient()

                withTimeout<Unit>(timeMillis = 60_000L) {
                    suspendCancellableCoroutine { cont ->
                        val done = AtomicBoolean(false)
                        fun finishOk() {
                            if (done.compareAndSet(false, true)) {
                                cont.resume(Unit)
                            }
                        }
                        fun finishErr(t: Throwable) {
                            if (done.compareAndSet(false, true)) {
                                cont.resumeWithException(t)
                            }
                        }
                        wv.webViewClient =
                            object : WebViewClient() {
                                override fun onPageFinished(
                                    view: WebView?,
                                    url: String?,
                                ) {
                                    finishOk()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val main = request?.isForMainFrame == true
                                        if (main) {
                                            val msg =
                                                error?.description?.toString()
                                                    ?: "Lỗi tải trang"
                                            finishErr(IllegalStateException(msg))
                                        }
                                    }
                                }

                                @Suppress("DEPRECATION")
                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?,
                                ) {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                                        finishErr(
                                            IllegalStateException(
                                                description ?: "Lỗi tải trang ($errorCode)",
                                            ),
                                        )
                                    }
                                }
                            }
                        wv.loadUrl(pageUrl)
                        cont.invokeOnCancellation { wv.stopLoading() }
                    }
                }
                delay(400)
                val raw = extractPlainTextFromWebViewForSelectors(wv, multiline)
                val baseForNext = wv.url?.trim()?.takeIf { it.isNotEmpty() } ?: pageUrl
                val nextSel =
                    withContext(Dispatchers.IO) {
                        repository.getOnlineNextPageSelectorForCategory(categoryId)
                    }
                val nextUrl =
                    resolveNextPageAbsoluteUrlFromWebView(
                        wv,
                        nextSel,
                        baseForNext,
                    )
                withContext(Dispatchers.IO) {

                    val flat =    ParagraphTextService.parseStoredTextToSentences(raw)
                    val canonical = flat.joinToString("\n")
                    // cập nhật canonical vào nội dung truyện , không gọi setChapterText để tránh đè vào chapter hiện tại
                    repository.updateStoryText(storyId, canonical)
                 
                    repository.markOnlineStoryContentParseSuccess(storyId, nextUrl)
                    repository.ensureOnlineNextChapterStoryRow(
                        currentStoryId = storyId,
                        nextPageOverride = nextUrl?.trim()?.takeIf { it.isNotEmpty() },
                    )
                }
            } finally {
                wv.stopLoading()
                wv.destroy()
            }
        }
    }
}
