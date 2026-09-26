package com.gsvn.aamusic.web

import android.content.Context
import android.webkit.WebView
import com.gsvn.aamusic.data.DriveSettings
import com.gsvn.aamusic.data.PlayerBackgrounds

/**
 * Công tắc "Hiện video".
 *
 *  - **Tắt** (mặc định): khung hình video bị ẩn, chỗ trình phát phủ kín một
 *    **ảnh nền** người dùng chọn ([PlayerBackgrounds]) — không hiện ảnh bìa
 *    bài hát, cho màn hình yên, không đổi màu theo từng bài. Luồng hình vẫn ép
 *    xuống 144p nếu bật tiết kiệm dữ liệu.
 *  - **Bật**: trang hiện video y như YouTube, và không ép chất lượng nữa —
 *    xem video 144p thì chẳng khác gì không xem.
 *
 * Chỉ đổi giao diện trong trang (một lớp CSS trên `<html>`), không nạp lại
 * trang nên bật/tắt giữa bài không làm nhạc ngắt.
 */
object VideoMode {

    /** Áp các tuỳ chọn liên quan (video, tiết kiệm dữ liệu, ảnh nền) lên [webView]. */
    fun applyPrefs(context: Context, webView: WebView?) {
        val view = webView ?: return
        val showVideo = DriveSettings.isOn(context, DriveSettings.KEY_SHOW_VIDEO)
        val dataSaver = DriveSettings.isOn(context, DriveSettings.KEY_DATA_SAVER)
        // Id lấy từ danh sách cố định — an toàn để ghép thẳng vào JS.
        val bg = PlayerBackgrounds.current(context).id
        view.evaluateJavascript(
            "window.__ytaShowVideo=$showVideo;" +
                "document.documentElement.classList.toggle('yta-audio-only',${!showVideo});" +
                "window.__ytaBg='$bg';",
            null
        )
        PlaybackGuard.setDataSaver(view, dataSaver && !showVideo)
    }

    /**
     * Cài lớp ảnh nền vào trang. Chạy sau khi trang dựng xong (cùng chỗ với
     * các script dọn giao diện khác); cờ `__ytaShowVideo`, `__ytaBg` do
     * [applyPrefs] đặt.
     *
     * Ảnh được chèn ngay sau khung video trong trình phát chính, không đặt
     * z-index: các nút điều khiển của trình phát đứng sau trong DOM nên vẫn
     * nằm trên, và `pointer-events: none` để chạm xuyên qua như cũ. Ảnh lấy từ
     * `<origin>/__drivetune/bg/<id>.jpg` — ConfiguredWebView trả từ tài nguyên
     * của app.
     */
    val JS = """
        (function() {
            if (window.__ytaVideoMode) return;
            window.__ytaVideoMode = true;
            if (typeof window.__ytaShowVideo === 'undefined') window.__ytaShowVideo = false;
            document.documentElement.classList.toggle('yta-audio-only', !window.__ytaShowVideo);

            var css = [
                'html.yta-audio-only #player video, html.yta-audio-only .html5-video-player video,',
                'html.yta-audio-only #song-video, html.yta-audio-only ytmusic-player #video,',
                'html.yta-audio-only .video-stream { visibility: hidden !important; }',
                '#yta-bg { display: none; position: absolute; left: 0; top: 0; right: 0; bottom: 0;',
                '  background: #0a1022 center / cover no-repeat; pointer-events: none; }',
                'html.yta-audio-only #yta-bg { display: block; }'
            ].join('\n');
            var style = document.createElement('style');
            style.textContent = css;
            (document.head || document.documentElement).appendChild(style);

            function mainVideo() {
                if (window.__ytaMainVideo) return window.__ytaMainVideo();
                return document.querySelector('#movie_player video, .html5-video-player video');
            }

            setInterval(function() {
                var v = mainVideo();
                var player = v && v.closest && v.closest('#movie_player, .html5-video-player');
                if (!player) return;

                var bg = player.querySelector(':scope > #yta-bg');
                if (!bg) {
                    var old = document.getElementById('yta-bg');
                    if (old) old.remove();
                    bg = document.createElement('div');
                    bg.id = 'yta-bg';
                    var box = player.querySelector(':scope > .html5-video-container');
                    if (box && box.nextSibling) player.insertBefore(bg, box.nextSibling);
                    else player.appendChild(bg);
                }

                var url = window.__ytaBg
                    ? location.origin + '/__drivetune/bg/' + window.__ytaBg + '.jpg' : '';
                if (bg.__ytaUrl !== url) {
                    bg.__ytaUrl = url;
                    bg.style.backgroundImage = url ? 'url("' + url + '")' : '';
                }
            }, 1000);
        })();
    """.trimIndent()
}
