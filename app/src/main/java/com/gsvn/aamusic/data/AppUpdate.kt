package com.gsvn.aamusic.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Báo có bản mới trên GitHub Releases và cập nhật ngay trong app.
 *
 * Hỏi `releases/latest` của repo lúc mở app, tối đa một lần mỗi
 * [CHECK_INTERVAL_MS]; kết quả lưu lại để Cài đặt hiện dòng "Cập nhật" mà
 * không phải hỏi lại. Cập nhật = DownloadManager tải APK (có thanh tiến độ
 * trên thanh thông báo) rồi mở màn cài đặt của hệ thống; hỏng bước nào thì
 * mở link APK bằng trình duyệt.
 */
object AppUpdate {

    data class Release(val version: String, val apkUrl: String)

    private const val API_URL =
        "https://api.github.com/repos/mjnamjkaze/DriveTune/releases/latest"
    private const val PREF = "aamusic"
    private const val KEY_CHECKED_AT = "update_checked_at"
    private const val KEY_VERSION = "update_version"
    private const val KEY_APK_URL = "update_apk_url"
    private const val KEY_NOTIFIED = "update_notified"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val APK_MIME = "application/vnd.android.package-archive"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Bản mới hơn bản đang chạy, theo lần hỏi gần nhất; null = đang là mới nhất. */
    fun available(context: Context): Release? {
        val p = prefs(context)
        val version = p.getString(KEY_VERSION, null) ?: return null
        val apk = p.getString(KEY_APK_URL, null) ?: return null
        return if (isNewer(version, currentVersion(context))) Release(version, apk) else null
    }

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /**
     * Hỏi GitHub nếu đã quá hạn (hoặc [force]). [onResult] chạy trên main
     * thread với bản mới (nếu có) — kể cả khi chưa tới hạn hỏi, dùng kết quả cũ.
     */
    fun check(
        context: Context,
        scope: CoroutineScope,
        force: Boolean = false,
        onResult: (Release?) -> Unit
    ) {
        val p = prefs(context)
        val due = force ||
            System.currentTimeMillis() - p.getLong(KEY_CHECKED_AT, 0) > CHECK_INTERVAL_MS
        if (!due) {
            onResult(available(context))
            return
        }
        scope.launch {
            val latest = withContext(Dispatchers.IO) { runCatching { fetchLatest() }.getOrNull() }
            if (latest != null) {
                p.edit()
                    .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                    .putString(KEY_VERSION, latest.version)
                    .putString(KEY_APK_URL, latest.apkUrl)
                    .apply()
            }
            onResult(available(context))
        }
    }

    /** Đúng một lần cho mỗi bản mới: trả true nếu chưa từng báo bản [version]. */
    fun markNotified(context: Context, version: String): Boolean {
        val p = prefs(context)
        if (p.getString(KEY_NOTIFIED, null) == version) return false
        p.edit().putString(KEY_NOTIFIED, version).apply()
        return true
    }

    /** Tải APK rồi mở màn cài đặt; lỗi thì mở link bằng trình duyệt. */
    fun download(context: Context, release: Release) {
        val app = context.applicationContext
        val dm = app.getSystemService(DownloadManager::class.java)
        val id = runCatching {
            val name = "DriveTune-${release.version}.apk"
            // Bản tải dở/cũ cùng tên thì DownloadManager đặt tên khác — xoá trước.
            app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.resolve(name)?.delete()
            dm.enqueue(
                DownloadManager.Request(Uri.parse(release.apkUrl))
                    .setTitle("DriveTune ${release.version}")
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, name)
            )
        }.getOrElse {
            openInBrowser(context, release.apkUrl)
            return
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                runCatching { app.unregisterReceiver(this) }
                val uri = dm.getUriForDownloadedFile(id)
                if (uri == null) {
                    openInBrowser(app, release.apkUrl)
                    return
                }
                val install = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { app.startActivity(install) }
                    .onFailure { openInBrowser(app, release.apkUrl) }
            }
        }
        app.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }

    private fun openInBrowser(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun fetchLatest(): Release? {
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("tag_name").removePrefix("v").trim()
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    val url = asset.optString("browser_download_url")
                    if (version.isNotEmpty() && url.isNotEmpty()) return Release(version, url)
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** "2.10.0" > "2.9.1": so từng số, thiếu coi như 0. */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
