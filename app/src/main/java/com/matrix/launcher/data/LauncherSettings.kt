package com.matrix.launcher.data

enum class Intensity { LOW, MEDIUM, EXTREME }

data class LauncherSettings(
    val intensity: Intensity = Intensity.MEDIUM,
    val codeRain: Boolean = true,
    val scanlines: Boolean = true,
    val radarAnim: Boolean = true,
    val sound: Boolean = false,
    val haptics: Boolean = true,
    val is24h: Boolean = true,
    val gridColumns: Int = 4,
    val iconSize: Int = 52,
    val terminalTextSize: Int = 13,
    val brightness: Float = 1f,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val bootSequence: Boolean = true,
    val listView: Boolean = false,
    val voiceEnabled: Boolean = true,
    val matrixIcons: Boolean = true,
    val deviceLabel: String = "",
)
