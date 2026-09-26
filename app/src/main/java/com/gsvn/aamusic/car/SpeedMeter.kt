package com.gsvn.aamusic.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Tốc độ xe lấy từ GPS của điện thoại.
 *
 * Android Auto không cho app nhạc đọc tốc độ từ xe (CarHardwareManager chỉ
 * mở cho app dạng template), nên dùng GPS: sai số vài km/h, đủ để nhìn.
 * Chỉ chạy khi màn hình app đang hiện (MainActivity bật ở onResume, tắt ở
 * onPause) — không ai nhìn thì không tốn pin cho GPS.
 *
 * @param onSpeed km/h đã làm tròn, hoặc null khi chưa bắt được GPS / mất tín
 *   hiệu quá [STALE_MS].
 */
class SpeedMeter(
    private val context: Context,
    private val onSpeed: (Int?) -> Unit
) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    /** Mất tín hiệu (hầm, nhà xe): quá lâu không có điểm mới thì hiện "--". */
    private val staleCheck = Runnable { onSpeed(null) }

    private val listener = LocationListener { location -> onLocation(location) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (running || !hasPermission()) return
        running = true
        onSpeed(null)
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper()
            )
        }.onFailure { running = false }
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(staleCheck)
        runCatching { locationManager.removeUpdates(listener) }
    }

    private fun onLocation(location: Location) {
        handler.removeCallbacks(staleCheck)
        handler.postDelayed(staleCheck, STALE_MS)
        if (!location.hasSpeed()) return
        val kmh = (location.speed * 3.6f).roundToInt()
        // Đứng yên GPS vẫn trôi 1–3 km/h; hiện 0 cho khỏi nhảy số.
        onSpeed(if (kmh < MIN_KMH) 0 else kmh)
    }

    private companion object {
        const val INTERVAL_MS = 1000L
        const val STALE_MS = 6000L
        const val MIN_KMH = 3
    }
}
