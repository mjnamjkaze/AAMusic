package com.gsvn.aamusic.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thư viện cá nhân của người dùng: bài yêu thích, bài vừa nghe, hàng chờ, và
 * điểm dừng để nghe tiếp.
 *
 * Toàn bộ nội dung là **dấu trang do người dùng tạo ra trong lúc dùng app** —
 * mỗi mục chỉ là id video đang mở cùng tên bài mà trình phát đang hiển thị, thứ
 * [com.gsvn.aamusic.player.PlayerController] vẫn đọc sẵn để bơm vào phiên media.
 * Không có chỗ nào duyệt/rút dữ liệu của YouTube.
 *
 * Lưu bằng JSON trong SharedPreferences, tái dùng [VideoItem] (đã có sẵn khả
 * năng ser/de) thay vì dựng thêm một model nữa.
 */
object DriveLibrary {

    private const val PREF = "aamusic_library"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_RECENT = "recent"
    private const val KEY_QUEUE = "queue"
    private const val KEY_RESUME = "resume"

    private const val MAX_RECENT = 50
    private const val MAX_QUEUE = 100

    /** Điểm dừng của lần nghe trước. */
    data class ResumePoint(
        val item: VideoItem,
        val positionSec: Int,
        val savedAt: Long
    )

    // ── Yêu thích ──────────────────────────────────────────────────

    fun favorites(context: Context): List<VideoItem> = read(context, KEY_FAVORITES)

    fun isFavorite(context: Context, videoId: String): Boolean =
        videoId.isNotBlank() && favorites(context).any { it.id == videoId }

    /** Bật/tắt yêu thích cho [item]. @return trạng thái SAU khi đổi. */
    fun toggleFavorite(context: Context, item: VideoItem): Boolean {
        if (item.id.isBlank()) return false
        val list = favorites(context).toMutableList()
        val removed = list.removeAll { it.id == item.id }
        if (!removed) list.add(0, item)
        write(context, KEY_FAVORITES, list)
        return !removed
    }

    fun removeFavorite(context: Context, videoId: String) {
        write(context, KEY_FAVORITES, favorites(context).filterNot { it.id == videoId })
    }

    // ── Vừa nghe ───────────────────────────────────────────────────

    fun recent(context: Context): List<VideoItem> = read(context, KEY_RECENT)

    /** Ghi nhận bài đang phát; bài cũ trùng id được đẩy lên đầu chứ không nhân đôi. */
    fun notePlayed(context: Context, item: VideoItem) {
        if (item.id.isBlank()) return
        val list = recent(context).toMutableList()
        val existing = list.indexOfFirst { it.id == item.id }
        // Giữ lại tên/kênh cũ nếu lần này trang chưa kịp dựng xong metadata.
        val merged = if (existing >= 0) item.mergedWith(list[existing]) else item
        if (existing >= 0) list.removeAt(existing)
        list.add(0, merged)
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        write(context, KEY_RECENT, list)
    }

    fun clearRecent(context: Context) {
        prefs(context).edit().remove(KEY_RECENT).apply()
    }

    // ── Hàng chờ ───────────────────────────────────────────────────
    //
    // Hàng chờ của app chỉ hoạt động khi người dùng tự thêm bài vào. Để trống
    // thì app không can thiệp, YouTube tự chạy bài kế như trước giờ.

    fun queue(context: Context): List<VideoItem> = read(context, KEY_QUEUE)

    fun enqueue(context: Context, item: VideoItem) {
        if (item.id.isBlank()) return
        val list = queue(context).toMutableList()
        list.removeAll { it.id == item.id }
        list.add(item)
        while (list.size > MAX_QUEUE) list.removeAt(0)
        write(context, KEY_QUEUE, list)
    }

    fun removeFromQueue(context: Context, videoId: String) {
        write(context, KEY_QUEUE, queue(context).filterNot { it.id == videoId })
    }

    /** Lấy và bỏ bài đầu hàng chờ; null nếu hàng chờ rỗng. */
    fun popQueue(context: Context): VideoItem? {
        val list = queue(context).toMutableList()
        if (list.isEmpty()) return null
        val head = list.removeAt(0)
        write(context, KEY_QUEUE, list)
        return head
    }

    fun clearQueue(context: Context) {
        prefs(context).edit().remove(KEY_QUEUE).apply()
    }

    /** Thay cả hàng chờ bằng [items] — dùng khi phát nguyên một danh sách. */
    fun replaceQueue(context: Context, items: List<VideoItem>) {
        write(context, KEY_QUEUE, items.take(MAX_QUEUE))
    }

    // ── Nghe tiếp ──────────────────────────────────────────────────

    fun saveResume(context: Context, item: VideoItem, positionSec: Int) {
        if (item.id.isBlank()) return
        val obj = item.toJson()
            .put("pos", positionSec)
            .put("at", System.currentTimeMillis())
        prefs(context).edit().putString(KEY_RESUME, obj.toString()).apply()
    }

    /** Điểm dừng gần nhất, hoặc null nếu chưa có / đã quá [maxAgeMs]. */
    fun resumePoint(context: Context, maxAgeMs: Long = MAX_RESUME_AGE_MS): ResumePoint? {
        val raw = prefs(context).getString(KEY_RESUME, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val item = VideoItem.fromJson(obj) ?: return null
            val savedAt = obj.optLong("at")
            if (savedAt <= 0 || System.currentTimeMillis() - savedAt > maxAgeMs) return null
            ResumePoint(item, obj.optInt("pos"), savedAt)
        }.getOrNull()
    }

    fun clearResume(context: Context) {
        prefs(context).edit().remove(KEY_RESUME).apply()
    }

    // ── Lưu trữ ────────────────────────────────────────────────────

    private fun read(context: Context, key: String): List<VideoItem> {
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return runCatching { VideoItem.listFromJson(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    private fun write(context: Context, key: String, items: List<VideoItem>) {
        prefs(context).edit().putString(key, VideoItem.listToJson(items).toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Quá một tuần thì "nghe tiếp" không còn là thứ người dùng đang chờ. */
    private const val MAX_RESUME_AGE_MS = 7L * 24 * 60 * 60 * 1000
}
