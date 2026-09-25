package com.gsvn.aamusic.web

import android.content.Context
import android.webkit.WebView
import com.gsvn.aamusic.data.DriveSettings

/**
 * Công tắc "Hiện video".
 *
 *  - **Tắt** (mặc định, như trước giờ): khung hình video bị ẩn, chỗ trình phát
 *    hiện một đĩa nhạc với ảnh bìa bài ở giữa, quay khi đang phát và đứng yên
 *    khi dừng. Luồng hình vẫn ép xuống 144p nếu bật tiết kiệm dữ liệu.
 *  - **Bật**: trang hiện video y như YouTube, và không ép chất lượng nữa —
 *    xem video 144p thì chẳng khác gì không xem.
 *
 * Chỉ đổi giao diện trong trang (một lớp CSS trên `<html>`), không nạp lại
 * trang nên bật/tắt giữa bài không làm nhạc ngắt.
 */
object VideoMode {

    /** Áp cả hai tuỳ chọn liên quan (video, tiết kiệm dữ liệu) lên [webView]. */
    fun applyPrefs(context: Context, webView: WebView?) {
        val view = webView ?: return
        val showVideo = DriveSettings.isOn(context, DriveSettings.KEY_SHOW_VIDEO)
        val dataSaver = DriveSettings.isOn(context, DriveSettings.KEY_DATA_SAVER)
        view.evaluateJavascript(
            "window.__ytaShowVideo=$showVideo;" +
                "document.documentElement.classList.toggle('yta-audio-only',${!showVideo});",
            null
        )
        PlaybackGuard.setDataSaver(view, dataSaver && !showVideo)
    }

    /**
     * Cài đĩa nhạc vào trang. Chạy sau khi trang dựng xong (cùng chỗ với các
     * script dọn giao diện khác); cờ `__ytaShowVideo` do [applyPrefs] đặt.
     *
     * Đĩa được chèn ngay sau khung video trong trình phát chính, không đặt
     * z-index: các nút điều khiển của trình phát đứng sau trong DOM nên vẫn
     * nằm trên, và `pointer-events: none` để chạm xuyên qua như cũ.
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
                '#yta-disc { display: none; position: absolute; left: 0; top: 0; right: 0; bottom: 0;',
                '  align-items: center; justify-content: center; background: #000;',
                '  pointer-events: none; }',
                'html.yta-audio-only #yta-disc { display: flex; }',
                '#yta-disc .yta-d { height: 86%; aspect-ratio: 1 / 1; border-radius: 50%;',
                '  background: repeating-radial-gradient(circle, #101010 0 2px, #1d1d1d 2px 4px);',
                '  box-shadow: 0 0 0 2px #2a2a2a, 0 6px 24px rgba(0,0,0,.6);',
                '  display: flex; align-items: center; justify-content: center;',
                '  animation: yta-spin 8s linear infinite; animation-play-state: paused; }',
                '#yta-disc .yta-d.yta-on { animation-play-state: running; }',
                '#yta-disc .yta-l { position: relative; width: 44%; height: 44%; border-radius: 50%;',
                '  background: #333 center / cover no-repeat; box-shadow: 0 0 0 3px #000; }',
                '#yta-disc .yta-l::after { content: ""; position: absolute; left: 50%; top: 50%;',
                '  width: 12%; height: 12%; margin: -6% 0 0 -6%; border-radius: 50%; background: #000; }',
                '@keyframes yta-spin { to { transform: rotate(360deg); } }'
            ].join('\n');
            var style = document.createElement('style');
            style.textContent = css;
            (document.head || document.documentElement).appendChild(style);

            function mainVideo() {
                if (window.__ytaMainVideo) return window.__ytaMainVideo();
                return document.querySelector('#movie_player video, .html5-video-player video');
            }

            function videoId() {
                try { return new URL(location.href).searchParams.get('v') || ''; }
                catch (e) { return ''; }
            }

            var shownId = null;

            setInterval(function() {
                var v = mainVideo();
                var player = v && v.closest && v.closest('#movie_player, .html5-video-player');
                if (!player) return;

                var disc = player.querySelector(':scope > #yta-disc');
                if (!disc) {
                    var old = document.getElementById('yta-disc');
                    if (old) old.remove();
                    disc = document.createElement('div');
                    disc.id = 'yta-disc';
                    disc.innerHTML = '<div class="yta-d"><div class="yta-l"></div></div>';
                    var box = player.querySelector(':scope > .html5-video-container');
                    if (box && box.nextSibling) player.insertBefore(disc, box.nextSibling);
                    else player.appendChild(disc);
                    shownId = null;
                }

                var id = videoId();
                if (id !== shownId) {
                    shownId = id;
                    disc.querySelector('.yta-l').style.backgroundImage =
                        id ? 'url("https://i.ytimg.com/vi/' + id + '/mqdefault.jpg")' : '';
                }
                disc.firstChild.classList.toggle('yta-on', !v.paused && !v.ended);
            }, 1000);
        })();
    """.trimIndent()
}
