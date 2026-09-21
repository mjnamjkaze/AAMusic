package com.gsvn.aamusic.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gsvn.aamusic.databinding.SheetSettingsBinding

/**
 * Bảng giới thiệu, mở bằng cách chạm logo trên thanh tìm kiếm.
 */
class SettingsSheet(private val activity: Activity) {

    private lateinit var binding: SheetSettingsBinding

    fun show() {
        binding = SheetSettingsBinding.inflate(activity.layoutInflater)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(binding.root)

        setupAbout()

        dialog.show()
    }

    private fun setupAbout() {
        val version = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty()
        binding.aboutVersion.text = if (version.isBlank()) "" else "v$version"

        binding.aboutRow.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_URL))
            runCatching { activity.startActivity(intent) }.onFailure {
                Toast.makeText(activity, ABOUT_URL, Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        const val ABOUT_URL = "https://gosei.com.vn/"
    }
}
