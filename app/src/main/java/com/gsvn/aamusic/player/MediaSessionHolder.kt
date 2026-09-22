package com.gsvn.aamusic.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
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

    /**
     * Xử lý yêu cầu phát một mục chọn từ cây duyệt Android Auto.
     * [com.gsvn.aamusic.car.DriveBrowserService] gán vào.
     */
    var onPlayRequest: ((mediaId: String) -> Unit)? = null

    private var lastState: PlayerController.PlaybackState? = null
    // Ảnh bìa tải bất đồng bộ (ArtworkCache) nên có thể về sau trạng thái; giữ
    // lại ảnh gần nhất để metadata không mất artwork giữa hai lần cập nhật.
    private var lastArtwork: Bitmap? = null
    private var lastArtworkId: String = ""
    // Ảnh thương hiệu, dùng khi chưa tải được ảnh bìa thật của bài.
    private var fallbackArtwork: Bitmap? = null

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
        onPlayRequest = null
        session?.release()
        session = null
        lastState = null
        lastArtwork = null
        lastArtworkId = ""
        fallbackArtwork = null
    }

    /** Ảnh thương hiệu dùng khi bài chưa có ảnh bìa; chỉ cần đặt một lần. */
    fun setFallbackArtwork(bitmap: Bitmap) {
        fallbackArtwork = bitmap
        if (lastArtwork == null) lastState?.let { publishMetadata(it) }
    }

    /**
     * Cập nhật phiên theo [state]. Metadata chỉ ghi lại khi bài đổi, còn
     * PlaybackState ghi mỗi nhịp để thanh tiến độ trên màn hình khoá /
     * Android Auto chạy đúng. Ảnh bìa tải nền theo id bài, có là bơm vào ngay.
     *
     * @return true nếu bài hoặc trạng thái phát đã đổi so với lần trước.
     */
    fun update(state: PlayerController.PlaybackState): Boolean {
        val previous = lastState
        lastState = state

        val trackChanged = previous == null ||
            previous.videoId != state.videoId ||
            previous.title != state.title ||
            previous.channel != state.channel ||
            previous.durationSec != state.durationSec

        if (trackChanged) {
            publishMetadata(state)
            requestArtwork(state.videoId)
        }
        publishState(state)
        return trackChanged || previous?.playing != state.playing
    }

    /** Tải ảnh bìa của bài và ghi lại metadata khi ảnh về. */
    private fun requestArtwork(videoId: String) {
        if (videoId.isBlank() || videoId == lastArtworkId) return
        lastArtworkId = videoId
        // Chưa có ảnh mới thì bỏ ảnh của bài cũ đi, đừng để lệch bài.
        lastArtwork = ArtworkCache.cached(videoId)
        ArtworkCache.load(videoId) { bitmap ->
            if (lastArtworkId != videoId) return@load
            lastArtwork = bitmap
            lastState?.let { publishMetadata(it) }
        }
    }

    private fun publishMetadata(state: PlayerController.PlaybackState) {
        val s = session ?: return
        val artwork = lastArtwork ?: fallbackArtwork
        val builder = MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                state.title.ifBlank { APP_LABEL }
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                state.channel.ifBlank { APP_LABEL }
            )
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, APP_LABEL)
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                if (state.durationSec > 0) state.durationSec * 1000L else -1L
            )
        if (artwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artwork)
        }
        s.setMetadata(builder.build())
    }

    private fun publishState(state: PlayerController.PlaybackState) {
        val s = session ?: return
        s.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    if (state.playing) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    if (state.durationSec > 0) state.positionSec * 1000L
                    else PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    // Vị trí được bơm lại mỗi giây nên không cần hệ thống nội
                    // suy; dừng thì để tốc độ 0 cho thanh tiến độ đứng yên.
                    if (state.playing) 1f else 0f
                )
                .build()
        )
    }

    private fun publishState(playing: Boolean) =
        publishState(PlayerController.PlaybackState(playing = playing))

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
        override fun onSeekTo(pos: Long) = PlayerController.seekTo((pos / 1000).toInt())

        // "Phát <gì đó> trên DriveTune" — Trợ lý Google / Android Auto.
        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (query.isNullOrBlank()) PlayerController.play() else PlayerController.search(query)
        }

        // Một mục được chọn trong cây duyệt của Android Auto.
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val handler = onPlayRequest
            if (handler != null && !mediaId.isNullOrBlank()) handler(mediaId)
            else PlayerController.play()
        }
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
