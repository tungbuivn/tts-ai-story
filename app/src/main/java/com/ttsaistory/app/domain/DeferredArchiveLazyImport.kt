package com.ttsaistory.app.domain

import com.ttsaistory.app.data.LibraryStoryRow

/** Tiêu đề dạng 8 chữ số (chỉ số chương deferred) → chỉ số 1-based; ngược lại null. */
fun parseEightDigitDeferredArchiveStoryIndex1(title: String): Int? {
    val t = title.trim()
    if (t.length != 8 || !t.all { it.isDigit() }) return null
    return t.toIntOrNull()?.takeIf { it > 0 }
}

/** Khóa nguồn deferred (pdf/zip/epub) để bảng «đã xử lý» — trùng với [deferredArchiveSourceKey] trong AppTabs. */
fun deferredArchiveSourceKeyFromLazyOnlineUrl(url: String?): String? {
    parseDeferredPdfPageOnlineUrl(url)?.let { s ->
        return "pdf|${s.sourcePdfPath.trim()}|${s.totalPages}"
    }
    parseDeferredZipEntryOnlineUrl(url)?.let { s ->
        return "zip|${s.sourceZipPath.trim()}|${s.totalEntries}"
    }
    parseDeferredEpubChapterOnlineUrl(url)?.let { s ->
        return "epub|${s.sourceEpubPath.trim()}|${s.totalChapters}"
    }
    return null
}

/** Chỉ số trang / mục ZIP / chương EPUB (1-based) nếu [url] là lazy URL; ngược lại null. */
fun deferredArchiveLazyItemIndex1(url: String?): Int? =
    parseDeferredPdfPageOnlineUrl(url)?.pageIndex1
        ?: parseDeferredZipEntryOnlineUrl(url)?.entryIndex1
        ?: parseDeferredEpubChapterOnlineUrl(url)?.chapterIndex1

fun isDeferredArchiveLazyOnlineUrl(url: String?): Boolean =
    deferredArchiveLazyItemIndex1(url) != null

/**
 * Chỉ số lớn nhất đã coi là «đã nạp xong» từ bản ghi thư viện: tiêu đề 8 chữ số trong 1..[totalItems]
 * và [LibraryStoryRow.onlineContentParseOk].
 */
fun deferredImportMaxParsedIndex1FromRows(
    rows: List<LibraryStoryRow>,
    totalItems: Int,
): Int =
    rows
        .mapNotNull { r ->
            parseEightDigitDeferredArchiveStoryIndex1(r.title)
                ?.takeIf { idx -> idx in 1..totalItems && r.onlineContentParseOk }
        }
        .maxOrNull()
        ?: 0

/**
 * Biên nạp «chỉ tiến»: mục tiếp theo cần xử lý = max(chỉ số lớn nhất đã parse ok trên bản ghi,
 * [persistedMaxProcessedIndex1] từ bảng đánh dấu đã xử lý) + 1.
 * [persistedMaxProcessedIndex1] giữ tiến độ khi người dùng xóa chương đã nạp — không lùi biên.
 */
fun deferredImportFrontierIndex1(
    rows: List<LibraryStoryRow>,
    totalItems: Int,
    persistedMaxProcessedIndex1: Int = 0,
): Int {
    val maxFromRows = deferredImportMaxParsedIndex1FromRows(rows, totalItems)
    val persisted = persistedMaxProcessedIndex1.coerceIn(0, totalItems)
    return maxOf(maxFromRows, persisted) + 1
}
