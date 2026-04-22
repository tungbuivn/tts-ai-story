package com.ttsaistory.app.data

/**
 * Tên bảng / cột SQLite thư viện truyện (`story_library.db`, bảng `categories` và `saved_stories`).
 *
 * Mục đích: một chỗ tham chiếu tĩnh cho raw SQL, migration và tài liệu. Bạn có thể bổ sung KDoc
 * chi tiết nghiệp vụ trên từng hằng số.
 *
 * **Lưu ý:** Giữ [DATABASE_VERSION] khớp với `StoryLibraryOpenHelper` / `StoryLibraryRepository` khi nâng DB.
 */
object StoryLibraryDbSchema {

    /** Tệp SQLite trong thư mục app (xem [StoryLibraryRepository]). */
    const val DATABASE_FILE_NAME: String = "story_library.db"

    /**
     * Phiên bản schema (`SQLiteOpenHelper`).
     * Khi tăng DB: cập nhật cùng lúc hằng số tương ứng trong `StoryLibraryRepository` (open helper).
     */
    const val DATABASE_VERSION: Int = 10

    /** Bảng thể loại. */
    object Categories {
        const val TABLE_NAME: String = "categories"

        const val ID: String = "id"
        const val NAME: String = "name"
        const val CREATED_AT: String = "created_at"
        const val SORT_ORDER: String = "sort_order"
        const val IMPORT_FOLDER_TREE_URI: String = "import_folder_tree_uri"
        const val COVER_IMAGE_PATH: String = "cover_image_path"
        const val IS_ONLINE: String = "is_online"
        const val ONLINE_BASE_URL: String = "online_base_url"
        /** Lưu JSON mảng selector nội dung (app decode qua repository). */
        const val ONLINE_CONTENT_SELECTOR: String = "online_content_selector"
        const val ONLINE_NEXT_PAGE_SELECTOR: String = "online_next_page_selector"
    }

    /**
     * Parser mặc định theo **domain** (host chuẩn hóa): khi tạo thể loại online, app khớp domain của URL
     * với bảng này để gán selector «trang tiếp» / «nội dung» cho thể loại.
     */
    object OnlineDomainParsers {
        const val TABLE_NAME: String = "online_domain_parsers"

        const val ID: String = "id"
        const val DOMAIN: String = "domain"
        const val ONLINE_CONTENT_SELECTOR: String = "online_content_selector"
        const val ONLINE_NEXT_PAGE_SELECTOR: String = "online_next_page_selector"
    }

    /** Bảng truyện đã lưu; nội dung văn bản nằm ở file [FILE_PATH]. */
    object SavedStories {
        const val TABLE_NAME: String = "saved_stories"

        const val ID: String = "id"
        /** FK → [Categories.ID] (`ON DELETE CASCADE`). */
        const val CATEGORY_ID: String = "category_id"
        const val TITLE: String = "title"
        /** Đường dẫn tuyệt đối file `story_{id}.txt` trong thư mục thể loại. */
        const val FILE_PATH: String = "file_path"
        const val LAST_SPEECH_SENTENCE_INDEX: String = "last_speech_sentence_index"
        const val SORT_ORDER: String = "sort_order"
        const val IMPORT_SOURCE_URI: String = "import_source_uri"
        /** URL trang web gắn với truyện sẽ được parse bằng web view */
        const val ONLINE_PAGE_URL: String = "online_page_url"
        const val ONLINE_CONTENT_PARSE_OK: String = "online_content_parse_ok"
        /** URL trang kế (href tuyệt đối) sau lần parse thành công; null nếu không lấy được. */
        const val ONLINE_NEXT_PAGE_URL: String = "online_next_page_url"
        const val CREATED_AT: String = "created_at"
        const val UPDATED_AT: String = "updated_at"
    }
}
