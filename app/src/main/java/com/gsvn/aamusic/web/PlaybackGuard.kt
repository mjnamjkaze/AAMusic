package com.gsvn.aamusic.web

/**
 * Hai lớp giữ cho nhạc chạy liên tục và đỡ tốn data:
 *
 *  - [AUTO_RESUME_JS]: YouTube dừng nhạc vì đủ thứ lý do ngoài ý người dùng —
 *    hộp thoại "còn xem không?", mất mạng chốc lát, trang giành/nhả luồng
 *    audio, quảng cáo chèn hỏng. Nhạc thường chạy nền hoặc tắt màn hình nên
 *    không ai bấm, tiếng cứ thế lặng đi. Watchdog này phát tiếp.
 *  - [LOW_QUALITY_JS]: app nghe là chính, nhưng trang vẫn tải song song luồng
 *    hình. Ép chất lượng xuống mức thấp nhất để cắt phần dung lượng đó.
 *
 * Cả hai đều bám vào "thẻ video của trình phát chính" do [PreviewGuard] xác
 * định, không phải thẻ <video> đầu tiên của trang — trên trang chủ thẻ đầu
 * tiên thường là ô xem thử trong danh sách.
 */
object PlaybackGuard {

    /**
     * Mấu chốt là phân biệt "người dùng bấm dừng" với "trang tự dừng".
     *
     * Bản trước coi *mọi* thao tác chạm trong 1,5 giây trước khi dừng là do
     * người dùng — cuộn danh sách hay chạm nhầm màn hình đúng lúc trang tự
     * dừng là nhạc tắt luôn, vì cờ `__ytaWantPlay` một khi hạ xuống thì không
     * có gì nâng lại. Giờ chỉ tính là chủ động khi cú chạm/phím rơi đúng vào
     * nút phát/tạm dừng (hoặc phím tắt k/space của YouTube); còn lại đều coi
     * là trang tự dừng và phát tiếp.
     *
     * Các nút của app (bong bóng, notification, vô lăng) tự đặt cờ qua
     * PlayerController nên không phụ thuộc lớp dò này.
     */
    val AUTO_RESUME_JS = """
        (function() {
            if (window.__ytaResumeGuard) return;
            window.__ytaResumeGuard = true;
            if (typeof window.__ytaWantPlay === 'undefined') window.__ytaWantPlay = true;

            // Nút phát/tạm dừng trên mọi giao diện YouTube mà app có thể mở.
            var PAUSE_CONTROLS = [
                '.ytp-play-button',
                '.ytp-large-play-button',
                'ytmusic-player-bar #play-pause-button',
                '#play-pause-button',
                'tp-yt-paper-icon-button.play-pause-button',
                '.player-control-play-pause-icon',
                'button[aria-label*="Pause" i]',
                'button[aria-label*="Tạm dừng" i]',
                '[title*="Pause" i]'
            ].join(',');

            // Khoảng lặng sau cú bấm đúng nút, đủ dài để sự kiện pause kịp bắn.
            var CONTROL_WINDOW_MS = 1500;
            var lastControlHit = 0;

            function markIfControl(target) {
                if (!target || !target.closest) return;
                try {
                    if (target.closest(PAUSE_CONTROLS)) lastControlHit = Date.now();
                } catch (e) {}
            }

            ['pointerdown', 'touchstart', 'mousedown', 'click'].forEach(function(type) {
                document.addEventListener(type, function(e) {
                    markIfControl(e.target);
                }, true);
            });

            // Phím tắt phát/dừng của YouTube. Các phím khác không đụng tới.
            document.addEventListener('keydown', function(e) {
                var k = (e.key || '').toLowerCase();
                if (k === 'k' || k === ' ' || k === 'spacebar') lastControlHit = Date.now();
            }, true);

            var CONFIRM_SELECTORS = [
                'yt-confirm-dialog-renderer #confirm-button',
                'ytm-confirm-dialog-renderer .confirm-button',
                'ytmusic-you-there-renderer button',
                'tp-yt-paper-dialog #confirm-button',
                '.ytp-confirm-dialog-renderer button',
                'button[aria-label*="Continue watching" i]',
                'button[aria-label*="Tiếp tục xem" i]',
                'button[aria-label*="Vẫn đang xem" i]',
                'button[aria-label*="Yes" i]',
                '#confirm-button button'
            ];

            function confirmStillWatching() {
                for (var i = 0; i < CONFIRM_SELECTORS.length; i++) {
                    var el = document.querySelector(CONFIRM_SELECTORS[i]);
                    // Phần tử có thể đang bị CSS ẩn; click bằng JS vẫn ăn.
                    if (el) { el.click(); return true; }
                }
                return false;
            }

            // Trình phát bọc quanh video chính — ô preview cũng dựng ra một
            // '.html5-video-player' của riêng nó nên không hỏi trang trước.
            function player() {
                var v = mainVideo();
                var p = v && v.closest && v.closest('#movie_player, .html5-video-player');
                return p || document.querySelector('#movie_player, .html5-video-player');
            }

            // Thẻ video của trình phát chính. PreviewGuard đã loại ô preview
            // trong danh sách ra rồi; không có nó thì đành lấy thẻ đầu tiên.
            function mainVideo() {
                if (window.__ytaMainVideo) return window.__ytaMainVideo();
                return document.querySelector('video');
            }

            function playNow(v) {
                confirmStillWatching();
                var p = player();
                if (p && p.playVideo) { try { p.playVideo(); } catch (e) {} }
                var r = v.play();
                if (r && r.catch) r.catch(function() {});
            }

            function hook(v) {
                if (v.__ytaGuardHooked) return;
                v.__ytaGuardHooked = true;
                v.addEventListener('pause', function() {
                    // Chỉ cú bấm đúng nút phát/dừng mới được coi là chủ động.
                    if (Date.now() - lastControlHit < CONTROL_WINDOW_MS) {
                        window.__ytaWantPlay = false;
                    }
                });
                v.addEventListener('play', function() {
                    window.__ytaWantPlay = true;
                });
                // Hết bài mà trang không tự chuyển thì bấm hộ nút bài kế.
                v.addEventListener('ended', function() {
                    if (!window.__ytaWantPlay) return;
                    setTimeout(function() {
                        var cur = mainVideo();
                        if (!cur || !cur.ended) return;
                        var next = document.querySelector(
                            'ytmusic-player-bar .next-button, .next-button, .ytp-next-button');
                        if (next) next.click();
                    }, 5000);
                });
            }

            // Kẹt buffering: trang không "pause" nhưng tiếng vẫn tắt. Sau một
            // lúc đứng yên thì tua nhẹ để ép trình phát nạp lại luồng.
            var lastTime = -1;
            var stuckSince = 0;
            var lastNudge = 0;

            function nudgeIfStuck(v) {
                var now = Date.now();
                if (v.paused || v.ended) { lastTime = -1; stuckSince = 0; return; }

                if (v.currentTime !== lastTime) {
                    lastTime = v.currentTime;
                    stuckSince = 0;
                    return;
                }
                if (!stuckSince) { stuckSince = now; return; }
                if (now - stuckSince < 15000) return;
                if (now - lastNudge < 15000) return;

                lastNudge = now;
                stuckSince = 0;
                try { v.currentTime = Math.max(0, v.currentTime - 0.5); } catch (e) {}
                playNow(v);
            }

            setInterval(function() {
                var v = mainVideo();
                if (!v) return;
                hook(v);

                if (!window.__ytaWantPlay) return;
                if (!v.paused) { nudgeIfStuck(v); return; }
                if (v.ended) return;
                // Vừa bấm đúng nút: chờ vòng sau, sự kiện pause có thể chưa tới.
                if (Date.now() - lastControlHit < CONTROL_WINDOW_MS) return;

                playNow(v);
            }, 1000);
        })();
    """.trimIndent()

    /**
     * Trình phát của trang tải luồng video song song với audio và chọn độ phân
     * giải theo kích thước khung hình — nó không biết app chỉ cần tiếng. Hạ
     * xuống 'tiny' (144p) cắt được phần lớn dung lượng thừa; không tắt hẳn
     * được vì trang luôn cần một luồng hình để chạy.
     */
    val LOW_QUALITY_JS = """
        (function() {
            if (window.__ytaQualityGuard) return;
            window.__ytaQualityGuard = true;
            if (typeof window.__ytaDataSaver === 'undefined') window.__ytaDataSaver = true;

            function apply() {
                if (!window.__ytaDataSaver) return;
                var p = document.querySelector('#movie_player, .html5-video-player');
                if (!p) return;
                try {
                    if (p.setPlaybackQualityRange) p.setPlaybackQualityRange('tiny', 'tiny');
                } catch (e) {}
                try {
                    if (p.setPlaybackQuality) p.setPlaybackQuality('tiny');
                } catch (e) {}
            }

            apply();
            // Trình phát hay tự nâng chất lượng lại khi mạng khoẻ hoặc chuyển
            // bài, nên phải ép lại định kỳ.
            setInterval(apply, 5000);
        })();
    """.trimIndent()

    /**
     * Bật/tắt chế độ tiết kiệm dữ liệu trên trang đang mở.
     *
     * Không gỡ được vòng lặp của [LOW_QUALITY_JS] sau khi đã chạy, nên nó đọc
     * cờ `__ytaDataSaver` mỗi vòng; ở đây chỉ việc hạ/nâng cờ. Tắt thì trình
     * phát tự nâng chất lượng trở lại theo mạng, không cần làm gì thêm.
     */
    fun setDataSaver(webView: android.webkit.WebView?, enabled: Boolean) {
        val view = webView ?: return
        view.evaluateJavascript("window.__ytaDataSaver=$enabled;", null)
    }
}
