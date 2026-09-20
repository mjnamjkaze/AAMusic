package com.gsvn.aamusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live search autocomplete backed by the public YouTube "suggest" endpoint.
 *
 * The `client=firefox` variant returns clean JSON: `["query", ["s1", "s2", ...]]`,
 * and being a native HTTP call it is not subject to the browser CORS rules that
 * block the same request from inside the WebView.
 */
object SearchSuggest {

    private const val ENDPOINT = "https://suggestqueries.google.com/complete/search"

    /** Fetches up to [limit] YouTube suggestions for [query]. Never throws. */
    suspend fun fetch(query: String, limit: Int = 8): List<String> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()

            runCatching {
                val url = URL(
                    "$ENDPOINT?client=firefox&ds=yt&hl=vi&q=" +
                        URLEncoder.encode(q, "UTF-8")
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        return@runCatching emptyList<String>()
                    }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    // ["query", ["s1", "s2", ...], ...]
                    val arr = JSONArray(body).getJSONArray(1)
                    (0 until arr.length())
                        .map { arr.getString(it) }
                        .take(limit)
                } finally {
                    conn.disconnect()
                }
            }.getOrDefault(emptyList())
        }
}
