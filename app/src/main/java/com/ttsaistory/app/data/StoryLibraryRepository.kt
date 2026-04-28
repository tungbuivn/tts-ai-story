package com.ttsaistory.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.ttsaistory.app.domain.buildEpub3ZipBytes
import com.ttsaistory.app.domain.canonicalTextFromRaw
import com.ttsaistory.app.domain.deferredArchiveSourceKeyFromLazyOnlineUrl
import com.ttsaistory.app.domain.parseDeferredEpubChapterOnlineUrl
import com.ttsaistory.app.domain.parseDeferredPdfPageOnlineUrl
import com.ttsaistory.app.domain.parseDeferredZipEntryOnlineUrl
import com.ttsaistory.app.domain.isDeferredArchiveLazyOnlineUrl
import com.ttsaistory.app.domain.mergeParagraphs
import com.ttsaistory.app.domain.parseEightDigitDeferredArchiveStoryIndex1
import com.ttsaistory.app.domain.ParagraphTextService
import com.ttsaistory.app.domain.splitIntoParagraphs
import com.ttsaistory.app.domain.firstLineForEpubNavigationLabel
import com.ttsaistory.app.domain.listImportFolderFilesSorted
import com.ttsaistory.app.domain.readUtf8FromImportTreeEntry
import com.ttsaistory.app.domain.readMergedUtf8FromDocumentTree
import com.ttsaistory.app.domain.readUtf8FromDocumentUri
import com.ttsaistory.app.domain.deleteTtsAiStoryDownloadsStagingCopy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.bufferedReader
import com.ttsaistory.app.model.AppPreferenceKeys
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LibraryCategoryRow(
    val id: Long,
    val name: String,
    val storyCount: Int,
    /** Đã gắn thư mục SAF qua «Import thư mục» — mới đồng bộ lại được. */
    val hasImportFolder: Boolean,
    /** Đường dẫn file ảnh đại diện (JPEG đã resize) trong thư mục thể loại; null nếu chưa chọn. */
    val coverImagePath: String? = null,
    /** Thể loại nguồn web (URL + selector). */
    val isOnline: Boolean = false,
    val onlineBaseUrl: String? = null,
    /** Một hoặc nhiều CSS selector vùng nội dung (lưu JSON mảng trong DB). */
    val onlineContentSelectors: List<String> = emptyList(),
    val onlineNextPageSelector: String? = null,
)

/** Parser mặc định theo domain (bảng `online_domain_parsers`). */
data class OnlineDomainParserRow(
    val id: Long,
    val domain: String,
    val onlineNextPageSelector: String?,
    val contentSelectors: List<String>,
)

data class LibraryStoryRow(
    val id: Long,
    val categoryId: Long,
    val title: String,
    val filePath: String,
    val lastSpeechSentenceIndex: Int,
    val sortOrder: Int,
    /** URI cây SAF (persist) nếu truyện được import từ thư mục — dùng để đồng bộ lại. */
    val importSourceUri: String? = null,
    /** URL trang web gắn với truyện (nguồn online / WebView / cấu hình). */
    val onlinePageUrl: String? = null,
    /**
     * Đã trích nội dung từ web thành công (selector + lưu file).
     * false + [onlinePageUrl] khác rỗng → mở truyện sẽ thử parse lại.
     */
    val onlineContentParseOk: Boolean = true,
    /** URL trang kế (href tuyệt đối) sau lần parse thành công; null nếu không lấy được. */
    val onlineNextPageUrl: String? = null,
)

private class StoryLibraryOpenHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                created_at INTEGER NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                import_folder_tree_uri TEXT,
                cover_image_path TEXT,
                is_online INTEGER NOT NULL DEFAULT 0,
                online_base_url TEXT,
                online_content_selector TEXT,
                online_next_page_selector TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE saved_stories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                file_path TEXT NOT NULL UNIQUE,
                last_speech_sentence_index INTEGER NOT NULL DEFAULT -1,
                sort_order INTEGER NOT NULL DEFAULT 0,
                import_source_uri TEXT,
                online_page_url TEXT,
                online_content_parse_ok INTEGER NOT NULL DEFAULT 1,
                online_next_page_url TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        val now = System.currentTimeMillis()
        val seed =
            ContentValues().apply {
                put("name", DEFAULT_CATEGORY_NAME)
                put("created_at", now)
                put("sort_order", 0)
            }
        db.insert("categories", null, seed)
        db.execSQL(
            """
            CREATE TABLE online_domain_parsers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                domain TEXT NOT NULL UNIQUE COLLATE NOCASE,
                online_next_page_selector TEXT,
                online_content_selector TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE deferred_archive_processed_items (
                category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
                source_key TEXT NOT NULL,
                item_index1 INTEGER NOT NULL,
                processed_at INTEGER NOT NULL,
                PRIMARY KEY (category_id, source_key, item_index1)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE saved_stories ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0",
            )
            val catIds = mutableListOf<Long>()
            db.rawQuery("SELECT DISTINCT category_id FROM saved_stories", null).use { c ->
                while (c.moveToNext()) {
                    catIds.add(c.getLong(0))
                }
            }
            for (cid in catIds) {
                val ids = mutableListOf<Long>()
                db.rawQuery(
                    "SELECT id FROM saved_stories WHERE category_id = ? ORDER BY created_at ASC, id ASC",
                    arrayOf(cid.toString()),
                ).use { c ->
                    while (c.moveToNext()) {
                        ids.add(c.getLong(0))
                    }
                }
                ids.forEachIndexed { ord, sid ->
                    val cv = ContentValues().apply { put("sort_order", ord) }
                    db.update("saved_stories", cv, "id = ?", arrayOf(sid.toString()))
                }
            }
        }
        if (oldVersion < 3) {
            db.execSQL(
                "ALTER TABLE categories ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0",
            )
            val ids = mutableListOf<Long>()
            db.rawQuery(
                "SELECT id FROM categories ORDER BY name COLLATE NOCASE ASC, id ASC",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    ids.add(c.getLong(0))
                }
            }
            ids.forEachIndexed { ord, id ->
                val cv = ContentValues().apply { put("sort_order", ord) }
                db.update("categories", cv, "id = ?", arrayOf(id.toString()))
            }
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE saved_stories ADD COLUMN import_source_uri TEXT")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE categories ADD COLUMN import_folder_tree_uri TEXT")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE categories ADD COLUMN cover_image_path TEXT")
        }
        if (oldVersion < 7) {
            db.execSQL(
                "ALTER TABLE categories ADD COLUMN is_online INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL("ALTER TABLE categories ADD COLUMN online_base_url TEXT")
            db.execSQL("ALTER TABLE categories ADD COLUMN online_content_selector TEXT")
            db.execSQL("ALTER TABLE categories ADD COLUMN online_next_page_selector TEXT")
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE saved_stories ADD COLUMN online_page_url TEXT")
        }
        if (oldVersion < 9) {
            db.execSQL(
                "ALTER TABLE saved_stories ADD COLUMN online_content_parse_ok INTEGER NOT NULL DEFAULT 1",
            )
            db.execSQL("ALTER TABLE saved_stories ADD COLUMN online_next_page_url TEXT")
        }
        if (oldVersion < 10) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS online_domain_parsers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    domain TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    online_next_page_selector TEXT,
                    online_content_selector TEXT
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 12) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS deferred_archive_processed_items (
                    category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
                    source_key TEXT NOT NULL,
                    item_index1 INTEGER NOT NULL,
                    processed_at INTEGER NOT NULL,
                    PRIMARY KEY (category_id, source_key, item_index1)
                )
                """.trimIndent(),
            )
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val DB_NAME = "story_library.db"
        const val DB_VERSION = 12
        const val DEFAULT_CATEGORY_NAME = "Chưa phân loại"
    }
}

/**
 * SQLite: thể loại + metadata truyện (đường dẫn file, chỉ số câu TTS cuối).
 * Nội dung UTF-8 nằm dưới [libraryRootDir] (thư mục app trên bộ nhớ ngoài, không cần quyền ghi riêng).
 */
class StoryLibraryRepository(private val context: Context) {

    private val helper = StoryLibraryOpenHelper(context)

    /**
     * Tuần tự hóa mọi ghi liên quan import deferred (PDF/ZIP/EPUB) —
     * tránh reorder / chèn chương xen kẽ với prefetch nền.
     */
    private val deferredArchiveWriteMutex = Mutex()

    suspend fun <T> withDeferredArchiveWriteLock(block: suspend () -> T): T =
        deferredArchiveWriteMutex.withLock { block() }

    fun libraryRootDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, LIBRARY_FOLDER).apply { mkdirs() }
    }

    private fun categoryDir(categoryId: Long): File =
        File(libraryRootDir(), "cat_$categoryId").apply { mkdirs() }

    fun listCategories(): List<LibraryCategoryRow> {
        ensureDeferredArchiveProcessedBackfillAfterDbUpgrade()
        val db = helper.readableDatabase
        db.rawQuery(
            """
            SELECT c.id, c.name,
                   (SELECT COUNT(*) FROM saved_stories s WHERE s.category_id = c.id) AS cnt,
                   CASE
                       WHEN c.import_folder_tree_uri IS NOT NULL
                           AND length(trim(c.import_folder_tree_uri)) > 0
                       THEN 1 ELSE 0
                   END AS has_import,
                   c.cover_image_path,
                   c.is_online,
                   c.online_base_url,
                   c.online_content_selector,
                   c.online_next_page_selector
            FROM categories c
            ORDER BY c.sort_order ASC, c.name COLLATE NOCASE ASC
            """.trimIndent(),
            null,
        ).use { c ->
            val out = mutableListOf<LibraryCategoryRow>()
            while (c.moveToNext()) {
                out.add(
                    LibraryCategoryRow(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "",
                        storyCount = c.getInt(2),
                        hasImportFolder = c.getInt(3) != 0,
                        coverImagePath = c.getString(4)?.trim()?.takeIf { it.isNotEmpty() },
                        isOnline = c.getInt(5) != 0,
                        onlineBaseUrl = c.getString(6)?.trim()?.takeIf { it.isNotEmpty() },
                        onlineContentSelectors =
                            decodeOnlineContentSelectors(c.getString(7)),
                        onlineNextPageSelector =
                            c.getString(8)?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }
            return out
        }
    }

    fun insertCategory(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        val now = System.currentTimeMillis()
        val db = helper.writableDatabase
        val nextOrd =
            db.rawQuery(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM categories",
                null,
            ).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        val cv =
            ContentValues().apply {
                put("name", trimmed)
                put("created_at", now)
                put("sort_order", nextOrd)
            }
        val id = db.insert("categories", null, cv)
        if (id < 0) error("Không tạo được truyện (tên trùng?)")
        return id
    }

    /**
     * Tạo thể loại online: [name] và [online_base_url] đều là URL đã chuẩn hóa (unique theo tên);
     * seed một chương với [online_page_url] = URL.
     * Trả về (id, url) để mở WebView.
     */
    fun insertOnlineLibraryCategory(userInput: String): Pair<Long, String> {
        val url = normalizeWebCategoryUrl(userInput.trim())
        require(url.isNotEmpty())
        val parsed = Uri.parse(url)
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") { "URL phải bắt đầu bằng http hoặc https" }
        require(!parsed.host.isNullOrBlank()) { "URL không có tên máy chủ" }
        val now = System.currentTimeMillis()
        val db = helper.writableDatabase
        val nextOrd =
            db.rawQuery(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM categories",
                null,
            ).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        val cv =
            ContentValues().apply {
                put("name", url)
                put("created_at", now)
                put("sort_order", nextOrd)
                put("is_online", 1)
                put("online_base_url", url)
            }
        val id = db.insert("categories", null, cv)
        if (id < 0) error("Không tạo được truyện online (URL trùng?)")
        val seedTitle =
            parsed.host?.trim()?.takeIf { it.isNotEmpty() } ?: "Trang web"
        val seedBody =
            buildString {
                appendLine("Truyện online — URL trang được lưu trong metadata chương (online_page_url).")
                appendLine()
                appendLine(url)
            }
        try {
            insertStory(
                categoryId = id,
                title = seedTitle,
                body = seedBody,
                onlinePageUrl = url,
            )
        } catch (e: Exception) {
            helper.writableDatabase.delete("categories", "id = ?", arrayOf(id.toString()))
            runCatching { categoryDir(id).deleteRecursively() }
            throw e
        }
        return id to url
    }

    fun updateOnlineNextPageSelector(categoryId: Long, nextPageSelector: String) {
        setOnlineNextPageSelectorForCategory(categoryId, nextPageSelector.trim())
    }

    fun getOnlineNextPageSelectorForCategory(categoryId: Long): String? {
        helper.readableDatabase
            .rawQuery(
                "SELECT online_next_page_selector FROM categories WHERE id = ?",
                arrayOf(categoryId.toString()),
            )
            .use { c ->
                if (!c.moveToFirst()) return null
                return c.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
            }
    }

    /** null = xóa selector trang sau. */
    fun setOnlineNextPageSelectorForCategory(categoryId: Long, selector: String?) {
        val cv = ContentValues()
        val t = selector?.trim().orEmpty()
        if (t.isEmpty()) {
            cv.putNull("online_next_page_selector")
        } else {
            cv.put("online_next_page_selector", t)
        }
        val n =
            helper.writableDatabase.update(
                "categories",
                cv,
                "id = ?",
                arrayOf(categoryId.toString()),
            )
        if (n != 1) error("Không cập nhật được selector trang sau")
    }

    fun getOnlineContentSelectorsForCategory(categoryId: Long): List<String> {
        val raw = readOnlineContentSelectorColumnRaw(categoryId)
        return decodeOnlineContentSelectors(raw)
    }

    /** Thêm một selector nội dung (giữ các selector cũ). */
    fun appendOnlineContentSelector(categoryId: Long, selector: String) {
        val trimmed = selector.trim()
        require(trimmed.isNotEmpty())
        val list = getOnlineContentSelectorsForCategory(categoryId).toMutableList()
        list.add(trimmed)
        writeOnlineContentSelectorsColumn(categoryId, list)
    }

    /** Ghi đè toàn bộ danh sách selector nội dung. */
    fun replaceOnlineContentSelectors(categoryId: Long, selectors: List<String>) {
        writeOnlineContentSelectorsColumn(
            categoryId,
            selectors.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    fun listOnlineDomainParsers(): List<OnlineDomainParserRow> {
        helper.readableDatabase
            .rawQuery(
                """
                SELECT id, domain, online_next_page_selector, online_content_selector
                FROM online_domain_parsers
                ORDER BY domain COLLATE NOCASE ASC
                """.trimIndent(),
                null,
            )
            .use { c ->
                return buildList {
                    while (c.moveToNext()) {
                        add(
                            OnlineDomainParserRow(
                                id = c.getLong(0),
                                domain = c.getString(1)?.trim().orEmpty(),
                                onlineNextPageSelector =
                                    c.getString(2)?.trim()?.takeIf { it.isNotEmpty() },
                                contentSelectors = decodeOnlineContentSelectors(c.getString(3)),
                            ),
                        )
                    }
                }
            }
    }

    /**
     * Ghi parser cho [domainKey] (đã chuẩn hóa, ví dụ `example.com`). Cùng domain thì cập nhật.
     */
    fun upsertOnlineDomainParser(
        domainKey: String,
        nextPageSelector: String?,
        contentSelectors: List<String>,
    ) {
        val domain =
            domainKey
                .trim()
                .lowercase(Locale.ROOT)
                .removePrefix("www.")
                .trim()
        require(domain.isNotEmpty())
        val enc = encodeOnlineContentSelectors(contentSelectors)
        val db = helper.writableDatabase
        val cv =
            ContentValues().apply {
                put("domain", domain)
                val np = nextPageSelector?.trim().orEmpty()
                if (np.isEmpty()) {
                    putNull("online_next_page_selector")
                } else {
                    put("online_next_page_selector", np)
                }
                put("online_content_selector", enc)
            }
        val n = db.update("online_domain_parsers", cv, "domain = ?", arrayOf(domain))
        if (n == 0) {
            val ins = db.insert("online_domain_parsers", null, cv)
            if (ins < 0) error("Không thêm được parser domain")
        }
    }

    fun deleteOnlineDomainParser(id: Long) {
        helper.writableDatabase.delete("online_domain_parsers", "id = ?", arrayOf(id.toString()))
    }

    /** Khớp theo domain của [pageUrl] với bảng parser mặc định. */
    fun findOnlineDomainParserForPageUrl(pageUrl: String): OnlineDomainParserRow? {
        val key = normalizedOnlineParserDomainKey(pageUrl) ?: return null
        helper.readableDatabase
            .rawQuery(
                """
                SELECT id, domain, online_next_page_selector, online_content_selector
                FROM online_domain_parsers
                WHERE domain = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(key),
            )
            .use { c ->
                if (!c.moveToFirst()) return null
                return OnlineDomainParserRow(
                    id = c.getLong(0),
                    domain = c.getString(1)?.trim().orEmpty(),
                    onlineNextPageSelector =
                        c.getString(2)?.trim()?.takeIf { it.isNotEmpty() },
                    contentSelectors = decodeOnlineContentSelectors(c.getString(3)),
                )
            }
    }

    private fun readOnlineContentSelectorColumnRaw(categoryId: Long): String? {
        helper.readableDatabase
            .rawQuery(
                "SELECT online_content_selector FROM categories WHERE id = ?",
                arrayOf(categoryId.toString()),
            )
            .use { c ->
                if (!c.moveToFirst()) return null
                return c.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
            }
    }

    private fun writeOnlineContentSelectorsColumn(categoryId: Long, selectors: List<String>) {
        val encoded = encodeOnlineContentSelectors(selectors)
        val cv = ContentValues().apply { put("online_content_selector", encoded) }
        val n =
            helper.writableDatabase.update(
                "categories",
                cv,
                "id = ?",
                arrayOf(categoryId.toString()),
            )
        if (n != 1) error("Không cập nhật được selector nội dung cho truyện")
    }

    /** Ghi lại thứ tự hiển thị thể loại (0 = trên cùng). */
    fun reorderCategoryDisplayOrder(categoryIdsInDisplayOrder: List<Long>) {
        if (categoryIdsInDisplayOrder.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            categoryIdsInDisplayOrder.forEachIndexed { ord, id ->
                val cv = ContentValues().apply { put("sort_order", ord) }
                db.update("categories", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Tìm thể loại theo tên (UNIQUE COLLATE NOCASE trên bảng). */
    fun findCategoryIdByName(name: String): Long? {
        val t = name.trim()
        if (t.isEmpty()) return null
        helper.readableDatabase
            .rawQuery(
                "SELECT id FROM categories WHERE name = ? LIMIT 1",
                arrayOf(t),
            ).use { c ->
                if (c.moveToFirst()) return c.getLong(0)
            }
        return null
    }

    /** Tạo thể loại nếu chưa có (xử lý trùng tên do race). */
    fun getOrCreateCategoryByName(name: String): Long {
        val t = name.trim()
        require(t.isNotEmpty())
        findCategoryIdByName(t)?.let { return it }
        return runCatching { insertCategory(t) }.getOrElse {
            findCategoryIdByName(t) ?: error("Không tạo hoặc tìm truyện: $t")
        }
    }

    /**
     * Số thứ tự tiếp theo cho tiêu đề `không tên N` trong thể loại (theo max N trong tiêu đề hiện có).
     */
    fun nextUntitledInboundStorySuffix(categoryId: Long): Int {
        val p = Regex("^không tên\\s+(\\d+)\\s*$", RegexOption.IGNORE_CASE)
        var max = 0
        for (row in listStories(categoryId)) {
            p.find(row.title.trim())?.let { m ->
                m.groupValues[1].toIntOrNull()?.let { max = maxOf(max, it) }
            }
        }
        return max + 1
    }

    /** Lưu nội dung đã chuẩn hoá vào thể loại [INBOUND_UNTITLED_CATEGORY_NAME], tiêu đề `không tên N`. */
    fun importInboundTextAsUntitledStory(
        canonicalBody: String,
        importSourceUri: String? = null,
    ): Pair<Long, String> {
        val catId = getOrCreateCategoryByName(INBOUND_UNTITLED_CATEGORY_NAME)
        val n = nextUntitledInboundStorySuffix(catId)
        val title = "không tên $n"
        val id = insertStory(catId, title, canonicalBody, importSourceUri = importSourceUri)
        return id to title
    }

    private val shareImportTitlePattern =
        Regex("^chia sẻ\\s+(\\d+)\\s*$", RegexOption.IGNORE_CASE)

    private fun nextShareImportTitleSuffix(categoryId: Long): Int {
        var max = 0
        for (row in listStories(categoryId)) {
            shareImportTitlePattern.find(row.title.trim())?.let { m ->
                m.groupValues[1].toIntOrNull()?.let { max = maxOf(max, it) }
            }
        }
        return max + 1
    }

    /**
     * Lưu nội dung chia sẻ vào thể loại [categoryId] (thể loại của truyện đang mở); tiêu đề `Chia sẻ N`.
     */
    fun importSharedTextIntoCategory(
        categoryId: Long,
        canonicalBody: String,
        importSourceUri: String? = null,
    ): Pair<Long, String> {
        val n = nextShareImportTitleSuffix(categoryId)
        val title = "Chia sẻ $n"
        val id = insertStory(categoryId, title, canonicalBody, importSourceUri = importSourceUri)
        return id to title
    }

    fun renameCategory(categoryId: Long, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty())
        val cv = ContentValues().apply { put("name", trimmed) }
        val n =
            helper.writableDatabase.update(
                "categories",
                cv,
                "id = ?",
                arrayOf(categoryId.toString()),
            )
        if (n <= 0) error("Không đổi tên được truyện")
    }

    /** Ghi URI cây SAF của lần «Import thư mục» gần nhất — dùng để nhập lại / xóa hết rồi import lại. */
    fun setCategoryImportFolderTreeUri(categoryId: Long, treeUri: String?) {
        val cv =
            ContentValues().apply {
                if (treeUri.isNullOrBlank()) {
                    putNull("import_folder_tree_uri")
                } else {
                    put("import_folder_tree_uri", treeUri.trim())
                }
            }
        val n =
            helper.writableDatabase.update(
                "categories",
                cv,
                "id = ?",
                arrayOf(categoryId.toString()),
            )
        if (n <= 0) error("Không cập nhật được truyện")
    }

    /**
     * Lưu ảnh đại diện thể loại từ URI (picker): giải mã, thu nhỏ tối đa 256 px cạnh dài, JPEG trong thư mục thể loại.
     */
    fun saveCategoryCoverFromContentUri(categoryId: Long, imageUri: Uri) {
        val decoded =
            context.contentResolver.openInputStream(imageUri)?.use { ins ->
                decodeBitmapFromStreamWithMaxSide(ins, maxDimension = 960)
            } ?: error("Không đọc được ảnh")
        val scaled = scaleBitmapToMaxSide(decoded, maxSide = 256)
        if (scaled !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }
        try {
            val dest = File(categoryDir(categoryId), COVER_THUMB_FILE_NAME)
            FileOutputStream(dest).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)) {
                    error("Không lưu được ảnh")
                }
            }
            val cv = ContentValues().apply { put("cover_image_path", dest.absolutePath) }
            val n =
                helper.writableDatabase.update(
                    "categories",
                    cv,
                    "id = ?",
                    arrayOf(categoryId.toString()),
                )
            if (n != 1) error("Không cập nhật được ảnh đại diện")
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    private fun decodeBitmapFromStreamWithMaxSide(
        ins: java.io.InputStream,
        maxDimension: Int,
    ): Bitmap? {
        val bytes = ins.readBytes()
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize =
                    calculateInSampleSize(
                        bounds.outWidth,
                        bounds.outHeight,
                        maxDimension,
                        maxDimension,
                    )
            }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun calculateInSampleSize(outWidth: Int, outHeight: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (outHeight > reqH || outWidth > reqW) {
            var halfH = outHeight / 2
            var halfW = outWidth / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmapToMaxSide(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun getCategoryImportFolderTreeUri(categoryId: Long): String? {
        helper.readableDatabase
            .rawQuery(
                "SELECT import_folder_tree_uri FROM categories WHERE id = ?",
                arrayOf(categoryId.toString()),
            )
            .use { c ->
                if (!c.moveToFirst()) return null
                return c.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
            }
    }

    /**
     * Thư mục giải nén EPUB/ZIP/PDF (`…/tts-ai-story/<tên>/`) chỉ lưu bằng `file://` từ
     * [setCategoryImportFolderTreeUri] — không áp dụng cho URI cây SAF «Import thư mục».
     */
    private fun archiveExtractRootFromFileImportTreeUri(uriStr: String): File? {
        val u = runCatching { Uri.parse(uriStr.trim()) }.getOrNull() ?: return null
        if (!u.scheme.equals("file", ignoreCase = true)) return null
        val path = u.path ?: return null
        val dir = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        if (!dir.isDirectory) return null
        val abs = dir.absolutePath.replace('\\', '/')
        val seg = "/$EXPORT_DOWNLOAD_FOLDER/"
        if (!abs.contains(seg) && !abs.endsWith("/$EXPORT_DOWNLOAD_FOLDER")) return null
        return dir
    }

    private fun deferredArchiveSourceFileFromOnlinePageUrl(url: String?): File? {
        val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        parseDeferredPdfPageOnlineUrl(u)?.let { return File(it.sourcePdfPath) }
        parseDeferredZipEntryOnlineUrl(u)?.let { return File(it.sourceZipPath) }
        parseDeferredEpubChapterOnlineUrl(u)?.let { return File(it.sourceEpubPath) }
        return null
    }

    private fun canonicalFilePathOrNull(f: File): String? =
        runCatching { f.canonicalFile.absolutePath }.getOrNull()

    private fun anyStoryStillReferencesArchiveExtractRoot(
        categoryId: Long,
        extractRootCanon: String,
    ): Boolean {
        val rootWithSep = extractRootCanon + File.separator
        for (s in listStories(categoryId)) {
            val src = deferredArchiveSourceFileFromOnlinePageUrl(s.onlinePageUrl) ?: continue
            val c = canonicalFilePathOrNull(src) ?: continue
            if (c == extractRootCanon || c.startsWith(rootWithSep)) return true
        }
        return false
    }

    /**
     * Sau khi xóa chương: nếu không còn chương nào tham chiếu file nguồn trong thư mục giải nén
     * `Download/tts-ai-story/…` đã gắn thể loại — xóa thư mục đó và gỡ `import_folder_tree_uri` dạng file.
     */
    private fun maybeDeleteOrphanedTtsAiStoryArchiveExtractDir(categoryId: Long) {
        val uriStr = getCategoryImportFolderTreeUri(categoryId) ?: return
        val root = archiveExtractRootFromFileImportTreeUri(uriStr) ?: return
        val rootCanon = runCatching { root.canonicalPath }.getOrNull() ?: return
        if (anyStoryStillReferencesArchiveExtractRoot(categoryId, rootCanon)) return
        runCatching { root.deleteRecursively() }
        runCatching { setCategoryImportFolderTreeUri(categoryId, null) }
    }

    private fun getCategoryName(categoryId: Long): String? {
        helper.readableDatabase
            .rawQuery(
                "SELECT name FROM categories WHERE id = ?",
                arrayOf(categoryId.toString()),
            )
            .use { c ->
                if (!c.moveToFirst()) return null
                return c.getString(0)
            }
    }

    /** Xóa mọi truyện trong thể loại (file nội dung + bản ghi; quyền URI từng file nếu có). */
    fun deleteAllStoriesInCategory(categoryId: Long) {
        val ids = listStories(categoryId).map { it.id }
        for (id in ids) {
            deleteStory(id)
        }
    }

    fun deleteCategory(categoryId: Long) {
        val db = helper.writableDatabase
        val treeUriStr =
            db.rawQuery(
                "SELECT import_folder_tree_uri FROM categories WHERE id = ?",
                arrayOf(categoryId.toString()),
            ).use { c ->
                if (c.moveToFirst()) c.getString(0)?.trim()?.takeIf { it.isNotEmpty() } else null
            }
        treeUriStr?.let { s ->
            archiveExtractRootFromFileImportTreeUri(s)?.let { root ->
                runCatching { root.deleteRecursively() }
            }
        }
        treeUriStr?.let { s ->
            val u = runCatching { Uri.parse(s) }.getOrNull()
            if (u != null && DocumentsContract.isTreeUri(u)) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        }
        val paths = mutableListOf<String>()
        val importUris = mutableListOf<String?>()
        db.rawQuery(
            "SELECT file_path, import_source_uri FROM saved_stories WHERE category_id = ?",
            arrayOf(categoryId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                c.getString(0)?.let { paths.add(it) }
                importUris.add(c.getString(1))
            }
        }
        for (src in importUris) {
            src?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(s),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                deleteTtsAiStoryDownloadsStagingCopy(context, s)
            }
        }
        db.delete("categories", "id = ?", arrayOf(categoryId.toString()))
        for (p in paths) {
            runCatching { File(p).delete() }
        }
        runCatching { categoryDir(categoryId).deleteRecursively() }
    }

    fun listStories(categoryId: Long): List<LibraryStoryRow> {
        val db = helper.readableDatabase
        db.rawQuery(
            """
            SELECT id, category_id, title, file_path, last_speech_sentence_index, sort_order, import_source_uri, online_page_url, online_content_parse_ok, online_next_page_url
            FROM saved_stories
            WHERE category_id = ?
            ORDER BY sort_order ASC, id ASC
            """.trimIndent(),
            arrayOf(categoryId.toString()),
        ).use { c ->
            val out = mutableListOf<LibraryStoryRow>()
            while (c.moveToNext()) {
                out.add(
                    LibraryStoryRow(
                        id = c.getLong(0),
                        categoryId = c.getLong(1),
                        title = c.getString(2) ?: "",
                        filePath = c.getString(3) ?: "",
                        lastSpeechSentenceIndex = c.getInt(4),
                        sortOrder = c.getInt(5),
                        importSourceUri = c.getString(6),
                        onlinePageUrl = c.getString(7)?.trim()?.takeIf { it.isNotEmpty() },
                        onlineContentParseOk = c.getInt(8) != 0,
                        onlineNextPageUrl =
                            c.getString(9)?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }
            return out
        }
    }

    /**
     * Truyện mở khi người dùng chọn một thể loại: ưu tiên [preferredStoryId] nếu thuộc thể loại,
     * sau đó bản ghi [updated_at] mới nhất, cuối cùng truyện đầu ([listStories] — sort_order, id).
     */
    fun resolveStoryIdToOpenForCategory(
        categoryId: Long,
        preferredStoryId: Long?,
    ): Long? {
        val stories = listStories(categoryId)
        if (stories.isEmpty()) return null
        if (preferredStoryId != null && stories.any { it.id == preferredStoryId }) {
            return preferredStoryId
        }
        return mostRecentlyUpdatedStoryIdInCategory(categoryId) ?: stories.first().id
    }

    private fun mostRecentlyUpdatedStoryIdInCategory(categoryId: Long): Long? {
        val db = helper.readableDatabase
        db.rawQuery(
            """
            SELECT id FROM saved_stories
            WHERE category_id = ?
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(categoryId.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            return c.getLong(0)
        }
    }

    /** Truyện kế trong cùng thể loại ([listStories] — sort_order, id); null nếu không còn. */
    fun nextStoryInCategoryAfter(storyId: Long): LibraryStoryRow? {
        val row = getStory(storyId) ?: return null
        val list = listStories(row.categoryId)
        val idx = list.indexOfFirst { it.id == storyId }
        if (idx < 0) return null
        return list.getOrNull(idx + 1)
    }

    /** Truyện trước trong cùng thể loại; null nếu là đầu danh sách. */
    fun previousStoryInCategoryBefore(storyId: Long): LibraryStoryRow? {
        val row = getStory(storyId) ?: return null
        val list = listStories(row.categoryId)
        val idx = list.indexOfFirst { it.id == storyId }
        if (idx <= 0) return null
        return list.getOrNull(idx - 1)
    }

    /**
     * Truyện trong thể loại có [LibraryStoryRow.onlinePageUrl] khớp [pageUrl]
     * (sau [normalizeOnlineStoryPageUrlForMatch]).
     */
    fun findStoryInCategoryByOnlinePageUrl(
        categoryId: Long,
        pageUrl: String,
    ): LibraryStoryRow? {
        val want = normalizeOnlineStoryPageUrlForMatch(pageUrl)
        if (want.isEmpty()) return null
        for (r in listStories(categoryId)) {
            val u = r.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (normalizeOnlineStoryPageUrlForMatch(u) == want) return r
        }
        return null
    }

    /** Tiêu đề truyện chưa trùng các truyện hiện có trong thể loại. */
    fun suggestUniqueStoryTitle(
        categoryId: Long,
        base: String,
    ): String {
        val used = listStories(categoryId).map { it.title }.toMutableSet()
        var t = base.trim().ifEmpty { "Trang web" }.take(120)
        var i = 2
        while (t in used) {
            t = "${base.trim().take(100)} ($i)"
            i++
        }
        return t
    }

    fun getStory(storyId: Long): LibraryStoryRow? {
        val db = helper.readableDatabase
        db.rawQuery(
            """
            SELECT id, category_id, title, file_path, last_speech_sentence_index, sort_order, import_source_uri, online_page_url, online_content_parse_ok, online_next_page_url
            FROM saved_stories WHERE id = ?
            """.trimIndent(),
            arrayOf(storyId.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            return LibraryStoryRow(
                id = c.getLong(0),
                categoryId = c.getLong(1),
                title = c.getString(2) ?: "",
                filePath = c.getString(3) ?: "",
                lastSpeechSentenceIndex = c.getInt(4),
                sortOrder = c.getInt(5),
                importSourceUri = c.getString(6),
                onlinePageUrl = c.getString(7)?.trim()?.takeIf { it.isNotEmpty() },
                onlineContentParseOk = c.getInt(8) != 0,
                onlineNextPageUrl =
                    c.getString(9)?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    /** Có [online_page_url] nhưng chưa parse nội dung web thành công → cần tải lại khi mở. */
    fun storyNeedsOnlineContentRefresh(row: LibraryStoryRow): Boolean =
        !row.onlineContentParseOk &&
            row.onlinePageUrl
                ?.trim()
                ?.let { url ->
                    val s = Uri.parse(url).scheme?.lowercase(Locale.ROOT)
                    s == "http" || s == "https"
                } == true

    /**
     * Đánh dấu một chỉ số nguồn deferred (PDF/ZIP/EPUB) đã được nạp xong — giữ khi xóa chương để
     * không nạp lại cùng chỉ số.
     */
    fun markDeferredArchiveItemProcessed(
        categoryId: Long,
        sourceKey: String,
        itemIndex1: Int,
    ) {
        if (itemIndex1 < 1 || sourceKey.isBlank()) return
        val db = helper.writableDatabase
        val cv =
            ContentValues().apply {
                put("category_id", categoryId)
                put("source_key", sourceKey)
                put("item_index1", itemIndex1)
                put("processed_at", System.currentTimeMillis())
            }
        db.insertWithOnConflict(
            "deferred_archive_processed_items",
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun isDeferredArchiveItemProcessed(
        categoryId: Long,
        sourceKey: String,
        itemIndex1: Int,
    ): Boolean {
        if (itemIndex1 < 1 || sourceKey.isBlank()) return false
        val db = helper.readableDatabase
        db.rawQuery(
            """
            SELECT 1 FROM deferred_archive_processed_items
            WHERE category_id = ? AND source_key = ? AND item_index1 = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(categoryId.toString(), sourceKey, itemIndex1.toString()),
        ).use { return it.moveToFirst() }
    }

    fun maxDeferredArchiveProcessedIndex1(
        categoryId: Long,
        sourceKey: String,
    ): Int {
        if (sourceKey.isBlank()) return 0
        val db = helper.readableDatabase
        db.rawQuery(
            """
            SELECT COALESCE(MAX(item_index1), 0) FROM deferred_archive_processed_items
            WHERE category_id = ? AND source_key = ?
            """.trimIndent(),
            arrayOf(categoryId.toString(), sourceKey),
        ).use {
            if (!it.moveToFirst()) return 0
            return it.getInt(0)
        }
    }

    private fun ensureDeferredArchiveProcessedBackfillAfterDbUpgrade() {
        val appPrefs =
            context.applicationContext.getSharedPreferences(
                AppPreferenceKeys.PREF_NAME,
                Context.MODE_PRIVATE,
            )
        if (appPrefs.getInt(AppPreferenceKeys.KEY_DEFERRED_ARCHIVE_PROCESSED_BACKFILL_DB_VERSION, 0) >= 12) {
            return
        }
        runCatching {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                db.rawQuery(
                    """
                    SELECT category_id, title, online_page_url FROM saved_stories
                    WHERE online_content_parse_ok = 1
                    AND online_page_url IS NOT NULL
                    AND length(trim(online_page_url)) > 0
                    """.trimIndent(),
                    null,
                ).use { c ->
                    while (c.moveToNext()) {
                        val catId = c.getLong(0)
                        val title = c.getString(1) ?: continue
                        val url = c.getString(2) ?: continue
                        val sk = deferredArchiveSourceKeyFromLazyOnlineUrl(url) ?: continue
                        val idx = parseEightDigitDeferredArchiveStoryIndex1(title) ?: continue
                        val cv =
                            ContentValues().apply {
                                put("category_id", catId)
                                put("source_key", sk)
                                put("item_index1", idx)
                                put("processed_at", System.currentTimeMillis())
                            }
                        db.insertWithOnConflict(
                            "deferred_archive_processed_items",
                            null,
                            cv,
                            SQLiteDatabase.CONFLICT_IGNORE,
                        )
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            appPrefs
                .edit()
                .putInt(AppPreferenceKeys.KEY_DEFERRED_ARCHIVE_PROCESSED_BACKFILL_DB_VERSION, 12)
                .apply()
        }
    }

    /**
     * Sau khi trích nội dung từ WebView thành công (trang đã tải không lỗi): đánh dấu parse xong
     * và lưu URL trang kế nếu lấy được `href`; không lấy được `href` thì vẫn coi parse xong,
     * `online_next_page_url` để null.
     */
    fun markOnlineStoryContentParseSuccess(
        storyId: Long,
        nextPageAbsoluteUrl: String?,
    ) {
        val cv =
            ContentValues().apply {
                put("online_content_parse_ok", 1)
                if (nextPageAbsoluteUrl.isNullOrBlank()) {
                    putNull("online_next_page_url")
                } else {
                    put("online_next_page_url", nextPageAbsoluteUrl.trim())
                }
                put("updated_at", System.currentTimeMillis())
            }
        val n =
            helper.writableDatabase.update(
                "saved_stories",
                cv,
                "id = ?",
                arrayOf(storyId.toString()),
            )
        if (n != 1) error("Không cập nhật được trạng thái parse online")
    }

    /**
     * Đảm bảo đã có bản ghi chương/trang kế: **`online_page_url` của chương kế = `online_next_page_url`
     * của chương hiện tại** (đọc từ DB sau [markOnlineStoryContentParseSuccess], hoặc [nextPageOverride]
     * nếu truyền — dùng khi chưa kịp đọc lại bản ghi cha).
     *
     * Chưa có thì [insertStory] với body rỗng, `online_content_parse_ok = 0` (pending/reload/queue).
     *
     * @return id truyện đã tồn tại hoặc vừa tạo; null nếu không có URL kế hoặc trùng vòng với chính [currentStoryId].
     */
    fun ensureOnlineNextChapterStoryRow(
        currentStoryId: Long,
        nextPageOverride: String? = null,
    ): Long? {
        val parent = getStory(currentStoryId) ?: return null
        val categoryId = parent.categoryId
        val resolvedNext =
            nextPageOverride?.trim()?.takeIf { it.isNotEmpty() }
                ?: parent.onlineNextPageUrl?.trim()?.takeIf { it.isNotEmpty() }
                ?: return null
        val norm = normalizeWebCategoryUrl(resolvedNext)
        if (norm.isEmpty()) return null
        val self = parent.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (self != null &&
            normalizeOnlineStoryPageUrlForMatch(self) == normalizeOnlineStoryPageUrlForMatch(norm)
        ) {
            return null
        }
        val existing = findStoryInCategoryByOnlinePageUrl(categoryId, norm)
        if (existing != null) {
            if (existing.id == currentStoryId) return null
            return existing.id
        }
        val baseTitle = baseStoryTitleFromOnlinePageUrl(norm)
        val title = suggestUniqueStoryTitle(categoryId, baseTitle)
        return insertStory(
            categoryId = categoryId,
            title = title,
            body = "",
            onlinePageUrl = norm,
        )
    }

    /**
     * Nếu truyện [storyId] đã có [LibraryStoryRow.onlineNextPageUrl] trong DB, đảm bảo tồn tại
     * một truyện khác trong cùng thể loại với `online_page_url` = URL đó (chèn mới nếu thiếu),
     * luôn ở trạng thái **chưa parse** (`online_content_parse_ok = 0`) để reload / hàng đợi tải nội dung.
     *
     * @return `true` nếu vừa [insertStory] thêm bản ghi mới.
     */
    fun ensurePlaceholderStoryForStoredOnlineNextPageUrl(storyId: Long): Boolean {
        val row = getStory(storyId) ?: return false
        val next =
            row.onlineNextPageUrl?.trim()?.takeIf { it.isNotEmpty() }
                ?: return false
        val self = row.onlinePageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (self != null &&
            normalizeOnlineStoryPageUrlForMatch(self) == normalizeOnlineStoryPageUrlForMatch(next)
        ) {
            return false
        }
        val norm = normalizeWebCategoryUrl(next)
        val before = findStoryInCategoryByOnlinePageUrl(row.categoryId, norm)
        ensureOnlineNextChapterStoryRow(storyId)
        val after = findStoryInCategoryByOnlinePageUrl(row.categoryId, norm)
        return before == null && after != null
    }

    private fun baseStoryTitleFromOnlinePageUrl(url: String): String {
        val uri = Uri.parse(url)
        val host = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: "web"
        val segments =
            uri.path?.trim('/')?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
        val seg =
            segments.lastOrNull()?.take(60)
                ?: uri.lastPathSegment?.take(60)
                ?: "trang"
        return "$host — $seg".take(120)
    }

    fun readStoryText(storyId: Long): String? {
        val row = getStory(storyId) ?: return null
        val f = File(row.filePath)
        if (!f.isFile) return null
        return f.readText(Charsets.UTF_8)
    }

    /** Nội dung file để ghép: bỏ dòng đầu nếu trùng tên file vật lý (vd. `story_12.txt`). */
    internal fun readStoryTextBodyForMerge(storyId: Long): String {
        val row = getStory(storyId) ?: return ""
        val raw = readStoryText(storyId).orEmpty()
        return stripLeadingFilenameLine(raw, row.filePath)
    }

    private fun stripLeadingFilenameLine(rawText: String, filePath: String): String {
        val fileName = File(filePath).name.trim()
        if (fileName.isEmpty()) return rawText
        val body = rawText.trimStart('\uFEFF')
        val lines = body.lines()
        if (lines.isEmpty()) return rawText
        val first = lines.first().trim()
        if (!first.equals(fileName, ignoreCase = true)) return rawText
        return lines.drop(1).joinToString("\n").trimEnd()
    }

    /** Ghép nội dung các file trong thể loại theo [sort_order] (xuống dòng giữa file). */
    fun mergeCategoryStoriesText(categoryId: Long): String {
        return listStories(categoryId).joinToString("\n") { readStoryTextBodyForMerge(it.id) }
    }

    /**
     * Ghi file UTF-8 vào `Downloads/[EXPORT_DOWNLOAD_FOLDER]/` trên bộ nhớ ngoài.
     * Tên file cố định `<tên_thể_loại>.txt` — xuất lại sẽ **ghi đè** file cũ.
     * Nội dung = [mergeCategoryStoriesText] (chỉ nối nội dung các file, không chèn tiêu đề hay phân cách).
     * API 29+: MediaStore.Downloads; API 28-: thư mục Downloads công khai (cần quyền tới API 28).
     */
    fun exportCategoryMergedTextToDownloads(
        categoryId: Long,
        categoryDisplayName: String,
        onProgress: ((completedSteps: Int, totalSteps: Int, currentLabel: String) -> Unit)? = null,
    ): String {
        val base = sanitizeExportFileBase(categoryDisplayName)
        val fileName = "${base}.txt"
        onProgress?.invoke(0, 1, "Đang ghép nội dung…")
        val merged = mergeCategoryStoriesText(categoryId)
        onProgress?.invoke(0, 1, "Đang ghi $fileName…")
        val out =
            writeUtf8TextToDownloadsRelative(
                "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DOWNLOAD_FOLDER",
                fileName,
                merged,
            )
        onProgress?.invoke(1, 1, "Hoàn tất")
        return out
    }

    /**
     * Mỗi truyện một file `.txt` trong `Downloads/[EXPORT_DOWNLOAD_FOLDER]/<tên thể loại>/`,
     * tên file đánh số 8 chữ số có số 0 đầu (`00000001.txt` …).
     * API 29+: trước khi ghi xóa các file cũ trong thư mục; từng file mới ghi đè nếu còn sót bản ghi trùng tên.
     */
    fun exportCategoryStoriesSeparateFilesToDownloads(
        categoryId: Long,
        categoryDisplayName: String,
        onProgress: ((completedSteps: Int, totalSteps: Int, currentLabel: String) -> Unit)? = null,
    ): String {
        val stories = listStories(categoryId)
        if (stories.isEmpty()) error("Không có chương")
        val folderBase = sanitizeExportFileBase(categoryDisplayName).ifBlank { "the_loai" }
        val relativeParent =
            "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DOWNLOAD_FOLDER/$folderBase"
        val total = stories.size
        onProgress?.invoke(0, total, "Đang dọn thư mục…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleteMediaStoreDownloadsInRelativeFolder(relativeParent)
            stories.forEachIndexed { index, story ->
                val fileName = String.format(Locale.US, "%08d.txt", index + 1)
                onProgress?.invoke(index, total, fileName)
                val body = readStoryTextBodyForMerge(story.id)
                insertUtf8TextIntoDownloads(relativeParent, fileName, body)
                onProgress?.invoke(index + 1, total, fileName)
            }
        } else {
            val downloads =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "$EXPORT_DOWNLOAD_FOLDER/$folderBase")
            if (dir.exists()) {
                dir.listFiles()?.forEach { child ->
                    if (child.isFile) child.delete()
                }
            }
            if (!dir.exists() && !dir.mkdirs()) {
                error("Không tạo được thư mục ${dir.absolutePath}")
            }
            stories.forEachIndexed { index, story ->
                val fileName = String.format(Locale.US, "%08d.txt", index + 1)
                onProgress?.invoke(index, total, fileName)
                val body = readStoryTextBodyForMerge(story.id)
                File(dir, fileName).writeText(body, Charsets.UTF_8)
                onProgress?.invoke(index + 1, total, fileName)
            }
        }
        onProgress?.invoke(total, total, "Hoàn tất")
        return "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DOWNLOAD_FOLDER/$folderBase/"
    }

    /**
     * Xuất thể loại thành một file `.zip` trong `Downloads/[EXPORT_DOWNLOAD_FOLDER]/`.
     * [mergeSingleFile]: `true` → một entry `<tên_thể_loại>.txt` (nội dung ghép UTF-8);
     * `false` → các entry `00000001.txt` … (mỗi truyện một file, tương thích nhập zip).
     * Xuất lại **ghi đè** file `.zip` cùng tên nếu đã có.
     */
    fun exportCategoryZipToDownloads(
        categoryId: Long,
        categoryDisplayName: String,
        mergeSingleFile: Boolean,
        onProgress: ((completedSteps: Int, totalSteps: Int, currentLabel: String) -> Unit)? = null,
    ): String {
        val base = sanitizeExportFileBase(categoryDisplayName).ifBlank { "the_loai" }
        val zipName = "$base.zip"
        val relativeParent = "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DOWNLOAD_FOLDER"
        onProgress?.invoke(0, 1, "Đang nén…")
        val zipBytes =
            if (mergeSingleFile) {
                val merged = mergeCategoryStoriesText(categoryId).toByteArray(Charsets.UTF_8)
                buildZipWithEntries(listOf("$base.txt" to merged))
            } else {
                val stories = listStories(categoryId)
                if (stories.isEmpty()) error("Không có chương")
                val entries =
                    stories.mapIndexed { index, story ->
                        val name = String.format(Locale.US, "%08d.txt", index + 1)
                        name to readStoryTextBodyForMerge(story.id).toByteArray(Charsets.UTF_8)
                    }
                buildZipWithEntries(entries)
            }
        onProgress?.invoke(0, 1, "Đang ghi $zipName…")
        val out =
            writeBytesToDownloadsRelative(
                relativeParent,
                zipName,
                "application/zip",
                zipBytes,
            )
        onProgress?.invoke(1, 1, "Hoàn tất")
        return out
    }

    /**
     * Xuất thể loại thành một file `.epub` (EPUB 3) trong `Downloads/[EXPORT_DOWNLOAD_FOLDER]/`.
     * Mục lục điều hướng (`nav.xhtml`): mỗi mục là **dòng đầu tiên** (trim) của nội dung truyện
     * sau khi áp dụng cùng quy tắc bỏ dòng tên file như [readStoryTextBodyForMerge].
     * Mỗi truyện một chương XHTML, nội dung trong `<pre>`.
     */
    fun exportCategoryEpubToDownloads(
        categoryId: Long,
        categoryDisplayName: String,
        onProgress: ((completedSteps: Int, totalSteps: Int, currentLabel: String) -> Unit)? = null,
    ): String {
        val stories = listStories(categoryId)
        if (stories.isEmpty()) error("Không có chương")
        val base = sanitizeExportFileBase(categoryDisplayName).ifBlank { "the_loai" }
        val epubName = "$base.epub"
        val relativeParent = "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DOWNLOAD_FOLDER"
        onProgress?.invoke(0, 1, "Đang tạo EPUB…")
        val chapters =
            stories.map { story ->
                val body = readStoryTextBodyForMerge(story.id)
                val navLabel = firstLineForEpubNavigationLabel(body)
                navLabel to body
            }
        val bytes =
            buildEpub3ZipBytes(
                bookTitle = categoryDisplayName.trim().ifBlank { base },
                chapters = chapters,
            )
        onProgress?.invoke(0, 1, "Đang ghi $epubName…")
        val out =
            writeBytesToDownloadsRelative(
                relativeParent,
                epubName,
                "application/epub+zip",
                bytes,
            )
        onProgress?.invoke(1, 1, "Hoàn tất")
        return out
    }

    private fun buildZipWithEntries(entries: List<Pair<String, ByteArray>>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((entryName, data) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun writeBytesToDownloadsRelative(
        relativeParentPath: String,
        displayName: String,
        mimeType: String,
        data: ByteArray,
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertBinaryIntoDownloads(relativeParentPath, displayName, mimeType, data)
            return "$relativeParentPath/$displayName"
        }
        val downloads =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val relAfterDownload =
            relativeParentPath.removePrefix("${Environment.DIRECTORY_DOWNLOADS}/")
        val dir = File(downloads, relAfterDownload)
        if (!dir.exists() && !dir.mkdirs()) {
            error("Không tạo được thư mục ${dir.absolutePath}")
        }
        val outFile = File(dir, displayName)
        outFile.writeBytes(data)
        return outFile.absolutePath
    }

    private fun overwriteBinaryInDownloadsUri(uri: Uri, data: ByteArray) {
        val resolver = context.contentResolver
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) }
        resolver.update(uri, values, null, null)
        try {
            resolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(data)
            } ?: error("Không mở được luồng ghi file")
        } catch (e: Exception) {
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun insertBinaryIntoDownloads(
        relativeParentPath: String,
        displayName: String,
        mimeType: String,
        data: ByteArray,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val existingIds = queryDownloadIdsSameFolderAndName(relativeParentPath, displayName)
            if (existingIds.isNotEmpty()) {
                val keepId = existingIds.first()
                for (extra in existingIds.drop(1)) {
                    runCatching {
                        resolver.delete(ContentUris.withAppendedId(collection, extra), null, null)
                    }
                }
                overwriteBinaryInDownloadsUri(
                    ContentUris.withAppendedId(collection, keepId),
                    data,
                )
                return
            }
        }
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeParentPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri =
            resolver.insert(collection, values)
                ?: error("Không tạo file trong Downloads ($relativeParentPath)")
        try {
            resolver.openOutputStream(uri)?.use { os ->
                os.write(data)
            } ?: error("Không mở được luồng ghi file")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun sanitizeExportFileBase(name: String): String =
        name.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(72)
            .ifBlank { "the_loai" }

    /**
     * Thư mục giải nén zip import: cùng quy ước đường dẫn với export từng file
     * (`Download/tts-ai-story/<tên đã chuẩn hoá>/`).
     * Ưu tiên thư mục Downloads công khai; nếu không tạo được (scoped storage) dùng
     * `Android/data/.../files/Download/tts-ai-story/…`.
     * Xóa sạch nội dung thư mục trước khi giải nén lại.
     */
    fun prepareZipImportExtractDirectory(folderLabel: String): File {
        val fb = sanitizeExportFileBase(folderLabel).ifBlank { "the_loai" }
        val relative = "$EXPORT_DOWNLOAD_FOLDER/$fb"
        val publicRoot =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: error("Không có thư mục Downloads")
        val publicDir = File(publicRoot, relative)
        val dir =
            if (publicDir.exists() || publicDir.mkdirs()) {
                publicDir
            } else {
                val parent =
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: run {
                            val ext = context.getExternalFilesDir(null) ?: context.filesDir
                            File(ext, "Download").apply { mkdirs() }
                        }
                val scoped = File(parent, relative)
                if (!scoped.exists() && !scoped.mkdirs()) {
                    error("Không tạo được thư mục giải nén ($relative).")
                }
                scoped
            }
        clearDirectoryForZipExtractReuse(dir)
        return dir
    }

    private fun clearDirectoryForZipExtractReuse(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                child.deleteRecursively()
            } else {
                child.delete()
            }
        }
    }

    private fun writeUtf8TextToDownloadsRelative(
        relativeParentPath: String,
        displayName: String,
        utf8Text: String,
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertUtf8TextIntoDownloads(relativeParentPath, displayName, utf8Text)
            return "$relativeParentPath/$displayName"
        }
        val downloads =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val relAfterDownload =
            relativeParentPath.removePrefix("${Environment.DIRECTORY_DOWNLOADS}/")
        val dir = File(downloads, relAfterDownload)
        if (!dir.exists() && !dir.mkdirs()) {
            error("Không tạo được thư mục ${dir.absolutePath}")
        }
        val outFile = File(dir, displayName)
        outFile.writeText(utf8Text, Charsets.UTF_8)
        return outFile.absolutePath
    }

    private fun queryDownloadIdsSameFolderAndName(
        relativeParentPath: String,
        displayName: String,
    ): List<Long> {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val norm = relativeParentPath.trimEnd('/')
        val withSlash = "$norm/"
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val sel =
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?)"
        val args = arrayOf(displayName, norm, withSlash)
        val out = mutableListOf<Long>()
        resolver.query(collection, projection, sel, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (c.moveToNext()) {
                out.add(c.getLong(idCol))
            }
        }
        return out
    }

    /** Ghi đè nội dung bản ghi Downloads hiện có (API 29+). */
    private fun overwriteUtf8TextInDownloadsUri(uri: Uri, utf8Text: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) }
        resolver.update(uri, values, null, null)
        try {
            resolver.openOutputStream(uri, "wt")?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(utf8Text) }
            } ?: error("Không mở được luồng ghi file")
        } catch (e: Exception) {
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    /** Ghi một file UTF-8 vào [MediaStore.Downloads] với [MediaStore.MediaColumns.RELATIVE_PATH] = [relativeParentPath]. */
    private fun insertUtf8TextIntoDownloads(
        relativeParentPath: String,
        displayName: String,
        utf8Text: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val existingIds = queryDownloadIdsSameFolderAndName(relativeParentPath, displayName)
            if (existingIds.isNotEmpty()) {
                val keepId = existingIds.first()
                for (extra in existingIds.drop(1)) {
                    runCatching {
                        resolver.delete(ContentUris.withAppendedId(collection, extra), null, null)
                    }
                }
                overwriteUtf8TextInDownloadsUri(
                    ContentUris.withAppendedId(collection, keepId),
                    utf8Text,
                )
                return
            }
        }
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeParentPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri =
            resolver.insert(collection, values)
                ?: error("Không tạo file trong Downloads ($relativeParentPath)")
        try {
            resolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { it.write(utf8Text) }
            } ?: error("Không mở được luồng ghi file")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun deleteMediaStoreDownloadsInRelativeFolder(relativeParentPath: String) {
        val normalized = relativeParentPath.trimEnd('/')
        for (path in listOf(normalized, "$normalized/")) {
            deleteMediaStoreDownloadsExactRelativePath(path)
        }
    }

    private fun deleteMediaStoreDownloadsExactRelativePath(relativeParentPath: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(relativeParentPath)
        val ids = mutableListOf<Long>()
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (c.moveToNext()) {
                ids.add(c.getLong(idCol))
            }
        }
        for (id in ids) {
            val u = ContentUris.withAppendedId(collection, id)
            runCatching { resolver.delete(u, null, null) }
        }
    }

    private fun nextSortOrderForCategory(db: SQLiteDatabase, categoryId: Long): Int {
        db.rawQuery(
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM saved_stories WHERE category_id = ?",
            arrayOf(categoryId.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return 0
            return c.getInt(0)
        }
    }

    /** Đổi thể loại: chuyển file sang thư mục mới, cập nhật DB. */
    fun moveStoryToCategory(storyId: Long, newCategoryId: Long) {
        val row = getStory(storyId) ?: error("Không tìm thấy chương")
        if (row.categoryId == newCategoryId) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val oldFile = File(row.filePath)
            val newDir = categoryDir(newCategoryId)
            newDir.mkdirs()
            val newFile = File(newDir, "story_$storyId.txt")
            if (oldFile.exists()) {
                if (!oldFile.renameTo(newFile)) {
                    oldFile.copyTo(newFile, overwrite = true)
                    oldFile.delete()
                }
            } else {
                newFile.writeText("", Charsets.UTF_8)
            }
            val ord = nextSortOrderForCategory(db, newCategoryId)
            val now = System.currentTimeMillis()
            val cv =
                ContentValues().apply {
                    put("category_id", newCategoryId)
                    put("file_path", newFile.absolutePath)
                    put("sort_order", ord)
                    put("updated_at", now)
                }
            db.update("saved_stories", cv, "id = ?", arrayOf(storyId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Ghi lại thứ tự truyện trong thể loại (0 = đầu khi ghép/đọc), theo [sort_order]. */
    fun reorderStoriesDisplayOrder(categoryId: Long, storyIdsInDisplayOrder: List<Long>) {
        if (storyIdsInDisplayOrder.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            storyIdsInDisplayOrder.forEachIndexed { ord, id ->
                val cv = ContentValues().apply { put("sort_order", ord) }
                db.update(
                    "saved_stories",
                    cv,
                    "id = ? AND category_id = ?",
                    arrayOf(id.toString(), categoryId.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Đổi thứ tự trong cùng thể loại: [delta] = -1 lên trên, +1 xuống dưới. */
    fun moveStoryOrderInCategory(storyId: Long, categoryId: Long, delta: Int) {
        require(delta == -1 || delta == 1)
        val db = helper.writableDatabase
        val pairs = mutableListOf<Pair<Long, Int>>()
        db.rawQuery(
            """
            SELECT id, sort_order FROM saved_stories
            WHERE category_id = ? ORDER BY sort_order ASC, id ASC
            """.trimIndent(),
            arrayOf(categoryId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                pairs.add(c.getLong(0) to c.getInt(1))
            }
        }
        val i = pairs.indexOfFirst { it.first == storyId }
        if (i < 0) return
        val j = i + delta
        if (j !in pairs.indices) return
        db.beginTransaction()
        try {
            val a = pairs[i]
            val b = pairs[j]
            db.update(
                "saved_stories",
                ContentValues().apply { put("sort_order", b.second) },
                "id = ?",
                arrayOf(a.first.toString()),
            )
            db.update(
                "saved_stories",
                ContentValues().apply { put("sort_order", a.second) },
                "id = ?",
                arrayOf(b.first.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Tạo bản ghi + file `story_{id}.txt` (metadata + nội dung). */
    private fun insertStoryWithoutAutoSplit(
        categoryId: Long,
        title: String,
        body: String,
        importSourceUri: String? = null,
        onlinePageUrl: String? = null,
    ): Long {
        val trimmedTitle = title.trim().ifEmpty { "Không tiêu đề" }
        val now = System.currentTimeMillis()
        val db = helper.writableDatabase
        val pendingPath = "pending:${System.nanoTime()}"
        val sortOrder = nextSortOrderForCategory(db, categoryId)
        val cv =
            ContentValues().apply {
                put("category_id", categoryId)
                put("title", trimmedTitle)
                put("file_path", pendingPath)
                put("last_speech_sentence_index", -1)
                put("sort_order", sortOrder)
                put("import_source_uri", importSourceUri)
                if (onlinePageUrl != null) {
                    put("online_page_url", onlinePageUrl.trim())
                    put("online_content_parse_ok", 0)
                } else {
                    put("online_content_parse_ok", 1)
                }
                put("created_at", now)
                put("updated_at", now)
            }
        val id = db.insert("saved_stories", null, cv)
        if (id < 0) error("Không lưu được metadata chương")
        val dir = categoryDir(categoryId)
        val file = File(dir, "story_$id.txt")
        file.writeText(body, Charsets.UTF_8)
        val upd = ContentValues().apply { put("file_path", file.absolutePath) }
        db.update("saved_stories", upd, "id = ?", arrayOf(id.toString()))
        return id
    }

    /** Tạo bản ghi + file `story_{id}.txt` trong thư mục thể loại. */
    fun insertStory(
        categoryId: Long,
        title: String,
        body: String,
        importSourceUri: String? = null,
        onlinePageUrl: String? = null,
    ): Long {
        val trimmedTitle = title.trim().ifEmpty { "Không tiêu đề" }
        return insertStoryWithoutAutoSplit(
            categoryId = categoryId,
            title = trimmedTitle,
            body = body,
            importSourceUri = importSourceUri,
            onlinePageUrl = onlinePageUrl,
        )
    }

    private fun readerPrefsForLibrarySplit(): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            AppPreferenceKeys.PREF_NAME,
            Context.MODE_PRIVATE,
        )

    private fun configuredSentencesPerChapterForLibrarySplit(): Int =
        readerPrefsForLibrarySplit()
            .getInt(
                AppPreferenceKeys.KEY_READER_VIEW_SENTENCES_PER_PAGE,
                AppPreferenceKeys.DEFAULT_READER_VIEW_SENTENCES_PER_PAGE,
            )
            .coerceIn(1, 9999)

    private fun canManualSentenceSplitLibraryRow(row: LibraryStoryRow): Boolean {
        val u = row.onlinePageUrl?.trim()
        if (u.isNullOrEmpty()) return true
        return isDeferredArchiveLazyOnlineUrl(u)
    }

    /** Tách theo câu ([ParagraphTextService.parseStoredTextToSentences]), tối đa [n] câu mỗi chương; cần > [n] câu và ≥ 2 chunk. */
    private fun computeLibrarySentenceSplitChunks(canon: String, n: Int): List<String>? {
        if (n < 1) return null
        val sentences =
            try {
                ParagraphTextService.parseStoredTextToSentences(canon)
                    .map { ParagraphTextService.sanitizeParagraphText(it) }
                    .filter { it.isNotEmpty() }
            } catch (_: Exception) {
                emptyList()
            }
        if (sentences.size <= n) return null
        val ch =
            sentences
                .chunked(n)
                .map { mergeParagraphs(it).trim() }
                .filter { it.isNotEmpty() }
                .map { canonicalTextFromRaw(it) }
        return if (ch.size >= 2) ch else null
    }

    /**
     * Tách chương trong mutex — ghi đầu bằng [writeStoryTextBodyToDiskAndDb] (không qua [updateStoryText]).
     */
    private suspend fun applyLibrarySentenceChapterSplitInLock(
        storyId: Long,
        categoryId: Long,
        displayTitle: String,
    ) {
        val row = getStory(storyId) ?: return
        if (!canManualSentenceSplitLibraryRow(row)) return
        val n = configuredSentencesPerChapterForLibrarySplit()
        val raw = readStoryText(storyId)?.let { canonicalTextFromRaw(it) } ?: return
        if (raw.isEmpty()) return
        val chunks = computeLibrarySentenceSplitChunks(raw, n) ?: return
        val headCanon = chunks.first()
        val oldBm = row.lastSpeechSentenceIndex
        val headParas =
            try {
                splitIntoParagraphs(headCanon)
            } catch (_: Exception) {
                emptyList()
            }
        val maxIdx = headParas.size - 1
        val clamped =
            if (oldBm < 0) {
                -1
            } else {
                oldBm.coerceAtMost(maxOf(0, maxIdx))
            }
        val fresh = getStory(storyId) ?: return
        writeStoryTextBodyToDiskAndDb(storyId, fresh, headCanon)
        updateLastSpeechSentenceIndex(storyId, clamped)
        val titleBase = displayTitle.trim().ifEmpty { "Chương" }
        val out = ArrayList<Long>()
        for (i in 1 until chunks.size) {
            val piece = chunks[i]
            val t = suggestUniqueStoryTitle(categoryId, "$titleBase (${i + 1})")
            out.add(
                insertStoryWithoutAutoSplit(
                    categoryId = categoryId,
                    title = t,
                    body = piece,
                    importSourceUri = null,
                    onlinePageUrl = null,
                ),
            )
        }
        val allIds = listStories(categoryId).map { it.id }
        val withoutNew = allIds.filter { it !in out }
        val sidPos = withoutNew.indexOf(storyId)
        if (sidPos >= 0) {
            val rebuilt = withoutNew.toMutableList()
            var at = sidPos + 1
            for (nid in out) {
                rebuilt.add(at, nid)
                at++
            }
            reorderStoriesDisplayOrder(categoryId, rebuilt)
        }
    }

    /**
     * Tách chương thư viện đang mở theo [AppPreferenceKeys.KEY_READER_VIEW_SENTENCES_PER_PAGE] (số câu mỗi khối khi tách thủ công).
     * Chương web `http` không tách; chương local hoặc deferred archive (epub-lazy, …) thì được.
     *
     * @return `true` nếu đã tách thành ít nhất hai chương.
     */
    fun trySplitLibraryChapterByConfiguredSentenceCount(storyId: Long): Boolean {
        val row = getStory(storyId) ?: return false
        if (!canManualSentenceSplitLibraryRow(row)) return false
        val canon = readStoryText(storyId)?.let { canonicalTextFromRaw(it) } ?: return false
        if (canon.isEmpty()) return false
        val n = configuredSentencesPerChapterForLibrarySplit()
        if (computeLibrarySentenceSplitChunks(canon, n) == null) return false
        runBlocking {
            withDeferredArchiveWriteLock {
                applyLibrarySentenceChapterSplitInLock(
                    storyId = storyId,
                    categoryId = row.categoryId,
                    displayTitle = row.title.trim().ifEmpty { "Chương" },
                )
            }
        }
        return true
    }

    /**
     * Import thư mục SAF: **mỗi file (đệ quy) một truyện**, không ghép nội dung.
     * Tiêu đề truyện lấy từ đường dẫn tương đối trong thư mục; [import_source_uri] = URI từng file để đồng bộ.
     * @return số truyện đã tạo
     */
    /**
     * @param onProgress (completedSteps, totalFiles, currentLabel) — gọi trên luồng gọi (thường IO);
     * [completedSteps] tăng dần 0..[totalFiles] sau mỗi file đã xử lý (kể cả bỏ qua).
     */
    fun importFolderAsSeparateStories(
        categoryId: Long,
        treeUri: Uri,
        _folderDisplayName: String,
        onProgress: ((completedSteps: Int, totalFiles: Int, currentLabel: String) -> Unit)? = null,
    ): Int {
        val entries = listImportFolderFilesSorted(context, treeUri.toString())
        if (entries.isEmpty()) error("Thư mục không có file")
        val total = entries.size
        onProgress?.invoke(0, total, "")
        val usedTitles = mutableSetOf<String>()
        var n = 0
        entries.forEachIndexed { index, item ->
            onProgress?.invoke(index, total, item.relativePath)
            val raw =
                runCatching {
                    readUtf8FromImportTreeEntry(context, item)
                }.getOrNull()
            if (raw != null) {
                val body = canonicalTextFromRaw(raw)
                if (body.isNotBlank()) {
                    val title =
                        uniquifyStoryTitleForImport(
                            sanitizeImportStoryTitle(item.relativePath),
                            usedTitles,
                        )
                    insertStory(categoryId, title, body, item.uri.toString())
                    n++
                }
            }
            onProgress?.invoke(index + 1, total, item.relativePath)
        }
        onProgress?.invoke(total, total, "Hoàn tất")
        if (n == 0) error("Không có file nào có nội dung sau khi chuẩn hoá")
        return n
    }

    /**
     * Nhập **một** file văn bản dưới [rootDir] (vd. vừa giải nén từ ZIP) thành một truyện trong [categoryId].
     * Bỏ qua nếu không phải file con của [rootDir], đọc UTF-8 lỗi, hoặc nội dung sau [canonicalTextFromRaw] rỗng.
     *
     * @param usedTitles tập tiêu đề đã dùng trong cùng một lượt nhập nhiều file (giống [importLocalDirectoryAsSeparateStories]).
     * @param storyTitleOverride nếu khác null — dùng làm cơ sở tiêu đề (sau [uniquifyStoryTitleForImport]), ví dụ `00000001` từ EPUB.
     * @return `true` nếu đã [insertStory].
     */
    fun importSingleLocalFileAsStoryIfText(
        categoryId: Long,
        rootDir: File,
        file: File,
        usedTitles: MutableSet<String>,
        storyTitleOverride: String? = null,
    ): Boolean {
        val rootCanon = rootDir.canonicalFile
        if (!rootCanon.isDirectory) return false
        val fileCanon =
            try {
                file.canonicalFile
            } catch (_: Exception) {
                return false
            }
        if (!fileCanon.isFile) return false
        if (!fileCanon.path.startsWith(rootCanon.path + File.separator)) return false
        val rel =
            rootCanon
                .toURI()
                .relativize(fileCanon.toURI())
                .path
                .trim('/')
                .replace('\\', '/')
        if (rel.isEmpty()) return false
        val raw =
            runCatching {
                fileCanon.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()
                ?: return false
        val body = canonicalTextFromRaw(raw)
        if (body.isBlank()) return false
        val baseTitle =
            storyTitleOverride?.trim()?.takeIf { it.isNotEmpty() }
                ?: sanitizeImportStoryTitle(rel)
        val title = uniquifyStoryTitleForImport(baseTitle, usedTitles)
        insertStory(categoryId, title, body, importSourceUri = Uri.fromFile(fileCanon).toString())
        return true
    }

    /**
     * Giống [importFolderAsSeparateStories] nhưng đọc từ thư mục cục bộ (vd. zip đã giải nén).
     * [import_source_uri] để null — không đồng bộ SAF theo URI file gốc.
     */
    fun importLocalDirectoryAsSeparateStories(
        categoryId: Long,
        rootDir: File,
        onProgress: ((completedSteps: Int, totalFiles: Int, currentLabel: String) -> Unit)? = null,
    ): Int {
        val rootCanon = rootDir.canonicalFile
        if (!rootCanon.isDirectory) error("Không phải thư mục")
        val entries =
            rootCanon
                .walkTopDown()
                .filter { it.isFile }
                .map { f ->
                    val rel =
                        rootCanon
                            .toURI()
                            .relativize(f.canonicalFile.toURI())
                            .path
                            .trim('/')
                            .replace('\\', '/')
                    rel to f
                }
                .sortedBy { it.first }
                .toList()
        if (entries.isEmpty()) error("Thư mục không có file")
        val total = entries.size
        onProgress?.invoke(0, total, "")
        val usedTitles = mutableSetOf<String>()
        var n = 0
        entries.forEachIndexed { index, (relPath, file) ->
            onProgress?.invoke(index, total, relPath)
            val raw =
                runCatching {
                    file.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull()
            if (raw != null) {
                val body = canonicalTextFromRaw(raw)
                if (body.isNotBlank()) {
                    val title =
                        uniquifyStoryTitleForImport(
                            sanitizeImportStoryTitle(relPath),
                            usedTitles,
                        )
                    insertStory(categoryId, title, body, importSourceUri = Uri.fromFile(file).toString())
                    n++
                }
            }
            onProgress?.invoke(index + 1, total, relPath)
        }
        onProgress?.invoke(total, total, "Hoàn tất")
        if (n == 0) error("Không có file nào có nội dung sau khi chuẩn hoá")
        return n
    }

    /**
     * Nhập các file `00000001.txt` … trong [dir] theo thứ tự số (không dùng thứ tự tên lexicographic),
     * mỗi file một truyện; tiêu đề = 8 chữ số (không đuôi .txt).
     */
    fun importEightDigitNumberedTxtFilesAsStories(
        categoryId: Long,
        dir: File,
        onProgress: ((completedSteps: Int, totalFiles: Int, currentLabel: String) -> Unit)? = null,
    ): Int {
        val files =
            dir.listFiles()
                ?.filter { f ->
                    f.isFile &&
                        f.name.length == 12 &&
                        f.name.endsWith(".txt", ignoreCase = true) &&
                        f.name.take(8).all { it.isDigit() }
                }
                ?.sortedBy { f -> f.name.take(8).toInt() }
                ?: emptyList()
        if (files.isEmpty()) error("Không có file 00000001.txt … trong thư mục")
        val total = files.size
        onProgress?.invoke(0, total, "")
        var n = 0
        files.forEachIndexed { index, file ->
            onProgress?.invoke(index, total, file.name)
            val title = file.name.take(8)
            val raw =
                runCatching {
                    file.readText(Charsets.UTF_8)
                }.getOrNull()
            if (raw != null) {
                val body = canonicalTextFromRaw(raw)
                if (body.isNotBlank()) {
                    insertStory(categoryId, title, body, Uri.fromFile(file).toString())
                    n++
                }
            }
            onProgress?.invoke(index + 1, total, file.name)
        }
        onProgress?.invoke(total, total, "Hoàn tất")
        if (n == 0) error("Không có file nào có nội dung sau khi chuẩn hoá")
        return n
    }

    private fun sanitizeImportStoryTitle(relativePath: String): String =
        relativePath.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(120)
            .ifBlank { "không tên" }

    private fun uniquifyStoryTitleForImport(base: String, used: MutableSet<String>): String {
        var t = base
        var i = 2
        while (t in used) {
            t = "${base.take(100)} ($i)"
            i++
        }
        used.add(t)
        return t
    }

    /**
     * Đồng bộ nội dung truyện import: URI **file** → đọc lại một file;
     * URI **cây** (bản cũ) → đọc ghép toàn cây như trước.
     */
    fun resyncImportedStory(storyId: Long) {
        val row = getStory(storyId) ?: error("Không tìm thấy chương")
        val uriStr =
            row.importSourceUri?.trim().takeUnless { it.isNullOrEmpty() }
                ?: error("Chương không gắn import")
        val uri = Uri.parse(uriStr)
        val raw =
            when {
                DocumentsContract.isTreeUri(uri) ->
                    readMergedUtf8FromDocumentTree(context, uri)
                uri.scheme.equals("file", ignoreCase = true) -> {
                    val p = uri.path ?: error("file URI không có path")
                    File(p).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                else -> readUtf8FromDocumentUri(context, uri)
            }
        updateStoryText(storyId, canonicalTextFromRaw(raw))
    }

    /**
     * Xóa toàn bộ truyện trong thể loại rồi nhập lại từ thư mục SAF đã lưu (`import_folder_tree_uri`),
     * giống lần «Import thư mục» đầu tiên (mỗi file một truyện).
     *
     * @throws IllegalStateException nếu thể loại chưa có URI cây (chưa import thư mục sau khi cập nhật app).
     */
    fun syncImportedStoriesInCategory(
        categoryId: Long,
        onProgress: ((completedSteps: Int, totalFiles: Int, currentLabel: String) -> Unit)? = null,
    ): Int {
        val treeStr =
            getCategoryImportFolderTreeUri(categoryId)
                ?: error(
                    "Truyện chưa gắn thư mục import. Hãy dùng «Import thư mục» cho truyện này (một lần) để lưu đường dẫn thư mục.",
                )
        val categoryName =
            getCategoryName(categoryId) ?: error("Không tìm thấy truyện")
        val entries = listImportFolderFilesSorted(context, treeStr)
        if (entries.isEmpty()) {
            error("Không đọc được file trong thư mục (có thể đã mất quyền hoặc thư mục rỗng).")
        }
        deleteAllStoriesInCategory(categoryId)
        return importFolderAsSeparateStories(
            categoryId,
            Uri.parse(treeStr.trim()),
            categoryName,
            onProgress,
        )
    }

    /** Đồng bộ mọi truyện có [import_source_uri]; trả về số truyện đồng bộ thành công. */
    fun syncAllImportedStories(): Int {
        val db = helper.readableDatabase
        val ids = mutableListOf<Long>()
        db.rawQuery(
            """
            SELECT id FROM saved_stories
            WHERE import_source_uri IS NOT NULL AND LENGTH(TRIM(import_source_uri)) > 0
            """.trimIndent(),
            null,
        ).use { c ->
            while (c.moveToNext()) {
                ids.add(c.getLong(0))
            }
        }
        var n = 0
        for (id in ids) {
            runCatching {
                resyncImportedStory(id)
                n++
            }
        }
        return n
    }

    private fun writeStoryTextBodyToDiskAndDb(storyId: Long, row: LibraryStoryRow, body: String) {
        val f = File(row.filePath)
        f.parentFile?.mkdirs()
        f.writeText(body, Charsets.UTF_8)
        val cv =
            ContentValues().apply {
                put("updated_at", System.currentTimeMillis())
            }
        helper.writableDatabase.update("saved_stories", cv, "id = ?", arrayOf(storyId.toString()))
    }

    fun updateStoryText(storyId: Long, body: String) {
        val row = getStory(storyId) ?: error("Không tìm thấy chương")
        writeStoryTextBodyToDiskAndDb(storyId, row, body)
    }

    /**
     * Nối nội dung [appendStoryId] vào cuối [targetStoryId] (đã bỏ dòng đầu trùng tên file nếu có),
     * giữ / ánh xạ `last_speech_sentence_index` của đích theo nội dung sau ghép, rồi xóa truyện [appendStoryId].
     * Hai chương phải thuộc cùng một truyện (nhóm).
     */
    fun joinAppendStoryIntoTarget(targetStoryId: Long, appendStoryId: Long) {
        val target = getStory(targetStoryId) ?: error("Không tìm thấy chương đích")
        val append = getStory(appendStoryId) ?: error("Không tìm thấy chương ghép")
        require(target.categoryId == append.categoryId) { "Hai chương không cùng một truyện" }
        require(targetStoryId != appendStoryId) { "Không ghép chương vào chính nó" }
        val a = readStoryTextBodyForMerge(targetStoryId).trimEnd()
        val b = readStoryTextBodyForMerge(appendStoryId).trim()
        val merged =
            when {
                a.isEmpty() -> b
                b.isEmpty() -> a
                else -> "$a\n\n$b"
            }
        updateStoryText(targetStoryId, merged)
        val mergedCanonical = canonicalTextFromRaw(merged)
        val mergedSentenceCount = splitIntoParagraphs(mergedCanonical).size
        val maxIdx = mergedSentenceCount - 1
        fun clampSentenceIndex(i: Int): Int =
            when {
                i < 0 || maxIdx < 0 -> -1
                else -> i.coerceIn(0, maxIdx)
            }
        val canonicalA = canonicalTextFromRaw(a)
        val sentencesBeforeAppend =
            if (canonicalA.isEmpty()) 0 else splitIntoParagraphs(canonicalA).size
        val mergedBookmark =
            when {
                b.isEmpty() -> clampSentenceIndex(target.lastSpeechSentenceIndex)
                a.isEmpty() -> clampSentenceIndex(append.lastSpeechSentenceIndex)
                append.lastSpeechSentenceIndex >= 0 ->
                    clampSentenceIndex(sentencesBeforeAppend + append.lastSpeechSentenceIndex)
                else -> clampSentenceIndex(target.lastSpeechSentenceIndex)
            }
        val now = System.currentTimeMillis()
        val cv =
            ContentValues().apply {
                put("last_speech_sentence_index", mergedBookmark)
                put("updated_at", now)
            }
        helper.writableDatabase.update(
            "saved_stories",
            cv,
            "id = ?",
            arrayOf(targetStoryId.toString()),
        )
        deleteStory(appendStoryId)
    }

    /**
     * Ghi nội dung truyện giống [updateStoryText] nhưng **không** ném lỗi khi bản ghi đã bị xóa
     * (vd. người dùng xóa truyện rồi mở EPUB/zip — autosave vẫn giữ [storyId] cũ trong state).
     */
    fun updateStoryTextIfExists(storyId: Long, body: String): Boolean {
        val row = getStory(storyId) ?: return false
        writeStoryTextBodyToDiskAndDb(storyId, row, body)
        return true
    }

    /**
     * Đổi selector / URL online: coi nội dung đã lưu là cũ — truyện có [online_page_url] trong thể loại
     * được đánh dấu chưa parse để lần mở sau (hoặc sync) tải lại từ web.
     */
    fun resetOnlineContentParseStateForStoriesInCategory(categoryId: Long) {
        val now = System.currentTimeMillis()
        val cv =
            ContentValues().apply {
                put("online_content_parse_ok", 0)
                putNull("online_next_page_url")
                put("updated_at", now)
            }
        helper.writableDatabase.update(
            "saved_stories",
            cv,
            "category_id = ? AND online_page_url IS NOT NULL AND length(trim(online_page_url)) > 0",
            arrayOf(categoryId.toString()),
        )
    }

    /** Chỉ đổi tiêu đề hiển thị trong thư viện (không đổi file nội dung). */
    fun renameStory(storyId: Long, newTitle: String) {
        getStory(storyId) ?: error("Không tìm thấy chương")
        val trimmed = newTitle.trim().ifEmpty { "Không tiêu đề" }
        val cv =
            ContentValues().apply {
                put("title", trimmed)
                put("updated_at", System.currentTimeMillis())
            }
        val n =
            helper.writableDatabase.update(
                "saved_stories",
                cv,
                "id = ?",
                arrayOf(storyId.toString()),
            )
        if (n <= 0) error("Không đổi tên được")
    }

    /** Cập nhật `last_speech_sentence_index` trong DB cho chương [storyId]. */
    fun updateLastSpeechSentenceIndex(storyId: Long, sentenceIndex: Int) {
        val cv =
            ContentValues().apply {
                put("last_speech_sentence_index", sentenceIndex)
                put("updated_at", System.currentTimeMillis())
            }
        helper.writableDatabase.update("saved_stories", cv, "id = ?", arrayOf(storyId.toString()))
    }

    fun deleteStory(storyId: Long) {
        val row = getStory(storyId) ?: return
        if (row.onlineContentParseOk) {
            val sk = deferredArchiveSourceKeyFromLazyOnlineUrl(row.onlinePageUrl) ?: ""
            if (sk.isNotEmpty()) {
                val idx = parseEightDigitDeferredArchiveStoryIndex1(row.title)
                if (idx != null) {
                    markDeferredArchiveItemProcessed(row.categoryId, sk, idx)
                }
            }
        }
        row.importSourceUri?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(s),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        deleteTtsAiStoryDownloadsStagingCopy(context, row.importSourceUri)
        runCatching { File(row.filePath).delete() }
        val categoryId = row.categoryId
        helper.writableDatabase.delete("saved_stories", "id = ?", arrayOf(storyId.toString()))
        maybeDeleteOrphanedTtsAiStoryArchiveExtractDir(categoryId)
    }

    companion object {
        private const val LIBRARY_FOLDER = "story_library"

        private const val EXPORT_DOWNLOAD_FOLDER = "tts-ai-story"

        /** Thể loại mặc định khi nhận chia sẻ / Mở bằng / Gửi tới. */
        const val INBOUND_UNTITLED_CATEGORY_NAME = "không tên"

        private const val COVER_THUMB_FILE_NAME = "cover_thumb.jpg"
    }
}
