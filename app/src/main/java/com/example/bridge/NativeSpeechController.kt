package com.example.bridge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONObject

class NativeSpeechController(
    private val context: Context,
    private val onDispatchJsEvent: (script: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    fun startListening(rawLang: String) {
        mainHandler.post {
            val lang = normalizeLocale(rawLang)
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                dispatchEvent("error", "Speech recognition unavailable on this device")
                dispatchEvent("end", "")
                return@post
            }

            stopInternal()

            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    dispatchEvent("start", "")
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    val code = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                        else -> "aborted"
                    }
                    dispatchEvent("error", code)
                    dispatchEvent("end", "")
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) {
                        dispatchEvent("result", text, isFinal = true)
                    }
                    dispatchEvent("end", "")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) {
                        dispatchEvent("result", text, isFinal = false)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            stopInternal()
            dispatchEvent("end", "")
        }
    }

    fun destroy() {
        mainHandler.post {
            stopInternal()
        }
    }

    private fun stopInternal() {
        recognizer?.let { sr ->
            runCatching { sr.stopListening() }
            runCatching { sr.cancel() }
            runCatching { sr.destroy() }
        }
        recognizer = null
    }

    private fun normalizeLocale(rawLang: String): String {
        val cleaned = rawLang.trim()
        return when {
            cleaned.equals("ml-IN", ignoreCase = true) || cleaned.startsWith("ml", ignoreCase = true) -> "ml-IN"
            cleaned.equals("en-IN", ignoreCase = true) -> "en-IN"
            cleaned.matches(Regex("^[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?$")) -> cleaned
            else -> "en-IN"
        }
    }

    private fun dispatchEvent(type: String, payload: String, isFinal: Boolean = true) {
        val json = JSONObject().apply {
            put("type", type)
            put("payload", payload)
            put("isFinal", isFinal)
        }
        val script = "window.__civicNativeSpeechDispatch && window.__civicNativeSpeechDispatch($json);"
        onDispatchJsEvent(script)
    }
}
