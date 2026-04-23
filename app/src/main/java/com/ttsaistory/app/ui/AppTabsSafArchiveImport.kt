package com.ttsaistory.app.ui

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.widget.Toast
import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.domain.extractZipContentToDirectory
import com.ttsaistory.app.domain.importEpubChaptersAsNumberedTxtFiles
import com.ttsaistory.app.domain.importPdfPagesAsNumberedTxtFiles
import com.ttsaistory.app.domain.safeCategoryNameFromEpubDisplayName
import com.ttsaistory.app.domain.safeCategoryNameFromPdfDisplayName
import com.ttsaistory.app.domain.safeCategoryNameFromZipDisplayName
import com.ttsaistory.app.ui.library.OpenFileProgressLogUi
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
            message = "Đang chuẩn bị và đọc ZIP…",
            progressCompleted = false,
        )
    }
    val importCategoryId =
        withContext(Dispatchers.IO) {
        val catName =
            safeCategoryNameFromZipDisplayName(
                resolvedDisplayName ?: "archive.zip",
            )
        val extractRoot = storyLibrary.prepareZipImportExtractDirectory(catName)
        val categoryId = storyLibrary.getOrCreateCategoryByName(catName)
        val extractRootCanon = extractRoot.canonicalFile
        val usedTitles = mutableSetOf<String>()
        val importedStoryCount = AtomicInteger(0)
        val extractedFiles = mutableListOf<Pair<String, File>>()
        coroutineScope {
            val importQueue = Channel<Pair<String, File>>(Channel.UNLIMITED)
            val importWorker =
                launch {
                    for ((label, file) in importQueue) {
                        val added =
                            storyLibrary.importSingleLocalFileAsStoryIfText(
                                categoryId,
                                extractRootCanon,
                                file,
                                usedTitles,
                            )
                        if (added) importedStoryCount.incrementAndGet()
                        logBridge.postUpdate { current ->
                            if (label.isEmpty()) {
                                current
                            } else {
                                current?.copy(
                                    message =
                                        if (added) {
                                            "Đã nhập: $label"
                                        } else {
                                            "Đã giải nén (bỏ qua / không phải văn): $label"
                                        },
                                )
                            }
                        }
                    }
                }
            try {
                extractZipContentToDirectory(
                    activity,
                    pickedUri,
                    extractRoot,
                    onFileExtracted = { _, entryName ->
                        val label = entryName.trim()
                        val file = File(extractRoot, entryName)
                        extractedFiles.add(label to file)
                    },
                )
                extractedFiles.sortBy { it.first }
                for (pair in extractedFiles) {
                    check(importQueue.trySend(pair).isSuccess) {
                        "Không đưa được file vào hàng đợi nhập"
                    }
                }
            } finally {
                importQueue.close()
            }
            importWorker.join()
        }
        logBridge.postUpdate { it?.copy(progressCompleted = true) }
        if (importedStoryCount.get() == 0) {
            error("Không có file nào có nội dung sau khi chuẩn hoá")
        }
        storyLibrary.setCategoryImportFolderTreeUri(
            categoryId,
            Uri.fromFile(extractRoot.canonicalFile).toString(),
        )
        categoryId
    }
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
            message = "Đang chuẩn bị và đọc EPUB…",
            progressCompleted = false,
        )
    }
    val (epubCategoryName, importedCount, epubCategoryId) =
        withContext(Dispatchers.IO) {
            val catName =
                safeCategoryNameFromEpubDisplayName(
                    resolvedDisplayName ?: "book.epub",
                )
            val extractRoot = storyLibrary.prepareZipImportExtractDirectory(catName)
            val categoryId = storyLibrary.getOrCreateCategoryByName(catName)
            val extractRootCanon = extractRoot.canonicalFile
            val usedTitles = mutableSetOf<String>()
            val importedStoryCount = AtomicInteger(0)
            coroutineScope {
                val importQueue = Channel<Pair<String, File>>(Channel.UNLIMITED)
                val importWorker =
                    launch {
                        for ((label, file) in importQueue) {
                            val isNumberedChapter =
                                file.name.length == 12 &&
                                    file.name.endsWith(".txt", ignoreCase = true) &&
                                    file.name.take(8).all { it.isDigit() }
                            val added =
                                storyLibrary.importSingleLocalFileAsStoryIfText(
                                    categoryId,
                                    extractRootCanon,
                                    file,
                                    usedTitles,
                                    storyTitleOverride =
                                        if (isNumberedChapter) {
                                            file.name.take(8)
                                        } else {
                                            null
                                        },
                                )
                            if (added) importedStoryCount.incrementAndGet()
                            logBridge.postUpdate { current ->
                                if (label.isEmpty()) {
                                    current
                                } else {
                                    current?.copy(
                                        message =
                                            if (added) {
                                                "Đã nhập: $label"
                                            } else {
                                                "Đã ghi (bỏ qua / không phải văn): $label"
                                            },
                                    )
                                }
                            }
                        }
                    }
                try {
                    importEpubChaptersAsNumberedTxtFiles(
                        activity,
                        pickedUri,
                        extractRoot,
                        onProgress = { pathInZip ->
                            logBridge.postUpdate { current ->
                                val short =
                                    pathInZip.substringAfterLast('/').ifBlank { pathInZip }
                                current?.copy(message = "Đang trích chương EPUB: $short")
                            }
                        },
                        onChapterFileWritten = { fileName, file ->
                            check(importQueue.trySend(fileName to file).isSuccess) {
                                "Không đưa được file vào hàng đợi nhập"
                            }
                        },
                    )
                } finally {
                    importQueue.close()
                }
                importWorker.join()
            }
            logBridge.postUpdate { it?.copy(progressCompleted = true) }
            if (importedStoryCount.get() == 0) {
                error("Không có file nào có nội dung sau khi chuẩn hoá")
            }
            storyLibrary.setCategoryImportFolderTreeUri(
                categoryId,
                Uri.fromFile(extractRoot.canonicalFile).toString(),
            )
            Triple(catName, importedStoryCount.get(), categoryId)
        }
    Toast.makeText(
        activity,
        "Đã import $importedCount chương EPUB vào thể loại \"$epubCategoryName\".",
        Toast.LENGTH_LONG,
    ).show()
    logBridge.postClear()
    onFinishedArchiveImport(epubCategoryId)
}

internal suspend fun importOpenedPdfArchiveFromSaf(
    activity: Activity,
    storyLibrary: StoryLibraryRepository,
    pickedUri: Uri,
    resolvedDisplayName: String?,
    logBridge: OpenFileProgressLogBridge,
    onFinishedArchiveImport: (categoryId: Long) -> Unit,
) {
    logBridge.postUpdate {
        it?.copy(
            message = "Đang chuẩn bị và đọc PDF…",
            progressCompleted = false,
        )
    }
    val (pdfCategoryName, importedCount, pdfCategoryId) =
        withContext(Dispatchers.IO) {
            val catName =
                safeCategoryNameFromPdfDisplayName(
                    resolvedDisplayName ?: "document.pdf",
                )
            val extractRoot = storyLibrary.prepareZipImportExtractDirectory(catName)
            val categoryId = storyLibrary.getOrCreateCategoryByName(catName)
            val extractRootCanon = extractRoot.canonicalFile
            val usedTitles = mutableSetOf<String>()
            val importedStoryCount = AtomicInteger(0)
            coroutineScope {
                val importQueue = Channel<Pair<String, File>>(Channel.UNLIMITED)
                val importWorker =
                    launch {
                        for ((label, file) in importQueue) {
                            val isNumberedChapter =
                                file.name.length == 12 &&
                                    file.name.endsWith(".txt", ignoreCase = true) &&
                                    file.name.take(8).all { it.isDigit() }
                            val added =
                                storyLibrary.importSingleLocalFileAsStoryIfText(
                                    categoryId,
                                    extractRootCanon,
                                    file,
                                    usedTitles,
                                    storyTitleOverride =
                                        if (isNumberedChapter) {
                                            file.name.take(8)
                                        } else {
                                            null
                                        },
                                )
                            if (added) importedStoryCount.incrementAndGet()
                            logBridge.postUpdate { current ->
                                if (label.isEmpty()) {
                                    current
                                } else {
                                    current?.copy(
                                        message =
                                            if (added) {
                                                "Đã nhập: $label"
                                            } else {
                                                "Đã ghi (bỏ qua / không phải văn): $label"
                                            },
                                    )
                                }
                            }
                        }
                    }
                try {
                    importPdfPagesAsNumberedTxtFiles(
                        activity,
                        pickedUri,
                        extractRoot,
                        onProgress = { msg ->
                            logBridge.postUpdate { current ->
                                current?.copy(message = "Đang trích PDF: $msg")
                            }
                        },
                        onPageFileWritten = { fileName, file ->
                            check(importQueue.trySend(fileName to file).isSuccess) {
                                "Không đưa được file vào hàng đợi nhập"
                            }
                        },
                    )
                } finally {
                    importQueue.close()
                }
                importWorker.join()
            }
            logBridge.postUpdate { it?.copy(progressCompleted = true) }
            if (importedStoryCount.get() == 0) {
                error("Không có trang nào có nội dung chữ sau khi đọc PDF.")
            }
            storyLibrary.setCategoryImportFolderTreeUri(
                categoryId,
                Uri.fromFile(extractRoot.canonicalFile).toString(),
            )
            Triple(catName, importedStoryCount.get(), categoryId)
        }
    Toast.makeText(
        activity,
        "Đã import $importedCount trang PDF vào thể loại \"$pdfCategoryName\".",
        Toast.LENGTH_LONG,
    ).show()
    logBridge.postClear()
    onFinishedArchiveImport(pdfCategoryId)
}
