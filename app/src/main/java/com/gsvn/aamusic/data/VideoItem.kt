package com.gsvn.aamusic.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Một bài hát / video đã bóc tách từ dữ liệu trang, đủ để dựng hàng trong danh
 * sách native mà không cần hiển thị bất kỳ phần giao diện web nào.
 */
data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val duration: String
) {
    /** Ảnh bìa lấy thẳng từ CDN theo id — không phụ thuộc markup của trang. */
    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

    /** Bản lớn hơn cho trình phát toàn màn hình. */
    val hqThumbnailUrl: String get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    val watchUrl: String get() = "https://www.youtube.com/watch?v=$id"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("channel", channel)
        .put("duration", duration)

    companion object {
        fun fromJson(obj: JSONObject): VideoItem? {
            val id = obj.optString("id")
            if (id.isBlank()) return null
            return VideoItem(
                id = id,
                title = obj.optString("title"),
                channel = obj.optString("channel"),
                duration = obj.optString("duration")
            )
        }

        fun listFromJson(array: JSONArray): List<VideoItem> =
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { fromJson(it) }
            }

        fun listToJson(items: List<VideoItem>): JSONArray =
            JSONArray().apply { items.forEach { put(it.toJson()) } }
    }
}
