package com.gsvn.aamusic.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.webkit.WebStorage
import android.widget.CompoundButton
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.data.DrivePlaylists
import com.gsvn.aamusic.data.DriveSettings
import com.gsvn.aamusic.data.PlayerBackgrounds
import com.gsvn.aamusic.databinding.SheetSettingsBinding
import com.gsvn.aamusic.player.ArtworkCache

/**
 * Cài đặt, mở bằng cách chạm logo trên thanh tìm kiếm.
 *
 * Cố tình ngắn — tám công tắc và vài dòng — theo đúng tinh thần "ít lựa chọn,
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
    private val switches = mutableMapOf<String, MaterialSwitch>()

    /** Đang đồng bộ lại công tắc theo [DriveSettings]: đừng coi là người dùng bấm. */
    private var syncing = false

    fun show() {
        binding = SheetSettingsBinding.inflate(activity.layoutInflater)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(binding.root)

        bind(binding.switchDriveOnStart, DriveSettings.KEY_DRIVE_ON_START)
        bind(binding.switchResume, DriveSettings.KEY_RESUME)
        bind(binding.switchCarResume, DriveSettings.KEY_CAR_AUTO_RESUME)
        bind(binding.switchVoice, DriveSettings.KEY_VOICE_COMMANDS)
        bind(binding.switchShowVideo, DriveSettings.KEY_SHOW_VIDEO)
        bind(binding.switchShowSpeed, DriveSettings.KEY_SHOW_SPEED)
        bind(binding.switchDataSaver, DriveSettings.KEY_DATA_SAVER)
        bind(binding.switchForceDark, DriveSettings.KEY_FORCE_DARK)

        setupDefaultPlaylist()
        setupPlayerBackground()
        setupClearData()
        setupAbout()

        dialog.show()
    }

    private fun bind(switch: MaterialSwitch, key: String) {
        switches[key] = switch
        switch.isChecked = DriveSettings.isOn(activity, key)
        switch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            if (syncing) return@setOnCheckedChangeListener
            DriveSettings.set(activity, key, checked)
            onSettingChanged(key)
        }
    }

    /**
     * Gạt công tắc về đúng giá trị đang lưu — khi activity phải tự tắt một
     * tuỳ chọn (vd. từ chối quyền vị trí thì tắt "Hiện tốc độ xe").
     */
    fun sync() {
        if (!::binding.isInitialized) return
        syncing = true
        switches.forEach { (key, switch) -> switch.isChecked = DriveSettings.isOn(activity, key) }
        syncing = false
    }

    /** Dòng "Hình nền"; chạm để chọn trong bảng có ảnh thu nhỏ. */
    private fun setupPlayerBackground() {
        renderPlayerBackground()
        binding.playerBgRow.setOnClickListener { showPlayerBackgroundPicker() }
    }

    private fun renderPlayerBackground() {
        val current = PlayerBackgrounds.byId(DriveSettings.playerBackground(activity))
        val name = activity.getString(current?.nameRes ?: R.string.bg_none)
        binding.playerBgRow.text = activity.getString(R.string.settings_player_bg_value, name)
    }

    private fun showPlayerBackgroundPicker() {
        val selected = DriveSettings.playerBackground(activity)
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp, 0, 8.dp)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.settings_player_bg_title)
            .setView(list)
            .create()

        // Dòng đầu = không dùng hình nền (ảnh bài hát như cũ).
        val options = listOf(Triple("", R.string.bg_none, 0)) +
            PlayerBackgrounds.ALL.map { Triple(it.id, it.nameRes, it.drawableRes) }
        for ((id, nameRes, drawableRes) in options) {
            val row = activity.layoutInflater.inflate(R.layout.item_player_bg, list, false)
            val thumb = row.findViewById<ImageView>(R.id.bgThumb)
            if (drawableRes != 0) {
                thumb.setImageBitmap(decodeThumb(drawableRes))
            } else {
                thumb.scaleType = ImageView.ScaleType.CENTER
                thumb.setImageResource(R.drawable.ic_music_note)
                thumb.setColorFilter(activity.getColor(R.color.drive_text_dim))
            }
            row.findViewById<RadioButton>(R.id.bgName).apply {
                setText(nameRes)
                isChecked = id == selected
            }
            row.setOnClickListener {
                DriveSettings.setPlayerBackground(activity, id)
                renderPlayerBackground()
                onSettingChanged(DriveSettings.KEY_PLAYER_BG)
                dialog.dismiss()
            }
            list.addView(row)
        }
        dialog.show()
    }

    /** Ảnh HD 1920×1080 thu 8 lần cho ô xem trước — khỏi giải mã đủ cỡ. */
    private fun decodeThumb(resId: Int) = BitmapFactory.decodeResource(
        activity.resources, resId, BitmapFactory.Options().apply { inSampleSize = 8 }
    )

    private val Int.dp: Int get() = (this * activity.resources.displayMetrics.density).toInt()

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
