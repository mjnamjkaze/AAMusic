package com.gsvn.aamusic.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gsvn.aamusic.R
import com.gsvn.aamusic.databinding.SheetSettingsBinding
import com.gsvn.aamusic.offline.OfflinePlayer
import com.gsvn.aamusic.offline.OfflineStore
import com.gsvn.aamusic.player.PlayerController

/**
 * Bảng cài đặt + giới thiệu, mở bằng cách chạm logo trên thanh tìm kiếm.
 *
 * Gồm công tắc lưu offline (mặc định tắt), danh sách bài đã tải để nghe khi
 * mất mạng, và mục giới thiệu.
 */
class SettingsSheet(private val activity: Activity) {

    private lateinit var binding: SheetSettingsBinding

    fun show() {
        binding = SheetSettingsBinding.inflate(activity.layoutInflater)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(binding.root)

        binding.downloadsScroll.maxHeight =
            (280 * activity.resources.displayMetrics.density).toInt()

        setupOfflineSwitch()
        setupAbout()
        renderDownloads(dialog)

        dialog.show()
    }

    private fun setupOfflineSwitch() {
        binding.offlineSwitch.isChecked = OfflineStore.isEnabled(activity)
        binding.offlineSwitch.setOnCheckedChangeListener { _, checked ->
            OfflineStore.setEnabled(activity, checked)
            if (checked) {
                Toast.makeText(activity, R.string.settings_offline_on, Toast.LENGTH_LONG).show()
            }
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

    private fun renderDownloads(dialog: BottomSheetDialog) {
        val tracks = OfflineStore.all(activity)
        val megabytes = OfflineStore.totalBytes(activity) / (1024f * 1024f)
        binding.downloadsHeader.text = activity.getString(
            R.string.settings_downloads_header, tracks.size, megabytes
        )

        binding.downloadsEmpty.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        binding.downloadsClear.visibility = if (tracks.isEmpty()) View.GONE else View.VISIBLE
        binding.downloadsClear.setOnClickListener { confirmClearAll(dialog) }

        val list = binding.downloadsList
        list.removeAllViews()

        for (track in tracks) {
            val row = activity.layoutInflater.inflate(R.layout.item_video, list, false)
            row.findViewById<TextView>(R.id.itemTitle).text = track.title
            row.findViewById<TextView>(R.id.itemChannel).text = track.channel
            row.findViewById<TextView>(R.id.itemDuration).visibility = View.GONE
            Thumbnails.load(
                row.findViewById(R.id.itemThumb), track.thumbnailUrl, R.drawable.ic_music_note
            )
            row.setOnClickListener {
                // Trang đang phát cũng phải im, nếu không hai nguồn chồng tiếng.
                PlayerController.pauseWeb()
                OfflinePlayer.play(activity, track, tracks)
                Toast.makeText(activity, track.title, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            row.setOnLongClickListener {
                OfflineStore.remove(activity, track.id)
                renderDownloads(dialog)
                true
            }
            list.addView(row)
        }
    }

    private fun confirmClearAll(dialog: BottomSheetDialog) {
        MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.settings_downloads_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_downloads_clear) { _, _ ->
                if (OfflinePlayer.isActive) OfflinePlayer.stop()
                OfflineStore.all(activity).forEach { OfflineStore.remove(activity, it.id) }
                renderDownloads(dialog)
            }
            .show()
    }

    private companion object {
        const val ABOUT_URL = "https://gosei.com.vn/"
    }
}
