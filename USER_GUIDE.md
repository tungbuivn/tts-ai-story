# Hướng dẫn sử dụng — TTS AI Story

Tài liệu này dành cho **người dùng cuối**: cách mở app, đọc truyện bằng giọng nói, quản lý thư viện và xuất file. Không cần biết lập trình.

---

## App này làm gì?

**TTS AI Story** giúp bạn:

- **Đọc văn bản thành tiếng** bằng giọng **máy Android** hoặc giọng **ElevenLabs** (cần Internet và tài khoản/API key).
- **Soạn, dán hoặc mở** nội dung trên màn hình **Text**.
- **Lưu truyện theo thể loại** trên màn hình **Thư viện** — thêm, đổi tên, sắp xếp, xóa, **nhập cả thư mục** từ máy, **xuất** ra thư mục Tải xuống khi cần.
- **Xuất file âm thanh** dạng **.m4a** (AAC) từ nội dung đang đọc (có thể hiện thông báo khi xuất).

---

## Màn hình chính

- **Hai tab ở giữa:** **Text** | **Thư viện** — chạm để chuyển.
- **Góc trên bên trái:** nút **☰** (ba gạch) — mở **menu bên** với đủ mục cài đặt và điều hướng.
- **Thanh trên bên phải:** tùy tab có thêm nút (ví dụ **bánh răng** cài đặt TTS khi đang ở tab Text; **thêm thể loại** / **nhập thư mục** khi ở tab Thư viện).
- **Sát đáy màn hình** (khi đang ở tab Text): thanh **điều hướng đoạn/câu** và số thứ tự đang đọc (ví dụ `3 / 120`).

---

## Menu bên (☰)

| Bạn chạm | Việc xảy ra |
|----------|-------------|
| **Mở file…** | Mở trình chọn file của Android — chọn **file văn bản** để đưa vào app (thường để đọc hoặc xử lý như mở file). |
| **Text** / **Thư viện** | Chuyển nhanh sang tab tương ứng. |
| **Cấu hình ElevenLabs** | Nhập **API key**, chọn giọng/tùy chọn ElevenLabs. Cần **mạng**. |
| **Cấu hình TTS hệ thống** | Chọn **giọng đọc**, **tốc độ**, **cao độ** của TTS có sẵn trên máy. |
| **Fonts** | Đổi **font chữ** vùng soạn trên tab Text (có thể chọn font từ máy nếu app hỗ trợ). |
| **Giới thiệu** | Xem tên app, **phiên bản**, mô tả ngắn; nút **Đóng** để thoát hộp thoại. |

---

## Tab **Text** — soạn và nghe đọc

### 1. Có nội dung để đọc

- Gõ hoặc **dán** văn bản vào vùng lớn ở giữa.
- Hoặc mở truyện từ **Thư viện** (chạm tên truyện) — nội dung sẽ hiện ở đây.
- Hoặc dùng **Mở file…** trong menu ☰.

### 2. Chọn giọng đọc

- Trên vùng Text có phần chọn **TTS hệ thống** hoặc **ElevenLabs** (tùy giao diện phiên bản bạn đang dùng).
- **Hệ thống:** không tốn API; dùng giọng đã cài trên máy (Google TTS, Samsung, v.v.).
- **ElevenLabs:** giọng chất lượng cao hơn; cần cấu hình trong menu ☰ và **Internet**.

### 3. Đọc và dừng

- Dùng nút **Phát** / **Dừng** (và các nút liên quan) trên **thanh công cụ** phía trên vùng soạn.
- Muốn chỉnh nhanh giọng/tốc độ: chạm **bánh răng** trên thanh trên (khi đang ở tab Text) — mở đúng màn hình cài đặt cho engine bạn đang chọn.

### 4. Đoạn / câu

- App có thể chia văn bản thành **đoạn** hoặc **câu** để đọc lần lượt.
- **Thanh dưới cùng:** nhảy **đoạn trước / sau**, **về đầu / về cuối**, xem **đang ở đoạn mấy / tổng bao nhiêu**.

### 5. Lưu khi sửa truyện từ thư viện

- Nếu bạn mở truyện từ Thư viện rồi sửa chữ trên tab Text, app thường **tự lưu** sau một lúc (không cần bấm Lưu thủ công trong hầu hết trường hợp).

### 6. Xuất file âm thanh (.m4a)

- Trên thanh công cụ tab Text có mục xuất **AAC / .m4a** (biểu tượng lưu âm thanh).
- Làm theo hộp thoại: chọn tên / xác nhận. Khi xuất lâu, có thể có **thông báo** trên thanh trạng thái — **đừng tắt app đột ngột** cho đến khi xong.

---

## Tab **Thư viện** — thể loại và truyện

### Thể loại (mỗi “ô” lớn là một thể loại)

- **Mở / thu gọn danh sách truyện:** chạm **tên thể loại** hoặc **mũi tên**.
- **Đọc hết thể loại:** dùng nút **phát cả thể loại** (nếu có) — app ghép nội dung các truyện theo thứ tự rồi chuyển sang tab Text để đọc TTS (khi có chữ để đọc).
- **Đổi thứ tự thể loại:** giữ biểu tượng **hai hàng chấm dọc** (⋮⋮) rồi **kéo lên/xuống**, thả tay.

**Menu ⋮ trên từng thể loại**

- **Đổi tên thể loại**
- **Xuất ra…** — đưa nội dung ra thư mục **Tải xuống** (thường có đường dẫn kiểu `Download/tts-ai-story/…`):
  - **Một file .txt** ghép tất cả truyện, hoặc
  - **Mỗi truyện một file** (tên dạng số thứ tự).
- **Đồng bộ thư mục** — chỉ có ý nghĩa nếu thể loại đó trước đó được tạo bằng **Nhập thư mục**: app **xóa hết truyện trong thể loại** rồi **nhập lại** từ **cùng một thư mục** bạn đã chọn lúc đầu. Bạn cần vẫn **cho phép app truy cập thư mục** (Android có thể hỏi lại).
- **Xóa thể loại** — mất hết truyện trong thể loại đó trong app (cân nhắc trước khi xóa).

### Truyện trong một thể loại

- **Mở đọc / sửa:** chạm dòng **tên truyện** → sang tab Text; app có thể **nhớ chỗ đang đọc** (bookmark).
- **Đổi tên:** biểu tượng **bút**.
- **Chuyển sang thể loại khác:** biểu tượng **chuyển** — chọn thể loại đích trong hộp thoại.
- **Xóa:** biểu tượng **thùng rác**.
- **Đổi thứ tự truyện:** kéo **⋮⋮** giống thể loại.

### Thêm thể loại mới

- Trên **thanh trên** khi đang ở tab Thư viện: chạm nút **thêm thể loại** (thường là dấu **+**) — nhập tên → xác nhận.

### Nhập cả thư mục từ máy

- Trên thanh trên tab Thư viện: chạm **Import thư mục** / **Nhập thư mục** (tùy nhãn hiển thị).
- Android mở màn hình **chọn thư mục** — bạn chọn **một thư mục** chứa file truyện.
- App sẽ xin **quyền đọc thư mục đó** (để sau này có thể **đồng bộ lại**).
- Mỗi **file văn bản** trong thư mục (kể cả trong thư mục con) có thể trở thành **một truyện**; tên thể loại thường theo **tên thư mục** bạn chọn.
- Trong lúc nhập có **cửa sổ tiến trình** (số file, đường dẫn đang xử lý) — chờ đến khi xong.

---

## Đưa chữ vào app từ app khác

- **Chia sẻ** một đoạn văn hoặc **liên kết** từ trình duyệt / ghi chú — chọn **TTS AI Story** trong danh sách chia sẻ. App sẽ xử lý (văn bản hoặc thử tải nội dung từ link, tùy loại dữ liệu).
- **Mở file .txt** từ ứng dụng Quản lý file — **Mở bằng** → chọn app này nếu Android gợi ý.
- Một số app hỗ trợ **“Xử lý văn bản đã chọn”** — bạn có thể gửi đoạn đang bôi đen sang TTS AI Story.

Nếu sau khi mở bạn **quay lại** app mà nội dung không bị nhập trùng, đó là do app đã xử lý xong lần chia sẻ trước (hành vi bình thường).

---

## Quyền và dữ liệu (ngắn gọn)

- **Internet:** bắt buộc nếu dùng ElevenLabs hoặc khi app cần tải nội dung từ liên kết.
- **Thông báo:** có thể được hỏi khi **xuất file âm thanh** chạy nền — nên **cho phép** để theo dõi tiến độ.
- Truyện trong Thư viện được lưu **trong app**; muốn có bản trên máy dễ tìm (Máy tính / Tải xuống), hãy dùng **Xuất ra…** hoặc xuất từ menu thể loại như trên.

---

## Gặp sự cố?

| Hiện tượng | Gợi ý |
|------------|--------|
| Không có tiếng | Kiểm tra **âm lượng máy**, đang chọn **TTS hệ thống** hay **ElevenLabs**, và trong menu ☰ mở **Cấu hình TTS** tương ứng xem đã chọn giọng chưa. |
| ElevenLabs không đọc | Cần **mạng**, **API key đúng**, còn hạn mức tài khoản ElevenLabs. |
| Import thư mục trống | Thư mục cần có **file văn bản** (txt, v.v.) mà app đọc được; thử thư mục khác hoặc kiểm tra định dạng file. |
| Đồng bộ thư mục báo lỗi / không chạy | Thư mục gốc có thể đã **đổi tên / di chuyển** hoặc Android **thu hồi quyền** — thử **Nhập thư mục** lại cho thể loại mới. |

---

## Tài liệu khác

- **README.md** trong cùng thư mục dự án: thông tin kỹ thuật ngắn và hướng dẫn **build** cho lập trình viên.

Nếu bạn cần bản **tiếng Anh** của hướng dẫn này, có thể nhân bản file và dịch theo cùng cấu trúc.
