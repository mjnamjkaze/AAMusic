package com.gsvn.aamusic.web

/**
 * SponsorBlock integration: skips creator-inserted segments (sponsor, intro,
 * outro, self-promo, non-music sections, …) using the crowd-sourced
 * SponsorBlock API. Runs entirely inside the page via injected JS, so it works
 * on both youtube.com and music.youtube.com watch pages.
 *
 * These are NOT YouTube ads (those are handled by [AdBlocker]); SponsorBlock
 * skips the parts of a video the uploader themselves inserted.
 */
object SponsorBlock {

    val SKIP_JS = """
        (function() {
            if (window.__sb_injected) return;
            window.__sb_injected = true;

            var CATS = ['sponsor','selfpromo','interaction','intro','outro',
                        'preview','music_offtopic','filler'];
            var segments = [];
            var loadedId = null;

            function videoId() {
                try {
                    var u = new URL(location.href);
                    var v = u.searchParams.get('v');
                    if (v) return v;
                    var m = location.href.match(/(?:youtu\.be\/|shorts\/|embed\/)([\w-]{11})/);
                    if (m) return m[1];
                } catch (e) {}
                return null;
            }

            function load(id) {
                loadedId = id;
                segments = [];
                if (!id) return;
                var url = 'https://sponsor.ajay.app/api/skipSegments?videoID=' + id
                    + '&' + CATS.map(function(c){ return 'category=' + c; }).join('&');
                fetch(url)
                    .then(function(r){ return r.ok ? r.json() : []; })
                    .then(function(data){
                        if (loadedId !== id) return;
                        segments = (data || []).map(function(s){
                            return { start: s.segment[0], end: s.segment[1] };
                        });
                    })
                    .catch(function(){});
            }

            setInterval(function() {
                var v = document.querySelector('video');
                if (!v) return;
                var id = videoId();
                if (id !== loadedId) load(id);
                if (!segments.length) return;
                var t = v.currentTime;
                for (var i = 0; i < segments.length; i++) {
                    var s = segments[i];
                    if (t >= s.start && t < s.end - 0.3) {
                        v.currentTime = s.end;
                        break;
                    }
                }
            }, 500);
        })();
    """.trimIndent()
}
