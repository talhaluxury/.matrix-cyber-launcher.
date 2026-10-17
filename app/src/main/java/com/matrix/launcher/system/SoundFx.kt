package com.matrix.launcher.system

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock

/** Tiny generated UI tones (no audio assets). Quiet, rate-limited, follows the system volume / silent mode. */
class SoundFx {
    enum class Type { BOOT, TAP, SCAN, COMMAND, DRAWER }

    private var gen: ToneGenerator? = null
    private var last = 0L

    fun play(type: Type) {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 120) return
        last = now
        try {
            if (gen == null) gen = ToneGenerator(AudioManager.STREAM_SYSTEM, 25)
            val (tone, ms) = when (type) {
                Type.BOOT -> ToneGenerator.TONE_PROP_ACK to 120
                Type.TAP -> ToneGenerator.TONE_PROP_BEEP to 30
                Type.SCAN -> ToneGenerator.TONE_PROP_BEEP2 to 80
                Type.COMMAND -> ToneGenerator.TONE_PROP_PROMPT to 60
                Type.DRAWER -> ToneGenerator.TONE_PROP_BEEP to 40
            }
            gen?.startTone(tone, ms)
        } catch (e: RuntimeException) {
            release()
        }
    }

    fun release() {
        try { gen?.release() } catch (e: RuntimeException) { /* ignore */ }
        gen = null
    }
}
