package com.gsvn.aamusic.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gsvn.aamusic.R
import com.gsvn.aamusic.data.DriveLibrary
import com.gsvn.aamusic.data.DrivePlaylist
import com.gsvn.aamusic.data.DrivePlaylists
import com.gsvn.aamusic.data.DriveSettings
import com.gsvn.aamusic.data.VideoItem
import com.gsvn.aamusic.data.YouTubeSearch
import com.gsvn.aamusic.databinding.SheetQueueBinding
import com.gsvn.aamusic.player.ArtworkCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Bảng thư viện: Tìm · Hàng chờ · Yêu thích · Vừa nghe · Danh sách.
 *
 * "Tìm" là đường thêm bài vào hàng chờ: gõ (hoặc nói) tên, bấm + ở bài muốn
 * nghe; chạm vào bài thì phát ngay; kéo xuống cuối thì tải thêm trang sau.
 * Trên màn hình Android Auto ô tìm dùng bàn phím của app ([CarKeyboard]) vì
 * bàn phím hệ thống ở đó hiện bé tí.
 *
 * Gộp tất cả vào một bảng trượt vì đang lái thì mỗi lần chuyển màn hình là một
 * lần rời mắt khỏi đường. Cách dựng hàng theo đúng lối `MainActivity` đang dùng
 * cho ô gợi ý tìm kiếm — inflate `item_*` rồi `addView` — thay vì kéo thêm
 * RecyclerView + adapter vào một dự án chưa dùng chúng ở đâu.
 *
 * @param onPlay     mở một bài (địa chỉ watch).
 * @param onPlaylist mở một danh sách dựng sẵn.
 * @param carDisplay đang chiếu lên màn hình xe — dùng bàn phím của app.
 * @param onVoice    nghe một câu rồi gọi lại với chữ nghe được; null = không có mic.
 */
class LibrarySheet(
    private val activity: Activity,
    private val onPlay: (VideoItem) -> Unit,
    private val onPlaylist: (DrivePlaylist) -> Unit,
    private val carDisplay: Boolean = false,
    private val onVoice: (((String) -> Unit) -> Unit)? = null
) {

    private enum class Tab(val labelRes: Int) {
        SEARCH(R.string.library_tab_search),
        QUEUE(R.string.library_tab_queue),
        FAVORITES(R.string.library_tab_favorites),
        RECENT(R.string.library_tab_recent),
        PLAYLISTS(R.string.library_tab_playlists)
    }

    private lateinit var binding: SheetQueueBinding
    private lateinit var dialog: BottomSheetDialog
    private var tab = Tab.QUEUE

    // ── Mục Tìm ──
    private var searchJob: Job? = null
    private var results: List<VideoItem> = emptyList()
    private var searchQuery: String = ""
    /** Mã trang kết quả kế tiếp; null = hết hoặc chưa tìm. */
    private var nextPage: String? = null
    private var loadingMore = false
    private var loadingRow: View? = null
    /** Chữ thay cho danh sách khi chưa có kết quả (gợi ý / đang tìm / lỗi). */
    private var searchStatus: Int = R.string.library_search_empty

    /** Báo cho nơi gọi biết thư viện đã đổi, để nút Yêu thích vẽ lại. */
    var onLibraryChanged: (() -> Unit)? = null

    fun show() {
        binding = SheetQueueBinding.inflate(activity.layoutInflater)
        dialog = BottomSheetDialog(activity)
        dialog.setContentView(binding.root)

        // Hàng chờ rỗng thì mở thẳng vào Danh sách — đó mới là thứ người dùng
        // cần khi vừa lên xe và chưa xếp bài nào.
        tab = if (DriveLibrary.queue(activity).isEmpty()) Tab.PLAYLISTS else Tab.QUEUE

        buildTabs()
        setupSearch()
        render()
        dialog.setOnDismissListener { searchJob?.cancel() }
        if (carDisplay) {
            // Màn hình xe thấp: mở hẳn bảng, khỏi phải kéo lên mới thấy bàn phím.
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }

    private fun setupSearch() {
        binding.searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
        }
        binding.searchGo.setOnClickListener { runSearch() }

        val voice = onVoice
        if (voice == null) {
            binding.searchMic.visibility = View.GONE
        } else {
            val listen = {
                voice { text ->
                    binding.searchField.setText(text)
                    binding.searchField.setSelection(text.length)
                    runSearch()
                }
            }
            binding.searchMic.setOnClickListener { listen() }
            binding.sheetCarKeyboard.onVoice = listen
        }

        if (carDisplay) setupCarKeyboard()

        // Kéo gần tới cuối danh sách kết quả thì tải trang sau.
        binding.queueScroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
                if (tab != Tab.SEARCH || nextPage == null || loadingMore) {
                    return@OnScrollChangeListener
                }
                val content = v.getChildAt(0)?.height ?: return@OnScrollChangeListener
                if (scrollY + v.height >= content - LOAD_MORE_THRESHOLD_PX) loadMore()
            }
        )
    }

    private fun setupCarKeyboard() {
        val field = binding.searchField
        val keyboard = binding.sheetCarKeyboard
        field.showSoftInputOnFocus = false
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) keyboard.visibility = View.VISIBLE
        }
        field.setOnClickListener { keyboard.visibility = View.VISIBLE }
        keyboard.onKey = { c ->
            val text = field.text
            if (text != null) {
                val a = field.selectionStart.coerceIn(0, text.length)
                val b = field.selectionEnd.coerceIn(0, text.length)
                text.replace(minOf(a, b), maxOf(a, b), c.toString())
            }
        }
        keyboard.onBackspace = {
            val text = field.text
            if (text != null) {
                val a = field.selectionStart
                val b = field.selectionEnd
                if (a != b) text.delete(minOf(a, b), maxOf(a, b))
                else if (a > 0) text.delete(a - 1, a)
            }
        }
        keyboard.onSearch = { runSearch() }
    }

    private fun runSearch() {
        val query = binding.searchField.text?.toString().orEmpty().trim()
        if (query.isEmpty()) return
        val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: return
        hideKeyboard()
        searchJob?.cancel()
        searchQuery = query
        results = emptyList()
        nextPage = null
        loadingMore = false
        searchStatus = R.string.library_searching
        render()
        searchJob = scope.launch {
            val found = YouTubeSearch.searchPage(query)
            val page = found.getOrNull()
            results = page?.items.orEmpty()
            nextPage = page?.next
            searchStatus = when {
                found.isFailure -> R.string.library_search_error
                results.isEmpty() -> R.string.library_search_none
                else -> 0
            }
            if (tab == Tab.SEARCH) {
                render()
                binding.queueScroll.scrollTo(0, 0)
            }
        }
    }

    /** Trang kết quả kế tiếp, nối vào cuối danh sách (không vẽ lại từ đầu). */
    private fun loadMore() {
        val token = nextPage ?: return
        val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: return
        loadingMore = true
        showLoadingRow(true)
        val query = searchQuery
        searchJob = scope.launch {
            val page = YouTubeSearch.searchPage(query, token).getOrNull()
            loadingMore = false
            if (query != searchQuery) return@launch
            showLoadingRow(false)
            // Mất mạng giữa chừng: giữ mã cũ, kéo lại là thử lại.
            if (page == null) return@launch
            nextPage = page.next
            val known = results.mapTo(HashSet()) { it.id }
            val fresh = page.items.filter { it.id !in known }
            results = results + fresh
            if (tab != Tab.SEARCH) return@launch
            val inflater = activity.layoutInflater
            for (item in fresh) {
                binding.queueList.addView(buildTrackRow(inflater, binding.queueList, item, null))
            }
        }
    }

    private fun showLoadingRow(show: Boolean) {
        loadingRow?.let { binding.queueList.removeView(it) }
        loadingRow = null
        if (!show || tab != Tab.SEARCH) return
        loadingRow = TextView(activity).apply {
            setText(R.string.library_loading_more)
            setTextColor(activity.getColor(R.color.drive_text_dim))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            val pad = (16 * activity.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        binding.queueList.addView(loadingRow)
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.searchField.windowToken, 0)
        binding.sheetCarKeyboard.visibility = View.GONE
        binding.searchField.clearFocus()
    }

    private fun buildTabs() {
        val inflater = activity.layoutInflater
        binding.queueTabs.removeAllViews()
        for (entry in Tab.entries) {
            val chip = inflater.inflate(
                R.layout.item_queue_tab, binding.queueTabs, false
            ) as TextView
            chip.text = activity.getString(entry.labelRes)
            chip.isSelected = entry == tab
            chip.setOnClickListener {
                if (tab == entry) return@setOnClickListener
                tab = entry
                for (i in 0 until binding.queueTabs.childCount) {
                    binding.queueTabs.getChildAt(i).isSelected = (i == entry.ordinal)
                }
                render()
            }
            binding.queueTabs.addView(chip)
        }
    }

    private fun render() {
        val list = binding.queueList
        list.removeAllViews()
        binding.searchBox.visibility = if (tab == Tab.SEARCH) View.VISIBLE else View.GONE
        if (tab != Tab.SEARCH) binding.sheetCarKeyboard.visibility = View.GONE
        loadingRow = null

        when (tab) {
            Tab.SEARCH -> {
                setAction(0, null)
                setEmpty(if (results.isEmpty()) searchStatus else 0)
                val inflater = activity.layoutInflater
                for (item in results) {
                    list.addView(buildTrackRow(inflater, list, item, onRemove = null))
                }
            }

            Tab.QUEUE -> renderTracks(
                DriveLibrary.queue(activity),
                emptyRes = R.string.library_empty_queue,
                actionRes = R.string.library_clear_queue,
                onAction = { DriveLibrary.clearQueue(activity); render() },
                onRemove = { DriveLibrary.removeFromQueue(activity, it.id) }
            )

            Tab.FAVORITES -> renderTracks(
                DriveLibrary.favorites(activity),
                emptyRes = R.string.library_empty_favorites,
                actionRes = 0,
                onAction = null,
                onRemove = {
                    DriveLibrary.removeFavorite(activity, it.id)
                    onLibraryChanged?.invoke()
                }
            )

            Tab.RECENT -> renderTracks(
                DriveLibrary.recent(activity),
                emptyRes = R.string.library_empty_recent,
                actionRes = R.string.library_clear_recent,
                onAction = { DriveLibrary.clearRecent(activity); render() },
                onRemove = null
            )

            Tab.PLAYLISTS -> renderPlaylists(list)
        }
    }

    private fun renderTracks(
        items: List<VideoItem>,
        emptyRes: Int,
        actionRes: Int,
        onAction: (() -> Unit)?,
        onRemove: ((VideoItem) -> Unit)?
    ) {
        setAction(if (items.isEmpty()) 0 else actionRes, onAction)
        setEmpty(if (items.isEmpty()) emptyRes else 0)

        val inflater = activity.layoutInflater
        for (item in items) {
            binding.queueList.addView(buildTrackRow(inflater, binding.queueList, item, onRemove))
        }
    }

    private fun buildTrackRow(
        inflater: LayoutInflater,
        parent: ViewGroup,
        item: VideoItem,
        onRemove: ((VideoItem) -> Unit)?
    ): View {
        val row = inflater.inflate(R.layout.item_track, parent, false)
        row.findViewById<TextView>(R.id.trackTitle).text =
            item.title.ifBlank { activity.getString(R.string.drive_unknown_track) }
        row.findViewById<TextView>(R.id.trackSubtitle).text =
            listOf(item.channel, item.duration).filter { it.isNotBlank() }.joinToString(" · ")

        // Ảnh đã nằm trong cache thì callback chạy ngay tại đây, lúc hàng còn
        // chưa gắn vào bảng — nên đánh dấu bằng tag chứ đừng hỏi isAttachedToWindow.
        val thumb = row.findViewById<ImageView>(R.id.trackThumb)
        thumb.tag = item.id
        ArtworkCache.load(item.id) { bitmap ->
            if (thumb.tag != item.id) return@load
            // app:tint trong layout là màu của ảnh giữ chỗ; còn để nguyên thì
            // nó nhuộm luôn cả ảnh bìa thật.
            thumb.imageTintList = null
            thumb.clearColorFilter()
            thumb.setPadding(0, 0, 0, 0)
            thumb.setImageBitmap(bitmap)
        }

        // Ở mục Hàng chờ thì nút "thêm vào hàng chờ" là thừa.
        val queueButton = row.findViewById<ImageButton>(R.id.trackQueue)
        if (tab == Tab.QUEUE) {
            queueButton.visibility = View.GONE
        } else {
            queueButton.setOnClickListener {
                DriveLibrary.enqueue(activity, item)
                // Tô màu nhấn để thấy bài nào đã thêm khi thêm liền nhiều bài.
                queueButton.imageTintList =
                    ColorStateList.valueOf(activity.getColor(R.color.drive_accent))
                toast(R.string.drive_queued)
            }
        }

        val removeButton = row.findViewById<ImageButton>(R.id.trackRemove)
        if (onRemove == null) {
            removeButton.visibility = View.GONE
        } else {
            removeButton.setOnClickListener {
                onRemove(item)
                render()
            }
        }

        row.setOnClickListener {
            onPlay(item)
            dialog.dismiss()
        }
        return row
    }

    private fun renderPlaylists(list: LinearLayout) {
        setAction(0, null)
        setEmpty(0)

        val inflater = activity.layoutInflater
        val defaultId = DriveSettings.defaultPlaylist(activity)
        for (playlist in DrivePlaylists.ALL) {
            val row = inflater.inflate(R.layout.item_playlist, list, false)
            row.findViewById<ImageView>(R.id.playlistIcon).setImageResource(playlist.iconRes)
            row.findViewById<TextView>(R.id.playlistName).text =
                activity.getString(playlist.nameRes)

            // Ghim = mở sẵn danh sách này mỗi khi vào Drive Mode.
            val pin = row.findViewById<ImageButton>(R.id.playlistDefault)
            val pinned = playlist.id == defaultId
            pin.imageTintList = ColorStateList.valueOf(
                activity.getColor(if (pinned) R.color.drive_accent else R.color.drive_text_dim)
            )
            pin.setOnClickListener {
                DriveSettings.setDefaultPlaylist(activity, if (pinned) "" else playlist.id)
                toast(if (pinned) R.string.library_default_cleared else R.string.library_default_set)
                render()
            }

            row.setOnClickListener {
                onPlaylist(playlist)
                dialog.dismiss()
            }
            list.addView(row)
        }
    }

    private fun setAction(textRes: Int, onAction: (() -> Unit)?) {
        if (textRes == 0 || onAction == null) {
            binding.queueAction.visibility = View.GONE
            return
        }
        binding.queueAction.visibility = View.VISIBLE
        binding.queueAction.setText(textRes)
        binding.queueAction.setOnClickListener { onAction() }
    }

    private fun setEmpty(textRes: Int) {
        if (textRes == 0) {
            binding.queueEmpty.visibility = View.GONE
            return
        }
        binding.queueEmpty.visibility = View.VISIBLE
        binding.queueEmpty.setText(textRes)
    }

    private companion object {
        /** Còn chừng này px nữa là chạm đáy thì tải trang sau. */
        const val LOAD_MORE_THRESHOLD_PX = 600
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(activity, resId, android.widget.Toast.LENGTH_SHORT).show()
    }
}
