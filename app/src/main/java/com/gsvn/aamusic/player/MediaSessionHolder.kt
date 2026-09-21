package com.gsvn.aamusic.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import com.gsvn.aamusic.MainActivity

/**
 * Phiên media dùng chung cho cả app.
 *
 * Nút chuyển bài trên vô lăng / tai nghe không gửi tới activity mà tới phiên
 * media đang hoạt động của hệ thống. Trước đây phiên này nằm trong
 * [com.gsvn.aamusic.BackgroundPlaybackService] — mà service chỉ chạy khi app
 * xuống nền, nên lúc app đang hiện trên màn hình xe (Android Auto) không có
 * phiên nào nhận phím, bấm vô lăng không ăn gì.
 *
 * Giữ phiên ở đây, sống suốt vòng đời app, thì cả hai trường hợp đều nhận
 * được; service chỉ mượn lại token để dựng notification.
 */
object MediaSessionHolder {

    private var session: MediaSessionCompat? = null

    /** Service gán vào để nút "dừng hẳn" tắt luôn phát nhạc nền. */
    var onStopRequested: (() -> Unit)? = null

    private var lastTitle: String = ""
    private var lastPlaying: Boolean = false
    // Activity poll không dựng ảnh bìa; giữ lại ảnh service đã đưa để metadata
    // không mất artwork khi app quay lại foreground.
    private var lastArtwork: Bitmap? = null

    val sessionToken: MediaSessionCompat.Token?
        get() = session?.sessionToken

    /** Tạo phiên nếu chưa có; gọi lại nhiều lần vô hại. */
    fun ensure(context: Context): MediaSessionCompat {
        session?.let { return it }

        val appContext = context.applicationContext
        val created = MediaSessionCompat(appContext, "DriveTune").apply {
            @Suppress("DEPRECATION")
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(
                PendingIntent.getActivity(
                    appContext, 0,
                    Intent(appContext, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            setCallback(Callbacks)
            isActive = true
        }
        session = created
        // Phải có PlaybackState ngay từ đầu, nếu không hệ thống coi phiên là
        // chưa phát và không định tuyến phím media tới đây.
        publishState(false)
        return created
    }

    fun release() {
        onStopRequested = null
        session?.release()
        session = null
        lastTitle = ""
        lastPlaying = false
        lastArtwork = null
    }

    /**
     * Cập nhật tên bài + trạng thái. Bỏ qua nếu không đổi, tránh ghi lại
     * metadata mỗi giây trong khi vẫn poll đều.
     *
     * @return true nếu có thay đổi so với lần cập nhật trước.
     */
    fun update(title: String, playing: Boolean, artwork: Bitmap? = null): Boolean {
        if (title == lastTitle && playing == lastPlaying) return false
        lastTitle = title
        lastPlaying = playing
        if (artwork != null) lastArtwork = artwork
        publishMetadata(title, lastArtwork)
        publishState(playing)
        return true
    }

    private fun publishMetadata(title: String, artwork: Bitmap?) {
        val s = session ?: return
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, APP_LABEL)
        if (artwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
        }
        s.setMetadata(builder.build())
    }

    private fun publishState(playing: Boolean) {
        val s = session ?: return
        s.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )
    }

    /**
     * Xử lý phím media đến thẳng cửa sổ activity. Một số head unit gửi phím
     * cứng tới ứng dụng đang hiển thị chứ không qua phiên media.
     *
     * @return true nếu phím đã được xử lý.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
            // Vẫn nuốt ACTION_UP của phím đã xử lý để WebView không nhận lại.
            return event.action == KeyEvent.ACTION_UP && isMediaKey(event.keyCode)
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> { PlayerController.next(); true }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> { PlayerController.previous(); true }

            KeyEvent.KEYCODE_MEDIA_PLAY -> { PlayerController.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { PlayerController.pause(); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> { PlayerController.playPause(); true }

            else -> false
        }
    }

    private fun isMediaKey(keyCode: Int): Boolean = keyCode in setOf(
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK
    )

    private object Callbacks : MediaSessionCompat.Callback() {
        override fun onPlay() = PlayerController.play()
        override fun onPause() = PlayerController.pause()
        override fun onSkipToNext() = PlayerController.next()
        override fun onSkipToPrevious() = PlayerController.previous()
        // Không có service nền nào đang chạy thì "dừng hẳn" rút về tạm dừng.
        override fun onStop() {
            val stop = onStopRequested
            if (stop != null) stop() else PlayerController.pause()
        }

        // Phím nào không có transport control tương ứng (tua nhanh/tua lùi trên
        // vô lăng) vẫn nên chuyển bài, vì đây là app nghe nhạc.
        override fun onMediaButtonEvent(intent: Intent): Boolean {
            val event = intent.getParcelableExtra(
                Intent.EXTRA_KEY_EVENT, KeyEvent::class.java
            ) ?: return super.onMediaButtonEvent(intent)

            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> { PlayerController.next(); return true }
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> { PlayerController.previous(); return true }
                }
            }
            return super.onMediaButtonEvent(intent)
        }
    }

    private const val APP_LABEL = "DriveTune"
}
