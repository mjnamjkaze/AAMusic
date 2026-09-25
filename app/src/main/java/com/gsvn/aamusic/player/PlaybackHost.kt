package com.gsvn.aamusic.player

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import com.gsvn.aamusic.BackgroundPlaybackService
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.data.VideoItem
import com.gsvn.aamusic.web.BackgroundPlayWebView
import com.gsvn.aamusic.web.BrowserCallbacks
import com.gsvn.aamusic.web.VideoMode
import com.gsvn.aamusic.web.configureWebView
import com.gsvn.aamusic.web.releaseCompletely

/**
 * Chỗ đảm bảo **luôn có một WebView để phát**, và nhịp poll trạng thái duy nhất
 * của app.
 *
 * ## Vì sao cần
 *
 * Trình phát là WebView của [com.gsvn.aamusic.MainActivity]. Trên Android Auto,
 * lúc chọn bài từ cây duyệt thì activity rất hay không có mặt: xe vừa nối là
 * Android Auto tự bind [com.gsvn.aamusic.car.DriveBrowserService] (tiến trình
 * mở lên mà chẳng có activity nào), hoặc người dùng đã thoát app trên điện
 * thoại. Khi đó bản cũ chỉ còn cách mở activity từ nền — Android 10+ chặn im
 * lặng — nên xe đứng mãi ở "Đang tải dữ liệu...".
 *
 * Giờ không có activity thì dựng một WebView **ngầm** ngay trong tiến trình,
 * cấu hình y hệt (cùng [configureWebView]: chặn quảng cáo, giữ phát nền, chặn
 * preview). Mở app lên sau đó thì activity **nhận lại chính WebView này**
 * ([adoptHeadless]) — nhạc không ngắt quãng.
 *
 * WebView ngầm **không gắn vào cửa sổ nào**, cố ý: Chromium coi WebView chưa
 * từng gắn vào cửa sổ là "đang hiển thị" (`BrowserViewRenderer::IsClientVisible`
 * = `!was_attached_ || (attached && window_visible)`), nên trang và trình phát
 * chạy như lúc mở trên màn hình. Gắn vào rồi gỡ ra thì ngược lại, bị coi là
 * ẩn và video bị dừng. Cũng không bao giờ gọi `onPause()` cho nó.
 *
 * ## Nhịp poll
 *
 * Trước đây activity poll khi đang hiện, service poll khi app xuống nền; lúc
 * chỉ có WebView ngầm thì không ai poll cả nên phiên media không bao giờ đổi
 * trạng thái. Giờ chỉ một nhịp ở đây, chạy suốt khi còn WebView; activity và
 * service chỉ đăng ký nghe để vẽ giao diện của mình.
 */
object PlaybackHost {

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    private var headless: WebView? = null

    /** Activity đang ở foreground — khi đó không cần service giữ tiến trình. */
    var activityResumed: Boolean = false

    private val listeners = LinkedHashSet<(PlayerController.PlaybackState) -> Unit>()
    private var ticking = false

    // Sổ sách theo bài: ghi "Vừa nghe", điểm nghe tiếp, chuyển bài trong hàng chờ.
    /** Bài đang mở theo lần poll gần nhất. */
    var currentTrackId: String = ""
        private set
    private var notedTrackTitle: String = ""
    private var advancedFromTrackId: String = ""
    private var lastResumeSaveMs: Long = 0L

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    // ── Nghe trạng thái ────────────────────────────────────────────

    fun addListener(listener: (PlayerController.PlaybackState) -> Unit) {
        listeners.add(listener)
        startTicker()
    }

    fun removeListener(listener: (PlayerController.PlaybackState) -> Unit) {
        listeners.remove(listener)
    }

    /** Bật nhịp poll; gọi lại nhiều lần vô hại. */
    fun startTicker() {
        if (ticking) return
        ticking = true
        main.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (PlayerController.currentWebView() == null) {
                // Không còn trình phát nào: ngừng poll, để phiên media về dừng.
                ticking = false
                MediaSessionHolder.update(PlayerController.PlaybackState())
                return
            }
            PlayerController.queryState { state -> onState(state) }
            main.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun onState(state: PlayerController.PlaybackState) {
        MediaSessionHolder.update(state)
        appContext?.let { keepBooks(it, state) }
        listeners.toList().forEach { it(state) }
    }

    private fun keepBooks(context: Context, state: PlayerController.PlaybackState) {
        if (!state.hasTrack) return
        if (state.videoId != currentTrackId) {
            currentTrackId = state.videoId
            notedTrackTitle = ""
            advancedFromTrackId = ""
        }
        // Trang mất vài giây mới dựng xong tên bài, nên ghi lại khi tên có.
        if (state.title.isNotBlank() && state.title != notedTrackTitle) {
            notedTrackTitle = state.title
            state.toItem()?.let { DriveLibrary.notePlayed(context, it) }
        }

        if (state.playing && state.positionSec > 0) {
            val now = System.currentTimeMillis()
            if (now - lastResumeSaveMs >= RESUME_SAVE_INTERVAL_MS) {
                lastResumeSaveMs = now
                state.toItem()?.let { DriveLibrary.saveResume(context, it, state.positionSec) }
            }
        }

        // Hết bài thì lấy bài kế trong hàng chờ; hàng chờ rỗng thì để YouTube
        // tự chạy bài tiếp như từ trước tới nay.
        if (state.ended && state.videoId != advancedFromTrackId) {
            val next = DriveLibrary.popQueue(context) ?: return
            advancedFromTrackId = state.videoId
            play(context, next)
        }
    }

    // ── Phát ───────────────────────────────────────────────────────

    /**
     * Phát [item]. Phiên media chuyển ngay sang "đang tải" kèm tên bài, để màn
     * hình xe có phản hồi tức thì thay vì đứng chờ trang nạp xong.
     */
    fun play(context: Context, item: VideoItem, startSec: Int = 0) {
        MediaSessionHolder.showPending(item)
        val url = if (startSec > 0) "${item.watchUrl}&t=${startSec}s" else item.watchUrl
        load(context, url)
    }

    /**
     * Mở [url] trong trình phát, dựng WebView ngầm nếu activity không có.
     * @return false nếu không thể có trình phát nào.
     */
    fun load(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        attach(context)
        val wv = PlayerController.currentWebView() ?: createHeadless(context) ?: return false
        // Nút "dừng hẳn" có thể đã onPause() WebView; đánh thức lại trước khi nạp.
        wv.onResume()
        wv.loadUrl(url)
        if (!activityResumed) keepProcessAlive(context)
        startTicker()
        return true
    }

    /**
     * Chạy service nền để giữ tiến trình + notification. Lệnh điều khiển gửi
     * tới phiên media (từ Android Auto, tai nghe…) được hệ thống cho phép mở
     * foreground service từ nền; nếu máy vẫn từ chối thì bỏ qua — WebView vẫn
     * phát được chừng nào tiến trình còn sống (Android Auto đang bind nó).
     */
    private fun keepProcessAlive(context: Context) {
        runCatching { BackgroundPlaybackService.start(context) }
    }

    /**
     * Gọi ngay khi nhận lệnh từ xe, **trước** mọi việc chậm (tìm kiếm qua mạng):
     * hệ thống chỉ cho mở foreground service từ nền trong ~10 giây sau lệnh.
     */
    fun keepAlive(context: Context) {
        if (!activityResumed) keepProcessAlive(context)
    }

    // ── WebView ngầm ───────────────────────────────────────────────

    private fun createHeadless(context: Context): WebView? {
        val app = context.applicationContext
        return runCatching {
            // MutableContextWrapper để activity nhận lại được WebView này mà
            // không giữ context của ứng dụng cho những thứ cần activity.
            val wv = BackgroundPlayWebView(MutableContextWrapper(app))
            configureWebView(
                wv,
                BrowserCallbacks(onPageFinished = { VideoMode.applyPrefs(app, wv) })
            )
            // Không có cửa sổ thì không ai đo/đặt kích thước: tự làm, để trang
            // có khổ màn hình điện thoại bình thường thay vì 0×0.
            val density = app.resources.displayMetrics.density
            val w = (VIEWPORT_W_DP * density).toInt()
            val h = (VIEWPORT_H_DP * density).toInt()
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            wv.layout(0, 0, w, h)
            PlayerController.register(wv)
            headless = wv
            wv
        }.getOrNull()
    }

    /**
     * Activity vừa dựng lên: trao WebView ngầm (nếu có) để nó gắn vào giao
     * diện thay cho WebView trống của layout. Nhạc đang phát chạy tiếp.
     */
    fun adoptHeadless(activity: Context): WebView? {
        val wv = headless ?: return null
        headless = null
        (wv.context as? MutableContextWrapper)?.baseContext = activity
        return wv
    }

    /** Dừng hẳn: huỷ WebView ngầm (nếu đang dùng). */
    fun releaseHeadless() {
        val wv = headless ?: return
        headless = null
        PlayerController.unregister(wv)
        runCatching { wv.releaseCompletely() }
    }

    private const val POLL_INTERVAL_MS = 1_000L

    /** Ghi điểm "nghe tiếp" thưa hơn nhịp poll để đỡ ghi prefs liên tục. */
    private const val RESUME_SAVE_INTERVAL_MS = 5_000L

    /** Khổ màn hình điện thoại thông thường, để trang dựng giao diện mobile. */
    private const val VIEWPORT_W_DP = 360
    private const val VIEWPORT_H_DP = 640
}
