package com.gsvn.aamusic.data

import com.gsvn.aamusic.R

/**
 * Hình nền HD cho màn phát, thay cho ảnh bìa bài hát (đĩa nhạc quay trên
 * trang, ô ảnh bìa trong Chế độ lái).
 *
 * Ảnh nằm trong `drawable-nodpi` (1920×1080, không bị co theo mật độ màn
 * hình). Trang web lấy cùng tệp đó qua đường dẫn giả [WEB_PATH] mà
 * ConfiguredWebView chặn lại và trả thẳng từ tài nguyên của app — không tải
 * gì từ mạng, không nhân đôi tệp.
 */
data class PlayerBackground(val id: String, val nameRes: Int, val drawableRes: Int)

object PlayerBackgrounds {

    /** Tiền tố đường dẫn trên trang: `<origin>/__drivetune/bg/<id>.jpg`. */
    const val WEB_PATH = "/__drivetune/bg/"

    val ALL = listOf(
        PlayerBackground("night_drive", R.string.bg_night_drive, R.drawable.bg_night_drive),
        PlayerBackground("aurora", R.string.bg_aurora, R.drawable.bg_aurora),
        PlayerBackground("sunset", R.string.bg_sunset, R.drawable.bg_sunset)
    )

    fun byId(id: String?): PlayerBackground? = ALL.firstOrNull { it.id == id }

    /** Id trong đường dẫn [WEB_PATH], hoặc null nếu không phải ảnh của app. */
    fun byWebPath(path: String?): PlayerBackground? {
        if (path == null || !path.startsWith(WEB_PATH)) return null
        return byId(path.removePrefix(WEB_PATH).removeSuffix(".jpg"))
    }
}
