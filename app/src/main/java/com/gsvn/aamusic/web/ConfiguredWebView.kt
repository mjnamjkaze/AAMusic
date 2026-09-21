package com.gsvn.aamusic.web

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

data class BrowserCallbacks(
    val onProgressChange: (Int) -> Unit = {},
    val onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit = { _, _ -> },
    val onExitFullscreen: () -> Unit = {},
    val onPermissionRequest: (PermissionRequest) -> Unit = { it.deny() },
    val onFocusNativeSearch: () -> Unit = {},
    val onPageFinished: (String) -> Unit = {}
)

fun configureWebView(
    webView: WebView,
    callbacks: BrowserCallbacks = BrowserCallbacks()
) {
    with(webView) {
        setBackgroundColor(Color.BLACK)

        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false

        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()

        WebView.setWebContentsDebuggingEnabled(false)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)

            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            offscreenPreRaster = true

            // Mobile Chrome UA so YouTube Music serves the touch web app.
            userAgentString = MOBILE_CHROME_UA
            useWideViewPort = false
            loadWithOverviewMode = false
        }

        CookieManager.getInstance().also {
            it.setAcceptCookie(true)
            it.setAcceptThirdPartyCookies(this, true)
        }

        // Bridge để JS gọi native show/hide keyboard (một số head unit cần)
        addJavascriptInterface(ImeKeyboardBridge(this, callbacks), "ImeKeyboardBridge")

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, DOCUMENT_START_JS, setOf("*"))
            // Phải chạy trước script của trang thì mới cắt được dữ liệu quảng cáo
            // trước khi trình phát đọc — xem AdBlocker.EARLY_JS.
            WebViewCompat.addDocumentStartJavaScript(this, AdBlocker.EARLY_JS, setOf("*"))
            // Cũng phải chạy trước script của trang: vá play() sau khi trang đã
            // gọi thì preview đã kịp phát — xem PreviewGuard.
            WebViewCompat.addDocumentStartJavaScript(this, PreviewGuard.BLOCK_JS, setOf("*"))
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val blocked = AdBlocker.shouldBlock(request.url?.toString())
                if (blocked != null) return blocked
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url?.scheme?.lowercase() ?: return false
                // Chỉ load http/https, bỏ qua intent://, market://, ...
                return scheme !in setOf("http", "https", "about", "javascript", "data")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)

                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view.evaluateJavascript(DOCUMENT_START_JS, null)
                    view.evaluateJavascript(AdBlocker.EARLY_JS, null)
                    view.evaluateJavascript(PreviewGuard.BLOCK_JS, null)
                }

                val pageUrl = url?.lowercase() ?: ""
                val isYouTube = pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be")

                // Phải chạy TRƯỚC KEYBOARD_TRIGGER_JS: listener focusin của nó
                // chặn (stopImmediatePropagation) focus vào ô search của YouTube
                // nên bàn phím native không bị bật nhầm.
                if (isYouTube) view.evaluateJavascript(YT_SEARCH_LOCKDOWN_JS, null)

                view.evaluateJavascript(KEYBOARD_TRIGGER_JS, null)

                if (isYouTube) {
                    view.evaluateJavascript(AdBlocker.MUSIC_ADBLOCK_JS, null)
                    view.evaluateJavascript(SponsorBlock.SKIP_JS, null)
                    // Nhạc thường chạy nền/tắt màn hình, lúc YouTube hỏi "còn
                    // xem không?" thì không ai bấm — tự xác nhận và phát tiếp.
                    view.evaluateJavascript(PlaybackGuard.AUTO_RESUME_JS, null)
                    view.evaluateJavascript(PlaybackGuard.LOW_QUALITY_JS, null)
                }

                callbacks.onPageFinished(url.orEmpty())
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgressChange(newProgress)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    callbacks.onEnterFullscreen(view, callback)
                } else {
                    super.onShowCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                callbacks.onExitFullscreen()
                super.onHideCustomView()
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return

                val allowed = setOf(
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE
                )

                val grantable = request.resources.filter { it in allowed }.toTypedArray()

                if (grantable.isEmpty()) {
                    request.deny()
                    return
                }

                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in grantable) {
                    callbacks.onPermissionRequest(request)
                } else {
                    this@with.post { request.grant(grantable) }
                }
            }
        }
    }
}

/**
 * JavaScript interface để JS trong WebView gọi native show/hide bàn phím.
 */
private class ImeKeyboardBridge(
    private val webView: WebView,
    private val callbacks: BrowserCallbacks
) {

    @JavascriptInterface
    fun showKeyboard() {
        webView.post {
            webView.requestFocus()
            val imm = webView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    @JavascriptInterface
    fun focusNativeSearch() {
        webView.post { callbacks.onFocusNativeSearch() }
    }

    @JavascriptInterface
    fun hideKeyboard() {
        webView.post {
            val imm = webView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(webView.windowToken, 0)
        }
    }
}

fun WebView.releaseCompletely() {
    stopLoading()
    webChromeClient = WebChromeClient()
    webViewClient = WebViewClient()
    destroy()
}

private const val CHROME_VERSION = "144.0.0.0"
private const val MOBILE_CHROME_UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROME_VERSION} Mobile Safari/537.36"

/**
 * Ẩn toàn bộ UI tìm kiếm mặc định của YouTube (icon kính lúp trên header,
 * ô search box, voice search) để người dùng chỉ gõ được ở ô search custom
 * của app. Nếu YouTube đổi markup/ngôn ngữ khiến CSS không ẩn hết, lớp chặn
 * focus bên dưới sẽ blur ô search của YouTube và chuyển focus về ô native.
 */
private const val YT_SEARCH_LOCKDOWN_JS = """
    (function() {
        if (window.__ytSearchLockdown) return;
        window.__ytSearchLockdown = true;

        var css = [
            'ytm-searchbox',
            'ytm-search-box',
            'form[action="/results"]',
            'input[name="search_query"]',
            '.searchbox-input',
            'button[aria-label*="search" i]',
            'button[aria-label*="tìm kiếm" i]'
        ].join(',') + '{display:none !important;}';

        function addStyle() {
            var s = document.createElement('style');
            s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
        }
        if (document.head) addStyle();
        else document.addEventListener('DOMContentLoaded', addStyle);

        function isYtSearchInput(el) {
            if (!el || !el.matches) return false;
            return el.matches(
                'input[name="search_query"], .searchbox-input, ' +
                'ytm-searchbox input, form[action="/results"] input');
        }

        document.addEventListener('focusin', function(e) {
            if (!isYtSearchInput(e.target)) return;
            e.stopImmediatePropagation();
            e.target.blur();
            if (window.ImeKeyboardBridge && window.ImeKeyboardBridge.focusNativeSearch) {
                window.ImeKeyboardBridge.focusNativeSearch();
            }
        }, true);
    })();
"""

private const val KEYBOARD_TRIGGER_JS = """
    (function() {
        if (window.__imeInjected) return;
        window.__imeInjected = true;

        function isInputEl(el) {
            if (!el) return false;
            var tag = el.tagName ? el.tagName.toLowerCase() : '';
            if (tag === 'input' || tag === 'textarea' || tag === 'select') return true;
            if (el.isContentEditable) return true;
            return false;
        }

        function onFocusIn(e) {
            if (isInputEl(e.target)) {
                if (window.ImeKeyboardBridge) {
                    window.ImeKeyboardBridge.showKeyboard();
                }
            }
        }

        document.addEventListener('focusin', onFocusIn, true);
    })();
"""

/**
 * Injected before every page loads. Spoofs page visibility/focus so that
 * YouTube Music keeps playing audio when the screen turns off or the app is
 * backgrounded. Unlike the browser variant, it does NOT suppress autoplay —
 * a selected song must be allowed to play.
 */
private const val DOCUMENT_START_JS = """
    (function() {
        if (window.__bgPlaybackInjected) return;
        window.__bgPlaybackInjected = true;

        Object.defineProperty(document, 'visibilityState', {
            get: function() { return 'visible'; },
            configurable: true
        });
        Object.defineProperty(document, 'hidden', {
            get: function() { return false; },
            configurable: true
        });
        document.hasFocus = function() { return true; };

        document.addEventListener('visibilitychange', function(e) {
            e.stopImmediatePropagation();
        }, true);
        window.addEventListener('blur', function(e) {
            e.stopImmediatePropagation();
        }, true);
        window.addEventListener('focusout', function(e) {
            e.stopImmediatePropagation();
        }, true);
        window.addEventListener('pagehide', function(e) {
            e.stopImmediatePropagation();
        }, true);
    })();
"""
