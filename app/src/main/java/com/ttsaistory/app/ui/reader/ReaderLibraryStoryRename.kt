package com.ttsaistory.app.ui.reader

import android.content.Context
import android.widget.Toast
import com.ttsaistory.app.data.LibraryStoryRow
import com.ttsaistory.app.data.StoryLibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun launchReaderRenameStoryInMoveCategory(
    scope: CoroutineScope,
    context: Context,
    storyId: Long,
    titleDraft: String,
    libraryRepository: StoryLibraryRepository,
    onRenamed: (LibraryStoryRow) -> Unit,
    onLibraryDataChanged: () -> Unit,
) {
    scope.launch {
        try {
            val updated =
                withContext(Dispatchers.IO) {
                    val trimmed = titleDraft.trim().ifEmpty { "Không tiêu đề" }
                    libraryRepository.renameStory(storyId, trimmed)
                    libraryRepository.getStory(storyId)
                }
            if (updated != null) {
                onRenamed(updated)
                onLibraryDataChanged()
                Toast.makeText(context, "Đã đổi tên", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Lỗi", Toast.LENGTH_LONG).show()
        }
    }
}
