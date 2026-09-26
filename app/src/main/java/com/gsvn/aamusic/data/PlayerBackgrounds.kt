package com.gsvn.aamusic.data

import android.content.Context
import com.gsvn.aamusic.R

/**
 * Ảnh nền của màn phát khi chỉ nghe (tắt "Hiện video") và của ô ảnh lớn trong
 * Chế độ lái — lúc nào cũng là ảnh nền, không bao giờ là ảnh bìa bài hát.
 *
 * Mười ảnh 1920×1080 trong `drawable-nodpi`, mỗi ảnh một chủ đề (oải hương,
 * biển, hoa anh đào, đồi chè, rừng thu, núi tuyết, đồng hoa, đáy biển, phố
 * đêm, ngân hà): màu tươi nhưng không chói, phẳng, ít chi tiết nhấp nháy.
 * Trang web lấy cùng tệp đó qua đường dẫn giả [WEB_PATH] mà ConfiguredWebView
 * chặn lại và trả thẳng từ tài nguyên của app — không tải gì từ mạng.
 */
data class PlayerBackground(val id: String, val nameRes: Int, val drawableRes: Int)

object PlayerBackgrounds {

    /** Tiền tố đường dẫn trên trang: `<origin>/__drivetune/bg/<id>.jpg`. */
    const val WEB_PATH = "/__drivetune/bg/"

    val ALL = listOf(
        PlayerBackground("lavender", R.string.bg_lavender, R.drawable.bg_lavender),
        PlayerBackground("beach", R.string.bg_beach, R.drawable.bg_beach),
        PlayerBackground("sakura", R.string.bg_sakura, R.drawable.bg_sakura),
        PlayerBackground("tea_hills", R.string.bg_tea_hills, R.drawable.bg_tea_hills),
        PlayerBackground("autumn", R.string.bg_autumn, R.drawable.bg_autumn),
        PlayerBackground("snow_peaks", R.string.bg_snow_peaks, R.drawable.bg_snow_peaks),
        PlayerBackground("meadow", R.string.bg_meadow, R.drawable.bg_meadow),
        PlayerBackground("ocean_deep", R.string.bg_ocean_deep, R.drawable.bg_ocean_deep),
        PlayerBackground("city_night", R.string.bg_city_night, R.drawable.bg_city_night),
        PlayerBackground("galaxy", R.string.bg_galaxy, R.drawable.bg_galaxy)
    )

    fun byId(id: String?): PlayerBackground? = ALL.firstOrNull { it.id == id }

    /** Ảnh người dùng chọn; chưa chọn (hoặc id cũ không còn) thì ảnh đầu tiên. */
    fun current(context: Context): PlayerBackground =
        byId(DriveSettings.playerBackground(context)) ?: ALL.first()

    /** Ảnh ứng với đường dẫn [WEB_PATH], hoặc null nếu không phải ảnh của app. */
    fun byWebPath(path: String?): PlayerBackground? {
        if (path == null || !path.startsWith(WEB_PATH)) return null
        return byId(path.removePrefix(WEB_PATH).removeSuffix(".jpg"))
    }
}
