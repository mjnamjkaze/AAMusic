package com.gsvn.aamusic.car

import android.content.Context
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.data.DrivePlaylist
import com.gsvn.aamusic.data.DrivePlaylists
import com.gsvn.aamusic.data.DriveSettings
import com.gsvn.aamusic.data.VideoItem
import com.gsvn.aamusic.data.YouTubeSearch
import com.gsvn.aamusic.player.MediaSessionHolder
import com.gsvn.aamusic.player.PlaybackHost
import com.gsvn.aamusic.player.PlayerController
import com.gsvn.aamusic.voice.VoiceCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Mọi lệnh phát đến từ xe: chọn một mục trong cây duyệt, tìm kiếm / đọc lệnh
 * cho Trợ lý, nút play và bài kế trên màn hình xe hoặc vô lăng.
 *
 * Không phụ thuộc [DriveBrowserService] còn sống hay activity có mặt — phiên
 * media gọi thẳng vào đây, còn việc có WebView để phát là của [PlaybackHost].
 *
 * Danh sách dựng sẵn và kết quả tìm kiếm được phân giải thành **bài cụ thể**
 * bằng [YouTubeSearch] rồi phát bài đầu, xếp phần còn lại vào hàng chờ. Bản cũ
 * mở trang kết quả tìm kiếm của YouTube — trang đó không tự phát gì nên xe đứng
 * mãi ở "Đang tải dữ liệu...".
 */
object CarPlayback {

    // Media id của một bài: "track|<nguồn>|<id video>". Nguồn cho biết bài nằm
    // trong danh sách nào để xếp các bài sau nó vào hàng chờ.
    private const val PREFIX_TRACK = "track|"
    const val PREFIX_PLAYLIST = "playlist:"
    const val SOURCE_FAVORITES = "fav"
    const val SOURCE_RECENT = "recent"
    const val SOURCE_SEARCH = "search"
    const val SOURCE_RESUME = "resume"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Tăng mỗi lần có yêu cầu mới; kết quả tìm về muộn của yêu cầu cũ bị bỏ. */
    private var requestSeq = 0

    /** Kết quả tìm kiếm gần nhất — chọn một bài trong đó thì biết bài kế. */
    private var lastSearch: List<VideoItem> = emptyList()

    fun trackMediaId(source: String, item: VideoItem): String =
        "$PREFIX_TRACK$source|${item.id}"

    fun rememberSearch(items: List<VideoItem>) {
        lastSearch = items
    }

    // ── Chọn trong cây duyệt ───────────────────────────────────────

    fun playMediaId(context: Context, mediaId: String) {
        requestSeq++
        when {
            mediaId.startsWith(PREFIX_TRACK) -> {
                val parts = mediaId.removePrefix(PREFIX_TRACK).split('|', limit = 2)
                if (parts.size == 2) playTrack(context, parts[0], parts[1])
                else playOrResume(context)
            }

            mediaId.startsWith(PREFIX_PLAYLIST) -> {
                val playlist = DrivePlaylists.byId(mediaId.removePrefix(PREFIX_PLAYLIST))
                if (playlist != null) playPlaylist(context, playlist) else playOrResume(context)
            }

            else -> playOrResume(context)
        }
    }

    private fun playTrack(context: Context, source: String, videoId: String) {
        val list = when (source) {
            SOURCE_FAVORITES -> DriveLibrary.favorites(context)
            SOURCE_RECENT -> DriveLibrary.recent(context)
            SOURCE_SEARCH -> lastSearch
            else -> emptyList()
        }
        val index = list.indexOfFirst { it.id == videoId }
        val item = list.getOrNull(index)
            ?: DriveLibrary.resumePoint(context)?.item?.takeIf { it.id == videoId }
            ?: VideoItem(videoId, "", "", "")

        // "Vừa nghe" là lịch sử, không phải danh sách để nghe lần lượt; còn
        // Yêu thích / kết quả tìm thì nghe tiếp các bài sau bài vừa chọn.
        if (source == SOURCE_FAVORITES || source == SOURCE_SEARCH) {
            if (index >= 0) DriveLibrary.replaceQueue(context, list.drop(index + 1))
        }
        val start = if (source == SOURCE_RESUME) {
            DriveLibrary.resumePoint(context)?.takeIf { it.item.id == videoId }?.positionSec ?: 0
        } else 0
        PlaybackHost.play(context, item, start)
    }

    fun playPlaylist(context: Context, playlist: DrivePlaylist) {
        if (playlist.isLocal) {
            val favorites = DriveLibrary.favorites(context)
            val first = favorites.firstOrNull()
            if (first == null) {
                MediaSessionHolder.showError(context.getString(R.string.aa_error_no_favorites))
                return
            }
            DriveLibrary.replaceQueue(context, favorites.drop(1))
            PlaybackHost.play(context, first)
            return
        }
        searchAndPlay(context, playlist.query, context.getString(playlist.nameRes))
    }

    // ── Tìm kiếm / Trợ lý ──────────────────────────────────────────

    /**
     * "Phát <gì đó> trên DriveTune", hoặc chọn một từ khoá trong ô tìm của xe.
     * Câu nói được hiểu như lệnh nói trong app: "bài tiếp", "nhạc rock"…
     */
    fun playQuery(context: Context, query: String) {
        requestSeq++
        val text = query.trim()
        if (text.isEmpty()) {
            playOrResume(context)
            return
        }
        when (val action = VoiceCommands.parse(text)) {
            is VoiceCommands.Action.Next -> next(context)
            is VoiceCommands.Action.Previous -> PlayerController.previous()
            is VoiceCommands.Action.Pause -> PlayerController.pause()
            is VoiceCommands.Action.Resume -> playOrResume(context)
            is VoiceCommands.Action.OpenPlaylist -> playPlaylist(context, action.playlist)
            is VoiceCommands.Action.Search -> searchAndPlay(context, action.query, action.query)
        }
    }

    /**
     * Tìm [query] rồi phát bài đầu, xếp các bài còn lại vào hàng chờ.
     * [onError] để giao diện trong app báo thêm (màn hình xe đã có lỗi trên phiên).
     */
    fun searchAndPlay(
        context: Context,
        query: String,
        label: String = query,
        onError: ((String) -> Unit)? = null
    ) {
        val seq = ++requestSeq
        fun fail(message: String) {
            MediaSessionHolder.showError(message)
            onError?.invoke(message)
        }
        MediaSessionHolder.showPending(
            VideoItem("", label, context.getString(R.string.aa_searching), "")
        )
        scope.launch {
            val result = YouTubeSearch.search(query)
            if (seq != requestSeq) return@launch
            val items = result.getOrNull()
            when {
                items == null -> fail(context.getString(R.string.aa_error_network))
                items.isEmpty() -> fail(context.getString(R.string.aa_error_no_results, label))
                else -> {
                    rememberSearch(items)
                    DriveLibrary.replaceQueue(context, items.drop(1))
                    PlaybackHost.play(context, items.first())
                }
            }
        }
    }

    /** Kết quả cho ô tìm của Android Auto: bài trong máy trước, rồi YouTube. */
    suspend fun search(context: Context, query: String): List<VideoItem> {
        val needle = DrivePlaylists.normalize(query)
        val local = (DriveLibrary.favorites(context) + DriveLibrary.recent(context))
            .distinctBy { it.id }
            .filter { item ->
                DrivePlaylists.normalize(item.title).contains(needle) ||
                    DrivePlaylists.normalize(item.channel).contains(needle)
            }
            .take(MAX_LOCAL_HITS)
        val online = YouTubeSearch.search(query).getOrDefault(emptyList())
        return (local + online).distinctBy { it.id }
    }

    // ── Nút trên xe ────────────────────────────────────────────────

    /**
     * Nút play. Đang có bài thì phát tiếp; chưa có gì (tiến trình vừa mở lên vì
     * xe) thì nghe tiếp chỗ lần trước, không có thì danh sách mặc định, không
     * nữa thì bài vừa nghe gần nhất.
     */
    fun playOrResume(context: Context) {
        requestSeq++
        val hasPlayer = PlayerController.currentWebView() != null
        if (hasPlayer && MediaSessionHolder.lastPlayerState?.hasTrack == true) {
            PlayerController.play()
            return
        }
        DriveLibrary.resumePoint(context)?.let {
            PlaybackHost.play(context, it.item, it.positionSec)
            return
        }
        DrivePlaylists.byId(DriveSettings.defaultPlaylist(context))?.let {
            playPlaylist(context, it)
            return
        }
        val fallback = DriveLibrary.recent(context).firstOrNull()
            ?: DriveLibrary.favorites(context).firstOrNull()
        if (fallback != null) PlaybackHost.play(context, fallback)
        else MediaSessionHolder.showError(context.getString(R.string.aa_error_nothing))
    }

    /** Bài kế: theo hàng chờ của app nếu có, không thì để YouTube chọn. */
    fun next(context: Context) {
        requestSeq++
        val queued = DriveLibrary.popQueue(context)
        if (queued != null) PlaybackHost.play(context, queued)
        else PlayerController.next()
    }

    private const val MAX_LOCAL_HITS = 5
}
