package com.ttsaistory.app.ui

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.widget.Toast
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.domain.copyPdfUriToLocalFile
import com.ttsaistory.app.domain.deferredEpubChapterOnlineUrl
import com.ttsaistory.app.domain.deferredPdfPageOnlineUrl
import com.ttsaistory.app.domain.deferredZipEntryOnlineUrl
import com.ttsaistory.app.domain.listZipTextEntryNames
import com.ttsaistory.app.domain.readEpubChapterCount
import com.ttsaistory.app.domain.readEpubChapterPlainText
import com.ttsaistory.app.ui.reader.ReaderService
import com.ttsaistory.app.domain.readZipTextEntryByIndex
import com.ttsaistory.app.domain.safeCategoryNameFromEpubDisplayName
import com.ttsaistory.app.domain.safeCategoryNameFromPdfDisplayName
import com.ttsaistory.app.domain.safeCategoryNameFromZipDisplayName
import com.ttsaistory.app.ui.library.OpenFileProgressLogUi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cầu cập nhật [OpenFileProgressLogUi] từ luồng IO qua [Handler] — giữ nguyên hành vi cũ trong [AppTabs].
 */
internal class OpenFileProgressLogBridge(
    private val mainHandler: Handler,
    private val getLog: () -> OpenFileProgressLogUi?,
    private val setLog: (OpenFileProgressLogUi?) -> Unit,
) {
    fun postUpdate(transform: (OpenFileProgressLogUi?) -> OpenFileProgressLogUi?) {
        mainHandler.post { setLog(transform(getLog())) }
    }

    fun postClear() {
        mainHandler.post { setLog(null) }
    }
}

internal suspend fun importOpenedZipArchiveFromSaf(
    activity: Activity,
    storyLibrary: StoryLibraryRepository,
    pickedUri: Uri,
    resolvedDisplayName: String?,
    logBridge: OpenFileProgressLogBridge,
    /** [categoryId] của thể loại vừa nhập — để mở chương đầu trong thể loại. */
    onFinishedArchiveImport: (categoryId: Long) -> Unit,
) {
    logBridge.postUpdate {
        it?.copy(
            message = "Đang chuẩn bị và nhập ZIP (mục đầu)…",
            progressCompleted = false,
        )
    }
    val (zipCategoryName, zipPreloadedCount, importCategoryId) =
        withContext(Dispatchers.IO) {
            val catName =
                safeCategoryNameFromZipDisplayName(
                    resolvedDisplayName ?: "archive.zip",
                )
            val extractRoot = storyLibrary.prepareZipImportExtractDirectory(catName)
            val categoryId = storyLibrary.getOrCreateCategoryByName(catName)
            val sourceZip = File(extractRoot, "_source.zip")
            logBridge.postUpdate { it?.copy(message = "Đang sao chép nguồn ZIP…") }
            copyPdfUriToLocalFile(activity, pickedUri, sourceZip)
            val names = listZipTextEntryNames(sourceZip)
            if (names.isEmpty()) error("ZIP không có file văn bản phù hợp.")
            val preloadCount = minOf(1, names.size)
            for (i in 1..preloadCount) {
                logBridge.postUpdate { it?.copy(message = "Đang đọc mục $i / $preloadCount…") }
                val body = readZipTextEntryByIndex(sourceZip, i)
                storyLibrary.insertStory(
                    categoryId = categoryId,
                    title = String.format("%08d", i),
                    body = body,
                )
            }
            if (preloadCount < names.size) {
                val next = preloadCount + 1
                storyLibrary.insertStory(
                    categoryId = categoryId,
                    title = String.format("%08d", next),
                    body = "",
                    onlinePageUrl =
                        deferredZipEntryOnlineUrl(
                            sourceZipPath = sourceZip.absolutePath,
                            entryIndex1 = next,
                            totalEntries = names.size,
                        ),
                )
            }
            logBridge.postUpdate { it?.copy(progressCompleted = true) }
            storyLibrary.setCategoryImportFolderTreeUri(
                categoryId,
                Uri.fromFile(extractRoot.canonicalFile).toString(),
            )
            Triple(catName, preloadCount, categoryId)
        }
    Toast.makeText(
        activity,
        "Đã import trước $zipPreloadedCount mục ZIP vào truyện «$zipCategoryName». Các mục sau sẽ nạp dần khi đọc.",
        Toast.LENGTH_LONG,
    ).show()
    logBridge.postClear()
    onFinishedArchiveImport(importCategoryId)
}

internal suspend fun importOpenedEpubArchiveFromSaf(
    activity: Activity,
    storyLibrary: StoryLibraryRepository,
    pickedUri: Uri,
    resolvedDisplayName: String?,
    logBridge: OpenFileProgressLogBridge,
    onFinishedArchiveImport: (categoryId: Long) -> Unit,
) {
    logBridge.postUpdate {
        it?.copy(
            message = "Đang chuẩn bị và nhập EPUB (chương đầu)…",
            progressCompleted = false,
        )
    }
    val (epubCategoryName, epubPreloadedCount, epubCategoryId) =
        withContext(Dispatchers.IO) {
            val catName =
                safeCategoryNameFromEpubDisplayName(
                    resolvedDisplayName ?: "book.epub",
                )
            val extractRoot = storyLibrary.prepareZipImportExtractDirectory(catName)
            val categoryId = storyLibrary.getOrCreateCategoryByName(catName)
            val sourceEpub = File(extractRoot, "_source.epub")
            logBridge.postUpdate { it?.copy(message = "Đang sao chép nguồn EPUB…") }
            copyPdfUriToLocalFile(activity, pickedUri, sourceEpub)
            val totalChapters = readEpubChapterCount(sourceEpub)
            val preloadCount = minOf(1, totalChapters)
            for (i in 1..preloadCount) {
                logBridge.postUpdate { it?.copy(message = "Đang đọc chương $i / $preloadCount…") }
                val body = readEpubChapterPlainText(sourceEpub, i)
                storyLibrary.insertStory(
                    categoryId = categoryId,
                    title = String.format("%08d", i),
                    body = body,
                )
            }
            if (preloadCount < totalChapters) {
                val next = preloadCount + 1
                storyLibrary.insertStory(
                    categoryId = categoryId,
                    title = String.format("%08d", next),
                    body = "",
                    onlinePageUrl =
                        deferredEpubChapterOnlineUrl(
                            sourceEpubPath = sourceEpub.absolutePath,
                            chapterIndex1 = next,
                            totalChapters = totalChapters,
                        ),
                )
            }
            logBridge.postUpdate { it?.copy(progressCompleted = true) }
            storyLibrary.setCategoryImportFolderTreeUri(
                categoryId,
                Uri.fromFile(extractRoot.canonicalFile).toString(),
            )
            Triple(catName, preloadCount, categoryId)
        }
    Toast.makeText(
        activity,
        "Đã import trước $epubPreloadedCount chương EPUB vào truyện «$epubCategoryName». Các chương sau sẽ nạp dần khi đọc.",
        Toast.LENGTH_LONG,
    ).show()
    logBridge.postClear()
    onFinishedArchiveImport(epubCategoryId)
}

internal suspend fun importOpenedPdfArchiveFromSaf(
    activity: Activity,
    storyLibrary: StoryLibraryRepository,
    readerService: ReaderService,
    pickedUri: Uri,
    resolvedDisplayName: String?,
    logBridge: OpenFileProgressLogBridge,
    onFinishedArchiveImport: (categoryId: Long) -> Unit,
) {
    logBridge.postUpdate {
        it?.copy(
            message = "Đang chuẩn bị và nhập PDF (trang đầu)…",
            progressCompleted = false,
        )
    }
    val (pdfCategoryName, preloadedPages, pdfCategoryId) =
        withContext(Dispatchers.IO) {
            val catName =
                safeCategoryNameFromPdfDisplayName(
                    resolvedDisplayName ?: "document.pdf",
                )
            val extractRoot = storyLibrary.prepareZipImportExtractDirectory(catName)
            val categoryId = storyLibrary.getOrCreateCategoryByName(catName)
            val sourcePdf = File(extractRoot, "_source.pdf")
            logBridge.postUpdate { it?.copy(message = "Đang sao chép nguồn PDF…") }
            copyPdfUriToLocalFile(activity, pickedUri, sourcePdf)
            val totalPages = readerService.readPdfTotalPages(sourcePdf)
            val preloadPages = minOf(1, totalPages)
            for (p in 1..preloadPages) {
                logBridge.postUpdate { it?.copy(message = "Đang trích trang $p / $preloadPages…") }
                val pageBody = readerService.readPdfSinglePageText(sourcePdf, p).trim()
                storyLibrary.insertStory(
                    categoryId = categoryId,
                    title = String.format("%08d", p),
                    body = pageBody,
                )
            }
            if (preloadPages < totalPages) {
                val nextPage = preloadPages + 1
                storyLibrary.insertStory(
                    categoryId = categoryId,
                    title = String.format("%08d", nextPage),
                    body = "",
                    onlinePageUrl =
                        deferredPdfPageOnlineUrl(
                            sourcePdfPath = sourcePdf.absolutePath,
                            pageIndex1 = nextPage,
                            totalPages = totalPages,
                        ),
                )
            }
            logBridge.postUpdate { it?.copy(progressCompleted = true) }
            storyLibrary.setCategoryImportFolderTreeUri(
                categoryId,
                Uri.fromFile(extractRoot.canonicalFile).toString(),
            )
            Triple(catName, preloadPages, categoryId)
        }
    Toast.makeText(
        activity,
        "Đã import trước $preloadedPages trang PDF vào truyện «$pdfCategoryName». Các trang sau sẽ nạp dần khi đọc.",
        Toast.LENGTH_LONG,
    ).show()
    logBridge.postClear()
    onFinishedArchiveImport(pdfCategoryId)
}
