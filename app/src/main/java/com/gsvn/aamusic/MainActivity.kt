package com.gsvn.aamusic

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Display
import android.view.KeyEvent
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.gsvn.aamusic.car.CarConnection
import com.gsvn.aamusic.car.CarPlayback
import com.gsvn.aamusic.data.AppUpdate
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.data.DrivePlaylist
import com.gsvn.aamusic.data.DrivePlaylists
import com.gsvn.aamusic.data.DriveSettings
import com.gsvn.aamusic.data.SearchHistory
import com.gsvn.aamusic.data.SearchSuggest
import com.gsvn.aamusic.data.VideoItem
import com.gsvn.aamusic.databinding.ActivityMainBinding
import com.gsvn.aamusic.player.ArtworkCache
import com.gsvn.aamusic.player.MediaSessionHolder
import com.gsvn.aamusic.player.PlaybackHost
import com.gsvn.aamusic.player.PlayerController
import com.gsvn.aamusic.ui.DriveMode
import com.gsvn.aamusic.ui.LibrarySheet
import com.gsvn.aamusic.ui.ResumeSheet
import com.gsvn.aamusic.ui.SettingsSheet
import com.gsvn.aamusic.voice.VoiceCommands
import com.gsvn.aamusic.voice.VoiceListener
import com.gsvn.aamusic.web.BrowserCallbacks
import com.gsvn.aamusic.web.configureWebView
import com.gsvn.aamusic.web.VideoMode
import com.gsvn.aamusic.web.releaseCompletely

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var webView: android.webkit.WebView? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var isBackgroundPlaybackActive: Boolean = false

    // ── Nhịp trạng thái ─────────────────────────────────────────────
    // PlaybackHost poll một chỗ cho cả app (phiên media, "Vừa nghe", hàng
    // chờ…); activity chỉ nghe để vẽ Chế độ lái khi đang hiển thị.
    private val mediaHandler = Handler(Looper.getMainLooper())
    private val stateListener: (PlayerController.PlaybackState) -> Unit =
        { state -> onPlaybackState(state) }

    /** WebView nhận lại từ PlaybackHost — nhạc đang phát sẵn, đừng mở trang chủ. */
    private var adoptedPlayer: Boolean = false

    // ── Chế độ lái + thư viện ───────────────────────────────────────
    private var driveMode: DriveMode? = null
    private lateinit var carConnection: CarConnection

    /** Nghe giọng nói ngay trong app, không qua hộp thoại của hệ thống. */
    private val voiceListener by lazy { VoiceListener(this) }

    // ── Live YouTube search suggestions ─────────────────────────────
    private var suggestJob: kotlinx.coroutines.Job? = null
    private var remoteSuggestions: List<String> = emptyList()
    private var remoteSuggestQuery: String = ""

    // ── Voice search ────────────────────────────────────────────────
    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == RESULT_OK && !text.isNullOrBlank()) {
            handleVoiceResult(text)
        }
    }

    // ── Audio Focus ─────────────────────────────────────────────────
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            // Chỉ đường dẫn của Maps, cuộc gọi… cướp focus làm trang tự dừng;
            // giành lại được thì phát tiếp, người dùng không phải bấm gì.
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                webView?.onResume()
                // Trang cần một nhịp để nhả lại luồng audio trước khi play() ăn.
                mediaHandler.postDelayed({ PlayerController.resume() }, RESUME_AFTER_FOCUS_MS)
            }
            else -> {
                // Keep playing — BackgroundPlaybackService keeps the WebView alive
                // and YouTube Music manages its own volume.
            }
        }
    }

    private val stopPlaybackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BackgroundPlaybackService.ACTION_STOP_PLAYBACK) {
                isBackgroundPlaybackActive = false
                webView?.onPause()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Phải đặt trước khi dựng view, nếu không theme áp muộn sẽ nháy màu.
        applyNightMode()
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Pick best display mode (highest refresh rate)
        val best = display?.supportedModes?.maxWithOrNull(
            compareBy({ it.refreshRate }, { it.physicalWidth.toLong() * it.physicalHeight })
        )
        best?.let { window.attributes = window.attributes.apply { preferredDisplayModeId = it.modeId } }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        requestAudioFocus()
        ArtworkCache.attach(this)
        MediaSessionHolder.ensure(this)
        PlaybackHost.attach(this)

        setupWebView()
        setupSearchBar()
        setupDriveMode()
        setupBackPressHandling()
        ensureStartupPermissions()
        maybeRequestOverlayPermission()
        registerReceiver(
            stopPlaybackReceiver,
            IntentFilter(BackgroundPlaybackService.ACTION_STOP_PLAYBACK),
            RECEIVER_NOT_EXPORTED
        )

        carConnection = CarConnection(this).apply {
            onCarConnected = {
                Toast.makeText(
                    this@MainActivity, R.string.car_connected_resume, Toast.LENGTH_SHORT
                ).show()
            }
            register()
        }

        // handleSearchIntent() nuốt luôn action sau khi xử lý, nên phải xem
        // trước: app được mở kèm yêu cầu phát nhạc thì đừng chen bảng "nghe tiếp".
        val launchedWithRequest = intent?.action == ACTION_SEARCH
        handleSearchIntent(intent)
        // Chỉ hỏi ở lần mở mới, không hỏi lại sau khi xoay máy; nhạc đang phát
        // sẵn (nhận lại từ Android Auto) thì cũng không hỏi.
        if (savedInstanceState == null && !launchedWithRequest && !adoptedPlayer) {
            maybeOfferResume()
        }
        if (savedInstanceState == null) checkForUpdate()
    }

    /**
     * Hỏi GitHub có bản mới không (AppUpdate tự giãn nhịp hỏi). Có thì báo một
     * câu, mỗi bản đúng một lần; nút cập nhật nằm trong Cài đặt, trên Giới thiệu.
     * Trên màn hình xe thì im — đang lái, đừng chen chữ.
     */
    private fun checkForUpdate() {
        AppUpdate.check(this, lifecycleScope) { release ->
            if (release == null || isCarDisplay()) return@check
            if (!AppUpdate.markNotified(this, release.version)) return@check
            Toast.makeText(
                this, getString(R.string.update_available_toast, release.version),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Chế độ lái ─────────────────────────────────────────────────

    private fun setupDriveMode() {
        val root = binding.root.findViewById<View>(R.id.driveRoot) ?: return
        driveMode = DriveMode(this, root, onOpenLibrary = { showLibrary() })
        binding.driveModeButton.setOnClickListener { enterDriveMode() }
        binding.libraryButton.setOnClickListener { showLibrary() }
        if (DriveSettings.isOn(this, DriveSettings.KEY_DRIVE_ON_START)) enterDriveMode()
    }

    /**
     * Activity khai báo `configChanges` cho cả orientation (để xoay máy không
     * nạp lại trang web), nên hệ thống KHÔNG tự lấy lại layout theo hướng mới.
     * Lớp phủ Chế độ lái có bản riêng cho màn hình ngang — chính là hình dạng
     * màn hình xe — nên phải tự dựng lại nó ở đây, nếu không bản ngang sẽ chẳng
     * bao giờ được dùng trên điện thoại.
     *
     * Chỉ thay đúng lớp phủ; WebView bên dưới không bị đụng tới nên nhạc vẫn chạy.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildDriveMode()
    }

    private fun rebuildDriveMode() {
        val parent = binding.root as? ViewGroup ?: return
        val old = parent.findViewById<View>(R.id.driveRoot) ?: return
        val wasVisible = driveMode?.isVisible == true

        parent.removeView(old)
        val fresh = layoutInflater.inflate(R.layout.view_drive_mode, parent, false)
        parent.addView(fresh)

        driveMode = DriveMode(this, fresh, onOpenLibrary = { showLibrary() })
        if (wasVisible) {
            driveMode?.show()
            PlayerController.queryState { state -> driveMode?.render(state) }
        }
    }

    /**
     * Mở Chế độ lái. Chưa có gì đang phát mà người dùng đã đặt danh sách mặc
     * định thì mở luôn danh sách đó — vào xe là có nhạc, không phải tìm.
     */
    private fun enterDriveMode() {
        val mode = driveMode ?: return
        mode.show()
        PlayerController.queryState { state ->
            // Vẽ ngay, đừng để màn hình trống tới nhịp poll kế tiếp.
            mode.render(state)
            if (state.hasTrack) return@queryState
            val playlist = DrivePlaylists.byId(DriveSettings.defaultPlaylist(this))
                ?: return@queryState
            openPlaylist(playlist)
        }
    }

    private fun showLibrary() {
        val sheet = LibrarySheet(
            this,
            onPlay = { item -> playTrack(item) },
            onPlaylist = { playlist -> openPlaylist(playlist) }
        )
        sheet.onLibraryChanged = { driveMode?.refreshFavorite() }
        sheet.show()
    }

    private fun playTrack(item: VideoItem) {
        PlaybackHost.play(this, item)
    }

    /**
     * Mở một danh sách dựng sẵn: phát ngay bài đầu, các bài còn lại vào hàng
     * chờ. Danh sách là một từ khoá tìm kiếm, được phân giải thành bài cụ thể
     * (CarPlayback) — không bắt người dùng rời Chế độ lái để tự chọn bài nữa.
     */
    private fun openPlaylist(playlist: DrivePlaylist) {
        if (playlist.isLocal && DriveLibrary.favorites(this).isEmpty()) {
            Toast.makeText(this, R.string.library_empty_favorites, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(
            this,
            getString(R.string.voice_playing_playlist, getString(playlist.nameRes)),
            Toast.LENGTH_SHORT
        ).show()
        CarPlayback.playPlaylist(this, playlist)
    }

    private fun maybeOfferResume() {
        if (!DriveSettings.isOn(this, DriveSettings.KEY_RESUME)) return
        ResumeSheet(this).showIfAvailable { url, startSec ->
            // YouTube hiểu tham số `t` ngay trên URL, khỏi phải canh lúc trang
            // dựng xong trình phát rồi mới tua.
            val target = if (startSec > 0) "$url&t=${startSec}s" else url
            webView?.loadUrl(target)
        }
    }

    // ── Nhịp trạng thái ────────────────────────────────────────────

    /**
     * Một nhịp poll khi activity đang hiển thị: vẽ lại Chế độ lái. Phiên media,
     * "Vừa nghe", điểm nghe tiếp và hàng chờ do [PlaybackHost] lo cho cả lúc
     * app chạy nền.
     */
    private fun onPlaybackState(state: PlayerController.PlaybackState) {
        driveMode?.render(state)
    }

    /**
     * Một số head unit gửi phím chuyển bài trên vô lăng thẳng tới cửa sổ đang
     * hiển thị thay vì qua phiên media. Bắt ở đây trước khi WebView nuốt mất.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (MediaSessionHolder.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSearchIntent(intent)
    }

    // ── Audio Focus ─────────────────────────────────────────────────

    private fun requestAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()

        audioManager.requestAudioFocus(audioFocusRequest!!)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    override fun onResume() {
        super.onResume()
        val wasBackgroundActive = isBackgroundPlaybackActive
        // Dừng cả khi chính activity không mở nó: PlaybackHost cũng mở service
        // này lúc phát từ Android Auto.
        BackgroundPlaybackService.stop(this)
        isBackgroundPlaybackActive = false
        requestAudioFocus()
        if (!wasBackgroundActive) {
            webView?.onResume()
        }
        updateOverlayButton()
        PlaybackHost.activityResumed = true
        PlaybackHost.addListener(stateListener)
    }

    override fun onPause() {
        voiceListener.cancel()
        PlaybackHost.activityResumed = false
        PlaybackHost.removeListener(stateListener)
        exitFullscreen()
        isBackgroundPlaybackActive = true
        BackgroundPlaybackService.start(this)
        super.onPause()
        // Do NOT abandon audio focus — playback continues in background.
    }

    override fun onDestroy() {
        if (::carConnection.isInitialized) carConnection.unregister()
        BackgroundPlaybackService.stop(this)
        isBackgroundPlaybackActive = false
        abandonAudioFocus()
        runCatching { unregisterReceiver(stopPlaybackReceiver) }
        exitFullscreen()
        mediaHandler.removeCallbacksAndMessages(null)
        PlaybackHost.activityResumed = false
        PlaybackHost.removeListener(stateListener)
        // KHÔNG release phiên media: Android Auto đang giữ token của nó, huỷ đi
        // là mọi lần chọn bài trên xe sau đó treo ở "Đang tải dữ liệu...".
        webView?.let { view ->
            PlayerController.unregister(view)
            view.releaseCompletely()
        }
        webView = null
        super.onDestroy()
    }

    // ── WebView setup ──────────────────────────────────────────────

    private fun setupWebView() {
        val callbacks = BrowserCallbacks(
            onProgressChange = { progress ->
                runOnUiThread {
                    binding.progressIndicator.visibility =
                        if (progress in 1..99) View.VISIBLE else View.GONE
                    if (progress in 1..99) binding.progressIndicator.setProgressCompat(progress, true)
                }
            },
            onEnterFullscreen = { view, callback ->
                runOnUiThread { enterFullscreen(view, callback) }
            },
            onExitFullscreen = {
                runOnUiThread { exitFullscreen(fromWebChrome = true) }
            },
            onPermissionRequest = { request ->
                runOnUiThread { handlePermissionRequest(request) }
            },
            onFocusNativeSearch = {
                runOnUiThread {
                    showSuggestions()
                    binding.searchInput.requestFocus()
                    binding.searchInput.post { showKeyboard() }
                }
            },
            // Trang vừa nạp xong đã có script ép chất lượng; đồng bộ lại cờ
            // theo tuỳ chọn hiện tại của người dùng.
            onPageFinished = { runOnUiThread { applyDataSaver() } }
        )

        // Android Auto đã dựng trình phát ngầm (lúc app chưa mở): gắn chính
        // WebView đó vào giao diện thay cho WebView trống của layout, để nhạc
        // đang phát không bị ngắt.
        val adopted = PlaybackHost.adoptHeadless(this)
        if (adopted != null) {
            swapInWebView(adopted)
            adoptedPlayer = true
        }

        webView = if (adopted != null) adopted else binding.webView
        webView?.let { view ->
            configureWebView(view, callbacks, installDocumentStartScripts = adopted == null)
            PlayerController.register(view)
            if (adopted == null) view.loadUrl(HOME_URL)
        }
    }

    /** Thay WebView trống của layout bằng [player], giữ nguyên vị trí/kích thước. */
    private fun swapInWebView(player: android.webkit.WebView) {
        val placeholder = binding.webView
        val parent = placeholder.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(placeholder)
        val params = placeholder.layoutParams
        parent.removeView(placeholder)
        placeholder.destroy()
        (player.parent as? ViewGroup)?.removeView(player)
        player.overScrollMode = View.OVER_SCROLL_NEVER
        parent.addView(player, index, params)
    }

    // ── Search bar ─────────────────────────────────────────────────

    private fun setupSearchBar() {
        binding.searchInput.setOnEditorActionListener { v, _, _ ->
            submitSearch(v.text?.toString().orEmpty())
            true
        }
        binding.searchButton.setOnClickListener {
            val text = binding.searchInput.text?.toString().orEmpty()
            if (text.isBlank()) {
                binding.searchInput.requestFocus()
                showSuggestions()
                showKeyboard()
            } else {
                submitSearch(text)
            }
        }
        // Quick-clear the whole query being typed.
        binding.clearButton.setOnClickListener {
            binding.searchInput.setText("")
            binding.searchInput.requestFocus()
            showSuggestions()
            showKeyboard()
        }
        binding.overlayButton.setOnClickListener { openOverlaySettings() }
        binding.headerLogo.contentDescription = getString(R.string.settings_open)
        binding.headerLogo.setOnClickListener {
            SettingsSheet(this) { key -> onSettingChanged(key) }.show()
        }
        binding.micButton.setOnClickListener { startVoiceSearch() }
        binding.searchInput.doAfterTextChanged { text ->
            binding.clearButton.visibility =
                if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            requestRemoteSuggestions(text?.toString().orEmpty())
            if (binding.searchInput.hasFocus()) showSuggestions()
        }
        binding.searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showSuggestions()
                if (isCarDisplay()) showKeyboard()
            } else binding.searchInput.post {
                if (!binding.searchInput.hasFocus()) {
                    hideSuggestions()
                    if (isCarDisplay()) hideKeyboard()
                }
            }
        }
        setupCarKeyboard()
    }

    /** Áp dụng ngay những tuỳ chọn có tác dụng tức thì. */
    private fun onSettingChanged(key: String) {
        when (key) {
            DriveSettings.KEY_FORCE_DARK -> applyNightMode()
            DriveSettings.KEY_DATA_SAVER,
            DriveSettings.KEY_SHOW_VIDEO -> applyDataSaver()
            DriveSettings.KEY_PLAYER_BG -> {
                // Ảnh nền: vẽ lại cả trên trang lẫn trong Chế độ lái.
                applyDataSaver()
                driveMode?.applyBackground()
            }
        }
    }

    private fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            if (DriveSettings.isOn(this, DriveSettings.KEY_FORCE_DARK)) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** Áp "Hiện video" + "Tiết kiệm dữ liệu" lên trang đang mở (xem VideoMode). */
    private fun applyDataSaver() {
        VideoMode.applyPrefs(this, webView)
    }

    // ── In-app keyboard (Android Auto car display) ─────────────────

    /**
     * True when the activity is projected onto an Android Auto car screen, where
     * the system IME renders unusably small/misaligned. Detected via a car UI
     * mode or the activity being on a non-default (virtual) display.
     */
    private fun isCarDisplay(): Boolean {
        val uiCar = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_CAR
        val secondary = (display?.displayId ?: Display.DEFAULT_DISPLAY) != Display.DEFAULT_DISPLAY
        return uiCar || secondary
    }

    private fun setupCarKeyboard() {
        binding.carKeyboard.onKey = { c -> insertIntoSearch(c.toString()) }
        binding.carKeyboard.onBackspace = { backspaceSearch() }
        binding.carKeyboard.onSearch = {
            submitSearch(binding.searchInput.text?.toString().orEmpty())
        }
        binding.carKeyboard.onVoice = { startVoiceSearch() }
        // Keep the system IME from popping (and rendering tiny) on the car screen.
        if (isCarDisplay()) binding.searchInput.showSoftInputOnFocus = false
    }

    private fun insertIntoSearch(s: String) {
        val et = binding.searchInput
        val editable = et.text ?: return
        val a = et.selectionStart.coerceIn(0, editable.length)
        val b = et.selectionEnd.coerceIn(0, editable.length)
        editable.replace(minOf(a, b), maxOf(a, b), s)
    }

    private fun backspaceSearch() {
        val et = binding.searchInput
        val editable = et.text ?: return
        val a = et.selectionStart
        val b = et.selectionEnd
        if (a != b) editable.delete(minOf(a, b), maxOf(a, b))
        else if (a > 0) editable.delete(a - 1, a)
    }

    /**
     * Nghe một câu nói rồi xử lý luôn. Nghe thẳng bằng SpeechRecognizer (không
     * cần hộp thoại — hộp thoại hệ thống không hiện được trên màn hình xe);
     * máy không có dịch vụ nhận dạng thì mới rơi về hộp thoại cũ.
     */
    private fun startVoiceSearch() {
        val canRecord = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (canRecord && voiceListener.isAvailable) {
            Toast.makeText(this, R.string.voice_search_prompt, Toast.LENGTH_SHORT).show()
            voiceListener.listen(
                onResult = { text -> handleVoiceResult(text) },
                onFail = {
                    Toast.makeText(this, R.string.voice_not_heard, Toast.LENGTH_SHORT).show()
                }
            )
            return
        }
        startVoiceSearchDialog()
    }

    private fun startVoiceSearchDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_prompt))
        }
        runCatching { voiceSearchLauncher.launch(intent) }.onFailure {
            Toast.makeText(this, R.string.voice_search_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Xử lý câu vừa đọc: hiểu thành lệnh điều khiển nếu người dùng bật tuỳ chọn,
     * còn lại là tìm kiếm — và **phát luôn bài đầu tiên**, vì đọc lệnh thường là
     * lúc đang lái, không rảnh tay chọn trong trang kết quả.
     */
    private fun handleVoiceResult(spoken: String) {
        if (!DriveSettings.isOn(this, DriveSettings.KEY_VOICE_COMMANDS)) {
            voiceSearchAndPlay(spoken)
            return
        }
        when (val action = VoiceCommands.parse(spoken)) {
            is VoiceCommands.Action.Next -> PlayerController.next()
            is VoiceCommands.Action.Previous -> PlayerController.previous()
            is VoiceCommands.Action.Pause -> PlayerController.pause()
            is VoiceCommands.Action.Resume -> PlayerController.play()
            is VoiceCommands.Action.OpenPlaylist -> openPlaylist(action.playlist)
            is VoiceCommands.Action.Search -> voiceSearchAndPlay(action.query)
        }
    }

    private fun voiceSearchAndPlay(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        SearchHistory.add(this, text)
        binding.searchInput.setText(text)
        binding.searchInput.setSelection(text.length)
        binding.searchInput.clearFocus()
        hideSuggestions()
        hideKeyboard()
        Toast.makeText(this, getString(R.string.voice_searching, text), Toast.LENGTH_SHORT).show()
        CarPlayback.searchAndPlay(this, text) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Fetches live YouTube suggestions for [raw] (debounced), then refreshes the
     * dropdown if the query is still current. Cancels any in-flight request.
     */
    private fun requestRemoteSuggestions(raw: String) {
        val query = raw.trim()
        suggestJob?.cancel()
        if (query.isEmpty()) {
            remoteSuggestions = emptyList()
            remoteSuggestQuery = ""
            return
        }
        suggestJob = lifecycleScope.launch {
            delay(250)
            val results = SearchSuggest.fetch(query)
            remoteSuggestQuery = query
            remoteSuggestions = results
            // Only repaint if the user hasn't typed something else in the meantime.
            if (binding.searchInput.hasFocus() &&
                binding.searchInput.text?.toString().orEmpty().trim() == query
            ) {
                showSuggestions()
            }
        }
    }

    /** Rebuilds and shows the suggestions dropdown, filtered by the current query. */
    private fun showSuggestions() {
        val query = binding.searchInput.text?.toString().orEmpty().trim()
        val list = binding.suggestionsList
        list.removeAllViews()

        val pinned = SearchHistory.pinned(this)
        // History = pinned first, then the rest of recents (deduped), all filtered.
        val history = (pinned + SearchHistory.recent(this)
            .filterNot { r -> pinned.any { it.equals(r, ignoreCase = true) } })
            .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }

        // Live YouTube suggestions, only valid for the query they were fetched for.
        val remote = if (query.isNotEmpty() && remoteSuggestQuery == query) {
            remoteSuggestions.filterNot { s -> history.any { it.equals(s, ignoreCase = true) } }
        } else emptyList()

        // Static keyword list: shown when idle (empty query) or as a fallback while
        // there is no live result for the current query.
        val suggestions = if (query.isEmpty() || remote.isEmpty()) {
            SearchHistory.SUGGESTIONS
                .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
                .filterNot { s -> history.any { it.equals(s, ignoreCase = true) } }
        } else emptyList()

        val inflater = layoutInflater
        if (history.isNotEmpty()) {
            addSuggestionHeader(
                inflater, list, getString(R.string.search_recent),
                getString(R.string.search_clear)
            ) {
                SearchHistory.clear(this)
                showSuggestions()
            }
            for (item in history) {
                addSuggestionRow(
                    inflater, list, item, isRecent = true,
                    isPinned = pinned.any { it.equals(item, ignoreCase = true) }
                )
            }
        }
        if (remote.isNotEmpty()) {
            addSuggestionHeader(
                inflater, list, getString(R.string.search_suggestions_online), null, null
            )
            for (item in remote) {
                addSuggestionRow(inflater, list, item, isRecent = false, isPinned = false)
            }
        }
        if (suggestions.isNotEmpty()) {
            addSuggestionHeader(inflater, list, getString(R.string.search_suggestions), null, null)
            for (item in suggestions) {
                addSuggestionRow(inflater, list, item, isRecent = false, isPinned = false)
            }
        }

        // Panel phủ cố định toàn vùng nội dung — không ẩn khi list tạm rỗng
        // để kích thước không nhảy trong lúc kết quả đang về dần.
        binding.suggestionsScroll.scrollTo(0, 0)
        binding.suggestionsPanel.visibility = View.VISIBLE
    }

    private fun hideSuggestions() {
        binding.suggestionsPanel.visibility = View.GONE
    }

    private fun addSuggestionHeader(
        inflater: LayoutInflater,
        parent: LinearLayout,
        title: String,
        actionText: String?,
        onAction: (() -> Unit)?
    ) {
        val row = inflater.inflate(R.layout.item_suggestion_header, parent, false)
        row.findViewById<TextView>(R.id.headerTitle).text = title
        val action = row.findViewById<TextView>(R.id.headerAction)
        if (actionText != null && onAction != null) {
            action.text = actionText
            action.visibility = View.VISIBLE
            action.setOnClickListener { onAction() }
        } else {
            action.visibility = View.GONE
        }
        parent.addView(row)
    }

    private fun addSuggestionRow(
        inflater: LayoutInflater,
        parent: LinearLayout,
        text: String,
        isRecent: Boolean,
        isPinned: Boolean
    ) {
        val row = inflater.inflate(R.layout.item_suggestion, parent, false)
        row.findViewById<ImageView>(R.id.itemIcon).setImageResource(
            when {
                isPinned -> R.drawable.ic_pin
                isRecent -> R.drawable.ic_history
                else -> R.drawable.search_24px
            }
        )
        row.findViewById<TextView>(R.id.itemText).text = text
        val pin = row.findViewById<ImageButton>(R.id.itemPin)
        val delete = row.findViewById<ImageButton>(R.id.itemDelete)
        if (isRecent) {
            pin.visibility = View.VISIBLE
            pin.setColorFilter(
                themeColor(
                    if (isPinned) androidx.appcompat.R.attr.colorPrimary
                    else com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
            pin.contentDescription =
                getString(if (isPinned) R.string.search_unpin else R.string.search_pin)
            pin.setOnClickListener {
                if (isPinned) SearchHistory.unpin(this, text) else SearchHistory.pin(this, text)
                showSuggestions()
            }
            delete.visibility = View.VISIBLE
            delete.setOnClickListener {
                SearchHistory.remove(this, text)
                showSuggestions()
            }
        } else {
            pin.visibility = View.GONE
            delete.visibility = View.GONE
        }
        row.setOnClickListener { submitSearch(text) }
        parent.addView(row)
    }

    /** Resolves a theme color attribute (e.g. colorPrimary) to a color int. */
    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun submitSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        SearchHistory.add(this, query)
        val url = "$SEARCH_URL${Uri.encode(query)}"
        webView?.loadUrl(url)
        binding.searchInput.setText(query)
        binding.searchInput.setSelection(query.length)
        binding.searchInput.clearFocus()
        hideSuggestions()
        hideKeyboard()
    }

    private fun handleSearchIntent(intent: Intent?) {
        if (intent?.action != ACTION_SEARCH) return
        val url = intent.getStringExtra(EXTRA_URL)
        val query = intent.getStringExtra(EXTRA_QUERY)
        if (!url.isNullOrBlank()) {
            // Đến từ Android Auto / bong bóng khi WebView đã bị thu hồi.
            webView?.loadUrl(url)
        } else if (!query.isNullOrBlank()) {
            binding.searchInput.setText(query)
            submitSearch(query)
        } else if (intent.getBooleanExtra(EXTRA_START_VOICE, false)) {
            // Mở thẳng hộp thoại đọc: đang lái thì không gõ được.
            startVoiceSearch()
        } else if (intent.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) {
            showSuggestions()
            binding.searchInput.requestFocus()
            binding.searchInput.post { showKeyboard() }
        }
        // Consume so it doesn't re-fire on rotation/resume.
        intent.action = null
    }

    private fun showKeyboard() {
        if (isCarDisplay()) {
            binding.searchInput.showSoftInputOnFocus = false
            binding.searchInput.requestFocus()
            binding.carKeyboard.visibility = View.VISIBLE
            return
        }
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        if (isCarDisplay()) {
            binding.carKeyboard.visibility = View.GONE
            return
        }
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    // ── Fullscreen ─────────────────────────────────────────────────

    private fun isInFullscreen(): Boolean = customView != null

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) { callback.onCustomViewHidden(); return }
        (view.parent as? ViewGroup)?.removeView(view)
        customView = view
        customViewCallback = callback
        webView?.visibility = View.INVISIBLE
        binding.fullscreenContainer.apply {
            visibility = View.VISIBLE
            removeAllViews()
            addView(view, FrameLayout.LayoutParams(-1, -1))
            addFullscreenControls(this)
            bringToFront()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, binding.fullscreenContainer)
            .hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Nút nổi (thoát fullscreen / thu nhỏ bubble) đè lên góc trên-phải video. */
    private fun addFullscreenControls(container: FrameLayout) {
        val controls = layoutInflater.inflate(R.layout.fullscreen_controls, container, false)
        controls.findViewById<View>(R.id.fsExitButton).setOnClickListener { exitFullscreen() }
        controls.findViewById<View>(R.id.fsBubbleButton).setOnClickListener { minimizeToBubble() }
        val margin = (16 * resources.displayMetrics.density).toInt()
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = margin
            marginEnd = margin
        }
        container.addView(controls, lp)
    }

    /**
     * Đưa app về nền: onPause khởi động BackgroundPlaybackService — nhạc tiếp
     * tục phát và bubble nổi xuất hiện. Chưa có quyền overlay thì mở màn cấp
     * quyền thay vì thu nhỏ "câm".
     */
    private fun minimizeToBubble() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        moveTaskToBack(true)
    }

    private fun exitFullscreen(fromWebChrome: Boolean = false) {
        if (customView == null) return
        binding.fullscreenContainer.apply { removeAllViews(); visibility = View.GONE }
        webView?.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, binding.root)
            .show(WindowInsetsCompat.Type.systemBars())
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        if (!fromWebChrome) callback?.onCustomViewHidden()
    }

    // ── Back press ─────────────────────────────────────────────────

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                driveMode?.isVisible == true -> driveMode?.hide()
                binding.suggestionsPanel.visibility == View.VISIBLE -> {
                    hideSuggestions()
                    binding.searchInput.clearFocus()
                }
                isInFullscreen() -> exitFullscreen()
                webView?.canGoBack() == true -> webView?.goBack()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    // ── Permissions ────────────────────────────────────────────────

    private fun ensureStartupPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), RC_STARTUP_PERMISSIONS
            )
        }
    }

    /** Ask for "draw over other apps" once so the floating bubble can appear. */
    private fun maybeRequestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return
        val prefs = getSharedPreferences("aamusic", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_overlay", false)) return
        prefs.edit().putBoolean("asked_overlay", true).apply()
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    /** Opens the system "display over other apps" screen for this app. */
    private fun openOverlaySettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    /** The header button is shown only while the overlay permission is missing. */
    private fun updateOverlayButton() {
        binding.overlayButton.visibility =
            if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
    }

    private fun handlePermissionRequest(request: PermissionRequest) {
        val allowed = setOf(
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE
        )
        val grantable = request.resources.filter { it in allowed }.toTypedArray()
        if (grantable.isEmpty()) { request.deny(); return }

        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in grantable) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                request.grant(grantable)
            } else {
                pendingPermissionRequest = request
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECORD_AUDIO), RC_AUDIO
                )
            }
        } else {
            request.grant(grantable)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_AUDIO) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            if (request != null) {
                if (granted) {
                    val allowed = setOf(
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    )
                    val grantable = request.resources.filter { it in allowed }.toTypedArray()
                    if (grantable.isNotEmpty()) request.grant(grantable) else request.deny()
                } else {
                    request.deny()
                }
            }
        }
    }

    companion object {
        // Regular YouTube (video content is far richer than YT Music);
        // playback still runs audio-only in the background as usual.
        private const val HOME_URL = "https://www.youtube.com"
        private const val SEARCH_URL = "https://www.youtube.com/results?search_query="

        private const val RESUME_AFTER_FOCUS_MS = 600L

        private const val RC_AUDIO = 1102
        private const val RC_STARTUP_PERMISSIONS = 1103

        const val ACTION_SEARCH = "com.gsvn.aamusic.action.SEARCH"
        const val EXTRA_QUERY = "com.gsvn.aamusic.extra.QUERY"
        const val EXTRA_FOCUS_SEARCH = "com.gsvn.aamusic.extra.FOCUS_SEARCH"
        const val EXTRA_START_VOICE = "com.gsvn.aamusic.extra.START_VOICE"
        const val EXTRA_URL = "com.gsvn.aamusic.extra.URL"
    }
}
