package com.gsvn.aamusic.data

import android.content.Context
import com.gsvn.aamusic.R

/**
 * Ảnh nền của màn phát khi chỉ nghe (tắt "Hiện video") và của ô ảnh lớn trong
 * Chế độ lái — lúc nào cũng là ảnh nền, không bao giờ là ảnh bìa bài hát.
 *
 * Mười ảnh 1920×1080 trong `drawable-nodpi`, năm tối năm sáng, cố ý tương
 * phản thấp và ít chi tiết để nhìn lướt khi lái không bị chói hay phân tâm.
 * Trang web lấy cùng tệp đó qua đường dẫn giả [WEB_PATH] mà ConfiguredWebView
 * chặn lại và trả thẳng từ tài nguyên của app — không tải gì từ mạng.
 */
data class PlayerBackground(val id: String, val nameRes: Int, val drawableRes: Int)

object PlayerBackgrounds {

    /** Tiền tố đường dẫn trên trang: `<origin>/__drivetune/bg/<id>.jpg`. */
    const val WEB_PATH = "/__drivetune/bg/"

    val ALL = listOf(
        PlayerBackground("night_sea", R.string.bg_night_sea, R.drawable.bg_night_sea),
        PlayerBackground("mountain_night", R.string.bg_mountain_night, R.drawable.bg_mountain_night),
        PlayerBackground("aurora", R.string.bg_aurora, R.drawable.bg_aurora),
        PlayerBackground("forest_dusk", R.string.bg_forest_dusk, R.drawable.bg_forest_dusk),
        PlayerBackground("graphite", R.string.bg_graphite, R.drawable.bg_graphite),
        PlayerBackground("morning_mist", R.string.bg_morning_mist, R.drawable.bg_morning_mist),
        PlayerBackground("dunes", R.string.bg_dunes, R.drawable.bg_dunes),
        PlayerBackground("mint", R.string.bg_mint, R.drawable.bg_mint),
        PlayerBackground("pastel_sunset", R.string.bg_pastel_sunset, R.drawable.bg_pastel_sunset),
        PlayerBackground("cloud_sky", R.string.bg_cloud_sky, R.drawable.bg_cloud_sky)
    )

    fun byId(id: String?): PlayerBackground? = ALL.firstOrNull { it.id == id }

    /** Ảnh người dùng chọn; chưa chọn (hoặc id cũ không còn) thì ảnh tối đầu tiên. */
    fun current(context: Context): PlayerBackground =
        byId(DriveSettings.playerBackground(context)) ?: ALL.first()

    /** Ảnh ứng với đường dẫn [WEB_PATH], hoặc null nếu không phải ảnh của app. */
    fun byWebPath(path: String?): PlayerBackground? {
        if (path == null || !path.startsWith(WEB_PATH)) return null
        return byId(path.removePrefix(WEB_PATH).removeSuffix(".jpg"))
    }
}
