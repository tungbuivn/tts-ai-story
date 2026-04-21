package com.ttsaistory.app.domain

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Dòng đầu (sau trim) của nội dung truyện — dùng làm nhãn mục lục EPUB. */
fun firstLineForEpubNavigationLabel(storyBody: String): String {
    val t = storyBody.trimStart('\uFEFF')
    val first = t.lineSequence().firstOrNull()?.trim().orEmpty()
    return first.take(500).ifBlank { "Chương" }
}

fun escapeXmlText(s: String): String =
    buildString(s.length + 16) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

private fun putMimetypeEntry(zos: ZipOutputStream) {
    val raw = "application/epub+zip".toByteArray(StandardCharsets.US_ASCII)
    val entry = ZipEntry("mimetype")
    entry.method = ZipEntry.STORED
    entry.size = raw.size.toLong()
    entry.crc = CRC32().apply { update(raw) }.value
    zos.putNextEntry(entry)
    zos.write(raw)
    zos.closeEntry()
}

private fun putDeflatedUtf8(zos: ZipOutputStream, zipPath: String, utf8Text: String) {
    val bytes = utf8Text.toByteArray(StandardCharsets.UTF_8)
    zos.putNextEntry(ZipEntry(zipPath))
    zos.write(bytes)
    zos.closeEntry()
}

/**
 * Tạo gói EPUB 3 tối thiểu (ZIP): `nav.xhtml` là mục lục, mỗi chương một XHTML (`<pre>` nội dung thuần).
 * [chapters]: cặp (nhãn mục lục, nội dung thuần UTF-8).
 */
fun buildEpub3ZipBytes(
    bookTitle: String,
    chapters: List<Pair<String /* nav label */, String /* plain body */>>,
): ByteArray {
    require(chapters.isNotEmpty())
    val titleSafe = bookTitle.trim().ifBlank { "Sách" }
    val uuid = UUID.randomUUID().toString()
    val modified = Instant.now().toString()

    val chapterHrefs =
        chapters.indices.map { i ->
            String.format(Locale.US, "chapter%04d.xhtml", i + 1)
        }

    val navOl =
        chapters.zip(chapterHrefs).joinToString("\n") { (pair, href) ->
            val (navLabel, _) = pair
            """    <li><a href="${escapeXmlText(href)}">${escapeXmlText(navLabel)}</a></li>"""
        }

    val navXhtml =
        """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="vi">
<head><title>Mục lục</title></head>
<body>
<nav epub:type="toc" id="toc">
  <ol>
$navOl
  </ol>
</nav>
</body>
</html>
""".trimIndent()

    val manifestItems =
        buildString {
            append(
                """    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""",
            )
            chapterHrefs.forEachIndexed { i, href ->
                append('\n')
                append(
                    """    <item id="ch${String.format(Locale.US, "%04d", i + 1)}" href="$href" media-type="application/xhtml+xml"/>""",
                )
            }
        }

    val spineItems =
        chapterHrefs.indices.joinToString("\n") { i ->
            """    <itemref idref="ch${String.format(Locale.US, "%04d", i + 1)}"/>"""
        }

    val contentOpf =
        """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="3.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:$uuid</dc:identifier>
    <dc:title>${escapeXmlText(titleSafe)}</dc:title>
    <dc:language>vi</dc:language>
    <meta property="dcterms:modified">${escapeXmlText(modified)}</meta>
  </metadata>
  <manifest>
$manifestItems
  </manifest>
  <spine>
$spineItems
  </spine>
</package>
""".trimIndent()

    val containerXml =
        """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
""".trimIndent()

    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
        putMimetypeEntry(zos)
        putDeflatedUtf8(zos, "META-INF/container.xml", containerXml)
        putDeflatedUtf8(zos, "OEBPS/content.opf", contentOpf)
        putDeflatedUtf8(zos, "OEBPS/nav.xhtml", navXhtml)
        chapters.forEachIndexed { index, (navLabel, body) ->
            val href = chapterHrefs[index]
            val chapterXhtml =
                """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="vi">
<head><title>${escapeXmlText(navLabel)}</title></head>
<body><pre>${escapeXmlText(body)}</pre></body>
</html>
""".trimIndent()
            putDeflatedUtf8(zos, "OEBPS/$href", chapterXhtml)
        }
    }
    return baos.toByteArray()
}
