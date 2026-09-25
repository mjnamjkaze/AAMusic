package com.gsvn.aamusic.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.WebStorage
import android.widget.CompoundButton
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.data.DrivePlaylists
import com.gsvn.aamusic.data.DriveSettings
import com.gsvn.aamusic.databinding.SheetSettingsBinding
import com.gsvn.aamusic.player.ArtworkCache

/**
 * Cài đặt, mở bằng cách chạm logo trên thanh tìm kiếm.
 *
 * Cố tình ngắn — bảy công tắc và hai dòng — theo đúng tinh thần "ít lựa chọn,
 * mỗi lựa chọn đều rõ tác dụng". Mỗi công tắc ghi thẳng vào [DriveSettings];
 * thứ nào cần áp dụng ngay (nền tối, tiết kiệm dữ liệu) thì báo ngược ra
 * activity qua [onSettingChanged].
 *
 * @param onSettingChanged khoá vừa đổi, xem hằng KEY_* của [DriveSettings].
 */
class SettingsSheet(
    private val activity: Activity,
    private val onSettingChanged: (key: String) -> Unit = {}
) {

    private lateinit var binding: SheetSettingsBinding

    fun show() {
        binding = SheetSettingsBinding.inflate(activity.layoutInflater)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(binding.root)

        bind(binding.switchDriveOnStart, DriveSettings.KEY_DRIVE_ON_START)
        bind(binding.switchResume, DriveSettings.KEY_RESUME)
        bind(binding.switchCarResume, DriveSettings.KEY_CAR_AUTO_RESUME)
        bind(binding.switchVoice, DriveSettings.KEY_VOICE_COMMANDS)
        bind(binding.switchShowVideo, DriveSettings.KEY_SHOW_VIDEO)
        bind(binding.switchDataSaver, DriveSettings.KEY_DATA_SAVER)
        bind(binding.switchForceDark, DriveSettings.KEY_FORCE_DARK)

        setupDefaultPlaylist()
        setupClearData()
        setupAbout()

        dialog.show()
    }

    private fun bind(switch: MaterialSwitch, key: String) {
        switch.isChecked = DriveSettings.isOn(activity, key)
        switch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            DriveSettings.set(activity, key, checked)
            onSettingChanged(key)
        }
    }

    /** Dòng hiển thị danh sách mặc định; đổi thì vào bảng Danh sách mà ghim. */
    private fun setupDefaultPlaylist() {
        val current = DrivePlaylists.byId(DriveSettings.defaultPlaylist(activity))
        val name = current?.let { activity.getString(it.nameRes) }
            ?: activity.getString(R.string.settings_default_playlist_none)
        binding.defaultPlaylistRow.text =
            activity.getString(R.string.settings_default_playlist_value, name)
        binding.defaultPlaylistRow.setOnClickListener {
            Toast.makeText(activity, R.string.settings_default_playlist_hint, Toast.LENGTH_LONG)
                .show()
        }
    }

    /**
     * Dọn bộ nhớ: lịch sử nghe + hàng chờ + điểm nghe tiếp + cache của trang.
     *
     * KHÔNG đụng tới bài yêu thích và cookie đăng nhập — mất hai thứ đó là mất
     * công người dùng gây dựng, chứ không phải "dọn rác".
     */
    private fun setupClearData() {
        binding.clearDataRow.setOnClickListener {
            DriveLibrary.clearRecent(activity)
            DriveLibrary.clearQueue(activity)
            DriveLibrary.clearResume(activity)
            ArtworkCache.clearDisk()
            runCatching { WebStorage.getInstance().deleteAllData() }
            Toast.makeText(activity, R.string.settings_clear_data_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAbout() {
        val version = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty()
        binding.aboutVersion.text = if (version.isBlank()) "" else "v$version"

        binding.aboutRow.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_URL))
            runCatching { activity.startActivity(intent) }.onFailure {
                Toast.makeText(activity, ABOUT_URL, Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        const val ABOUT_URL = "https://gosei.com.vn/"
    }
}
