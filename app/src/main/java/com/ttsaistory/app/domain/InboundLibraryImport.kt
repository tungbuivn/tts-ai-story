package com.ttsaistory.app.domain

import com.ttsaistory.app.data.StoryLibraryRepository
import com.ttsaistory.app.model.InboundLibraryPersistResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chuẩn hoá + tạo truyện mới.
 * Nếu [activeLibraryStoryId] trỏ tới truyện còn tồn tại → lưu vào **cùng thể loại** với truyện đó
 * ([StoryLibraryRepository.importSharedTextIntoCategory], tiêu đề `Chia sẻ N`).
 * Ngược lại → thể loại [StoryLibraryRepository.INBOUND_UNTITLED_CATEGORY_NAME] (`không tên N`).
 */
suspend fun persistInboundSharedTextToLibrary(
    raw: String,
    repo: StoryLibraryRepository,
    activeLibraryStoryId: Long? = null,
    /** URI chuỗi của bản sao trong Download/tts-ai-story (sau [copyPickedDocumentToDownloadsTtsAiStoryFolder]) — xóa khi xóa chương. */
    downloadsStagingSourceUri: String? = null,
): InboundLibraryPersistResult {
    val cleanedText =
        withContext(Dispatchers.Default) {
            canonicalTextFromRaw(raw)
        }
    val (storyId, savedTitle) =
        withContext(Dispatchers.IO) {
            val categoryId =
                activeLibraryStoryId?.let { sid -> repo.getStory(sid)?.categoryId }
            if (categoryId != null) {
                repo.importSharedTextIntoCategory(
                    categoryId,
                    cleanedText,
                    importSourceUri = downloadsStagingSourceUri,
                )
            } else {
                repo.importInboundTextAsUntitledStory(
                    cleanedText,
                    importSourceUri = downloadsStagingSourceUri,
                )
            }
        }
    return InboundLibraryPersistResult(cleanedText, storyId, savedTitle)
}

/**
 * Nhập nội dung từ file đã chọn (SAF): tạo hoặc dùng thể loại trùng tên [fileStemForLibraryCategory]
 * của [displayName], lưu một truyện mới trong thể loại đó.
 */
suspend fun persistOpenedTextFileToLibrary(
    raw: String,
    repo: StoryLibraryRepository,
    displayName: String?,
    /** URI chuỗi của bản sao trong Download/tts-ai-story — xóa khi xóa chương. */
    downloadsStagingSourceUri: String? = null,
): InboundLibraryPersistResult {
    val cleanedText =
        withContext(Dispatchers.Default) {
            canonicalTextFromRaw(raw)
        }
    val rawDisplay = displayName?.trim().orEmpty()
    val categoryName = fileStemForLibraryCategory(rawDisplay)
    val storyTitle =
        rawDisplay.ifEmpty {
            categoryName
        }
    val (storyId, savedTitle) =
        withContext(Dispatchers.IO) {
            val catId = repo.getOrCreateCategoryByName(categoryName)
            val title = storyTitle.ifBlank { categoryName }
            val id = repo.insertStory(catId, title, cleanedText, importSourceUri = downloadsStagingSourceUri)
            id to title
        }
    return InboundLibraryPersistResult(cleanedText, storyId, savedTitle)
}
