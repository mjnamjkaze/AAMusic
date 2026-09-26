# DriveTune — Nghe nhạc YouTube trên Android Auto khi đang lái xe (miễn phí, phát nền, chặn quảng cáo)

**DriveTune** là app Android miễn phí để **nghe nhạc YouTube trên màn hình ô tô qua Android Auto**, kể cả **khi xe đang chạy**. Chọn bài bằng nút to trên màn hình xe hoặc **ra lệnh bằng giọng nói** ("Hey Google, phát Sơn Tùng trên DriveTune"). App **phát nền khi tắt màn hình**, **chặn quảng cáo**, tự bỏ qua đoạn tài trợ (SponsorBlock), và có sẵn danh sách **nhạc trẻ, nhạc Việt, nhạc Trung (Hoa ngữ), K-Pop, Âu Mỹ, Bolero, Remix, nhạc không lời, thiếu nhi**, cùng mục nghe **khoa học, lịch sử, sách nói, podcast, học tiếng Anh**.

<p align="center">
  <a href="https://github.com/mjnamjkaze/DriveTune/releases/latest"><b>⬇️ Tải DriveTune APK bản mới nhất</b></a>
</p>

- ✅ **Android Auto khi xe đang chạy**: dùng giao diện media chuẩn của Android Auto (Yêu thích · Danh sách · Vừa nghe · Tìm kiếm), chạm một lần là phát.
- 🎙️ **Tìm và phát bằng giọng nói**: nói tên bài, ca sĩ hoặc thể loại, app phát ngay bài đầu tiên và xếp các bài sau vào hàng chờ.
- 🎬 **Bật/tắt video**: bật để xem video như YouTube; tắt để chỉ nghe, màn hình hiện **ảnh nền dịu mắt** thay cho ảnh bìa, lại đỡ tốn 3G/4G.
- 🖼️ **10 ảnh nền HD, 10 chủ đề**: oải hương, biển, hoa anh đào, đồi chè, rừng thu, núi tuyết, đồng hoa, đáy biển, phố đêm, ngân hà. Màu tươi mà không chói.
- 🔎 **Tìm bài rồi thêm vào hàng chờ**: bấm + ở bài muốn nghe, kéo xuống là hiện thêm kết quả; trên Android Auto có bàn phím to và nút nói.
- 🔔 **Tự báo có bản mới**: cập nhật ngay trong Cài đặt, không phải lên GitHub tải tay.
- 🔇 **Không quảng cáo**: chặn từ gốc, bài vào luôn không phải chờ.
- 📱 **Phát nền, tắt màn hình vẫn nghe**, có bong bóng nổi để điều khiển trên Google Maps.
- 🚗 **Chế độ lái**: nút to, nền tối, nút vô lăng chuyển bài được.
- ⏯️ **Nghe tiếp** đúng chỗ đang dở, lưu **Yêu thích**, **Hàng chờ**.

> Từ khoá: nghe nhạc youtube trên android auto, youtube android auto, app nghe nhạc ô tô, nghe nhạc khi lái xe, youtube phát nền, youtube không quảng cáo, nhạc cho xe hơi, android auto music app, youtube music android auto apk.

## Cài đặt nhanh (3 bước)

1. **Tải APK** ở trang [Releases](https://github.com/mjnamjkaze/DriveTune/releases/latest) rồi cài lên điện thoại (Android 15 trở lên). Khi máy hỏi thì cho phép *cài từ nguồn không xác định*.
2. Mở DriveTune một lần trên điện thoại và cấp các quyền **Thông báo**, **Micro** (để tìm bằng giọng nói) và **Hiển thị trên ứng dụng khác** (cho bong bóng nổi).
3. Trong app **Android Auto** trên điện thoại, bấm ~10 lần vào dòng *Phiên bản* để bật chế độ nhà phát triển. Sau đó vào **⋮ → Developer settings** và bật **Unknown sources**. Cắm điện thoại vào xe là thấy icon DriveTune. Chi tiết xem [Sideload lên Android Auto](#sideload-lên-android-auto-từng-bước).

## Hỏi đáp

**Chạm bài trên xe mà màn hình cứ báo "Đang tải dữ liệu...", không phát?**
Lỗi này có ở bản 2.3.0 và đã sửa ở **2.4.0**. "Đang tải dữ liệu..." là màn hình chờ của Android Auto: xe đợi app báo "đang phát", nhưng app cũ không bao giờ báo, vì ba lỗi:
- app cố mở màn hình điện thoại từ nền để phát, và Android chặn việc này;
- phiên media bị huỷ khi thoát app;
- chạm vào danh mục chỉ mở trang kết quả tìm kiếm, không phát bài nào.

Từ 2.4.0, app phát được **mà không cần mở trên điện thoại**. Có lỗi thì xe hiện thông báo rõ ràng thay vì chờ mãi.

**Xe đang chạy có phát nhạc được không?** Được. Khi xe chạy, Android Auto chỉ cho dùng giao diện media chuẩn và khoá bàn phím. Giao diện này DriveTune hỗ trợ đầy đủ: chạm để chọn bài, bấm 🎙️ để tìm bằng giọng nói.

**Nói xong mà không tìm gì?** Đã sửa ở 2.4.0: câu nói được nhận ngay trong app, không qua hộp thoại riêng (hộp thoại này hay không hiện được trên màn hình xe), rồi phát bài đầu tiên luôn.

**Đổi ảnh nền màn phát?** Cài đặt (chạm logo) → **Hình nền** → chạm một trong 10 ảnh: Đồng oải hương · Biển nhiệt đới · Hoa anh đào · Đồi chè · Rừng thu · Núi tuyết · Đồng hoa · Đáy biển · Phố đêm · Dải ngân hà. Ảnh có sẵn trong app, không tải qua mạng.

**Xếp nhiều bài để nghe lần lượt?** Bấm nút **thư viện** cạnh ô tìm (hoặc nút Hàng chờ trong Chế độ lái) → mục **Tìm** → gõ (hoặc bấm 🎙️ nói) tên bài → bấm **+** ở từng bài. Kéo xuống cuối là tải thêm kết quả. Chạm vào bài là phát ngay. Trên màn hình Android Auto, ô tìm dùng bàn phím to của app thay cho bàn phím hệ thống (vốn hiện bé tí, không gõ được).

**Cập nhật bản mới thế nào?** Có bản mới trên GitHub thì lúc mở app sẽ có một dòng thông báo. Vào Cài đặt → **Cập nhật lên bản …** (ngay trên dòng Ủng hộ tác giả), app tự tải rồi mở màn cài đặt. Lần đầu Android sẽ hỏi cho phép DriveTune cài ứng dụng, bấm cho phép.

**Ủng hộ tác giả?** DriveTune miễn phí. Thấy hay thì vào Cài đặt → **Ủng hộ tác giả** để quét mã VietQR (BIDV · VO HAI NAM · 2152709353). Cảm ơn bạn ☕

**Tốn dữ liệu di động không?** Tắt *Hiện video* và bật *Tiết kiệm dữ liệu* trong Cài đặt (chạm logo). Khi đó mất khoảng 90 MB mỗi giờ.

---

## Thông tin kỹ thuật

Ứng dụng Android bọc WebView phát **YouTube / YouTube Music dạng audio-first**, làm riêng cho lúc lái xe — **Chế độ lái** nút to chữ rõ, thư viện Yêu thích / Hàng chờ / Danh sách dựng sẵn, nghe tiếp từ chỗ đang dở, lệnh nói, chạy nhạc nền, chặn quảng cáo, và **chạy được trên Android Auto** (cả giao diện media chuẩn lẫn chiếu màn hình).

- Package: `com.gsvn.aamusic`
- minSdk 35 · targetSdk 36 · versionName 2.7.0
- Ngôn ngữ: Kotlin + WebView (không dùng thư viện player riêng)

> ⚠️ Dự án mang tính học tập / cá nhân. Việc bọc YouTube trong WebView, chặn quảng cáo và giả dạng app điều hướng để lên Android Auto có thể vi phạm ToS của YouTube/Google. Tự chịu trách nhiệm khi dùng.

---

## Mục lục

1. [Kiến trúc tổng quan](#kiến-trúc-tổng-quan)
2. [Các kỹ thuật cốt lõi](#các-kỹ-thuật-cốt-lõi)
   - [1. Bật/tắt video – ảnh nền](#1-bậttắt-video--ảnh-nền)
   - [2. Phát nền khi tắt màn hình / chuyển app (spoof visibility)](#2-phát-nền-khi-tắt-màn-hình--chuyển-app-spoof-visibility)
   - [3. Foreground service + WakeLock + AudioFocus](#3-foreground-service--wakelock--audiofocus)
   - [4. MediaSession + nút chuyển bài trên vô lăng](#4-mediasession--nút-chuyển-bài-trên-vô-lăng)
   - [5. Bong bóng nổi điều khiển (chat-head)](#5-bong-bóng-nổi-điều-khiển-chat-head)
   - [6. PlayerController – điều khiển qua JS injection](#6-playercontroller--điều-khiển-qua-js-injection)
   - [7. Chặn quảng cáo 3 lớp (mạng + dữ liệu + CSS/JS)](#7-chặn-quảng-cáo-3-lớp-mạng--dữ-liệu--cssjs)
   - [8. SponsorBlock](#8-sponsorblock)
   - [9. Khoá ô tìm kiếm YouTube + bàn phím native](#9-khoá-ô-tìm-kiếm-youtube--bàn-phím-native)
   - [10. Chống tự dừng + ép chất lượng thấp](#10-chống-tự-dừng--ép-chất-lượng-thấp)
   - [11. Tìm bằng giọng nói](#11-tìm-bằng-giọng-nói)
   - [12. Chặn preview tự phát trong danh sách](#12-chặn-preview-tự-phát-trong-danh-sách)
   - [13. Chế độ lái](#13-chế-độ-lái)
   - [14. Thư viện: yêu thích, hàng chờ, vừa nghe](#14-thư-viện-yêu-thích-hàng-chờ-vừa-nghe)
   - [15. Danh sách dựng sẵn theo bối cảnh lái](#15-danh-sách-dựng-sẵn-theo-bối-cảnh-lái)
   - [16. Nghe tiếp từ chỗ đang dở](#16-nghe-tiếp-từ-chỗ-đang-dở)
   - [17. Nối/ngắt dàn âm thanh của xe](#17-nốingắt-dàn-âm-thanh-của-xe)
   - [18. Lệnh nói](#18-lệnh-nói)
3. [Bí quyết lên được Android Auto](#bí-quyết-lên-được-android-auto)
4. [Sideload lên Android Auto (từng bước)](#sideload-lên-android-auto-từng-bước)
5. [Build](#build)

---

## Kiến trúc tổng quan

App chỉ là một `WebView` full-screen trỏ vào YouTube Music, cộng thêm các lớp native bao quanh để biến trải nghiệm "xem video" thành "nghe nhạc":

| File | Vai trò |
|---|---|
| [MainActivity.kt](app/src/main/java/com/gsvn/aamusic/MainActivity.kt) | Host WebView, AudioFocus, fullscreen, thu nhỏ bubble |
| [BackgroundPlayWebView.kt](app/src/main/java/com/gsvn/aamusic/web/BackgroundPlayWebView.kt) | WebView không tự pause khi mất visibility |
| [ConfiguredWebView.kt](app/src/main/java/com/gsvn/aamusic/web/ConfiguredWebView.kt) | Cấu hình WebView + inject JS spoof visibility, khoá search |
| [PlayerController.kt](app/src/main/java/com/gsvn/aamusic/player/PlayerController.kt) | Cầu nối điều khiển play/next/prev qua JS |
| [BackgroundPlaybackService.kt](app/src/main/java/com/gsvn/aamusic/BackgroundPlaybackService.kt) | Foreground service, WakeLock, notification, bubble nổi |
| [AdBlocker.kt](app/src/main/java/com/gsvn/aamusic/web/AdBlocker.kt) | Chặn quảng cáo cấp mạng + cắt dữ liệu ad + CSS/JS |
| [SponsorBlock.kt](app/src/main/java/com/gsvn/aamusic/web/SponsorBlock.kt) | Bỏ qua đoạn sponsor/intro/outro |
| [PlaybackGuard.kt](app/src/main/java/com/gsvn/aamusic/web/PlaybackGuard.kt) | Tự phát lại khi trang tự dừng + ép chất lượng thấp |
| [MediaSessionHolder.kt](app/src/main/java/com/gsvn/aamusic/player/MediaSessionHolder.kt) | Phiên media dùng chung — nhận nút chuyển bài trên vô lăng |
| [DriveMode.kt](app/src/main/java/com/gsvn/aamusic/ui/DriveMode.kt) | Lớp phủ Chế độ lái (nút to, nền tối, ảnh bìa) |
| [LibrarySheet.kt](app/src/main/java/com/gsvn/aamusic/ui/LibrarySheet.kt) | Bảng Hàng chờ · Yêu thích · Vừa nghe · Danh sách |
| [ResumeSheet.kt](app/src/main/java/com/gsvn/aamusic/ui/ResumeSheet.kt) | Hỏi "nghe tiếp?" khi mở lại app |
| [DriveLibrary.kt](app/src/main/java/com/gsvn/aamusic/data/DriveLibrary.kt) | Yêu thích / vừa nghe / hàng chờ / điểm nghe tiếp |
| [DrivePlaylists.kt](app/src/main/java/com/gsvn/aamusic/data/DrivePlaylists.kt) | 29 danh sách dựng sẵn: lái xe, thể loại, nghe & học |
| [DriveSettings.kt](app/src/main/java/com/gsvn/aamusic/data/DriveSettings.kt) | Tuỳ chọn người dùng |
| [ArtworkCache.kt](app/src/main/java/com/gsvn/aamusic/player/ArtworkCache.kt) | Tải + nhớ tạm ảnh bìa cho phiên media/notification |
| [CarConnection.kt](app/src/main/java/com/gsvn/aamusic/car/CarConnection.kt) | Nhận biết nối/ngắt dàn âm thanh của xe |
| [DriveBrowserService.kt](app/src/main/java/com/gsvn/aamusic/car/DriveBrowserService.kt) | Cây duyệt media + ô tìm kiếm cho Android Auto |
| [CarPlayback.kt](app/src/main/java/com/gsvn/aamusic/car/CarPlayback.kt) | Mọi lệnh phát từ xe: chọn bài, danh sách, tìm kiếm, giọng nói |
| [PlaybackHost.kt](app/src/main/java/com/gsvn/aamusic/player/PlaybackHost.kt) | Luôn có WebView để phát (dựng ngầm khi app chưa mở) + nhịp poll chung |
| [YouTubeSearch.kt](app/src/main/java/com/gsvn/aamusic/data/YouTubeSearch.kt) | Tìm bài native (InnerTube) cho Android Auto và lệnh nói |
| [ArtworkProvider.kt](app/src/main/java/com/gsvn/aamusic/player/ArtworkProvider.kt) | Ảnh bìa `content://` cho màn hình xe |
| [VideoMode.kt](app/src/main/java/com/gsvn/aamusic/web/VideoMode.kt) | Bật/tắt video, ảnh nền màn phát |
| [VoiceListener.kt](app/src/main/java/com/gsvn/aamusic/voice/VoiceListener.kt) | Nghe giọng nói ngay trong app (SpeechRecognizer) |
| [VoiceCommands.kt](app/src/main/java/com/gsvn/aamusic/voice/VoiceCommands.kt) | Hiểu câu nói thành thao tác |
| [SettingsSheet.kt](app/src/main/java/com/gsvn/aamusic/ui/SettingsSheet.kt) | Cài đặt + giới thiệu |
| [AndroidManifest.xml](app/src/main/AndroidManifest.xml) | Khai báo intent-filter giả dạng app điều hướng cho Android Auto |

---

## Các kỹ thuật cốt lõi

### 1. Bật/tắt video – ảnh nền

App **không** dùng player audio riêng; nó vẫn phát thẻ `<video>` của YouTube. Công tắc **Hiện video** trong Cài đặt ([VideoMode](app/src/main/java/com/gsvn/aamusic/web/VideoMode.kt)) quyết định cách hiển thị:

- **Tắt** (mặc định): gắn class `yta-audio-only` lên `<html>`. Class này **ẩn bề mặt video bằng CSS** (`visibility: hidden`, không dùng `display: none`, để video vẫn decode và phát tiếng) và phủ kín khung phát bằng **ảnh nền** người dùng chọn — không hiện ảnh bìa bài hát, để màn hình yên, không đổi màu theo từng bài. Luồng hình bị ép xuống 144p nếu bật *Tiết kiệm dữ liệu*.
- **Bật**: hiện video như YouTube và bỏ ép chất lượng.

Đổi công tắc chỉ đổi một class CSS, không nạp lại trang, nên nhạc không bị ngắt. Lớp ảnh nền được chèn ngay sau khung video trong `#movie_player`, không đặt `z-index`, và có `pointer-events: none`. Vì vậy nút điều khiển của trình phát vẫn nằm trên và vẫn bấm được.

[PlayerBackgrounds](app/src/main/java/com/gsvn/aamusic/data/PlayerBackgrounds.kt): 10 ảnh JPEG 1920×1080 (~60–250 KB mỗi ảnh) trong `res/drawable-nodpi`, mỗi ảnh một chủ đề, vẽ phẳng, màu tươi nhưng không chói. Trang nạp ảnh qua đường dẫn giả `<origin>/__drivetune/bg/<id>.jpg`; `shouldInterceptRequest` trả thẳng từ tài nguyên của app, cùng origin nên không vướng CSP, không nhân đôi tệp. Ô ảnh lớn của Chế độ lái dùng cùng ảnh đó.

### 2. Phát nền khi tắt màn hình / chuyển app (spoof visibility)

YouTube web tự pause khi phát hiện tab bị ẩn. App đánh lừa bằng **2 lớp**:

**Lớp native** — [BackgroundPlayWebView.kt](app/src/main/java/com/gsvn/aamusic/web/BackgroundPlayWebView.kt#L26): ghi đè `onWindowVisibilityChanged` để **luôn báo `VISIBLE`**, chặn WebView tự pause media khi app xuống nền/tắt màn hình.

**Lớp JS** — [ConfiguredWebView.DOCUMENT_START_JS](app/src/main/java/com/gsvn/aamusic/web/ConfiguredWebView.kt#L296) (inject trước mọi trang):

```js
Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
Object.defineProperty(document, 'hidden',          { get: () => false });
document.hasFocus = () => true;
// nuốt các event khiến YT pause:
['visibilitychange','blur','focusout','pagehide'].forEach(...stopImmediatePropagation);
```

→ YouTube tưởng người dùng vẫn đang xem nên phát tiếp.

### 3. Foreground service + WakeLock + AudioFocus

- [BackgroundPlaybackService.kt](app/src/main/java/com/gsvn/aamusic/BackgroundPlaybackService.kt) là **foreground service** loại `mediaPlayback`, giữ tiến trình sống để nhạc không bị hệ thống kill.
- `PARTIAL_WAKE_LOCK` (tối đa 4 giờ) giữ CPU chạy khi màn hình tắt — [acquireWakeLock](app/src/main/java/com/gsvn/aamusic/BackgroundPlaybackService.kt#L418).
- [MainActivity](app/src/main/java/com/gsvn/aamusic/MainActivity.kt#L185) yêu cầu `AUDIOFOCUS_GAIN` và **không** abandon focus khi xuống nền → nhạc tiếp tục.
- `mediaPlaybackRequiresUserGesture = false` để không bị chặn autoplay.

### 4. MediaSession + nút chuyển bài trên vô lăng

`MediaSessionCompat` + `MediaStyle` notification hiển thị **icon/tên app của chính mình** thay vì artwork YouTube, kèm nút Prev/Play/Pause/Next/Stop.

Phiên media nằm ở [MediaSessionHolder](app/src/main/java/com/gsvn/aamusic/player/MediaSessionHolder.kt) chứ không nằm trong service. Lý do: hệ thống định tuyến phím media tới **phiên đang hoạt động**, mà service nền chỉ chạy khi app xuống nền — đúng lúc app hiện trên màn hình xe thì không có phiên nào nhận, bấm vô lăng không ăn gì. Giữ phiên sống suốt vòng đời app thì cả hai trường hợp đều nhận được; service chỉ mượn token để dựng notification.

Ba đường vào, phủ các kiểu head unit khác nhau:

| Đường | Bắt ở đâu |
|---|---|
| Transport control (chuẩn) | `MediaSessionCompat.Callback.onSkipToNext/Previous` |
| Intent `ACTION_MEDIA_BUTTON` | `onMediaButtonEvent` — map thêm `FAST_FORWARD`/`REWIND` thành chuyển bài |
| Phím cứng gửi thẳng tới cửa sổ | `MainActivity.dispatchKeyEvent` — bắt trước khi WebView nuốt mất |

Phiên luôn được `setPlaybackState` ngay từ lúc tạo; thiếu bước này hệ thống coi là phiên chưa phát và không định tuyến phím tới.

### 5. Bong bóng nổi điều khiển (chat-head)

Dùng `TYPE_APPLICATION_OVERLAY` + `SYSTEM_ALERT_WINDOW` để vẽ bong bóng nổi trên các app khác (vd Google Maps). Bong bóng **không giành focus** (`FLAG_NOT_FOCUSABLE`) nên app bên dưới vẫn dùng bình thường. Kéo-thả xuống vùng đáy để đóng kiểu Messenger. Xem [showBubble / DragTouchListener](app/src/main/java/com/gsvn/aamusic/BackgroundPlaybackService.kt#L125).

### 6. PlayerController – điều khiển qua JS injection

Không ghép cứng với MediaSession của YouTube. [PlayerController](app/src/main/java/com/gsvn/aamusic/player/PlayerController.kt) điều khiển bằng cách **inject JS click vào nút trên thanh player** của YT Music / YouTube, đồng thời poll tiêu đề + trạng thái đang phát để cập nhật bubble/notification:

```js
document.querySelector('ytmusic-player-bar #play-pause-button, ...').click();
```

### 7. Chặn quảng cáo 3 lớp (mạng + dữ liệu + CSS/JS)

**Lớp mạng** — [AdBlocker.shouldBlock](app/src/main/java/com/gsvn/aamusic/web/AdBlocker.kt): trong `shouldInterceptRequest`, trả về response rỗng cho các domain quảng cáo (`doubleclick.net`, `googlesyndication.com`, `imasdk.googleapis.com`, …) và path ad của YouTube (`/pagead/`, `/get_video_ads`, `/youtubei/v1/player/ad`, …). Stream audio (`googlevideo.com`) **không** bị chặn trừ khi có marker ad rõ ràng.

**Lớp dữ liệu** — [AdBlocker.EARLY_JS](app/src/main/java/com/gsvn/aamusic/web/AdBlocker.kt): đây mới là lớp *bỏ qua được luôn* thay vì chỉ tắt tiếng rồi ngồi đợi — cách Brave/uBlock vẫn làm. Trang **tự mô tả quảng cáo trong chính JSON của trình phát**:

```jsonc
{ "adPlacements": [...], "playerAds": [...], "playerConfig": { "adConfig": {...} } }
```

Xoá các khoá đó trước khi trang đọc thì trình phát **không có gì để chèn** — quảng cáo biến mất chứ không phải bị tua qua. Phải bắt cả ba đường dữ liệu vào:

| Đường | Cách vá |
|---|---|
| `ytInitialPlayerResponse` (nhúng trong HTML lần tải đầu) | `Object.defineProperty` setter, scrub khi trang gán |
| `fetch('/youtubei/v1/player')` (bài sau, điều hướng kiểu SPA) | bọc `window.fetch`, dựng lại `Response` đã cắt |
| `XMLHttpRequest` | định nghĩa lại getter `responseText`/`response` ngay từ `open()` |

Script này **bắt buộc chạy ở document-start** (`WebViewCompat.addDocumentStartJavaScript`). Chạy ở `onPageFinished` là muộn: biến đã được gán và trang đã giữ tham chiếu `fetch` gốc.

Getter XHR định nghĩa từ `open()` chứ không đăng ký listener trong `send()`, để không phụ thuộc thứ tự đăng ký handler của trang; gặp `responseType` khác `''`/`'text'` thì trả nguyên bản thay vì làm hỏng request.

**Lớp CSS/JS** — [MUSIC_ADBLOCK_JS](app/src/main/java/com/gsvn/aamusic/web/AdBlocker.kt): dọn nốt phần lọt lưới. Ẩn banner upsell/Premium; đang dính ad video thì **tự bấm nút Skip**, không có nút thì **tua thẳng tới `duration`** để trình phát coi như xem xong và vào bài ngay (kèm `playbackRate = 16` + mute), sau đó khôi phục. Nhận diện ad ưu tiên `movie_player.getAdState()`, dự phòng bằng class `.ad-showing`; thao tác luôn nhắm đúng `<video>` của trình phát chứ không phải thẻ `<video>` đầu tiên trên trang. Có theo dõi ý định mute của user để không ghi đè.

### 8. SponsorBlock

[SponsorBlock.SKIP_JS](app/src/main/java/com/gsvn/aamusic/web/SponsorBlock.kt#L14) gọi API cộng đồng `sponsor.ajay.app` để lấy các đoạn `sponsor/intro/outro/selfpromo/music_offtopic/...` rồi tự tua qua. Đây là đoạn do người đăng chèn (khác quảng cáo YouTube).

### 9. Khoá ô tìm kiếm YouTube + bàn phím native

[YT_SEARCH_LOCKDOWN_JS](app/src/main/java/com/gsvn/aamusic/web/ConfiguredWebView.kt#L224) ẩn ô search mặc định của YouTube và chuyển focus về ô search native của app (phù hợp bàn phím xe/head unit). `ImeKeyboardBridge` là cầu JS→native để bật/tắt bàn phím theo ý app.

### 10. Chống tự dừng + ép chất lượng thấp

YouTube dừng nhạc vì đủ thứ lý do ngoài ý người dùng: hộp thoại *"còn xem không?"*, mất mạng chốc lát, trang giành/nhả luồng audio, quảng cáo chèn hỏng. Nhạc thường chạy nền hoặc tắt màn hình nên không ai bấm, tiếng cứ thế lặng đi. [PlaybackGuard.AUTO_RESUME_JS](app/src/main/java/com/gsvn/aamusic/web/PlaybackGuard.kt) chạy watchdog mỗi giây, tự bấm nút xác nhận rồi `playVideo()` + `video.play()`.

Mấu chốt là **không được cướp nút tạm dừng của người dùng**, nên watchdog phải phân loại cú dừng. Bản trước coi *mọi* thao tác chạm trong 1,5 giây trước đó là do người dùng — cuộn danh sách hay chạm nhầm màn hình đúng lúc trang tự dừng là nhạc tắt luôn, vì cờ `__ytaWantPlay` một khi hạ xuống thì không có gì nâng lại. Giờ chỉ tính là chủ động khi cú chạm rơi **đúng vào nút phát/tạm dừng** (hit-test bằng `Element.closest`) hoặc phím tắt `k`/`space` của YouTube:

| Nguồn dừng | Dấu hiệu | Xử lý |
|---|---|---|
| Người dùng bấm nút trên trang | `closest()` khớp selector nút play/pause, hoặc phím `k`/`space` | tôn trọng, hạ cờ `__ytaWantPlay` |
| Nút của app (bong bóng, notification, vô lăng) | `PlayerController` tự đặt cờ | tôn trọng |
| Cuộn / chạm chỗ khác / không thao tác gì | không khớp nút nào | **phát tiếp** |

Hai lớp hồi phục nữa cho các ca "tiếng tắt mà trang không báo pause":

- **Kẹt buffering**: `currentTime` đứng yên quá 15 giây trong khi `paused === false` → tua lùi 0,5 giây để ép nạp lại luồng.
- **Hết bài mà trang không tự chuyển**: sau 5 giây vẫn `ended` → bấm hộ nút bài kế.

Phía native, [MainActivity](app/src/main/java/com/gsvn/aamusic/MainActivity.kt) giành lại `AUDIOFOCUS_GAIN` (sau chỉ đường Maps, cuộc gọi…) thì gọi `PlayerController.resume()` — bản `resume()` này **bỏ qua nếu người dùng đang chủ động tạm dừng**, khác với `play()` của nút play rõ ràng.

Kèm theo là chuyện data: ẩn video bằng CSS **không cắt được byte nào** — trang vẫn tải luồng hình song song với audio và tự chọn độ phân giải theo khung hình. `LOW_QUALITY_JS` ép `setPlaybackQualityRange('tiny','tiny')` định kỳ (trình phát hay tự nâng lại khi mạng khoẻ hoặc chuyển bài). Ước lượng thô:

| | MB/phút | MB/giờ |
|---|---|---|
| Audio 128kbps | ~0,9 | ~56 |
| + video 144p | ~1,5 | ~90 |
| + video 720p | ~12–20 | ~700–1200 |

**Về chuyện "cache lại cho đỡ tốn data lần sau":** app chỉ cache được **ảnh bìa** ([ArtworkCache](app/src/main/java/com/gsvn/aamusic/player/ArtworkCache.kt) giữ trên đĩa, trần 6 MB, dọn theo kiểu ít dùng nhất). Luồng nhạc thì không — địa chỉ `googlevideo.com` của nó có chữ ký kèm tham số `expire=`, mỗi phiên một khác và hết hạn sau vài giờ, nên cache HTTP không bao giờ trúng lại cho cùng một bài ở lần mở sau. Còn tải hẳn về máy để nghe offline thì đã là chuyện khác: dự án **cố ý bỏ** tính năng đó ở [623888c](../../commit/623888c) và không đưa lại.

### 11. Tìm bằng giọng nói

Lái xe thì không gõ được, nên giọng nói là đường nhập chính. Từ 2.4.0 app nghe **trực tiếp bằng `SpeechRecognizer`** ([VoiceListener](app/src/main/java/com/gsvn/aamusic/voice/VoiceListener.kt), `vi-VN`) chứ không mở hộp thoại `RecognizerIntent`. Hộp thoại đó hay không hiện được trên màn hình xe, nên nói xong không có kết quả nào trả về. Manifest phải khai `<queries>` cho `android.speech.RecognitionService`, nếu không Android 11+ ẩn dịch vụ nhận dạng. Nói xong, app **tìm và phát luôn bài đầu tiên** ([YouTubeSearch](app/src/main/java/com/gsvn/aamusic/data/YouTubeSearch.kt)) và xếp các bài còn lại vào hàng chờ. Máy không có dịch vụ nhận dạng thì mới dùng hộp thoại cũ. Các chỗ gọi được:

| Chỗ | Khi nào dùng |
|---|---|
| Nút mic trên thanh tìm kiếm | app đang mở |
| Nút mic trên **bong bóng nổi** | đang mở Maps, app ở nền — mở app rồi bật luôn hộp thoại đọc |
| Phím 🎙 trên **bàn phím xe** | đang ở màn hình Android Auto (chiếu giao diện app) |
| 🎙️ / "Hey Google, phát … trên DriveTune" trong **giao diện media của Android Auto** | lúc xe đang chạy — đi qua `onPlayFromSearch`/`onSearch` |

Hai chỗ sau đi qua `PlayerController.openVoiceSearch()` → intent `EXTRA_START_VOICE` → `MainActivity` mở hộp thoại ngay khi lên foreground. Máy không có trình nhận dạng thì báo Toast chứ không crash.


### 12. Chặn preview tự phát trong danh sách

Mở trang chủ hay trang kết quả là YouTube tự chạy video xem thử ngay trên thumbnail. Với app nghe nhạc trên xe thì đó thuần tuý là hại: tốn data, chen tiếng vào bài đang nghe, và thẻ `<video>` của ô preview thường **đứng trước** thẻ của trình phát chính trong DOM — mọi chỗ trong app dùng `document.querySelector('video')` sẽ điều khiển nhầm ô preview (bấm nút vô lăng mà bài đang nghe không nhúc nhích).

Không nhờ WebView chặn hộ được: app phải để `mediaPlaybackRequiresUserGesture = false` thì bài nhạc mới chạy tiếp lúc tắt màn hình, mà cờ đó mở đường cho luôn preview. Nên chặn bằng JS, ở **document-start** — vá `HTMLMediaElement.play` *trước* script của trang, preview bị từ chối ngay lần gọi đầu nên không kịp ra tiếng cũng không kịp nạp luồng:

```js
HTMLMediaElement.prototype.play = function() {
    if (isPreview(this)) {
        block(this);
        // Đúng lỗi trình duyệt ném ra khi chặn autoplay — trang có sẵn
        // nhánh xử lý nên giao diện không vỡ.
        return Promise.reject(new DOMException('preview blocked', 'NotAllowedError'));
    }
    return origPlay.apply(this, arguments);
};
```

[PreviewGuard](app/src/main/java/com/gsvn/aamusic/web/PreviewGuard.kt) phân loại thế này — quan trọng nhất là **fail-open**: không nhận ra trình phát chính thì không chặn gì cả. YouTube đổi markup thì cùng lắm preview quay lại (khó chịu), chứ không bao giờ được phép thành câm tiếng cả app.

| Thẻ `<video>` | Kết luận |
|---|---|
| Nằm trong ô danh sách (`ytm-video-with-context-renderer`, `ytd-rich-item-renderer`, `yt-lockup-view-model`…) | preview → chặn |
| Nằm trong trình phát chính (`#movie_player`, `ytm-watch`, `ytmusic-player`…) *và không* nằm trong ô danh sách | trình phát chính → cho chạy |
| Lạc loài, mà **đã nhận ra** trình phát chính ở chỗ khác | thứ thừa → chặn |
| Lạc loài, và **không** nhận ra trình phát chính | không đủ căn cứ → cho chạy |

Ba lớp bắt, vì preview còn chạy được bằng thuộc tính `autoplay` chứ không chỉ qua `play()`: vá `play()` → nghe sự kiện `play`/`playing` ở capture phase → quét mỗi giây cho thẻ nào gọi `play()` lúc còn chưa gắn vào DOM.

Lớp này cũng công bố định nghĩa dùng chung `window.__ytaMainVideo()`; [PlaybackGuard](app/src/main/java/com/gsvn/aamusic/web/PlaybackGuard.kt), [AdBlocker](app/src/main/java/com/gsvn/aamusic/web/AdBlocker.kt) và [PlayerController](app/src/main/java/com/gsvn/aamusic/player/PlayerController.kt) bám vào đó thay vì bốc thẻ `<video>` đầu tiên của trang.

### 13. Chế độ lái

Lớp phủ toàn màn hình, bật bằng nút hình ô tô trên thanh tìm kiếm (hoặc mở sẵn lúc khởi động nếu bật trong Cài đặt).

```
┌─────────────────────────────┐
│  🚗 DRIVETUNE          ⤢    │
│                             │
│         [ Ảnh bìa ]         │
│                             │
│        Tên bài hát          │
│           Kênh              │
│   1:02 ▰▰▰▰▱▱▱▱▱▱ 3:45      │
│                             │
│    ◀◀      ▶      ▶▶        │
│                             │
│      ♡     ☰     🔊         │
└─────────────────────────────┘
```

Vài điểm đáng nói:

- **Không phải activity/fragment riêng** — chỉ là một `<include>` trong `activity_main.xml` được bật/tắt bằng `visibility`. WebView bên dưới không hề bị đụng tới nên nhạc không gợn một nhịp khi vào/ra, và app vẫn chỉ có đúng một màn hình như trước.
- **Vùng chạm 88–116dp** (gấp ~2 lần mức 48dp thông thường), khai báo ở `drive_*` trong [dimens.xml](app/src/main/res/values/dimens.xml).
- **Màu cố định, không theo day/night**: trong xe thì nền phải tối và chữ phải tương phản cao kể cả khi hệ thống đang ở chế độ sáng. Tỉ lệ tương phản trên nền `#0B0E14`: chữ chính 17:1, chữ mờ 7.4:1, cam nhấn 8.2:1.
- **Có bản riêng cho màn hình ngang** ([layout-land](app/src/main/res/layout-land/view_drive_mode.xml)) — chính là hình dạng màn hình xe (800×480): ảnh bìa sang trái, chữ và nút sang phải để nút không bị bóp dẹp.
- Activity khai báo `configChanges` cho cả `orientation` (để xoay máy không nạp lại trang), nên hệ thống **không** tự lấy lại layout theo hướng mới — `MainActivity.onConfigurationChanged` tự dựng lại riêng lớp phủ này.
- Nút 🔊 gọi thẳng thanh âm lượng của hệ thống (`adjustStreamVolume(..., FLAG_SHOW_UI)`) thay vì tự vẽ: thanh quen thuộc của máy dễ dùng hơn khi đang lái, lại chỉnh đúng luồng đang phát qua dàn của xe.

### 14. Thư viện: yêu thích, hàng chờ, vừa nghe

Bảng thư viện ([LibrarySheet](app/src/main/java/com/gsvn/aamusic/ui/LibrarySheet.kt)) có mục **Tìm**: gọi `YouTubeSearch.searchPage` ([YouTubeSearch](app/src/main/java/com/gsvn/aamusic/data/YouTubeSearch.kt)), mỗi kết quả có nút + để `DriveLibrary.enqueue`, chạm hàng thì phát ngay. Kéo gần đáy thì xin trang sau bằng mã `continuationItemRenderer…continuationCommand.token` của `youtubei/v1/search` và nối vào cuối danh sách. Trên màn hình xe, ô tìm tắt bàn phím hệ thống (`showSoftInputOnFocus = false`) và hiện [CarKeyboard](app/src/main/java/com/gsvn/aamusic/widget/CarKeyboard.kt) ngay trong bảng; nút 🎙️ dùng `SpeechRecognizer` như nút mic chính. Mở từ nút thư viện trên thanh tìm hoặc nút Hàng chờ trong Chế độ lái.

[DriveLibrary](app/src/main/java/com/gsvn/aamusic/data/DriveLibrary.kt) lưu JSON trong `SharedPreferences`, tái dùng model [VideoItem](app/src/main/java/com/gsvn/aamusic/data/VideoItem.kt) vốn đã có sẵn khả năng ser/de.

Mỗi mục chỉ là **dấu trang do người dùng tạo ra trong lúc dùng app**: id video đang mở cùng tên bài mà trình phát đang hiển thị — đúng thứ [PlayerController.queryState](app/src/main/java/com/gsvn/aamusic/player/PlayerController.kt) vẫn đọc để bơm vào phiên media. Không có chỗ nào duyệt hay rút dữ liệu của YouTube.

**Hàng chờ chỉ hoạt động khi người dùng tự xếp bài.** Để trống thì app không can thiệp, YouTube tự chạy bài kế như từ trước tới nay — nhờ vậy tính năng mới không giành quyền với hành vi cũ.

### 15. Danh sách dựng sẵn theo bối cảnh lái

29 mục, chia 3 nhóm (màn hình xe hiện tiêu đề nhóm):

| Nhóm | Danh sách |
|---|---|
| Lái xe | Yêu thích · Sáng sớm · Đêm khuya · Đường dài · Trong phố · Thư giãn · Sôi động · Rock · Disco |
| Thể loại | Nhạc Việt · Nhạc trẻ · Nhạc Trung (Hoa ngữ) · Nhạc Hàn (K-Pop) · Âu Mỹ (English) · Bolero – Trữ tình · Rap Việt · Remix – EDM · Acoustic · Nhạc Trịnh · Không lời – Piano · Jazz · Lofi · Thiếu nhi |
| Nghe & học | Khoa học · Lịch sử · Sách nói · Truyện audio · Podcast · Học tiếng Anh |

Đây **không phải** một hệ thống playlist riêng — mỗi mục chỉ là một từ khoá tìm kiếm đi qua đúng luồng tìm kiếm sẵn có, còn "Yêu thích" thì đọc từ `DriveLibrary`. Thêm/bớt một danh sách chỉ là sửa bảng `ALL` trong [DrivePlaylists.kt](app/src/main/java/com/gsvn/aamusic/data/DrivePlaylists.kt).

"Yêu thích" là danh sách nội bộ nên phát được ngay. Các danh sách còn lại là một từ khoá: [CarPlayback](app/src/main/java/com/gsvn/aamusic/car/CarPlayback.kt) tìm từ khoá đó ra **các bài cụ thể**, phát bài đầu và xếp các bài còn lại vào hàng chờ. Người dùng không phải rời Chế độ lái để tự chọn bài. Bản cũ mở trang kết quả tìm kiếm, mà trang đó không tự phát gì, nên trên Android Auto màn hình treo ở "Đang tải dữ liệu...".

Lệnh nói khớp tên danh sách theo **cụm từ dài nhất**: "học tiếng anh" ra *Học tiếng Anh* chứ không ra *Âu Mỹ* (vì "tiếng anh" ngắn hơn).

### 16. Nghe tiếp từ chỗ đang dở

Mỗi 5 giây, bài + vị trí đang phát được ghi lại. Mở lại app thì hỏi một lần:

```
Nghe tiếp?
<tên bài>   <kênh> · 23:42
[ Từ đầu ]  [ Nghe tiếp ]
```

Chỉ hỏi, **không tự phát**: tự động nổ nhạc lúc vừa mở app là hành vi người dùng không lường trước được. Bấm "Nghe tiếp" thì app mở thẳng `watch?v=<id>&t=<giây>s` — YouTube hiểu tham số `t` ngay trên URL nên khỏi phải canh lúc trang dựng xong trình phát rồi mới tua.

Điểm dừng quá 7 ngày, hoặc mới nghe dưới 20 giây, thì bỏ qua — lúc đó "nghe tiếp" chẳng khác gì mở lại từ đầu.

### 17. Nối/ngắt dàn âm thanh của xe

[CarConnection](app/src/main/java/com/gsvn/aamusic/car/CarConnection.kt) dùng `AudioManager.registerAudioDeviceCallback` chứ không nghe `BluetoothDevice.ACTION_ACL_CONNECTED`: cách này thấy đúng thứ cần thấy (đầu ra âm thanh vừa xuất hiện, kể cả cắm dây AUX/USB) và **không cần xin quyền `BLUETOOTH_CONNECT`**.

- **Nối vào xe** → phát tiếp, nếu người dùng đã bật tuỳ chọn. Mặc định TẮT: tự nhiên rống nhạc lúc vừa nổ máy là hành vi rất khó chịu.
- **Rút ra** (`ACTION_AUDIO_BECOMING_NOISY`) → dừng hẳn, việc này **luôn** làm. Watchdog của [PlaybackGuard](app/src/main/java/com/gsvn/aamusic/web/PlaybackGuard.kt) vốn coi mọi lần trang tự dừng là sự cố và phát lại, nên nếu không hạ cờ `__ytaWantPlay` thì rút dây khỏi xe là nhạc chuyển sang gào trên loa điện thoại.

### 18. Lệnh nói

Câu nói (từ nút 🎤 trong app hoặc từ Trợ lý Google/Android Auto) đi qua [VoiceCommands.parse](app/src/main/java/com/gsvn/aamusic/voice/VoiceCommands.kt) trước.

| Nói | Kết quả |
|---|---|
| "bài sau", "next", "skip" | chuyển bài |
| "bài trước", "previous" | bài trước |
| "tạm dừng", "pause" | dừng |
| "phát nhạc", "tiếp tục", "play" | phát tiếp |
| "phát nhạc rock", "mở nhạc Việt", "phát yêu thích" | mở danh sách dựng sẵn |
| còn lại | tìm và **phát luôn** bài đầu tiên |

Cố tình dừng ở mức bảng từ khoá, không phải trợ lý. Hai chỗ dễ sai nhất — và là lý do file này có [bộ test JVM riêng](app/src/test/java/com/gsvn/aamusic/voice/VoiceCommandsTest.kt):

1. **Lệnh phải khớp cả câu.** "phát bài hát tiếp theo của Sơn Tùng" mà hiểu thành nút Next thì hỏng; câu dài luôn được coi là tên bài.
2. **Cắt tiền tố theo *từ*, trên bản đã bỏ dấu, nhưng trả lại chữ còn dấu.** So khớp cần "phat", còn YouTube cần "Nơi Này Có Anh". Và tiền tố chỉ gồm động từ — cắt luôn chữ "nhạc" thì "mở nhạc Việt" chỉ còn "Việt", không khớp bí danh nào nữa.

---

### 19. Cập nhật trong app

[AppUpdate](app/src/main/java/com/gsvn/aamusic/data/AppUpdate.kt) hỏi `api.github.com/repos/mjnamjkaze/DriveTune/releases/latest` lúc mở app (tối đa 6 giờ một lần), so `tag_name` với `versionName` theo từng số, lưu kết quả vào `SharedPreferences`. Có bản mới thì báo một toast (mỗi bản một lần, không báo trên màn hình xe), và Cài đặt hiện dòng **Cập nhật lên bản …** ngay trên Giới thiệu. Chạm vào: `DownloadManager` tải asset `.apk` của release, xong thì mở `ACTION_VIEW` với `application/vnd.android.package-archive` (cần `REQUEST_INSTALL_PACKAGES`); lỗi bước nào thì mở link APK bằng trình duyệt. Mỗi release phải đính kèm file `.apk` ký cùng khoá thì mới cài đè được.

## Bí quyết lên được Android Auto

Android Auto (chiếu điện thoại lên màn hình xe) **chỉ cho phép** các loại app: media, messaging, navigation/point-of-interest… và **không có** hạng mục "trình duyệt WebView". Vì vậy app **giả dạng app điều hướng/bản đồ** để được chiếu lên màn xe.

Các mảnh ghép trong [AndroidManifest.xml](app/src/main/AndroidManifest.xml) — **thiếu bất kỳ mảnh nào là app biến mất khỏi Android Auto**:

1. **Quyền car app** ([dòng 17-18](app/src/main/AndroidManifest.xml#L17)):
   ```xml
   <uses-permission android:name="androidx.car.app.ACCESS_SURFACE" />
   <uses-permission android:name="androidx.car.app.MAP_TEMPLATES" />
   ```
2. **Hai intent-filter** trên `MainActivity` ([dòng 62-79](app/src/main/AndroidManifest.xml#L62)) với các category:
   - `CAR_LAUNCHER` → đặt icon vào car app drawer.
   - `androidx.car.app.category.NAVIGATION` + `android.intent.category.APP_MAPS` → khiến Android Auto **phân loại activity này là app điều hướng** (đây là mảnh dễ bị tưởng thừa trên app nhạc — **giữ lại**).
3. **Meta-data**:
   - `distractionOptimized = true` → khai báo giao diện đã tối ưu chống mất tập trung (bắt buộc để hiện khi đang lái).
   - `androidx.car.app.minCarApiLevel = 7`.
   - `com.google.android.gms.car.application` trỏ tới [automotive_app_desc.xml](app/src/main/res/xml/automotive_app_desc.xml) khai báo `<uses name="media" />`.
4. `resizeableActivity=true`, thẻ `<layout>` và `android.max_aspect` để activity vừa nhiều tỉ lệ màn hình xe.

> Vì đây là app bên thứ ba không qua Play Store, Android Auto mặc định ẩn nó. Phải **bật Unknown sources cho Android Auto** (xem bên dưới) thì icon mới hiện.

### Cây duyệt media — dùng được lúc xe đang chạy

Khi xe chạy, Android Auto chỉ cho dùng **giao diện media chuẩn**: hệ thống tự vẽ nút to, khoá bàn phím và cho đọc bằng giọng nói. [DriveBrowserService](app/src/main/java/com/gsvn/aamusic/car/DriveBrowserService.kt) đưa **Yêu thích · Danh sách · Vừa nghe** cùng **ô tìm kiếm** vào giao diện đó. Phần này **thêm vào, không thay thế**: các intent-filter giả dạng app điều hướng ở `MainActivity` vẫn là thứ chiếu giao diện điện thoại lên màn xe, đừng bỏ.

**Vì sao bản 2.3.0 treo ở "Đang tải dữ liệu..."** — sau khi chạm một mục, màn hình chờ đó của Android Auto chỉ tắt khi phiên media báo `PLAYING` hoặc `ERROR`. Có bốn lỗi khiến không bao giờ có báo cáo nào:

| Lỗi 2.3.0 | Sửa ở 2.4.0 |
|---|---|
| Phát bằng cách mở `MainActivity` từ nền. Android 10+ chặn im lặng việc này; lúc tiến trình mới được Android Auto bind thì còn chưa có context để mở | [PlaybackHost](app/src/main/java/com/gsvn/aamusic/player/PlaybackHost.kt) dựng **WebView ngầm** trong tiến trình. Mở app lên sau thì activity **nhận lại chính WebView đó** (`MutableContextWrapper`), nhạc không ngắt |
| `MainActivity.onDestroy` release phiên media. `setSessionToken` chỉ gọi được một lần, nên xe giữ token chết | Phiên sống suốt vòng đời tiến trình, không bao giờ release (NewPipe cũng làm vậy) |
| Chạm danh mục chỉ mở trang kết quả tìm kiếm, trang này không tự phát | Tìm ra bài cụ thể ([YouTubeSearch](app/src/main/java/com/gsvn/aamusic/data/YouTubeSearch.kt), InnerTube `youtubei/v1/search`), phát bài đầu, xếp phần còn lại vào hàng chờ |
| Không báo trạng thái trong lúc chờ | Báo `STATE_BUFFERING` kèm tên bài ngay khi nhận lệnh; lỗi (mất mạng, không có kết quả) thì báo `STATE_ERROR` kèm lời nhắn |

Các chi tiết khác:
- **WebView ngầm không gắn vào cửa sổ nào**, và đây là cố ý. Chromium coi WebView *chưa từng* gắn vào cửa sổ là đang hiển thị (`BrowserViewRenderer::IsClientVisible = !was_attached_ || (attached && window_visible)`), nên trình phát chạy bình thường. Nếu gắn vào overlay rồi gỡ ra thì nó bị coi là ẩn và video bị dừng.
- **Foreground service** được mở ngay khi nhận lệnh từ xe, *trước* bước tìm kiếm qua mạng. Hệ thống chỉ cho mở trong khoảng 10 giây sau lệnh (`MediaSessionService.tempAllowlistTargetPkgIfPossible`).
- **Tìm kiếm**: root trả extras `android.media.browse.SEARCH_SUPPORTED = true`. `onSearch` dùng `result.detach()` rồi trả kết quả trong máy trước, kết quả YouTube sau. `onPlayFromSearch` phục vụ "Hey Google, phát … trên DriveTune".
- **Ảnh bìa** đi qua [ArtworkProvider](app/src/main/java/com/gsvn/aamusic/player/ArtworkProvider.kt) dạng `content://`, vì Android Auto không tải ảnh `https://`.
- **Nút play** khi chưa có bài nào mở: nghe tiếp chỗ lần trước, không có thì mở danh sách mặc định, không có nữa thì bài vừa nghe gần nhất.
- **Bài kế** đi theo hàng chờ của app (Yêu thích, danh sách, kết quả tìm) trước, hàng chờ rỗng mới để YouTube tự chọn.

---

## Sideload lên Android Auto (từng bước)

> Cần: điện thoại Android, cáp USB (hoặc Android Auto không dây), xe/màn hình head unit hỗ trợ Android Auto. Chỉ dùng cho mục đích cá nhân/thử nghiệm.

### Bước 1 — Cài APK vào điện thoại
- Tải `DriveTune-<version>.apk` ở trang [Releases](https://github.com/mjnamjkaze/DriveTune/releases/latest), copy vào máy và cài (cho phép "Cài từ nguồn không xác định" khi được hỏi), **hoặc**
- Cài qua ADB:
  ```bash
  adb install -r DriveTune-2.7.0.apk
  ```

### Bước 2 — Bật Developer mode trong app Android Auto
1. Mở app **Android Auto** (trên Android 12+ nằm trong **Cài đặt → Ứng dụng đã kết nối → Android Auto**, hoặc tải "Android Auto" từ Play Store nếu chưa có).
2. Kéo xuống dưới cùng, bấm liên tục vào dòng **Version** (Phiên bản) ~10 lần cho tới khi hiện "Developer mode enabled".

### Bước 3 — Cho phép Unknown sources
1. Vào menu **⋮ (ba chấm) → Developer settings** trong app Android Auto.
2. Bật **"Unknown sources"** (Nguồn không xác định).
3. (Tuỳ máy) bật thêm **"Add new cars to Android Auto"** nếu cần kết nối xe mới.

### Bước 4 — Kết nối và mở app
1. Cắm điện thoại vào xe (hoặc kết nối AA không dây).
2. Trên màn hình xe, mở **app drawer** (biểu tượng lưới các ứng dụng).
3. Icon **DriveTune** sẽ xuất hiện (nhờ các intent-filter ở trên). Bấm để mở.

### Bước 5 — Nếu không thấy icon
- Kiểm tra đã bật **Unknown sources** (Bước 3) chưa — đây là lỗi hay gặp nhất.
- Ngắt kết nối rồi cắm lại cáp, hoặc khởi động lại điện thoại.
- Xoá cache app Android Auto: **Cài đặt → Ứng dụng → Android Auto → Bộ nhớ → Xoá bộ nhớ đệm**.
- Đảm bảo bản APK cài đúng (`versionName` khớp) và không bị chặn bởi Play Protect.

> 🔧 Mẹo test nhanh không cần xe: dùng **Desktop Head Unit (DHU)** trong Android SDK (`extras/google/auto/desktop-head-unit`) để mô phỏng màn hình xe qua ADB.

---

## Build

```bash
# Test (JVM, không cần máy/thiết bị)
./gradlew testDebugUnitTest

# Debug
./gradlew assembleDebug

# Release (cần cấu hình keystore trong local.properties, xem app/build.gradle.kts)
./gradlew assembleRelease
```

APK sau khi build được tự động đổi tên `DriveTune-<version>[_debug].apk` và copy về thư mục gốc dự án (xem `RenameApkTask` trong [app/build.gradle.kts](app/build.gradle.kts#L118)).

**Quyền cần cấp lần đầu khi chạy:**
- **Hiển thị trên ứng dụng khác** (SYSTEM_ALERT_WINDOW) — cho bong bóng nổi.
- **Thông báo** (POST_NOTIFICATIONS) — cho notification điều khiển.
- **Micro** (RECORD_AUDIO) — cho tìm kiếm bằng giọng nói (tuỳ chọn).
