package com.gsvn.aamusic.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.gsvn.aamusic.data.DriveSettings
import com.gsvn.aamusic.player.PlayerController

/**
 * Theo dõi việc nối / ngắt dàn âm thanh của xe.
 *
 * Dùng [AudioManager.registerAudioDeviceCallback] chứ không nghe
 * `BluetoothDevice.ACTION_ACL_CONNECTED`: cách này thấy đúng thứ cần thấy (đầu
 * ra âm thanh vừa xuất hiện, kể cả cắm dây AUX/USB) và **không cần xin quyền
 * BLUETOOTH_CONNECT** — app nghe nhạc không nên đòi quyền Bluetooth.
 *
 * Hai việc:
 *  1. Nối vào xe → phát tiếp, nếu người dùng đã bật tuỳ chọn. Mặc định TẮT: tự
 *     nhiên rống nhạc lúc vừa nổ máy là hành vi rất khó chịu.
 *  2. Rút ra → dừng hẳn. Việc này **luôn** làm, không cần tuỳ chọn: watchdog
 *     của [com.gsvn.aamusic.web.PlaybackGuard] vốn coi mọi lần trang tự dừng là
 *     sự cố và phát lại, nên nếu không hạ cờ `__ytaWantPlay` thì rút dây khỏi
 *     xe là nhạc chuyển sang gào trên loa điện thoại.
 */
class CarConnection(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false

    /** Báo cho UI biết vừa nối/ngắt xe (để hiện thông báo nhẹ). */
    var onCarConnected: (() -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (addedDevices?.any { it.type in CAR_OUTPUT_TYPES } != true) return
            if (!DriveSettings.isOn(context, DriveSettings.KEY_CAR_AUTO_RESUME)) return
            onCarConnected?.invoke()
            // Đường ra âm thanh vừa đổi, trình phát cần một nhịp để bám vào
            // thiết bị mới thì play() mới ăn.
            handler.postDelayed({ PlayerController.play() }, RESUME_DELAY_MS)
        }
    }

    private val becomingNoisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            // pause() hạ cờ __ytaWantPlay nên watchdog không phát lại.
            PlayerController.pause()
        }
    }

    fun register() {
        if (registered) return
        registered = true
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        context.registerReceiver(
            becomingNoisy,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregister() {
        if (!registered) return
        registered = false
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { context.unregisterReceiver(becomingNoisy) }
        handler.removeCallbacksAndMessages(null)
        onCarConnected = null
    }

    /** Đang nối vào một đầu ra kiểu xe hơi / Bluetooth stereo. */
    fun isConnectedToCar(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type in CAR_OUTPUT_TYPES }

    private companion object {
        val CAR_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BUS,            // dàn âm thanh tích hợp trên xe
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_LINE_ANALOG
        )

        const val RESUME_DELAY_MS = 1200L
    }
}
