package com.gsvn.aamusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat as MediaStyleNotif
import com.gsvn.aamusic.player.ArtworkCache
import com.gsvn.aamusic.player.MediaSessionHolder
import com.gsvn.aamusic.player.PlayerController
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Foreground service that (1) keeps the app process alive so music keeps
 * playing in the background, and (2) shows a draggable floating "bubble"
 * over other apps (e.g. Google Maps) with mini playback controls.
 *
 * The bubble floats as a small window and never steals focus, so the app
 * underneath (Maps navigation) stays fully usable.
 */
class BackgroundPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    private var windowManager: WindowManager? = null
    private var bubbleRoot: View? = null
    private var collapsedView: ImageView? = null
    private var expandedView: LinearLayout? = null
    private var titleView: TextView? = null
    private var playPauseBtn: ImageButton? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Drag-to-close target (Messenger chat-head style)
    private var closeTargetView: View? = null
    private var closeCircle: View? = null
    private var overClose = false
    private var screenW = 0
    private var screenH = 0

    // Notification hiển thị thương hiệu của app thay vì artwork của YouTube.
    // Phiên media nằm ở [MediaSessionHolder] (sống suốt vòng đời app), ở đây
    // chỉ mượn token để dựng MediaStyle.
    private val artwork: Bitmap by lazy { buildArtwork() }

    // Phiên media có thể đã đúng sẵn (activity cũng poll), nhưng notification
    // của service thì chưa — nên dò trùng riêng, đừng dùng chung với phiên.
    private var lastNotifTitle: String? = null
    private var lastNotifPlaying: Boolean? = null
    private var lastNotifVideoId: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    private val statedPoll = object : Runnable {
        override fun run() {
            if (!polling) return
            PlayerController.queryState { state ->
                val shownTitle = state.title.ifBlank { getString(R.string.bubble_playing) }
                titleView?.text = shownTitle
                playPauseBtn?.setImageResource(
                    if (state.playing) R.drawable.ic_pause else R.drawable.ic_play
                )
                MediaSessionHolder.update(state)

                if (shownTitle != lastNotifTitle || state.playing != lastNotifPlaying ||
                    state.videoId != lastNotifVideoId
                ) {
                    lastNotifTitle = shownTitle
                    lastNotifPlaying = state.playing
                    lastNotifVideoId = state.videoId
                    notify(shownTitle, state)
                    // Ảnh bìa về sau một nhịp thì dựng lại notification để
                    // thumbnail thật thay chỗ ảnh thương hiệu.
                    ArtworkCache.load(state.videoId) {
                        if (lastNotifVideoId == state.videoId) notify(shownTitle, state)
                    }
                }
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun notify(title: String, state: PlayerController.PlaybackState) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(title, state))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ArtworkCache.attach(this)
        MediaSessionHolder.ensure(this)
        MediaSessionHolder.setFallbackArtwork(artwork)
        MediaSessionHolder.onStopRequested = { stopEverything() }
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            ACTION_PLAYPAUSE -> { PlayerController.playPause(); return START_STICKY }
            ACTION_NEXT -> { PlayerController.next(); return START_STICKY }
            ACTION_PREV -> { PlayerController.previous(); return START_STICKY }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        showBubble()
        startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPolling()
        removeBubble()
        lastNotifTitle = null
        lastNotifPlaying = null
        lastNotifVideoId = null
        MediaSessionHolder.onStopRequested = null
        releaseWakeLock()
        super.onDestroy()
    }

    // ── Floating bubble ────────────────────────────────────────────

    private fun showBubble() {
        if (bubbleRoot != null) return
        if (!Settings.canDrawOverlays(this)) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val bounds = wm.currentWindowMetrics.bounds
        screenW = bounds.width()
        screenH = bounds.height()

        val root = LayoutInflater.from(this).inflate(R.layout.bubble_overlay, null)
        bubbleRoot = root
        collapsedView = root.findViewById(R.id.bubbleCollapsed)
        expandedView = root.findViewById(R.id.bubbleExpanded)
        titleView = root.findViewById(R.id.bubbleTitle)
        playPauseBtn = root.findViewById(R.id.btnPlayPause)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 220
        }

        // Drag on the collapsed circle; tap toggles to expanded. Dragging it down
        // onto the close target dismisses the bubble (Messenger chat-head style).
        collapsedView?.setOnTouchListener(DragTouchListener(enableClose = true) { expand() })
        // Drag on the expanded bar (buttons consume their own touches first).
        // Tapping the left/title area collapses back to a bubble; dragging the
        // whole bar down onto the close target dismisses it, just like the bubble.
        expandedView?.setOnTouchListener(DragTouchListener(enableClose = true) { collapse() })

        root.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { PlayerController.previous() }
        playPauseBtn?.setOnClickListener { PlayerController.playPause() }
        root.findViewById<ImageButton>(R.id.btnNext).setOnClickListener { PlayerController.next() }
        root.findViewById<ImageButton>(R.id.btnVoice).setOnClickListener { PlayerController.openVoiceSearch() }
        root.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener { PlayerController.openSearch() }
        root.findViewById<ImageButton>(R.id.btnOpen).setOnClickListener { PlayerController.openApp() }
        root.findViewById<ImageButton>(R.id.btnCollapse).setOnClickListener { collapse() }

        addCloseTarget(wm)                              // sits below the bubble
        runCatching { wm.addView(root, layoutParams) }  // bubble drawn on top
    }

    private fun expand() {
        collapsedView?.visibility = View.GONE
        expandedView?.visibility = View.VISIBLE
    }

    private fun collapse() {
        expandedView?.visibility = View.GONE
        collapsedView?.visibility = View.VISIBLE
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(statedPoll)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(statedPoll)
    }

    private fun removeBubble() {
        removeCloseTarget()
        bubbleRoot?.let { root ->
            runCatching { windowManager?.removeView(root) }
        }
        bubbleRoot = null
        collapsedView = null
        expandedView = null
        titleView = null
        playPauseBtn = null
        windowManager = null
    }

    // ── Drag-to-close target (Messenger chat-head style) ───────────

    private fun stopEverything() {
        sendBroadcast(Intent(ACTION_STOP_PLAYBACK).setPackage(packageName))
        stopPolling()
        removeBubble()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Adds the (initially hidden) close target BELOW the bubble window. */
    private fun addCloseTarget(wm: WindowManager) {
        val view = LayoutInflater.from(this).inflate(R.layout.bubble_close_target, null)
        view.visibility = View.GONE
        closeTargetView = view
        closeCircle = view.findViewById(R.id.closeCircle)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(220),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        runCatching { wm.addView(view, params) }
    }

    private fun showCloseTarget() {
        closeTargetView?.let { if (it.visibility != View.VISIBLE) it.visibility = View.VISIBLE }
    }

    private fun hideCloseTarget() {
        setOverClose(false)
        closeTargetView?.visibility = View.GONE
    }

    private fun removeCloseTarget() {
        closeTargetView?.let { runCatching { windowManager?.removeView(it) } }
        closeTargetView = null
        closeCircle = null
        overClose = false
    }

    /** Enlarges the close circle while the bubble hovers over it. */
    private fun setOverClose(over: Boolean) {
        if (over == overClose) return
        overClose = over
        val scale = if (over) 1.35f else 1f
        closeCircle?.animate()?.scaleX(scale)?.scaleY(scale)?.setDuration(120)?.start()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /**
     * Moves the bubble window while dragging; fires [onTap] when the gesture
     * was a tap (little to no movement) rather than a drag. When [enableClose]
     * is set, dragging near the bottom-center snaps onto the close target and
     * releasing there dismisses the bubble.
     */
    private inner class DragTouchListener(
        private val enableClose: Boolean,
        private val onTap: (() -> Unit)?
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false
        private val slop = ViewConfiguration.get(this@BackgroundPlaybackService).scaledTouchSlop

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true

                    if (enableClose && moved) {
                        showCloseTarget()
                        val cx = screenW / 2f
                        val cy = screenH - dp(72).toFloat()
                        val inZone = hypot(event.rawX - cx, event.rawY - cy) < dp(120)
                        setOverClose(inZone)
                        if (inZone) {
                            // Magnetize the bubble onto the close target.
                            layoutParams.x = (cx - dp(28)).toInt()
                            layoutParams.y = (cy - dp(28)).toInt()
                        } else {
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                        }
                    } else {
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                    }
                    runCatching { windowManager?.updateViewLayout(bubbleRoot, layoutParams) }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (enableClose) {
                        val wasOverClose = overClose
                        hideCloseTarget()
                        if (wasOverClose) {
                            stopEverything()
                            return true
                        }
                    }
                    if (!moved) onTap?.invoke()
                    return true
                }
            }
            return false
        }
    }

    // ── Notification / wakelock ────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps music playing when the app is in the background"
            setShowBadge(false)
            setSound(null, null)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, BackgroundPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Dựng ảnh bìa dự phòng từ chính icon của app (vô-lăng + nốt nhạc), dùng
     * khi bài chưa tải được thumbnail thật.
     *
     * Vẽ cả lớp nền lẫn lớp hình của adaptive icon để ảnh trong notification
     * trùng khớp với icon ngoài màn hình chính.
     */
    private fun buildArtwork(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Màu nền đáy, phòng khi lớp nền vector không nạp được.
        canvas.drawColor(0xFF121829.toInt())
        ContextCompat.getDrawable(this, R.drawable.ic_launcher_background)?.let { d ->
            d.setBounds(0, 0, size, size)
            d.draw(canvas)
        }
        ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)?.let { d ->
            // Lớp hình của adaptive icon nằm gọn trong vùng an toàn; vẽ tràn ra
            // ngoài một chút cho dấu hiệu lấp đầy khung ảnh bìa.
            val over = (size * 0.18f).toInt()
            d.setBounds(-over, -over, size + over, size + over)
            d.draw(canvas)
        }
        return bmp
    }

    private fun buildNotification(
        title: String = getString(R.string.bubble_playing),
        state: PlayerController.PlaybackState = PlayerController.PlaybackState()
    ): Notification {
        val playing = state.playing
        // Ảnh bìa thật của bài nếu đã tải xong, không thì ảnh thương hiệu.
        val largeIcon = ArtworkCache.cached(state.videoId) ?: artwork
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setColor(0xFFE60000.toInt())
            .setContentTitle(title)
            .setContentText(state.channel.ifBlank { getString(R.string.app_name) })
            .setLargeIcon(largeIcon)
            .setContentIntent(openPending)
            .setDeleteIntent(servicePending(ACTION_STOP, 1))
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, "Prev", servicePending(ACTION_PREV, 2))
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                "Play/Pause", servicePending(ACTION_PLAYPAUSE, 3)
            )
            .addAction(R.drawable.ic_skip_next, "Next", servicePending(ACTION_NEXT, 4))
            .addAction(R.drawable.ic_close, "Stop", servicePending(ACTION_STOP, 1))
            .setStyle(
                MediaStyleNotif.MediaStyle()
                    .setMediaSession(MediaSessionHolder.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AAMusic::BackgroundPlayback"
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // 4 hours max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    companion object {
        const val CHANNEL_ID = "background_playback"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "com.gsvn.aamusic.STOP_BG_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "com.gsvn.aamusic.STOP_PLAYBACK_BROADCAST"
        const val ACTION_PLAYPAUSE = "com.gsvn.aamusic.PLAYPAUSE"
        const val ACTION_NEXT = "com.gsvn.aamusic.NEXT"
        const val ACTION_PREV = "com.gsvn.aamusic.PREV"
        private const val POLL_INTERVAL_MS = 1000L

        fun start(context: Context) {
            val intent = Intent(context, BackgroundPlaybackService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundPlaybackService::class.java)
            context.stopService(intent)
        }
    }
}
