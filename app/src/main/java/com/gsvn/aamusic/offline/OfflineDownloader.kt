package com.gsvn.aamusic.offline

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Tải luồng audio của bài đang phát về máy để nghe offline.
 *
 * URL luồng lấy từ player response của chính trang đang mở, nên dùng đúng
 * phiên/cookie hiện tại. Một số bài trả về `signatureCipher` thay vì URL thẳng
 * — những bài đó bị bỏ qua thay vì cố giải chữ ký, vì cơ chế ký đổi liên tục
 * và không đáng để chạy theo.
 *
 * Luồng tải không được tăng tốc (tham số chống-bóc của YouTube), nên tốc độ
 * xấp xỉ tốc độ phát: một bài 5 phút mất cỡ 5 phút để lưu xong trong lúc nghe.
 */
object OfflineDownloader {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "offline-dl").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    /** Các id đang tải dở, tránh tải chồng khi trang được kiểm tra lại. */
    private val inFlight = mutableSetOf<String>()

    /**
     * Thử lưu bài đang phát. Không làm gì nếu công tắc đang tắt, bài đã có sẵn,
     * hoặc trang chưa sẵn sàng. [onResult] chạy trên main thread, chỉ được gọi
     * khi thực sự có kết quả đáng báo cho người dùng.
     */
    fun captureCurrent(
        webView: WebView,
        context: Context,
        onResult: (Result) -> Unit
    ) {
        val ctx = context.applicationContext
        if (!OfflineStore.isEnabled(ctx)) return

        webView.evaluateJavascript(EXTRACT_AUDIO_JS) { raw ->
            val obj = decode(raw) ?: return@evaluateJavascript
            if (!obj.optBoolean("ok")) return@evaluateJavascript

            val id = obj.optString("id")
            val url = obj.optString("url")
            if (id.isBlank() || url.isBlank()) return@evaluateJavascript
            if (OfflineStore.has(ctx, id)) return@evaluateJavascript
            synchronized(inFlight) {
                if (!inFlight.add(id)) return@evaluateJavascript
            }

            val title = obj.optString("title")
            val channel = obj.optString("channel")
            val extension = if (obj.optString("mime").contains("webm")) "webm" else "m4a"
            val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
            val userAgent = webView.settings.userAgentString

            main.post { onResult(Result.Started(title.ifBlank { id })) }

            executor.execute {
                val result = download(ctx, id, title, channel, url, extension, cookie, userAgent)
                synchronized(inFlight) { inFlight.remove(id) }
                main.post { onResult(result) }
            }
        }
    }

    private fun download(
        ctx: Context,
        id: String,
        title: String,
        channel: String,
        url: String,
        extension: String,
        cookie: String,
        userAgent: String
    ): Result {
        val fileName = "$id.$extension"
        val target = File(OfflineStore.dir(ctx), fileName)
        val partial = File(OfflineStore.dir(ctx), "$fileName.part")

        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Referer", "https://www.youtube.com/")
                if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
            }

            if (conn.responseCode !in 200..299) {
                return Result.Failed(title, "HTTP ${conn.responseCode}")
            }

            conn.inputStream.use { input ->
                partial.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }

            if (partial.length() <= 0L) {
                partial.delete()
                return Result.Failed(title, "file rỗng")
            }

            partial.renameTo(target)
            val track = OfflineTrack(
                id = id,
                title = title.ifBlank { id },
                channel = channel,
                fileName = fileName,
                sizeBytes = target.length()
            )
            OfflineStore.add(ctx, track)
            Result.Saved(track)
        } catch (e: Exception) {
            runCatching { partial.delete() }
            Result.Failed(title, e.message ?: "lỗi mạng")
        }
    }

    private fun decode(raw: String?): JSONObject? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            val inner = JSONTokener(raw).nextValue() as? String ?: return null
            JSONObject(inner)
        } catch (_: Exception) {
            null
        }
    }

    sealed interface Result {
        data class Started(val title: String) : Result
        data class Saved(val track: OfflineTrack) : Result
        data class Failed(val title: String, val reason: String) : Result
    }

    /**
     * Lấy player response từ chính phần tử trình phát (`getPlayerResponse`)
     * trước, rồi mới tới biến toàn cục: trang điều hướng kiểu SPA không nạp lại
     * `ytInitialPlayerResponse`, nên chỉ đọc biến đó sẽ lưu nhầm bài cũ.
     */
    private val EXTRACT_AUDIO_JS = """
        (function() {
            try {
                var el = document.querySelector('#movie_player');
                var r = (el && el.getPlayerResponse) ? el.getPlayerResponse() : null;
                if (!r || !r.streamingData) r = window.ytInitialPlayerResponse;
                if (!r || !r.streamingData) return JSON.stringify({ ok: false });

                var details = r.videoDetails || {};
                var formats = r.streamingData.adaptiveFormats || [];
                var best = null;

                for (var i = 0; i < formats.length; i++) {
                    var f = formats[i];
                    if (!f.mimeType || f.mimeType.indexOf('audio/') !== 0) continue;
                    // Không có url nghĩa là bài này dùng signatureCipher — bỏ.
                    if (!f.url) continue;
                    var rate = f.bitrate || 0;
                    if (!best) { best = f; continue; }
                    var bestRate = best.bitrate || 0;
                    // Ưu tiên bản cao nhất nhưng không quá ~128kbps: đủ nghe mà
                    // không nuốt dung lượng máy.
                    var fits = rate <= 140000;
                    var bestFits = bestRate <= 140000;
                    if (fits && (!bestFits || rate > bestRate)) best = f;
                    else if (!fits && !bestFits && rate < bestRate) best = f;
                }

                if (!best) return JSON.stringify({ ok: false });

                return JSON.stringify({
                    ok: true,
                    id: details.videoId || '',
                    title: details.title || '',
                    channel: details.author || '',
                    url: best.url,
                    mime: best.mimeType || ''
                });
            } catch (e) {
                return JSON.stringify({ ok: false });
            }
        })();
    """.trimIndent()
}
