package com.matrix.launcher.anim

import com.matrix.launcher.data.Intensity

/** Everything the animation engine needs to know. Decorative animation runs only when [animate] is true. */
data class Fx(
    val visible: Boolean = true,
    val rain: Boolean = true,
    val scanlines: Boolean = true,
    val radar: Boolean = true,
    val intensity: Intensity = Intensity.MEDIUM,
    val reduceMotion: Boolean = false,
    val brightness: Float = 1f,
    val highContrast: Boolean = false,
    val matrixIcons: Boolean = true,
    val rainSpeed: Float = 1f,
) {
    val animate: Boolean get() = visible && !reduceMotion
    val fps: Int
        get() = when (intensity) {
            Intensity.LOW -> 20
            Intensity.MEDIUM -> 30
            Intensity.EXTREME -> 45
        }
    val rainColumns: Int
        get() = when (intensity) {
            Intensity.LOW -> 12
            Intensity.MEDIUM -> 20
            Intensity.EXTREME -> 32
        }
    val speed: Float
        get() = when (intensity) {
            Intensity.LOW -> 0.6f
            Intensity.MEDIUM -> 1f
            Intensity.EXTREME -> 1.6f
        } * rainSpeed
}
