package com.matrix.launcher.system

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.matrix.launcher.VoiceState

/**
 * Opt-in, one-shot speech recognition. Starts only from an explicit user tap, listens once, then stops.
 * The installed speech service (often Google) may send audio over the internet unless an offline model is installed.
 */
class VoiceController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onState: (VoiceState) -> Unit,
    private val onFail: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable()) {
            onFail("VOICE UNAVAILABLE: NO SPEECH SERVICE ON THIS DEVICE")
            return
        }
        destroy()
        try {
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    onState(VoiceState.IDLE)
                    onFail(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "VOICE: DIDN'T CATCH THAT - TRY AGAIN"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "VOICE: SPEECH SERVICE NEEDS INTERNET (NO OFFLINE MODEL)"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "VOICE: MICROPHONE PERMISSION MISSING"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "VOICE: RECOGNIZER BUSY"
                            else -> "VOICE ERROR ($error)"
                        }
                    )
                }

                override fun onResults(results: Bundle?) {
                    onState(VoiceState.IDLE)
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = list?.firstOrNull()
                    if (best.isNullOrBlank()) onFail("VOICE: NOTHING RECOGNIZED") else onResult(best)
                }
            })
            recognizer = r
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            r.startListening(i)
            onState(VoiceState.LISTENING)
        } catch (e: Exception) {
            onState(VoiceState.IDLE)
            onFail("VOICE UNAVAILABLE: ${e.javaClass.simpleName}")
        }
    }

    fun stop() {
        try { recognizer?.stopListening() } catch (e: Exception) { /* ignore */ }
        onState(VoiceState.IDLE)
    }

    fun destroy() {
        try { recognizer?.destroy() } catch (e: Exception) { /* ignore */ }
        recognizer = null
    }
}
