package com.matrix.launcher.core

data class AssistantResult(
    val lines: List<String>,
    val action: TerminalAction = TerminalAction.None,
    val handled: Boolean = true,
)

/**
 * Offline, rule-based assistant. It maps plain-English requests onto the same safe commands as the
 * terminal and never invents results. Anything it cannot map is reported as unhandled so the caller
 * can say so honestly (or, if the user enabled it, forward the typed question to their own AI provider).
 */
object LocalAssistant {
    val EXPLAIN: Map<String, String> = mapOf(
        "HELP" to "Lists every terminal command.",
        "APPS" to "Lists the apps installed on this phone that have a launcher icon.",
        "SEARCH" to "SEARCH <text> filters installed apps by name.",
        "OPEN" to "OPEN <app> launches the matching installed app. Ambiguous names list the candidates.",
        "TIME" to "Shows the phone's current time.",
        "DATE" to "Shows the phone's current date.",
        "BATTERY" to "Shows the real battery level and charging state.",
        "STORAGE" to "Shows free and total internal storage as reported by Android.",
        "NETWORK" to "Shows connection type (Wi-Fi, cellular, offline). It never inspects traffic.",
        "STATUS" to "Shows launcher version, default-home state, app count, network, battery, RAM and uptime.",
        "RADAR" to "Opens a radar animation. Blips are simulated decoration, not real scans.",
        "SCAN" to "Runs a visual scan animation and refreshes real local telemetry. Nothing is probed.",
        "THEME" to "THEME lists themes, THEME <name> switches (for example THEME red).",
        "SETTINGS" to "Opens launcher settings.",
        "CLEAR" to "Clears the terminal output.",
        "SHOW" to "Starts the visual demonstration (Show Mode). It performs no real operations.",
        "GLOBE" to "Switches the home panel to the simulated cyber globe.",
    )

    private val INTRO = listOf(
        "LOCAL ASSISTANT ONLINE (OFFLINE RULES, NO AI MODEL).",
        "TRY: OPEN CHROME / FIND CAMERA / BATTERY / STORAGE / NETWORK / WHAT TIME IS IT",
        "TRY: EXPLAIN SCAN / THEME RED / SHOW / HELP",
    )

    fun reply(raw: String, env: TerminalEnv): AssistantResult {
        val t = raw.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return AssistantResult(emptyList())
        val words = t.split(' ')

        val explainPrefix = t.startsWith("explain ") || t.startsWith("what does ") || t.startsWith("how do i use ")
        if (explainPrefix) {
            val target = words.drop(1).map { it.uppercase() }.firstOrNull { it in EXPLAIN }
            if (target != null) return AssistantResult(listOf("$target: ${EXPLAIN[target]}"))
        }

        if (words[0].uppercase() in CommandParser.COMMANDS || words[0].uppercase() in EXPLAIN) {
            return fromTerminal(raw, env)
        }

        val rest = words.drop(1).joinToString(" ")
        when {
            (words[0] == "open" || words[0] == "launch" || words[0] == "start") && rest.isNotEmpty() ->
                return fromTerminal("OPEN $rest", env)
            words[0] == "find" || words[0] == "search" || (t.startsWith("where is ")) -> {
                val q = t.removePrefix("where is ").removePrefix("find ").removePrefix("search ").removePrefix("for ").trim()
                if (q.isNotEmpty()) return fromTerminal("SEARCH $q", env)
            }
        }

        fun has(vararg w: String) = w.any { it in words }
        return when {
            has("battery", "charge", "charging") -> fromTerminal("BATTERY", env)
            has("storage", "disk") || t.contains("free space") -> fromTerminal("STORAGE", env)
            has("network", "wifi", "internet", "online") || t.contains("wi fi") -> fromTerminal("NETWORK", env)
            t.contains("what time") || t.contains("current time") || t == "time" -> fromTerminal("TIME", env)
            has("date") || t.contains("what day") -> fromTerminal("DATE", env)
            has("status", "ram", "uptime") || t.contains("my phone") || t.contains("device") -> fromTerminal("STATUS", env)
            has("theme", "themes") -> fromTerminal("THEME " + t.substringAfter("theme").removePrefix("s").trim(), env)
            has("help", "hello", "hi") || t.contains("what can you do") -> AssistantResult(INTRO)
            else -> AssistantResult(emptyList(), handled = false)
        }
    }

    private fun fromTerminal(cmd: String, env: TerminalEnv): AssistantResult {
        val r = CommandParser.execute(cmd, env)
        return AssistantResult(r.lines, r.action)
    }

    fun unhandledMessage(aiAvailable: Boolean): List<String> =
        if (aiAvailable) emptyList()
        else listOf(
            "I CAN'T ANSWER THAT OFFLINE. I ONLY KNOW LAUNCHER AND DEVICE COMMANDS.",
            "TYPE HELP, OR ENABLE THE OPTIONAL REMOTE AI IN SETTINGS FOR GENERAL QUESTIONS.",
        )
}
