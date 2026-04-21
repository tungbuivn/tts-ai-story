package com.ttsaistory.app.domain

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubImportHtmlToTextTest {

    @Test
    fun chapter0_preservesVietnameseDiacritics() {
        val raw =
            javaClass.getResourceAsStream("/chapter-0.xhtml")!!.use { ins ->
                ins.readBytes().toString(StandardCharsets.UTF_8)
            }
        val plain = htmlOrXhtmlToPlainText(raw)
        assertTrue("title line", plain.contains("Chương 1: Hắn tên là Bạch Tiểu Thuần"))
        assertTrue("body", plain.contains("Mạo Nhi Sơn"))
        assertTrue("decomposed Vietnamese line from sample", plain.contains("Giờ phút này"))
        assertTrue("another tonal line", plain.contains("đã thấy") || plain.contains("đã thấy"))
    }
}
