package com.gsvn.aamusic.web

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Network-level blocker for YouTube Music:
 *  - Blocks known ad-serving domains and YouTube ad paths.
 *
 * Album covers / thumbnails are allowed (shown in the UI). The video surface
 * is hidden via CSS instead, keeping the app music-first while still showing
 * cover art. Audio streams (googlevideo.com) are never blocked unless they
 * carry an unambiguous ad marker.
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
        "s0.2mdn.net",
        "pagead-googlehosted.l.google.com",
        "ads.youtube.com",
        "moatads.com",
        "adsafeprotected.com",
        "doubleverify.com",
    )

    // YouTube-specific ad URL path patterns
    private val YOUTUBE_AD_PATHS = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/get_midroll_info",
        "/get_video_ads",
        "/ptracking",
        "/api/stats/qoe?adformat",
        "/youtubei/v1/player/ad_break",
        "/youtubei/v1/player/ad",
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
     * JavaScript injected on music.youtube.com to:
     *  - Hide ad renderers / upsell banners and skip/fast-forward audio ads.
     *  - Hide only the video surface so playback stays music-first, while
     *    album covers / thumbnails remain visible.
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
                'ytmusic-popup-container:has(a[href*="premium"]) { display: none !important; }',
                'ad-slot-renderer, ytmusic-ad-slot-renderer { display: none !important; }',
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

            function trySkipAd() {
                var skip = document.querySelector(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, button[class*="skip"]'
                );
                if (skip) { skip.click(); return true; }
                return false;
            }

            function speedUpAd() {
                var player = document.querySelector('.html5-main-video, video');
                if (!player) return;
                var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                if (adShowing) {
                    if (!trySkipAd()) {
                        if (player.duration && isFinite(player.duration) && player.duration > 0) {
                            player.currentTime = player.duration - 0.1;
                        }
                        player.playbackRate = 16;
                    }
                    if (!player.muted) {
                        player._adMuting = true;
                        player.muted = true;
                        setTimeout(function() { player._adMuting = false; }, 100);
                    }
                } else {
                    if (player.playbackRate > 2) player.playbackRate = 1;
                    if (player.muted && !player._userMuted) {
                        player._adMuting = true;
                        player.muted = false;
                        setTimeout(function() { player._adMuting = false; }, 100);
                    }
                }
            }

            setInterval(function() {
                trySkipAd();
                speedUpAd();
            }, 500);

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
