package com.matrix.launcher.system

import android.view.HapticFeedbackConstants
import android.view.View

class Feedback(
    private val view: View?,
    private val haptics: Boolean,
    private val sound: SoundFx?,
    private val soundOn: Boolean,
) {
    fun tap() {
        if (haptics) view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (soundOn) sound?.play(SoundFx.Type.TAP)
    }

    fun play(type: SoundFx.Type) {
        if (soundOn) sound?.play(type)
    }

    companion object {
        val None = Feedback(null, false, null, false)
    }
}
