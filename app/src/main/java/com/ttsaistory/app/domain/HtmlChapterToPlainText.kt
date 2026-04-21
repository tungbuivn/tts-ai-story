package com.ttsaistory.app.domain

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Chuyển (X)HTML chương EPUB sang văn bản thuần, **giữ nguyên Unicode** (dấu tiếng Việt).
 * Không dùng [android.text.Html] / HtmlCompat — parser đó thường làm hỏng chuỗi có dấu tổ hợp.
 */
fun htmlOrXhtmlToPlainText(raw: String): String {
    val noDecl = raw.replaceFirst(Regex("""<\?xml[^?]*\?>""", RegexOption.IGNORE_CASE), "").trim()
    val bodyInner =
        Regex("""(?is)<body[^>]*>(.*?)</body>""").find(noDecl)?.groupValues?.get(1)?.trim()
            ?: noDecl
    val wrapped =
        """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/></head><body>""" +
            bodyInner +
            """</body></html>"""
    val doc =
        runCatching {
            Jsoup.parse(wrapped, "", Parser.xmlParser())
        }.getOrElse {
            Jsoup.parse(wrapped, "", Parser.htmlParser())
        }
    val body = doc.body() ?: return ""
    return bodyToPlainTextPreservingUnicode(body)
}

private fun bodyToPlainTextPreservingUnicode(body: Element): String {
    val segments = mutableListOf<String>()
    for (child in body.children()) {
        val text =
            child.wholeText()
                .replace('\u00A0', ' ')
                .lines()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trim()
        if (text.isNotEmpty()) segments.add(text)
    }
    if (segments.isNotEmpty()) {
        return segments.joinToString("\n\n").trim()
    }
    return body.wholeText()
        .replace('\u00A0', ' ')
        .lines()
        .map { it.trimEnd() }
        .joinToString("\n")
        .trim()
}
