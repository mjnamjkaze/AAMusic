package com.gsvn.aamusic.data

import android.content.Context

/**
 * Stores recent search queries so the user doesn't have to retype them,
 * and provides a list of common suggested keywords.
 */
object SearchHistory {

    private const val PREF = "aamusic_search"
    private const val KEY_RECENT = "recent"
    private const val KEY_PINNED = "pinned"
    private const val SEP = "\n"
    private const val MAX = 20

    /** Common keyword suggestions shown in the search dropdown (priority order). */
    val SUGGESTIONS: List<String> = listOf(
        // ── Priority keywords ──
        "best english song",
        "chinese song",
        "song for kids",
        "truyện cổ tích",
        "Truyện thiếu nhi",
        "Sách nói tiếng Anh",
        "Lịch sử thế giới",
        "Khoa học phổ thông",
        "Podcast khoa học",
        "Nhạc thư giãn",
        "Nhạc sàn",
        "Nhạc trẻ",
        // ── Extra suggestions ──
        "English songs for kids",
        "Học tiếng Anh giao tiếp",
        "Truyện ngụ ngôn",
        "Nhạc thiếu nhi",
        "Nhạc không lời",
        "Lofi chill",
        "Podcast tiếng Anh",
        "Sách nói tiếng Việt"
    )

    /** Full search history (most-recent first). */
    fun recent(context: Context): List<String> = read(context, KEY_RECENT)

    /** Pinned queries (most-recently pinned first). These survive [clear]. */
    fun pinned(context: Context): List<String> = read(context, KEY_PINNED)

    fun isPinned(context: Context, query: String): Boolean =
        pinned(context).any { it.equals(query.trim(), ignoreCase = true) }

    private fun read(context: Context, key: String): List<String> {
        val raw = prefs(context).getString(key, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(SEP).filter { it.isNotBlank() }
    }

    private fun write(context: Context, key: String, list: List<String>) {
        prefs(context).edit().putString(key, list.joinToString(SEP)).apply()
    }

    fun add(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val list = recent(context).toMutableList()
        // Move to front, dedup case-insensitively
        list.removeAll { it.equals(q, ignoreCase = true) }
        list.add(0, q)
        while (list.size > MAX) list.removeAt(list.size - 1)
        prefs(context).edit().putString(KEY_RECENT, list.joinToString(SEP)).apply()
    }

    fun remove(context: Context, query: String) {
        write(context, KEY_RECENT, recent(context).filterNot { it.equals(query, ignoreCase = true) })
        write(context, KEY_PINNED, pinned(context).filterNot { it.equals(query, ignoreCase = true) })
    }

    /** Pin a query so it stays at the top of the history and survives [clear]. */
    fun pin(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val list = pinned(context).toMutableList()
        list.removeAll { it.equals(q, ignoreCase = true) }
        list.add(0, q)
        write(context, KEY_PINNED, list)
    }

    fun unpin(context: Context, query: String) {
        write(context, KEY_PINNED, pinned(context).filterNot { it.equals(query, ignoreCase = true) })
    }

    /** Clears the (unpinned) history. Pinned queries are kept. */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_RECENT).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
