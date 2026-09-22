package com.gsvn.aamusic.data

import android.content.Context

/**
 * Các tuỳ chọn của người dùng, đọc/ghi thẳng trên SharedPreferences có sẵn
 * ("aamusic" — cùng chỗ với cờ `asked_overlay` của MainActivity) để không sinh
 * thêm một lớp lưu trữ nữa. Cách gọi theo đúng lối của [SearchHistory]:
 * hàm tĩnh nhận `context`.
 *
 * Mọi khoá đều có mặc định giữ nguyên hành vi bản trước, nên người dùng cũ
 * cập nhật lên không thấy app đổi tính nết.
 */
object DriveSettings {

    private const val PREF = "aamusic"

    const val KEY_DRIVE_ON_START = "drive_mode_on_start"
    const val KEY_RESUME = "resume_playback"
    const val KEY_CAR_AUTO_RESUME = "car_auto_resume"
    const val KEY_VOICE_COMMANDS = "voice_commands"
    const val KEY_DATA_SAVER = "data_saver"
    const val KEY_FORCE_DARK = "force_dark"

    private const val KEY_DEFAULT_PLAYLIST = "default_playlist"

    /** Mặc định của từng công tắc. Nơi duy nhất quyết định hành vi ban đầu. */
    private val DEFAULTS = mapOf(
        KEY_DRIVE_ON_START to false,
        KEY_RESUME to true,
        KEY_CAR_AUTO_RESUME to false,
        KEY_VOICE_COMMANDS to true,
        KEY_DATA_SAVER to true,
        KEY_FORCE_DARK to false
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isOn(context: Context, key: String): Boolean =
        prefs(context).getBoolean(key, DEFAULTS[key] ?: false)

    fun set(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    /** Id của [DrivePlaylist] mở sẵn khi vào Drive Mode; rỗng = không mở gì. */
    fun defaultPlaylist(context: Context): String =
        prefs(context).getString(KEY_DEFAULT_PLAYLIST, "").orEmpty()

    fun setDefaultPlaylist(context: Context, id: String) {
        prefs(context).edit().putString(KEY_DEFAULT_PLAYLIST, id).apply()
    }
}
