package com.gsvn.aamusic.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.player.ArtworkCache
import com.gsvn.aamusic.player.PlayerController

/**
 * Drive Mode — lớp phủ điều khiển dành cho lúc đang lái.
 *
 * Không phải activity hay fragment riêng: chỉ là một view trong
 * `activity_main.xml` được bật/tắt. Nhờ vậy WebView phía dưới **không hề bị
 * đụng tới** — nhạc không gợn một nhịp khi vào/ra Drive Mode, và cũng không
 * sinh thêm tầng điều hướng nào.
 *
 * Lớp này chỉ vẽ; mọi lệnh phát đều đi qua [PlayerController] như phần còn lại
 * của app.
 */
class DriveMode(
    private val activity: Activity,
    private val root: View,
    private val onOpenLibrary: () -> Unit
) {

    private val artwork: ImageView = root.findViewById(R.id.driveArtwork)
    private val title: TextView = root.findViewById(R.id.driveTitle)
    private val channel: TextView = root.findViewById(R.id.driveChannel)
    private val position: TextView = root.findViewById(R.id.drivePosition)
    private val duration: TextView = root.findViewById(R.id.driveDuration)
    private val progress: LinearProgressIndicator = root.findViewById(R.id.driveProgress)
    private val playPause: ImageButton = root.findViewById(R.id.drivePlayPause)
    private val favorite: ImageButton = root.findViewById(R.id.driveFavorite)

    private val audioManager =
        activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Bài đang hiện trên màn hình — để biết khi nào cần đổi ảnh bìa. */
    private var shownVideoId: String = ""

    val isVisible: Boolean get() = root.visibility == View.VISIBLE

    init {
        root.findViewById<View>(R.id.driveExit).setOnClickListener { hide() }
        root.findViewById<View>(R.id.drivePrev).setOnClickListener { PlayerController.previous() }
        root.findViewById<View>(R.id.driveNext).setOnClickListener { PlayerController.next() }
        playPause.setOnClickListener { PlayerController.playPause() }
        root.findViewById<View>(R.id.driveQueue).setOnClickListener { onOpenLibrary() }
        root.findViewById<View>(R.id.driveVolume).setOnClickListener { showVolumePanel() }
        favorite.setOnClickListener { toggleFavorite() }
    }

    fun show() {
        if (isVisible) return
        root.visibility = View.VISIBLE
        root.bringToFront()
        // Màn hình xe hay bị tắt giữa chừng; giữ sáng trong lúc Drive Mode mở.
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun hide() {
        if (!isVisible) return
        root.visibility = View.GONE
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Vẽ lại theo trạng thái trình phát. Được gọi mỗi nhịp poll của
     * MainActivity nên phải rẻ: chỉ đụng tới view khi giá trị thật sự đổi.
     */
    fun render(state: PlayerController.PlaybackState) {
        if (!isVisible) return

        val shownTitle = state.title.ifBlank { activity.getString(R.string.drive_nothing_playing) }
        if (title.text != shownTitle) title.text = shownTitle
        if (channel.text != state.channel) channel.text = state.channel

        playPause.setImageResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
        playPause.contentDescription = activity.getString(
            if (state.playing) R.string.drive_pause else R.string.drive_play
        )

        position.text = PlayerController.formatTime(state.positionSec)
        duration.text = PlayerController.formatTime(state.durationSec)
        val percent = if (state.durationSec > 0) {
            (state.positionSec * 100 / state.durationSec).coerceIn(0, 100)
        } else 0
        progress.setProgressCompat(percent, true)

        if (state.videoId != shownVideoId) {
            shownVideoId = state.videoId
            renderArtwork(state.videoId)
            renderFavorite(DriveLibrary.isFavorite(activity, state.videoId))
        }
    }

    private fun renderArtwork(videoId: String) {
        if (videoId.isBlank()) {
            artwork.setImageResource(R.drawable.ic_music_note)
            artwork.imageTintList = null
            artwork.setColorFilter(activity.getColor(R.color.drive_text_dim))
            return
        }
        ArtworkCache.cached(videoId)?.let { applyArtwork(videoId, it); return }
        ArtworkCache.load(videoId) { bitmap -> applyArtwork(videoId, bitmap) }
    }

    private fun applyArtwork(videoId: String, bitmap: android.graphics.Bitmap) {
        // Ảnh về muộn hơn lần chuyển bài kế tiếp thì bỏ, đừng dán nhầm bìa.
        if (shownVideoId != videoId) return
        // app:tint trong layout là màu của ảnh giữ chỗ; còn để nguyên thì nó
        // nhuộm luôn cả ảnh bìa thật.
        artwork.imageTintList = null
        artwork.clearColorFilter()
        artwork.setImageBitmap(bitmap)
    }

    /** Gọi sau khi thư viện đổi ở nơi khác (bảng Hàng chờ) để nút đồng bộ lại. */
    fun refreshFavorite() {
        renderFavorite(DriveLibrary.isFavorite(activity, shownVideoId))
    }

    private fun renderFavorite(on: Boolean) {
        favorite.setImageResource(
            if (on) R.drawable.ic_favorite_filled else R.drawable.favorite_24px
        )
        favorite.imageTintList = android.content.res.ColorStateList.valueOf(
            activity.getColor(if (on) R.color.drive_accent else R.color.drive_text)
        )
        favorite.contentDescription = activity.getString(
            if (on) R.string.drive_unfavorite else R.string.drive_favorite
        )
    }

    private fun toggleFavorite() {
        // Trạng thái poll gần nhất đã nằm sẵn ở shownVideoId; chưa có bài thì
        // không có gì để lưu.
        PlayerController.queryState { state ->
            val item = state.toItem()
            if (item == null) {
                toast(R.string.drive_favorite_unavailable)
                return@queryState
            }
            val added = DriveLibrary.toggleFavorite(activity, item)
            renderFavorite(added)
            toast(if (added) R.string.drive_favorite_added else R.string.drive_favorite_removed)
        }
    }

    /**
     * Mở thanh âm lượng của hệ thống thay vì tự vẽ một cái.
     *
     * Đang lái thì thanh quen thuộc của máy dễ dùng hơn bất cứ thứ gì app tự
     * nghĩ ra, lại chỉnh đúng luồng nhạc đang phát qua dàn của xe.
     */
    private fun showVolumePanel() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(activity, resId, android.widget.Toast.LENGTH_SHORT).show()
    }
}
