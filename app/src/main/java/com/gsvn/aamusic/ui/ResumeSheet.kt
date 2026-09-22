package com.gsvn.aamusic.ui

import android.app.Activity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.databinding.SheetResumeBinding
import com.gsvn.aamusic.player.ArtworkCache
import com.gsvn.aamusic.player.PlayerController

/**
 * "Nghe tiếp?" — hỏi một lần khi mở lại app nếu lần trước đang nghe dở.
 *
 * Chỉ hỏi, không tự phát: tự động nổ nhạc lúc vừa mở app là hành vi người dùng
 * không lường trước được. Chọn gì cũng xoá điểm dừng đi, để lần mở sau không bị
 * hỏi lại về cùng một bài.
 */
class ResumeSheet(private val activity: Activity) {

    /**
     * Hiện bảng nếu có điểm dừng đáng hỏi. [onChoice] nhận số giây cần tua tới
     * (0 = nghe lại từ đầu) cùng địa chỉ bài.
     *
     * @return true nếu bảng được hiện.
     */
    fun showIfAvailable(onChoice: (url: String, startSec: Int) -> Unit): Boolean {
        val point = DriveLibrary.resumePoint(activity) ?: return false
        // Mới nghe được vài giây thì "nghe tiếp" chẳng khác gì mở lại từ đầu.
        if (point.positionSec < MIN_POSITION_SEC) return false

        val binding = SheetResumeBinding.inflate(activity.layoutInflater)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(binding.root)

        binding.resumeTitle.text =
            point.item.title.ifBlank { activity.getString(R.string.drive_unknown_track) }
        // Dòng phụ: kênh · thời điểm đang dừng.
        val time = PlayerController.formatTime(point.positionSec)
        binding.resumePosition.text =
            if (point.item.channel.isBlank()) time else "${point.item.channel} · $time"

        ArtworkCache.load(point.item.id) { bitmap ->
            // app:tint trong layout là màu của ảnh giữ chỗ; còn để nguyên thì
            // nó nhuộm luôn cả ảnh bìa thật.
            binding.resumeThumb.imageTintList = null
            binding.resumeThumb.clearColorFilter()
            binding.resumeThumb.setPadding(0, 0, 0, 0)
            binding.resumeThumb.setImageBitmap(bitmap)
        }

        binding.resumeContinue.setOnClickListener {
            DriveLibrary.clearResume(activity)
            onChoice(point.item.watchUrl, point.positionSec)
            dialog.dismiss()
        }
        binding.resumeStartOver.setOnClickListener {
            DriveLibrary.clearResume(activity)
            onChoice(point.item.watchUrl, 0)
            dialog.dismiss()
        }

        dialog.show()
        return true
    }

    private companion object {
        const val MIN_POSITION_SEC = 20
    }
}
