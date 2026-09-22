package com.gsvn.aamusic.voice

import com.gsvn.aamusic.data.DrivePlaylists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoiceCommands] là chỗ dễ sai nhất trong đợt này: nó cắt tiền tố trên chuỗi
 * đã bỏ dấu nhưng phải trả lại chuỗi còn dấu, và ranh giới giữa "lệnh điều
 * khiển" với "tên bài cần tìm" chỉ nằm ở vài từ.
 *
 * Chạy được trên JVM vì [VoiceCommands] không đụng gì tới Android — phần duy
 * nhất cần Android (`DrivePlaylist.searchUrl`) không nằm trên đường đi của
 * `parse`.
 */
class VoiceCommandsTest {

    // ── Lệnh điều khiển ───────────────────────────────────────────

    @Test
    fun `nhan lenh chuyen bai`() {
        for (spoken in listOf("bài sau", "Bài Sau", "bai tiep theo", "next", "skip")) {
            assertEquals(spoken, VoiceCommands.Action.Next, VoiceCommands.parse(spoken))
        }
    }

    @Test
    fun `nhan lenh bai truoc`() {
        assertEquals(VoiceCommands.Action.Previous, VoiceCommands.parse("bài trước"))
        assertEquals(VoiceCommands.Action.Previous, VoiceCommands.parse("previous"))
    }

    @Test
    fun `nhan lenh tam dung va phat tiep`() {
        assertEquals(VoiceCommands.Action.Pause, VoiceCommands.parse("tạm dừng"))
        assertEquals(VoiceCommands.Action.Pause, VoiceCommands.parse("pause"))
        assertEquals(VoiceCommands.Action.Resume, VoiceCommands.parse("tiếp tục"))
        assertEquals(VoiceCommands.Action.Resume, VoiceCommands.parse("play"))
    }

    /** "phát nhạc" bỏ tiền tố xong không còn gì — vẫn phải là lệnh phát tiếp. */
    @Test
    fun `phat nhac khong con gi thi la phat tiep`() {
        assertEquals(VoiceCommands.Action.Resume, VoiceCommands.parse("phát nhạc"))
        assertEquals(VoiceCommands.Action.Resume, VoiceCommands.parse("mở nhạc"))
    }

    // ── Danh sách dựng sẵn ────────────────────────────────────────

    @Test
    fun `nhan ten danh sach co va khong dau`() {
        val rock = VoiceCommands.parse("phát nhạc rock")
        assertTrue(rock is VoiceCommands.Action.OpenPlaylist)
        assertEquals("rock", (rock as VoiceCommands.Action.OpenPlaylist).playlist.id)

        val viet = VoiceCommands.parse("mở nhạc việt")
        assertEquals(
            "vietnamese",
            (viet as VoiceCommands.Action.OpenPlaylist).playlist.id
        )

        val fav = VoiceCommands.parse("phát yêu thích")
        assertEquals(
            DrivePlaylists.ID_FAVORITES,
            (fav as VoiceCommands.Action.OpenPlaylist).playlist.id
        )
    }

    // ── Tìm kiếm ──────────────────────────────────────────────────

    /**
     * Câu dài là tên bài cụ thể, không phải tên danh sách — kể cả khi có chứa
     * đúng từ khoá của một danh sách ("rock").
     */
    @Test
    fun `cau dai van la tim kiem du co tu khoa danh sach`() {
        val action = VoiceCommands.parse("phát Rock Anh Nghe Em Hát Bản Acoustic")
        assertTrue(action is VoiceCommands.Action.Search)
        assertEquals(
            "Rock Anh Nghe Em Hát Bản Acoustic",
            (action as VoiceCommands.Action.Search).query
        )
    }

    /** Cắt tiền tố trên bản bỏ dấu, nhưng phải trả lại chữ CÒN DẤU cho YouTube. */
    @Test
    fun `giu nguyen dau tieng viet trong tu khoa tim`() {
        val action = VoiceCommands.parse("phát Nơi Này Có Anh")
        assertEquals("Nơi Này Có Anh", (action as VoiceCommands.Action.Search).query)
    }

    @Test
    fun `khong co tien to thi giu nguyen ca cau`() {
        val action = VoiceCommands.parse("Sơn Tùng MTP")
        assertEquals("Sơn Tùng MTP", (action as VoiceCommands.Action.Search).query)
    }

    @Test
    fun `cau rong khong lam vo gi`() {
        assertEquals(VoiceCommands.Action.Search(""), VoiceCommands.parse("   "))
    }

    // ── Chuẩn hoá ─────────────────────────────────────────────────

    @Test
    fun `bo dau va chu d gach ngang`() {
        assertEquals("nhac viet", DrivePlaylists.normalize("Nhạc Việt"))
        assertEquals("dem khuya", DrivePlaylists.normalize("Đêm Khuya"))
    }
}
