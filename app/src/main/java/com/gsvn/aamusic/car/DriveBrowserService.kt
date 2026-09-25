package com.gsvn.aamusic.car

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.data.DrivePlaylist
import com.gsvn.aamusic.data.DrivePlaylists
import com.gsvn.aamusic.data.VideoItem
import com.gsvn.aamusic.player.ArtworkProvider
import com.gsvn.aamusic.player.MediaSessionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Cây duyệt cho Android Auto: Yêu thích · Danh sách · Vừa nghe, cộng ô tìm kiếm.
 *
 * Đây là giao diện duy nhất Android Auto cho dùng **lúc xe đang chạy** (hệ
 * thống tự vẽ nút to, khoá bàn phím, cho đọc bằng giọng nói). App vẫn chiếu
 * giao diện điện thoại lên màn xe như trước (xem các intent-filter của
 * MainActivity trong AndroidManifest) — service này **thêm vào**, không thay thế.
 *
 * Service chỉ dựng cây; chọn một mục thì phiên media gọi thẳng [CarPlayback],
 * không phụ thuộc service hay activity còn sống.
 */
class DriveBrowserService : MediaBrowserServiceCompat() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Phiên media sống suốt vòng đời tiến trình; token chỉ đặt được một lần.
        sessionToken = MediaSessionHolder.ensure(this).sessionToken
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        // Hệ thống hỏi "bài vừa nghe" để hiện nút nghe tiếp lúc khởi động —
        // không hỗ trợ, trả null như NewPipe để khỏi bị gọi phát ngoài ý muốn.
        if (rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) == true) return null
        val extras = Bundle().apply {
            // Không có cờ này thì Android Auto không hiện ô tìm kiếm, và lệnh
            // "tìm …" bằng giọng nói không bao giờ tới app.
            putBoolean(EXTRA_SEARCH_SUPPORTED, true)
            putInt(EXTRA_STYLE_BROWSABLE, STYLE_LIST)
            putInt(EXTRA_STYLE_PLAYABLE, STYLE_LIST)
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        val items = when (parentId) {
            ROOT_ID -> rootMenu()
            NODE_FAVORITES -> DriveLibrary.favorites(this)
                .map { trackItem(CarPlayback.SOURCE_FAVORITES, it) }
            NODE_RECENT -> DriveLibrary.recent(this).take(MAX_ROWS)
                .map { trackItem(CarPlayback.SOURCE_RECENT, it) }
            NODE_PLAYLISTS -> DrivePlaylists.ALL.map(::playlistItem)
            else -> emptyList()
        }
        result.sendResult(items.toMutableList())
    }

    /** Ô tìm của xe (gõ khi đỗ, hoặc đọc khi đang chạy): bài trong máy + YouTube. */
    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaItem>>
    ) {
        result.detach()
        scope.launch {
            val hits = runCatching { CarPlayback.search(this@DriveBrowserService, query) }
                .getOrDefault(emptyList())
                .take(MAX_ROWS)
            CarPlayback.rememberSearch(hits)
            result.sendResult(
                hits.map { trackItem(CarPlayback.SOURCE_SEARCH, it) }.toMutableList()
            )
        }
    }

    private fun rootMenu(): List<MediaItem> = listOf(
        browsableItem(NODE_FAVORITES, getString(R.string.library_tab_favorites)),
        browsableItem(NODE_PLAYLISTS, getString(R.string.library_tab_playlists)),
        browsableItem(NODE_RECENT, getString(R.string.library_tab_recent))
    )

    private fun browsableItem(id: String, title: String): MediaItem = MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).build(),
        MediaItem.FLAG_BROWSABLE
    )

    private fun trackItem(source: String, item: VideoItem): MediaItem = MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(CarPlayback.trackMediaId(source, item))
            .setTitle(item.title.ifBlank { getString(R.string.drive_unknown_track) })
            .setSubtitle(item.channel)
            .setIconUri(ArtworkProvider.uriFor(item.id))
            .build(),
        MediaItem.FLAG_PLAYABLE
    )

    private fun playlistItem(playlist: DrivePlaylist): MediaItem = MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(CarPlayback.PREFIX_PLAYLIST + playlist.id)
            .setTitle(getString(playlist.nameRes))
            .setIconUri(resourceUri(playlist.iconRes))
            // Các mục liền nhau cùng nhóm được xe gom dưới một tiêu đề.
            .setExtras(Bundle().apply {
                putString(EXTRA_GROUP_TITLE, getString(playlist.groupRes))
            })
            .build(),
        // Danh sách dựng sẵn là một tìm kiếm, không phải thư mục duyệt được:
        // chạm vào là phát luôn — lúc lái xe càng ít chạm càng tốt.
        MediaItem.FLAG_PLAYABLE
    )

    private fun resourceUri(resId: Int): android.net.Uri =
        android.net.Uri.parse("android.resource://$packageName/$resId")

    private companion object {
        const val ROOT_ID = "drivetune_root"
        const val NODE_FAVORITES = "node_favorites"
        const val NODE_PLAYLISTS = "node_playlists"
        const val NODE_RECENT = "node_recent"

        // androidx.media.utils.MediaConstants — viết tay để khỏi thêm thư viện.
        const val EXTRA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        const val EXTRA_STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val EXTRA_STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val STYLE_LIST = 1
        const val EXTRA_GROUP_TITLE = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"

        /** Màn hình xe không cuộn được dài; danh sách quá dài chỉ gây rối. */
        const val MAX_ROWS = 30
    }
}
