package com.matrix.launcher.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.matrixStore by preferencesDataStore(name = "matrix_launcher")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.matrixStore

    private object Keys {
        val INTENSITY = stringPreferencesKey("intensity")
        val RAIN = booleanPreferencesKey("rain")
        val SCAN = booleanPreferencesKey("scanlines")
        val RADAR = booleanPreferencesKey("radar")
        val SOUND = booleanPreferencesKey("sound")
        val HAPTICS = booleanPreferencesKey("haptics")
        val H24 = booleanPreferencesKey("h24")
        val COLS = intPreferencesKey("cols")
        val ICON = intPreferencesKey("icon")
        val TERM = intPreferencesKey("term")
        val BRIGHT = floatPreferencesKey("bright")
        val CONTRAST = booleanPreferencesKey("contrast")
        val REDUCE = booleanPreferencesKey("reduce")
        val BOOT = booleanPreferencesKey("boot")
        val LIST = booleanPreferencesKey("list")
        val VOICE = booleanPreferencesKey("voice")
        val LABEL = stringPreferencesKey("label")
        val FAVORITES = stringPreferencesKey("favorites")
        val RECENTS = stringPreferencesKey("recents")
        val WIDGETS = stringPreferencesKey("widgets")
    }

    private val safeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<LauncherSettings> = safeData.map { read(it) }.distinctUntilChanged()
    val favorites: Flow<List<String>> = safeData.map { split(it[Keys.FAVORITES]) }.distinctUntilChanged()
    val recents: Flow<List<String>> = safeData.map { split(it[Keys.RECENTS]) }.distinctUntilChanged()

    val widgets: Flow<List<Int>> =
        safeData.map { p -> split(p[Keys.WIDGETS]).mapNotNull { it.toIntOrNull() } }.distinctUntilChanged()

    suspend fun setWidgets(list: List<Int>) {
        try {
            store.edit { p -> p[Keys.WIDGETS] = list.joinToString("\n") }
        } catch (e: IOException) {
            Log.w("Settings", "DataStore write failed", e)
        }
    }

    private fun split(s: String?): List<String> =
        if (s.isNullOrEmpty()) emptyList() else s.split("\n").filter { it.isNotBlank() }

    private fun read(p: Preferences): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            intensity = p[Keys.INTENSITY]?.let { runCatching { Intensity.valueOf(it) }.getOrNull() } ?: d.intensity,
            codeRain = p[Keys.RAIN] ?: d.codeRain,
            scanlines = p[Keys.SCAN] ?: d.scanlines,
            radarAnim = p[Keys.RADAR] ?: d.radarAnim,
            sound = p[Keys.SOUND] ?: d.sound,
            haptics = p[Keys.HAPTICS] ?: d.haptics,
            is24h = p[Keys.H24] ?: d.is24h,
            gridColumns = (p[Keys.COLS] ?: d.gridColumns).coerceIn(3, 6),
            iconSize = (p[Keys.ICON] ?: d.iconSize).coerceIn(36, 80),
            terminalTextSize = (p[Keys.TERM] ?: d.terminalTextSize).coerceIn(10, 20),
            brightness = (p[Keys.BRIGHT] ?: d.brightness).coerceIn(0.3f, 1f),
            highContrast = p[Keys.CONTRAST] ?: d.highContrast,
            reduceMotion = p[Keys.REDUCE] ?: d.reduceMotion,
            bootSequence = p[Keys.BOOT] ?: d.bootSequence,
            listView = p[Keys.LIST] ?: d.listView,
            voiceEnabled = p[Keys.VOICE] ?: d.voiceEnabled,
            deviceLabel = p[Keys.LABEL] ?: d.deviceLabel,
        )
    }

    private fun write(p: MutablePreferences, s: LauncherSettings) {
        p[Keys.INTENSITY] = s.intensity.name
        p[Keys.RAIN] = s.codeRain
        p[Keys.SCAN] = s.scanlines
        p[Keys.RADAR] = s.radarAnim
        p[Keys.SOUND] = s.sound
        p[Keys.HAPTICS] = s.haptics
        p[Keys.H24] = s.is24h
        p[Keys.COLS] = s.gridColumns
        p[Keys.ICON] = s.iconSize
        p[Keys.TERM] = s.terminalTextSize
        p[Keys.BRIGHT] = s.brightness
        p[Keys.CONTRAST] = s.highContrast
        p[Keys.REDUCE] = s.reduceMotion
        p[Keys.BOOT] = s.bootSequence
        p[Keys.LIST] = s.listView
        p[Keys.VOICE] = s.voiceEnabled
        p[Keys.LABEL] = s.deviceLabel.take(32)
    }

    suspend fun update(block: (LauncherSettings) -> LauncherSettings) {
        try {
            store.edit { p -> write(p, block(read(p))) }
        } catch (e: IOException) {
            Log.w("Settings", "DataStore write failed", e)
        }
    }

    suspend fun setFavorites(list: List<String>) {
        try {
            store.edit { p -> p[Keys.FAVORITES] = list.joinToString("\n") }
        } catch (e: IOException) {
            Log.w("Settings", "DataStore write failed", e)
        }
    }

    suspend fun pushRecent(key: String) {
        try {
            store.edit { p ->
                val cur = split(p[Keys.RECENTS]).filter { it != key }
                p[Keys.RECENTS] = (listOf(key) + cur).take(8).joinToString("\n")
            }
        } catch (e: IOException) {
            Log.w("Settings", "DataStore write failed", e)
        }
    }
}
