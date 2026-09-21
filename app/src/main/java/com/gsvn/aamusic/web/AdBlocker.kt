package com.gsvn.aamusic.web

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Chặn quảng cáo ba lớp, theo đúng thứ tự Brave/uBlock vẫn làm:
 *
 *  1. Mạng — [shouldBlock] vứt các request tới domain/đường dẫn phục vụ quảng cáo.
 *  2. Dữ liệu — [EARLY_JS] cắt phần mô tả quảng cáo khỏi phản hồi `/youtubei/v1/player`
 *     *trước khi* trang đọc, nên trình phát không hề biết là có quảng cáo để chèn.
 *     Đây là lớp thật sự "bỏ qua được luôn", thay vì chỉ tắt tiếng rồi ngồi đợi.
 *  3. Giao diện — [MUSIC_ADBLOCK_JS] dọn nốt những quảng cáo lọt lưới: bấm nút bỏ
 *     qua, tua thẳng tới cuối, ẩn banner mời mua Premium.
 *
 * Ảnh bìa / thumbnail vẫn được tải bình thường; chỉ khung hình video bị ẩn bằng
 * CSS để app thuần nghe nhạc. Luồng audio (googlevideo.com) không bao giờ bị
 * chặn trừ khi mang dấu hiệu quảng cáo rõ ràng.
 */
object AdBlocker {

    // Known ad-serving domains (subdomains matched too, see isAdDomain)
    private val AD_DOMAINS = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "google-analytics.com",
        "googletagservices.com",
        "googletagmanager.com",
        "2mdn.net",
        "pagead-googlehosted.l.google.com",
        "ads.youtube.com",
        "imasdk.googleapis.com",
        "moatads.com",
        "adsafeprotected.com",
        "doubleverify.com",
        "serving-sys.com",
        "scorecardresearch.com",
    )

    // YouTube-specific ad URL path patterns
    private val YOUTUBE_AD_PATHS = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/api/stats/atr",
        "/get_midroll_info",
        "/get_video_ads",
        "/ptracking",
        "/api/stats/qoe?adformat",
        "/youtubei/v1/player/ad_break",
        "/youtubei/v1/player/ad",
        "/pcs/activeview",
    )

    /**
     * Returns a blank response if the URL should be blocked (ad), or null to
     * let it load normally.
     */
    fun shouldBlock(url: String?): WebResourceResponse? {
        if (url.isNullOrBlank()) return null

        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path?.lowercase() ?: ""

        // 1. Ad domains
        if (isAdDomain(host)) return createEmptyResponse()

        // 2. YouTube ad paths / ad streams
        if (host.contains("youtube.com") || host.contains("googlevideo.com")) {
            val query = uri.query?.lowercase() ?: ""

            for (adPath in YOUTUBE_AD_PATHS) {
                if (path.contains(adPath)) return createEmptyResponse()
            }

            if (host.contains("googlevideo.com")) {
                if (query.contains("oad=") ||
                    url.contains("&ad_type=") ||
                    url.contains("&adformat=")
                ) {
                    return createEmptyResponse()
                }
            }
        }

        return null
    }

    private fun isAdDomain(host: String): Boolean {
        if (host in AD_DOMAINS) return true
        for (adDomain in AD_DOMAINS) {
            if (host.endsWith(".$adDomain")) return true
        }
        return false
    }

    private fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Chạy TRƯỚC mọi script của trang (document-start), nếu không thì
     * `ytInitialPlayerResponse` đã được gán và `fetch` đã bị trang giữ tham
     * chiếu, vá lúc đó là muộn.
     *
     * Trang mô tả quảng cáo trong chính JSON của trình phát (`adPlacements`,
     * `playerAds`, `adSlots`…). Xoá các khoá đó đi thì trình phát không có gì
     * để chèn — quảng cáo biến mất chứ không phải bị tua qua.
     */
    val EARLY_JS = """
        (function() {
            if (window.__ytaAdDataGuard) return;
            window.__ytaAdDataGuard = true;

            var AD_KEYS = [
                'adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams',
                'adServiceConfig', 'adParams'
            ];
            var MAX_DEPTH = 6;

            function scrubDeep(node, depth) {
                if (!node || typeof node !== 'object' || depth > MAX_DEPTH) return node;
                for (var i = 0; i < AD_KEYS.length; i++) {
                    if (AD_KEYS[i] in node) {
                        try { delete node[AD_KEYS[i]]; } catch (e) {}
                    }
                }
                if (node.playerConfig && node.playerConfig.adConfig) {
                    try { delete node.playerConfig.adConfig; } catch (e) {}
                }
                for (var k in node) {
                    var v = node[k];
                    if (v && typeof v === 'object') scrubDeep(v, depth + 1);
                }
                return node;
            }

            // Dữ liệu trình phát nhúng thẳng trong HTML của lần tải đầu.
            try {
                var cached;
                Object.defineProperty(window, 'ytInitialPlayerResponse', {
                    configurable: true,
                    get: function() { return cached; },
                    set: function(v) { cached = scrubDeep(v, 0); }
                });
            } catch (e) {}

            function isPlayerUrl(url) {
                return url.indexOf('/youtubei/v1/player') >= 0;
            }

            function cleanJson(text) {
                return JSON.stringify(scrubDeep(JSON.parse(text), 0));
            }

            // Các bài sau được nạp bằng fetch (trang kiểu SPA, không tải lại).
            var origFetch = window.fetch;
            if (origFetch && !origFetch.__ytaPatched) {
                var patched = function(input, init) {
                    var url = '';
                    try {
                        url = (typeof input === 'string') ? input : (input && input.url) || '';
                    } catch (e) {}
                    var res = origFetch.apply(this, arguments);
                    if (!isPlayerUrl(url)) return res;
                    return res.then(function(r) {
                        if (!r || !r.ok) return r;
                        return r.clone().text().then(function(text) {
                            // Headers dựng mới: giữ nguyên header cũ thì content-length
                            // không còn khớp với body đã bị cắt bớt.
                            return new Response(cleanJson(text), {
                                status: r.status,
                                statusText: r.statusText,
                                headers: { 'content-type': 'application/json' }
                            });
                        }).catch(function() { return r; });
                    });
                };
                patched.__ytaPatched = true;
                window.fetch = patched;
            }

            // Một số luồng của trang di động vẫn dùng XHR. Định nghĩa lại getter
            // ngay từ open() để không phụ thuộc thứ tự đăng ký listener của trang.
            try {
                var XHR = window.XMLHttpRequest;
                var rawDesc = Object.getOwnPropertyDescriptor(XHR.prototype, 'responseText');
                var origOpen = XHR.prototype.open;

                if (rawDesc && rawDesc.get && !XHR.prototype.__ytaPatched) {
                    XHR.prototype.__ytaPatched = true;

                    // Trả về bản đã cắt quảng cáo; hỏng ở bất cứ bước nào thì trả
                    // nguyên bản, không được làm hỏng request của trang.
                    var cleanedText = function(xhr) {
                        var raw;
                        try { raw = rawDesc.get.call(xhr); } catch (e) { return null; }
                        if (xhr.readyState !== 4 || !raw) return raw;
                        if (xhr.__ytaRaw === raw) return xhr.__ytaClean;
                        xhr.__ytaRaw = raw;
                        try { xhr.__ytaClean = cleanJson(raw); }
                        catch (e) { xhr.__ytaClean = raw; }
                        return xhr.__ytaClean;
                    };

                    XHR.prototype.open = function(method, url) {
                        var self = this;
                        if (isPlayerUrl(String(url || ''))) {
                            try {
                                Object.defineProperty(self, 'responseText', {
                                    configurable: true,
                                    get: function() { return cleanedText(self); }
                                });
                                Object.defineProperty(self, 'response', {
                                    configurable: true,
                                    get: function() {
                                        // responseText chỉ đọc được với responseType rỗng/'text';
                                        // kiểu khác (json, arraybuffer…) phải để nguyên.
                                        var t = self.responseType;
                                        if (t !== '' && t !== 'text') {
                                            delete self.response;
                                            return self.response;
                                        }
                                        var clean = cleanedText(self);
                                        return clean === null ? '' : clean;
                                    }
                                });
                            } catch (e) {}
                        }
                        return origOpen.apply(this, arguments);
                    };
                }
            } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Lớp dọn giao diện, chạy sau khi trang dựng xong:
     *  - Bỏ qua quảng cáo còn lọt lưới (bấm nút skip, tua thẳng tới cuối).
     *  - Ẩn banner / mời mua Premium.
     *  - Ẩn khung hình video để app thuần nghe, ảnh bìa vẫn hiện.
     */
    val MUSIC_ADBLOCK_JS = """
        (function() {
            if (window.__ytm_adblock_injected) return;
            window.__ytm_adblock_injected = true;

            var style = document.createElement('style');
            style.textContent = [
                /* ── Hide the video surface (keep audio + cover art) ── */
                '#player video, .html5-video-player video,',
                '#song-video, ytmusic-player #video,',
                '.video-stream { visibility: hidden !important; }',

                /* ── Ad / upsell elements ── */
                'ytmusic-mealbar-promo-renderer { display: none !important; }',
                'ytmusic-statement-banner-renderer { display: none !important; }',
                'ytmusic-you-there-renderer { display: none !important; }',
                '.ytp-ad-module { display: none !important; }',
                '.ytp-ad-overlay-container { display: none !important; }',
                '.ytp-ad-player-overlay { display: none !important; }',
                '.ytp-ad-player-overlay-layout { display: none !important; }',
                'ytmusic-popup-container:has(a[href*="premium"]) { display: none !important; }',
                'ad-slot-renderer, ytmusic-ad-slot-renderer { display: none !important; }',
                'ytd-ad-slot-renderer, ytm-companion-ad-renderer { display: none !important; }',
                '.ad-showing .video-ads { display: none !important; }',

                /* ── Small "YouTube Premium / Upgrade" icon in the header ── */
                'a[href*="premium"], a[href*="musicpremium"] { display: none !important; }',
                'ytmusic-pivot-bar-item-renderer:has(a[href*="premium"]) { display: none !important; }',
                'ytmusic-guide-entry-renderer:has(a[href*="premium"]) { display: none !important; }',
                'tp-yt-paper-icon-button[aria-label*="Premium" i],',
                'yt-button-shape a[aria-label*="Premium" i],',
                '[aria-label*="Get Music Premium" i],',
                '[aria-label*="YouTube Premium" i],',
                '[title*="Music Premium" i] { display: none !important; }'
            ].join('\n');
            (document.head || document.documentElement).appendChild(style);

            /** Video của trình phát, không phải thẻ <video> lạc nào đó trên trang. */
            function playerVideo() {
                // PreviewGuard biết đâu là trình phát chính, đâu là ô xem thử
                // trong danh sách; thiếu nó thì quay về cách đoán cũ.
                if (window.__ytaMainVideo) {
                    var m = window.__ytaMainVideo();
                    if (m) return m;
                }
                var p = document.querySelector('#movie_player, .html5-video-player');
                return (p && p.querySelector('video')) || document.querySelector('video');
            }

            function player() {
                var v = playerVideo();
                var p = v && v.closest && v.closest('#movie_player, .html5-video-player');
                return p || document.querySelector('#movie_player, .html5-video-player');
            }

            /**
             * Đang phát quảng cáo hay không. getAdState() là nguồn chuẩn nhất;
             * lớp CSS .ad-showing là phương án dự phòng khi API đổi tên.
             */
            function adShowing() {
                var p = player();
                if (p) {
                    try {
                        if (typeof p.getAdState === 'function' && p.getAdState() === 1) return true;
                    } catch (e) {}
                    if (p.classList &&
                        (p.classList.contains('ad-showing') ||
                         p.classList.contains('ad-interrupting'))) return true;
                }
                return !!document.querySelector('.ad-showing, .ad-interrupting');
            }

            var SKIP_SELECTORS = [
                '.ytp-ad-skip-button',
                '.ytp-ad-skip-button-modern',
                '.ytp-skip-ad-button',
                '.ytp-ad-skip-button-container button',
                'button.ytp-ad-skip-button-modern',
                '.ytp-ad-overlay-close-button',
                '.ytp-ad-overlay-close-container'
            ].join(',');

            function trySkipAd() {
                var btn = document.querySelector(SKIP_SELECTORS);
                if (btn) { btn.click(); return true; }
                return false;
            }

            /**
             * Không bấm được nút bỏ qua (quảng cáo chưa cho skip) thì tua thẳng
             * tới cuối: trình phát coi như quảng cáo đã xem xong và vào bài ngay.
             */
            function fastForwardAd(v) {
                if (!v) return;
                if (v.duration && isFinite(v.duration) && v.duration > 0) {
                    try { v.currentTime = v.duration; } catch (e) {}
                }
                try { v.playbackRate = 16; } catch (e) {}
                var p = v.play();
                if (p && p.catch) p.catch(function() {});
            }

            function setMuted(v, muted) {
                if (v.muted === muted) return;
                v._adMuting = true;
                v.muted = muted;
                setTimeout(function() { v._adMuting = false; }, 100);
            }

            function tick() {
                var v = playerVideo();
                if (!v) return;

                if (adShowing()) {
                    setMuted(v, true);
                    if (!trySkipAd()) fastForwardAd(v);
                    return;
                }

                // Hết quảng cáo: trả lại tốc độ và tiếng như cũ.
                if (v.playbackRate > 2) {
                    try { v.playbackRate = 1; } catch (e) {}
                }
                if (v.muted && !v._userMuted) setMuted(v, false);
                // Overlay banner có thể hiện ngoài lúc chèn quảng cáo hình.
                trySkipAd();
            }

            setInterval(tick, 250);

            // Track the user's own mute intent so we don't fight it after ads.
            setInterval(function() {
                document.querySelectorAll('video').forEach(function(v) {
                    if (v._muteListenerAdded) return;
                    v._muteListenerAdded = true;
                    v._userMuted = v.muted;
                    v.addEventListener('volumechange', function() {
                        if (!v._adMuting) v._userMuted = v.muted;
                    });
                });
            }, 2000);
        })();
    """.trimIndent()
}
