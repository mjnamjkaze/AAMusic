package com.gsvn.aamusic.web

/**
 * Chặn YouTube tự phát video xem thử (preview) trong danh sách.
 *
 * Mở trang chủ hay trang kết quả là YouTube tự chạy preview ngay trên thumbnail.
 * Với app nghe nhạc trên xe thì đó thuần tuý là hại: tốn data, chen tiếng vào
 * bài đang nghe, và tệ nhất là thẻ <video> của ô preview thường đứng trước thẻ
 * của trình phát chính trong DOM — mọi chỗ trong app dùng
 * `document.querySelector('video')` sẽ điều khiển nhầm ô preview.
 *
 * App cho `mediaPlaybackRequiresUserGesture = false` (bắt buộc, để bài nhạc
 * chạy tiếp lúc tắt màn hình) nên không thể nhờ WebView chặn autoplay hộ —
 * phải tự chặn bằng JS.
 *
 * [BLOCK_JS] chạy ở document-start: vá `HTMLMediaElement.play` *trước* script
 * của trang, nên preview bị từ chối ngay lần gọi đầu, không kịp phát ra tiếng
 * cũng không kịp nạp luồng. Lỗi trả về là `NotAllowedError` — đúng thứ trình
 * duyệt ném ra khi chặn autoplay, trang vốn đã có nhánh xử lý nên giao diện
 * không vỡ.
 *
 * Nguyên tắc an toàn: **không nhận ra trình phát chính thì không chặn gì cả**.
 * YouTube đổi markup thì cùng lắm preview quay lại (khó chịu), chứ không bao
 * giờ được phép thành ra câm tiếng cả app.
 */
object PreviewGuard {

    val BLOCK_JS = """
        (function() {
            if (window.__ytaPreviewGuard) return;
            window.__ytaPreviewGuard = true;

            // Ô video trong danh sách / đề xuất — chỗ preview sống.
            var FEED_ITEMS = [
                'ytm-video-with-context-renderer',
                'ytm-compact-video-renderer',
                'ytm-large-media-item-renderer',
                'ytm-media-item',
                'ytm-rich-item-renderer',
                'ytm-shorts-lockup-view-model',
                'ytm-reel-item-renderer',
                'ytm-inline-player-renderer',
                '#inline-preview-player',
                'ytd-rich-item-renderer',
                'ytd-video-renderer',
                'ytd-compact-video-renderer',
                'ytd-grid-video-renderer',
                'yt-lockup-view-model',
                'ytmusic-two-row-item-renderer',
                'ytmusic-responsive-list-item-renderer',
                'ytmusic-carousel-shelf-renderer'
            ].join(',');

            // Trình phát chính: thứ duy nhất được phép ra tiếng.
            var PLAYER_HOSTS = [
                '#movie_player',
                '#player-container-id',
                'ytm-watch',
                'ytd-watch-flexy',
                'ytd-player',
                'ytmusic-player'
            ].join(',');

            var PLAYER_VIDEOS = PLAYER_HOSTS.split(',').map(function(s) {
                return s + ' video';
            }).join(',');

            function inFeed(el) {
                try { return !!(el && el.closest && el.closest(FEED_ITEMS)); }
                catch (e) { return false; }
            }

            /**
             * Thẻ video của trình phát chính, hoặc null nếu không nhận ra.
             * Preview cũng dựng ra một trình phát đầy đủ bên trong ô danh sách,
             * nên phải loại những cái nằm trong danh sách ra trước.
             */
            function mainVideo() {
                var list = document.querySelectorAll(PLAYER_VIDEOS);
                for (var i = 0; i < list.length; i++) {
                    if (!inFeed(list[i])) return list[i];
                }
                return null;
            }

            function isPreview(v) {
                if (!v || v.tagName !== 'VIDEO') return false;
                // Chưa gắn vào DOM thì chưa đủ căn cứ; lớp quét bên dưới lo nốt.
                if (!v.isConnected || !v.closest) return false;
                if (inFeed(v)) return true;
                try { if (v.closest(PLAYER_HOSTS)) return false; } catch (e) { return false; }
                // Thẻ video lạc loài: chỉ dám chặn khi đã nhận ra trình phát
                // chính ở chỗ khác, nghĩa là cái này chắc chắn là thứ thừa.
                return !!mainVideo();
            }

            function block(v) {
                try { v.autoplay = false; } catch (e) {}
                try { v.preload = 'none'; } catch (e) {}
                try { if (!v.paused) v.pause(); } catch (e) {}
            }

            var origPlay = HTMLMediaElement.prototype.play;
            HTMLMediaElement.prototype.play = function() {
                if (isPreview(this)) {
                    block(this);
                    return Promise.reject(
                        new DOMException('preview blocked', 'NotAllowedError'));
                }
                return origPlay.apply(this, arguments);
            };

            // Preview còn có thể chạy qua thuộc tính autoplay, không qua play().
            ['play', 'playing'].forEach(function(type) {
                document.addEventListener(type, function(e) {
                    if (isPreview(e.target)) block(e.target);
                }, true);
            });

            // Lưới cuối, cho thẻ video gọi play lúc còn chưa gắn vào DOM.
            setInterval(function() {
                var vs = document.querySelectorAll('video');
                for (var i = 0; i < vs.length; i++) {
                    if (!vs[i].paused && isPreview(vs[i])) block(vs[i]);
                }
            }, 1000);

            /**
             * Định nghĩa dùng chung cho các script khác của app: "thẻ video của
             * trình phát chính". Không có nó thì PlaybackGuard/PlayerController
             * vẫn bốc thẻ <video> đầu tiên của trang — tức là ô preview.
             */
            window.__ytaIsPreview = isPreview;
            window.__ytaMainVideo = function() {
                var m = mainVideo();
                if (m) return m;
                var vs = document.querySelectorAll('video');
                for (var i = 0; i < vs.length; i++) {
                    if (!isPreview(vs[i])) return vs[i];
                }
                return null;
            };
        })();
    """.trimIndent()
}
