# Học Từ Vựng 📚

App Android học từ vựng tiếng Anh cho người Việt — 300 từ chia 3 chủ đề:

| Chủ đề | Số từ |
|---|---|
| 💻 Công nghệ thông tin | 100 |
| 🏠 Đời sống hằng ngày | 100 |
| ⚙️ Máy móc – Cơ khí | 100 |

## Tính năng
- **Flashcard + lặp lại ngắt quãng (SM-2):** tự chấm Quên / Khó / Nhớ / Dễ, app tự lên lịch ôn.
- **Gợi nhớ chủ động:** xem từ tiếng Anh, nhớ nghĩa trước rồi mới lật thẻ.
- **Mỗi từ có:** emoji, phiên âm IPA, phát âm (Text-to-Speech), ví dụ + dịch, mẹo nhớ tiếng Việt.
- **Kiểm tra 4 dạng:** Anh→Việt, Việt→Anh, nghe chọn từ, điền chỗ trống. Câu sai tự quay lại hàng ôn.
- **Streak, mục tiêu từ mới/ngày, tiến độ theo chủ đề.**
- **Widget màn hình chính:** hiện từ ngẫu nhiên, đổi khi rời app, bấm “↻ Từ khác”, và tự đổi mỗi ~30 phút.

## Build
Yêu cầu: JDK 17+, Android SDK (compileSdk 37).

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Thêm / sửa từ vựng
File `app/src/main/assets/words_*.txt`, mỗi dòng một từ, 8 trường ngăn bởi `|`:

```
emoji|word|IPA|loại từ|nghĩa|ví dụ EN|ví dụ VI|mẹo nhớ
```
