package com.gsvn.aamusic.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.gsvn.aamusic.data.VideoItem
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Ảnh bìa cho phiên media / notification / Drive Mode.
 *
 * Ảnh lấy thẳng từ CDN ảnh đại diện của YouTube theo id ([VideoItem.thumbnailUrl])
 * — địa chỉ công khai, không đụng gì tới trang. Dự án không dùng thư viện tải
 * ảnh nào nên ở đây là một lớp tải tối giản: một luồng nền, nhớ tạm trong RAM,
 * **cache xuống đĩa**, và **không bao giờ ném lỗi** — hỏng gì thì trả null, nơi
 * gọi tự dùng ảnh thương hiệu như trước.
 *
 * ## Vì sao có cache đĩa
 *
 * Bộ nhớ RAM mất sạch khi tiến trình bị thu hồi, nên mỗi lần mở lại app là tải
 * lại toàn bộ ảnh của Yêu thích / Vừa nghe. Ảnh `mqdefault` chỉ ~10 KB nhưng
 * một danh sách 50 bài là ~0,5 MB mỗi lần mở — đáng để giữ lại trên đĩa.
 *
 * Đây là thứ **duy nhất** app cache được để đỡ tốn data. Luồng nhạc thì không:
 * địa chỉ của nó có chữ ký kèm hạn dùng, mỗi phiên một khác, nên cache không
 * bao giờ trúng lại; còn tải về lưu sẵn thì đã là chuyện khác hẳn.
 */
object ArtworkCache {

    private const val MAX_MEMORY_ENTRIES = 12

    /** Trần cache đĩa. ~10 KB/ảnh nên chừng này đủ cho vài trăm bài. */
    private const val MAX_DISK_BYTES = 6L * 1024 * 1024

    private const val DIR_NAME = "artwork"

    private val memory = LruCache<String, Bitmap>(MAX_MEMORY_ENTRIES)
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DriveTune-Artwork").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var cacheDir: File? = null

    /**
     * Cho lớp này biết chỗ lưu. Gọi nhiều lần vô hại; chưa gọi thì cache đĩa
     * đơn giản là tắt, mọi thứ khác vẫn chạy.
     */
    fun attach(context: Context) {
        if (cacheDir != null) return
        cacheDir = File(context.applicationContext.cacheDir, DIR_NAME)
    }

    /** Ảnh đã nằm sẵn trong RAM, hoặc null. Gọi được từ main thread. */
    fun cached(videoId: String): Bitmap? =
        if (videoId.isBlank()) null else memory.get(videoId)

    /**
     * Lấy ảnh bìa của [videoId]; [onReady] chạy trên main thread và **chỉ được
     * gọi khi có ảnh thật**. Đã có sẵn trong RAM thì gọi lại ngay lập tức.
     */
    fun load(videoId: String, onReady: (Bitmap) -> Unit) {
        if (videoId.isBlank()) return
        memory.get(videoId)?.let { onReady(it); return }
        io.execute {
            val bitmap = fromDisk(videoId) ?: download(videoId) ?: return@execute
            memory.put(videoId, bitmap)
            main.post { onReady(bitmap) }
        }
    }

    /**
     * Tệp ảnh bìa trên đĩa, tải về nếu chưa có. **Chặn luồng** — chỉ gọi từ
     * luồng nền (ArtworkProvider chạy trên luồng binder). Null nếu không có.
     */
    fun fileBlocking(videoId: String): File? {
        val file = fileFor(videoId) ?: return null
        if (file.isFile) return file
        download(videoId) ?: return null
        return file.takeIf { it.isFile }
    }

    /** Xoá cache đĩa (mục "Dọn lịch sử và bộ nhớ đệm" trong Cài đặt). */
    fun clearDisk() {
        val dir = cacheDir ?: return
        io.execute { runCatching { dir.listFiles()?.forEach { it.delete() } } }
    }

    // ── Đĩa ────────────────────────────────────────────────────────

    private fun fileFor(videoId: String): File? {
        val dir = cacheDir ?: return null
        // Id video của YouTube là 11 ký tự [A-Za-z0-9_-]; chặn lại cho chắc để
        // không có cách nào ghép ra đường dẫn đi ra ngoài thư mục cache.
        if (!videoId.matches(SAFE_ID)) return null
        return File(dir, "$videoId.jpg")
    }

    private fun fromDisk(videoId: String): Bitmap? {
        val file = fileFor(videoId) ?: return null
        if (!file.isFile) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
        if (bitmap == null) {
            // Tệp hỏng (ghi dở vì mất điện chẳng hạn): bỏ đi, tải lại.
            runCatching { file.delete() }
            return null
        }
        // Chạm vào để bản mới dùng sống lâu hơn khi dọn bớt.
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        return bitmap
    }

    private fun saveToDisk(videoId: String, bytes: ByteArray) {
        val file = fileFor(videoId) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            // Ghi ra tệp tạm rồi đổi tên: đọc song song không bao giờ gặp tệp dở.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) tmp.delete()
        }
        pruneDisk()
    }

    /** Giữ thư mục dưới [MAX_DISK_BYTES], bỏ ảnh lâu không đụng tới trước. */
    private fun pruneDisk() {
        val dir = cacheDir ?: return
        runCatching {
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_DISK_BYTES) return
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_DISK_BYTES) break
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }

    // ── Mạng ───────────────────────────────────────────────────────

    private fun download(videoId: String): Bitmap? = runCatching {
        val url = URL(VideoItem(videoId, "", "", "").thumbnailUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val bytes = conn.inputStream.use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@runCatching null
            saveToDisk(videoId, bytes)
            bitmap
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,16}")
}
