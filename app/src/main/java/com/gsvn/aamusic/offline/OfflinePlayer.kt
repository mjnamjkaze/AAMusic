package com.gsvn.aamusic.offline

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager

/**
 * Phát các bài đã tải, không qua WebView.
 *
 * Khi đang chạy, [com.gsvn.aamusic.player.PlayerController] chuyển hướng
 * play/next/prev sang đây, nên bong bóng nổi, notification và MediaSession
 * điều khiển được y như lúc phát trực tuyến.
 */
object OfflinePlayer {

    private var player: MediaPlayer? = null
    private var queue: List<OfflineTrack> = emptyList()
    private var index: Int = -1
    private var appContext: Context? = null

    /** Gọi khi bài kết thúc hoặc người dùng chuyển bài, để UI vẽ lại. */
    var onTrackChanged: ((OfflineTrack?) -> Unit)? = null

    val isActive: Boolean get() = player != null

    val currentTrack: OfflineTrack? get() = queue.getOrNull(index)

    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun play(context: Context, track: OfflineTrack, playlist: List<OfflineTrack>) {
        val ctx = context.applicationContext
        appContext = ctx
        queue = playlist.ifEmpty { listOf(track) }
        index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        start(ctx)
    }

    private fun start(ctx: Context) {
        val track = currentTrack ?: return
        stopPlayer()

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            runCatching {
                setDataSource(OfflineStore.fileOf(ctx, track).absolutePath)
                setOnCompletionListener { next() }
                setOnErrorListener { _, _, _ -> stop(); true }
                prepare()
                start()
            }.onFailure { stop() }
        }
        onTrackChanged?.invoke(currentTrack)
    }

    fun playPause() {
        val p = player ?: return
        runCatching { if (p.isPlaying) p.pause() else p.start() }
    }

    fun next() {
        val ctx = appContext ?: return
        if (queue.isEmpty()) return
        index = if (index + 1 in queue.indices) index + 1 else 0
        start(ctx)
    }

    fun previous() {
        val ctx = appContext ?: return
        if (queue.isEmpty()) return
        // Quá 3 giây thì nút lùi phát lại bài hiện tại, giống thói quen chung.
        val p = player
        if (p != null && runCatching { p.currentPosition > 3000 }.getOrDefault(false)) {
            runCatching { p.seekTo(0) }
            return
        }
        index = if (index - 1 in queue.indices) index - 1 else queue.lastIndex
        start(ctx)
    }

    fun stop() {
        stopPlayer()
        queue = emptyList()
        index = -1
        onTrackChanged?.invoke(null)
    }

    private fun stopPlayer() {
        val p = player ?: return
        player = null
        runCatching { if (p.isPlaying) p.stop() }
        runCatching { p.release() }
    }
}
