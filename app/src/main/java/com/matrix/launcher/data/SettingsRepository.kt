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
        val THEME = stringPreferencesKey("theme")
        val RAIN_SPEED = intPreferencesKey("rainspeed")
        val BATTERY_FRIENDLY = booleanPreferencesKey("batfriendly")
        val PARALLAX = booleanPreferencesKey("parallax")
        val HUD = booleanPreferencesKey("hud")
        val DEFAULT_PANEL = intPreferencesKey("defpanel")
        val AI_ON = booleanPreferencesKey("aion")
        val AI_URL = stringPreferencesKey("aiurl")
        val AI_MODEL = stringPreferencesKey("aimodel")
        val AI_KEY = stringPreferencesKey("aikey")
        val PIN_HASH = stringPreferencesKey("pinhash")
        val PIN_SALT = stringPreferencesKey("pinsalt")
        val PRIVATE_APPS = stringPreferencesKey("privapps")
        val MATRIX_ICONS = booleanPreferencesKey("mticons")
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

    val pinState: Flow<PinState> = safeData.map { p ->
        val h = p[Keys.PIN_HASH]
        val s = p[Keys.PIN_SALT]
        if (h != null && s != null) PinState.Set(salt = s, hash = h) else PinState.None
    }.distinctUntilChanged()

    val privateApps: Flow<List<String>> = safeData.map { split(it[Keys.PRIVATE_APPS]) }.distinctUntilChanged()
    val aiKey: Flow<String> = safeData.map { it[Keys.AI_KEY] ?: "" }.distinctUntilChanged()

    suspend fun setPin(salt: String, hash: String) = safeEdit { p ->
        p[Keys.PIN_SALT] = salt
        p[Keys.PIN_HASH] = hash
    }

    suspend fun clearPin() = safeEdit { p ->
        p.remove(Keys.PIN_SALT)
        p.remove(Keys.PIN_HASH)
        p.remove(Keys.PRIVATE_APPS)
    }

    suspend fun setPrivateApps(list: List<String>) = safeEdit { p -> p[Keys.PRIVATE_APPS] = list.joinToString("\n") }
    suspend fun setAiKey(key: String) = safeEdit { p -> p[Keys.AI_KEY] = key.trim().take(300) }

    private suspend fun safeEdit(block: (MutablePreferences) -> Unit) {
        try {
            store.edit { p -> block(p) }
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
            matrixIcons = p[Keys.MATRIX_ICONS] ?: d.matrixIcons,
            deviceLabel = p[Keys.LABEL] ?: d.deviceLabel,
            theme = p[Keys.THEME] ?: d.theme,
            rainSpeed = (p[Keys.RAIN_SPEED] ?: d.rainSpeed).coerceIn(50, 200),
            batteryFriendly = p[Keys.BATTERY_FRIENDLY] ?: d.batteryFriendly,
            parallax = p[Keys.PARALLAX] ?: d.parallax,
            hudVisible = p[Keys.HUD] ?: d.hudVisible,
            defaultPanel = (p[Keys.DEFAULT_PANEL] ?: d.defaultPanel).coerceIn(0, 2),
            aiEnabled = p[Keys.AI_ON] ?: d.aiEnabled,
            aiBaseUrl = p[Keys.AI_URL] ?: d.aiBaseUrl,
            aiModel = p[Keys.AI_MODEL] ?: d.aiModel,
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
        p[Keys.MATRIX_ICONS] = s.matrixIcons
        p[Keys.LABEL] = s.deviceLabel.take(32)
        p[Keys.THEME] = s.theme
        p[Keys.RAIN_SPEED] = s.rainSpeed
        p[Keys.BATTERY_FRIENDLY] = s.batteryFriendly
        p[Keys.PARALLAX] = s.parallax
        p[Keys.HUD] = s.hudVisible
        p[Keys.DEFAULT_PANEL] = s.defaultPanel
        p[Keys.AI_ON] = s.aiEnabled
        p[Keys.AI_URL] = s.aiBaseUrl.take(200)
        p[Keys.AI_MODEL] = s.aiModel.take(80)
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
