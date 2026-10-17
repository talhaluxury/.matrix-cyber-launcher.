package com.matrix.launcher.anim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * One cheap frame clock per effect. Stops completely when [active] is false (app hidden / reduce motion).
 * Read the returned State only inside draw lambdas so frames cause redraws, not recompositions.
 */
@Composable
fun rememberFrameTime(active: Boolean, fps: Int): State<Float> {
    val time = remember { mutableStateOf(0f) }
    LaunchedEffect(active, fps) {
        if (!active) return@LaunchedEffect
        val minDelta = 1_000_000_000L / fps.coerceAtLeast(5)
        var start = -1L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                if (now - last >= minDelta) {
                    last = now
                    time.value = (now - start) / 1_000_000_000f
                }
            }
        }
    }
    return time
}
