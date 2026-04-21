package com.ttsaistory.app.ui.library

/** Cách xuất thể loại ra Downloads — chỉ chọn một. */
internal enum class LibraryCategoryExportFormat {
    /** Thư mục + `00000001.txt` … */
    SeparateFilesInFolder,
    /** Một file `.txt` ghép toàn bộ truyện. */
    MergedSingleTxt,
    /** Một file `.zip` (bên trong: các `.txt` đánh số như thư mục). */
    SingleZip,
    /** Một file `.epub` — mục lục lấy dòng đầu tiên của mỗi truyện. */
    SingleEpub,
}
