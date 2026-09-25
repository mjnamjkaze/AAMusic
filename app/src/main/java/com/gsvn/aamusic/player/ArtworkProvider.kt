package com.gsvn.aamusic.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Ảnh bìa cho màn hình xe.
 *
 * Android Auto chỉ nhận ảnh qua `content://` (hoặc `android.resource://`) —
 * địa chỉ https của YouTube thì nó bỏ qua, danh sách trên xe trống ảnh. Provider
 * này trả tệp ảnh trong cache đĩa của [ArtworkCache], chưa có thì tải về ngay
 * (openFile chạy trên luồng binder nên chặn được).
 *
 * Chỉ đọc, và chỉ phục vụ ảnh đại diện công khai theo id video đã kiểm tra
 * định dạng, nên để exported cho Android Auto đọc được.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { ArtworkCache.attach(it) }
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment?.removeSuffix(".jpg").orEmpty()
        val file = ArtworkCache.fileBlocking(id) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val AUTHORITY = "com.gsvn.aamusic.artwork"

        fun uriFor(videoId: String): Uri = Uri.parse("content://$AUTHORITY/$videoId.jpg")
    }
}
