package com.gsvn.aamusic.offline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Một bài đã tải về máy. */
data class OfflineTrack(
    val id: String,
    val title: String,
    val channel: String,
    val fileName: String,
    val sizeBytes: Long
) {
    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("channel", channel)
        .put("file", fileName)
        .put("size", sizeBytes)

    companion object {
        fun fromJson(o: JSONObject): OfflineTrack? {
            val id = o.optString("id")
            val file = o.optString("file")
            if (id.isBlank() || file.isBlank()) return null
            return OfflineTrack(
                id = id,
                title = o.optString("title"),
                channel = o.optString("channel"),
                fileName = file,
                sizeBytes = o.optLong("size")
            )
        }
    }
}

/**
 * Kho bài offline: file audio nằm trong bộ nhớ riêng của app, phần mô tả
 * (tiêu đề, kênh, tên file) lưu dạng JSON trong SharedPreferences.
 *
 * Việc tải chỉ chạy khi người dùng bật công tắc trong Cài đặt — mặc định tắt,
 * vì tải về tốn thêm dung lượng máy và chạy trên data di động.
 */
object OfflineStore {

    private const val PREFS = "aamusic"
    private const val KEY_ENABLED = "offline_enabled"
    private const val KEY_TRACKS = "offline_tracks"
    private const val DIR = "offline"

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun dir(ctx: Context): File =
        File(ctx.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun fileOf(ctx: Context, track: OfflineTrack): File = File(dir(ctx), track.fileName)

    fun all(ctx: Context): List<OfflineTrack> {
        val raw = prefs(ctx).getString(KEY_TRACKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it)?.let(OfflineTrack::fromJson) }
                // Bỏ qua bản ghi mà file đã bị xoá khỏi máy bằng cách khác.
                .filter { File(dir(ctx), it.fileName).exists() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun has(ctx: Context, id: String): Boolean = all(ctx).any { it.id == id }

    fun add(ctx: Context, track: OfflineTrack) {
        val updated = all(ctx).filterNot { it.id == track.id } + track
        write(ctx, updated)
    }

    fun remove(ctx: Context, id: String) {
        val track = all(ctx).firstOrNull { it.id == id }
        if (track != null) runCatching { fileOf(ctx, track).delete() }
        write(ctx, all(ctx).filterNot { it.id == id })
    }

    fun totalBytes(ctx: Context): Long = all(ctx).sumOf { it.sizeBytes }

    private fun write(ctx: Context, tracks: List<OfflineTrack>) {
        val array = JSONArray().apply { tracks.forEach { put(it.toJson()) } }
        prefs(ctx).edit().putString(KEY_TRACKS, array.toString()).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
