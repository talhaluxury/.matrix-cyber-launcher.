package com.matrix.launcher.core

sealed class TerminalAction {
    object None : TerminalAction()
    object Clear : TerminalAction()
    object OpenSettings : TerminalAction()
    object OpenRadar : TerminalAction()
    object Scan : TerminalAction()
    object OpenShow : TerminalAction()
    object OpenGlobe : TerminalAction()
    data class SetTheme(val id: String) : TerminalAction()
    data class Launch(val key: String) : TerminalAction()
}

data class TerminalResult(val lines: List<String>, val action: TerminalAction = TerminalAction.None)

interface TerminalEnv {
    val apps: List<AppRef>
    fun time(): String
    fun date(): String
    fun battery(): String
    fun status(): List<String>
    fun storage(): String = "STORAGE: UNAVAILABLE"
    fun network(): String = "NETWORK: UNAVAILABLE"
    fun currentTheme(): String = "MATRIX_GREEN"
}

/**
 * Safe command parser. It only ever returns text and a small set of launcher actions.
 * There is no shell, no network and no file access behind these commands.
 */
object CommandParser {
    const val MAX_INPUT = 200

    val COMMANDS = listOf("HELP", "APPS", "SEARCH", "OPEN", "TIME", "DATE", "BATTERY", "STORAGE", "NETWORK", "STATUS", "RADAR", "SCAN", "THEME", "GLOBE", "SHOW", "SETTINGS", "CLEAR")

    /** Persisted ids of the built-in themes (must match ui.theme.ThemeId names). */
    val THEMES = listOf("MATRIX_GREEN", "STEALTH_BLACK", "CYAN_CYBER", "RED_ALERT", "MINIMAL_HUD")

    private fun matchTheme(arg: String): String? {
        val n = arg.trim().uppercase().replace('_', ' ').replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return null
        val hits = THEMES.filter { it.replace('_', ' ').startsWith(n) || it.replace('_', ' ').split(' ').first() == n }
        return hits.singleOrNull()
    }

    fun autocomplete(partial: String): List<String> {
        val p = partial.trim().uppercase()
        if (p.isEmpty() || p.contains(' ')) return emptyList()
        return COMMANDS.filter { it.startsWith(p) && it != p }
    }

    fun execute(raw: String, env: TerminalEnv): TerminalResult {
        val input = raw.filter { !it.isISOControl() }.trim().take(MAX_INPUT)
        if (input.isEmpty()) return TerminalResult(emptyList())
        val parts = input.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].uppercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()
        return when (cmd) {
            "HELP" -> TerminalResult(HELP)
            "APPS" -> {
                val labels = env.apps.map { it.label }
                val shown = labels.take(40)
                val lines = ArrayList<String>()
                lines.add("${labels.size} LAUNCHABLE APPS:")
                shown.forEach { lines.add("  $it") }
                if (labels.size > shown.size) lines.add("  ... +${labels.size - shown.size} MORE (USE SEARCH <TEXT>)")
                TerminalResult(lines)
            }
            "SEARCH" -> {
                if (arg.isEmpty()) TerminalResult(listOf("USAGE: SEARCH <TEXT>"))
                else {
                    val hits = env.apps.filter { it.label.contains(arg, ignoreCase = true) }
                    if (hits.isEmpty()) TerminalResult(listOf("NO APPS MATCH \"$arg\""))
                    else TerminalResult(listOf("${hits.size} MATCH(ES):") + hits.take(30).map { "  ${it.label}" })
                }
            }
            "OPEN" -> {
                if (arg.isEmpty()) TerminalResult(listOf("USAGE: OPEN <APP NAME>"))
                else when (val m = AppMatcher.match(arg, env.apps)) {
                    is MatchResult.None -> TerminalResult(listOf("NO APP MATCHES \"$arg\""))
                    is MatchResult.Single -> TerminalResult(listOf("LAUNCHING ${m.app.label}"), TerminalAction.Launch(m.app.key))
                    is MatchResult.Multiple -> TerminalResult(
                        listOf("AMBIGUOUS \"$arg\" - BE MORE SPECIFIC:") + m.apps.take(8).map { "  ${it.label}" }
                    )
                }
            }
            "TIME" -> TerminalResult(listOf("TIME: ${env.time()}"))
            "DATE" -> TerminalResult(listOf("DATE: ${env.date()}"))
            "BATTERY" -> TerminalResult(listOf(env.battery()))
            "STORAGE" -> TerminalResult(listOf(env.storage()))
            "NETWORK" -> TerminalResult(listOf(env.network(), "NOTE: CONNECTION TYPE ONLY. NO TRAFFIC IS INSPECTED."))
            "SCAN" -> TerminalResult(
                listOf("SCAN: VISUAL ANIMATION (SIMULATED) + REAL LOCAL TELEMETRY REFRESH", "NO NETWORK OR DEVICE IS PROBED."),
                TerminalAction.Scan,
            )
            "THEME" -> {
                if (arg.isEmpty()) {
                    TerminalResult(
                        listOf("CURRENT THEME: ${env.currentTheme().replace('_', ' ')}", "USAGE: THEME <NAME>  OPTIONS:") +
                            THEMES.map { "  ${it.replace('_', ' ')}" }
                    )
                } else {
                    val id = matchTheme(arg)
                    if (id == null) TerminalResult(listOf("UNKNOWN OR AMBIGUOUS THEME \"$arg\" (TYPE THEME)"))
                    else TerminalResult(listOf("THEME SET: ${id.replace('_', ' ')}"), TerminalAction.SetTheme(id))
                }
            }
            "GLOBE" -> TerminalResult(listOf("SWITCHING TO CYBER GLOBE (SIMULATED DATA)"), TerminalAction.OpenGlobe)
            "SHOW" -> TerminalResult(listOf("STARTING SHOW MODE - VISUAL DEMONSTRATION ONLY"), TerminalAction.OpenShow)
            "STATUS" -> TerminalResult(env.status())
            "RADAR" -> TerminalResult(listOf("OPENING RADAR (VISUAL SIMULATION - NO REAL SCAN)"), TerminalAction.OpenRadar)
            "SETTINGS" -> TerminalResult(listOf("OPENING LAUNCHER SETTINGS"), TerminalAction.OpenSettings)
            "CLEAR" -> TerminalResult(emptyList(), TerminalAction.Clear)
            else -> TerminalResult(listOf("UNKNOWN COMMAND: $cmd  (TYPE HELP)"))
        }
    }

    private val HELP = listOf(
        "COMMANDS:",
        "  HELP            SHOW THIS LIST",
        "  APPS            LIST LAUNCHABLE APPS",
        "  SEARCH <TEXT>   FILTER INSTALLED APPS",
        "  OPEN <APP>      LAUNCH A MATCHING APP",
        "  TIME / DATE     DEVICE TIME AND DATE",
        "  BATTERY         BATTERY LEVEL",
        "  STORAGE         INTERNAL STORAGE FREE/TOTAL",
        "  NETWORK         CONNECTION TYPE (NO TRAFFIC DATA)",
        "  STATUS          LOCAL LAUNCHER STATUS",
        "  SCAN            VISUAL SCAN + REAL TELEMETRY REFRESH",
        "  THEME [NAME]    LIST OR SWITCH THEMES",
        "  RADAR           SIMULATED RADAR (VISUAL ONLY)",
        "  GLOBE           SIMULATED CYBER GLOBE PANEL",
        "  SHOW            VISUAL DEMO (NO REAL OPERATIONS)",
        "  SETTINGS        LAUNCHER SETTINGS",
        "  CLEAR           CLEAR OUTPUT",
        "NOTE: THIS IS NOT A SHELL. NO SYSTEM COMMANDS RUN.",
    )
}
