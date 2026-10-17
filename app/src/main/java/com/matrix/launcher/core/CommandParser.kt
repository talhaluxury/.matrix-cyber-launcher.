package com.matrix.launcher.core

sealed class TerminalAction {
    object None : TerminalAction()
    object Clear : TerminalAction()
    object OpenSettings : TerminalAction()
    object OpenRadar : TerminalAction()
    data class Launch(val key: String) : TerminalAction()
}

data class TerminalResult(val lines: List<String>, val action: TerminalAction = TerminalAction.None)

interface TerminalEnv {
    val apps: List<AppRef>
    fun time(): String
    fun date(): String
    fun battery(): String
    fun status(): List<String>
}

/**
 * Safe command parser. It only ever returns text and a small set of launcher actions.
 * There is no shell, no network and no file access behind these commands.
 */
object CommandParser {
    const val MAX_INPUT = 200

    val COMMANDS = listOf("HELP", "APPS", "SEARCH", "OPEN", "TIME", "DATE", "BATTERY", "STATUS", "RADAR", "SETTINGS", "CLEAR")

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
        "  STATUS          LOCAL LAUNCHER STATUS",
        "  RADAR           SIMULATED RADAR (VISUAL ONLY)",
        "  SETTINGS        LAUNCHER SETTINGS",
        "  CLEAR           CLEAR OUTPUT",
        "NOTE: THIS IS NOT A SHELL. NO SYSTEM COMMANDS RUN.",
    )
}
