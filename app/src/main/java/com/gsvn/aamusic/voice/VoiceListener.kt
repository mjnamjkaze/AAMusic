package com.gsvn.aamusic.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Nghe một câu nói ngay trong app bằng [SpeechRecognizer].
 *
 * Bản cũ mở hộp thoại nhận giọng nói của hệ thống (RecognizerIntent) — trên
 * màn hình xe hộp thoại đó hay không hiện được hoặc hiện trên điện thoại, nói
 * xong không có kết quả nào trả về app. Nghe trực tiếp thì không cần màn hình
 * nào cả.
 *
 * Ưu tiên dịch vụ nhận dạng của Google: máy Samsung/Xiaomi mặc định dùng dịch
 * vụ của hãng, thường không hiểu tiếng Việt và trả lỗi ngay.
 *
 * Phải có quyền RECORD_AUDIO và `<queries>` cho RecognitionService trong
 * manifest (Android 11+ ẩn dịch vụ nhận dạng nếu không khai).
 */
class VoiceListener(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Bắt đầu nghe. [onResult] nhận câu nhận ra được; [onFail] nhận mã lỗi
     * `SpeechRecognizer.ERROR_*` (xem [isNoSpeech]). Cả hai chạy trên main thread.
     */
    fun listen(onResult: (String) -> Unit, onFail: (Int) -> Unit) {
        cancel()
        val sr = runCatching { create() }.getOrNull()
            ?: run { onFail(SpeechRecognizer.ERROR_CLIENT); return }
        recognizer = sr
        // Kết quả tạm: có máy nói xong vẫn báo "không khớp" dù đã nghe ra chữ.
        var partial: String? = null
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = firstText(results) ?: partial
                release()
                if (text != null) onResult(text) else onFail(SpeechRecognizer.ERROR_NO_MATCH)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstText(partialResults)?.let { partial = it }
            }

            override fun onError(error: Int) {
                release()
                val text = partial
                if (text != null && isNoSpeech(error)) onResult(text) else onFail(error)
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { sr.startListening(intent) }.onFailure {
            release()
            onFail(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun cancel() {
        recognizer?.let { runCatching { it.cancel() } }
        release()
    }

    /** Dịch vụ của Google nếu máy có, không thì dịch vụ mặc định của máy. */
    private fun create(): SpeechRecognizer {
        val google = context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { it.serviceInfo }
            .firstOrNull { it.packageName == GOOGLE_APP }
        return if (google != null) {
            SpeechRecognizer.createSpeechRecognizer(
                context, ComponentName(google.packageName, google.name)
            )
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private fun firstText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull { it.isNotBlank() }

    private fun release() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    companion object {
        private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

        /** Lỗi do người dùng không nói / nói không rõ — không phải hỏng dịch vụ. */
        fun isNoSpeech(error: Int): Boolean =
            error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    }
}
