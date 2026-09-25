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
import com.gsvn.aamusic.car.CarPlayback
import com.gsvn.aamusic.data.VideoItem

/**
 * Phiên media dùng chung cho cả app.
 *
 * Nút chuyển bài trên vô lăng / tai nghe không gửi tới activity mà tới phiên
 * media đang hoạt động của hệ thống. Trước đây phiên này nằm trong
 * [com.gsvn.aamusic.BackgroundPlaybackService] — mà service chỉ chạy khi app
 * xuống nền, nên lúc app đang hiện trên màn hình xe (Android Auto) không có
 * phiên nào nhận phím, bấm vô lăng không ăn gì.
 *
 * Giữ phiên ở đây, sống suốt vòng đời **tiến trình**, thì cả hai trường hợp
 * đều nhận được; service chỉ mượn lại token để dựng notification.
 *
 * Không được release phiên khi activity đóng: [com.gsvn.aamusic.car.DriveBrowserService]
 * chỉ trao token cho Android Auto **một lần**, phiên bị huỷ là xe cầm một token
 * chết — mọi lần chọn bài sau đó đứng mãi ở "Đang tải dữ liệu...".
 */
object MediaSessionHolder {

    private var session: MediaSessionCompat? = null

    /** Service gán vào để nút "dừng hẳn" tắt luôn phát nhạc nền. */
    var onStopRequested: (() -> Unit)? = null

    private var appContext: Context? = null

    private var lastState: PlayerController.PlaybackState? = null
    // Ảnh bìa tải bất đồng bộ (ArtworkCache) nên có thể về sau trạng thái; giữ
    // lại ảnh gần nhất để metadata không mất artwork giữa hai lần cập nhật.
    private var lastArtwork: Bitmap? = null
    private var lastArtworkId: String = ""
    // Ảnh thương hiệu, dùng khi chưa tải được ảnh bìa thật của bài.
    private var fallbackArtwork: Bitmap? = null

    /**
     * Bài vừa được yêu cầu mà trang chưa phát tới. Trong lúc chờ, phiên báo
     * "đang tải" kèm tên bài thay vì trạng thái của trang cũ / trang trống.
     */
    private var pending: VideoItem? = null
    private var pendingSince: Long = 0L

    /** Lỗi vừa báo lên màn hình xe; giữ một lúc để người dùng kịp đọc. */
    private var errorUntil: Long = 0L

    val sessionToken: MediaSessionCompat.Token?
        get() = session?.sessionToken

    /** Tạo phiên nếu chưa có; gọi lại nhiều lần vô hại. */
    fun ensure(context: Context): MediaSessionCompat {
        session?.let { return it }

        val appContext = context.applicationContext
        this.appContext = appContext
        PlayerController.attach(appContext)
        PlaybackHost.attach(appContext)
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
        val now = System.currentTimeMillis()
        pending?.let { wanted ->
            val arrived = wanted.id.isNotBlank() && state.videoId == wanted.id && state.playing
            if (!arrived && now - pendingSince < PENDING_TIMEOUT_MS) return false
            pending = null
        }
        // Đang hiện lỗi thì đừng để nhịp poll kế tiếp xoá mất, trừ khi nhạc đã chạy.
        if (now < errorUntil && !state.playing) return false
        errorUntil = 0L

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

    /**
     * Báo ngay cho màn hình xe là đang mở [item] (BUFFERING + tên bài).
     * Android Auto hiện "đang tải" cho tới khi phiên chuyển trạng thái; không
     * có bước này thì nó cứ chờ theo trạng thái của trang cũ / trang trống.
     * [item] có id rỗng nghĩa là đang tìm, chưa biết bài nào.
     */
    fun showPending(item: VideoItem) {
        pending = item
        pendingSince = System.currentTimeMillis()
        errorUntil = 0L
        val shown = PlayerController.PlaybackState(
            title = item.title, channel = item.channel, videoId = item.id
        )
        lastState = shown
        if (item.id.isBlank()) {
            // Đang tìm, chưa biết bài: đừng để ảnh bìa của bài cũ.
            lastArtwork = null
            lastArtworkId = ""
        }
        publishMetadata(shown)
        requestArtwork(item.id)
        publishRaw(
            PlaybackStateCompat.STATE_BUFFERING,
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f
        )
    }

    /** Hiện lỗi trên màn hình xe thay vì để nó chờ mãi. */
    fun showError(message: String) {
        pending = null
        errorUntil = System.currentTimeMillis() + ERROR_HOLD_MS
        publishRaw(
            PlaybackStateCompat.STATE_ERROR,
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f, message
        )
    }

    /** Trạng thái trình phát theo lần poll gần nhất. */
    val lastPlayerState: PlayerController.PlaybackState?
        get() = lastState

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
        if (state.videoId.isNotBlank()) {
            // Android Auto đọc ảnh qua URI content:// ổn định hơn bitmap nhét
            // trong metadata (bị giới hạn kích thước qua binder).
            val art = ArtworkProvider.uriFor(state.videoId).toString()
            builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, state.videoId)
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, art)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, art)
        }
        if (artwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artwork)
        }
        s.setMetadata(builder.build())
    }

    private fun publishState(state: PlayerController.PlaybackState) = publishRaw(
        if (state.playing) PlaybackStateCompat.STATE_PLAYING
        else PlaybackStateCompat.STATE_PAUSED,
        if (state.durationSec > 0) state.positionSec * 1000L
        else PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
        // Vị trí được bơm lại mỗi giây nên không cần hệ thống nội
        // suy; dừng thì để tốc độ 0 cho thanh tiến độ đứng yên.
        if (state.playing) 1f else 0f
    )

    private fun publishRaw(state: Int, positionMs: Long, speed: Float, error: String? = null) {
        val s = session ?: return
        val builder = PlaybackStateCompat.Builder()
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
            .setState(state, positionMs, speed)
        if (error != null) {
            builder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, error)
        }
        s.setPlaybackState(builder.build())
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
        // Bấm play trên xe lúc chưa có bài nào mở thì phải tự chọn bài để
        // phát — nếu không nút play chẳng có tác dụng gì.
        override fun onPlay() {
            val ctx = appContext ?: return PlayerController.play()
            PlaybackHost.keepAlive(ctx)
            CarPlayback.playOrResume(ctx)
        }
        override fun onPause() = PlayerController.pause()
        // Có hàng chờ (danh sách vừa chọn trên xe) thì đi theo hàng chờ.
        override fun onSkipToNext() {
            val ctx = appContext ?: return PlayerController.next()
            CarPlayback.next(ctx)
        }
        override fun onSkipToPrevious() = PlayerController.previous()
        override fun onSeekTo(pos: Long) = PlayerController.seekTo((pos / 1000).toInt())

        // "Phát <gì đó> trên DriveTune" — Trợ lý Google / tìm kiếm trên Android Auto.
        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            val ctx = appContext ?: return
            PlaybackHost.keepAlive(ctx)
            CarPlayback.playQuery(ctx, query.orEmpty())
        }

        // Một mục được chọn trong cây duyệt của Android Auto.
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val ctx = appContext ?: return
            PlaybackHost.keepAlive(ctx)
            CarPlayback.playMediaId(ctx, mediaId.orEmpty())
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

    /** Quá lâu mà trang chưa phát được thì thôi chờ, trả về trạng thái thật. */
    private const val PENDING_TIMEOUT_MS = 30_000L
    private const val ERROR_HOLD_MS = 8_000L
}
