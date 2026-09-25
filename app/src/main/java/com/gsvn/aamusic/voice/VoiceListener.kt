package com.gsvn.aamusic.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
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
 * Phải có quyền RECORD_AUDIO và `<queries>` cho RecognitionService trong
 * manifest (Android 11+ ẩn dịch vụ nhận dạng nếu không khai).
 */
class VoiceListener(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Bắt đầu nghe. [onResult] nhận câu nhận ra được; [onFail] khi không nghe
     * được gì (im lặng, mất mạng, không có dịch vụ). Cả hai chạy trên main thread.
     */
    fun listen(onResult: (String) -> Unit, onFail: () -> Unit) {
        cancel()
        val sr = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            ?: run { onFail(); return }
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                release()
                if (text != null) onResult(text) else onFail()
            }

            override fun onError(error: Int) {
                release()
                onFail()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { sr.startListening(intent) }.onFailure {
            release()
            onFail()
        }
    }

    fun cancel() {
        recognizer?.let { runCatching { it.cancel() } }
        release()
    }

    private fun release() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }
}
