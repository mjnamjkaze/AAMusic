package com.gsvn.aamusic.data

import android.net.Uri
import com.gsvn.aamusic.R

/**
 * Một mục trong danh sách phát gợi ý sẵn.
 *
 * @param id     khoá bền, dùng cho tuỳ chọn "danh sách mặc định" và lệnh nói.
 * @param query  từ khoá gửi cho YouTube; rỗng nghĩa là danh sách nội bộ của app
 *               (Yêu thích) chứ không phải một tìm kiếm.
 * @param spoken các cách người dùng hay đọc tên danh sách này khi ra lệnh nói.
 */
data class DrivePlaylist(
    val id: String,
    val nameRes: Int,
    val iconRes: Int,
    val query: String,
    val spoken: List<String> = emptyList()
) {
    val isLocal: Boolean get() = query.isBlank()

    /**
     * Trang kết quả tìm kiếm cho danh sách này.
     *
     * `sp=EgIQAQ%3D%3D` là tham số lọc "chỉ video" công khai của chính YouTube —
     * cùng thứ trang web đặt vào URL khi người dùng bấm bộ lọc — nên kết quả
     * bớt kênh/playlist lẫn vào, hợp với lúc đang lái.
     */
    val searchUrl: String
        get() = "https://www.youtube.com/results?search_query=${Uri.encode(query)}&sp=EgIQAQ%3D%3D"
}

/**
 * Bộ danh sách phát dựng sẵn theo bối cảnh lái xe.
 *
 * Chúng **không phải** một hệ thống playlist riêng: mỗi mục chỉ là một từ khoá
 * tìm kiếm đi qua đúng luồng tìm kiếm sẵn có của app, còn "Yêu thích" thì đọc
 * từ [DriveLibrary]. Nhờ vậy thêm/bớt một danh sách chỉ là sửa bảng dưới đây.
 */
object DrivePlaylists {

    /** Id của danh sách nội bộ "Yêu thích" — nơi khác tham chiếu tới hằng này. */
    const val ID_FAVORITES = "favorites"

    val ALL: List<DrivePlaylist> = listOf(
        DrivePlaylist(
            ID_FAVORITES, R.string.playlist_favorites, R.drawable.favorite_24px, "",
            listOf("yêu thích", "yeu thich", "favorite", "favourites", "favorites")
        ),
        DrivePlaylist(
            "morning", R.string.playlist_morning, R.drawable.ic_playlist_morning,
            "morning drive playlist",
            listOf("buổi sáng", "buoi sang", "sáng", "morning")
        ),
        DrivePlaylist(
            "night", R.string.playlist_night, R.drawable.ic_playlist_night,
            "night drive playlist",
            listOf("ban đêm", "ban dem", "đêm", "night")
        ),
        DrivePlaylist(
            "highway", R.string.playlist_highway, R.drawable.ic_playlist_highway,
            "highway driving music mix",
            listOf("cao tốc", "cao toc", "đường dài", "highway")
        ),
        DrivePlaylist(
            "city", R.string.playlist_city, R.drawable.ic_playlist_city,
            "city drive city pop playlist",
            listOf("phố", "trong phố", "thành phố", "city")
        ),
        DrivePlaylist(
            "chill", R.string.playlist_chill, R.drawable.ic_playlist_chill,
            "chill driving music lofi",
            listOf("thư giãn", "thu gian", "nhẹ nhàng", "chill", "lofi")
        ),
        DrivePlaylist(
            "energy", R.string.playlist_energy, R.drawable.ic_playlist_energy,
            "high energy driving music",
            listOf("sôi động", "soi dong", "mạnh", "energy", "edm")
        ),
        DrivePlaylist(
            "rock", R.string.playlist_rock, R.drawable.ic_playlist_rock,
            "rock driving playlist",
            listOf("rock", "nhạc rock", "nhac rock")
        ),
        DrivePlaylist(
            "disco", R.string.playlist_disco, R.drawable.ic_playlist_disco,
            "disco funk driving playlist",
            listOf("disco", "funk", "nhạc sàn", "nhac san")
        ),
        DrivePlaylist(
            "vietnamese", R.string.playlist_vietnamese, R.drawable.ic_playlist_vietnam,
            "nhạc Việt hay nhất",
            listOf("nhạc việt", "nhac viet", "việt nam", "viet nam", "vietnamese", "v-pop", "vpop")
        )
    )

    fun byId(id: String): DrivePlaylist? = ALL.firstOrNull { it.id == id }

    /**
     * Danh sách khớp với câu người dùng đọc, hoặc null.
     *
     * So khớp trên chuỗi đã chuẩn hoá (thường + bỏ dấu) nên "nhạc rock", "Nhac
     * Rock" hay "rock" đều về cùng một mục.
     */
    fun match(spokenText: String): DrivePlaylist? {
        val text = normalize(spokenText)
        if (text.isBlank()) return null
        return ALL.firstOrNull { playlist ->
            playlist.spoken.any { text.contains(normalize(it)) }
        }
    }

    /** Bỏ dấu tiếng Việt để so khớp không phụ thuộc cách máy nghe ra dấu. */
    fun normalize(text: String): String {
        val lower = text.lowercase().trim()
        val sb = StringBuilder(lower.length)
        for (c in java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)) {
            // Loại bỏ ký tự dấu kết hợp (U+0300..U+036F) và riêng đ/Đ.
            when {
                c in '̀'..'ͯ' -> Unit
                c == 'đ' -> sb.append('d')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
