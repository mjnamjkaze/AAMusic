package com.gsvn.aamusic.voice

import com.gsvn.aamusic.data.DrivePlaylist
import com.gsvn.aamusic.data.DrivePlaylists

/**
 * Hiểu câu nói của người lái thành một thao tác.
 *
 * Cố tình dừng ở mức bảng từ khoá, không phải trợ lý: đang lái thì thứ cần là
 * đoán đúng vài câu hay dùng nhất và **không bao giờ đoán sai thành im lặng** —
 * không khớp lệnh nào thì rơi về tìm kiếm, đúng như hành vi trước đây.
 *
 * So khớp trên chuỗi đã bỏ dấu ([DrivePlaylists.normalize]) nên máy nghe ra
 * "bai sau" hay "bài sau" đều như nhau.
 */
object VoiceCommands {

    sealed interface Action {
        data object Next : Action
        data object Previous : Action
        data object Pause : Action
        data object Resume : Action
        data class OpenPlaylist(val playlist: DrivePlaylist) : Action
        data class Search(val query: String) : Action
    }

    // Lệnh điều khiển phải khớp *cả câu* (sau khi bỏ tiền tố), nếu không thì
    // "phát bài hát tiếp theo của Sơn Tùng" lại bị hiểu thành bấm nút chuyển bài.
    private val NEXT = setOf(
        "tiep", "tiep theo", "bai sau", "bai tiep", "bai tiep theo", "chuyen bai",
        "qua bai khac", "next", "next song", "skip"
    )
    private val PREVIOUS = setOf(
        "bai truoc", "quay lai", "lui lai", "tro lai", "previous", "prev", "back"
    )
    private val PAUSE = setOf(
        "dung", "dung lai", "tam dung", "ngung", "tat nhac", "im lang", "pause", "stop"
    )
    private val RESUME = setOf(
        "tiep tuc", "phat tiep", "nghe tiep", "phat", "mo nhac", "phat nhac",
        "bat nhac", "nghe nhac", "resume", "play", "continue", "play music"
    )

    /**
     * Tiền tố "hãy phát…", "mở…" — bỏ đi để lấy phần thực sự người dùng muốn.
     *
     * Cố tình CHỈ có động từ, không có "phát nhạc"/"mở nhạc": cắt luôn chữ
     * "nhạc" thì "mở nhạc việt" chỉ còn "việt", mà tên danh sách lại đăng ký là
     * "nhạc việt" — hỏng. Những câu chỉ có đúng "phát nhạc" đã nằm sẵn trong
     * [RESUME] và được nhận ở bước khớp cả câu phía trên.
     */
    private val PLAY_PREFIXES = listOf(
        "hay phat", "phat", "mo", "bat", "nghe", "tim kiem", "tim",
        "play some", "play me", "put on", "search for", "play", "search"
    )

    /**
     * Câu dài gần như luôn là tên một bài cụ thể ("phát Rock Anh Nghe Em Hát"),
     * nên chỉ nhận là tên danh sách dựng sẵn khi phần còn lại đủ ngắn.
     */
    private const val PLAYLIST_WORD_LIMIT = 4

    fun parse(spoken: String): Action {
        val words = spoken.trim().split(WHITESPACE).filter { it.isNotBlank() }
        if (words.isEmpty()) return Action.Search("")

        val normalized = words.map { DrivePlaylists.normalize(it) }
        asCommand(normalized.joinToString(" "))?.let { return it }

        val body = stripLeading(words, normalized, PLAY_PREFIXES)
        // "phát"/"mở" trơ trọi: bỏ tiền tố xong chẳng còn gì — là lệnh phát tiếp.
        if (body.isEmpty()) return Action.Resume
        asCommand(body.normalizedText)?.let { return it }

        // Khớp thẳng trên phần còn lại: [DrivePlaylists.match] đã dò theo kiểu
        // "có chứa", nên "danh sách yêu thích" vẫn trúng bí danh "yêu thích" mà
        // không cần cắt thêm từ đệm nào.
        if (body.words.size <= PLAYLIST_WORD_LIMIT) {
            DrivePlaylists.match(body.normalizedText)?.let { return Action.OpenPlaylist(it) }
        }

        // Không khớp lệnh nào: coi là từ khoá tìm kiếm. Trả về chữ gốc (còn dấu)
        // để YouTube tìm đúng tiếng Việt.
        return Action.Search(body.text)
    }

    private fun asCommand(text: String): Action? = when (text) {
        in NEXT -> Action.Next
        in PREVIOUS -> Action.Previous
        in PAUSE -> Action.Pause
        in RESUME -> Action.Resume
        else -> null
    }

    /** Câu đã cắt tiền tố, giữ song song bản gốc (có dấu) và bản đã chuẩn hoá. */
    private class Phrase(val words: List<String>, val normalized: List<String>) {
        val text: String get() = words.joinToString(" ")
        val normalizedText: String get() = normalized.joinToString(" ")
        fun isEmpty() = words.isEmpty()
    }

    /**
     * Bỏ tiền tố dài nhất trong [prefixes] khỏi câu.
     *
     * Cắt theo *từ* chứ không theo số ký tự: bản chuẩn hoá và bản gốc có thể
     * lệch độ dài (chữ có dấu), nhưng số từ thì luôn khớp.
     */
    private fun stripLeading(
        words: List<String>,
        normalized: List<String>,
        prefixes: List<String>
    ): Phrase {
        for (prefix in prefixes.sortedByDescending { it.count { c -> c == ' ' } }) {
            val parts = prefix.split(' ')
            if (parts.size > normalized.size) continue
            if (normalized.subList(0, parts.size) != parts) continue
            // Cắt trụi cả câu cũng chấp nhận: nơi gọi tự quyết nghĩa của rỗng.
            return Phrase(words.drop(parts.size), normalized.drop(parts.size))
        }
        return Phrase(words, normalized)
    }

    private val WHITESPACE = Regex("\\s+")
}
