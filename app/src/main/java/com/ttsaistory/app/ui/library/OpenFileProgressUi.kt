package com.ttsaistory.app.ui.library

/**
 * Tiến trình chung cho **mọi** thao tác mở / nhập tệp (SAF): EPUB, ZIP, văn bản, import thư mục,
 * xuất Downloads — một dialog duy nhất ([OpenFileProgressDialog]).
 */
data class OpenFileProgressUi(
    val completed: Int,
    val total: Int,
    val label: String,
    val dialogTitle: String = "Đang mở tệp",
)
