# TTS AI Story

**Người dùng cuối:** xem [USER_GUIDE.md](USER_GUIDE.md) — hướng dẫn từng bước, không cần kiến thức lập trình.

Ứng dụng Android đọc văn bản bằng **TTS** (giọng hệ thống hoặc **ElevenLabs**), soạn/ghi nhớ nội dung và quản lý **thư viện truyện** theo thể loại — có **import thư mục** từ bộ nhớ (SAF), **mở file .zip / .epub** (nhập thư viện), xuất văn bản (**.txt**, **.zip**, **.epub**) / xuất **file âm thanh AAC (.m4a)**. Chi tiết thao tác: [USER_GUIDE.md](USER_GUIDE.md).

- **Package:** `com.ttsaistory.app`
- **minSdk / targetSdk:** 26 / 35 (xem `app/build.gradle.kts`).

---

## Giao diện chính

| Khu vực | Mô tả |
|--------|--------|
| **Thanh trên (Top bar)** | Tiêu đề app, nút **menu** (☰), và tùy tab: **Cài đặt TTS** (tab Text); **Thêm thể loại** + **Import thư mục** (tab Thư viện). |
| **Hai tab** | **Text** — vùng soạn và đọc TTS. **Thư viện** — danh sách thể loại và truyện. |
| **Thanh dưới** | Trên tab Text: điều hướng đoạn/câu, tiến độ đọc (ví dụ `1 / 68`). |

---

## Menu bên (Navigation drawer)

Mở bằng nút **☰** trên thanh trên.

| Mục | Chức năng |
|-----|------------|
| **Mở file…** | SAF: **.txt** (văn bản), **.zip** (giải nén + nhập từng file văn), **.epub** (chương theo spine → thư viện). Zip/epub dùng dialog **Open file progress** trong lúc xử lý. |
| **Text** / **Thư viện** | Chuyển nhanh giữa hai tab. |
| **Cấu hình ElevenLabs** | API key và tùy chọn dùng giọng ElevenLabs (cần Internet). |
| **Cấu hình TTS hệ thống** | Giọng, tốc độ, cao độ Android TTS. |
| **Fonts** | Cấu hình font vùng soạn (tab Text). |
| **Giới thiệu** | Hộp thoại: tên app, phiên bản (`BuildConfig`), mô tả ngắn. |

---

## Tab **Text**

- **Soạn văn:** nhập hoặc dán nội dung; app có thể tách **đoạn / câu** để đọc TTS tuần tự (theo chế độ đang bật trên thanh công cụ soạn).
- **Chọn engine đọc:** **TTS hệ thống** hoặc **ElevenLabs** (segment trên vùng soạn / toolbar — đổi engine sẽ ảnh hưởng luồng đọc).
- **Danh sách truyện (icon list):** chọn truyện khác trong cùng thể loại, lọc tên, đổi thứ tự (khi không lọc).
- **Phát / dừng đọc:** dùng các nút điều khiển trên toolbar vùng Text (Play, dừng toàn bộ, v.v.). Khi tắt màn hình, app dùng **wake lock** để giảm khả năng bị cắt giữa chừng (tùy OEM / pin).
- **Cài đặt nhanh:** nút **bánh răng** trên top bar khi đang ở tab Text — mở cấu hình TTS tương ứng engine đang chọn (hệ thống / ElevenLabs).
- **Xuất âm thanh AAC (.m4a):** trên toolbar có thao tác xuất TTS sang file **.m4a** (tiến trình có thể chạy nền với thông báo; chi tiết trong UI dialog xuất).
- **Thanh dưới:** nhảy đoạn đầu/cuối, đoạn trước/sau, hiển thị chỉ số câu/đoạn đang đọc và tổng số (khi đã tính xong).

**Truyện đang mở từ thư viện:** khi mở một truyện từ tab Thư viện, tab Text hiển thị nội dung file thư viện; chỉnh sửa có thể được đồng bộ ghi lại vào file truyện (autosave theo debounce của app).

---

## Tab **Thư viện**

### Thể loại (category)

- Mỗi thể loại là một **thẻ**: tên, số truyện, nút **mở rộng/thu gọn** (mũi tên), **phát cả thể loại** (playlist — ghép nội dung các truyện rồi đưa sang tab Text để đọc TTS nếu có nội dung).
- **Kéo thứ tự thể loại:** giữ **⋮⋮** (drag handle) rồi kéo dọc; thả tay sau khi xong — thứ tự được lưu.
- **Menu ⋮** trên từng thể loại:
  - **Đổi tên thể loại**
  - **Xuất ra…** — xuất nội dung thể loại ra **Downloads** (đường dẫn kiểu `Download/tts-ai-story/…`): **một .txt ghép**, **thư mục + file .txt đánh số**, **một .zip**, hoặc **một .epub** (mục lục từ dòng đầu mỗi truyện).
  - **Đồng bộ thư mục** — với thể loại đã **import thư mục** trước đó (app đã lưu URI cây SAF): **xóa toàn bộ truyện trong thể loại** rồi **import lại** từ cùng thư mục; có **popup tiến trình** giống lúc import. Cần quyền đọc thư mục vẫn còn hiệu lực.
  - **Xóa thể loại**

### Truyện trong thể loại

- Chạm **tên thể loại** hoặc mũi tên để mở danh sách truyện.
- **Mở truyện:** chạm dòng truyện → chuyển tab Text, nạp nội dung, nhớ vị trí đọc (bookmark câu) nếu có.
- **Đổi tên:** nút **bút** (Edit) trên dòng truyện.
- **Chuyển thể loại:** icon chuyển file; hộp thoại chọn thể loại đích.
- **Xóa truyện:** icon thùng rác.
- **Thứ tự truyện:** kéo **⋮⋮** trên từng dòng truyện (tương tự thể loại).

### Thêm thể loại & import

- **Thêm thể loại:** từ **top bar** (tab Thư viện) hoặc từ drawer (nếu vẫn có mục tương ứng) — mở hộp thoại nhập tên thể loại mới.
- **Import thư mục:** top bar (tab Thư viện) — chọn **cây thư mục** qua SAF; app xin **quyền đọc lâu dài** cho URI cây. Mỗi **file** (đệ quy) trong thư mục có nội dung hợp lệ → **một truyện**; tên thể loại thường theo tên thư mục. Có **popup tiến trình** (số file, đường dẫn đang xử lý).

---

## Nhận nội dung từ app khác (Intent)

App đăng ký nhiều `intent-filter` (xem `AndroidManifest.xml`):

| Hành động | Hành vi tóm tắt |
|-----------|------------------|
| **Chia sẻ / Gửi** (`ACTION_SEND`) | Văn bản: lưu vào thư viện (có thể nhận URL để tải nội dung). File stream: đọc UTF-8 nếu là văn bản; không hỗ trợ ảnh/video làm nội dung chính. |
| **Mở bằng** (`ACTION_VIEW`) | File `.txt` / `text/plain` / `content`… — mở vào app. |
| **Chọn xử lý văn bản** (`PROCESS_TEXT`) | Nhận đoạn văn bản được chọn từ app khác. |

Sau khi xử lý, intent thường được **xoá** để tránh nhập trùng khi quay lại activity.

---

## Quyền & dữ liệu

- **Internet:** ElevenLabs, tải URL khi chia sẻ link.
- **WAKE_LOCK:** hỗ trợ phát TTS / MP3 khi màn hình tắt (không thay thế foreground service media đầy đủ).
- **Thông báo / Foreground service:** phục vụ xuất AAC và các tác vụ nền liên quan (theo manifest).
- **Bộ nhớ ngoài (tuỳ API):** ghi Downloads / Music cho xuất; đọc font (API cũ có thể cần `READ_EXTERNAL_STORAGE` tới 32).

Nội dung thư viện nằm trong **không gian lưu riêng của app** (external files / DB SQLite), không phải “thư mục Documents công khai” của từng truyện — trừ khi bạn **xuất** ra Downloads.

---

## Build & chạy

Yêu cầu **JDK 17** (Android Gradle Plugin; tránh dùng JDK 25 làm mặc định nếu build lỗi).

```bash
./gradlew :app:assembleDebug
```

Cài file APK debug sinh ra dưới `app/build/outputs/apk/debug/`.

---

## Ghi chú cho dev

- UI chính: `AppTabs.kt` → `AppModalNavigationDrawerScaffold.kt` (drawer + scaffold + tab), `TextInputTab.kt`, `LibraryTab.kt`.
- Thư viện / DB: `StoryLibraryRepository.kt`.
- Intent chia sẻ / VIEW: xử lý trong `MainActivity` / `AppTabs.kt` (consumer, coroutine, v.v.).

Nếu bạn muốn README thêm mục **FAQ**, **screenshot**, hoặc bản **English** song song, có thể bổ sung từng phần sau.
