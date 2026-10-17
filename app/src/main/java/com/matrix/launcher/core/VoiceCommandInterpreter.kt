package com.matrix.launcher.core

sealed class VoiceIntent {
    data class OpenApp(val name: String) : VoiceIntent()
    data class Search(val query: String) : VoiceIntent()
    object ShowBattery : VoiceIntent()
    object OpenTerminal : VoiceIntent()
    object OpenSettings : VoiceIntent()
    data class Unknown(val heard: String) : VoiceIntent()
}

object VoiceCommandInterpreter {
    fun interpret(text: String): VoiceIntent {
        val t = text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (t.isEmpty()) return VoiceIntent.Unknown(text)
        return when {
            t == "open terminal" || t == "terminal" || t == "show terminal" -> VoiceIntent.OpenTerminal
            t == "open settings" || t == "settings" || t == "show settings" -> VoiceIntent.OpenSettings
            t == "show battery" || t == "battery" || t == "battery status" -> VoiceIntent.ShowBattery
            t == "search applications" || t == "search apps" || t == "search application" -> VoiceIntent.Search("")
            t.startsWith("search for ") -> VoiceIntent.Search(t.removePrefix("search for ").trim())
            t.startsWith("search ") -> VoiceIntent.Search(t.removePrefix("search ").trim())
            t.startsWith("open ") -> VoiceIntent.OpenApp(t.removePrefix("open ").trim())
            t.startsWith("launch ") -> VoiceIntent.OpenApp(t.removePrefix("launch ").trim())
            else -> VoiceIntent.Unknown(t)
        }
    }
}
