package com.gsvn.aamusic.web

/**
 * Hai lớp giữ cho nhạc chạy liên tục và đỡ tốn data:
 *
 *  - [AUTO_RESUME_JS]: YouTube thỉnh thoảng tự dừng và hỏi "còn xem không?".
 *    Nhạc thường chạy nền hoặc tắt màn hình nên không ai bấm, tiếng cứ thế
 *    lặng đi. Watchdog này tự xác nhận rồi phát tiếp.
 *  - [LOW_QUALITY_JS]: app nghe là chính, nhưng trang vẫn tải song song luồng
 *    hình. Ép chất lượng xuống mức thấp nhất để cắt phần dung lượng đó.
 */
object PlaybackGuard {

    /**
     * Mấu chốt là phân biệt "người dùng bấm dừng" với "trang tự dừng":
     *
     *  - dừng ngay sau một thao tác chạm/phím → do người dùng, tôn trọng;
     *  - dừng qua nút của app (bong bóng, notification) → `JS_PLAY_PAUSE` của
     *    PlayerController đã tự hạ cờ `__ytaWantPlay`;
     *  - dừng mà không có thao tác nào trước đó → hộp thoại rình rập, phát tiếp.
     */
    val AUTO_RESUME_JS = """
        (function() {
            if (window.__ytaResumeGuard) return;
            window.__ytaResumeGuard = true;
            if (typeof window.__ytaWantPlay === 'undefined') window.__ytaWantPlay = true;

            // Khoảng lặng sau thao tác của người dùng, đủ dài để bắt trọn cú
            // bấm nút tạm dừng trên trang (click → sự kiện pause).
            var GESTURE_WINDOW_MS = 1500;
            var lastGesture = 0;

            ['pointerdown', 'touchstart', 'mousedown', 'keydown'].forEach(function(type) {
                document.addEventListener(type, function() {
                    lastGesture = Date.now();
                }, true);
            });

            var CONFIRM_SELECTORS = [
                'yt-confirm-dialog-renderer #confirm-button',
                'ytm-confirm-dialog-renderer .confirm-button',
                'ytmusic-you-there-renderer button',
                'tp-yt-paper-dialog #confirm-button',
                '.ytp-confirm-dialog-renderer button',
                'button[aria-label*="Continue watching" i]',
                'button[aria-label*="Tiếp tục xem" i]',
                'button[aria-label*="Vẫn đang xem" i]'
            ];

            function confirmStillWatching() {
                for (var i = 0; i < CONFIRM_SELECTORS.length; i++) {
                    var el = document.querySelector(CONFIRM_SELECTORS[i]);
                    // Phần tử có thể đang bị CSS ẩn; click bằng JS vẫn ăn.
                    if (el) { el.click(); return true; }
                }
                return false;
            }

            function hook(v) {
                if (!v || v.__ytaGuardHooked) return;
                v.__ytaGuardHooked = true;
                v.addEventListener('pause', function() {
                    if (Date.now() - lastGesture < GESTURE_WINDOW_MS) {
                        window.__ytaWantPlay = false;
                    }
                });
                v.addEventListener('play', function() {
                    window.__ytaWantPlay = true;
                });
            }

            setInterval(function() {
                var v = document.querySelector('video');
                if (!v) return;
                hook(v);

                if (!window.__ytaWantPlay) return;
                if (!v.paused || v.ended) return;
                // Vừa có thao tác: chờ vòng sau, biết đâu người dùng đang bấm.
                if (Date.now() - lastGesture < GESTURE_WINDOW_MS) return;

                confirmStillWatching();
                var p = v.play();
                if (p && p.catch) p.catch(function() {});
            }, 2000);
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

            function apply() {
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
}
