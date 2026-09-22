package com.gsvn.aamusic.car

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.data.DrivePlaylists
import com.gsvn.aamusic.data.VideoItem
import com.gsvn.aamusic.player.PlayerController

/**
 * Cây duyệt cho Android Auto: Yêu thích · Danh sách · Vừa nghe.
 *
 * App vẫn chiếu giao diện điện thoại lên màn xe như trước (xem các intent-filter
 * của MainActivity trong AndroidManifest) — service này **thêm vào**, không thay
 * thế: nó cho phép chọn bài từ giao diện media chuẩn của Android Auto, nơi hệ
 * thống tự vẽ nút to và lo phần chống mất tập trung.
 *
 * Nội dung cây hoàn toàn là thư viện cá nhân trong máy ([DriveLibrary]) và bảng
 * danh sách dựng sẵn ([DrivePlaylists]); service không gọi ra ngoài lấy gì cả.
 *
 * Phát nhạc vẫn do WebView trong MainActivity đảm nhiệm, nên chọn một mục ở đây
 * sẽ đưa app lên trước rồi mở địa chỉ tương ứng — đúng luồng mà bong bóng nổi
 * và notification đang dùng ([PlayerController.load]).
 */
class DriveBrowserService : MediaBrowserServiceCompat() {

    override fun onCreate() {
        super.onCreate()
        // Dùng chung phiên media sống suốt vòng đời app, để nút trên màn xe và
        // nút trên vô lăng cùng nói chuyện với một chỗ.
        val session = com.gsvn.aamusic.player.MediaSessionHolder.ensure(this)
        sessionToken = session.sessionToken
        com.gsvn.aamusic.player.MediaSessionHolder.onPlayRequest = { mediaId -> play(mediaId) }
    }

    override fun onDestroy() {
        com.gsvn.aamusic.player.MediaSessionHolder.onPlayRequest = null
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        val items = when {
            parentId == ROOT_ID -> rootMenu()
            parentId == NODE_FAVORITES -> DriveLibrary.favorites(this).map(::trackItem)
            parentId == NODE_RECENT -> DriveLibrary.recent(this).take(MAX_ROWS).map(::trackItem)
            parentId == NODE_PLAYLISTS -> DrivePlaylists.ALL.map(::playlistItem)
            else -> emptyList()
        }
        result.sendResult(items.toMutableList())
    }

    /**
     * Tìm trong thư viện của máy.
     *
     * App không có chỉ mục bài hát của riêng mình để tra, nên ở đây chỉ lọc
     * những gì người dùng đã nghe/đã thích. Muốn tìm mới thì dùng
     * `onPlayFromSearch` của phiên media — nó mở đúng trang kết quả YouTube.
     */
    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaItem>>
    ) {
        val needle = DrivePlaylists.normalize(query)
        val hits = (DriveLibrary.favorites(this) + DriveLibrary.recent(this))
            .distinctBy { it.id }
            .filter { item ->
                DrivePlaylists.normalize(item.title).contains(needle) ||
                    DrivePlaylists.normalize(item.channel).contains(needle)
            }
            .take(MAX_ROWS)
        result.sendResult(hits.map(::trackItem).toMutableList())
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

    private fun trackItem(item: VideoItem): MediaItem = MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(PREFIX_TRACK + item.id)
            .setTitle(item.title.ifBlank { getString(R.string.drive_unknown_track) })
            .setSubtitle(item.channel)
            .setIconUri(android.net.Uri.parse(item.thumbnailUrl))
            .build(),
        MediaItem.FLAG_PLAYABLE
    )

    private fun playlistItem(playlist: com.gsvn.aamusic.data.DrivePlaylist): MediaItem = MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(PREFIX_PLAYLIST + playlist.id)
            .setTitle(getString(playlist.nameRes))
            .build(),
        // Danh sách dựng sẵn là một tìm kiếm, không phải thư mục duyệt được:
        // chạm vào là phát luôn.
        MediaItem.FLAG_PLAYABLE
    )

    /** Xử lý mục vừa được chọn trên màn hình xe. */
    private fun play(mediaId: String) {
        when {
            mediaId.startsWith(PREFIX_TRACK) -> {
                val id = mediaId.removePrefix(PREFIX_TRACK)
                PlayerController.load(VideoItem(id, "", "", "").watchUrl)
            }

            mediaId.startsWith(PREFIX_PLAYLIST) -> {
                val playlist = DrivePlaylists.byId(mediaId.removePrefix(PREFIX_PLAYLIST)) ?: return
                if (playlist.isLocal) {
                    // "Yêu thích": phát bài đầu, phần còn lại xếp vào hàng chờ.
                    val favorites = DriveLibrary.favorites(this)
                    val first = favorites.firstOrNull() ?: return
                    DriveLibrary.replaceQueue(this, favorites.drop(1))
                    PlayerController.load(first.watchUrl)
                } else {
                    PlayerController.load(playlist.searchUrl)
                }
            }

            else -> PlayerController.play()
        }
    }

    private companion object {
        const val ROOT_ID = "drivetune_root"
        const val NODE_FAVORITES = "node_favorites"
        const val NODE_PLAYLISTS = "node_playlists"
        const val NODE_RECENT = "node_recent"
        const val PREFIX_TRACK = "track:"
        const val PREFIX_PLAYLIST = "playlist:"

        /** Màn hình xe không cuộn được dài; danh sách quá dài chỉ gây rối. */
        const val MAX_ROWS = 30
    }
}
