package com.gsvn.aamusic.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebStorage
import android.widget.CompoundButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.AppUpdate
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
        setupPlayerBackground()
        setupClearData()
        setupAbout()
        setupUpdate()

        dialog.show()
    }

    private fun bind(switch: MaterialSwitch, key: String) {
        switch.isChecked = DriveSettings.isOn(activity, key)
        switch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            DriveSettings.set(activity, key, checked)
            onSettingChanged(key)
        }
    }

    /** Dòng "Hình nền"; chạm để chọn trong lưới ảnh thu nhỏ. */
    private fun setupPlayerBackground() {
        renderPlayerBackground()
        binding.playerBgRow.setOnClickListener { showPlayerBackgroundPicker() }
    }

    private fun renderPlayerBackground() {
        val name = activity.getString(PlayerBackgrounds.current(activity).nameRes)
        binding.playerBgRow.text = activity.getString(R.string.settings_player_bg_value, name)
    }

    /** Lưới 2 cột: chạm một ảnh là chọn luôn và đóng. */
    private fun showPlayerBackgroundPicker() {
        val selected = PlayerBackgrounds.current(activity).id
        val grid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 8.dp, 18.dp, 8.dp)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.settings_player_bg_title)
            .setView(ScrollView(activity).apply { addView(grid) })
            .create()

        for (pair in PlayerBackgrounds.ALL.chunked(2)) {
            val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (bg in pair) {
                val tile = activity.layoutInflater.inflate(R.layout.item_player_bg, line, false)
                tile.findViewById<ImageView>(R.id.bgThumb).setImageBitmap(decodeThumb(bg.drawableRes))
                val isSelected = bg.id == selected
                tile.findViewById<MaterialCardView>(R.id.bgCard).strokeWidth =
                    if (isSelected) 3.dp else 0
                tile.findViewById<TextView>(R.id.bgName).apply {
                    setText(bg.nameRes)
                    if (isSelected) setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                tile.setOnClickListener {
                    DriveSettings.setPlayerBackground(activity, bg.id)
                    renderPlayerBackground()
                    onSettingChanged(DriveSettings.KEY_PLAYER_BG)
                    dialog.dismiss()
                }
                line.addView(tile)
            }
            grid.addView(line)
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

    /** Dòng cuối: phiên bản + ủng hộ tác giả. */
    private fun setupAbout() {
        val version = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty()
        binding.aboutVersion.text = if (version.isBlank()) "" else "v$version"

        binding.aboutRow.setOnClickListener { showDonate() }
    }

    /** Mã VietQR để chuyển khoản ủng hộ, kèm nút chép số tài khoản. */
    private fun showDonate() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.donate_title)
            .setView(R.layout.dialog_donate)
            .setPositiveButton(R.string.donate_copy) { _, _ ->
                val clipboard = activity.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        activity.getString(R.string.donate_name),
                        activity.getString(R.string.donate_account)
                    )
                )
                Toast.makeText(activity, R.string.donate_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.donate_close, null)
            .show()
    }

    /** Có bản mới trên GitHub thì hiện dòng "Cập nhật lên bản …" ngay trên dòng Ủng hộ. */
    private fun setupUpdate() {
        val scope = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope ?: return
        AppUpdate.check(activity, scope) { release ->
            if (release == null) return@check
            binding.updateRow.visibility = android.view.View.VISIBLE
            binding.updateRow.text = activity.getString(R.string.update_row, release.version)
            binding.updateRow.setOnClickListener {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.update_downloading, release.version),
                    Toast.LENGTH_LONG
                ).show()
                AppUpdate.download(activity, release)
            }
        }
    }

}
