package com.gsvn.aamusic.player

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.gsvn.aamusic.MainActivity
import com.gsvn.aamusic.data.VideoItem
import org.json.JSONObject
import org.json.JSONTokener
import java.lang.ref.WeakReference

/**
 * App-scoped bridge that lets the floating bubble control the YouTube Music
 * WebView (owned by [MainActivity]) even while the app is in the background.
 *
 * Playback is controlled by injecting JS that clicks the YT Music player-bar
 * buttons — no native MediaSession coupling required.
 */
object PlayerController {

    private var webViewRef: WeakReference<WebView>? = null
    private var appContext: Context? = null

    fun register(webView: WebView) {
        webViewRef = WeakReference(webView)
        appContext = webView.context.applicationContext
    }

    /** Gỡ [webView] nếu nó đúng là trình phát đang đăng ký. */
    fun unregister(webView: WebView) {
        if (webViewRef?.get() !== webView) return
        webViewRef?.clear()
        webViewRef = null
    }

    /** Cho biết context ứng dụng từ trước khi có WebView (phiên media, service). */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** WebView đang làm trình phát, hoặc null nếu chưa có / đã bị huỷ. */
    fun currentWebView(): WebView? = webViewRef?.get()

    private fun webView(): WebView? = currentWebView()

    fun playPause() = eval(JS_PLAY_PAUSE)

    fun next() = eval(JS_NEXT)

    fun previous() = eval(JS_PREV)

    /**
     * Phát tiếp sau khi giành lại audio focus (chỉ đường Maps, cuộc gọi…).
     * Tôn trọng cú bấm tạm dừng của người dùng: đang dừng chủ động thì để yên.
     */
    fun resume() = eval(JS_RESUME)

    /** Phát ngay theo lệnh rõ ràng (nút play trên vô lăng / notification). */
    fun play() = eval(JS_FORCE_PLAY)

    /** Dừng theo ý người dùng: hạ cờ để watchdog không tự phát lại. */
    fun pause() = eval(JS_PAUSE)

    /**
     * Ảnh chụp trạng thái trình phát tại một thời điểm.
     *
     * Toàn bộ trường đều đọc từ bài **đang mở trên màn hình** — cùng nguồn mà
     * phiên media vẫn cần để hiện tên bài, thanh tiến độ và ảnh bìa.
     */
    data class PlaybackState(
        val title: String = "",
        val channel: String = "",
        val videoId: String = "",
        val playing: Boolean = false,
        val ended: Boolean = false,
        val positionSec: Int = 0,
        val durationSec: Int = 0
    ) {
        val hasTrack: Boolean get() = videoId.isNotBlank()

        /** Bản ghi để lưu vào thư viện; null khi trang chưa xác định được bài. */
        fun toItem(): VideoItem? {
            if (videoId.isBlank()) return null
            return VideoItem(
                id = videoId,
                title = title,
                channel = channel,
                duration = formatTime(durationSec)
            )
        }
    }

    /** Poll trạng thái trình phát; callback chạy trên main thread. */
    fun queryState(callback: (PlaybackState) -> Unit) {
        val wv = webView() ?: run { callback(PlaybackState()); return }
        wv.post {
            wv.evaluateJavascript(JS_STATE) { result -> callback(parseState(result)) }
        }
    }

    /** Tua tới giây [sec] của bài đang phát. */
    fun seekTo(sec: Int) {
        if (sec <= 0) return
        eval("(function(){var v=" + V + ";if(v){try{v.currentTime=" + sec + ";}catch(e){}}})()")
    }

    /**
     * Mở [url] trong trình phát. Activity không còn WebView (đã thoát app, hay
     * tiến trình chỉ mở lên vì Android Auto) thì [PlaybackHost] dựng WebView
     * ngầm — không mở activity từ nền, vì Android chặn việc đó.
     */
    fun load(url: String) {
        if (url.isBlank()) return
        val ctx = appContext ?: return
        PlaybackHost.load(ctx, url)
    }

    /** "3:07" / "1:02:33" — dùng chung cho nhãn thời lượng khắp app. */
    fun formatTime(sec: Int): String {
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    /** Bring the app to the front focused on the search field (no query yet). */
    fun openSearch() = launchActivity {
        it.action = MainActivity.ACTION_SEARCH
        it.putExtra(MainActivity.EXTRA_FOCUS_SEARCH, true)
    }

    /** Đưa app lên trước và mở luôn hộp thoại tìm bằng giọng nói. */
    fun openVoiceSearch() = launchActivity {
        it.action = MainActivity.ACTION_SEARCH
        it.putExtra(MainActivity.EXTRA_START_VOICE, true)
    }

    /** Bring the app to the front and run a search for [query]. */
    fun search(query: String) = launchActivity {
        it.action = MainActivity.ACTION_SEARCH
        it.putExtra(MainActivity.EXTRA_QUERY, query)
    }

    /** Bring the app to the front as-is. */
    fun openApp() = launchActivity { }

    private fun launchActivity(configure: (Intent) -> Unit) {
        val ctx = appContext ?: return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            configure(this)
        }
        ctx.startActivity(intent)
    }

    private fun eval(js: String) {
        val wv = webView() ?: return
        wv.post { wv.evaluateJavascript(js, null) }
    }

    private fun parseState(result: String?): PlaybackState {
        if (result.isNullOrBlank() || result == "null") return PlaybackState()
        return try {
            // evaluateJavascript returns the JS value JSON-encoded: a quoted,
            // escaped string. Decode once to get the inner JSON, then parse.
            val inner = JSONTokener(result).nextValue() as? String ?: return PlaybackState()
            val obj = JSONObject(inner)
            PlaybackState(
                title = obj.optString("t"),
                channel = obj.optString("c"),
                videoId = obj.optString("i"),
                playing = obj.optBoolean("p"),
                ended = obj.optBoolean("e"),
                positionSec = obj.optInt("cu"),
                durationSec = obj.optInt("d")
            )
        } catch (_: Exception) {
            PlaybackState()
        }
    }

    // ── Injected JS (works on both YouTube Music and regular YouTube) ───
    // Đặt lại window.__ytaWantPlay theo trạng thái SAU cú bấm, để watchdog
    // chống-pause (PlaybackGuard) biết người dùng còn muốn nghe hay không.
    //
    // Luôn lấy video qua $V chứ không phải querySelector('video'): thẻ đầu
    // tiên của trang chủ là ô xem thử trong danh sách, bấm nút trên vô lăng mà
    // trúng nó thì bài đang nghe không hề nhúc nhích. PreviewGuard cung cấp
    // __ytaMainVideo; chưa chạy được thì quay về cách cũ.
    private const val V =
        "(window.__ytaMainVideo?window.__ytaMainVideo():document.querySelector('video'))"
    private const val JS_PLAY_PAUSE =
        "(function(){var v=" + V + ";" +
            "window.__ytaWantPlay=v?v.paused:true;" +
            "var b=document.querySelector('ytmusic-player-bar #play-pause-button, " +
            "#play-pause-button, .ytp-play-button, tp-yt-paper-icon-button.play-pause-button');" +
            "if(b){b.click();return;}" +
            "if(v){if(v.paused)v.play();else v.pause();}})()"

    // Bỏ qua nếu PlaybackGuard đã ghi nhận người dùng chủ động tạm dừng.
    private const val JS_RESUME =
        "(function(){if(window.__ytaWantPlay===false)return;" +
            "window.__ytaWantPlay=true;" +
            "var v=" + V + ";" +
            "if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}})()"

    // Cờ __ytaWantPlay được bật lại để watchdog của PlaybackGuard tiếp tục canh.
    private const val JS_FORCE_PLAY =
        "(function(){window.__ytaWantPlay=true;" +
            "var v=" + V + ";" +
            "if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}})()"

    private const val JS_PAUSE =
        "(function(){window.__ytaWantPlay=false;" +
            "var v=" + V + ";if(v&&!v.paused)v.pause();})()"

    private const val JS_NEXT =
        "(function(){var b=document.querySelector('ytmusic-player-bar .next-button, " +
            ".next-button, .ytp-next-button, tp-yt-paper-icon-button.next-button');" +
            "if(b){b.click();return;}document.dispatchEvent(new KeyboardEvent('keydown'," +
            "{key:'N',keyCode:78,which:78,shiftKey:true,bubbles:true}));})()"

    private const val JS_PREV =
        "(function(){var b=document.querySelector('ytmusic-player-bar .previous-button, " +
            ".previous-button, .ytp-prev-button, tp-yt-paper-icon-button.previous-button');" +
            "if(b){b.click();return;}var v=" + V + ";" +
            "if(v&&v.currentTime>3){v.currentTime=0;return;}document.dispatchEvent(" +
            "new KeyboardEvent('keydown',{key:'P',keyCode:80,which:80,shiftKey:true,bubbles:true}));})()"

    /**
     * Ngoài tên bài + trạng thái, trả thêm id/kênh/vị trí/thời lượng: phiên
     * media cần chúng để hiện ảnh bìa và thanh tiến độ, còn app dùng để lưu dấu
     * trang và điểm "nghe tiếp". Tất cả đều là dữ liệu của chính bài đang mở
     * trên màn hình, không dò thêm gì trong trang.
     *
     * Viết bằng chuỗi thô (như [com.gsvn.aamusic.web.PlaybackGuard]) vì có biểu
     * thức chính quy: nối chuỗi thường thì mỗi dấu `\` phải nhân đôi, rất dễ sai
     * mà chỉ lộ ra lúc chạy. Trong chuỗi thô, `$V` được nội suy thành hằng [V].
     */
    private val JS_STATE = """
        (function() {
            function txt(s) {
                var e = document.querySelector(s);
                return e ? (e.textContent || '').trim() : '';
            }

            var t = txt('ytmusic-player-bar .title');
            if (!t) t = txt('h1.ytd-watch-metadata, .ytp-title-link, ' +
                            'ytm-slim-video-information-renderer h2');
            if (!t) t = (document.title || '').replace(/\s*-\s*YouTube.*$/, '').trim();

            var c = txt('ytmusic-player-bar .byline a');
            if (!c) c = txt('ytm-slim-owner-renderer a, #owner #channel-name a, ' +
                            '#upload-info #channel-name a, .ytp-title-expanded-title');

            var i = '';
            try {
                i = new URL(location.href).searchParams.get('v') || '';
                if (!i) {
                    var m = location.href.match(
                        /(?:youtu\.be\/|shorts\/|embed\/)([\w-]{11})/);
                    if (m) i = m[1];
                }
            } catch (e) {}

            var v = $V;
            return JSON.stringify({
                t: t, c: c, i: i,
                p: v ? !v.paused : false,
                e: v ? !!v.ended : false,
                cu: v ? Math.floor(v.currentTime || 0) : 0,
                d: (v && isFinite(v.duration)) ? Math.floor(v.duration) : 0
            });
        })();
    """.trimIndent()
}
