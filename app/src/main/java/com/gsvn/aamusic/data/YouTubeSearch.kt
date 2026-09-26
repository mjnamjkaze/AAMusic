package com.gsvn.aamusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tìm bài trên YouTube bằng lời gọi mạng trực tiếp, không qua WebView.
 *
 * Android Auto và lệnh nói cần **bài cụ thể** để phát ngay: mở trang kết quả
 * trong WebView thì trang không tự phát gì, xe đứng mãi ở "Đang tải dữ liệu...",
 * còn ô tìm kiếm của xe thì cần một danh sách để hiển thị. Cùng kiểu với
 * [SearchSuggest]: gọi native nên không vướng CORS, và **không bao giờ ném lỗi**.
 *
 * Dùng API nội bộ `youtubei/v1/search` mà chính trang web gọi (không cần khoá),
 * lọc "chỉ video". Hỏng thì lùi về đọc `ytInitialData` trong HTML của trang
 * kết quả. Cả hai đều đi qua cùng bộ bóc [collect], dò đệ quy nên chịu được
 * YouTube xáo trộn cấu trúc JSON.
 */
object YouTubeSearch {

    /** Một trang kết quả; [next] là mã để xin trang sau (null = hết). */
    data class Page(val items: List<VideoItem>, val next: String?)

    private const val API_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val HTML_URL = "https://www.youtube.com/results?search_query="

    private const val CLIENT_NAME = "WEB"
    private const val CLIENT_NAME_ID = "1"
    // Theo ClientsConstants.WEB_HARDCODED_CLIENT_VERSION của NewPipeExtractor.
    private const val CLIENT_VERSION = "2.20260805.01.00"

    /** Bộ lọc "Loại: Video" — cùng tham số trang web đặt khi bấm bộ lọc. */
    private const val VIDEOS_ONLY = "EgIQAQ%3D%3D"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"

    /**
     * Tìm [query]. Kết quả rỗng nghĩa là không có bài nào; [Result.failure]
     * nghĩa là không gọi được (mất mạng, YouTube chặn…).
     */
    suspend fun search(query: String, limit: Int = 20): Result<List<VideoItem>> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext Result.success(emptyList())

            val api = runCatching { viaApi(q) }
            val fromApi = api.getOrNull().orEmpty()
            if (fromApi.isNotEmpty()) return@withContext Result.success(fromApi.take(limit))

            val html = runCatching { viaHtml(q) }
            val fromHtml = html.getOrNull().orEmpty()
            when {
                fromHtml.isNotEmpty() -> Result.success(fromHtml.take(limit))
                // Ít nhất một đường gọi được mà không ra bài nào: thật sự không có.
                api.isSuccess || html.isSuccess -> Result.success(emptyList())
                else -> Result.failure(api.exceptionOrNull() ?: IllegalStateException())
            }
        }

    /**
     * Tìm theo trang cho danh sách kéo xuống là hiện thêm: trang đầu gọi với
     * [continuation] = null, các trang sau truyền [Page.next] của trang trước.
     */
    suspend fun searchPage(query: String, continuation: String? = null): Result<Page> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext Result.success(Page(emptyList(), null))
            if (continuation != null) {
                return@withContext runCatching {
                    val root = callApi(JSONObject().put("continuation", continuation))
                    Page(parse(root), findContinuation(root))
                }
            }
            val api = runCatching {
                callApi(JSONObject().put("query", q).put("params", VIDEOS_ONLY))
            }
            api.getOrNull()?.let { root ->
                val items = parse(root)
                if (items.isNotEmpty()) return@withContext Result.success(Page(items, findContinuation(root)))
            }
            val html = runCatching { htmlInitialData(q) }
            val root = html.getOrNull()
            when {
                root != null -> Result.success(Page(parse(root), findContinuation(root)))
                api.isSuccess || html.isSuccess -> Result.success(Page(emptyList(), null))
                else -> Result.failure(api.exceptionOrNull() ?: IllegalStateException())
            }
        }

    private fun viaApi(query: String): List<VideoItem> =
        parse(callApi(JSONObject().put("query", query).put("params", VIDEOS_ONLY)))

    /** POST `youtubei/v1/search` với [request] (query hoặc continuation) + context. */
    private fun callApi(request: JSONObject): JSONObject {
        val body = request
            .put(
                "context", JSONObject().put(
                    "client", JSONObject()
                        .put("clientName", CLIENT_NAME)
                        .put("clientVersion", CLIENT_VERSION)
                        .put("hl", "vi")
                        .put("gl", "VN")
                )
            )
            .toString()

        val conn = open(API_URL).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-YouTube-Client-Name", CLIENT_NAME_ID)
            setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("Referer", "https://www.youtube.com/")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun viaHtml(query: String): List<VideoItem> =
        htmlInitialData(query)?.let(::parse).orEmpty()

    private fun htmlInitialData(query: String): JSONObject? {
        val url = HTML_URL + URLEncoder.encode(query, "UTF-8") +
            "&sp=" + URLEncoder.encode(VIDEOS_ONLY, "UTF-8") + "&hl=vi&gl=VN"
        val conn = open(url)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val json = extractInitialData(html) ?: return null
            return JSONObject(json)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
            // Bỏ qua trang xin đồng ý cookie (ở vài vùng YouTube chuyển hướng
            // sang consent.youtube.com thay vì trả kết quả).
            setRequestProperty("Cookie", "SOCS=CAE=")
        }

    /** Cắt khối JSON `ytInitialData = {...};` ra khỏi HTML. */
    private fun extractInitialData(html: String): String? {
        val marker = html.indexOf("ytInitialData")
        if (marker < 0) return null
        val start = html.indexOf('{', marker)
        if (start < 0) return null
        // Đếm ngoặc (bỏ qua ngoặc nằm trong chuỗi) để tìm đúng chỗ kết thúc.
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until html.length) {
            val c = html[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return html.substring(start, i + 1)
            }
        }
        return null
    }

    // ── Bóc kết quả ────────────────────────────────────────────────

    internal fun parse(root: JSONObject): List<VideoItem> {
        val out = LinkedHashMap<String, VideoItem>()
        collect(root, out)
        return out.values.toList()
    }

    /** Mã trang sau: `continuationItemRenderer…continuationCommand.token`. */
    internal fun findContinuation(node: Any?, inside: Boolean = false): String? {
        when (node) {
            is JSONObject -> {
                if (inside) {
                    node.optJSONObject("continuationCommand")?.optString("token")
                        ?.takeIf { it.isNotBlank() }?.let { return it }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    findContinuation(node.opt(key), inside || key == "continuationItemRenderer")
                        ?.let { return it }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                findContinuation(node.opt(i), inside)?.let { return it }
            }
        }
        return null
    }

    /** Dò đệ quy mọi kiểu ô video mà YouTube đang dùng, giữ thứ tự xuất hiện. */
    private fun collect(node: Any?, out: MutableMap<String, VideoItem>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    val item = when (key) {
                        "videoRenderer", "compactVideoRenderer", "videoWithContextRenderer" ->
                            (value as? JSONObject)?.let(::fromRenderer)
                        "lockupViewModel" -> (value as? JSONObject)?.let(::fromLockup)
                        else -> null
                    }
                    if (item != null) {
                        if (item.id !in out) out[item.id] = item
                    } else {
                        collect(value, out)
                    }
                }
            }

            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
        }
    }

    private fun fromRenderer(r: JSONObject): VideoItem? {
        val id = r.optString("videoId")
        if (!id.matches(VIDEO_ID)) return null
        val title = text(r.optJSONObject("title")).ifBlank { text(r.optJSONObject("headline")) }
        if (title.isBlank()) return null
        val channel = text(r.optJSONObject("ownerText"))
            .ifBlank { text(r.optJSONObject("longBylineText")) }
            .ifBlank { text(r.optJSONObject("shortBylineText")) }
        return VideoItem(id, title, channel, text(r.optJSONObject("lengthText")))
    }

    /** Dạng ô mới (lockupViewModel) — chỉ lấy loại video, bỏ playlist/kênh. */
    private fun fromLockup(l: JSONObject): VideoItem? {
        if (l.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = l.optString("contentId")
        if (!id.matches(VIDEO_ID)) return null
        val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content").orEmpty()
        if (title.isBlank()) return null
        val channel = meta?.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")?.optJSONObject(0)
            ?.optJSONArray("metadataParts")?.optJSONObject(0)
            ?.optJSONObject("text")?.optString("content").orEmpty()
        return VideoItem(id, title, channel, "")
    }

    /** Chữ của một trường kiểu `{simpleText}` hoặc `{runs:[{text}]}`. */
    private fun text(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it.trim() }
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString().trim()
    }

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
}
