package com.gsvn.aamusic.web

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView

/**
 * Custom WebView that:
 * 1. Prevents automatic media pause when the window visibility changes
 *    (screen off, app backgrounded) — keeps music playing in background.
 * 2. Preserves proper IME (soft keyboard) behavior so the search field works.
 */
class BackgroundPlayWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    /**
     * When true, window visibility changes are ignored so media keeps playing.
     */
    var backgroundPlaybackEnabled: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (backgroundPlaybackEnabled) {
            // Always tell WebView the window is VISIBLE to prevent media pause.
            super.onWindowVisibilityChanged(VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun checkInputConnectionProxy(view: android.view.View?): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        return super.onCreateInputConnection(outAttrs)
    }
}
