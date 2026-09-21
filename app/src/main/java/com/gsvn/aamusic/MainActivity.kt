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
import com.google.android.material.color.DynamicColors
import com.gsvn.aamusic.data.SearchHistory
import com.gsvn.aamusic.data.SearchSuggest
import com.gsvn.aamusic.databinding.ActivityMainBinding
import com.gsvn.aamusic.player.MediaSessionHolder
import com.gsvn.aamusic.player.PlayerController
import com.gsvn.aamusic.ui.SettingsSheet
import com.gsvn.aamusic.web.BrowserCallbacks
import com.gsvn.aamusic.web.configureWebView
import com.gsvn.aamusic.web.releaseCompletely

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var webView: android.webkit.WebView? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var isBackgroundPlaybackActive: Boolean = false

    // ── Phiên media ─────────────────────────────────────────────────
    // Khi app đang hiển thị (kể cả trên màn hình xe) thì service nền không
    // chạy, nên chính activity phải bơm trạng thái vào phiên media — hệ thống
    // chỉ định tuyến phím vô lăng tới phiên media đang hoạt động.
    private val mediaHandler = Handler(Looper.getMainLooper())
    private val mediaStateTicker = object : Runnable {
        override fun run() {
            PlayerController.queryState { title, playing ->
                MediaSessionHolder.update(title.ifBlank { getString(R.string.bubble_playing) }, playing)
            }
            mediaHandler.postDelayed(this, MEDIA_STATE_POLL_MS)
        }
    }

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
            submitSearch(text)
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
        MediaSessionHolder.ensure(this)

        setupWebView()
        setupSearchBar()
        setupBackPressHandling()
        ensureStartupPermissions()
        maybeRequestOverlayPermission()
        registerReceiver(
            stopPlaybackReceiver,
            IntentFilter(BackgroundPlaybackService.ACTION_STOP_PLAYBACK),
            RECEIVER_NOT_EXPORTED
        )

        handleSearchIntent(intent)
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
        if (isBackgroundPlaybackActive) {
            BackgroundPlaybackService.stop(this)
            isBackgroundPlaybackActive = false
        }
        requestAudioFocus()
        if (!wasBackgroundActive) {
            webView?.onResume()
        }
        updateOverlayButton()
        mediaHandler.removeCallbacks(mediaStateTicker)
        mediaHandler.post(mediaStateTicker)
    }

    override fun onPause() {
        mediaHandler.removeCallbacks(mediaStateTicker)
        exitFullscreen()
        isBackgroundPlaybackActive = true
        BackgroundPlaybackService.start(this)
        super.onPause()
        // Do NOT abandon audio focus — playback continues in background.
    }

    override fun onDestroy() {
        BackgroundPlaybackService.stop(this)
        isBackgroundPlaybackActive = false
        abandonAudioFocus()
        runCatching { unregisterReceiver(stopPlaybackReceiver) }
        exitFullscreen()
        mediaHandler.removeCallbacksAndMessages(null)
        MediaSessionHolder.release()
        PlayerController.unregister()
        binding.webView.releaseCompletely()
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
            }
        )

        webView = binding.webView
        webView?.let { view ->
            configureWebView(view, callbacks)
            PlayerController.register(view)
            view.loadUrl(HOME_URL)
        }
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
        binding.headerLogo.setOnClickListener { SettingsSheet(this).show() }
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
     * Mở hộp thoại nhận dạng giọng nói của hệ thống; kết quả được submit
     * thành tìm kiếm luôn. Máy không có trình nhận dạng thì báo Toast.
     */
    private fun startVoiceSearch() {
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
        val query = intent.getStringExtra(EXTRA_QUERY)
        if (!query.isNullOrBlank()) {
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
        binding.webView.visibility = View.INVISIBLE
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
        binding.webView.visibility = View.VISIBLE
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

        /** Nhịp bơm tên bài + trạng thái vào phiên media khi app đang hiển thị. */
        private const val MEDIA_STATE_POLL_MS = 1_000L
        private const val RESUME_AFTER_FOCUS_MS = 600L

        private const val RC_AUDIO = 1102
        private const val RC_STARTUP_PERMISSIONS = 1103

        const val ACTION_SEARCH = "com.gsvn.aamusic.action.SEARCH"
        const val EXTRA_QUERY = "com.gsvn.aamusic.extra.QUERY"
        const val EXTRA_FOCUS_SEARCH = "com.gsvn.aamusic.extra.FOCUS_SEARCH"
        const val EXTRA_START_VOICE = "com.gsvn.aamusic.extra.START_VOICE"
    }
}
