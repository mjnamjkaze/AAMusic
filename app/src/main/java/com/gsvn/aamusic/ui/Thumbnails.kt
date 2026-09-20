package com.gsvn.aamusic.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Bộ tải ảnh bìa tối giản (không kéo thêm thư viện): cache RAM + pool 3 luồng.
 * Mỗi ImageView được tag bằng URL đang chờ nên khi hàng bị tái sử dụng, ảnh về
 * muộn của bài cũ sẽ bị bỏ qua thay vì đè nhầm.
 */
object Thumbnails {

    private val executor = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "thumb-loader").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private val main = Handler(Looper.getMainLooper())

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val tagKey = "thumb_url".hashCode()

    fun load(view: ImageView, url: String, placeholder: Int) {
        val cached = cache.get(url)
        if (cached != null) {
            view.setTag(tagKey, url)
            view.setImageBitmap(cached)
            return
        }

        view.setTag(tagKey, url)
        view.setImageResource(placeholder)

        executor.execute {
            val bitmap = fetch(url) ?: return@execute
            cache.put(url, bitmap)
            main.post {
                if (view.getTag(tagKey) == url) view.setImageBitmap(bitmap)
            }
        }
    }

    private fun fetch(url: String): Bitmap? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
        }
        conn.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
}
