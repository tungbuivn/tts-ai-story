package com.ttsaistory.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * URL trang để cấu hình / đồng bộ nội dung online (truyện đầu, base URL, hoặc tên dạng URL).
 */
suspend fun StoryLibraryRepository.resolveOnlineCategoryWebUrl(
    cat: LibraryCategoryRow,
): String? =
    withContext(Dispatchers.IO) {
        val fromStories =
            listStories(cat.id).firstOrNull()?.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() }
        (fromStories
            ?: cat.onlineBaseUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: run {
                val n = cat.name.trim()
                if (looksLikeWebCategoryUrl(n)) normalizeWebCategoryUrl(n) else null
            })
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
