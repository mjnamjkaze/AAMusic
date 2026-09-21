package com.gsvn.aamusic.player

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.gsvn.aamusic.MainActivity
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

    fun unregister() {
        webViewRef?.clear()
        webViewRef = null
    }

    private fun webView(): WebView? = webViewRef?.get()

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

    /** Poll current title + playing state; callback runs on the main thread. */
    fun queryState(callback: (title: String, playing: Boolean) -> Unit) {
        val wv = webView() ?: run { callback("", false); return }
        wv.post {
            wv.evaluateJavascript(JS_STATE) { result ->
                val (title, playing) = parseState(result)
                callback(title, playing)
            }
        }
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

    private fun parseState(result: String?): Pair<String, Boolean> {
        if (result.isNullOrBlank() || result == "null") return "" to false
        return try {
            // evaluateJavascript returns the JS value JSON-encoded: a quoted,
            // escaped string. Decode once to get the inner JSON, then parse.
            val inner = JSONTokener(result).nextValue() as? String ?: return "" to false
            val obj = JSONObject(inner)
            obj.optString("t") to obj.optBoolean("p")
        } catch (_: Exception) {
            "" to false
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
        "(function(){var v=${'$'}V;" +
            "window.__ytaWantPlay=v?v.paused:true;" +
            "var b=document.querySelector('ytmusic-player-bar #play-pause-button, " +
            "#play-pause-button, .ytp-play-button, tp-yt-paper-icon-button.play-pause-button');" +
            "if(b){b.click();return;}" +
            "if(v){if(v.paused)v.play();else v.pause();}})()"

    // Bỏ qua nếu PlaybackGuard đã ghi nhận người dùng chủ động tạm dừng.
    private const val JS_RESUME =
        "(function(){if(window.__ytaWantPlay===false)return;" +
            "window.__ytaWantPlay=true;" +
            "var v=${'$'}V;" +
            "if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}})()"

    // Cờ __ytaWantPlay được bật lại để watchdog của PlaybackGuard tiếp tục canh.
    private const val JS_FORCE_PLAY =
        "(function(){window.__ytaWantPlay=true;" +
            "var v=${'$'}V;" +
            "if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}})()"

    private const val JS_PAUSE =
        "(function(){window.__ytaWantPlay=false;" +
            "var v=${'$'}V;if(v&&!v.paused)v.pause();})()"

    private const val JS_NEXT =
        "(function(){var b=document.querySelector('ytmusic-player-bar .next-button, " +
            ".next-button, .ytp-next-button, tp-yt-paper-icon-button.next-button');" +
            "if(b){b.click();return;}document.dispatchEvent(new KeyboardEvent('keydown'," +
            "{key:'N',keyCode:78,which:78,shiftKey:true,bubbles:true}));})()"

    private const val JS_PREV =
        "(function(){var b=document.querySelector('ytmusic-player-bar .previous-button, " +
            ".previous-button, .ytp-prev-button, tp-yt-paper-icon-button.previous-button');" +
            "if(b){b.click();return;}var v=${'$'}V;" +
            "if(v&&v.currentTime>3){v.currentTime=0;return;}document.dispatchEvent(" +
            "new KeyboardEvent('keydown',{key:'P',keyCode:80,which:80,shiftKey:true,bubbles:true}));})()"

    private const val JS_STATE =
        "(function(){var t='';var el=document.querySelector('ytmusic-player-bar .title');" +
            "if(el)t=(el.textContent||'').trim();" +
            "if(!t){var h=document.querySelector('h1.ytd-watch-metadata, .ytp-title-link');" +
            "if(h)t=(h.textContent||'').trim();}" +
            "if(!t)t=(document.title||'').replace(/\\s*-\\s*YouTube.*$/,'').trim();" +
            "var v=${'$'}V;var p=v?!v.paused:false;" +
            "return JSON.stringify({t:t,p:p});})()"
}
